package com.mcplugin.features.leash;

import com.mcplugin.Main;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Позволяет привязать ЛЮБУЮ сущность к ЛЮБОЙ сущности через Lead (поводок).
 * <p>
 * — Обычный ПКМ с Lead: привязывает сущность к игроку (toggle)
 * — SHIFT+ПКМ с Lead: двухшаговая привязка сущность → сущность
 * — SHIFT+ПКМ с Lead по забору: привязка к блоку (якорь)
 * — Жёсткий стоп: сущность НЕ может уйти дальше max_distance —
 *   не растягивается, а мгновенно останавливается
 * — Поводки НЕ рвутся по дистанции — вместо разрыва жёсткий стоп
 * — Бесконечное количество поводков от одной сущности к разным холдерам
 * — Поддержка любых Entity: стрелы, лодки, предметы, мобы, игроки и т.д.
 */
public class LeashManager implements Listener {

    private static boolean enabled = true;
    private static int maxLeashDistance = 10;   // блоков
    private static int pullBackInterval = 2;    // тиков (2 = почти мгновенно)
    private static boolean preventBreak = true;
    private static boolean hardStop = true;     // жёсткий телепорт вместо подтягивания

    // =========================
    // PENDING LINK: игрок → выбранная сущность (для SHIFT+ПКМ)
    // =========================
    private static final Map<UUID, Entity> pendingLink = new HashMap<>();

    // =========================
    // CUSTOM LEASH TRACKING
    // leashedUUID → Set<holderUUID>  (один leashed может быть привязан к нескольким holders)
    // =========================
    private static final Map<UUID, Set<UUID>> customLeashMap = new ConcurrentHashMap<>();
    // holderUUID → Set<leashedUUID>  (один holder может держать бесконечно много сущностей)
    private static final Map<UUID, Set<UUID>> customLeashReverse = new ConcurrentHashMap<>();

    // =========================
    // PROXY ARMY-STAND ДЛЯ СУЩНОСТЕЙ БЕЗ ВАНИЛЬНОЙ ВЕРЁВКИ
    // Для Player и не-LivingEntity создаём ArmorStand-прокси.
    // ⚠ БЕЗ setMarker(true) — иначе верёвка не рендерится в Paper 1.21.4+.
    // leashedUUID → holderUUID → proxy ArmorStand
    // =========================
    private static final Map<UUID, Map<UUID, ArmorStand>> leashProxies = new ConcurrentHashMap<>();
    // UUID'ы прокси для O(1) проверки в checkLeashDistances
    private static final Set<UUID> proxyUuids = ConcurrentHashMap.newKeySet();

    // =========================
    // IMMOBILIZED PLAYERS
    // =========================
    private static final Set<UUID> immobilizedPlayers = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Float> originalWalkSpeeds = new ConcurrentHashMap<>();

    // =========================
    // TASK
    // =========================
    private static BukkitRunnable pullBackTask;
    private static BukkitRunnable proxyUpdateTask;
    private static boolean running = false;
    private static LeashManager registeredInstance = null;

    // =========================
    // INIT
    // =========================
    public static void init(Main plugin) {
        if (running) return;

        reloadConfig();

        if (registeredInstance == null) {
            registeredInstance = new LeashManager();
            plugin.getServer().getPluginManager().registerEvents(registeredInstance, plugin);
        }

        pullBackTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                checkLeashDistances();
            }
        };
        pullBackTask.runTaskTimer(plugin, 1L, pullBackInterval);

        proxyUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) return;
                updateProxyPositions();
            }
        };
        proxyUpdateTask.runTaskTimer(plugin, 1L, 1L);
        running = true;

        plugin.getLogger().info("[Leash] Initialized. maxDist=" + maxLeashDistance
                + " interval=" + pullBackInterval + " hardStop=" + hardStop
                + " preventBreak=" + preventBreak);
    }

    public static void shutdown() {
        if (pullBackTask != null) { pullBackTask.cancel(); pullBackTask = null; }
        if (proxyUpdateTask != null) { proxyUpdateTask.cancel(); proxyUpdateTask = null; }
        for (Map<UUID, ArmorStand> sub : leashProxies.values()) {
            for (ArmorStand proxy : sub.values()) proxy.remove();
        }
        leashProxies.clear();
        proxyUuids.clear();
        immobilizedPlayers.clear();
        originalWalkSpeeds.clear();
        pendingLink.clear();
        customLeashMap.clear();
        customLeashReverse.clear();
        running = false;
    }

    // =========================
    // RELOAD CONFIG
    // =========================
    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.leash");
        if (cfg != null) {
            enabled = cfg.getBoolean("enabled", true);
            maxLeashDistance = cfg.getInt("max_distance", 10);
            if (maxLeashDistance < 1) maxLeashDistance = 1;
            pullBackInterval = cfg.getInt("pull_back_interval", 2);
            if (pullBackInterval < 1) pullBackInterval = 1;
            preventBreak = cfg.getBoolean("prevent_break", true);
            hardStop = cfg.getBoolean("hard_stop", true);
        } else {
            enabled = true; maxLeashDistance = 10; pullBackInterval = 2;
            preventBreak = true; hardStop = true;
        }
    }

    // =========================
    // CUSTOM LEASH HELPERS (multi-holder)
    // =========================
    private static void addCustomLeash(Entity leashed, Entity holder) {
        UUID lUuid = leashed.getUniqueId();
        UUID hUuid = holder.getUniqueId();
        customLeashMap.computeIfAbsent(lUuid, k -> ConcurrentHashMap.newKeySet()).add(hUuid);
        customLeashReverse.computeIfAbsent(hUuid, k -> ConcurrentHashMap.newKeySet()).add(lUuid);
    }

    private static void removeCustomLeash(Entity leashed, Entity holder) {
        UUID lUuid = leashed.getUniqueId();
        UUID hUuid = holder.getUniqueId();
        Set<UUID> holders = customLeashMap.get(lUuid);
        if (holders != null) { holders.remove(hUuid); if (holders.isEmpty()) customLeashMap.remove(lUuid); }
        Set<UUID> leasheds = customLeashReverse.get(hUuid);
        if (leasheds != null) { leasheds.remove(lUuid); if (leasheds.isEmpty()) customLeashReverse.remove(hUuid); }
    }

    private static void removeAllCustomLeashes(Entity leashed) {
        if (leashed == null) return;
        UUID lUuid = leashed.getUniqueId();
        Set<UUID> holders = customLeashMap.remove(lUuid);
        if (holders != null) {
            for (UUID hUuid : holders) {
                Set<UUID> leasheds = customLeashReverse.get(hUuid);
                if (leasheds != null) { leasheds.remove(lUuid); if (leasheds.isEmpty()) customLeashReverse.remove(hUuid); }
            }
        }
    }

    private static Set<UUID> getCustomLeashHolders(Entity leashed) {
        Set<UUID> set = customLeashMap.get(leashed.getUniqueId());
        return set != null ? set : Collections.emptySet();
    }

    // =========================
    // ОБНОВЛЕНИЕ ПОЗИЦИЙ ПРОКСИ (1 тик)
    // =========================
    private static void updateProxyPositions() {
        for (UUID entityUuid : Set.copyOf(leashProxies.keySet())) {
            Entity owner = Bukkit.getEntity(entityUuid);
            Map<UUID, ArmorStand> subMap = leashProxies.get(entityUuid);

            if (owner == null || !owner.isValid() || owner.isDead() || subMap == null) {
                if (subMap != null) for (ArmorStand p : subMap.values()) { proxyUuids.remove(p.getUniqueId()); p.remove(); }
                leashProxies.remove(entityUuid);
                immobilizedPlayers.remove(entityUuid);
                originalWalkSpeeds.remove(entityUuid);
                removeAllCustomLeashes(owner != null ? owner : null);
                continue;
            }

            for (Map.Entry<UUID, ArmorStand> entry : Set.copyOf(subMap.entrySet())) {
                UUID hUuid = entry.getKey();
                ArmorStand proxy = entry.getValue();
                if (proxy == null || !proxy.isValid()) { subMap.remove(hUuid); continue; }

                Entity holder = Bukkit.getEntity(hUuid);
                if (holder == null || !holder.isValid() || holder.isDead()) {
                    proxyUuids.remove(proxy.getUniqueId());
                    proxy.setLeashHolder(null); proxy.remove();
                    subMap.remove(hUuid);
                    removeCustomLeash(owner, holder != null ? holder : null);
                    continue;
                }

                if (!owner.getWorld().equals(proxy.getWorld())) {
                    proxyUuids.remove(proxy.getUniqueId()); proxy.remove();
                    double yOff = (owner instanceof Player) ? 1.0 : 0.5;
                    ArmorStand newProxy = owner.getWorld().spawn(owner.getLocation().add(0, yOff, 0), ArmorStand.class, stand -> {
                        stand.setVisible(false);
                        stand.setGravity(false); stand.setInvulnerable(true);
                        stand.setSilent(true); stand.setCanMove(false);
                    });
                    if (holder.getWorld().equals(owner.getWorld())) newProxy.setLeashHolder(holder);
                    subMap.put(hUuid, newProxy);
                    proxyUuids.add(newProxy.getUniqueId());
                    continue;
                }

                double yOff = (owner instanceof Player) ? 1.0 : 0.5;
                Location target = owner.getLocation().add(0, yOff, 0);
                Location proxyLoc = proxy.getLocation();
                if (!proxyLoc.getWorld().equals(target.getWorld()) || proxyLoc.distanceSquared(target) > 0.01) {
                    proxy.teleport(target);
                }
            }
            if (subMap.isEmpty()) leashProxies.remove(entityUuid);
        }
    }

    // =========================
    // ПРОВЕРКА ДИСТАНЦИИ — ЖЁСТКИЙ СТОП
    // =========================
    private static void checkLeashDistances() {
        List<Entity[]> pairs = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (!living.isLeashed()) continue;
                if (proxyUuids.contains(living.getUniqueId())) continue;
                Entity holder = living.getLeashHolder();
                if (holder == null || !holder.isValid() || holder.isDead()) continue;
                pairs.add(new Entity[]{living, holder});
            }
        }

        for (Map.Entry<UUID, Set<UUID>> entry : new HashMap<>(customLeashMap).entrySet()) {
            UUID lUuid = entry.getKey();
            Entity leashed = Bukkit.getEntity(lUuid);
            if (leashed == null || !leashed.isValid() || leashed.isDead()) {
                removeAllCustomLeashes(leashed);
                continue;
            }
            for (UUID hUuid : new HashSet<>(entry.getValue())) {
                Entity holder = Bukkit.getEntity(hUuid);
                if (holder == null || !holder.isValid() || holder.isDead()) {
                    removeCustomLeash(leashed, holder);
                    continue;
                }
                if (leashed instanceof LivingEntity living && living.isLeashed()
                        && living.getLeashHolder() != null
                        && living.getLeashHolder().getUniqueId().equals(hUuid)) continue;
                pairs.add(new Entity[]{leashed, holder});
            }
        }

        for (Entity[] pair : pairs) {
            Entity leashed = pair[0], holder = pair[1];
            if (!leashed.isValid() || leashed.isDead()) continue;
            if (!holder.isValid() || holder.isDead()) continue;
            Location leashedLoc = leashed.getLocation(), holderLoc = holder.getLocation();
            if (!leashedLoc.getWorld().equals(holderLoc.getWorld())) continue;
            double dist = leashedLoc.distance(holderLoc);
            if (dist <= maxLeashDistance) continue;

            if (hardStop) {
                Vector dir = leashedLoc.toVector().subtract(holderLoc.toVector());
                if (dir.lengthSquared() < 0.0001) continue;
                dir.normalize();
                Location boundary = holderLoc.clone().add(dir.multiply(maxLeashDistance));
                if (!hasBlockBetween(leashedLoc, boundary)) {
                    if (leashedLoc.distance(boundary) > 0.1) {
                        boundary.setPitch(leashedLoc.getPitch());
                        boundary.setYaw(leashedLoc.getYaw());
                        leashed.teleport(boundary);
                    }
                }
                leashed.setVelocity(new Vector(0, 0, 0));
            } else {
                Vector direction = holderLoc.toVector().subtract(leashedLoc.toVector()).normalize();
                double pullStrength = Math.min(1.0, (dist - maxLeashDistance) / 5.0);
                Vector pullVelocity = direction.multiply(pullStrength * 0.5);
                if (pullVelocity.getY() > 0.3) pullVelocity.setY(0.3);
                if (pullVelocity.getY() < -0.3) pullVelocity.setY(-0.3);
                leashed.setVelocity(pullVelocity);
            }
        }
    }

    // =========================
    // EVENT: Отмена обрыва поводка — ЛЮБОЙ обрыв отменяется
    // =========================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeashBreak(EntityUnleashEvent event) {
        if (!enabled || !preventBreak) return;
        Entity unleashed = event.getEntity();

        // Проверяем прокси — обрыв прокси не должен ломать связь
        for (Map.Entry<UUID, Map<UUID, ArmorStand>> outer : leashProxies.entrySet()) {
            for (Map.Entry<UUID, ArmorStand> inner : outer.getValue().entrySet()) {
                if (inner.getValue().equals(unleashed)) {
                    UUID ownerUuid = outer.getKey();
                    UUID holderUuid = inner.getKey();
                    Entity owner = Bukkit.getEntity(ownerUuid);
                    // Удаляем только этот прокси (связь с этим холдером)
                    proxyUuids.remove(unleashed.getUniqueId());
                    unleashed.remove();
                    outer.getValue().remove(holderUuid);
                    if (outer.getValue().isEmpty()) leashProxies.remove(ownerUuid);
                    if (owner != null) {
                        removeCustomLeash(owner, Bukkit.getEntity(holderUuid));
                        // Если больше нет прокси — снимаем обездвиживание
                        if (!leashProxies.containsKey(ownerUuid)) {
                            immobilizedPlayers.remove(ownerUuid);
                            Float origSpeed = originalWalkSpeeds.remove(ownerUuid);
                            if (origSpeed != null && owner instanceof Player ownerPlayer) {
                                ownerPlayer.setWalkSpeed(origSpeed);
                            }
                        }
                    }
                    return;
                }
            }
        }

        // Отменяем ВСЕ обрывы — поводки не рвутся, только жёсткий стоп
        event.setCancelled(true);
    }

    // =========================
    // EVENT: SHIFT+ПКМ по блоку (забор/стена/ограда) — привязка к якорю
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        Entity pending = pendingLink.get(player.getUniqueId());
        if (pending == null) return;
        boolean isMainHand = (event.getHand() == EquipmentSlot.HAND);
        ItemStack heldItem = isMainHand ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (heldItem.getType() != Material.LEAD) return;
        if (!pending.isValid() || pending.isDead()) {
            pendingLink.remove(player.getUniqueId());
            player.sendMessage("§c❌ Выбранная сущность мертва или исчезла!");
            return;
        }
        Material blockType = event.getClickedBlock().getType();
        String blockTypeName = blockType.name();
        if (!blockTypeName.contains("FENCE") && !blockTypeName.contains("WALL") && !blockTypeName.contains("GATE")) {
            player.sendMessage("§c❌ Можно привязать только к забору, стене или ограде!");
            return;
        }
        event.setCancelled(true);
        Location anchorLoc = event.getClickedBlock().getLocation().add(0.5, 0.5, 0.5);
        Entity anchor;
        try { anchor = event.getClickedBlock().getWorld().spawn(anchorLoc, org.bukkit.entity.LeashHitch.class); }
        catch (Exception e) {
            ArmorStand stand = event.getClickedBlock().getWorld().spawn(anchorLoc, ArmorStand.class);
            stand.setVisible(false); stand.setGravity(false); stand.setCanMove(false);
            stand.setInvulnerable(true); stand.setSilent(true);
            anchor = stand;
        }
        leashEntityTo(pending, anchor);
        pendingLink.remove(player.getUniqueId());
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) heldItem.setAmount(heldItem.getAmount() - 1);
        player.sendMessage("§a✅ §e" + getEntityName(pending) + "§f привязан к блоку");
    }

    // =========================
    // EVENT: ПКМ по сущности с Lead
    // =========================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        Entity target = event.getRightClicked();
        boolean isMainHand = (event.getHand() == EquipmentSlot.HAND);
        ItemStack heldItem = isMainHand ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
        if (heldItem.getType() != Material.LEAD) return;
        event.setCancelled(true);
        if (player.isSneaking()) { handleEntityToEntity(player, target); return; }
        handleLeashToPlayer(player, target, heldItem);
    }

    // =========================
    // ПРИВЯЗКА СУЩНОСТИ К ИГРОКУ
    // =========================
    private void handleLeashToPlayer(Player player, Entity target, ItemStack heldItem) {
        // Toggle: если уже привязан к этому игроку — отвязать
        Set<UUID> holders = getCustomLeashHolders(target);
        if (holders.contains(player.getUniqueId())) {
            unleashEntityFrom(target, player);
            player.sendMessage("§e👝 §fПоводок снят с §e" + getEntityName(target));
            return;
        }
        leashEntityTo(target, player);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) heldItem.setAmount(heldItem.getAmount() - 1);
        player.sendMessage("§a✅ §fСущность привязана: §e" + getEntityName(target));
    }

    // =========================
    // SHIFT+ПКМ: сущность → сущность
    // =========================
    private void handleEntityToEntity(Player player, Entity target) {
        Entity pending = pendingLink.get(player.getUniqueId());
        if (pending == null) {
            pendingLink.put(player.getUniqueId(), target);
            player.sendMessage("§e👝 §fВыбрана сущность: §e" + getEntityName(target));
            player.sendMessage("§7Теперь SHIFT+ПКМ по другой сущности или блоку (забор), чтобы привязать §e" + getEntityName(target) + "§7.");
            return;
        }
        if (!pending.isValid() || pending.isDead()) {
            pendingLink.remove(player.getUniqueId());
            player.sendMessage("§c❌ Выбранная сущность мертва или исчезла! Выберите заново.");
            return;
        }
        if (pending.equals(target)) { player.sendMessage("§c❌ Нельзя привязать сущность к самой себе!"); return; }

        // НЕ отвязываем существующие связи — добавляем новую параллельно
        leashEntityTo(pending, target);
        pendingLink.remove(player.getUniqueId());
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            ItemStack held = player.getInventory().getItemInMainHand().getType() == Material.LEAD
                    ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
            if (held.getType() == Material.LEAD) held.setAmount(held.getAmount() - 1);
        }
        player.sendMessage("§a✅ §e" + getEntityName(pending) + "§f привязан к §e" + getEntityName(target));
    }

    // =========================
    // УНИВЕРСАЛЬНАЯ ПРИВЯЗКА (leashEntityTo)
    // =========================
    private static void leashEntityTo(Entity leashed, Entity holder) {
        UUID lUuid = leashed.getUniqueId();
        UUID hUuid = holder.getUniqueId();

        if (!(leashed instanceof LivingEntity) || leashed instanceof Player) {
            // Создаём прокси для не-LivingEntity и Player
            double yOff = (leashed instanceof Player) ? 1.0 : 0.5;
            Location proxyLoc = leashed.getLocation().add(0, yOff, 0);
            ArmorStand proxy = leashed.getWorld().spawn(proxyLoc, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false); stand.setInvulnerable(true);
                stand.setSilent(true); stand.setCanMove(false);
            });
            proxy.setLeashHolder(holder);
            leashProxies.computeIfAbsent(lUuid, k -> new ConcurrentHashMap<>()).put(hUuid, proxy);
            proxyUuids.add(proxy.getUniqueId());
            addCustomLeash(leashed, holder);
            if (leashed instanceof Player player) {
                immobilizedPlayers.add(player.getUniqueId());
                originalWalkSpeeds.putIfAbsent(player.getUniqueId(), player.getWalkSpeed());
                player.setWalkSpeed(0);
            }
            return;
        }

        // Для LivingEntity (мобы) — ванильный setLeashHolder
        if (leashed instanceof LivingEntity living) {
            if (living.isLeashed()) living.setLeashHolder(null);
            living.setLeashHolder(holder);
        }
        addCustomLeash(leashed, holder);
    }

    // =========================
    // ОТВЯЗКА ОТ КОНКРЕТНОГО ХОЛДЕРА
    // =========================
    private static void unleashEntityFrom(Entity leashed, Entity holder) {
        UUID lUuid = leashed.getUniqueId();
        UUID hUuid = holder.getUniqueId();

        // Удаляем прокси для этой пары
        Map<UUID, ArmorStand> subMap = leashProxies.get(lUuid);
        if (subMap != null) {
            ArmorStand proxy = subMap.remove(hUuid);
            if (proxy != null) { proxyUuids.remove(proxy.getUniqueId()); proxy.setLeashHolder(null); proxy.remove(); }
            if (subMap.isEmpty()) {
                leashProxies.remove(lUuid);
                immobilizedPlayers.remove(lUuid);
                Float origSpeed = originalWalkSpeeds.remove(lUuid);
                if (origSpeed != null && leashed instanceof Player player) player.setWalkSpeed(origSpeed);
            }
        }

        if (leashed instanceof LivingEntity living && living.isLeashed()
                && living.getLeashHolder() != null && living.getLeashHolder().getUniqueId().equals(hUuid)) {
            living.setLeashHolder(null);
        }
        removeCustomLeash(leashed, holder);
    }

    // =========================
    // PLAYER QUIT
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingLink.remove(uuid);
        immobilizedPlayers.remove(uuid);
        Float origSpeed = originalWalkSpeeds.remove(uuid);
        if (origSpeed != null) event.getPlayer().setWalkSpeed(origSpeed);

        // Удаляем все прокси этого игрока (когда он был привязан)
        Map<UUID, ArmorStand> subMap = leashProxies.remove(uuid);
        if (subMap != null) for (ArmorStand p : subMap.values()) { proxyUuids.remove(p.getUniqueId()); p.setLeashHolder(null); p.remove(); }

        // Освобождаем все сущности, привязанные к этому игроку
        Set<UUID> leashedUuids = customLeashReverse.remove(uuid);
        if (leashedUuids != null) {
            for (UUID lUuid : leashedUuids) {
                Set<UUID> holders = customLeashMap.get(lUuid);
                if (holders != null) { holders.remove(uuid); if (holders.isEmpty()) customLeashMap.remove(lUuid); }
                Entity leashed = Bukkit.getEntity(lUuid);
                if (leashed instanceof LivingEntity living && living.isLeashed()) {
                    Entity h = living.getLeashHolder();
                    if (h != null && h.getUniqueId().equals(uuid)) living.setLeashHolder(null);
                }
                Map<UUID, ArmorStand> lSubMap = leashProxies.get(lUuid);
                if (lSubMap != null) {
                    ArmorStand proxy = lSubMap.remove(uuid);
                    if (proxy != null) { proxyUuids.remove(proxy.getUniqueId()); proxy.setLeashHolder(null); proxy.remove(); }
                    if (lSubMap.isEmpty()) leashProxies.remove(lUuid);
                }
            }
        }
    }

    // =========================
    // HELPERS
    // =========================
    private static boolean hasBlockBetween(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance < 0.01) return false;
        direction.normalize();
        RayTraceResult result = from.getWorld().rayTraceBlocks(from, direction, distance, FluidCollisionMode.NEVER, true);
        return result != null && result.getHitBlock() != null;
    }

    private static String getEntityName(Entity entity) {
        if (entity == null) return "§f?";
        if (entity.getCustomName() != null) return entity.getCustomName();
        String typeName = entity.getType().name().toLowerCase().replace("_", " ");
        if (!typeName.isEmpty()) typeName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
        return "§f" + typeName;
    }
}

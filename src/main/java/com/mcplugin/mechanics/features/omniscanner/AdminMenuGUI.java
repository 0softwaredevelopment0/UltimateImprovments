package com.mcplugin.mechanics.features.omniscanner;

import com.mcplugin.core.Main;
import com.mcplugin.core.Keys;
import com.mcplugin.module.ModuleManager;
import com.mcplugin.util.MessageUtil;
import com.mcplugin.util.StatsTracker;
import com.mcplugin.util.ConsoleLogger;
import com.mcplugin.mechanics.features.world.ChunkLoaderItemListener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 🎛 /mp menu — админское GUI с информацией о плагине, статистикой сервера и предметами.
 * <p>
 * Вкладки:
 * - ✦ Информация — версия, авторы, модули, аптайм
 * - 📊 Статистика — TPS, MSPT, RAM, Ping, Онлайн
 * - 🎒 Предметы — все кастомные предметы плагина (можно взять в инвентарь)
 */
public class AdminMenuGUI implements Listener {

    private static final Map<UUID, MenuState> openMenus = new HashMap<>();
    private static boolean registered = false;

    // Layout: 6 строк (54 слота)
    private static final int SLOT_TAB_INFO = 0;
    private static final int SLOT_TAB_STATS = 1;
    private static final int SLOT_TAB_ITEMS = 2;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_NEXT_PAGE = 53;
    private static final int SLOT_CLOSE = 49;
    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 44;

    private static final DecimalFormat DF1 = new DecimalFormat("#.#");
    private static final DecimalFormat DF2 = new DecimalFormat("#.##");

    private static class MenuState {
        String tab = "INFO"; // INFO, STATS, ITEMS
        int page = 0;
    }

    public static void open(Player player) {
        register();
        MenuState state = new MenuState();
        openMenus.put(player.getUniqueId(), state);
        buildGUI(player, state);
    }

    // ========================================================================
    // BUILD GUI
    // ========================================================================

    private static void buildGUI(Player player, MenuState state) {
        Inventory inv = Bukkit.createInventory(null, 54,
                MessageUtil.legacy("<!italic><gradient:#00AAFF:#FF55FF>🎛 MC-Plugin Menu</gradient>"));

        // Верхняя панель
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createDivider());
        }

        // Вкладки
        inv.setItem(SLOT_TAB_INFO, createTabItem(Material.BOOK, "✦ Информация", state.tab.equals("INFO")));
        inv.setItem(SLOT_TAB_STATS, createTabItem(Material.CLOCK, "📊 Статистика", state.tab.equals("STATS")));
        inv.setItem(SLOT_TAB_ITEMS, createTabItem(Material.CHEST, "🎒 Предметы", state.tab.equals("ITEMS")));

        // Разделитель между вкладками и контентом
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, createDivider());
        }

        // Контент вкладки
        switch (state.tab) {
            case "STATS":
                buildStatsContent(inv, player, state);
                break;
            case "ITEMS":
                buildItemsContent(inv, player, state);
                break;
            default:
                buildInfoContent(inv, player, state);
        }

        // Нижняя панель (навигация)
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, createDivider());
        }
        if (state.tab.equals("ITEMS")) {
            inv.setItem(SLOT_PREV_PAGE, createActionItem(Material.ARROW,
                    "<gray>◀ Назад</gray>", state.page > 0 ? "<gray>Страница " + state.page + "</gray>" : "<dark_gray>Нет</dark_gray>"));
            inv.setItem(SLOT_NEXT_PAGE, createActionItem(Material.ARROW,
                    "<gray>Вперёд ▶</gray>", "<gray>Страница " + (state.page + 2) + "</gray>"));
        }
        inv.setItem(SLOT_CLOSE, createActionItem(Material.OAK_DOOR, "<gray>Закрыть</gray>", ""));

        player.openInventory(inv);
    }

    // ========================================================================
    // TAB: INFO
    // ========================================================================

    private static void buildInfoContent(Inventory inv, Player player, MenuState state) {
        // Plugin info
        inv.setItem(18, createInfoItem(Material.NETHER_STAR,
                "<gradient:#00AAFF:#FF55FF>MC-Plugin</gradient>",
                Arrays.asList(
                        "<gray>Версия: <white>" + Main.getInstance().getDescription().getVersion() + "</white></gray>",
                        "<gray>Авторы: <white>" + String.join(", ", Main.getInstance().getDescription().getAuthors()) + "</white></gray>",
                        "<gray>Сервер: <white>" + Main.getInstance().getServer().getName() + " " + Main.getInstance().getServer().getVersion() + "</white></gray>",
                        "<gray>API: <white>" + Main.getInstance().getServer().getBukkitVersion() + "</white></gray>"
                )));

        // Uptime
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long days = uptimeMs / 86400000;
        long hours = (uptimeMs % 86400000) / 3600000;
        long minutes = (uptimeMs % 3600000) / 60000;
        inv.setItem(20, createInfoItem(Material.CLOCK,
                "<gold>Аптайм</gold>",
                Arrays.asList(
                        "<gray>Сервер работает: <white>" + days + "д " + hours + "ч " + minutes + "м</white></gray>"
                )));

        // Online
        inv.setItem(22, createInfoItem(Material.PLAYER_HEAD,
                "<aqua>Онлайн</aqua>",
                Arrays.asList(
                        "<gray>Игроков онлайн: <white>" + Bukkit.getOnlinePlayers().size() + "</white></gray>",
                        "<gray>Макс. игроков: <white>" + Bukkit.getMaxPlayers() + "</white></gray>"
                )));

        // Modules
        StringBuilder modulesOk = new StringBuilder();
        StringBuilder modulesFail = new StringBuilder();
        int ok = 0, fail = 0;
        for (var m : ModuleManager.getInstance().getModules()) {
            if (m.isEnabled()) {
                ok++;
                if (modulesOk.length() > 0) modulesOk.append(", ");
                modulesOk.append("<green>").append(m.getName()).append("</green>");
            } else {
                fail++;
                if (modulesFail.length() > 0) modulesFail.append(", ");
                modulesFail.append("<red>").append(m.getName()).append("</red>");
            }
        }

        inv.setItem(24, createInfoItem(Material.COMMAND_BLOCK,
                "<light_purple>Модули</light_purple>",
                Arrays.asList(
                        "<gray>Всего: <white>" + (ok + fail) + "</white> | OK: <green>" + ok + "</green> | Ошибки: " + (fail > 0 ? "<red>" + fail + "</red>" : "<green>0</green>") + "</gray>",
                        fail > 0 ? "<red>⚠ " + modulesFail.toString() + "</red>" : "<dark_gray>Все модули работают</dark_gray>"
                )));

        // Chunks loaded
        long usedMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMem = Runtime.getRuntime().maxMemory();
        String memStr = formatBytes(usedMem) + " / " + formatBytes(maxMem);

        inv.setItem(26, createInfoItem(Material.CHEST,
                "<yellow>Память</yellow>",
                Arrays.asList(
                        "<gray>Использовано: <white>" + formatBytes(usedMem) + "</white></gray>",
                        "<gray>Всего: <white>" + formatBytes(maxMem) + "</white></gray>"
                )));

        // Worlds
        inv.setItem(28, createInfoItem(Material.GRASS_BLOCK,
                "<green>Миры</green>",
                Bukkit.getWorlds().stream()
                        .map(w -> "<gray>• <white>" + w.getName() + "</white> (" + w.getPlayers().size() + ")</gray>")
                        .collect(Collectors.toList())));
    }

    // ========================================================================
    // TAB: STATS
    // ========================================================================

    private static void buildStatsContent(Inventory inv, Player player, MenuState state) {
        StatsTracker st = StatsTracker.getInstance();
        if (st == null) {
            inv.setItem(22, createInfoItem(Material.BARRIER,
                    "<red>StatsTracker не инициализирован</red>", Collections.emptyList()));
            return;
        }

        // TPS
        double tps = st.getCurrentTps();
        inv.setItem(18, createInfoItem(Material.REDSTONE,
                "<gold>TPS</gold>",
                Arrays.asList(
                        "<gray>Текущий: </gray>" + StatsTracker.tpsColor(tps) + DF2.format(tps) + "</" + StatsTracker.tpsColor(tps).substring(1),
                        "<gray>Средний (1м): <white>" + DF2.format(st.getAvgTps(60)) + "</white></gray>",
                        "<gray>Мин (1м): " + StatsTracker.tpsColor(st.getMinTps(60)) + DF2.format(st.getMinTps(60)) + "</" + StatsTracker.tpsColor(st.getMinTps(60)).substring(1) + "</gray>"
                )));

        // MSPT (как процент от тика 50ms)
        double mspt = st.getCurrentMspt();
        double msptPct = (mspt / 50.0) * 100.0;
        inv.setItem(20, createInfoItem(Material.COMPARATOR,
                "<gold>MSPT</gold>",
                Arrays.asList(
                        "<gray>Текущий: </gray>" + StatsTracker.msptColor(mspt) + DF1.format(msptPct) + "%</" + StatsTracker.msptColor(mspt).substring(1),
                        "<gray>Средний (1м): <white>" + DF1.format((st.getAvgMspt(60) / 50.0) * 100.0) + "%</white></gray>",
                        "<gray>Макс (1м): " + StatsTracker.msptColor(st.getMaxMspt(60)) + DF1.format((st.getMaxMspt(60) / 50.0) * 100.0) + "%</" + StatsTracker.msptColor(st.getMaxMspt(60)).substring(1) + "</gray>"
                )));

        // RAM
        double ram = st.getCurrentRam();
        inv.setItem(22, createInfoItem(Material.CLOCK,
                "<gold>RAM</gold>",
                Arrays.asList(
                        "<gray>Текущая: </gray>" + StatsTracker.ramColor(ram) + DF1.format(ram) + "%</" + StatsTracker.ramColor(ram).substring(1),
                        "<gray>Средняя (1м): <white>" + DF1.format(st.getAvgRam(60)) + "%</white></gray>",
                        "<gray>Макс (1м): " + StatsTracker.ramColor(st.getMaxRam(60)) + DF1.format(st.getMaxRam(60)) + "%</" + StatsTracker.ramColor(st.getMaxRam(60)).substring(1) + "</gray>"
                )));

        // Ping
        double ping = st.getCurrentPing();
        inv.setItem(24, createInfoItem(Material.ENDER_PEARL,
                "<gold>Ping</gold>",
                Arrays.asList(
                        "<gray>Текущий: </gray>" + StatsTracker.pingColor(ping) + DF1.format(ping) + "ms</" + StatsTracker.pingColor(ping).substring(1),
                        "<gray>Мин (1м): " + StatsTracker.pingColor(st.getMinPing(60)) + DF1.format(st.getMinPing(60)) + "ms</" + StatsTracker.pingColor(st.getMinPing(60)).substring(1) + "</gray>",
                        "<gray>Макс (1м): " + StatsTracker.pingColor(st.getMaxPing(60)) + DF1.format(st.getMaxPing(60)) + "ms</" + StatsTracker.pingColor(st.getMaxPing(60)).substring(1) + "</gray>"
                )));

        // Online
        int online = st.getCurrentOnline();
        inv.setItem(26, createInfoItem(Material.PLAYER_HEAD,
                "<gold>Онлайн</gold>",
                Arrays.asList(
                        "<gray>Сейчас: <white>" + online + "</white></gray>",
                        "<gray>Мин (1м): <white>" + st.getMinOnline(60) + "</white></gray>",
                        "<gray>Макс (1м): <white>" + st.getMaxOnline(60) + "</white></gray>"
                )));

        // Performance bar
        int barSegments = 20;
        double health = (tps / 20.0) * 100.0;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barSegments; i++) {
            double pct = (double) (i + 1) / barSegments;
            if (pct <= health / 100.0) {
                if (tps >= 18.0) bar.append("<green>|</green>");
                else if (tps >= 15.0) bar.append("<yellow>|</yellow>");
                else bar.append("<red>|</red>");
            } else {
                bar.append("<dark_gray>|</dark_gray>");
            }
        }

        inv.setItem(28, createInfoItem(Material.GOLD_BLOCK,
                "<gold>Производительность</gold>",
                Arrays.asList(
                        bar.toString(),
                        "<gray>TPS: " + StatsTracker.tpsColor(tps) + DF2.format(tps) + "</" + StatsTracker.tpsColor(tps).substring(1) +
                                " | MSPT: " + StatsTracker.msptColor(mspt) + DF1.format(msptPct) + "%</" + StatsTracker.msptColor(mspt).substring(1),
                        "<gray>RAM: " + StatsTracker.ramColor(ram) + DF1.format(ram) + "%</" + StatsTracker.ramColor(ram).substring(1) +
                                " | Ping: " + StatsTracker.pingColor(ping) + DF1.format(ping) + "ms</" + StatsTracker.pingColor(ping).substring(1)
                )));
    }

    // ========================================================================
    // TAB: ITEMS
    // ========================================================================

    private static final List<ItemStack> CUSTOM_ITEMS = new ArrayList<>();

    static {
        initCustomItems();
    }

    private static void initCustomItems() {
        // 1. Omniscanner
        CUSTOM_ITEMS.add(OmniscannerManager.createItem());

        // 2. Plasma Cannon
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.WARPED_FUNGUS_ON_A_STICK,
                "<white>Photon Cannon *</white>",
                "<gray>Strange gun shoots with echo shards.</gray>"), Keys.PLASMA));

        // 3. Shoker
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.WARPED_FUNGUS_ON_A_STICK,
                "<aqua>Electro Shoker *</aqua>",
                "<gray>Stuns enemies with electricity.</gray>"), Keys.SHOCKER));

        // 4. Multimeter
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.CLOCK,
                "<white>Multimeter *</white>",
                "<gray>Inspect energy nodes and their connections.</gray>"), Keys.MULTIMETER));

        // 5. Metal Detector
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.STICK,
                "<white>Metal Detector *</white>",
                "<gray>Scans for metal blocks and items nearby.</gray>"), Keys.METAL_DETECTOR));

        // 6. Ore Finder
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.COMPASS,
                "<white>Ore Finder *</white>",
                "<gray>Scans chunk for ores.</gray>"), Keys.ORE_FINDER));

        // 7. Mob Finder
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.COMPASS,
                "<white>Mob Finder *</white>",
                "<gray>Scans chunk for entities.</gray>"), Keys.MOB_FINDER));

        // 8. Health Meter
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.COMPASS,
                "<white>Health Meter *</white>",
                "<gray>Check entity health.</gray>"), Keys.HEALTH_METER));

        // 9. Portable Radar
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.COMPASS,
                "<white>Portable Radar *</white>",
                "<gray>Find nearby entities.</gray>"), Keys.RADAR));

        // 10. Lead Ingot
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.IRON_INGOT,
                "<gray>Lead Ingot *</gray>",
                "<gray>Radiation shielding material.</gray>"), Keys.LEAD_INGOT));

        // 11. Lead Shield
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.SHIELD,
                "<gray>Lead Shield *</gray>",
                "<gray>Protects from radiation.</gray>"), Keys.LEAD_SHIELD));

        // 12. Concrete Bucket
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.BUCKET,
                "<white>Concrete Bucket *</white>",
                "<gray>Place instant concrete.</gray>"), Keys.CONCRETE_BUCKET));

        // 13. Portable Ender Chest (без PDC — работает по типу блока ENDER_CHEST)
        ItemStack echest = new ItemStack(Material.ENDER_CHEST);
        ItemMeta echestMeta = echest.getItemMeta();
        if (echestMeta != null) {
            echestMeta.displayName(MessageUtil.parse("<!italic><white>Портативное хранилище</white>"));
            echestMeta.lore(List.of(MessageUtil.parse("<!italic><gray>Поставьте и сломайте чтобы прочитать описание.</gray>")));
            echest.setItemMeta(echestMeta);
        }
        CUSTOM_ITEMS.add(echest);

        // 14. Chunk Loader
        ItemStack cl = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta clMeta = cl.getItemMeta();
        if (clMeta != null) {
            clMeta.displayName(MessageUtil.parse("<!italic><gold>✦ Чанклоадер ✦</gold>"));
            clMeta.lore(List.of(
                    MessageUtil.parse("<!italic><gray>При установке чанк остается загруженным</gray>"),
                    MessageUtil.parse("<!italic><gray>Разрушить — получить предмет обратно</gray>")
            ));
            clMeta.getPersistentDataContainer().set(ChunkLoaderItemListener.getChunkLoaderKey(), PersistentDataType.BYTE, (byte) 1);
            cl.setItemMeta(clMeta);
        }
        CUSTOM_ITEMS.add(cl);

        // 15. Entity Locator
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.COMPASS,
                "<white>Entity Locator *</white>",
                "<gray>Points to nearest entity.</gray>"), Keys.LOCATOR));

        // 16. Antimatter
        CUSTOM_ITEMS.add(tagPdc(createNamedItem(Material.NETHER_STAR,
                "<light_purple>Antimatter *</light_purple>",
                "<gray>Dangerous substance.</gray>"), Keys.ANTIMATTER));

        // 17. Particle Ring (без PDC — блок-плейсер, определение по типу)
        CUSTOM_ITEMS.add(createNamedItem(Material.CHISELED_TUFF,
                "<white>Particle Ring *</white>",
                "<gray>Guides particles along the accelerator path.</gray>"));

        // 18. Particle Engine (без PDC — блок-плейсер)
        CUSTOM_ITEMS.add(createNamedItem(Material.TUFF_BRICKS,
                "<white>Particle Engine *</white>",
                "<gray>Accelerates particles. Requires 500⚡ buffer.</gray>"));

        // 19. Particle Speed Sensor (без PDC — блок-плейсер)
        CUSTOM_ITEMS.add(createNamedItem(Material.POLISHED_DIORITE,
                "<white>Particle Speed Sensor *</white>",
                "<gray>Measures particle speed (0-99.999% light speed).</gray>"));

        // 20. Particle Injector (без PDC — блок-плейсер)
        CUSTOM_ITEMS.add(createNamedItem(Material.REINFORCED_DEEPSLATE,
                "<white>Particle Injector *</white>",
                "<gray>Right-click with any item to inject it as a particle.</gray>"));

        // 21. Netherite Upgraded Sword (пример улучшенного незеритового)
        ItemStack netheriteSword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta nsMeta = netheriteSword.getItemMeta();
        if (nsMeta != null) {
            nsMeta.displayName(MessageUtil.parse("<!italic><gradient:#8B4513:#DAA520>✦ Незеритовый меч ✦</gradient>"));
            nsMeta.lore(List.of(
                    MessageUtil.parse("<!italic><gradient:#8B4513:#DAA520>✦ Незерит — ⚔ 10.0 урона</gradient>"),
                    MessageUtil.parse("<!italic><gray>Пример улучшенного предмета</gray>")
            ));
            netheriteSword.setItemMeta(nsMeta);
        }
        CUSTOM_ITEMS.add(netheriteSword);

        // 22. Elytra Chestplate
        ItemStack elytraChest = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta ecMeta = elytraChest.getItemMeta();
        if (ecMeta != null) {
            ecMeta.displayName(MessageUtil.parse("<!italic><gradient:#00AAFF:#FF55FF>✦ Нагрудник полёта ✦</gradient>"));
            ecMeta.setGlider(true);
            ecMeta.getPersistentDataContainer().set(Keys.CHESTPLATE_FLIGHT, PersistentDataType.DOUBLE, 100.0);
            ecMeta.lore(List.of(
                    MessageUtil.parse("<!italic><green>Пригоден для полёта</green>"),
                    MessageUtil.parse("<!italic><gray>Пример улучшенного предмета</gray>")
            ));
            elytraChest.setItemMeta(ecMeta);
        }
        CUSTOM_ITEMS.add(elytraChest);

        // 23. Totem with charges
        ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta totemMeta = totem.getItemMeta();
        if (totemMeta != null) {
            totemMeta.displayName(MessageUtil.parse("<!italic><gold>✦ Тотем ✦</gold>"));
            var pdc = totemMeta.getPersistentDataContainer();
            pdc.set(Keys.TOTEM_CHARGE, PersistentDataType.INTEGER, 5);
            totemMeta.lore(List.of(
                    MessageUtil.parse("<!italic><white>Charge: <gray>5</gray></white>")
            ));
            totem.setItemMeta(totemMeta);
        }
        CUSTOM_ITEMS.add(totem);
    }

    private static ItemStack createNamedItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MessageUtil.parse("<!italic>" + name));
        meta.lore(List.of(MessageUtil.parse("<!italic>" + lore)));
        item.setItemMeta(meta);
        return item;
    }

    /** Помечает предмет PDC ключом PersistentDataType.BYTE (1). */
    private static ItemStack tagPdc(ItemStack item, NamespacedKey key) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void buildItemsContent(Inventory inv, Player player, MenuState state) {
        int itemsPerPage = CONTENT_END - CONTENT_START + 1;
        int totalPages = Math.max(1, (int) Math.ceil((double) CUSTOM_ITEMS.size() / itemsPerPage));
        state.page = Math.max(0, Math.min(state.page, totalPages - 1));

        int startIdx = state.page * itemsPerPage;
        int slot = CONTENT_START;

        for (int i = startIdx; i < CUSTOM_ITEMS.size() && slot <= CONTENT_END; i++) {
            ItemStack customItem = CUSTOM_ITEMS.get(i).clone();
            ItemMeta meta = customItem.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(MessageUtil.parse("<!italic><green>ПКМ — взять в инвентарь</green>"));
                meta.lore(lore);
                customItem.setItemMeta(meta);
            }
            inv.setItem(slot, customItem);
            slot++;
        }

        // Fill remaining slots with glass
        while (slot <= CONTENT_END) {
            inv.setItem(slot, createDivider());
            slot++;
        }

        // Page info
        inv.setItem(49, createActionItem(Material.BOOK,
                "<gray>Страница " + (state.page + 1) + "/" + totalPages + "</gray>",
                "<gray>Всего предметов: " + CUSTOM_ITEMS.size() + "</gray>"));
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private static ItemStack createTabItem(Material material, String name, boolean active) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String color = active ? "<gold>" : "<gray>";
        meta.displayName(MessageUtil.parse("<!italic>" + color + name + (active ? " <dark_gray>◄</dark_gray>" : "")));
        if (active) meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createInfoItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageUtil.parse("<!italic>" + name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(MessageUtil.parse("<!italic>" + line));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createActionItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(MessageUtil.parse("<!italic>" + name));
        if (!lore.isEmpty()) {
            meta.lore(List.of(MessageUtil.parse("<!italic>" + lore)));
        }
        meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createDivider() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            meta.getPersistentDataContainer().set(Keys.GUI_PROTECTED, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // ========================================================================
    // LISTENER
    // ========================================================================

    // ========================================================================
    // 🛡 DRAG HANDLER — не даём перетаскивать предметы в GUI
    // ========================================================================

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!openMenus.containsKey(uuid)) return;

        for (int slot : e.getRawSlots()) {
            if (slot < 54) {
                e.setCancelled(true);
                player.setItemOnCursor(null);
                player.updateInventory();
                return;
            }
        }
    }

    // ========================================================================
    // 🛡 CLICK HANDLER — блокируем все клики, чистим курсор
    // ========================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        MenuState state = openMenus.get(uuid);
        if (state == null) return;

        // 🛡 Блокируем ВСЕ клики + чистим курсор + форсируем синхронизацию
        e.setCancelled(true);
        player.setItemOnCursor(null);
        player.updateInventory();

        // Обрабатываем только клики в верхнем инвентаре
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        ItemStack clicked = e.getCurrentItem();

        // PDC-защита: если предмет защищён — блокируем взятие,
        // но разрешаем прописанные ниже действия (табы, навигация)
        boolean isProtectedItem = clicked != null && clicked.hasItemMeta()
                && clicked.getItemMeta().getPersistentDataContainer()
                        .has(Keys.GUI_PROTECTED, PersistentDataType.BYTE);

        // Вкладки — только ЛКМ
        if (slot == SLOT_TAB_INFO && e.isLeftClick()) { state.tab = "INFO"; state.page = 0; buildGUI(player, state); return; }
        if (slot == SLOT_TAB_STATS && e.isLeftClick()) { state.tab = "STATS"; state.page = 0; buildGUI(player, state); return; }
        if (slot == SLOT_TAB_ITEMS && e.isLeftClick()) { state.tab = "ITEMS"; state.page = 0; buildGUI(player, state); return; }

        // Навигация (только для вкладки предметов)
        if (state.tab.equals("ITEMS")) {
            int itemsPerPage = CONTENT_END - CONTENT_START + 1;
            int totalPages = Math.max(1, (int) Math.ceil((double) CUSTOM_ITEMS.size() / itemsPerPage));

            // Навигация — только ЛКМ
            if (slot == SLOT_PREV_PAGE && e.isLeftClick() && state.page > 0) {
                state.page--;
                buildGUI(player, state);
                return;
            }
            if (slot == SLOT_NEXT_PAGE && e.isLeftClick() && state.page < totalPages - 1) {
                state.page++;
                buildGUI(player, state);
                return;
            }

            // Клик по предмету — только ЛКМ и только НЕ защищённый, дать игроку
            if (slot >= CONTENT_START && slot <= CONTENT_END && e.isLeftClick() && !isProtectedItem) {
                if (clicked != null && clicked.getType() != Material.BLACK_STAINED_GLASS_PANE) {
                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(clicked.clone());
                    if (!leftover.isEmpty()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover.get(0));
                    }
                    player.sendMessage(MessageUtil.parse(
                            "<green>✔ Получен предмет: </green><white>" +
                                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                            .serialize(clicked.getItemMeta().displayName()) + "</white>"));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
                }
                return;
            }
        }

        // Закрыть — только ЛКМ
        if (slot == SLOT_CLOSE && e.isLeftClick()) {
            player.closeInventory();
            openMenus.remove(uuid);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        openMenus.remove(e.getPlayer().getUniqueId());
    }

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    public static void register() {
        if (registered) return;
        registered = true;
        Bukkit.getPluginManager().registerEvents(new AdminMenuGUI(), Main.getInstance());
        ConsoleLogger.info("[AdminMenu] Menu GUI registered with " + CUSTOM_ITEMS.size() + " custom items.");
    }
}

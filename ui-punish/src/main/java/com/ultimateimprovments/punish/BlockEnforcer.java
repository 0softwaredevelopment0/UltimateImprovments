package com.ultimateimprovments.punish;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BlockEnforcer — server-side enforcement of the {@link PunishmentManager.PunishType#BLOCK}
 * punishment.
 * <p>
 * A block is a <b>soft</b> punishment: the player is never kicked, they stay online
 * behind a modal {@link BlockScreen} dialog and cannot play:
 * <ul>
 *   <li>the dialog is shown on join (the record is read from the database);</li>
 *   <li>the dialog is re-sent (refreshed) every {@code punish.block.reopen_interval_ticks}
 *       ticks — 20 ticks (1 s) by default — and immediately on any attempt to move,
 *       interact, use an inventory, chat or run a command;</li>
 *   <li>the Close button and ESC do not lift the block: they fire the
 *       {@link BlockScreen#BLOCK_CLOSE_ID} custom action, the server re-checks the
 *       record and either shows «not expired yet» + re-opens the dialog or releases
 *       the player when the block is really over;</li>
 *   <li>movement is blocked: every positional change is reverted at the event level,
 *       so the player cannot walk away even with the dialog dismissed by a modified client.</li>
 * </ul>
 * Block records live in the {@code punishments} table (type {@code block}), so the
 * punishment survives restarts, reconnects and sessions. It ends only when it expires
 * or an admin runs {@code /ui punish unblock <player>}.
 */
public final class BlockEnforcer implements Listener {

    /** Players currently locked behind a block dialog: player UUID → active block record. */
    private static final Map<UUID, PunishmentManager.PunishmentRecord> blocked = new ConcurrentHashMap<>();

    /** Last time (ms) the dialog was sent to each blocked player — throttles re-sends. */
    private static final Map<UUID, Long> lastShown = new ConcurrentHashMap<>();

    /** Minimum gap between two dialog re-sends triggered by events (anti-spam). */
    private static final long EVENT_RESEND_THROTTLE_MS = 500L;

    /** Fallback re-open interval when the config value is missing/0. */
    private static final long DEFAULT_REOPEN_INTERVAL_MS = 1000L;

    /** Period of the housekeeping task (ticks): reopening + expiry checks. */
    private static final long TASK_PERIOD_TICKS = 20L;

    private static BukkitTask task;
    /** The registered listener instance — kept to avoid duplicate registration on soft reload. */
    private static BlockEnforcer listener;

    private BlockEnforcer() {}

    // =========================
    // LIFECYCLE
    // =========================

    /**
     * Registers the listeners and the housekeeping task, and applies pending blocks
     * to players who are already online (soft reload / plugin enable order).
     */
    public static void init(Main plugin) {
        shutdown();
        listener = new BlockEnforcer();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        long period = taskPeriodTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, BlockEnforcer::tick, period, period);
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    /**
     * Period of the housekeeping task: the dialog refresh interval (20 ticks = 1 s by
     * default), clamped to at least one second so expiry checks stay cheap.
     */
    private static long taskPeriodTicks() {
        int ticks = reopenIntervalTicks();
        return ticks <= 0 ? TASK_PERIOD_TICKS : Math.max(TASK_PERIOD_TICKS, ticks);
    }

    /**
     * Cancels the task, closes any open block dialog and forgets the cached state.
     * Called on disable/soft reload — the block itself stays in the database and is
     * re-applied on the next join or {@link #init(Main)}.
     */
    public static void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (listener != null) {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            listener = null;
        }
        for (UUID id : blocked.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) BlockScreen.close(player);
        }
        blocked.clear();
        lastShown.clear();
    }

    // =========================
    // PUBLIC API (used by /ui punish block|unblock)
    // =========================

    /**
     * Reads the player's active block from the database and applies or lifts the
     * dialog accordingly. Safe to call at any time (join, command, IP/HW match).
     */
    public static void refresh(Player player) {
        String ip = player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "";
        String hwId = PunishmentManager.computeHwId(ip, player.getName());

        PunishmentManager.PunishmentRecord block = PunishmentManager.getActivePunishment(
                PunishmentManager.PunishType.BLOCK,
                player.getUniqueId().toString(), player.getName(), ip, hwId);

        if (block == null) {
            blocked.remove(player.getUniqueId());
            lastShown.remove(player.getUniqueId());
            return;
        }
        if (block.isExpired()) {
            PunishmentManager.unpunishById(block.id);
            blocked.remove(player.getUniqueId());
            lastShown.remove(player.getUniqueId());
            return;
        }
        blocked.put(player.getUniqueId(), block);
        show(player, block, System.currentTimeMillis(), true);
    }

    /**
     * Applies a freshly created block record to an online player.
     */
    public static void apply(Player player, PunishmentManager.PunishmentRecord block) {
        blocked.put(player.getUniqueId(), block);
        lastShown.remove(player.getUniqueId());
        show(player, block, System.currentTimeMillis(), true);
    }

    /**
     * Lifts the block from a player: closes the dialog and forgets the cached record.
     *
     * @param notify whether to tell the player they are free again
     */
    public static void release(Player player, boolean notify) {
        boolean wasBlocked = blocked.remove(player.getUniqueId()) != null;
        lastShown.remove(player.getUniqueId());
        if (!wasBlocked) return;
        BlockScreen.close(player);
        if (notify) {
            player.sendMessage(MessageUtil.parse(
                    "<green>✔</green> <white>You have been unblocked.</white>"));
        }
    }

    /** @return whether the player is currently locked behind a block. */
    public static boolean isBlocked(Player player) {
        return blocked.containsKey(player.getUniqueId());
    }

    /**
     * Releases every player whose enforced block RECORD was just deactivated in the
     * database (used by {@code /ui punish pardon}): the dialog is closed and the
     * player is told the block is gone. Keyed by record ID rather than by player, so
     * everyone locked through an IP/HW-scoped record is released too.
     *
     * @param recordIds punishment row IDs that were lifted
     * @return how many players were released
     */
    public static int releaseByRecordIds(java.util.Collection<Integer> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) return 0;
        int released = 0;
        for (Map.Entry<UUID, PunishmentManager.PunishmentRecord> entry : blocked.entrySet()) {
            PunishmentManager.PunishmentRecord record = entry.getValue();
            if (record == null || !recordIds.contains(record.id)) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                release(player, true);
            } else {
                blocked.remove(entry.getKey());
                lastShown.remove(entry.getKey());
            }
            released++;
        }
        return released;
    }

    // =========================
    // PERIODIC TASK (re-open + expiry)
    // =========================

    private static void tick() {
        if (blocked.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean refreshDialog = reopenIntervalTicks() > 0;
        for (Map.Entry<UUID, PunishmentManager.PunishmentRecord> entry : blocked.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            PunishmentManager.PunishmentRecord record = entry.getValue();

            // Expired on its own — deactivate the DB row and let the player go.
            if (record.expiresAt > 0 && now >= record.expiresAt) {
                PunishmentManager.unpunishById(record.id);
                release(player, true);
                continue;
            }
            // Periodic refresh: re-send the dialog so a dismissed one pops back up
            // (and the remaining time in it stays current).
            if (refreshDialog) show(player, record, now, true);
        }
    }

    // =========================
    // DIALOG SENDING
    // =========================

    /**
     * Sends the block dialog to the player, throttled unless {@code force} is set.
     */
    private static void show(Player player, PunishmentManager.PunishmentRecord record,
                             long now, boolean force) {
        if (!player.isOnline()) return;
        Long last = lastShown.get(player.getUniqueId());
        long reopensAfter = reopenIntervalMs();
        if (!force && last != null && now - last < reopensAfter) return;
        lastShown.put(player.getUniqueId(), now);
        BlockScreen.open(player, record);
    }

    /** Re-open interval from {@code punish.block.reopen_interval_ticks}. */
    private static long reopenIntervalMs() {
        int ticks = reopenIntervalTicks();
        if (ticks <= 0) {
            // 0 disables the periodic re-open; keep the event throttle sensible anyway.
            return Math.max(EVENT_RESEND_THROTTLE_MS, DEFAULT_REOPEN_INTERVAL_MS);
        }
        return Math.max(EVENT_RESEND_THROTTLE_MS, ticks * 50L);
    }

    /** {@code punish.block.reopen_interval_ticks} (default 20 ticks = 1 s; 0 = off). */
    private static int reopenIntervalTicks() {
        Main main = Main.getInstance();
        return main != null
                ? main.getConfig().getInt("punish.block.reopen_interval_ticks", 20)
                : 20;
    }

    // =========================
    // JOIN / QUIT
    // =========================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        refresh(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        // The dialog disappears with the client; the DB record keeps the block alive.
        blocked.remove(e.getPlayer().getUniqueId());
        lastShown.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        // Show the dialog again once the respawn screen is gone.
        Player player = e.getPlayer();
        if (!isBlocked(player)) return;
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            PunishmentManager.PunishmentRecord record = blocked.get(player.getUniqueId());
            if (record != null && player.isOnline()) {
                show(player, record, System.currentTimeMillis(), true);
            }
        });
    }

    // =========================
    // MOVEMENT LOCK
    // =========================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent e) {
        PunishmentManager.PunishmentRecord record = blocked.get(e.getPlayer().getUniqueId());
        if (record == null) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        if (!moved) return; // looking around is allowed

        // Snap the position back but keep the client's new view direction.
        Location snap = from.clone();
        snap.setYaw(to.getYaw());
        snap.setPitch(to.getPitch());
        e.setTo(snap);
        show(e.getPlayer(), record, System.currentTimeMillis(), false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!blocked.containsKey(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        show(e.getPlayer(), blocked.get(e.getPlayer().getUniqueId()),
                System.currentTimeMillis(), false);
    }

    // =========================
    // ACTION LOCK
    // =========================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        blockAction(e.getPlayer(), () -> e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        blockAction(e.getPlayer(), () -> e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropItem(PlayerDropItemEvent e) {
        blockAction(e.getPlayer(), () -> e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        blockAction(e.getPlayer(), () -> e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        blockAction(player, () -> e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        blockAction(player, () -> e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        blockAction(e.getPlayer(), () -> e.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        if (!blocked.containsKey(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            PunishmentManager.PunishmentRecord record = blocked.get(player.getUniqueId());
            if (record != null && player.isOnline()) {
                show(player, record, System.currentTimeMillis(), false);
            }
        });
    }

    // =========================
    // CLOSE BUTTON / ESC — server-side check
    // =========================

    /**
     * Close button and ESC both fire {@link BlockScreen#BLOCK_CLOSE_ID}. The block is
     * lifted only when the record is really gone/expired; otherwise the player gets a
     * chat hint and the dialog is re-opened right away.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!BlockScreen.BLOCK_CLOSE_KEY.equals(event.getIdentifier())) return;
        Player player = playerOf(event);
        if (player == null) return;
        if (!blocked.containsKey(player.getUniqueId())) return;
        // Let the client finish closing the window before re-opening it.
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> handleClose(player));
    }

    /** Resolves the Bukkit player behind a {@link PlayerCustomClickEvent}. */
    private static Player playerOf(PlayerCustomClickEvent event) {
        if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
            return gameConn.getPlayer();
        }
        return null;
    }

    /** Checks whether the block is still active and reacts to the Close/ESC press. */
    private static void handleClose(Player player) {
        if (!player.isOnline()) return;
        PunishmentManager.PunishmentRecord cached = blocked.get(player.getUniqueId());

        // Always re-read the database: the block may have been lifted elsewhere.
        String ip = player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "";
        String hwId = PunishmentManager.computeHwId(ip, player.getName());
        PunishmentManager.PunishmentRecord fresh = PunishmentManager.getActivePunishment(
                PunishmentManager.PunishType.BLOCK,
                player.getUniqueId().toString(), player.getName(), ip, hwId);

        if (fresh == null || fresh.isExpired()) {
            if (cached != null) PunishmentManager.unpunishById(cached.id);
            if (fresh != null) PunishmentManager.unpunishById(fresh.id);
            release(player, true);
            return;
        }

        blocked.put(player.getUniqueId(), fresh);
        String duration = PunishmentMessages.durationLabel(fresh.expiresAt);
        player.sendMessage(PunishmentMessages.buildBlockStillActive(duration));
        show(player, fresh, System.currentTimeMillis(), true);
    }

    /** Cancels a world action for a blocked player and re-opens the dialog. */
    private static void blockAction(Player player, Runnable cancel) {
        PunishmentManager.PunishmentRecord record = blocked.get(player.getUniqueId());
        if (record == null) return;
        cancel.run();
        show(player, record, System.currentTimeMillis(), false);
    }
}

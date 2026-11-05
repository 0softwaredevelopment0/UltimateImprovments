package com.ultimateimprovments.core;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the default values in {@link Permissions}: a {@code PermissionDefault.TRUE}
 * on a state-changing command is a privilege-escalation bug (players get an admin
 * action out of the box), so the open set must stay allow-listed and explicit.
 * <p>
 * {@link Permissions#buildPermissions()} is pure data — no Bukkit server access — which
 * is why this can run as a plain unit test.
 */
class PermissionDefaultsTest {

    /** Nodes that are intentionally open to every player. Anything else must be FALSE/OP. */
    private static final Set<String> PLAYER_FACING = Set.of(
            Permissions.CMD_HELP,
            Permissions.CMD_BOSSBAR,
            Permissions.CMD_SCOREBOARD,
            Permissions.CMD_SCOREBOARDNICK,
            Permissions.SCOREBOARDNICK_TOGGLE,
            Permissions.CMD_PINGSOUND,
            Permissions.CMD_SHAREPOS,
            Permissions.CMD_TURRET,
            Permissions.CMD_OPSELF,
            Permissions.CMD_CLAN);

    private static Map<String, PermissionDefault> defaultsByName() {
        return Permissions.buildPermissions().stream()
                .collect(Collectors.toMap(Permission::getName, Permission::getDefault, (first, second) -> second));
    }

    @Test
    void onlyPlayerFacingNodesAreOpenToEveryone() {
        List<String> unexpected = defaultsByName().entrySet().stream()
                .filter(entry -> entry.getValue() == PermissionDefault.TRUE)
                .map(Map.Entry::getKey)
                .filter(name -> !PLAYER_FACING.contains(name))
                .sorted()
                .toList();

        assertTrue(unexpected.isEmpty(),
                "Permissions open to every player must be allow-listed in PLAYER_FACING; unexpected: " + unexpected);
    }

    @Test
    void spaceDimensionIsOpOnly() {
        assertEquals(PermissionDefault.OP, defaultsByName().get(Permissions.CMD_SPACE),
                "/ui space must not be usable by every player by default");
    }

    /**
     * The troll commands are not cosmetic any more: {@code grant_real_op} can hand
     * out real OP and {@code real_shutdown} can take the server down, so they must
     * never be usable out of the box. Services still work through a permissions
     * plugin — the nodes just default to OP.
     */
    @Test
    void trollCommandsAreOpOnly() {
        Map<String, PermissionDefault> defaults = defaultsByName();
        assertEquals(PermissionDefault.OP, defaults.get(Permissions.CMD_FORCEOP),
                "/forceop must be OP-only by default");
        assertEquals(PermissionDefault.OP, defaults.get(Permissions.CMD_CRASH),
                "/crash must be OP-only by default");
    }

    @Test
    void pluginManagementNodesAreClosed() {
        Map<String, PermissionDefault> defaults = defaultsByName();
        for (String node : List.of(
                Permissions.CMD_PLUGIN,
                Permissions.CMD_PLUGINS,
                Permissions.CMD_UPDATEJAR,
                Permissions.CMD_SWAPJAR,
                Permissions.CMD_RELOAD,
                Permissions.CMD_ADDONS,
                Permissions.CMD_CONFIG_REGEN,
                Permissions.CMD_CONFIG_RESET)) {
            assertEquals(PermissionDefault.FALSE, defaults.get(node), node + " must stay FALSE by default");
        }
    }

    @Test
    void authAdminNodesAreClosed() {
        Map<String, PermissionDefault> defaults = defaultsByName();
        for (String node : List.of(
                Permissions.CMD_AUTH_FORCELOGIN,
                Permissions.CMD_AUTH_RESETAUTH,
                Permissions.CMD_AUTH_DELSESSION,
                Permissions.CMD_AUTH_CHGPASS)) {
            assertEquals(PermissionDefault.FALSE, defaults.get(node), node + " must stay FALSE by default");
        }
    }
}

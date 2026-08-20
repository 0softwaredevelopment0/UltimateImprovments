package com.ultimateimprovments.command.clan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Table-driven tests for the clan role hierarchy
 * (member → moderator → organizer → leader).
 */
class ClanRolesTest {

    @Test
    @DisplayName("roleWeight assigns 0..3 in hierarchy order")
    void roleWeight() {
        assertEquals(0, ClanRoles.roleWeight(ClanRoles.ROLE_MEMBER));
        assertEquals(1, ClanRoles.roleWeight(ClanRoles.ROLE_MODERATOR));
        assertEquals(2, ClanRoles.roleWeight(ClanRoles.ROLE_ORGANIZER));
        assertEquals(3, ClanRoles.roleWeight(ClanRoles.ROLE_LEADER));
    }

    @Test
    @DisplayName("roleWeight treats null/unknown as member (0)")
    void roleWeightUnknown() {
        assertEquals(0, ClanRoles.roleWeight(null));
        assertEquals(0, ClanRoles.roleWeight(""));
        assertEquals(0, ClanRoles.roleWeight("admin"));
    }

    @Test
    @DisplayName("hasRole — role is at least the given weight")
    void hasRole() {
        assertTrue(ClanRoles.hasRole(ClanRoles.ROLE_LEADER, ClanRoles.W_LEADER));
        assertTrue(ClanRoles.hasRole(ClanRoles.ROLE_ORGANIZER, ClanRoles.W_MODERATOR));
        assertTrue(ClanRoles.hasRole(ClanRoles.ROLE_MODERATOR, ClanRoles.W_MEMBER));
        assertFalse(ClanRoles.hasRole(ClanRoles.ROLE_MEMBER, ClanRoles.W_MODERATOR));
        assertFalse(ClanRoles.hasRole(ClanRoles.ROLE_ORGANIZER, ClanRoles.W_LEADER));
    }

    @Test
    @DisplayName("isLeader — only the leader role")
    void isLeader() {
        assertTrue(ClanRoles.isLeader(ClanRoles.ROLE_LEADER));
        assertFalse(ClanRoles.isLeader(ClanRoles.ROLE_ORGANIZER));
        assertFalse(ClanRoles.isLeader(null));
    }

    @Test
    @DisplayName("canKick — actor must be strictly above target")
    void canKickMatrix() {
        String[] roles = {
                ClanRoles.ROLE_MEMBER, ClanRoles.ROLE_MODERATOR,
                ClanRoles.ROLE_ORGANIZER, ClanRoles.ROLE_LEADER
        };
        // expected[i][j] = can actor roles[i] kick target roles[j]
        boolean[][] expected = {
                //  member  mod   org   leader
                {false, false, false, false}, // member
                {true,  false, false, false}, // moderator
                {true,  true,  false, false}, // organizer
                {true,  true,  true,  false}, // leader
        };
        for (int i = 0; i < roles.length; i++) {
            for (int j = 0; j < roles.length; j++) {
                assertEquals(expected[i][j], ClanRoles.canKick(roles[i], roles[j]),
                        "canKick(" + roles[i] + ", " + roles[j] + ")");
            }
        }
    }

    @Test
    @DisplayName("canKick — leader cannot be kicked by anyone, even leader")
    void canKickLeaderProtected() {
        assertFalse(ClanRoles.canKick(ClanRoles.ROLE_LEADER, ClanRoles.ROLE_LEADER));
        assertFalse(ClanRoles.canKick(ClanRoles.ROLE_ORGANIZER, ClanRoles.ROLE_LEADER));
    }

    @Test
    @DisplayName("canGrantRole — only roles strictly below the actor")
    void canGrantRoleMatrix() {
        String[] roles = {
                ClanRoles.ROLE_MEMBER, ClanRoles.ROLE_MODERATOR,
                ClanRoles.ROLE_ORGANIZER, ClanRoles.ROLE_LEADER
        };
        boolean[][] expected = {
                //  member  mod   org   leader
                {false, false, false, false}, // member
                {true,  false, false, false}, // moderator
                {true,  true,  false, false}, // organizer
                {true,  true,  true,  false}, // leader
        };
        for (int i = 0; i < roles.length; i++) {
            for (int j = 0; j < roles.length; j++) {
                assertEquals(expected[i][j], ClanRoles.canGrantRole(roles[i], roles[j]),
                        "canGrantRole(" + roles[i] + ", " + roles[j] + ")");
            }
        }
    }

    @Test
    @DisplayName("grantableRoles — leader grants member/mod/organizer, organizer grants member/mod")
    void grantableRoles() {
        assertEquals(List.of(), ClanRoles.grantableRoles(ClanRoles.ROLE_MEMBER));
        assertEquals(List.of(ClanRoles.ROLE_MEMBER), ClanRoles.grantableRoles(ClanRoles.ROLE_MODERATOR));
        assertEquals(List.of(ClanRoles.ROLE_MEMBER, ClanRoles.ROLE_MODERATOR),
                ClanRoles.grantableRoles(ClanRoles.ROLE_ORGANIZER));
        assertEquals(List.of(ClanRoles.ROLE_MEMBER, ClanRoles.ROLE_MODERATOR, ClanRoles.ROLE_ORGANIZER),
                ClanRoles.grantableRoles(ClanRoles.ROLE_LEADER));
    }
}

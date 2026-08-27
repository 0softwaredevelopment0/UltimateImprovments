package com.ultimateimprovments.command.clan;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * ClanFriendlyFireListener — blocks clan members from damaging each other
 * when the clan setting selfpvp is on.
 *
 * Also blocks damage between main and dependent clan members
 * when the main clan has selfpvp=on.
 */
public final class ClanFriendlyFireListener implements Listener {

    private ClanFriendlyFireListener() {}

    public static void init(Main plugin) {
        plugin.getServer().getPluginManager().registerEvents(new ClanFriendlyFireListener(), plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        String attackerClan = ClanDatabase.getClanKeyByPlayer(attacker.getUniqueId().toString());
        if (attackerClan == null) return;
        String victimClan = ClanDatabase.getClanKeyByPlayer(victim.getUniqueId().toString());
        if (victimClan == null) return;

        // Same clan
        if (attackerClan.equals(victimClan)) {
            if (ClanDatabase.isSelfPvpEnabled(attackerClan)) {
                event.setCancelled(true);
                attacker.sendActionBar(MessageUtil.parse("<red>\u274c You can't attack your clanmate!</red>"));
            }
            return;
        }

        // Different clans — check dependency relationship
        if (isDependentPair(attackerClan, victimClan)) {
            // Find the main clan in the pair
            String mainClan;
            String mainOfAttacker = ClanDatabase.getMainClan(attackerClan);
            if (mainOfAttacker != null && mainOfAttacker.equals(victimClan)) {
                mainClan = victimClan;
            } else {
                mainClan = attackerClan;
            }
            // Check selfpvp on the main clan
            if (ClanDatabase.isSelfPvpEnabled(mainClan)) {
                event.setCancelled(true);
                attacker.sendActionBar(MessageUtil.parse("<red>\u274c You can't attack your allied clanmate!</red>"));
            }
        }
    }

    /** Returns true if two clans are in a main-dependent relationship. */
    private boolean isDependentPair(String clan1, String clan2) {
        // clan1 is main of clan2?
        String dep1 = ClanDatabase.getDependentClan(clan1);
        if (dep1 != null && dep1.equals(clan2)) return true;
        // clan2 is main of clan1?
        String dep2 = ClanDatabase.getDependentClan(clan2);
        if (dep2 != null && dep2.equals(clan1)) return true;
        return false;
    }
}

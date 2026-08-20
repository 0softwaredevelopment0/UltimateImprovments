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
 * when the clan setting {@code selfpvp} is {@code on}.
 *
 * <p>Default ({@code off}) keeps vanilla behavior — clanmates may fight.
 * The action bar message matches the cobweb style
 * ({@code BoostedCobwebManager}): {@code ❌ You can't attack...}</p>
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

        String clan = ClanDatabase.getClanKeyByPlayer(attacker.getUniqueId().toString());
        if (clan == null) return;
        if (!clan.equals(ClanDatabase.getClanKeyByPlayer(victim.getUniqueId().toString()))) return;
        if (!ClanDatabase.isSelfPvpEnabled(clan)) return;

        event.setCancelled(true);
        attacker.sendActionBar(MessageUtil.parse("<red>❌ You can't attack your clanmate!</red>"));
    }
}

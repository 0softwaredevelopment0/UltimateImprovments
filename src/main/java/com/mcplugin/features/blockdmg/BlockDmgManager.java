package com.mcplugin.features.blockdmg;

import com.mcplugin.Main;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BlockDmgManager extends BukkitRunnable {

    private static BlockDmgManager instance;
    private static boolean enabled = true;
    private static int dripstoneDmg = 1;
    private static int endrodDmg = 1;

    public static void init(Main plugin) {
        instance = new BlockDmgManager();
        reloadConfig();
        int interval = Main.getInstance().getConfig().getInt("features.blockdmg.interval_ticks", 5);
        instance.runTaskTimer(plugin, 20L, interval);
    }

    public static void reloadConfig() {
        var cfg = Main.getInstance().getConfig().getConfigurationSection("features.blockdmg");
        if (cfg == null) return;
        enabled = cfg.getBoolean("enabled", true);
        dripstoneDmg = cfg.getInt("dripstone_damage", 1);
        endrodDmg = cfg.getInt("endrod_damage", 1);
    }

    @Override
    public void run() {
        if (!enabled) return;

        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

            Block standing = player.getLocation().getBlock();
            double yOffset = player.getLocation().getY() - standing.getY();

            // Только если игрок вплотную к блоку (в пределах 0.1 блока по Y)
            if (yOffset > 0.1) continue;

            // Pointed dripstone at feet level
            if (standing.getType() == Material.POINTED_DRIPSTONE) {
                player.damage(dripstoneDmg);
            }

            // End rod facing up — игрок стоит на стержне
            if (standing.getType() == Material.END_ROD) {
                Directional directional = (Directional) standing.getBlockData();
                if (directional.getFacing() == BlockFace.UP) {
                    player.damage(endrodDmg);
                }
            }
        }
    }
}

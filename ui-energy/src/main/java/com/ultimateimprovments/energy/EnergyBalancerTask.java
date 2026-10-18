package com.ultimateimprovments.energy;

import com.ultimateimprovments.core.Main;
import com.ultimateimprovments.energy.storage.battery.BatteryManager;
import com.ultimateimprovments.energy.transfer.cable.CableNetwork;
import com.ultimateimprovments.energy.transfer.cable.CableNode;
import com.ultimateimprovments.energy.transfer.cable.EnergyFlow;
import com.ultimateimprovments.energy.transfer.cable.NodeType;
import com.ultimateimprovments.util.ConsoleLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * The single battery-balancing pass of the network.
 * <p>
 * Once per run it walks the connected components of the cable graph
 * ({@link EnergyFlow#collectBalanceGroup}) and equalizes the charge of every
 * battery group toward the average, capped by {@code energy.balancer.max_transfer}
 * per battery per run. This task replaced two competing systems: the old
 * balancer and {@code BatteryDrainTask} (which was a second balancer that
 * pushed energy into the first battery with free space, ignoring charge
 * modes — the two fought each other and kept the whole network churning).
 */
public class EnergyBalancerTask extends BukkitRunnable {

    /** The task runs every 40 ticks — the transfer cap is scaled by this, so the per-second balancing rate is unchanged. */
    private static final int TICKS_PER_RUN = 40;

    @Override
    public void run() {
        FileConfiguration cfg = Main.getInstance().getConfig();
        if (!cfg.getBoolean("energy.balancer.enabled", true)) return;

        int maxTransfer = cfg.getInt("energy.balancer.max_transfer", 25) * TICKS_PER_RUN;
        boolean log = cfg.getBoolean("energy.balancer.log", false);

        // Connected-component walk: every battery belongs to exactly one group.
        List<CableNode> starts = new ArrayList<>();
        CableNetwork.forEachNode(node -> {
            if (node != null && node.getType() == NodeType.BATTERY) {
                starts.add(node);
            }
        });

        Set<Long> visited = new HashSet<>();
        for (CableNode start : starts) {
            if (visited.contains(start.getKey())) continue;

            List<CableNode> group = EnergyFlow.collectBalanceGroup(start, visited);
            if (group.size() > 1) {
                balanceGroup(group, maxTransfer, log);
            }
        }
    }

    // =========================
    // GROUP BALANCING
    // =========================
    private void balanceGroup(List<CableNode> group, int maxTransfer, boolean log) {
        int totalEnergy = 0;
        for (CableNode b : group) totalEnergy += b.getEnergy();
        int average = totalEnergy / group.size();

        // Collect the excess from rich batteries
        int collected = 0;
        List<CableNode> rich = new ArrayList<>();
        List<CableNode> poor = new ArrayList<>();
        Map<CableNode, Integer> moved = new LinkedHashMap<>();
        for (CableNode b : group) {
            int current = b.getEnergy();
            if (current > average) {
                int remove = Math.min(current - average, maxTransfer);
                if (remove > 0) {
                    b.removeEnergy(remove);
                    collected += remove;
                    rich.add(b);
                    moved.merge(b, remove, Integer::sum);
                }
            } else if (current < average) {
                poor.add(b);
            }
        }

        if (collected <= 0) return;

        // Distribute to poor batteries
        for (CableNode b : poor) {
            if (collected <= 0) break;
            int deficit = average - b.getEnergy();
            if (deficit <= 0) continue;
            int add = Math.min(Math.min(deficit, maxTransfer), collected);
            if (add > 0) {
                b.addEnergy(add);
                collected -= add;
                moved.merge(b, add, Integer::sum);
            }
        }

        // Return undistributed energy back to rich batteries (proportionally)
        if (collected > 0 && !rich.isEmpty()) {
            int totalRichEnergy = 0;
            for (CableNode r : rich) totalRichEnergy += r.getEnergy();
            int remainingToReturn = collected;
            for (int i = 0; i < rich.size() && remainingToReturn > 0; i++) {
                CableNode r = rich.get(i);
                int takenBack;
                if (i == rich.size() - 1) {
                    takenBack = remainingToReturn;
                } else {
                    double share = totalRichEnergy > 0 ? (double) r.getEnergy() / totalRichEnergy : 1.0 / rich.size();
                    takenBack = Math.min(remainingToReturn, Math.max(1, (int) (collected * share)));
                    // Prevent over-allocation
                    if (takenBack > remainingToReturn) takenBack = remainingToReturn;
                }
                r.addEnergy(takenBack);
                remainingToReturn -= takenBack;
            }
            int returned = collected - remainingToReturn;
            if (log && returned > 0) {
                ConsoleLogger.info("[Balancer] Returned " + returned + " undistributed energy to rich batteries");
            }
        } else if (collected > 0 && log) {
            ConsoleLogger.info("[Balancer] " + collected + " energy left undistributed (no rich batteries to return to)");
        }

        // Activity trail on the cables adjacent to the moved batteries
        // (blink visuals + multimeter readings).
        for (Map.Entry<CableNode, Integer> entry : moved.entrySet()) {
            CableNode b = entry.getKey();
            String worldUid = b.getWorld().getUID().toString();
            for (long connKey : b.getConnectionKeys()) {
                CableNode cable = CableNetwork.getNodeByKey(worldUid, connKey);
                if (cable == null || cable.getType() != NodeType.CABLE) continue;
                CableNetwork.markFlowingKey(worldUid, cable.getKey());
                cable.addTransferred(entry.getValue());
            }
        }
    }
}

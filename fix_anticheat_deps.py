import re

# Fix ElytraCheck.java - replace ElytraBoostManager with soft reflection
f = 'ui-anticheat/src/main/java/com/ultimateimprovments/mechanics/security/anticheat/movement/ElytraCheck.java'
with open(f, 'r') as fh:
    c = fh.read()

# Replace import
c = c.replace(
    'import com.ultimateimprovments.mechanics.features.player.ElytraBoostManager;',
    '// ElytraBoostManager is a soft dependency (UI-Other)\n// import removed; using reflection'
)

# Replace the usage
c = c.replace(
    'boolean wasBoosted = ElytraBoostManager.isRecentlyBoosted(player.getUniqueId(), boostCheckWindowMs);',
    '''boolean wasBoosted = false;
            try {
                Class<?> ebm = Class.forName("com.ultimateimprovments.mechanics.features.player.ElytraBoostManager");
                wasBoosted = (boolean) ebm.getMethod("isRecentlyBoosted", java.util.UUID.class, long.class)
                        .invoke(null, player.getUniqueId(), boostCheckWindowMs);
            } catch (Exception ignored) {}'''
)

with open(f, 'w') as fh:
    fh.write(c)
print("ElytraCheck fixed")

# Fix ExemptionManager.java - replace CheckManager with soft reflection
f2 = 'ui-anticheat/src/main/java/com/ultimateimprovments/mechanics/security/anticheat/core/ExemptionManager.java'
with open(f2, 'r') as fh:
    c2 = fh.read()

c2 = c2.replace(
    'if (com.ultimateimprovments.mechanics.security.check.CheckManager.isBeingChecked(player)) return true;',
    '''try {
            Class<?> cm = Class.forName("com.ultimateimprovments.mechanics.security.check.CheckManager");
            if ((boolean) cm.getMethod("isBeingChecked", org.bukkit.entity.Player.class).invoke(null, player)) return true;
        } catch (Exception ignored) {}'''
)

with open(f2, 'w') as fh:
    fh.write(c2)
print("ExemptionManager fixed")

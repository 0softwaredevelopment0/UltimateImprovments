package com.ultimateimprovments.structure;

import com.ultimateimprovments.util.StructureTemplate;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Occupancy checks between the NBT structure that is being assembled and the
 * structures already registered in the world.
 * <p>
 * <b>Why this exists.</b> The assembly used to look for the first template
 * match within a 5-block radius and then asked the manager "is a structure at
 * this center?" — so a second, separate build standing NEARBY but not touching
 * an assembled one was rejected: the scan found the OLD structure's matching
 * volume first and reported "a structure is already active at this place"
 * although the clicked frame belonged to the new, empty one.
 * <p>
 * The conflict test here is coordinate-based, never radius-based: the candidate
 * template's volume is compared with the volume of every registered template
 * structure on all three axes (see
 * {@link StructureTemplate#overlapsVolume(StructureTemplate, int, int, int)}).
 * A structure standing next to another one with a gap (or even directly against
 * its face without sharing a cell) is free; only a structure that would occupy
 * cells of an already assembled structure is a conflict.
 * <p>
 * Only structures with a real NBT volume are considered: the marker types
 * {@code reactor} (Dark Fusion Reactor), {@code lightning} and
 * {@code nuclearreactor} (compact nuclear reactor), each of which registers
 * exactly one marker at its anchor. Per-block registries (batteries, lamps,
 * cables, magnets, generators) are physical clusters that already validate
 * their own block coordinates and have no NBT template to compare.
 */
public final class StructureOccupancy {

    /**
     * Marker type in {@code structure_markers} → NBT template name. The marker
     * type is what the owning manager writes at the structure anchor.
     */
    private static final Map<String, String> TEMPLATE_BY_MARKER_TYPE = Map.of(
            "reactor", "darkfusionreactor",
            "lightning", "lightning",
            "nuclearreactor", "nuclearreactor");

    /**
     * One assembled structure whose volume intersects the candidate.
     *
     * @param templateName template name of the assembled structure
     * @param displayName  localized display name (config {@code structures.names.*})
     * @param anchor       structure anchor / template center
     */
    public record Conflict(String templateName, String displayName, Location anchor) {

        /** Whether the candidate center IS this structure's anchor (same place). */
        public boolean at(Location center) {
            return center != null
                    && center.getWorld() != null
                    && anchor.getWorld() != null
                    && center.getWorld().equals(anchor.getWorld())
                    && center.getBlockX() == anchor.getBlockX()
                    && center.getBlockY() == anchor.getBlockY()
                    && center.getBlockZ() == anchor.getBlockZ();
        }
    }

    private StructureOccupancy() {}

    /**
     * Every assembled template structure whose volume overlaps the volume of
     * {@code template} placed at {@code center}. Empty when the place is free —
     * a nearby structure with a gap is NOT a conflict.
     *
     * @param template the template being assembled (must be loaded)
     * @param center   the candidate template center
     * @return immutable list of conflicts (empty when the position is free)
     */
    public static List<Conflict> conflictsAt(StructureTemplate template, Location center) {
        if (template == null || center == null || center.getWorld() == null) return List.of();

        World world = center.getWorld();
        String worldUid = world.getUID().toString();

        Set<String> seen = new HashSet<>();
        List<Conflict> conflicts = new ArrayList<>();

        for (Map.Entry<String, StructureMarker.StructureData> entry : StructureMarker.getAllEntries()) {
            StructureMarker.StructureData data = entry.getValue();
            if (data == null) continue;

            String templateName = TEMPLATE_BY_MARKER_TYPE.get(data.type());
            if (templateName == null) continue;

            String fk = entry.getKey();
            if (!worldUid.equals(StructureMarker.parseWorldUid(fk))) continue;

            // One anchor per structure: duplicate rows must not duplicate the conflict.
            if (!seen.add(data.type() + ":" + data.uuid())) continue;

            Location anchor;
            try {
                anchor = new Location(world,
                        StructureMarker.parseX(fk),
                        StructureMarker.parseY(fk),
                        StructureMarker.parseZ(fk));
            } catch (RuntimeException e) {
                continue; // corrupt row — the registry load filters those out as well
            }

            StructureTemplate other = StructureTemplate.get(templateName);
            if (other == null) continue; // template not loaded — nothing to compare against

            int dx = center.getBlockX() - anchor.getBlockX();
            int dy = center.getBlockY() - anchor.getBlockY();
            int dz = center.getBlockZ() - anchor.getBlockZ();
            if (other.overlapsVolume(template, dx, dy, dz)) {
                conflicts.add(new Conflict(templateName, other.getDisplayName(), anchor));
            }
        }
        return List.copyOf(conflicts);
    }

    /** Convenience: {@code true} when nothing assembled occupies the candidate volume. */
    public static boolean isFree(StructureTemplate template, Location center) {
        return conflictsAt(template, center).isEmpty();
    }
}

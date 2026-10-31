package com.ultimateimprovments.mechanics.features.items;

/**
 * Coordinates of the TNT block a Remote Detonator is bound to.
 * <p>
 * Stored inside the item's {@code PersistentDataContainer} as
 * {@code "x;y;z;world"} — the coordinates come first, so a world name that
 * contains a semicolon still round-trips (the decode split caps at four parts
 * and keeps the tail intact).
 * <p>
 * Deliberately plain Java (no Bukkit types): the codec is unit-tested without
 * a running server.
 *
 * @param world the world name ({@code World#getName()}), never blank
 * @param x     block X
 * @param y     block Y
 * @param z     block Z
 */
public record DetonatorTarget(String world, int x, int y, int z) {

    /** Encoded form written into the item's PDC. */
    public String encode() {
        return x + ";" + y + ";" + z + ";" + world;
    }

    /**
     * Parses the PDC payload.
     *
     * @param raw the stored value, may be {@code null}
     * @return the decoded target, or {@code null} when the value is missing or
     *         malformed (a bad value must never blow up the item's owner)
     */
    public static DetonatorTarget decode(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(";", 4);
        if (parts.length != 4) return null;

        String world = parts[3].trim();
        if (world.isEmpty()) return null;

        try {
            return new DetonatorTarget(world,
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

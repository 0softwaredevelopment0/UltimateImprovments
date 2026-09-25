package com.ultimateimprovments.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * CompositeConfig — the Bukkit {@link FileConfiguration} view handed out by
 * {@code Main.getConfig()} after the per-addon config split.
 * <p>
 * Every read/write is routed by first path segment: keys owned by an addon come
 * from its {@code configs/UI-<Addon>.toml} in-memory view, everything else from the
 * core view. {@code messages.<group>.*} / {@code messages_en.<group>.*} are routed by
 * the message group (second segment), because messages of an addon live in ITS file.
 * <p>
 * This keeps every existing {@code Main.getInstance().getConfig().get*(...)} call
 * site working without changes.
 */
public final class CompositeConfig extends FileConfiguration {

    public CompositeConfig() {
        super();
    }

    private static String rootOf(String path) {
        int dot = path.indexOf('.');
        return dot > 0 ? path.substring(0, dot) : path;
    }

    private static FileConfiguration targetFor(String path) {
        return AddonConfigManager.viewOf(AddonConfigManager.addonOfKey(path));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Routed reads
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public Object get(String path, Object def) {
        FileConfiguration target = targetFor(path);
        if (target.isSet(path)) return target.get(path, def);
        // cross-file fallback: the key may live in another addon's file
        for (FileConfiguration other : AddonConfigManager.allViews()) {
            if (other != target && other.isSet(path)) return other.get(path, def);
        }
        return def;
    }

    @Override
    public boolean isSet(String path) {
        for (FileConfiguration view : AddonConfigManager.allViews()) {
            if (view.isSet(path)) return true;
        }
        return false;
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        TreeSet<String> out = new TreeSet<>();
        for (FileConfiguration view : AddonConfigManager.allViews()) {
            out.addAll(view.getKeys(deep));
        }
        out.remove(AddonConfigManager.DIRTY_KEY);
        return out;
    }

    @Override
    public Map<String, Object> getValues(boolean deep) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (String key : getKeys(false)) {
            out.put(key, get(key));
        }
        return out;
    }

    @Override
    public ConfigurationSection getConfigurationSection(String path) {
        Object value = get(path, null);
        if (value instanceof ConfigurationSection cs) return cs;
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Routed writes
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void set(String path, Object value) {
        FileConfiguration target = targetFor(path);
        target.set(path, value);
        AddonConfigManager.touch(AddonConfigManager.addonOfKey(path));
    }

    @Override
    public ConfigurationSection createSection(String path) {
        FileConfiguration target = targetFor(path);
        ConfigurationSection section = target.createSection(path);
        AddonConfigManager.touch(AddonConfigManager.addonOfKey(path));
        return section;
    }

    @Override
    public ConfigurationSection createSection(String path, Map<?, ?> map) {
        FileConfiguration target = targetFor(path);
        ConfigurationSection section = target.createSection(path, map);
        AddonConfigManager.touch(AddonConfigManager.addonOfKey(path));
        return section;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Defaults (per-addon repair is owned by AddonConfigManager)
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void addDefault(String path, Object value) {
        // no-op: defaults come from the bundled per-addon fragments
    }

    // ══════════════════════════════════════════════════════════════════════
    // Serialization (not used for persistence — per-addon TOMLs are written
    // directly by AddonConfigManager; implemented for completeness)
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public String saveToString() {
        StringBuilder sb = new StringBuilder();
        for (FileConfiguration view : AddonConfigManager.allViews()) {
            sb.append(view.saveToString()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void loadFromString(String contents) {
        throw new UnsupportedOperationException(
                "CompositeConfig is a routed view; edit configs/UI-<Addon>.toml instead");
    }
}

package com.mcplugin.features;

import com.mcplugin.Main;

/**
 * FeaturesManager — replaced by individual feature modules.
 * Each feature is now its own module registered in Main.java.
 * This file remains for backward compatibility.
 */
public class FeaturesManager {

    private static FeaturesManager instance;

    public static void init(Main plugin) {
        instance = new FeaturesManager();
    }

    public static void reloadConfig() {
        // Config reload is now handled by individual feature modules
    }

    public static FeaturesManager getInstance() {
        return instance;
    }
}

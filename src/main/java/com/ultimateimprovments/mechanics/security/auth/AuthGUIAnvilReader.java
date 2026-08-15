package com.ultimateimprovments.mechanics.security.auth;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

/**
 * Reads text from the anvil rename field via NMS reflection.
 * Extracted into a separate class to isolate the dirty reflection.
 */
public class AuthGUIAnvilReader {

    private AuthGUIAnvilReader() {}

    /**
     * Gets the text the player entered in the anvil rename field.
     * Uses NMS reflection to access the itemName field in AnvilMenu.
     *
     * @param player the player whose anvil is open
     * @return the text from the rename field, or null if it could not be read
     */
    public static String getAnvilRenameText(Player player) {
        try {
            InventoryView view = player.getOpenInventory();

            // Get the CraftInventoryView to access the NMS container
            // Paper 1.21.11 uses setAccessible reflection approach
            Object craftView = view;
            Class<?> craftViewClass = craftView.getClass();

            // CraftInventoryView.getHandle() returns AbstractContainerMenu
            java.lang.reflect.Method getHandle = craftViewClass.getMethod("getHandle");
            Object handle = getHandle.invoke(craftView);

            // handle is net.minecraft.world.inventory.AnvilMenu
            // In Mojang mappings, the field is "itemName"
            Class<?> anvilClass = handle.getClass();
            java.lang.reflect.Field itemNameField;

            try {
                itemNameField = anvilClass.getDeclaredField("itemName");
            } catch (NoSuchFieldException e) {
                // Try parent class
                itemNameField = anvilClass.getSuperclass().getDeclaredField("itemName");
            }

            itemNameField.setAccessible(true);
            String text = (String) itemNameField.get(handle);
            itemNameField.setAccessible(false);
            return text;
        } catch (Exception e) {
            return null;
        }
    }
}

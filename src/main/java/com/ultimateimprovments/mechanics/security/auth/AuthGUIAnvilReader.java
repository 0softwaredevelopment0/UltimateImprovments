package com.ultimateimprovments.mechanics.security.auth;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;

/**
 * Чтение текста из поля переименования наковальни через NMS reflection.
 * Вынесено в отдельный класс для изоляции грязной рефлексии.
 */
public class AuthGUIAnvilReader {

    private AuthGUIAnvilReader() {}

    /**
     * Получает текст, введённый игроком в поле переименования наковальни.
     * Использует NMS reflection для доступа к полю itemName в AnvilMenu.
     *
     * @param player игрок, у которого открыта наковальня
     * @return текст из поля переименования, или null если не удалось прочитать
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

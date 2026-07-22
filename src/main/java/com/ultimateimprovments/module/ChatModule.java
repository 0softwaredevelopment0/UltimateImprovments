package com.ultimateimprovments.module;

import com.ultimateimprovments.chat.ChatManager;
import com.ultimateimprovments.core.Main;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Модуль кастомного чата.
 * <p>
 * Перехватывает AsyncPlayerChatEvent и форматирует сообщение
 * по шаблону из config.yml. По умолчанию ОТКЛЮЧЕН (chat.enabled: false).
 * <p>
 * Поддерживает MiniMessage, PAPI, LuckPerms per-group форматы, per-world форматы.
 */
public class ChatModule extends PluginModule {

    public ChatModule() {
        super("Chat", "chat", false);
    }

    @Override
    protected void onInit(JavaPlugin plugin) throws Exception {
        ChatManager.init();
    }

    @Override
    protected void onDisable(JavaPlugin plugin) {
        ChatManager.shutdown();
    }

    @Override
    protected void onReloadConfig(JavaPlugin plugin) {
        ChatManager.reload();
    }
}

package com.aiplayermod;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Перехватывает сообщения игроков в чате и передаёт в DialogueSystem.
 * Так ИИ может реагировать на то что пишут живые игроки.
 */
public class ChatListener {

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            AiPlayerManager mgr = AiPlayerMod.getManager();
            if (mgr == null) return;

            String playerName = sender.getName().getString();
            String text = message.getContent().getString();

            // Не реагируем на свои же сообщения
            if (playerName.equals(mgr.getConfig().botName)) return;

            mgr.onPlayerChat(sender.getServer(), playerName, text);
        });
    }
}

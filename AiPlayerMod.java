package com.aiplayermod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AiPlayerMod implements ModInitializer {

    public static final String MOD_ID = "aiplayermod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static AiPlayerManager aiPlayerManager;

    public static AiPlayerManager getManager() { return aiPlayerManager; }
    public static AiPlayerManager getClientManager() { return aiPlayerManager; }
    public static void setManager(AiPlayerManager manager) { aiPlayerManager = manager; }

    @Override
    public void onInitialize() {
        LOGGER.info("[AIPlayer] v5.0 загружается — Baritone + Диалоги + GUI");

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
            AiPlayerCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            if (aiPlayerManager != null) aiPlayerManager.onTick(server);
        });

        // Слушаем чат игроков для диалоговой системы
        ChatListener.register();

        LOGGER.info("[AIPlayer] v5.0 загружен! /aiplayer start <ник> | GUI: клавиша I");
    }
}

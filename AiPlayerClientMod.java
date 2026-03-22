package com.aiplayermod.client;

import com.aiplayermod.AiPlayerMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class AiPlayerClientMod implements ClientModInitializer {

    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // Регистрируем клавишу I для открытия GUI
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.aiplayermod.opengui",         // translation key
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_I,                    // клавиша I
            "category.aiplayermod"              // категория в настройках
        ));

        // Каждый тик клиента проверяем нажатие
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AiPlayerScreen());
                }
            }
        });

        AiPlayerMod.LOGGER.info("[AIPlayer] Клиентская часть загружена. Нажми I для настроек.");
    }
}

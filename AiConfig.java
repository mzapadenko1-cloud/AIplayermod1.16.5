package com.aiplayermod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

public class AiConfig {

    public String provider = "gemini";
    public String apiKey = "ВСТАВЬ_СВОЙ_КЛЮЧ_ЗДЕСЬ";
    public String model = "gemini-2.0-flash";
    public int thinkIntervalTicks = 100;
    public int memorySize = 20;
    public String botName = "Steve_AI";
    public boolean verboseChat = true;
    public String language = "Russian";
    public String personality = "любопытный новичок";

    public String getEndpoint() {
        switch (provider.toLowerCase()) {
            case "gemini":
                return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            case "groq":
                return "https://api.groq.com/openai/v1/chat/completions";
            case "grok":
                return "https://api.x.ai/v1/chat/completions";
            case "openrouter":
                return "https://openrouter.ai/api/v1/chat/completions";
            default:
                return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        }
    }

    public boolean needsBearerAuth() {
        return !provider.equalsIgnoreCase("gemini");
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static AiConfig load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("aiplayermod.json");
        File configFile = configPath.toFile();
        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile)) {
                return GSON.fromJson(reader, AiConfig.class);
            } catch (Exception e) {
                AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка чтения конфига: " + e.getMessage());
            }
        }
        AiConfig config = new AiConfig();
        config.save();
        return config;
    }

    public void save() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("aiplayermod.json");
        try (Writer writer = new FileWriter(configPath.toFile())) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка сохранения конфига: " + e.getMessage());
        }
    }
}

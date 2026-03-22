package com.aiplayermod;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Постоянная память ИИ — сохраняется между перезапусками.
 * Хранит: прогресс, историю действий, выученные механики, черты характера.
 */
public class AiMemory {

    // Прогресс выживания
    public int day = 0;
    public int woodCollected = 0;
    public int stoneCollected = 0;
    public int ironCollected = 0;
    public boolean hasWorkbench = false;
    public boolean hasWoodPickaxe = false;
    public boolean hasStonePickaxe = false;
    public boolean hasIronPickaxe = false;
    public boolean hasSword = false;
    public boolean hasShelter = false;
    public boolean knowsCrafting = false;
    public boolean knowsMining = false;
    public boolean survivedFirstNight = false;

    // Выученные механики (ИИ сам пишет что узнал)
    public List<String> learnedMechanics = new ArrayList<>();

    // Долгосрочная память действий (последние N)
    public List<String> actionHistory = new ArrayList<>();

    // Черты характера (развиваются со временем)
    public String personality = "любопытный новичок";
    public int bravery = 3;      // 1-10, растёт с опытом
    public int knowledge = 1;    // 1-10, растёт с освоением механик
    public int sociability = 5;  // 1-10, как часто болтает в чате

    // Статистика для GUI
    public int totalActions = 0;
    public int totalChatMessages = 0;
    public String lastSession = "";

    // === Сохранение/загрузка ===
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static AiMemory load(String playerName) {
        Path path = getPath(playerName);
        if (path.toFile().exists()) {
            try (Reader r = new FileReader(path.toFile())) {
                AiMemory mem = GSON.fromJson(r, AiMemory.class);
                if (mem.learnedMechanics == null) mem.learnedMechanics = new ArrayList<>();
                if (mem.actionHistory == null) mem.actionHistory = new ArrayList<>();
                AiPlayerMod.LOGGER.info("[AIPlayer] Загружена память для " + playerName + " (день " + mem.day + ")");
                return mem;
            } catch (Exception e) {
                AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка загрузки памяти: " + e.getMessage());
            }
        }
        AiPlayerMod.LOGGER.info("[AIPlayer] Новая память для " + playerName);
        return new AiMemory();
    }

    public void save(String playerName) {
        Path path = getPath(playerName);
        path.toFile().getParentFile().mkdirs();
        try (Writer w = new FileWriter(path.toFile())) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка сохранения памяти: " + e.getMessage());
        }
    }

    public void addAction(String action) {
        actionHistory.add(action);
        totalActions++;
        // Храним только последние 30
        while (actionHistory.size() > 30) actionHistory.remove(0);
    }

    public void learnMechanic(String mechanic) {
        if (!learnedMechanics.contains(mechanic)) {
            learnedMechanics.add(mechanic);
            knowledge = Math.min(10, knowledge + 1);
        }
    }

    public void reset() {
        day = 0; woodCollected = 0; stoneCollected = 0; ironCollected = 0;
        hasWorkbench = false; hasWoodPickaxe = false; hasStonePickaxe = false;
        hasIronPickaxe = false; hasSword = false; hasShelter = false;
        knowsCrafting = false; knowsMining = false; survivedFirstNight = false;
        learnedMechanics.clear(); actionHistory.clear();
        personality = "любопытный новичок";
        bravery = 3; knowledge = 1; sociability = 5;
        totalActions = 0; totalChatMessages = 0;
    }

    private static Path getPath(String playerName) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("aiplayermod_memory_" + playerName + ".json");
    }
}

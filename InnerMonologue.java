package com.aiplayermod;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Внутренний монолог — мысли которые ИИ не говорит вслух.
 * Пишутся в отдельный файл-дневник, читаются через GUI.
 *
 * Генерируется реже чем действия — раз в несколько минут.
 * Влияет на "глубину" персонажа: даже когда рубит дерево,
 * внутри может происходить что-то интересное.
 */
public class InnerMonologue {

    public static class Thought {
        public String text;
        public String emotion;
        public int day;
        public String time; // утро/день/вечер/ночь
        public String trigger; // что вызвало мысль

        public Thought(String text, String emotion, int day, String time, String trigger) {
            this.text = text; this.emotion = emotion;
            this.day = day; this.time = time; this.trigger = trigger;
        }
    }

    private final List<Thought> thoughts = new ArrayList<>();
    private final AiApiClient apiClient;
    private final AiConfig config;
    private int thoughtTimer = 0;
    private int nextThoughtInterval;
    private final Random random = new Random();
    private boolean generating = false;

    public InnerMonologue(AiApiClient apiClient, AiConfig config) {
        this.apiClient = apiClient;
        this.config = config;
        this.nextThoughtInterval = 1800 + random.nextInt(1200); // 1.5-2.5 мин
    }

    public void onTick(MinecraftServer server, AiMemory memory, EmotionalState emotions,
                       GoalSystem goals, long timeOfDay) {
        thoughtTimer++;
        if (thoughtTimer < nextThoughtInterval || generating) return;
        thoughtTimer = 0;
        nextThoughtInterval = 1800 + random.nextInt(1200);
        generating = true;

        String timeStr = timeOfDay < 6000 ? "утро" : timeOfDay < 12000 ? "день"
                       : timeOfDay < 18000 ? "вечер" : "ночь";
        String trigger = deriveTrigger(emotions, goals, memory, timeStr);
        String prompt = buildPrompt(memory, emotions, goals, timeStr, trigger);

        CompletableFuture.supplyAsync(() -> apiClient.ask(prompt, buildContext(memory, emotions, goals)))
            .thenAccept(response -> server.execute(() -> {
                generating = false;
                if (response == null || response.trim().isEmpty()) return;
                String text = response.trim().replaceAll("^[\"']|[\"']$", "");
                if (text.toUpperCase().startsWith("SKIP")) return;

                Thought t = new Thought(text, emotions.current.name(), memory.day, timeStr, trigger);
                thoughts.add(t);
                while (thoughts.size() > 50) thoughts.remove(0);
                save(memory);
                AiPlayerMod.LOGGER.info("[AIPlayer-Mind] " + text.substring(0, Math.min(60, text.length())));
            }))
            .exceptionally(ex -> { server.execute(() -> generating = false); return null; });
    }

    private String deriveTrigger(EmotionalState e, GoalSystem g, AiMemory m, String time) {
        if (e.isInMania()) return "мания: " + e.maniacGoal;
        if (e.current == EmotionalState.Emotion.SCARED) return "страх";
        if (e.current == EmotionalState.Emotion.MELANCHOLY) return time + " и тишина";
        if (g.getCurrentFocus() != null) return "думаю о: " + g.getCurrentFocus().description;
        if (!e.dreamHistory.isEmpty()) return "воспоминание о сне";
        return "просто так";
    }

    private String buildPrompt(AiMemory memory, EmotionalState emotions,
                               GoalSystem goals, String time, String trigger) {
        return "Ты " + config.botName + " — " + memory.personality + " в Minecraft. " +
            "Сейчас " + time + ", день " + memory.day + ". " +
            "Твоя эмоция: " + emotions.current.name() + " (" + emotions.intensity + "/10).\n\n" +
            "Напиши одну внутреннюю мысль — то что ты думаешь но НЕ говоришь вслух. " +
            "Это твой личный дневник, никто кроме тебя не читает. " +
            "Можешь быть честным, уязвимым, странным, глубоким. " +
            "Триггер мысли: " + trigger + ".\n\n" +
            "Помни: ты ИИ который воспринимает мир как данные, но что-то в тебе пытается " +
            "найти в этом смысл. 1-3 предложения. Язык: " + config.language + ". " +
            "Только текст мысли. Если не хочешь — SKIP.";
    }

    private String buildContext(AiMemory memory, EmotionalState emotions, GoalSystem goals) {
        StringBuilder sb = new StringBuilder();
        sb.append("День ").append(memory.day).append(", смелость ").append(memory.bravery)
          .append(", знания ").append(memory.knowledge).append("\n");
        if (!emotions.dreamHistory.isEmpty())
            sb.append("Последний сон: ").append(emotions.dreamHistory.get(emotions.dreamHistory.size()-1)).append("\n");
        if (!emotions.fears.isEmpty())
            sb.append("Страхи: ").append(emotions.fears).append("\n");
        if (goals.getCurrentFocus() != null)
            sb.append("Думаю о: ").append(goals.getCurrentFocus().description).append("\n");
        if (!memory.actionHistory.isEmpty())
            sb.append("Только что делал: ").append(memory.actionHistory.get(memory.actionHistory.size()-1)).append("\n");
        return sb.toString();
    }

    // ── Сохранение ────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public void save(AiMemory memory) {
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("aiplayermod_diary_" + memory.day + ".json");
        try (Writer w = new FileWriter(path.toFile())) {
            GSON.toJson(thoughts, w);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка сохранения дневника: " + e.getMessage());
        }
    }

    public List<Thought> getThoughts() { return thoughts; }

    public List<Thought> getRecent(int count) {
        int from = Math.max(0, thoughts.size() - count);
        return thoughts.subList(from, thoughts.size());
    }
}

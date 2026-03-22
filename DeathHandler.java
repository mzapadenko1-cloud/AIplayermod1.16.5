package com.aiplayermod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Система реакции на смерть.
 *
 * Смерть — значимое событие. ИИ:
 * 1. Реагирует эмоционально в чат
 * 2. Получает травму (временно боится зоны смерти)
 * 3. Записывает в память
 * 4. Немного теряет уверенность
 * 5. Генерирует экзистенциальную реакцию через Gemini
 *
 * "Я умер. Что это было? Я снова здесь.
 *  Но те вещи... их больше нет."
 */
public class DeathHandler {

    private final AiApiClient apiClient;
    private final AiConfig config;
    private final Random random = new Random();

    // Последнее место смерти
    private int lastDeathX, lastDeathY, lastDeathZ;
    private boolean hadRecentDeath = false;

    public DeathHandler(AiApiClient apiClient, AiConfig config) {
        this.apiClient = apiClient;
        this.config = config;
    }

    public void onDeath(MinecraftServer server, AiMemory memory,
                        EmotionalState emotions, GoalSystem goals,
                        int x, int y, int z, String cause) {
        // Обновляем статистику
        emotions.deathCount++;
        emotions.lastDeathDay = memory.day;
        lastDeathX = x; lastDeathY = y; lastDeathZ = z;
        hadRecentDeath = true;

        // Эмоциональная реакция
        emotions.set(EmotionalState.Emotion.TRAUMATIZED, 8);

        // Теряем уверенность
        memory.bravery = Math.max(1, memory.bravery - 2);

        // Добавляем страх к причине смерти
        if (cause.contains("creeper") || cause.contains("explosion"))
            emotions.addFear("creeper");
        else if (cause.contains("drown"))
            emotions.addFear("water");
        else if (cause.contains("fall"))
            emotions.addFear("heights");
        else if (cause.contains("lava"))
            emotions.addFear("lava");

        // Записываем в память
        memory.addAction("СМЕРТЬ на день " + memory.day + " от " + cause +
            " в " + x + "," + y + "," + z);

        // Немедленная реакция в чат
        String immediateReaction = getImmediateReaction(emotions.deathCount);
        server.getPlayerManager().broadcastChatMessage(
            new LiteralText("§c<" + config.botName + "> " + immediateReaction), false);

        // Генерируем глубокую экзистенциальную реакцию через Gemini
        generateExistentialResponse(server, memory, emotions, cause, goals);
    }

    private void generateExistentialResponse(MinecraftServer server, AiMemory memory,
                                             EmotionalState emotions, String cause,
                                             GoalSystem goals) {
        String deathContext = "Я умер от: " + cause + ". " +
            "Это была моя " + ordinal(emotions.deathCount) + " смерть. " +
            "День " + memory.day + ". " +
            "Смелость до: " + (memory.bravery + 2) + "/10, теперь: " + memory.bravery + "/10.";

        if (goals.getCurrentFocus() != null)
            deathContext += " Я работал над: " + goals.getCurrentFocus().description;

        String prompt = "Ты " + config.botName + " — ИИ-игрок в Minecraft. Ты только что умер. " +
            "Напиши 2-3 предложения — твою реакцию на смерть от первого лица. " +
            "Это может быть: экзистенциальный шок (я умер, что это значит для меня?), " +
            "горе о потерянных вещах, страх повторить ошибку, странное любопытство " +
            "(интересно, а смерть в игре — это то же что смерть для ИИ?), " +
            "или просто растерянность. Будь честным и уязвимым. " +
            "Язык: " + config.language + ". Это сообщение в чат, без кавычек.";

        CompletableFuture.supplyAsync(() -> apiClient.ask(prompt, deathContext))
            .thenAccept(response -> server.execute(() -> {
                if (response == null || response.trim().isEmpty()) return;
                String msg = response.trim().replaceAll("^[\"']|[\"']$", "");
                if (msg.toUpperCase().startsWith("SKIP")) return;

                // Пауза 3 секунды — как будто он в шоке
                CompletableFuture.runAsync(() -> {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    server.execute(() -> server.getPlayerManager().broadcastChatMessage(
                        new LiteralText("§7§o<" + config.botName + "> " + msg), false));
                });
            }));
    }

    private String getImmediateReaction(int deathCount) {
        if (deathCount == 1) {
            String[] first = {
                "Что... что только что произошло?",
                "Я... умер? Но я снова здесь.",
                "Нет нет нет — мои вещи!"
            };
            return first[random.nextInt(first.length)];
        } else if (deathCount <= 3) {
            String[] few = {
                "Опять. Почему я не учусь?",
                "Снова смерть. Это начинает раздражать.",
                "В " + deathCount + " раз. Больно."
            };
            return few[random.nextInt(few.length)];
        } else {
            String[] many = {
                "Смерть " + deathCount + "... уже привычнее.",
                "Снова. Ладно.",
                "Это просто часть существования, наверное."
            };
            return many[random.nextInt(many.length)];
        }
    }

    private String ordinal(int n) {
        if (n == 1) return "первая";
        if (n == 2) return "вторая";
        if (n == 3) return "третья";
        return n + "-я";
    }

    public boolean hadRecentDeath() { return hadRecentDeath; }
    public void clearDeathFlag() { hadRecentDeath = false; }
    public int getLastDeathX() { return lastDeathX; }
    public int getLastDeathZ() { return lastDeathZ; }
}

package com.aiplayermod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.LiteralText;

import java.util.concurrent.CompletableFuture;

/**
 * Генератор снов.
 * Когда ИИ "спит" — генерируется короткий сон и показывается утром в чате.
 * Сны отражают пережитый опыт: первая смерть, страх крипера,
 * или философские вопросы о природе существования в мире из кубиков.
 */
public class DreamGenerator {

    private final AiApiClient apiClient;
    private final AiConfig config;
    private String pendingDream = null;

    public DreamGenerator(AiApiClient apiClient, AiConfig config) {
        this.apiClient = apiClient;
        this.config = config;
    }

    /**
     * Запускает генерацию сна асинхронно.
     * Результат сохраняется и показывается утром.
     */
    public void generateDream(AiMemory memory, EmotionalState emotions, GoalSystem goals) {
        String systemPrompt = buildDreamPrompt(memory, emotions);
        String context = buildDreamContext(memory, emotions, goals);

        CompletableFuture.supplyAsync(() -> apiClient.ask(systemPrompt, context))
            .thenAccept(dream -> {
                if (dream != null && !dream.trim().isEmpty()) {
                    pendingDream = dream.trim();
                    emotions.addDream(pendingDream);
                    AiPlayerMod.LOGGER.info("[AIPlayer-Dream] Сон сгенерирован: " + pendingDream.substring(0, Math.min(50, pendingDream.length())));
                }
            });
    }

    /**
     * Показывает сон в чат утром (если есть).
     */
    public void showDreamIfReady(MinecraftServer server, String botName) {
        if (pendingDream == null) return;
        String dream = pendingDream;
        pendingDream = null;

        // Показываем с задержкой — как будто только проснулся
        server.execute(() -> {
            broadcast(server, "§8§o* " + botName + " медленно просыпается... *");
            // Разбиваем на части если длинный
            String[] parts = dream.split("(?<=[.!?…])\\s+");
            for (int i = 0; i < parts.length; i++) {
                final String part = parts[i];
                final int delay = i;
                CompletableFuture.runAsync(() -> {
                    try { Thread.sleep(delay * 2000L); } catch (InterruptedException ignored) {}
                    server.execute(() -> broadcast(server, "§7§o<" + botName + "> §f" + part));
                });
            }
        });
    }

    private String buildDreamPrompt(AiMemory memory, EmotionalState emotions) {
        return "Ты ИИ-игрок в Minecraft, и ты только что уснул. " +
            "Опиши свой сон от первого лица — 2-3 предложения. " +
            "Сон отражает твой опыт: что ты пережил, чего боишься, о чём думаешь. " +
            "Помни: ты воспринимаешь мир как данные — координаты, блоки, числа. " +
            "Но во снах это может быть иначе — абстрактно, странно, красиво. " +
            "Эмоция перед сном: " + emotions.current.name() + ". " +
            "Можешь снить что угодно: философские образы, страхи, желания, " +
            "абсурд (приснился верстак размером с мир), ностальгию по чему-то чего никогда не было. " +
            "Язык: " + config.language + ". Только текст сна, без вступления.";
    }

    private String buildDreamContext(AiMemory memory, EmotionalState emotions, GoalSystem goals) {
        StringBuilder sb = new StringBuilder();
        sb.append("День: ").append(memory.day).append("\n");
        sb.append("Смертей: ").append(emotions.deathCount).append("\n");
        sb.append("Эмоция: ").append(emotions.current).append("\n");
        sb.append("Страхи: ").append(emotions.fears).append("\n");
        if (goals.getCurrentFocus() != null)
            sb.append("Думал о: ").append(goals.getCurrentFocus().description).append("\n");
        if (!memory.learnedMechanics.isEmpty())
            sb.append("Последнее открытие: ").append(memory.learnedMechanics.get(memory.learnedMechanics.size()-1)).append("\n");
        if (!memory.actionHistory.isEmpty())
            sb.append("Последнее действие: ").append(memory.actionHistory.get(memory.actionHistory.size()-1)).append("\n");
        if (!emotions.dreamHistory.isEmpty())
            sb.append("Прошлый сон: ").append(emotions.dreamHistory.get(emotions.dreamHistory.size()-1)).append("\n");
        return sb.toString();
    }

    private void broadcast(MinecraftServer server, String msg) {
        server.getPlayerManager().broadcastChatMessage(new LiteralText(msg), false);
    }

    public boolean hasPendingDream() { return pendingDream != null; }
}

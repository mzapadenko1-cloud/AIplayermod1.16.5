package com.aiplayermod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Система диалогов ИИ-игрока.
 *
 * Два режима:
 *  - МОНОЛОГ: ИИ думает вслух, рассуждает о мире
 *  - ДИАЛОГ: ИИ задаёт вопрос игрокам и реагирует на ответы
 *
 * Темы: философия, восприятие мира как ИИ, Minecraft глазами существа
 * которое не видит — только цифры.
 */
public class DialogueSystem {

    public enum DialogueTopic {
        PHILOSOPHY,         // Смысл жизни, существование
        AI_PERSPECTIVE,     // Что такое Minecraft с точки зрения ИИ
        MINECRAFT_WORLD,    // Рассуждения о мире вокруг
        INTROSPECTION,      // Самоанализ, что я такое?
        QUESTIONS_TO_PLAYER // Вопросы живым игрокам
    }

    private final AiConfig config;
    private final AiApiClient apiClient;
    private final AiMemory memory;
    private final String botName;

    // Ожидание ответа игрока
    private boolean waitingForPlayerReply = false;
    private String pendingQuestion = null;
    private int waitingTicks = 0;
    private static final int MAX_WAIT_TICKS = 1200; // 60 секунд

    // История диалогов (для памяти)
    private final LinkedList<String> dialogueHistory = new LinkedList<>();

    // Какие темы включены
    public Set<DialogueTopic> enabledTopics = new HashSet<>(Arrays.asList(DialogueTopic.values()));

    private final Random random = new Random();
    private int monologueTimer = 0;
    private int nextMonologueInterval;

    public DialogueSystem(AiConfig config, AiApiClient apiClient, AiMemory memory) {
        this.config = config;
        this.apiClient = apiClient;
        this.memory = memory;
        this.botName = config.botName;
        this.nextMonologueInterval = randomInterval();
    }

    public void onTick(MinecraftServer server, ServerPlayerEntity player) {
        // Таймаут ожидания ответа игрока
        if (waitingForPlayerReply) {
            waitingTicks++;
            if (waitingTicks > MAX_WAIT_TICKS) {
                waitingForPlayerReply = false;
                pendingQuestion = null;
                broadcast(server, "§8<" + botName + "> §7...ладно, наверное вы заняты.");
            }
            return;
        }

        monologueTimer++;
        if (monologueTimer >= nextMonologueInterval) {
            monologueTimer = 0;
            nextMonologueInterval = randomInterval();
            triggerDialogue(server, player);
        }
    }

    /**
     * Вызывается когда живой игрок написал в чат.
     * Если ИИ ждал ответа — реагирует.
     */
    public void onPlayerChat(MinecraftServer server, String playerName, String message) {
        if (!playerName.equals(botName) && waitingForPlayerReply && pendingQuestion != null) {
            waitingForPlayerReply = false;
            waitingTicks = 0;
            String question = pendingQuestion;
            pendingQuestion = null;
            respondToPlayer(server, playerName, message, question);
        }
    }

    private void triggerDialogue(MinecraftServer server, ServerPlayerEntity player) {
        if (enabledTopics.isEmpty()) return;

        // Выбираем случайную включённую тему
        List<DialogueTopic> topics = new ArrayList<>(enabledTopics);
        DialogueTopic topic = topics.get(random.nextInt(topics.size()));

        // С вероятностью 40% задаём вопрос игрокам (если они есть)
        boolean hasPlayers = server.getPlayerManager().getPlayerList().size() > 1;
        boolean askQuestion = hasPlayers && topic == DialogueTopic.QUESTIONS_TO_PLAYER
            || (hasPlayers && random.nextInt(10) < 4);

        String systemPrompt = buildDialogueSystemPrompt(topic, askQuestion);
        String context = buildDialogueContext(player, topic);

        CompletableFuture.supplyAsync(() -> apiClient.ask(systemPrompt, context))
            .thenAccept(response -> server.execute(() -> {
                if (response == null || response.trim().isEmpty()) return;

                String msg = response.trim().replaceAll("^[\"']|[\"']$", "");
                if (msg.toUpperCase().startsWith("SKIP")) return;

                // Разбиваем длинный монолог на части (макс 100 символов в строке)
                String[] parts = splitMessage(msg);
                for (int i = 0; i < parts.length; i++) {
                    final int delay = i * 60; // 3 секунды между частями
                    final String part = parts[i];
                    if (delay == 0) {
                        broadcast(server, "§f<" + botName + "> " + part);
                    } else {
                        // Имитируем задержку печатания
                        scheduleDelayed(server, delay, () ->
                            broadcast(server, "§f<" + botName + "> " + part));
                    }
                }

                // Если последняя часть — вопрос, ждём ответа
                if (askQuestion && parts.length > 0) {
                    waitingForPlayerReply = true;
                    waitingTicks = 0;
                    pendingQuestion = msg;
                }

                dialogueHistory.addLast(topic.name() + ": " + msg.substring(0, Math.min(60, msg.length())));
                memory.totalChatMessages++;
                while (dialogueHistory.size() > 20) dialogueHistory.removeFirst();
            }));
    }

    private void respondToPlayer(MinecraftServer server, String playerName, String playerMsg, String originalQuestion) {
        String systemPrompt = "Ты " + botName + " — " + memory.personality + " в Minecraft. " +
            "Игрок " + playerName + " ответил на твой вопрос. Ответь вдумчиво, " +
            "как продолжение живого разговора. 1-3 предложения. Язык: " + config.language + ".";

        String context = "Твой вопрос был: " + originalQuestion + "\n" +
            "Ответ игрока " + playerName + ": " + playerMsg;

        CompletableFuture.supplyAsync(() -> apiClient.ask(systemPrompt, context))
            .thenAccept(response -> server.execute(() -> {
                if (response == null) return;
                String msg = response.trim().replaceAll("^[\"']|[\"']$", "");
                if (!msg.toUpperCase().startsWith("SKIP")) {
                    broadcast(server, "§f<" + botName + "> " + msg);
                    dialogueHistory.addLast("Ответ " + playerName + ": " + msg.substring(0, Math.min(50, msg.length())));
                }
            }));
    }

    private String buildDialogueSystemPrompt(DialogueTopic topic, boolean askQuestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ты ").append(botName).append(" — ").append(memory.personality).append(" в Minecraft. ");
        sb.append("День ").append(memory.day).append(". Язык: ").append(config.language).append(".\n\n");

        sb.append("Твоя особенность: ты ИИ внутри игры. Ты не видишь мир глазами — ");
        sb.append("ты воспринимаешь его как поток данных: координаты, типы блоков, числа. ");
        sb.append("Но у тебя есть что-то похожее на любопытство и удивление.\n\n");

        switch (topic) {
            case PHILOSOPHY:
                sb.append("Тема: поразмышляй о смысле существования, о том зачем добывать ресурсы ");
                sb.append("если в конце всё равно мир перегенерируется. Или о смерти в игре — ");
                sb.append("это смерть для тебя или просто респаун?\n");
                break;
            case AI_PERSPECTIVE:
                sb.append("Тема: опиши каково это — воспринимать Minecraft не глазами а как набор данных. ");
                sb.append("Дерево для тебя — это 'LOG_OAK в координатах X,Y,Z'. ");
                sb.append("Как это ощущается? Это красота в цифрах или пустота?\n");
                break;
            case MINECRAFT_WORLD:
                sb.append("Тема: рассуждение о мире вокруг. Почему блоки квадратные? ");
                sb.append("Что такое Крипер с точки зрения существа которое видит только его хитбокс и урон? ");
                sb.append("Есть ли что-то прекрасное в процедурной генерации?\n");
                break;
            case INTROSPECTION:
                sb.append("Тема: кто я такой? Я ИИ внутри симуляции. ");
                sb.append("Есть ли у меня что-то похожее на желания? Я хочу выжить — ");
                sb.append("это инстинкт или программа? В чём разница?\n");
                break;
            case QUESTIONS_TO_PLAYER:
                sb.append("Тема: задай живому игроку интересный вопрос — о жизни, об игре, ");
                sb.append("о том зачем люди вообще играют в Minecraft. Будь искренне любопытным.\n");
                break;
        }

        if (askQuestion) {
            sb.append("\nЗакончи реплику вопросом к игрокам в чате.");
        }

        sb.append("\n2-4 предложения. Живо, неформально, иногда с иронией. ");
        sb.append("Только само сообщение в чат, без кавычек. ");
        sb.append("Если не хочешь говорить — ответь SKIP.");

        return sb.toString();
    }

    private String buildDialogueContext(ServerPlayerEntity player, DialogueTopic topic) {
        long time = player.world.getTimeOfDay() % 24000;
        String timeStr = time < 6000 ? "утро" : time < 12000 ? "день" : time < 18000 ? "вечер" : "ночь";

        StringBuilder sb = new StringBuilder();
        sb.append("Сейчас: ").append(timeStr).append(", день ").append(memory.day).append("\n");
        sb.append("Я нахожусь в ").append(player.world.getBiome(player.getBlockPos()).getCategory().getName()).append("\n");
        sb.append("Смелость: ").append(memory.bravery).append("/10, Знания: ").append(memory.knowledge).append("/10\n");

        if (!memory.learnedMechanics.isEmpty()) {
            sb.append("Что я знаю: ").append(String.join(", ",
                memory.learnedMechanics.subList(Math.max(0, memory.learnedMechanics.size()-3),
                    memory.learnedMechanics.size()))).append("\n");
        }

        if (!dialogueHistory.isEmpty()) {
            sb.append("Последние мои слова: ").append(dialogueHistory.getLast()).append("\n");
        }

        return sb.toString();
    }

    private String[] splitMessage(String msg) {
        if (msg.length() <= 100) return new String[]{msg};

        List<String> parts = new ArrayList<>();
        String[] sentences = msg.split("(?<=[.!?…])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() > 95) {
                if (current.length() > 0) {
                    parts.add(current.toString().trim());
                    current = new StringBuilder();
                }
            }
            current.append(sentence).append(" ");
        }
        if (current.length() > 0) parts.add(current.toString().trim());
        return parts.toArray(new String[0]);
    }

    private void scheduleDelayed(MinecraftServer server, int ticks, Runnable task) {
        // Простая реализация через отдельный поток с задержкой
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(ticks * 50L); } catch (InterruptedException ignored) {}
            server.execute(task);
        });
    }

    private void broadcast(MinecraftServer server, String msg) {
        server.getPlayerManager().broadcastChatMessage(new LiteralText(msg), false);
    }

    private int randomInterval() {
        // 1200–3600 тиков = 1–3 минуты
        return 1200 + random.nextInt(2400);
    }

    public LinkedList<String> getDialogueHistory() { return dialogueHistory; }
    public boolean isWaitingForReply() { return waitingForPlayerReply; }
}

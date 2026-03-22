package com.aiplayermod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

public class AiPlayerCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(CommandManager.literal("aiplayer")
            .requires(source -> source.hasPermissionLevel(2))

            // /aiplayer start <ник_игрока>
            // Запустить ИИ-управление для игрока
            .then(CommandManager.literal("start")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> {
                        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                        AiConfig config = AiConfig.load();

                        if (config.apiKey.equals("ВСТАВЬ_СВОЙ_КЛЮЧ_СЮДА")) {
                            ctx.getSource().sendError(new LiteralText(
                                Formatting.RED + "[AIPlayer] Сначала укажи API ключ в конфиге: config/aiplayermod.json"
                            ));
                            return 0;
                        }

                        AiPlayerManager manager = new AiPlayerManager(config, target.getName().getString());
                        AiPlayerMod.setManager(manager);

                        ctx.getSource().sendFeedback(new LiteralText(
                            Formatting.GREEN + "[AIPlayer] ИИ-игрок запущен для " + target.getName().getString() +
                            " (провайдер: " + config.provider + ", модель: " + config.model + ")"
                        ), true);

                        // Приветствие от ИИ
                        ctx.getSource().getServer().getPlayerManager().broadcastChatMessage(
                            new LiteralText(Formatting.AQUA + "[" + config.botName + "] " +
                                Formatting.WHITE + "Привет! Я ИИ-игрок. Первый раз в Майнкрафте... " +
                                "с чего вообще начать? Наверное срублю дерево!"), false
                        );
                        return 1;
                    })))

            // /aiplayer stop
            .then(CommandManager.literal("stop")
                .executes(ctx -> {
                    AiPlayerMod.setManager(null);
                    ctx.getSource().sendFeedback(new LiteralText(
                        Formatting.YELLOW + "[AIPlayer] ИИ-игрок остановлен."
                    ), true);
                    return 1;
                }))

            // /aiplayer status
            .then(CommandManager.literal("status")
                .executes(ctx -> {
                    AiPlayerManager mgr = AiPlayerMod.getManager();
                    if (mgr == null) {
                        ctx.getSource().sendFeedback(new LiteralText(
                            Formatting.RED + "[AIPlayer] Не запущен. Используй /aiplayer start <игрок>"
                        ), false);
                    } else {
                        AiConfig cfg = AiConfig.load();
                        ctx.getSource().sendFeedback(new LiteralText(
                            Formatting.GREEN + "[AIPlayer] Активен для: " + mgr.getTargetPlayerName() + "\n" +
                            Formatting.AQUA + "Провайдер: " + cfg.provider + " | Модель: " + cfg.model + "\n" +
                            Formatting.WHITE + "Памяти: " + mgr.getMemory().size() + "/" + cfg.memorySize + " записей\n" +
                            Formatting.GRAY + "Последнее: " + (mgr.getMemory().isEmpty() ? "нет" : mgr.getMemory().getLast())
                        ), false);
                    }
                    return 1;
                }))

            // /aiplayer memory
            .then(CommandManager.literal("memory")
                .executes(ctx -> {
                    AiPlayerManager mgr = AiPlayerMod.getManager();
                    if (mgr == null) {
                        ctx.getSource().sendError(new LiteralText(Formatting.RED + "[AIPlayer] Не запущен."));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(new LiteralText(
                        Formatting.GOLD + "=== Память ИИ-игрока ==="
                    ), false);
                    for (String entry : mgr.getMemory()) {
                        ctx.getSource().sendFeedback(new LiteralText(
                            Formatting.GRAY + "• " + Formatting.WHITE + entry
                        ), false);
                    }
                    if (mgr.getMemory().isEmpty()) {
                        ctx.getSource().sendFeedback(new LiteralText(Formatting.GRAY + "(пусто)"), false);
                    }
                    return 1;
                }))

            // /aiplayer interval <тики>
            .then(CommandManager.literal("interval")
                .then(CommandManager.argument("ticks", IntegerArgumentType.integer(20, 1200))
                    .executes(ctx -> {
                        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                        AiConfig cfg = AiConfig.load();
                        cfg.thinkIntervalTicks = ticks;
                        cfg.save();
                        ctx.getSource().sendFeedback(new LiteralText(
                            Formatting.GREEN + "[AIPlayer] Интервал установлен: " + ticks + " тиков (" + (ticks / 20) + " сек)"
                        ), true);
                        return 1;
                    })))

            // /aiplayer reload
            .then(CommandManager.literal("reload")
                .executes(ctx -> {
                    // Перезапустить с новым конфигом
                    AiPlayerManager mgr = AiPlayerMod.getManager();
                    if (mgr != null) {
                        AiConfig cfg = AiConfig.load();
                        AiPlayerManager newMgr = new AiPlayerManager(cfg, mgr.getTargetPlayerName());
                        AiPlayerMod.setManager(newMgr);
                        ctx.getSource().sendFeedback(new LiteralText(
                            Formatting.GREEN + "[AIPlayer] Конфиг перезагружен."
                        ), true);
                    } else {
                        ctx.getSource().sendError(new LiteralText(Formatting.RED + "[AIPlayer] Сначала запусти /aiplayer start"));
                    }
                    return 1;
                }))
        );

        // Алиас /ai -> /aiplayer
        dispatcher.register(CommandManager.literal("ai")
            .redirect(dispatcher.getRoot().getChild("aiplayer"))
        );
    }
}

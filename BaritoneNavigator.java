package com.aiplayermod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Обёртка над Baritone API.
 * Baritone — клиентский мод, поэтому весь код здесь исполняется на клиенте.
 *
 * Если Baritone не установлен — тихо деградирует до "нет навигации"
 * и логирует предупреждение.
 *
 * Установка Baritone для Fabric 1.16.5:
 *   Скачать baritone-fabric-1.6.x.jar с https://github.com/cabaletta/baritone/releases
 *   и положить в папку mods/ рядом с этим модом.
 */
public class BaritoneNavigator {

    private static boolean baritoneAvailable = false;
    private static boolean checkedAvailability = false;

    // Проверяем наличие Baritone через reflection — не падаем если его нет
    public static boolean isAvailable() {
        if (!checkedAvailability) {
            checkedAvailability = true;
            try {
                Class.forName("baritone.api.BaritoneAPI");
                baritoneAvailable = true;
                AiPlayerMod.LOGGER.info("[AIPlayer] Baritone найден! Навигация активна.");
            } catch (ClassNotFoundException e) {
                baritoneAvailable = false;
                AiPlayerMod.LOGGER.warn("[AIPlayer] Baritone не найден. Используется базовое движение. " +
                    "Скачай baritone-fabric-1.6.x.jar и положи в mods/");
            }
        }
        return baritoneAvailable;
    }

    /**
     * Идти к указанной позиции
     */
    public static void goTo(BlockPos target) {
        if (!isAvailable()) return;
        try {
            Object baritone = getLocalBaritone();
            if (baritone == null) return;

            // baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(target))
            Object customGoal = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object goal = goalBlockClass.getConstructor(BlockPos.class).newInstance(target);
            customGoal.getClass().getMethod("setGoalAndPath",
                Class.forName("baritone.api.pathing.goals.Goal")).invoke(customGoal, goal);

            AiPlayerMod.LOGGER.info("[AIPlayer-Nav] Идём к " + target.toShortString());
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer-Nav] Ошибка навигации: " + e.getMessage());
        }
    }

    /**
     * Копать блок (Baritone сам найдёт путь и добудет)
     */
    public static void mineBlock(String blockName) {
        if (!isAvailable()) return;
        try {
            Object baritone = getLocalBaritone();
            if (baritone == null) return;

            // baritone.getMineProcess().mine(blockName)
            Object mineProcess = baritone.getClass().getMethod("getMineProcess").invoke(baritone);
            // Получаем класс блока через Minecraft registry
            net.minecraft.block.Block block = net.minecraft.util.registry.Registry.BLOCK
                .get(new net.minecraft.util.Identifier(blockName));
            mineProcess.getClass()
                .getMethod("mine", net.minecraft.block.Block[].class)
                .invoke(mineProcess, new Object[]{new net.minecraft.block.Block[]{block}});

            AiPlayerMod.LOGGER.info("[AIPlayer-Nav] Майним: " + blockName);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer-Nav] Ошибка майнинга: " + e.getMessage());
        }
    }

    /**
     * Строить (idти к позиции и поставить блок)
     */
    public static void buildAt(BlockPos pos, net.minecraft.block.BlockState state) {
        if (!isAvailable()) return;
        try {
            Object baritone = getLocalBaritone();
            if (baritone == null) return;
            // Просто идём туда — реальный build process сложнее, используем GoalBlock
            Object customGoal = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object goal = goalBlockClass.getConstructor(BlockPos.class).newInstance(pos);
            customGoal.getClass().getMethod("setGoalAndPath",
                Class.forName("baritone.api.pathing.goals.Goal")).invoke(customGoal, goal);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer-Nav] Ошибка buildAt: " + e.getMessage());
        }
    }

    /**
     * Исследовать — случайная точка в радиусе
     */
    public static void explore(BlockPos from, int radius) {
        if (!isAvailable()) return;
        try {
            Object baritone = getLocalBaritone();
            if (baritone == null) return;
            java.util.Random rnd = new java.util.Random();
            double angle = rnd.nextDouble() * 2 * Math.PI;
            int dist = radius / 2 + rnd.nextInt(radius / 2);
            BlockPos target = from.add(
                (int)(Math.cos(angle) * dist), 0, (int)(Math.sin(angle) * dist));
            Object customGoal = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            Class<?> goalXZClass = Class.forName("baritone.api.pathing.goals.GoalXZ");
            Object goal = goalXZClass.getConstructor(int.class, int.class)
                .newInstance(target.getX(), target.getZ());
            customGoal.getClass().getMethod("setGoalAndPath",
                Class.forName("baritone.api.pathing.goals.Goal")).invoke(customGoal, goal);
            AiPlayerMod.LOGGER.info("[AIPlayer-Nav] Исследуем → " + target.toShortString());
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer-Nav] Ошибка explore: " + e.getMessage());
        }
    }

    /**
     * Остановить текущий путь
     */
    public static void stop() {
        if (!isAvailable()) return;
        try {
            Object baritone = getLocalBaritone();
            if (baritone == null) return;
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            pathingBehavior.getClass().getMethod("cancelEverything").invoke(pathingBehavior);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer-Nav] Ошибка stop: " + e.getMessage());
        }
    }

    /**
     * Проверить, идёт ли сейчас навигация
     */
    public static boolean isNavigating() {
        if (!isAvailable()) return false;
        try {
            Object baritone = getLocalBaritone();
            if (baritone == null) return false;
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            return (boolean) pathingBehavior.getClass().getMethod("isPathing").invoke(pathingBehavior);
        } catch (Exception e) {
            return false;
        }
    }

    private static Object getLocalBaritone() throws Exception {
        Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
        Object provider = apiClass.getMethod("getProvider").invoke(null);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;
        return provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
    }
}

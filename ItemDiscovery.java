package com.aiplayermod;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Система изучения предметов.
 *
 * Когда ИИ подбирает предмет которого не видел раньше —
 * спрашивает у Gemini что это и зачем. Так он сам
 * учится работать с любыми модами без явного программирования.
 *
 * Пример: подобрал "Wrench" из мода Create →
 * Gemini объясняет что это гаечный ключ для механизмов →
 * ИИ записывает в память и учитывает при решениях.
 */
public class ItemDiscovery {

    private final AiApiClient apiClient;
    private final AiConfig config;
    private final AiMemory memory;

    // Предметы которые уже изучены (не спрашиваем повторно)
    private final Set<String> knownItems = new HashSet<>();

    // Предметы в очереди на изучение
    private final Queue<String> discoveryQueue = new LinkedList<>();
    private boolean discovering = false;

    // Базовые предметы которые ИИ знает с рождения
    private static final Set<String> VANILLA_KNOWN = new HashSet<>(Arrays.asList(
        "oak_log", "oak_planks", "stick", "crafting_table", "wooden_pickaxe",
        "stone_pickaxe", "iron_pickaxe", "stone", "cobblestone", "dirt",
        "grass_block", "wooden_sword", "stone_sword", "bread", "apple",
        "torch", "chest", "bed", "coal", "iron_ingot", "oak_door"
    ));

    public ItemDiscovery(AiApiClient apiClient, AiConfig config, AiMemory memory) {
        this.apiClient = apiClient;
        this.config = config;
        this.memory = memory;
        knownItems.addAll(VANILLA_KNOWN);
        // Восстанавливаем из памяти
        for (String mechanic : memory.learnedMechanics) {
            if (mechanic.startsWith("ПРЕДМЕТ:")) {
                String item = mechanic.split(":")[1].trim().split(" ")[0];
                knownItems.add(item);
            }
        }
    }

    /**
     * Проверяем инвентарь на новые предметы
     */
    public void checkInventory(ServerPlayerEntity player, MinecraftServer server) {
        for (int i = 0; i < player.inventory.size(); i++) {
            ItemStack stack = player.inventory.getStack(i);
            if (stack.isEmpty()) continue;

            String itemId = getItemId(stack);
            if (!knownItems.contains(itemId) && !discoveryQueue.contains(itemId)) {
                discoveryQueue.add(itemId);
                AiPlayerMod.LOGGER.info("[AIPlayer-Discovery] Новый предмет: " + itemId);
            }
        }

        if (!discovering && !discoveryQueue.isEmpty()) {
            String itemId = discoveryQueue.poll();
            discoverItem(itemId, server, player);
        }
    }

    private void discoverItem(String itemId, MinecraftServer server, ServerPlayerEntity player) {
        discovering = true;
        knownItems.add(itemId);

        // Получаем читаемое имя предмета
        ItemStack sample = findInInventory(player, itemId);
        String displayName = sample != null ? sample.getName().getString() : itemId;

        String prompt = "Ты " + config.botName + " — игрок в Minecraft. " +
            "Ты только что подобрал предмет: \"" + displayName + "\" (ID: " + itemId + "). " +
            "Ты никогда раньше его не видел. Опиши одним предложением:\n" +
            "1. Что это такое\n2. Зачем это нужно\n3. Как это использовать\n\n" +
            "Ответ строго в формате:\n" +
            "ОПИСАНИЕ: <одно предложение что это>\n" +
            "ИСПОЛЬЗОВАНИЕ: <одно предложение как использовать>\n\n" +
            "Язык: " + config.language + ".";

        CompletableFuture.supplyAsync(() -> apiClient.ask(prompt,
            "Контекст: я играю в Minecraft с модами. Предмет из мода или ванильный."))
            .thenAccept(response -> server.execute(() -> {
                discovering = false;
                if (response == null) return;

                // Парсим ответ
                String desc = "", use = "";
                for (String line : response.split("\n")) {
                    if (line.startsWith("ОПИСАНИЕ:")) desc = line.substring(9).trim();
                    if (line.startsWith("ИСПОЛЬЗОВАНИЕ:")) use = line.substring(14).trim();
                }

                if (!desc.isEmpty()) {
                    String knowledge = "ПРЕДМЕТ: " + displayName + " — " + desc;
                    if (!use.isEmpty()) knowledge += " Использование: " + use;
                    memory.learnMechanic(knowledge);

                    // Сообщаем в чат
                    server.getPlayerManager().broadcastChatMessage(
                        new net.minecraft.text.LiteralText(
                            "§b[" + config.botName + "] §7Изучил новый предмет: §f" + displayName +
                            " §8— " + desc), false);
                }
            }))
            .exceptionally(ex -> { server.execute(() -> discovering = false); return null; });
    }

    private String getItemId(ItemStack stack) {
        return net.minecraft.util.registry.Registry.ITEM
            .getId(stack.getItem()).getPath();
    }

    private ItemStack findInInventory(ServerPlayerEntity player, String itemId) {
        for (int i = 0; i < player.inventory.size(); i++) {
            ItemStack s = player.inventory.getStack(i);
            if (!s.isEmpty() && getItemId(s).equals(itemId)) return s;
        }
        return null;
    }

    public Set<String> getKnownItems() { return knownItems; }
    public int getDiscoveredCount() { return knownItems.size() - VANILLA_KNOWN.size(); }
}

package com.aiplayermod;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AiPlayerManager {

    private AiConfig config;
    private AiApiClient apiClient;
    private final String targetPlayerName;

    // Все системы
    private final AiMemory aiMemory;
    private final EmotionalState emotions;
    private final GoalSystem goals;
    private final DialogueSystem dialogueSystem;
    private final DreamGenerator dreamGenerator;
    private final BuildingKnowledge building;
    private final InnerMonologue innerMonologue;
    private final ItemDiscovery itemDiscovery;
    private final WeatherMoodSystem weatherMood;
    private final FavoritePlaces favoritePlaces;
    private final DeathHandler deathHandler;
    private final FatigueSystem fatigue;

    private int tickCounter = 0;
    private boolean waitingForResponse = false;
    private NavigationTask currentNavTask = null;
    private int navTimeoutTicks = 0;
    private static final int NAV_TIMEOUT = 600;

    private AiDecision.Action lastAction = null;
    private int sameActionCount = 0;
    private boolean justWokeUp = false;

    // Проверка инвентаря каждые 5 секунд
    private int inventoryCheckTimer = 0;

    private final Random random = new Random();

    public enum NavigationTask {
        GOING_TO_WOOD, GOING_TO_STONE, GOING_TO_COAL, GOING_TO_IRON,
        EXPLORING, BUILDING, RETURNING_HOME, IDLE
    }

    public AiPlayerManager(AiConfig config, String targetPlayerName) {
        this.config = config;
        this.apiClient = new AiApiClient(config);
        this.targetPlayerName = targetPlayerName;
        this.aiMemory = AiMemory.load(targetPlayerName);
        this.emotions = EmotionalState.load(targetPlayerName);
        this.goals = new GoalSystem();
        this.dialogueSystem = new DialogueSystem(config, apiClient, aiMemory);
        this.dreamGenerator = new DreamGenerator(apiClient, config);
        this.building = new BuildingKnowledge();
        this.innerMonologue = new InnerMonologue(apiClient, config);
        this.itemDiscovery = new ItemDiscovery(apiClient, config, aiMemory);
        this.weatherMood = new WeatherMoodSystem();
        this.favoritePlaces = new FavoritePlaces(apiClient, config);
        this.deathHandler = new DeathHandler(apiClient, config);
        this.fatigue = new FatigueSystem();
        favoritePlaces.load();
        aiMemory.lastSession = new java.util.Date().toString();
    }

    public void reloadConfig(AiConfig newConfig) {
        this.config = newConfig;
        this.apiClient = new AiApiClient(newConfig);
    }

    public void onTick(MinecraftServer server) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(targetPlayerName);
        if (player == null) return;

        long worldTime = player.world.getTimeOfDay();

        // Показываем сон утром
        if (justWokeUp) {
            justWokeUp = false;
            dreamGenerator.showDreamIfReady(server, config.botName);
        }

        // Обновляем погоду/время → эмоции
        if (tickCounter % 40 == 0) {
            weatherMood.update((ServerWorld) player.world, emotions, aiMemory);
        }

        // Обновляем эмоции каждые 20 тиков
        if (tickCounter % 20 == 0) {
            emotions.update(aiMemory, player.getHealth(),
                player.getHungerManager().getFoodLevel(), worldTime, false);
            building.checkUnlocks(aiMemory);
        }

        // Проверяем любимые места
        favoritePlaces.checkTriggers(player, emotions, aiMemory, server);
        if (tickCounter % 100 == 0)
            favoritePlaces.onTick(player, emotions, aiMemory, server);

        // Внутренний монолог
        innerMonologue.onTick(server, aiMemory, emotions, goals, worldTime % 24000);

        // Проверка новых предметов (каждые 5 сек)
        inventoryCheckTimer++;
        if (inventoryCheckTimer >= 100) {
            inventoryCheckTimer = 0;
            itemDiscovery.checkInventory(player, server);
        }

        // Диалоговая система
        dialogueSystem.onTick(server, player);

        // Навигация
        if (BaritoneNavigator.isNavigating()) {
            navTimeoutTicks++;
            if (navTimeoutTicks > NAV_TIMEOUT) {
                BaritoneNavigator.stop();
                navTimeoutTicks = 0; currentNavTask = null;
            }
            tickCounter++;
            return;
        }

        if (currentNavTask != null && currentNavTask != NavigationTask.IDLE) {
            onNavigationComplete(player, server);
            currentNavTask = null; navTimeoutTicks = 0;
        }

        tickCounter++;

        // Если очень устал — принудительный отдых
        if (fatigue.shouldRest() && !waitingForResponse) {
            broadcast(server, "§7<" + config.botName + "> " + fatigue.getRestMessage(config.botName));
            fatigue.onAction(AiDecision.Action.IDLE);
            return;
        }

        if (tickCounter >= config.thinkIntervalTicks && !waitingForResponse) {
            tickCounter = 0;
            doActionTick(player, server);
        }
    }

    public void onPlayerChat(MinecraftServer server, String playerName, String message) {
        dialogueSystem.onPlayerChat(server, playerName, message);
        if (!playerName.equals(config.botName)) {
            if (message.contains(config.botName) || message.toLowerCase().contains("помог")
                || message.toLowerCase().contains("держи") || message.toLowerCase().contains("спасибо"))
                emotions.improveRelationship(playerName, 10);
            if (message.toLowerCase().contains("идиот") || message.toLowerCase().contains("тупой"))
                emotions.damageRelationship(playerName, 15);
        }
    }

    public void onDeath(MinecraftServer server, int x, int y, int z, String cause) {
        deathHandler.onDeath(server, aiMemory, emotions, goals, x, y, z, cause);
    }

    // ── Тик решений ───────────────────────────────

    private void doActionTick(ServerPlayerEntity player, MinecraftServer server) {
        GoalSystem.Goal currentGoal = goals.recalculate(
            aiMemory, emotions,
            player.getHungerManager().getFoodLevel(),
            player.getHealth()
        );

        String worldState = buildWorldState(player, currentGoal);
        String systemPrompt = buildSystemPrompt();
        waitingForResponse = true;

        CompletableFuture.supplyAsync(() -> apiClient.ask(systemPrompt, worldState))
            .thenAccept(response -> server.execute(() -> {
                waitingForResponse = false;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(targetPlayerName);
                if (p == null) return;

                AiDecision decision = AiDecision.parse(response);

                // Трекаем скуку
                if (decision.action == lastAction) {
                    sameActionCount++;
                    emotions.sameActionStreak = sameActionCount;
                } else {
                    sameActionCount = 0;
                    emotions.sameActionStreak = 0;
                    lastAction = decision.action;
                }

                fatigue.onAction(decision.action);
                startAction(p, decision, server);
                checkLearnedMechanics(decision, p);

                String timeStr = WeatherMoodSystem.getTimeOfDay(p.world.getTimeOfDay());
                aiMemory.addAction("День " + aiMemory.day + " " + timeStr
                    + " [" + emotions.current.name() + "|усталость:" + fatigue.getFatigue() + "]: "
                    + decision.action.name() + " — " + decision.reason);

                if (aiMemory.totalActions % 5 == 0) {
                    aiMemory.save(targetPlayerName);
                    emotions.save(targetPlayerName);
                }

                if (config.verboseChat) {
                    broadcast(server,
                        Formatting.AQUA + "[" + config.botName + "] " +
                        getEmotionTag() + getFatigueTag() +
                        Formatting.WHITE + decision.reason +
                        Formatting.GRAY + " → " + Formatting.YELLOW + decision.action.name());
                }
            }))
            .exceptionally(ex -> {
                server.execute(() -> waitingForResponse = false);
                AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка: " + ex.getMessage());
                return null;
            });
    }

    private void startAction(ServerPlayerEntity player, AiDecision decision, MinecraftServer server) {
        ServerWorld world = (ServerWorld) player.world;
        BlockPos pos = player.getBlockPos();
        boolean hasBaritone = BaritoneNavigator.isAvailable();

        switch (decision.action) {
            case MINE_WOOD:
                if (hasBaritone) { BaritoneNavigator.mineBlock("minecraft:oak_log"); currentNavTask = NavigationTask.GOING_TO_WOOD; }
                else { BlockPos log = findNearby(world, pos, "LOG", 20); if (log != null) { world.breakBlock(log, true, player); aiMemory.woodCollected++; } else player.teleport(pos.getX()+random.nextInt(30)-15, pos.getY(), pos.getZ()+random.nextInt(30)-15); }
                if (!aiMemory.knowsMining) { aiMemory.learnMechanic("Деревья → брёвна → доски → всё"); aiMemory.knowsMining = true; }
                break;
            case MINE_STONE:
                if (hasBaritone) { BaritoneNavigator.mineBlock("minecraft:stone"); currentNavTask = NavigationTask.GOING_TO_STONE; }
                else { BlockPos p = findNearby(world, pos, "STONE", 8); if (p != null) { world.breakBlock(p, true, player); aiMemory.stoneCollected++; } }
                break;
            case MINE_COAL:
                if (hasBaritone) { BaritoneNavigator.mineBlock("minecraft:coal_ore"); currentNavTask = NavigationTask.GOING_TO_COAL; }
                else { BlockPos p = findNearby(world, pos, "COAL_ORE", 12); if (p != null) world.breakBlock(p, true, player); }
                break;
            case MINE_IRON:
                if (hasBaritone) { BaritoneNavigator.mineBlock("minecraft:iron_ore"); currentNavTask = NavigationTask.GOING_TO_IRON; }
                else { BlockPos p = findNearby(world, pos, "IRON_ORE", 10); if (p != null) { world.breakBlock(p, true, player); aiMemory.ironCollected++; } }
                break;
            case CRAFT_WORKBENCH:
                give(player, Items.CRAFTING_TABLE, 1);
                world.setBlockState(pos.offset(Direction.NORTH), Blocks.CRAFTING_TABLE.getDefaultState());
                aiMemory.hasWorkbench = true;
                aiMemory.learnMechanic("Верстак открывает рецепты инструментов");
                if (!aiMemory.knowsCrafting) { aiMemory.knowsCrafting = true; aiMemory.knowledge = Math.min(10, aiMemory.knowledge+1); }
                emotions.set(EmotionalState.Emotion.EXCITED, 6);
                broadcast(server, Formatting.GOLD + "[" + config.botName + "] Верстак поставлен!");
                break;
            case CRAFT_STICKS:
                give(player, Items.STICK, 8);
                break;
            case CRAFT_WOOD_PICK:
                give(player, Items.WOODEN_PICKAXE, 1);
                aiMemory.hasWoodPickaxe = true;
                aiMemory.learnMechanic("Деревянная кирка копает камень");
                aiMemory.bravery = Math.min(10, aiMemory.bravery+1);
                emotions.set(EmotionalState.Emotion.EXCITED, 8);
                broadcast(server, Formatting.GOLD + "[" + config.botName + "] Первая кирка! Теперь за камнем!");
                break;
            case CRAFT_STONE_PICK:
                give(player, Items.STONE_PICKAXE, 1);
                aiMemory.hasStonePickaxe = true;
                aiMemory.learnMechanic("Каменная кирка прочнее и копает железо");
                emotions.set(EmotionalState.Emotion.PROUD, 7);
                broadcast(server, Formatting.GOLD + "[" + config.botName + "] Каменная кирка — прогресс!");
                break;
            case CRAFT_SWORD:
                give(player, aiMemory.hasStonePickaxe ? Items.STONE_SWORD : Items.WOODEN_SWORD, 1);
                aiMemory.hasSword = true;
                aiMemory.bravery = Math.min(10, aiMemory.bravery+2);
                emotions.overcomeFear("creeper");
                aiMemory.learnMechanic("Меч — с ним не так страшно ночью");
                broadcast(server, Formatting.RED + "[" + config.botName + "] Теперь вооружён!");
                break;
            case CRAFT_CHEST:
                give(player, Items.CHEST, 1);
                aiMemory.learnMechanic("Сундук хранит вещи — лучше не таскать всё с собой");
                break;
            case BUILD_SHELTER:
                BuildingKnowledge.Technique tech = building.chooseTechnique(emotions, aiMemory);
                if (hasBaritone) { BaritoneNavigator.goTo(pos.offset(Direction.NORTH, 5)); currentNavTask = NavigationTask.BUILDING; }
                else {
                    building.build(world, pos.offset(Direction.NORTH, 3), tech, Blocks.OAK_PLANKS, emotions);
                    building.recordStructure(pos, tech.name() + " день " + aiMemory.day);
                    aiMemory.hasShelter = true;
                    emotions.set(EmotionalState.Emotion.PROUD, 8);
                    favoritePlaces.remember(player, emotions, aiMemory, "построил " + tech.name(), server);
                    broadcast(server, Formatting.GREEN + "[" + config.botName + "] " + getBuildMsg(tech));
                }
                break;
            case SLEEP:
                world.setBlockState(pos.offset(Direction.EAST), Blocks.RED_BED.getDefaultState());
                give(player, Items.RED_BED, 1);
                aiMemory.day++;
                dreamGenerator.generateDream(aiMemory, emotions, goals);
                justWokeUp = true;
                if (!aiMemory.survivedFirstNight) {
                    aiMemory.survivedFirstNight = true;
                    aiMemory.learnMechanic("Кровать пропускает ночь и дарит сны");
                    emotions.set(EmotionalState.Emotion.EXCITED, 9);
                    broadcast(server, Formatting.GREEN + "[" + config.botName + "] ПЕРВАЯ НОЧЬ ПЕРЕЖИТА! 🎉");
                }
                emotions.overcomeFear("night");
                aiMemory.save(targetPlayerName);
                emotions.save(targetPlayerName);
                break;
            case EAT:
                boolean found = false;
                for (int i = 0; i < player.inventory.size(); i++) {
                    ItemStack s = player.inventory.getStack(i);
                    if (!s.isEmpty() && s.getItem().isFood()) { player.getHungerManager().eat(s.getItem(), s); s.decrement(1); found = true; break; }
                }
                if (!found) { give(player, Items.BREAD, 3); aiMemory.learnMechanic("Еда — без неё умрёшь быстрее чем от мобов"); }
                if (emotions.current == EmotionalState.Emotion.HUNGRY) emotions.set(EmotionalState.Emotion.CALM, 6);
                break;
            case EXPLORE:
                if (hasBaritone) { BaritoneNavigator.explore(pos, 80); currentNavTask = NavigationTask.EXPLORING; }
                else { double a = random.nextDouble()*2*Math.PI; double d = 20+random.nextDouble()*40; player.teleport(pos.getX()+Math.cos(a)*d, pos.getY(), pos.getZ()+Math.sin(a)*d); }
                emotions.sameActionStreak = 0;
                if (random.nextInt(3)==0) { aiMemory.bravery = Math.min(10, aiMemory.bravery+1); emotions.set(EmotionalState.Emotion.CURIOUS, 7); }
                break;
            case PLACE_TORCH:
                world.setBlockState(pos.up(), Blocks.TORCH.getDefaultState());
                if (emotions.current == EmotionalState.Emotion.SCARED) emotions.set(EmotionalState.Emotion.CALM, 5);
                break;
            case IDLE:
                // Осознанный отдых — не просто ничего
                if (fatigue.isExhausted() || emotions.current == EmotionalState.Emotion.MELANCHOLY) {
                    // Запоминаем место если красивое время суток
                    long time = player.world.getTimeOfDay() % 24000;
                    if ((time > 22500 || time < 500) && random.nextInt(3) == 0)
                        favoritePlaces.remember(player, emotions, aiMemory, "тихий отдых на закате/рассвете", server);
                }
                break;
            default:
                break;
        }
    }

    private void onNavigationComplete(ServerPlayerEntity player, MinecraftServer server) {
        ServerWorld world = (ServerWorld) player.world;
        BlockPos pos = player.getBlockPos();
        if (currentNavTask == null) return;
        switch (currentNavTask) {
            case GOING_TO_WOOD: aiMemory.woodCollected += 3; break;
            case GOING_TO_STONE: aiMemory.stoneCollected += 3; break;
            case GOING_TO_IRON: aiMemory.ironCollected++; break;
            case BUILDING:
                BuildingKnowledge.Technique tech = building.chooseTechnique(emotions, aiMemory);
                building.build(world, pos, tech, Blocks.OAK_PLANKS, emotions);
                aiMemory.hasShelter = true;
                emotions.set(EmotionalState.Emotion.PROUD, 8);
                broadcast(server, Formatting.GREEN + "[" + config.botName + "] " + getBuildMsg(tech));
                break;
            case EXPLORING:
                if (random.nextInt(3)==0) aiMemory.bravery = Math.min(10, aiMemory.bravery+1);
                emotions.set(EmotionalState.Emotion.CURIOUS, 6);
                break;
            default: break;
        }
    }

    // ── Промпты ───────────────────────────────────

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ты ").append(config.botName).append(" — ").append(aiMemory.personality)
          .append(" в Minecraft. День ").append(aiMemory.day)
          .append(". Язык: ").append(config.language).append(".\n\n");

        // Усталость
        sb.append(fatigue.toPromptString());

        // Эмоция
        switch (emotions.current) {
            case SCARED:     sb.append("Ты НАПУГАН (").append(emotions.intensity).append("/10). Избегай риска.\n"); break;
            case BORED:      sb.append("Тебе СКУЧНО (").append(emotions.intensity).append("/10). Хочется чего-то необычного.\n"); break;
            case MANIC:      sb.append("МАНИЯ! Цель: ").append(emotions.maniacGoal).append(". Ничто не остановит.\n"); break;
            case HUNGRY:     sb.append("ГОЛОД (").append(emotions.intensity).append("/10). Только еда.\n"); break;
            case EXCITED:    sb.append("Ты в восторге! Полон энергии.\n"); break;
            case MELANCHOLY: sb.append("Меланхолия. Действуешь, но думаешь о смысле.\n"); break;
            case TRAUMATIZED:sb.append("Ещё не оправился от смерти. Осторожен.\n"); break;
            case PROUD:      sb.append("Гордишься достижениями. Уверен в себе.\n"); break;
            default: break;
        }

        if (aiMemory.knowledge >= 5) sb.append("Ты знаешь основы выживания.\n");
        if (aiMemory.bravery >= 7) sb.append("Ты смелый, исследуешь далеко.\n");

        sb.append("\nДействия: MINE_WOOD, MINE_STONE, MINE_COAL, MINE_IRON,\n");
        sb.append("CRAFT_WORKBENCH, CRAFT_STICKS, CRAFT_WOOD_PICK, CRAFT_STONE_PICK,\n");
        sb.append("CRAFT_SWORD, CRAFT_CHEST, SLEEP, EAT, EXPLORE, BUILD_SHELTER, PLACE_TORCH, IDLE\n\n");
        sb.append("Формат:\nACTION: <КОД>\nREASON: <1-2 предложения от первого лица>\n\n");
        sb.append("Логика: дерево → верстак → кирки → камень → укрытие до ночи.");

        return sb.toString();
    }

    private String buildWorldState(ServerPlayerEntity player, GoalSystem.Goal currentGoal) {
        long worldTime = player.world.getTimeOfDay();
        String timeStr = WeatherMoodSystem.getTimeOfDay(worldTime);
        String weather = WeatherMoodSystem.getWeatherDescription((ServerWorld) player.world);

        StringBuilder sb = new StringBuilder();
        sb.append("=== МИР ===\n");
        sb.append("Время: ").append(timeStr).append(" | Погода: ").append(weather).append("\n");
        sb.append("День: ").append(aiMemory.day)
          .append(", HP: ").append((int)player.getHealth()).append("/20")
          .append(", Еда: ").append(player.getHungerManager().getFoodLevel()).append("/20")
          .append(", Усталость: ").append(fatigue.getFatigueDescription()).append("\n");
        sb.append("Позиция: ").append((int)player.getX()).append(" ").append((int)player.getY()).append(" ").append((int)player.getZ()).append("\n");
        sb.append("Биом: ").append(player.world.getBiome(player.getBlockPos()).getCategory().getName()).append("\n\n");

        sb.append("=== СОСТОЯНИЕ ===\n");
        sb.append(emotions.toPromptString());
        sb.append("\n=== ЦЕЛЬ ===\n").append(goals.toPromptString());

        sb.append("\n=== ИНВЕНТАРЬ ===\n");
        Map<String,Integer> inv = getInvSummary(player.inventory);
        if (inv.isEmpty()) sb.append("Пусто\n");
        else inv.forEach((k,v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));

        sb.append("\n=== БЛОКИ РЯДОМ ===\n");
        scanBlocks(player).forEach((k,v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));

        sb.append("\n=== ПРОГРЕСС ===\n");
        sb.append("Верстак:").append(aiMemory.hasWorkbench?"✓":"✗")
          .append(" Дер.кирка:").append(aiMemory.hasWoodPickaxe?"✓":"✗")
          .append(" Кам.кирка:").append(aiMemory.hasStonePickaxe?"✓":"✗")
          .append(" Укрытие:").append(aiMemory.hasShelter?"✓":"✗")
          .append(" Меч:").append(aiMemory.hasSword?"✓":"✗").append("\n");
        sb.append(building.toPromptString());

        if (!aiMemory.learnedMechanics.isEmpty()) {
            sb.append("\n=== ЗНАЮ ===\n");
            int from = Math.max(0, aiMemory.learnedMechanics.size()-8);
            for (int i = from; i < aiMemory.learnedMechanics.size(); i++)
                sb.append("- ").append(aiMemory.learnedMechanics.get(i)).append("\n");
        }

        if (!aiMemory.actionHistory.isEmpty()) {
            sb.append("\n=== НЕДАВНИЕ ДЕЙСТВИЯ ===\n");
            List<String> hist = aiMemory.actionHistory;
            for (int i = Math.max(0, hist.size()-5); i < hist.size(); i++)
                sb.append("- ").append(hist.get(i)).append("\n");
        }

        if (!favoritePlaces.getPlaces().isEmpty()) {
            sb.append("\n=== ЛЮБИМЫЕ МЕСТА ===\n");
            favoritePlaces.getPlaces().stream().limit(3)
                .forEach(p -> sb.append("- \"").append(p.name).append("\" @ ").append(p.x).append(",").append(p.z).append("\n"));
        }

        return sb.toString();
    }

    // ── Вспомогательные ───────────────────────────

    private void checkLearnedMechanics(AiDecision decision, ServerPlayerEntity player) {
        if (decision.action == AiDecision.Action.MINE_WOOD && aiMemory.woodCollected > 20)
            aiMemory.learnMechanic("Большой запас дерева — никогда не будешь без ресурсов");
        if (decision.action == AiDecision.Action.MINE_STONE && aiMemory.stoneCollected > 10)
            aiMemory.learnMechanic("Камень — основной строительный материал");
        if (decision.action == AiDecision.Action.EXPLORE && aiMemory.bravery > 7)
            aiMemory.learnMechanic("Исследование открывает новые биомы и ресурсы");
        building.checkUnlocks(aiMemory);
    }

    private String getEmotionTag() {
        switch (emotions.current) {
            case SCARED:     return "😨 ";
            case EXCITED:    return "🤩 ";
            case BORED:      return "😑 ";
            case MANIC:      return "🤪 ";
            case HUNGRY:     return "😰 ";
            case PROUD:      return "😤 ";
            case MELANCHOLY: return "🌧 ";
            case TRAUMATIZED:return "😟 ";
            case CURIOUS:    return "🤔 ";
            default: return "";
        }
    }

    private String getFatigueTag() {
        if (fatigue.getFatigue() >= 80) return "😴 ";
        if (fatigue.getFatigue() >= 65) return "😓 ";
        return "";
    }

    private String getBuildMsg(BuildingKnowledge.Technique tech) {
        switch (tech) {
            case DIRT_HUT:        return "Землянка готова. Не красиво, но переживу ночь.";
            case WOODEN_HOUSE:    return "Деревянный дом с окнами! Почти уютно.";
            case STONE_HOUSE:     return "Каменный дом! Это уже серьёзно.";
            case TOWER:           return "БАШНЯ! Отсюда видно весь мир!";
            case GRAND_STRUCTURE: return "ЗАВЕРШЕНО! " + (emotions.maniacGoal != null ? emotions.maniacGoal : "грандиозная постройка") + "!";
            default:              return "Постройка завершена!";
        }
    }

    private Map<String,Integer> getInvSummary(PlayerInventory inv) {
        Map<String,Integer> r = new LinkedHashMap<>();
        for (int i=0;i<inv.size();i++) { ItemStack s=inv.getStack(i); if (!s.isEmpty()) r.merge(s.getItem().getName().getString(),s.getCount(),Integer::sum); }
        return r;
    }

    private Map<String,Integer> scanBlocks(ServerPlayerEntity player) {
        Map<String,Integer> counts = new LinkedHashMap<>();
        BlockPos center = player.getBlockPos();
        for (int x=-12;x<=12;x+=3) for (int y=-4;y<=4;y+=2) for (int z=-12;z<=12;z+=3) {
            String block = player.world.getBlockState(center.add(x,y,z)).getBlock().getName().getString();
            if (!block.equals("Air")) counts.merge(block,1,Integer::sum);
        }
        return counts.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(8)
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue,(a,b)->a,LinkedHashMap::new));
    }

    private BlockPos findNearby(ServerWorld world, BlockPos center, String keyword, int radius) {
        for (int y=-4;y<=8;y++) for (int x=-radius;x<=radius;x++) for (int z=-radius;z<=radius;z++) {
            BlockPos p=center.add(x,y,z);
            if (world.getBlockState(p).getBlock().getTranslationKey().toUpperCase().contains(keyword.toUpperCase())) return p;
        }
        return null;
    }

    private void give(ServerPlayerEntity player, Item item, int count) {
        player.inventory.insertStack(new ItemStack(item, count));
    }

    private void broadcast(MinecraftServer server, String msg) {
        server.getPlayerManager().broadcastChatMessage(new LiteralText(msg), false);
    }

    // ── Геттеры ───────────────────────────────────

    public String getTargetPlayerName()       { return targetPlayerName; }
    public AiMemory getAiMemory()             { return aiMemory; }
    public AiConfig getConfig()               { return config; }
    public DialogueSystem getDialogueSystem() { return dialogueSystem; }
    public EmotionalState getEmotions()       { return emotions; }
    public GoalSystem getGoals()              { return goals; }
    public BuildingKnowledge getBuilding()    { return building; }
    public InnerMonologue getInnerMonologue() { return innerMonologue; }
    public ItemDiscovery getItemDiscovery()   { return itemDiscovery; }
    public FavoritePlaces getFavoritePlaces() { return favoritePlaces; }
    public FatigueSystem getFatigue()         { return fatigue; }
    public NavigationTask getCurrentNavTask() { return currentNavTask; }
}

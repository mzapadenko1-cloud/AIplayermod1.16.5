package com.aiplayermod;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Система любимых мест.
 *
 * ИИ запоминает координаты мест где происходило что-то значимое:
 * первый восход, место постройки первого дома, место первой смерти,
 * красивый биом, место где нашёл алмазы.
 *
 * Иногда возвращается туда просто так — "хочу снова увидеть тот закат".
 */
public class FavoritePlaces {

    public static class Place {
        public int x, y, z;
        public String name;        // "место где я впервые увидел закат"
        public String memory;      // "был день 3, я был напуган но красиво"
        public String emotion;     // эмоция в момент запоминания
        public int day;
        public int visitCount;

        public Place(int x, int y, int z, String name, String memory, String emotion, int day) {
            this.x=x; this.y=y; this.z=z;
            this.name=name; this.memory=memory;
            this.emotion=emotion; this.day=day;
            this.visitCount=1;
        }

        public BlockPos toBlockPos() { return new BlockPos(x, y, z); }
        public double distanceTo(double px, double py, double pz) {
            return Math.sqrt((x-px)*(x-px)+(z-pz)*(z-pz));
        }
    }

    private final List<Place> places = new ArrayList<>();
    private final AiApiClient apiClient;
    private final AiConfig config;
    private final Random random = new Random();

    // Когда последний раз возвращался к любимому месту
    private int nostalgiaTimer = 0;
    private static final int NOSTALGIA_INTERVAL = 12000; // ~10 мин

    public FavoritePlaces(AiApiClient apiClient, AiConfig config) {
        this.apiClient = apiClient;
        this.config = config;
    }

    /**
     * Запоминает текущее место как значимое
     */
    public void remember(ServerPlayerEntity player, EmotionalState emotions,
                         AiMemory memory, String trigger, MinecraftServer server) {
        int x = (int)player.getX(), y = (int)player.getY(), z = (int)player.getZ();

        // Не запоминаем если уже есть место рядом
        for (Place p : places)
            if (p.distanceTo(x, y, z) < 20) return;

        String biome = player.world.getBiome(player.getBlockPos()).getCategory().getName();

        // Генерируем название места через ИИ
        String prompt = "Ты " + config.botName + " в Minecraft. Ты запоминаешь место. " +
            "Дай этому месту поэтическое короткое название (3-6 слов) и одно предложение о том что ты чувствуешь. " +
            "Биом: " + biome + ". Причина запомнить: " + trigger + ". Эмоция: " + emotions.current + ".\n" +
            "Формат:\nНАЗВАНИЕ: <название>\nПАМЯТЬ: <одно предложение>\nЯзык: " + config.language;

        final int fx = x, fy = y, fz = z;
        CompletableFuture.supplyAsync(() -> apiClient.ask(prompt, "День " + memory.day + ", биом " + biome))
            .thenAccept(response -> server.execute(() -> {
                if (response == null) return;
                String name = "место на день " + memory.day, mem = "";
                for (String line : response.split("\n")) {
                    if (line.startsWith("НАЗВАНИЕ:")) name = line.substring(9).trim();
                    if (line.startsWith("ПАМЯТЬ:")) mem = line.substring(7).trim();
                }
                Place place = new Place(fx, fy, fz, name, mem, emotions.current.name(), memory.day);
                places.add(place);
                save(memory.day);

                server.getPlayerManager().broadcastChatMessage(new LiteralText(
                    "§8§o* " + config.botName + " запомнил это место: §7§o\"" + name + "\" *"), false);
            }));
    }

    /**
     * Иногда возвращается к любимому месту
     */
    public void onTick(ServerPlayerEntity player, EmotionalState emotions,
                       AiMemory memory, MinecraftServer server) {
        nostalgiaTimer++;
        if (nostalgiaTimer < NOSTALGIA_INTERVAL || places.isEmpty()) return;
        nostalgiaTimer = 0;

        // Ностальгия чаще при меланхолии
        int chance = emotions.current == EmotionalState.Emotion.MELANCHOLY ? 3 : 8;
        if (random.nextInt(chance) != 0) return;

        // Выбираем случайное место которое не слишком близко
        List<Place> farPlaces = new ArrayList<>();
        for (Place p : places)
            if (p.distanceTo(player.getX(), player.getY(), player.getZ()) > 50)
                farPlaces.add(p);

        if (farPlaces.isEmpty()) return;

        Place dest = farPlaces.get(random.nextInt(farPlaces.size()));
        dest.visitCount++;

        // Идём туда (или телепортируемся если нет Baritone)
        if (BaritoneNavigator.isAvailable()) {
            BaritoneNavigator.goTo(dest.toBlockPos());
        } else {
            player.teleport(dest.x, dest.y, dest.z);
        }

        String msg = getNostalgiaMessage(dest, emotions);
        server.getPlayerManager().broadcastChatMessage(
            new LiteralText("§f<" + config.botName + "> " + msg), false);
    }

    private String getNostalgiaMessage(Place place, EmotionalState emotions) {
        String[] templates = {
            "Хочу снова побывать у \"" + place.name + "\"...",
            "Вспомнил про \"" + place.name + "\". Иду туда.",
            "\"" + place.name + "\" — там было хорошо. Возвращаюсь.",
            "Почему-то тянет к \"" + place.name + "\".",
            "Был там на день " + place.day + ". " + place.memory
        };
        return templates[new Random().nextInt(templates.length)];
    }

    // ── Триггеры для запоминания ──────────────────

    public void checkTriggers(ServerPlayerEntity player, EmotionalState emotions,
                              AiMemory memory, MinecraftServer server) {
        // Первый восход
        long time = player.world.getTimeOfDay() % 24000;
        if (time >= 23000 && time < 23100 && memory.day == 1)
            remember(player, emotions, memory, "первый рассвет", server);

        // После постройки первого дома
        if (memory.hasShelter && places.stream().noneMatch(p -> p.name.contains("дом") || p.name.contains("укрытие")))
            remember(player, emotions, memory, "построил своё первое укрытие", server);

        // Красивый биом
        String biome = player.world.getBiome(player.getBlockPos()).getCategory().getName();
        if ((biome.equals("JUNGLE") || biome.equals("MESA") || biome.equals("MUSHROOM"))
            && places.stream().noneMatch(p -> p.name.contains(biome.toLowerCase())))
            remember(player, emotions, memory, "необычный красивый биом: " + biome, server);

        // Место первой ночи
        if (memory.day == 1 && time > 13000 && time < 13100)
            remember(player, emotions, memory, "первая ночь — было страшно", server);
    }

    // ── Сохранение ────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public void save(int day) {
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("aiplayermod_places.json");
        try (Writer w = new FileWriter(path.toFile())) {
            GSON.toJson(places, w);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка сохранения мест: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void load() {
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("aiplayermod_places.json");
        if (!path.toFile().exists()) return;
        try (Reader r = new FileReader(path.toFile())) {
            List<Place> loaded = GSON.fromJson(r,
                new com.google.gson.reflect.TypeToken<List<Place>>(){}.getType());
            if (loaded != null) { places.clear(); places.addAll(loaded); }
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка загрузки мест: " + e.getMessage());
        }
    }

    public List<Place> getPlaces() { return places; }
}

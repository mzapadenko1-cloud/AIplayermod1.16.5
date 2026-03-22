package com.aiplayermod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Эмоциональная система ИИ.
 *
 * Эмоции влияют на принятие решений и диалоги.
 * Например: паника → убегать, скука → строить безумные проекты,
 * голод-стресс → приоритет еде над всем остальным.
 */
public class EmotionalState {

    public enum Emotion {
        CALM,       // Спокойствие — нейтральное состояние
        CURIOUS,    // Любопытство — хочет исследовать
        SCARED,     // Страх — после встречи с мобами, ночью
        EXCITED,    // Восторг — новое достижение, новый биом
        BORED,      // Скука — долго делал одно и то же → БЕЗУМНЫЕ ПРОЕКТЫ
        HUNGRY,     // Голод-стресс — приоритет еде
        PROUD,      // Гордость — после постройки чего-то крутого
        MELANCHOLY, // Меланхолия — философские мысли
        MANIC,      // МАНИЯ — состояние "построю Кёльнский собор из верстаков"
        TRAUMATIZED // Травма — после смерти, долго боится
    }

    // Текущая доминирующая эмоция
    public Emotion current = Emotion.CALM;

    // Интенсивность 1-10
    public int intensity = 5;

    // Счётчики для триггеров
    public int sameActionStreak = 0;    // Сколько раз подряд делал одно и то же
    public int lastDeathDay = -100;     // День последней смерти
    public int deathCount = 0;
    public int consecutiveBoredomTicks = 0;

    // Отношения с игроками (имя → отношение -100..100)
    public Map<String, Integer> relationships = new HashMap<>();

    // Текущая МАНИЯ-цель (если есть)
    public String maniacGoal = null;     // "построить Кёльнский собор из верстаков"
    public String maniacMaterial = null; // "верстак"
    public int maniacProgress = 0;
    public int maniacTarget = 0;

    // Травмы и страхи
    public List<String> fears = new ArrayList<>(); // "creeper", "night", "deep_caves"

    // История снов
    public List<String> dreamHistory = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Обновление эмоций ─────────────────────────

    public void update(AiMemory memory, float health, int hunger, long timeOfDay, boolean heardExplosion) {

        // Голод — высший приоритет
        if (hunger <= 6) {
            set(Emotion.HUNGRY, 8 + (int)((6 - hunger) * 0.5f));
            return;
        }

        // Страх смерти
        if (health <= 6) {
            set(Emotion.SCARED, 9);
            return;
        }

        // Ночной страх (если есть травма)
        if (timeOfDay > 13000 && timeOfDay < 23000 && fears.contains("night")) {
            set(Emotion.SCARED, 6);
            return;
        }

        // Взрыв рядом
        if (heardExplosion) {
            set(Emotion.SCARED, 8);
            addFear("creeper");
            return;
        }

        // Мания — если застрял в скуке долго
        if (current == Emotion.BORED && intensity >= 7 && maniacGoal == null) {
            triggerMania();
            return;
        }

        if (maniacGoal != null) {
            set(Emotion.MANIC, 9);
            return;
        }

        // Скука от повторений
        if (sameActionStreak > 5) {
            set(Emotion.BORED, Math.min(10, sameActionStreak - 3));
            return;
        }

        // Травма от недавней смерти
        if (memory.day - lastDeathDay < 3) {
            set(Emotion.TRAUMATIZED, 7 - (memory.day - lastDeathDay) * 2);
            return;
        }

        // Гордость после достижения
        if (memory.hasShelter && current == Emotion.CALM && memory.day < 5) {
            set(Emotion.PROUD, 7);
            return;
        }

        // Меланхолия ночью
        if (timeOfDay > 13000 && memory.day > 10) {
            set(Emotion.MELANCHOLY, 5);
            return;
        }

        // Любопытство по умолчанию для новых мест
        if (memory.day < 3) {
            set(Emotion.CURIOUS, 7);
            return;
        }

        set(Emotion.CALM, 5);
    }

    public void set(Emotion e, int i) {
        current = e;
        intensity = Math.max(1, Math.min(10, i));
    }

    // ── Мания ─────────────────────────────────────

    private void triggerMania() {
        set(Emotion.MANIC, 10);
        sameActionStreak = 0;

        String[] goals = {
            "построить Кёльнский собор",
            "воссоздать Эйфелеву башню",
            "выложить моё имя из блоков видимое из космоса",
            "построить подземный город",
            "создать лабиринт из стекла",
            "сделать пирамиду до облаков",
            "построить точную копию моего укрытия но в 100 раз больше",
            "выложить спираль Фибоначчи на земле"
        };

        String[] materials = {
            "верстака", "камня", "земли", "дерева", "стекла", "досок"
        };

        Random rnd = new Random();
        maniacGoal = goals[rnd.nextInt(goals.length)];
        maniacMaterial = materials[rnd.nextInt(materials.length)];
        maniacTarget = 200 + rnd.nextInt(800);
        maniacProgress = 0;

        AiPlayerMod.LOGGER.info("[AIPlayer] МАНИЯ АКТИВИРОВАНА: " + maniacGoal);
    }

    public void progressMania(int amount) {
        if (maniacGoal == null) return;
        maniacProgress += amount;
        if (maniacProgress >= maniacTarget) {
            // Завершил — гордость и усталость
            maniacGoal = null;
            maniacMaterial = null;
            set(Emotion.PROUD, 10);
        }
    }

    public boolean isInMania() { return maniacGoal != null && current == Emotion.MANIC; }

    // ── Отношения ─────────────────────────────────

    public void improveRelationship(String player, int amount) {
        relationships.merge(player, amount, (a, b) -> Math.min(100, a + b));
    }

    public void damageRelationship(String player, int amount) {
        relationships.merge(player, -amount, (a, b) -> Math.max(-100, a + b));
    }

    public int getRelationship(String player) {
        return relationships.getOrDefault(player, 0);
    }

    public String getRelationshipName(String player) {
        int r = getRelationship(player);
        if (r >= 70) return "друг";
        if (r >= 30) return "знакомый";
        if (r >= 0) return "нейтральный";
        if (r >= -40) return "раздражает";
        return "враг";
    }

    // ── Страхи ────────────────────────────────────

    public void addFear(String fear) {
        if (!fears.contains(fear)) fears.add(fear);
    }

    public void overcomeFear(String fear) { fears.remove(fear); }

    // ── Сны ───────────────────────────────────────

    public void addDream(String dream) {
        dreamHistory.add(dream);
        while (dreamHistory.size() > 10) dreamHistory.remove(0);
    }

    // ── Описание для промпта ──────────────────────

    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ЭМОЦИЯ: ").append(current.name()).append(" (").append(intensity).append("/10)\n");

        if (isInMania()) {
            sb.append("МАНИЯ: ").append(maniacGoal).append(" из ").append(maniacMaterial)
              .append(" [").append(maniacProgress).append("/").append(maniacTarget).append("]\n");
        }

        if (!fears.isEmpty()) sb.append("СТРАХИ: ").append(String.join(", ", fears)).append("\n");
        if (!relationships.isEmpty()) {
            sb.append("ОТНОШЕНИЯ: ");
            relationships.forEach((p, r) -> sb.append(p).append("=").append(getRelationshipName(p)).append(" "));
            sb.append("\n");
        }
        if (!dreamHistory.isEmpty())
            sb.append("ПОСЛЕДНИЙ СОН: ").append(dreamHistory.get(dreamHistory.size()-1)).append("\n");

        return sb.toString();
    }

    // ── Сохранение ────────────────────────────────

    public static EmotionalState load(String playerName) {
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("aiplayermod_emotions_" + playerName + ".json");
        if (path.toFile().exists()) {
            try (Reader r = new FileReader(path.toFile())) {
                EmotionalState s = GSON.fromJson(r, EmotionalState.class);
                if (s.relationships == null) s.relationships = new HashMap<>();
                if (s.fears == null) s.fears = new ArrayList<>();
                if (s.dreamHistory == null) s.dreamHistory = new ArrayList<>();
                return s;
            } catch (Exception e) {
                AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка загрузки эмоций: " + e.getMessage());
            }
        }
        return new EmotionalState();
    }

    public void save(String playerName) {
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("aiplayermod_emotions_" + playerName + ".json");
        try (Writer w = new FileWriter(path.toFile())) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            AiPlayerMod.LOGGER.error("[AIPlayer] Ошибка сохранения эмоций: " + e.getMessage());
        }
    }
}

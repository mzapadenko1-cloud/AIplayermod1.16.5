package com.aiplayermod;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Погода и время суток влияют на настроение ИИ.
 *
 * Дождь → меланхолия
 * Гроза → страх (особенно если есть страх молний)
 * Утро → бодрость, энергия
 * День → деятельность
 * Закат → задумчивость
 * Ночь → философия или страх (зависит от опыта)
 * Ясный день после дождя → восторг
 */
public class WeatherMoodSystem {

    private boolean wasRaining = false;
    private boolean wasThundering = false;
    private String lastTimeOfDay = "";
    private int moodCooldown = 0;

    public void update(ServerWorld world, EmotionalState emotions, AiMemory memory) {
        if (moodCooldown > 0) { moodCooldown--; return; }

        boolean isRaining = world.isRaining();
        boolean isThundering = world.isThundering();
        long time = world.getTimeOfDay() % 24000;
        String timeOfDay = time < 1000 ? "рассвет" : time < 6000 ? "утро"
                         : time < 11000 ? "день" : time < 13000 ? "закат"
                         : time < 23000 ? "ночь" : "поздняя ночь";

        // Гроза — страх если есть предрасположенность
        if (isThundering && !wasThundering) {
            emotions.addFear("thunder");
            if (emotions.current != EmotionalState.Emotion.MANIC) {
                emotions.set(EmotionalState.Emotion.SCARED, 7);
                moodCooldown = 200;
            }
            wasThundering = true;
            return;
        }

        // Гроза кончилась
        if (!isThundering && wasThundering) {
            wasThundering = false;
            if (emotions.current == EmotionalState.Emotion.SCARED)
                emotions.set(EmotionalState.Emotion.CALM, 5);
        }

        // Дождь начался
        if (isRaining && !wasRaining) {
            if (emotions.current == EmotionalState.Emotion.CALM ||
                emotions.current == EmotionalState.Emotion.CURIOUS) {
                emotions.set(EmotionalState.Emotion.MELANCHOLY, 6);
                moodCooldown = 400;
            }
            wasRaining = true;
        }

        // Дождь кончился — радость!
        if (!isRaining && wasRaining) {
            wasRaining = false;
            if (emotions.current == EmotionalState.Emotion.MELANCHOLY) {
                emotions.set(EmotionalState.Emotion.EXCITED, 6);
                moodCooldown = 300;
            }
        }

        // Переходы времени суток
        if (!timeOfDay.equals(lastTimeOfDay)) {
            lastTimeOfDay = timeOfDay;
            applyTimeMood(timeOfDay, emotions, memory);
        }
    }

    private void applyTimeMood(String timeOfDay, EmotionalState emotions, AiMemory memory) {
        // Не перебиваем сильные эмоции
        if (emotions.intensity >= 8 && emotions.current != EmotionalState.Emotion.CALM) return;
        if (emotions.isInMania()) return;

        switch (timeOfDay) {
            case "рассвет":
                // Рассвет всегда немного радостный
                emotions.set(EmotionalState.Emotion.EXCITED, 5);
                break;
            case "утро":
                // Утром энергичный
                if (emotions.current == EmotionalState.Emotion.MELANCHOLY ||
                    emotions.current == EmotionalState.Emotion.TRAUMATIZED)
                    emotions.set(EmotionalState.Emotion.CALM, 6);
                break;
            case "закат":
                // Закат — задумчивость
                emotions.set(EmotionalState.Emotion.MELANCHOLY, 4);
                break;
            case "ночь":
                // Ночью — зависит от опыта
                if (memory.day < 3 || emotions.fears.contains("night")) {
                    emotions.set(EmotionalState.Emotion.SCARED, 5 + (3 - Math.min(3, memory.day)));
                } else if (memory.day > 10) {
                    // Опытный игрок ночью философствует
                    emotions.set(EmotionalState.Emotion.MELANCHOLY, 5);
                }
                break;
            case "поздняя ночь":
                if (memory.day > 5)
                    emotions.set(EmotionalState.Emotion.MELANCHOLY, 6);
                break;
        }
    }

    public static String getTimeOfDay(long worldTime) {
        long time = worldTime % 24000;
        if (time < 1000) return "рассвет";
        if (time < 6000) return "утро";
        if (time < 11000) return "день";
        if (time < 13000) return "закат";
        if (time < 23000) return "ночь";
        return "поздняя ночь";
    }

    public static String getWeatherDescription(ServerWorld world) {
        if (world.isThundering()) return "гроза ⛈";
        if (world.isRaining()) return "дождь 🌧";
        return "ясно ☀";
    }
}

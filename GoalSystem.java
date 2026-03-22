package com.aiplayermod;

import java.util.*;

/**
 * Система долгосрочных целей.
 *
 * ИИ сам ставит себе цели на несколько дней и планомерно к ним идёт.
 * Приоритеты динамически пересчитываются под ситуацию:
 * голод > опасность > текущая мания > долгосрочная цель > выживание.
 */
public class GoalSystem {

    public enum GoalType {
        SURVIVE_FIRST_NIGHT,   // Пережить первую ночь
        BUILD_SHELTER,          // Построить укрытие
        GET_FOOD,               // Найти еду
        MAKE_FARM,              // Сделать ферму
        GET_WOOD_TOOLS,         // Деревянные инструменты
        GET_STONE_TOOLS,        // Каменные инструменты
        GET_IRON_TOOLS,         // Железные инструменты
        EXPLORE_BIOME,          // Исследовать новый биом
        BUILD_GRAND_PROJECT,    // Грандиозный проект (мания)
        MINE_DEEP,              // Копать глубоко за ресурсами
        BUILD_FARM,             // Ферма животных
        FIND_VILLAGE,           // Найти деревню
        MAKE_MAP,               // Создать карту территории
        FREE_TIME               // Свободное время — делать что хочется
    }

    public static class Goal {
        public GoalType type;
        public String description;
        public int priority;        // 1-100, выше = важнее
        public int progressPercent; // 0-100
        public boolean completed;
        public String notes;        // Заметки ИИ о прогрессе

        public Goal(GoalType type, String description, int priority) {
            this.type = type;
            this.description = description;
            this.priority = priority;
            this.progressPercent = 0;
            this.completed = false;
        }
    }

    private final List<Goal> activeGoals = new ArrayList<>();
    private Goal currentFocus = null;

    public GoalSystem() {
        // Стартовые цели
        addGoal(new Goal(GoalType.SURVIVE_FIRST_NIGHT, "Пережить первую ночь", 90));
        addGoal(new Goal(GoalType.GET_WOOD_TOOLS, "Скрафтить деревянные инструменты", 70));
        addGoal(new Goal(GoalType.BUILD_SHELTER, "Построить укрытие", 80));
    }

    public void addGoal(Goal goal) {
        activeGoals.add(goal);
        activeGoals.sort((a, b) -> b.priority - a.priority);
    }

    /**
     * Пересчитывает приоритеты и выбирает текущий фокус
     * с учётом эмоций, памяти и ситуации.
     */
    public Goal recalculate(AiMemory memory, EmotionalState emotions, int hunger, float health) {

        // Экстренные переопределения
        if (hunger <= 6) {
            Goal emergency = new Goal(GoalType.GET_FOOD, "СРОЧНО: найти еду!", 100);
            emergency.notes = "Голод " + hunger + "/20 — критично";
            return currentFocus = emergency;
        }

        if (health <= 5) {
            Goal emergency = new Goal(GoalType.BUILD_SHELTER, "СРОЧНО: найти укрытие!", 100);
            emergency.notes = "Здоровье " + (int)health + "/20";
            return currentFocus = emergency;
        }

        // Мания перехватывает управление
        if (emotions.isInMania()) {
            Goal mania = new Goal(GoalType.BUILD_GRAND_PROJECT,
                emotions.maniacGoal + " из " + emotions.maniacMaterial, 95);
            mania.notes = "Прогресс: " + emotions.maniacProgress + "/" + emotions.maniacTarget;
            return currentFocus = mania;
        }

        // Обновляем прогресс существующих целей
        updateGoalProgress(memory, emotions);

        // Добавляем новые цели по мере прогресса
        evolveGoals(memory, emotions);

        // Выбираем самую приоритетную незавершённую
        for (Goal g : activeGoals) {
            if (!g.completed) {
                currentFocus = g;
                return g;
            }
        }

        // Всё выполнено — свободное время → скука → мания
        emotions.sameActionStreak += 3;
        Goal free = new Goal(GoalType.FREE_TIME, "Делать что хочется", 50);
        free.notes = "Все основные цели выполнены";
        return currentFocus = free;
    }

    private void updateGoalProgress(AiMemory memory, EmotionalState emotions) {
        for (Goal g : activeGoals) {
            switch (g.type) {
                case SURVIVE_FIRST_NIGHT:
                    if (memory.survivedFirstNight) { g.completed = true; g.progressPercent = 100; }
                    break;
                case BUILD_SHELTER:
                    g.progressPercent = memory.hasShelter ? 100 : (memory.hasWorkbench ? 40 : (memory.woodCollected > 5 ? 20 : 0));
                    g.completed = memory.hasShelter;
                    break;
                case GET_WOOD_TOOLS:
                    g.progressPercent = memory.hasWoodPickaxe ? 100 : (memory.hasWorkbench ? 60 : (memory.woodCollected > 0 ? 20 : 0));
                    g.completed = memory.hasWoodPickaxe;
                    break;
                case GET_STONE_TOOLS:
                    g.progressPercent = memory.hasStonePickaxe ? 100 : (memory.stoneCollected > 0 ? 50 : 0);
                    g.completed = memory.hasStonePickaxe;
                    break;
                case GET_IRON_TOOLS:
                    g.progressPercent = memory.hasIronPickaxe ? 100 : (memory.ironCollected > 0 ? 30 : 0);
                    g.completed = memory.hasIronPickaxe;
                    break;
                default:
                    break;
            }
        }
    }

    private void evolveGoals(AiMemory memory, EmotionalState emotions) {
        boolean hasGoal = activeGoals.stream().anyMatch(g -> !g.completed);

        if (memory.hasWoodPickaxe && !hasGoalOfType(GoalType.GET_STONE_TOOLS)) {
            addGoal(new Goal(GoalType.GET_STONE_TOOLS, "Скрафтить каменные инструменты", 75));
        }
        if (memory.hasStonePickaxe && !hasGoalOfType(GoalType.GET_IRON_TOOLS)) {
            addGoal(new Goal(GoalType.GET_IRON_TOOLS, "Добыть железо и скрафтить железные инструменты", 70));
        }
        if (memory.hasShelter && memory.day > 3 && !hasGoalOfType(GoalType.MAKE_FARM)) {
            addGoal(new Goal(GoalType.MAKE_FARM, "Сделать ферму чтобы не голодать", 65));
        }
        if (memory.day > 7 && !hasGoalOfType(GoalType.EXPLORE_BIOME)) {
            addGoal(new Goal(GoalType.EXPLORE_BIOME, "Исследовать новые биомы", 40));
        }
        if (memory.day > 5 && !hasGoalOfType(GoalType.FIND_VILLAGE)) {
            addGoal(new Goal(GoalType.FIND_VILLAGE, "Найти деревню — там жители и торговля", 35));
        }

        // Длинная скука → грандиозный проект
        if (emotions.current == EmotionalState.Emotion.BORED && emotions.intensity >= 6
            && !hasGoalOfType(GoalType.BUILD_GRAND_PROJECT)) {
            addGoal(new Goal(GoalType.BUILD_GRAND_PROJECT,
                "Построить что-нибудь грандиозное и безумное", 80));
        }
    }

    private boolean hasGoalOfType(GoalType type) {
        return activeGoals.stream().anyMatch(g -> g.type == type);
    }

    public String toPromptString() {
        if (currentFocus == null) return "Цель: нет\n";
        StringBuilder sb = new StringBuilder();
        sb.append("ТЕКУЩАЯ ЦЕЛЬ: ").append(currentFocus.description)
          .append(" [").append(currentFocus.progressPercent).append("%]\n");
        if (currentFocus.notes != null)
            sb.append("ЗАМЕТКИ: ").append(currentFocus.notes).append("\n");

        sb.append("ВСЕ ЦЕЛИ:\n");
        for (Goal g : activeGoals) {
            if (!g.completed) {
                sb.append("  ").append(g.priority >= 90 ? "❗" : "•")
                  .append(" ").append(g.description)
                  .append(" [").append(g.progressPercent).append("%]\n");
            }
        }
        return sb.toString();
    }

    public List<Goal> getActiveGoals() { return activeGoals; }
    public Goal getCurrentFocus() { return currentFocus; }
}

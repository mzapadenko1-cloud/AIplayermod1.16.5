package com.aiplayermod;

/**
 * Система усталости.
 *
 * После долгой работы ИИ хочет отдохнуть — не просто IDLE,
 * а осознанный отдых: сидит, смотрит на закат, думает вслух.
 *
 * Усталость накапливается от активных действий и
 * восстанавливается от отдыха, сна, еды.
 *
 * При высокой усталости:
 * - Чаще выбирает IDLE
 * - В диалогах говорит что устал
 * - Внутренний монолог становится медитативным
 * - Снижается интенсивность действий
 */
public class FatigueSystem {

    // 0-100: 0=отдохнул, 100=измотан
    private int fatigue = 0;

    // Накопленные часы работы без отдыха
    private int workTicks = 0;

    private static final int MAX_FATIGUE = 100;
    private static final int FATIGUE_THRESHOLD = 65; // Выше — хочет отдохнуть
    private static final int REST_THRESHOLD = 30;    // Ниже — восстановился

    // Последнее состояние для GUI
    private String lastRestReason = "";

    public void onAction(AiDecision.Action action) {
        switch (action) {
            // Физически тяжёлые действия
            case MINE_WOOD:
            case MINE_STONE:
            case MINE_COAL:
            case MINE_IRON:
                fatigue = Math.min(MAX_FATIGUE, fatigue + 8);
                workTicks++;
                break;
            case BUILD_SHELTER:
                fatigue = Math.min(MAX_FATIGUE, fatigue + 12);
                workTicks++;
                break;
            case EXPLORE:
                fatigue = Math.min(MAX_FATIGUE, fatigue + 5);
                workTicks++;
                break;
            // Крафт — немного утомляет
            case CRAFT_WORKBENCH:
            case CRAFT_WOOD_PICK:
            case CRAFT_STONE_PICK:
            case CRAFT_SWORD:
                fatigue = Math.min(MAX_FATIGUE, fatigue + 3);
                break;
            // Восстановление
            case SLEEP:
                fatigue = Math.max(0, fatigue - 40);
                workTicks = 0;
                lastRestReason = "поспал";
                break;
            case EAT:
                fatigue = Math.max(0, fatigue - 10);
                lastRestReason = "поел";
                break;
            case IDLE:
                fatigue = Math.max(0, fatigue - 15);
                lastRestReason = "отдохнул";
                break;
            default:
                break;
        }
    }

    public boolean isExhausted() { return fatigue >= FATIGUE_THRESHOLD; }
    public boolean isRested() { return fatigue <= REST_THRESHOLD; }
    public int getFatigue() { return fatigue; }
    public int getWorkTicks() { return workTicks; }

    public String getFatigueDescription() {
        if (fatigue >= 80) return "измотан";
        if (fatigue >= 65) return "устал";
        if (fatigue >= 40) return "немного устал";
        if (fatigue >= 20) return "в норме";
        return "отдохнул";
    }

    /**
     * Добавляет контекст усталости в промпт
     */
    public String toPromptString() {
        if (fatigue < 20) return "";
        return "УСТАЛОСТЬ: " + getFatigueDescription() + " (" + fatigue + "/100). " +
            (isExhausted() ? "Очень хочется отдохнуть — всё даётся с трудом. " : "") + "\n";
    }

    /**
     * Должен ли ИИ отдохнуть прямо сейчас
     */
    public boolean shouldRest() {
        return fatigue >= 80;
    }

    public String getRestMessage(String botName) {
        String[] msgs = {
            "Нужен перерыв. Просто посижу немного.",
            "Руки устали. Отдохну и посмотрю на мир.",
            "Столько работы... дай хоть минуту.",
            "Устал. Сяду и подумаю.",
            "Работал " + workTicks + " раз подряд без отдыха. Пора остановиться."
        };
        return msgs[new java.util.Random().nextInt(msgs.length)];
    }
}

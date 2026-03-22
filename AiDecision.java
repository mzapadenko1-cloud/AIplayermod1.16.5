package com.aiplayermod;

/**
 * Разбирает текстовый ответ ИИ на конкретное действие.
 *
 * ИИ отвечает строго в формате:
 * ACTION: <код_действия>
 * REASON: <объяснение>
 *
 * Коды действий:
 *   MINE_WOOD       - рубить дерево
 *   MINE_STONE      - копать камень
 *   MINE_COAL       - копать уголь
 *   MINE_IRON       - копать железо
 *   CRAFT_WORKBENCH - поставить верстак
 *   CRAFT_STICKS    - скрафтить палки
 *   CRAFT_WOOD_PICK - деревянная кирка
 *   CRAFT_STONE_PICK- каменная кирка
 *   CRAFT_SWORD     - скрафтить меч
 *   CRAFT_CHEST     - скрафтить сундук
 *   SLEEP           - поспать (ночь)
 *   EAT             - есть еду из инвентаря
 *   EXPLORE         - исследовать местность
 *   BUILD_SHELTER   - строить укрытие
 *   PLACE_TORCH     - поставить факел
 *   IDLE            - подождать
 */
public class AiDecision {

    public enum Action {
        MINE_WOOD, MINE_STONE, MINE_COAL, MINE_IRON,
        CRAFT_WORKBENCH, CRAFT_STICKS, CRAFT_WOOD_PICK, CRAFT_STONE_PICK,
        CRAFT_SWORD, CRAFT_CHEST,
        SLEEP, EAT, EXPLORE, BUILD_SHELTER, PLACE_TORCH,
        IDLE
    }

    public final Action action;
    public final String reason;
    public final String rawResponse;

    public AiDecision(Action action, String reason, String rawResponse) {
        this.action = action;
        this.reason = reason;
        this.rawResponse = rawResponse;
    }

    public static AiDecision parse(String response) {
        if (response == null || response.isEmpty()) {
            return new AiDecision(Action.IDLE, "Нет ответа", response);
        }

        String actionStr = "";
        String reason = response;

        String upper = response.toUpperCase();

        // Ищем строку ACTION:
        for (String line : response.split("\n")) {
            if (line.toUpperCase().startsWith("ACTION:")) {
                actionStr = line.substring(7).trim().toUpperCase();
            }
            if (line.toUpperCase().startsWith("REASON:")) {
                reason = line.substring(7).trim();
            }
        }

        // Если ИИ не следовал формату - пробуем угадать из текста
        if (actionStr.isEmpty()) {
            if (upper.contains("РУБИ") || upper.contains("ДЕРЕВ") || upper.contains("WOOD")) actionStr = "MINE_WOOD";
            else if (upper.contains("КАМ") || upper.contains("STONE")) actionStr = "MINE_STONE";
            else if (upper.contains("УГОЛЬ") || upper.contains("COAL")) actionStr = "MINE_COAL";
            else if (upper.contains("ЖЕЛЕЗО") || upper.contains("IRON")) actionStr = "MINE_IRON";
            else if (upper.contains("ВЕРСТАК") || upper.contains("WORKBENCH")) actionStr = "CRAFT_WORKBENCH";
            else if (upper.contains("СПА") || upper.contains("SLEEP") || upper.contains("НОЧЬ")) actionStr = "SLEEP";
            else if (upper.contains("ЕЩ") || upper.contains("EAT") || upper.contains("ЕДА")) actionStr = "EAT";
            else if (upper.contains("ИССЛЕД") || upper.contains("EXPLOR")) actionStr = "EXPLORE";
            else if (upper.contains("УКРЫТ") || upper.contains("SHELTER") || upper.contains("ДОМ")) actionStr = "BUILD_SHELTER";
            else actionStr = "IDLE";
        }

        Action action;
        try {
            action = Action.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            action = Action.IDLE;
        }

        return new AiDecision(action, reason, response);
    }
}

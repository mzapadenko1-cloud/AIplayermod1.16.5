package com.aiplayermod.client;

import com.aiplayermod.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.List;

public class AiPlayerScreen extends Screen {

    private AiConfig config;
    // 0=настройки 1=личность 2=цели 3=дневник 4=места 5=baritone
    private int page = 0;

    private TextFieldWidget apiKeyField, botNameField, intervalField,
                            providerField, modelField, languageField;
    private String statusMsg = "";
    private int statusTimer = 0;

    private static final String[] PERSONALITIES = {
        "любопытный новичок", "осторожный выживальщик", "смелый авантюрист",
        "дружелюбный болтун", "молчаливый строитель", "Кент (уверенный эксперт)"
    };
    private int persIdx = 0;

    private static final int BG   = 0xDD080810;
    private static final int GOLD = 0xFFFFAA00;
    private static final int AQUA = 0xFF00FFFF;
    private static final int GRN  = 0xFF55FF55;
    private static final int RED  = 0xFFFF5555;
    private static final int GRAY = 0xFF888888;
    private static final int WHT  = 0xFFFFFFFF;
    private static final int PURP = 0xFFFF55FF;
    private static final int CYAN = 0xFF55FFFF;

    public AiPlayerScreen() {
        super(new LiteralText("AI Player v5"));
        config = AiConfig.load();
        for (int i = 0; i < PERSONALITIES.length; i++)
            if (PERSONALITIES[i].equals(config.personality)) { persIdx = i; break; }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        // Шапка с вкладками
        String[] icons = {"⚙","🎭","🎯","📓","📍","🧭"};
        int tw = 30, sx = cx - (icons.length * tw) / 2;
        for (int i = 0; i < icons.length; i++) {
            final int idx = i;
            addButton(new ButtonWidget(sx + i*tw, 18, tw-2, 13,
                new LiteralText(page == i ? "§e" + icons[i] : "§7" + icons[i]),
                b -> { page = idx; clearChildren(); init(); }));
        }
        addButton(new ButtonWidget(width - 46, 3, 42, 13,
            new LiteralText("§cЗакрыть"), b -> onClose()));

        switch (page) {
            case 0: initSettings(cx); break;
            case 1: initPersonality(cx); break;
            case 3: initDialogues(cx); break;
        }
    }

    // ── Инициализация вкладок ─────────────────────

    private void initSettings(int cx) {
        int y = 36, lx = cx-100, fw = 200;
        apiKeyField   = f(lx, y, fw, 18, config.apiKey, 200);     y+=22;
        botNameField  = f(lx, y, fw, 18, config.botName, 32);     y+=22;
        providerField = f(lx, y, 95, 18, config.provider, 20);
        modelField    = f(cx+5, y, 95, 18, config.model, 50);     y+=22;
        intervalField = f(lx, y, 95, 18, ""+config.thinkIntervalTicks, 6);
        addButton(new ButtonWidget(cx+5, y, 95, 18,
            new LiteralText(config.verboseChat ? "§aЧат:ВКЛ" : "§cЧат:ВЫКЛ"),
            b -> { config.verboseChat = !config.verboseChat;
                   b.setMessage(new LiteralText(config.verboseChat?"§aЧат:ВКЛ":"§cЧат:ВЫКЛ")); })); y+=22;
        languageField = f(lx, y, fw, 18, config.language, 20);    y+=22;
        addButton(new ButtonWidget(lx, y, fw, 18,
            new LiteralText("§dХарактер: §f"+config.personality),
            b -> { persIdx=(persIdx+1)%PERSONALITIES.length; config.personality=PERSONALITIES[persIdx];
                   b.setMessage(new LiteralText("§dХарактер: §f"+config.personality)); })); y+=22;
        addButton(new ButtonWidget(lx, y, fw, 20, new LiteralText("§a💾 Сохранить"), b -> save())); y+=24;
        addButton(new ButtonWidget(lx, y, fw, 14, new LiteralText("§cСбросить всю память ИИ"),
            b -> { AiPlayerManager m = AiPlayerMod.getClientManager();
                if (m!=null){m.getAiMemory().reset();m.getAiMemory().save(m.getTargetPlayerName());showStatus("§cПамять сброшена!");}
                else showStatus("§eНе запущен"); }));
    }

    private void initPersonality(int cx) {
        int y = 36, lx = cx-100;
        for (int i = 0; i < PERSONALITIES.length; i++) {
            final int idx = i;
            boolean sel = PERSONALITIES[i].equals(config.personality);
            addButton(new ButtonWidget(lx, y, 200, 16,
                new LiteralText((sel?"§a▶ ":"§7  ")+PERSONALITIES[i]),
                b -> { persIdx=idx; config.personality=PERSONALITIES[idx]; clearChildren(); init(); }));
            y += 18;
        }
    }

    private void initDialogues(int cx) {
        AiPlayerManager mgr = AiPlayerMod.getClientManager();
        if (mgr == null) return;
        DialogueSystem ds = mgr.getDialogueSystem();
        DialogueSystem.DialogueTopic[] topics = DialogueSystem.DialogueTopic.values();
        String[] names = {"🧠 Философия","🤖 ИИ-перспектива","🌍 Мир Minecraft","🪞 Самоанализ","❓ Вопросы игрокам"};
        int y = 36, lx = cx-100;
        for (int i = 0; i < topics.length; i++) {
            final DialogueSystem.DialogueTopic t = topics[i];
            boolean on = ds.enabledTopics.contains(t);
            addButton(new ButtonWidget(lx, y, 200, 16,
                new LiteralText((on?"§a✓ ":"§c✗ ")+names[i]),
                b -> { if(ds.enabledTopics.contains(t))ds.enabledTopics.remove(t);else ds.enabledTopics.add(t); clearChildren(); init(); }));
            y += 18;
        }
    }

    // ── Рендер ────────────────────────────────────

    @Override
    public void render(MatrixStack m, int mx, int my, float delta) {
        fill(m, 0, 0, width, height, BG);
        fill(m, 0, 0, width, 16, 0xFF05050F);
        drawCenteredText(m, textRenderer, "§6✦ AI Player §ev5.0 §6✦", width/2, 2, WHT);

        String[] tips = {"Настройки","Личность","Цели","Дневник","Места","Baritone"};
        int tw=30, sx=width/2-(6*tw)/2;
        if (page < tips.length)
            drawCenteredText(m, textRenderer, "§8" + tips[page], sx+page*tw+14, 33, GRAY);

        renderBackground(m);

        switch (page) {
            case 0: renderSettings(m); break;
            case 1: renderPersonality(m); break;
            case 2: renderGoals(m); break;
            case 3: renderDiary(m); break;
            case 4: renderPlaces(m); break;
            case 5: renderBaritone(m); break;
        }

        if (statusTimer > 0) { statusTimer--; drawCenteredText(m, textRenderer, statusMsg, width/2, height-16, WHT); }
        super.render(m, mx, my, delta);
        if (page == 0) renderFields(m, mx, my, delta);
    }

    private void renderSettings(MatrixStack m) {
        int lx = width/2-100, y = 34;
        drawCenteredText(m, textRenderer, "§e⚙ Настройки", width/2, y-3, GOLD);
        String[] labels = {"API Ключ:","Имя бота:","Провайдер: / Модель:","Интервал (тики): / Чат:","Язык:","Характер:"};
        int[] ys = {y+2,y+24,y+46,y+68,y+90,y+112};
        for (int i=0;i<labels.length;i++) textRenderer.draw(m,"§7"+labels[i],lx,ys[i],GRAY);
        textRenderer.draw(m,"§8gemini|groq|grok|openrouter",lx,y+128,GRAY);
        AiPlayerManager mgr = AiPlayerMod.getClientManager();
        String s = mgr != null ? "§aАктивен: §f"+mgr.getTargetPlayerName() : "§c/aiplayer start <ник>";
        drawCenteredText(m, textRenderer, s, width/2, height-30, WHT);
    }

    private void renderPersonality(MatrixStack m) {
        AiPlayerManager mgr = AiPlayerMod.getClientManager();
        int lx = width/2-100, y = 36+PERSONALITIES.length*18+8;
        drawCenteredText(m, textRenderer, "§dВыбери характер:", width/2, 34, PURP);
        if (mgr == null) return;
        EmotionalState e = mgr.getEmotions();
        AiMemory mem = mgr.getAiMemory();
        FatigueSystem fat = mgr.getFatigue();

        drawCenteredText(m, textRenderer, "§bСостояние", width/2, y, AQUA); y+=12;
        textRenderer.draw(m,"Эмоция: §d"+e.current.name()+" §7("+e.intensity+"/10)",lx,y,WHT); y+=11;
        textRenderer.draw(m,"Усталость: §f"+fat.getFatigueDescription()+" §7("+fat.getFatigue()+"/100)",lx,y,WHT); y+=11;
        bar(m,lx,y,"Смелость",mem.bravery,0xFFFF4444);    y+=12;
        bar(m,lx,y,"Знания  ",mem.knowledge,0xFF44FF44);  y+=12;
        bar(m,lx,y,"Общение ",mem.sociability,0xFF4488FF);y+=14;
        bar(m,lx,y,"Усталость",fat.getFatigue()/10,0xFFFFAA00);y+=14;
        if (e.isInMania()) {
            textRenderer.draw(m,"§c🤪 МАНИЯ: §f"+e.maniacGoal,lx,y,WHT); y+=11;
            bar(m,lx,y,"Прогресс",e.maniacProgress*10/Math.max(1,e.maniacTarget),0xFFFF55FF); y+=12;
        }
        if (!e.fears.isEmpty()) { textRenderer.draw(m,"§7Страхи: §e"+e.fears,lx,y,WHT); y+=11; }
        if (!e.relationships.isEmpty()) {
            textRenderer.draw(m,"§7Отношения:",lx,y,WHT); y+=11;
            e.relationships.forEach((p,r) -> textRenderer.draw(m,"§8  "+p+": §f"+e.getRelationshipName(p),lx,y,WHT));
        }
    }

    private void renderGoals(MatrixStack m) {
        AiPlayerManager mgr = AiPlayerMod.getClientManager();
        int lx = width/2-120, y = 35;
        drawCenteredText(m, textRenderer, "§e🎯 Цели ИИ", width/2, y-4, GOLD); y+=12;
        if (mgr == null) { drawCenteredText(m,textRenderer,"§cНе запущен",width/2,height/2,RED); return; }

        GoalSystem gs = mgr.getGoals();
        if (gs.getCurrentFocus() != null) {
            GoalSystem.Goal f = gs.getCurrentFocus();
            textRenderer.draw(m,"§e▶ ФОКУС: §f"+f.description,lx,y,WHT); y+=11;
            if (f.notes != null) { textRenderer.draw(m,"§8  "+f.notes,lx,y,GRAY); y+=11; }
            fill(m,lx,y,lx+240,y+6,0x33FFFFFF);
            fill(m,lx,y,lx+f.progressPercent*240/100,y+6,0xFF44FF44);
            textRenderer.draw(m,"§f"+f.progressPercent+"%",lx+244,y,WHT); y+=12;
        }
        y+=4;
        textRenderer.draw(m,"§7Все цели:",lx,y,GRAY); y+=10;
        for (GoalSystem.Goal g : gs.getActiveGoals()) {
            if (y > height-22) break;
            String icon = g.completed?"§a✓":g.priority>=90?"§c❗":"§7•";
            String col  = g.completed?"§8":g.priority>=90?"§c":"§f";
            textRenderer.draw(m,icon+" "+col+g.description+" §8["+g.progressPercent+"%]",lx,y,WHT); y+=10;
        }
    }

    private void renderDiary(MatrixStack m) {
        AiPlayerManager mgr = AiPlayerMod.getClientManager();
        int lx = width/2-120, y = 35;
        drawCenteredText(m, textRenderer, "§b📓 Дневник ИИ", width/2, y-4, AQUA); y+=12;

        if (mgr == null) { drawCenteredText(m,textRenderer,"§cНе запущен",width/2,height/2,RED); return; }

        InnerMonologue mono = mgr.getInnerMonologue();
        EmotionalState e = mgr.getEmotions();

        // Последние мысли
        textRenderer.draw(m,"§eВнутренние мысли (не говорит вслух):",lx,y,GOLD); y+=11;
        List<InnerMonologue.Thought> thoughts = mono.getRecent(6);
        if (thoughts.isEmpty()) {
            textRenderer.draw(m,"§8(мыслей ещё нет — подожди немного)",lx,y,GRAY); y+=11;
        } else {
            for (int i = thoughts.size()-1; i >= 0; i--) {
                if (y > height-60) break;
                InnerMonologue.Thought t = thoughts.get(i);
                textRenderer.draw(m,"§8День "+t.day+" "+t.time+" ["+t.emotion+"]:",lx,y,GRAY); y+=9;
                String txt = t.text.length()>68 ? t.text.substring(0,65)+"..." : t.text;
                textRenderer.draw(m,"§7§o  "+txt,lx,y,GRAY); y+=11;
            }
        }

        y+=5;
        // Сны
        textRenderer.draw(m,"§eСны:",lx,y,GOLD); y+=11;
        if (e.dreamHistory.isEmpty()) {
            textRenderer.draw(m,"§8(снов ещё не было)",lx,y,GRAY);
        } else {
            for (int i = Math.max(0,e.dreamHistory.size()-3); i < e.dreamHistory.size(); i++) {
                if (y > height-22) break;
                String d = e.dreamHistory.get(i);
                if (d.length()>68) d=d.substring(0,65)+"...";
                textRenderer.draw(m,"§7§o"+d,lx,y,GRAY); y+=11;
            }
        }
    }

    private void renderPlaces(MatrixStack m) {
        AiPlayerManager mgr = AiPlayerMod.getClientManager();
        int lx = width/2-120, y = 35;
        drawCenteredText(m, textRenderer, "§a📍 Любимые места", width/2, y-4, GRN); y+=12;

        if (mgr == null) { drawCenteredText(m,textRenderer,"§cНе запущен",width/2,height/2,RED); return; }

        List<FavoritePlaces.Place> places = mgr.getFavoritePlaces().getPlaces();
        ItemDiscovery disc = mgr.getItemDiscovery();

        if (places.isEmpty()) {
            textRenderer.draw(m,"§8(мест ещё не запомнено — нужно поиграть)",lx,y,GRAY); y+=11;
        } else {
            for (FavoritePlaces.Place p : places) {
                if (y > height-40) break;
                textRenderer.draw(m,"§a\""+p.name+"\"",lx,y,GRN); y+=9;
                textRenderer.draw(m,"§8  @ "+p.x+","+p.y+","+p.z+" | день "+p.day+" | визитов: "+p.visitCount,lx,y,GRAY); y+=9;
                if (p.memory != null && !p.memory.isEmpty()) {
                    String mem = p.memory.length()>60 ? p.memory.substring(0,57)+"..." : p.memory;
                    textRenderer.draw(m,"§7§o  \""+mem+"\"",lx,y,GRAY); y+=10;
                }
                y+=3;
            }
        }

        y+=5;
        textRenderer.draw(m,"§eИзученные предметы: §f"+disc.getDiscoveredCount()+" новых",lx,y,GOLD);
    }

    private void renderBaritone(MatrixStack m) {
        int lx = width/2-120, y = 35;
        drawCenteredText(m, textRenderer, "§b🧭 Baritone", width/2, y-4, AQUA); y+=15;
        boolean av = BaritoneNavigator.isAvailable();
        textRenderer.draw(m,"Статус: "+(av?"§a✓ Установлен!":"§c✗ Не найден"),lx,y,WHT); y+=13;
        if (av) {
            AiPlayerManager mgr = AiPlayerMod.getClientManager();
            if (mgr != null) {
                textRenderer.draw(m,"§7Задача: §f"+(mgr.getCurrentNavTask()!=null?mgr.getCurrentNavTask().name():"нет"),lx,y,WHT); y+=11;
                textRenderer.draw(m,"§7Идёт: "+(BaritoneNavigator.isNavigating()?"§aДА":"§cНет"),lx,y,WHT);
            }
        } else {
            y+=5;
            textRenderer.draw(m,"§eКак установить:",lx,y,GOLD); y+=12;
            String[] steps={"1. github.com/cabaletta/baritone/releases","2. baritone-fabric-1.6.x.jar","3. Положи в mods/ рядом с модом","4. Перезапусти Minecraft"};
            for (String s:steps) { textRenderer.draw(m,"§7"+s,lx,y,WHT); y+=11; }
            y+=5;
            textRenderer.draw(m,"§8Без Baritone — базовое движение/телепорт.",lx,y,GRAY); y+=10;
            textRenderer.draw(m,"§8С Baritone — реально ходит, копает, строит соборы.",lx,y,GRAY);
        }
    }

    // ── Утилиты ───────────────────────────────────

    private void bar(MatrixStack m, int x, int y, String label, int val, int color) {
        textRenderer.draw(m,"§7"+label+": ",x,y,WHT);
        int bx=x+72; fill(m,bx,y,bx+100,y+7,0x33FFFFFF);
        fill(m,bx,y,bx+Math.min(100,val*10),y+7,color|0xFF000000);
        textRenderer.draw(m,"§f"+val+"/10",bx+104,y,WHT);
    }

    private TextFieldWidget f(int x,int y,int w,int h,String val,int max) {
        TextFieldWidget fw=new TextFieldWidget(textRenderer,x,y,w,h,new LiteralText(""));
        fw.setMaxLength(max); fw.setText(val); addSelectableChild(fw); children.add(fw); return fw;
    }

    private void renderFields(MatrixStack m, int mx, int my, float d) {
        if(apiKeyField!=null)   apiKeyField.render(m,mx,my,d);
        if(botNameField!=null)  botNameField.render(m,mx,my,d);
        if(providerField!=null) providerField.render(m,mx,my,d);
        if(modelField!=null)    modelField.render(m,mx,my,d);
        if(intervalField!=null) intervalField.render(m,mx,my,d);
        if(languageField!=null) languageField.render(m,mx,my,d);
    }

    private void save() {
        config.apiKey   = apiKeyField.getText().trim();
        config.botName  = botNameField.getText().trim();
        config.provider = providerField.getText().trim().toLowerCase();
        config.model    = modelField.getText().trim();
        config.language = languageField.getText().trim();
        try { config.thinkIntervalTicks = Math.max(20, Integer.parseInt(intervalField.getText().trim())); }
        catch (NumberFormatException e) { config.thinkIntervalTicks = 100; }
        config.save();
        AiPlayerManager mgr = AiPlayerMod.getClientManager();
        if (mgr != null) mgr.reloadConfig(config);
        showStatus("§a✓ Сохранено!");
    }

    private void showStatus(String msg) { statusMsg = msg; statusTimer = 80; }

    @Override
    public boolean keyPressed(int k,int s,int mod) {
        if (k==256||k==73) { onClose(); return true; }
        return super.keyPressed(k,s,mod);
    }
    @Override public boolean shouldPause() { return false; }
}

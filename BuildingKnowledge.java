package com.aiplayermod;

import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

/**
 * Система строительных знаний.
 *
 * ИИ постепенно учится строить:
 * День 1: просто ящик из земли
 * День 5: дом с окнами и дверью
 * День 10: башня, арки, украшения
 * МАНИЯ: Кёльнский собор из верстаков
 *
 * Каждая техника разблокируется через опыт.
 */
public class BuildingKnowledge {

    public enum Technique {
        DIRT_HUT,       // Простой ящик из земли — первое что умеет
        WOODEN_HOUSE,   // Дом из дерева с окнами
        STONE_HOUSE,    // Каменный дом
        TOWER,          // Башня
        ARCH,           // Арка — красивый элемент
        SPIRAL_STAIR,   // Спиральная лестница
        DOME,           // Купол
        GRAND_STRUCTURE // Грандиозное строение (мания)
    }

    // Разблокированные техники
    public Set<Technique> unlockedTechniques = new HashSet<>();

    // Координатная карта — что построено и где
    public Map<String, String> builtStructures = new HashMap<>(); // "x,y,z" → "описание"

    public BuildingKnowledge() {
        unlockedTechniques.add(Technique.DIRT_HUT); // Знает с рождения
    }

    public void unlock(Technique t, String howLearned) {
        if (!unlockedTechniques.contains(t)) {
            unlockedTechniques.add(t);
            AiPlayerMod.LOGGER.info("[AIPlayer-Build] Изучена техника: " + t.name() + " — " + howLearned);
        }
    }

    public void recordStructure(BlockPos pos, String description) {
        builtStructures.put(pos.getX() + "," + pos.getY() + "," + pos.getZ(), description);
    }

    /**
     * Выбирает лучшую доступную технику под ситуацию
     */
    public Technique chooseTechnique(EmotionalState emotions, AiMemory memory) {
        if (emotions.isInMania()) return Technique.GRAND_STRUCTURE;
        if (unlockedTechniques.contains(Technique.DOME) && memory.day > 15) return Technique.DOME;
        if (unlockedTechniques.contains(Technique.TOWER) && memory.day > 10) return Technique.TOWER;
        if (unlockedTechniques.contains(Technique.STONE_HOUSE) && memory.hasShelter) return Technique.STONE_HOUSE;
        if (unlockedTechniques.contains(Technique.WOODEN_HOUSE) && memory.hasWorkbench) return Technique.WOODEN_HOUSE;
        return Technique.DIRT_HUT;
    }

    /**
     * Строит структуру нужной техникой
     */
    public void build(ServerWorld world, BlockPos origin, Technique technique,
                      Block material, EmotionalState emotions) {
        switch (technique) {
            case DIRT_HUT:       buildDirtHut(world, origin, material); break;
            case WOODEN_HOUSE:   buildWoodenHouse(world, origin, material); break;
            case STONE_HOUSE:    buildStoneHouse(world, origin, material); break;
            case TOWER:          buildTower(world, origin, material); break;
            case ARCH:           buildArch(world, origin, material); break;
            case GRAND_STRUCTURE: buildGrandStructure(world, origin, emotions); break;
            default:             buildDirtHut(world, origin, material); break;
        }
    }

    // ── Техники строительства ─────────────────────

    private void buildDirtHut(ServerWorld world, BlockPos o, Block mat) {
        // Простой куб 4x3x4
        for (int x = 0; x <= 3; x++)
            for (int z = 0; z <= 3; z++)
                for (int y = 0; y <= 2; y++) {
                    boolean wall = x==0||x==3||z==0||z==3, roof = y==2;
                    if ((wall||roof) && !(x==1&&z==0&&y<2))
                        setBlock(world, o.add(x,y,z), mat.getDefaultState());
                }
    }

    private void buildWoodenHouse(ServerWorld world, BlockPos o, Block mat) {
        // Дом 7x4x6 с окнами
        for (int x = 0; x <= 6; x++)
            for (int z = 0; z <= 5; z++)
                for (int y = 0; y <= 3; y++) {
                    boolean wall = x==0||x==6||z==0||z==5, roof = y==3;
                    boolean door = (x==3&&z==0&&(y==1||y==2));
                    boolean window = (y==2) && ((x==2&&z==0)||(x==4&&z==0)||(x==0&&z==2)||(x==6&&z==2));
                    if (door) continue;
                    if (window) { setBlock(world, o.add(x,y,z), Blocks.GLASS_PANE.getDefaultState()); continue; }
                    if (wall||roof) setBlock(world, o.add(x,y,z), mat.getDefaultState());
                }
        // Дверь
        setBlock(world, o.add(3,1,0), Blocks.OAK_DOOR.getDefaultState());
        // Факелы внутри
        setBlock(world, o.add(1,2,1), Blocks.WALL_TORCH.getDefaultState());
        setBlock(world, o.add(5,2,1), Blocks.WALL_TORCH.getDefaultState());
    }

    private void buildStoneHouse(ServerWorld world, BlockPos o, Block mat) {
        // Дом с крышей-скатом из ступенек
        buildWoodenHouse(world, o, mat);
        // Добавляем скатную крышу
        for (int x = -1; x <= 7; x++) {
            int roofH = 4 + Math.min(x, 6-x) / 2;
            for (int z = -1; z <= 6; z++) {
                setBlock(world, o.add(x, roofH, z), Blocks.STONE_BRICK_STAIRS.getDefaultState());
            }
        }
    }

    private void buildTower(ServerWorld world, BlockPos o, Block mat) {
        // Круглая башня высотой 15
        int height = 15, radius = 3;
        for (int y = 0; y <= height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x*x + z*z);
                    boolean isWall = dist >= radius - 0.7 && dist <= radius + 0.3;
                    boolean isFloor = y == 0 || y == height;
                    if (isWall || isFloor)
                        setBlock(world, o.add(x, y, z), mat.getDefaultState());
                }
            }
        }
        // Зубцы на вершине
        for (int x = -radius; x <= radius; x += 2)
            setBlock(world, o.add(x, height+1, -radius), mat.getDefaultState());
    }

    private void buildArch(ServerWorld world, BlockPos o, Block mat) {
        // Арка шириной 5, высотой 4
        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x <= 4; x++) {
                boolean leftPillar = x == 0 && y <= 3;
                boolean rightPillar = x == 4 && y <= 3;
                boolean topCurve = y == 4 && (x == 1 || x == 2 || x == 3);
                boolean topCorners = y == 3 && (x == 1 || x == 3);
                if (leftPillar || rightPillar || topCurve || topCorners)
                    setBlock(world, o.add(x, y, 0), mat.getDefaultState());
            }
        }
    }

    private void buildGrandStructure(ServerWorld world, BlockPos o, EmotionalState emotions) {
        // МАНИЯ: строим то что хочет ИИ из того материала
        Block material = getMaterialBlock(emotions.maniacMaterial);

        // Определяем форму по названию цели
        String goal = emotions.maniacGoal != null ? emotions.maniacGoal.toLowerCase() : "";

        if (goal.contains("собор") || goal.contains("cathedral")) {
            buildCathedral(world, o, material);
        } else if (goal.contains("пирамид") || goal.contains("pyramid")) {
            buildPyramid(world, o, material, 20);
        } else if (goal.contains("башн") || goal.contains("tower")) {
            buildTower(world, o, material);
            buildTower(world, o.add(10, 0, 0), material); // Двойная башня
        } else if (goal.contains("спираль") || goal.contains("spiral")) {
            buildSpiral(world, o, material);
        } else {
            // Что-то случайное но грандиозное
            buildPyramid(world, o, material, 15);
        }

        emotions.progressMania(50); // Прогресс маниакального проекта
    }

    private void buildCathedral(ServerWorld world, BlockPos o, Block mat) {
        // Упрощённый "собор" — неф + две башни + арки
        int length = 20, width = 8, height = 12;

        // Стены нефа
        for (int z = 0; z <= length; z++)
            for (int y = 0; y <= height; y++) {
                boolean wall = z==0||z==length;
                boolean sideWall = y <= height;
                if (wall || y == 0 || y == height)
                    for (int x = 0; x <= width; x++)
                        if (x==0||x==width||y==0||y==height||wall)
                            setBlock(world, o.add(x, y, z), mat.getDefaultState());
            }

        // Две башни спереди
        buildTower(world, o.add(-1, 0, 0), mat);
        buildTower(world, o.add(width+1, 0, 0), mat);

        // Арки вдоль нефа
        for (int z = 3; z < length; z += 4)
            buildArch(world, o.add(2, 0, z), mat);
    }

    private void buildPyramid(ServerWorld world, BlockPos o, Block mat, int size) {
        for (int y = 0; y < size; y++) {
            int s = size - y;
            for (int x = -s; x <= s; x++)
                for (int z = -s; z <= s; z++)
                    if (Math.abs(x) == s || Math.abs(z) == s || y == 0)
                        setBlock(world, o.add(x, y, z), mat.getDefaultState());
        }
    }

    private void buildSpiral(ServerWorld world, BlockPos o, Block mat) {
        // Спираль из блоков поднимающаяся вверх
        double angle = 0;
        for (int y = 0; y < 30; y++) {
            int x = (int)(Math.cos(angle) * 5);
            int z = (int)(Math.sin(angle) * 5);
            setBlock(world, o.add(x, y, z), mat.getDefaultState());
            setBlock(world, o.add(x+1, y, z), mat.getDefaultState());
            angle += 0.4;
        }
    }

    private Block getMaterialBlock(String materialName) {
        if (materialName == null) return Blocks.OAK_PLANKS;
        switch (materialName.toLowerCase()) {
            case "верстак": case "верстака": return Blocks.CRAFTING_TABLE;
            case "камень": case "камня": return Blocks.STONE;
            case "дерево": case "дерева": return Blocks.OAK_LOG;
            case "доски": case "досок": return Blocks.OAK_PLANKS;
            case "стекло": case "стекла": return Blocks.GLASS;
            case "земля": case "земли": return Blocks.DIRT;
            default: return Blocks.OAK_PLANKS;
        }
    }

    private void setBlock(ServerWorld world, BlockPos pos, BlockState state) {
        try { world.setBlockState(pos, state); } catch (Exception ignored) {}
    }

    // ── Прогресс знаний ───────────────────────────

    public void checkUnlocks(AiMemory memory) {
        if (memory.hasWorkbench) unlock(Technique.WOODEN_HOUSE, "поставил первый верстак");
        if (memory.hasStonePickaxe) unlock(Technique.STONE_HOUSE, "освоил камень");
        if (memory.day > 5 && memory.hasShelter) unlock(Technique.TOWER, "достаточно опыта в строительстве");
        if (memory.day > 8) unlock(Technique.ARCH, "изучал архитектуру");
        if (memory.day > 12) unlock(Technique.SPIRAL_STAIR, "эксперименты с формами");
        if (memory.day > 20) unlock(Technique.DOME, "мастерство строительства");
    }

    public String toPromptString() {
        return "СТРОИТЕЛЬНЫЕ ТЕХНИКИ: " + unlockedTechniques + "\n" +
               "ПОСТРОЕНО СТРУКТУР: " + builtStructures.size() + "\n";
    }
}

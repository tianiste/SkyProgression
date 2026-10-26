package io.github.tianiste.skyprogression.core;

import java.util.*;

public enum MiningMethod {
    COAL("Coal", Stat.ORE_FORTUNE, "COAL", false),
    GOLD("Gold", Stat.ORE_FORTUNE, "GOLD_INGOT", false),
    GROTTO("Grotto", Stat.GEMSTONE_FORTUNE, "ROUGH_JASPER_GEM", false),
    UMBER("Umber", Stat.DWARVEN_METAL_FORTUNE, "UMBER", false),
    SHAFTING("Shafting", Stat.DWARVEN_METAL_FORTUNE, "GLACITE", false),
    OBSIDIAN("Obsidian", Stat.BLOCK_FORTUNE, "OBSIDIAN", false),
    TUNGSTEN("Tungsten", Stat.DWARVEN_METAL_FORTUNE, "TUNGSTEN", false),
    MF_GEMS("MF Gems", Stat.GEMSTONE_FORTUNE, "ROUGH_AMBER_GEM", false),
    MF_RUBY("MF Ruby", Stat.GEMSTONE_FORTUNE, "ROUGH_RUBY_GEM", false),
    MF_TOPAZ("MF Topaz", Stat.GEMSTONE_FORTUNE, "ROUGH_TOPAZ_GEM", false),
    RED_SAND("Red Sand", Stat.BLOCK_FORTUNE, "SAND:1", true),
    MYCELIUM("Mycelium", Stat.BLOCK_FORTUNE, "MYCEL", true),
    END_STONE("End Stone", Stat.BLOCK_FORTUNE, "ENDER_STONE", true),
    COBBLESTONE("Cobblestone", Stat.BLOCK_FORTUNE, "COBBLESTONE", true);
    public final String label, product;
    public final Stat fortune;
    public final boolean instant;
    MiningMethod(String label, Stat fortune, String product, boolean instant) {
        this.label = label; this.fortune = fortune; this.product = product; this.instant = instant;
    }
    /**
     * Breaking Power the method's block needs (wiki Breaking Power): MF Gems is capped by Topaz (8); Red Sand and
     * Mycelium are not listed (null, no pickaxe or drill requirement known).
     */
    public Integer breakingPower() {
        return switch (this) {
            case COAL, END_STONE, COBBLESTONE -> 1;
            case GOLD -> 3;
            case OBSIDIAN -> 4;
            case MF_RUBY -> 6;
            case MF_GEMS, MF_TOPAZ -> 8;
            case GROTTO, UMBER, SHAFTING, TUNGSTEN -> 9;
            case RED_SAND, MYCELIUM -> null;
        };
    }
    /**
     * Uptime defaults per the Mining Cat developer (2026-10-26): about 80-85% for instant-mine blocks, 65-70% otherwise.
     * Coal ore is instamined on the ground from 1,801 Mining Speed (wiki Block Strength), so it takes the instant default.
     */
    public double defaultEfficiency() { return instant || this == COAL ? 82.5 : 65; }
    public boolean gems() { return fortune == Stat.GEMSTONE_FORTUNE; }
    /** Spread stat that applies: Gemstone Spread on gemstones, Mining Spread otherwise. */
    public Stat spread() { return gems() ? Stat.GEMSTONE_SPREAD : Stat.MINING_SPREAD; }
    /** Stats that change this method's yield or speed, in display order. */
    public List<Stat> relevantStats() {
        List<Stat> stats = new ArrayList<>(List.of(Stat.MINING_FORTUNE, Stat.MINING_SPEED, fortune, spread()));
        if (gems()) stats.add(Stat.PRISTINE);
        return stats;
    }
    /** The method's usual zone and block target with the God Potion assumed. */
    public StatContext usualContext() { return StatContext.in(defaultZone()).mining(target()); }
    /** Usual location of the method, used as the stat-breakdown default; the player can change it. */
    public StatContext.Zone defaultZone() {
        return switch (this) {
            case COAL, GOLD -> StatContext.Zone.DWARVEN_MINES;
            case GROTTO, OBSIDIAN -> StatContext.Zone.CRYSTAL_HOLLOWS;
            case MF_GEMS, MF_RUBY, MF_TOPAZ -> StatContext.Zone.MAGMA_FIELDS;
            case UMBER, TUNGSTEN -> StatContext.Zone.GLACITE_TUNNELS;
            case SHAFTING -> StatContext.Zone.GLACITE_MINESHAFTS;
            case RED_SAND, MYCELIUM, END_STONE, COBBLESTONE -> StatContext.Zone.OTHER;
        };
    }
    /** Block category mined, which decides block-specific perks such as Professional and Strong Arm. */
    public StatContext.Target target() {
        return switch (fortune) {
            case GEMSTONE_FORTUNE -> StatContext.Target.GEMSTONE;
            case ORE_FORTUNE -> StatContext.Target.ORE;
            case DWARVEN_METAL_FORTUNE -> StatContext.Target.DWARVEN_METAL;
            case BLOCK_FORTUNE -> StatContext.Target.BLOCK;
            default -> StatContext.Target.OTHER;
        };
    }
}

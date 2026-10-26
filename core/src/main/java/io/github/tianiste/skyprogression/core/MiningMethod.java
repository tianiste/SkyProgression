package io.github.tianiste.skyprogression.core;

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
    /** Uptime defaults per the Mining Cat developer (2026-10-26): about 80-85% for instant-mine blocks, 65-70% otherwise. */
    public double defaultEfficiency() { return instant ? 82.5 : 65; }
    public boolean gems() { return fortune == Stat.GEMSTONE_FORTUNE; }
    /** Usual location of the method, used as the stat-breakdown default; the player can change it. */
    public StatContext.Zone defaultZone() {
        return switch (this) {
            case COAL, GOLD -> StatContext.Zone.DWARVEN_MINES;
            case GROTTO, OBSIDIAN, MF_GEMS, MF_RUBY, MF_TOPAZ -> StatContext.Zone.CRYSTAL_HOLLOWS;
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
            default -> StatContext.Target.OTHER;
        };
    }
}

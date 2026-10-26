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
    public double defaultEfficiency() { return instant ? 90 : 65; }
    public boolean gems() { return fortune == Stat.GEMSTONE_FORTUNE; }
}

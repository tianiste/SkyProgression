package io.github.tianiste.skyprogression.core;

public enum Stat {
    MINING_SPEED("Mining Speed"), MINING_FORTUNE("Mining Fortune"),
    GEMSTONE_FORTUNE("Gemstone Fortune"), ORE_FORTUNE("Ore Fortune"),
    DWARVEN_METAL_FORTUNE("Dwarven Metal Fortune"), BLOCK_FORTUNE("Block Fortune"),
    MINING_SPREAD("Mining Spread"), GEMSTONE_SPREAD("Gemstone Spread"),
    PRISTINE("Pristine"), COLD_RESISTANCE("Cold Resistance");
    public final String label;
    Stat(String label) { this.label = label; }
}

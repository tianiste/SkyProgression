package io.github.tianiste.skyprogression.core;

/**
 * Mining stats. The first ten are the Type Stats inputs (InputsScreen reads them by position), so new stats go
 * at the end. {@code percent}: the value is a percentage (powder buffs, ability cooldown reduction).
 */
public enum Stat {
    MINING_SPEED("Mining Speed"), MINING_FORTUNE("Mining Fortune"),
    GEMSTONE_FORTUNE("Gemstone Fortune"), ORE_FORTUNE("Ore Fortune"),
    DWARVEN_METAL_FORTUNE("Dwarven Metal Fortune"), BLOCK_FORTUNE("Block Fortune"),
    MINING_SPREAD("Mining Spread"), GEMSTONE_SPREAD("Gemstone Spread"),
    PRISTINE("Pristine"), COLD_RESISTANCE("Cold Resistance"),
    MINING_WISDOM("Mining Wisdom"), BREAKING_POWER("Breaking Power"),
    PICKAXE_ABILITY_COOLDOWN("Pickaxe Ability Cooldown", true), HEAT_RESISTANCE("Heat Resistance"),
    ALL_POWDER("All Powder", true), MITHRIL_POWDER("Mithril Powder", true),
    GEMSTONE_POWDER("Gemstone Powder", true), GLACITE_POWDER("Glacite Powder", true);
    public final String label;
    public final boolean percent;
    Stat(String label) { this(label, false); }
    Stat(String label, boolean percent) { this.label = label; this.percent = percent; }
}

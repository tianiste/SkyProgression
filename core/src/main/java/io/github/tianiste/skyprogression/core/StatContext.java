package io.github.tianiste.skyprogression.core;

import java.util.*;

/**
 * Where the stats are evaluated. A null zone or target means "not chosen": location- or
 * block-specific sources are then reported as situational instead of being counted.
 * Temporary buffs (Mining Fiesta, weather, Sky Mall, lanterns) are never counted; the God Potion
 * is the one assumed buff (owner decision 2026-10-26), switchable with {@code godPotion}.
 */
public record StatContext(Zone zone, Target target, boolean godPotion) {
    public enum Zone {
        DEEP_CAVERNS("Deep Caverns"), DWARVEN_MINES("Dwarven Mines"), GLACITE_TUNNELS("Glacite Tunnels"),
        CRYSTAL_HOLLOWS("Crystal Hollows"), MAGMA_FIELDS("Magma Fields (Crystal Hollows)"),
        GLACITE_MINESHAFTS("Glacite Mineshafts"), OTHER("Other island");
        public final String label;
        Zone(String label) { this.label = label; }
    }
    /** Block category being mined. BLOCK is the Block Fortune group (Obsidian, End Stone, Cobblestone, sand, mycelium). */
    public enum Target { GEMSTONE, ORE, DWARVEN_METAL, HARD_STONE, BLOCK, OTHER }

    public static final Set<Zone> MINING_ISLANDS = EnumSet.of(Zone.DEEP_CAVERNS, Zone.DWARVEN_MINES,
            Zone.GLACITE_TUNNELS, Zone.CRYSTAL_HOLLOWS, Zone.MAGMA_FIELDS, Zone.GLACITE_MINESHAFTS);
    /** The Magma Fields are the lowest layer of the Crystal Hollows, so Crystal Hollows bonuses apply there too. */
    public static final Set<Zone> CRYSTAL_HOLLOWS = EnumSet.of(Zone.CRYSTAL_HOLLOWS, Zone.MAGMA_FIELDS);
    /** Dwarven Mines set bonuses exclude the Glacite Tunnels (wiki Mining Fortune/Speed source tables). */
    public static final Set<Zone> DWARVEN_MINES_ONLY = EnumSet.of(Zone.DWARVEN_MINES);
    public static final StatContext UNSPECIFIED = new StatContext(null, null, true);

    public static StatContext in(Zone zone) { return new StatContext(zone, null, true); }
    public StatContext mining(Target target) { return new StatContext(zone, target, godPotion); }
    public StatContext withGodPotion(boolean active) { return new StatContext(zone, target, active); }

    /** A requirement on the context. {@code checkable=false} marks conditions the API cannot confirm. */
    public record Condition(Set<Zone> zones, Set<Target> targets, String text, boolean checkable) {
        public static final Condition NONE = new Condition(Set.of(), Set.of(), "", true);
        public static Condition zones(Set<Zone> zones, String text) { return new Condition(zones, Set.of(), text, true); }
        public static Condition target(Target target, String text) { return new Condition(Set.of(), Set.of(target), text, true); }
        public static Condition targets(Set<Target> targets, String text) { return new Condition(Set.of(), targets, text, true); }
        public static Condition unchecked(String text) { return new Condition(Set.of(), Set.of(), text, false); }
        public boolean none() { return checkable && zones.isEmpty() && targets.isEmpty(); }
        public boolean satisfiedBy(StatContext context) {
            if (!checkable) return false;
            if (!zones.isEmpty() && (context.zone == null || !zones.contains(context.zone))) return false;
            return targets.isEmpty() || context.target != null && targets.contains(context.target);
        }
    }
}

package io.github.tianiste.skyprogression.core;

import java.util.*;

/**
 * Where the stats are evaluated. A null zone or target means "not chosen": location- or
 * block-specific sources are then reported as situational instead of being counted.
 */
public record StatContext(Zone zone, Target target) {
    public enum Zone {
        DEEP_CAVERNS("Deep Caverns"), DWARVEN_MINES("Dwarven Mines"), GLACITE_TUNNELS("Glacite Tunnels"),
        CRYSTAL_HOLLOWS("Crystal Hollows"), GLACITE_MINESHAFTS("Glacite Mineshafts"), OTHER("Other island");
        public final String label;
        Zone(String label) { this.label = label; }
    }
    /** Block category being mined. */
    public enum Target { GEMSTONE, ORE, DWARVEN_METAL, HARD_STONE, OTHER }

    public static final Set<Zone> MINING_ISLANDS = EnumSet.of(Zone.DEEP_CAVERNS, Zone.DWARVEN_MINES,
            Zone.GLACITE_TUNNELS, Zone.CRYSTAL_HOLLOWS, Zone.GLACITE_MINESHAFTS);
    /** Dwarven Mines set bonuses exclude the Glacite Tunnels (wiki Mining Fortune/Speed source tables). */
    public static final Set<Zone> DWARVEN_MINES_ONLY = EnumSet.of(Zone.DWARVEN_MINES);
    public static final StatContext UNSPECIFIED = new StatContext(null, null);

    public static StatContext in(Zone zone) { return new StatContext(zone, null); }
    public StatContext mining(Target target) { return new StatContext(zone, target); }

    /** A requirement on the context. {@code checkable=false} marks conditions the API cannot confirm. */
    public record Condition(Set<Zone> zones, Target target, String text, boolean checkable) {
        public static final Condition NONE = new Condition(Set.of(), null, "", true);
        public static Condition zones(Set<Zone> zones, String text) { return new Condition(zones, null, text, true); }
        public static Condition target(Target target, String text) { return new Condition(Set.of(), target, text, true); }
        public static Condition unchecked(String text) { return new Condition(Set.of(), null, text, false); }
        public boolean none() { return checkable && zones.isEmpty() && target == null; }
        public boolean satisfiedBy(StatContext context) {
            if (!checkable) return false;
            if (!zones.isEmpty() && (context.zone == null || !zones.contains(context.zone))) return false;
            return target == null || target == context.target;
        }
    }
}

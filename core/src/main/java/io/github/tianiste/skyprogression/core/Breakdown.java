package io.github.tianiste.skyprogression.core;

import java.util.*;

/**
 * Source-attributed ledger for one stat. Only COUNTED contributions with a known value enter the
 * total; unknown values stay null instead of becoming zero, and are reported as unresolved.
 */
public final class Breakdown {
    public enum State {
        /** Active in the evaluated context and part of the total. */
        COUNTED,
        /** Owned, but only applies under a condition the context does not satisfy (or cannot check). */
        SITUATIONAL,
        /** Not owned or not unlocked yet. */
        MISSING,
        /** Temporary effect that the API reports. */
        TEMPORARY,
        /** Never exposed by the API: neither confirmed nor ruled out. */
        UNTRACKABLE
    }
    public enum Confidence {
        /** Value derived from API data and a cited rule. */
        KNOWN,
        /** Source detected but its value or mechanic is not verified. */
        UNKNOWN,
        /** Needs the player's own input (for example the held tool). */
        MANUAL
    }
    /**
     * @param category grouping such as Armor, Tool, HOTM
     * @param value current amount; null when unknown (never silently zero)
     * @param maximum known value at the source's own maximum level, or null
     * @param condition when the source applies; empty when unconditional
     */
    public record Contribution(String category, String source, Stat stat, Double value, Double maximum,
                               String condition, State state, Confidence confidence, String note) {
        public boolean counts() { return state == State.COUNTED && value != null; }
    }

    public final Stat stat;
    public final Loadout.Kind loadout;
    public final StatContext context;
    public final List<Contribution> contributions = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();

    public Breakdown(Stat stat, Loadout.Kind loadout, StatContext context) {
        this.stat = stat; this.loadout = loadout; this.context = context;
    }
    /** Category of percentage buffs: summed, then applied once to the flat total (in-game stat menu, 2026-10-26). */
    public static final String ADDITIVE_PERCENT = "Additive %";
    public double flat() {
        return contributions.stream().filter(c -> c.counts() && !c.category.equals(ADDITIVE_PERCENT)).mapToDouble(Contribution::value).sum();
    }
    public double multiplier() {
        return 1 + contributions.stream().filter(c -> c.counts() && c.category.equals(ADDITIVE_PERCENT)).mapToDouble(Contribution::value).sum() / 100;
    }
    /**
     * Pickaxe ability cooldown reductions multiply (wiki Heart of the Mountain, "Cooldown Reductions"), so the
     * total is 1 - product(1 - r); every other stat is flat x multiplier.
     */
    public double total() {
        if (stat == Stat.PICKAXE_ABILITY_COOLDOWN) return combined(contributions.stream().filter(Contribution::counts).mapToDouble(Contribution::value).toArray());
        return flat() * multiplier();
    }
    /** Combined reduction in percent of several multiplicative percentage reductions. */
    public static double combined(double... percents) {
        double left = 1;
        for (double p : percents) left *= 1 - p / 100;
        return (1 - left) * 100;
    }
    /** Sources that would count in this context but whose value is unknown: the total is a lower bound. */
    public List<Contribution> unresolved() {
        return contributions.stream().filter(c -> c.state == State.COUNTED && c.value == null).toList();
    }
    public boolean complete() { return unresolved().isEmpty(); }
    public List<Contribution> in(State state) {
        return contributions.stream().filter(c -> c.state == state).toList();
    }
}

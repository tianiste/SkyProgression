package io.github.tianiste.skyprogression.core;

import java.util.*;
import java.util.regex.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Method rate engine. Implemented for Magma Fields gemstones (MF Gems / Ruby / Topaz), Fairy Grotto Jasper and Coal;
 * rules and sources are in docs/CALCULATIONS.md ("Rates: Magma Fields gemstones", "Rates: Fairy Grotto", "Rates: Coal"). Averages mining over
 * the method's value window, including the Mining Speed Boost cycle, at the player's efficiency (share of time spent breaking blocks).
 */
public final class MethodRates {
    private MethodRates() {}

    /** Pickaxe ability as the tool's lore shows it; boost, duration and cooldown are null when not shown. */
    public record Ability(String name, Double boostPercent, Double duration, Double cooldown) {
        public boolean speedBoost() { return name.equals("Mining Speed Boost") && boostPercent != null && duration != null && cooldown != null && cooldown > 0; }
    }
    /**
     * One gemstone of the method: block strength (wiki Block Strength), the flawless item, and the method guide's
     * reference rate with the efficiency it assumes and a short note on what the reference covers.
     */
    public record Gem(String name, double strength, String flawless, double guideFlawlessPerHour, double guideEfficiency, String guideNote) {}
    public record GemRate(Gem gem, double speed, int ticks, int boostedTicks, double uptime, double blocksPerHour,
                          double roughPerBlock, double flawlessPerHour, List<String> assumptions) {}

    /** Average of the 3-5 Rough base yield per gemstone block (wiki Gemstone). */
    public static final double BASE_ROUGH = 4;
    public static final double ROUGH_PER_FLAWLESS = 80 * 80 * 80;
    private static final String MF_NOTE = "Magma Fields guide, hypermax";

    public static Gem gem(MiningMethod m) {
        return switch (m) {
            case MF_RUBY -> new Gem("Ruby", 2300, "FLAWLESS_RUBY_GEM", 23.5, 60, MF_NOTE);
            case MF_TOPAZ -> new Gem("Topaz", 3800, "FLAWLESS_TOPAZ_GEM", 16.5, 60, MF_NOTE);
            case MF_GEMS -> new Gem("Amber (also Sapphire, Jade, Amethyst)", 3000, "FLAWLESS_AMBER_GEM", 20.1, 60, MF_NOTE);
            // Fairy Grotto guide: Mansion up to 12 Jasper plus 3.1 other Flawless / hour; Palace 11 plus 4.6 (0% Magma Fields).
            case GROTTO -> new Gem("Jasper", 4800, "FLAWLESS_JASPER_GEM", 12, 65,
                    "Fairy Grotto guide, Mansion; Palace 11. Plus 3.1-4.6 other Flawless/h; full Magma Fields grottos about x1.15");
            default -> null;
        };
    }
    /**
     * A non-gem block method: block strength and instamine factor (wiki Block Strength: instamining needs more than
     * 60 x strength on ores, Obsidian, gemstones and Dwarven Metal, else 30 x), the share of blocks mined airborne,
     * base drops per block, the priced product, and the value window in hours with its description.
     */
    public record Block(String name, double strength, double instamineFactor, double airShare, double baseDrops, String product, String unit,
                        double windowHours, String window, String guideNote) {}
    public record BlockRate(Block block, double speed, int groundTicks, int airTicks, int boostedGroundTicks, int boostedAirTicks, double uptime,
                            double blocksPerHour, double dropsPerBlock, double perHour, List<String> assumptions) {
        public double perWindow() { return perHour * block.windowHours(); }
    }

    public static Block block(MiningMethod m) {
        return switch (m) {
            // Coal guide (Oct 2026): rough estimates, "likely to make in between/less than what is written".
            case COAL -> new Block("Coal Ore", 30, 60, .08, 1, "COAL", "Coal", .5, "30 minutes (two lobby hops)",
                    "Coal guide: 4.0M-9.0M Coal plus 3.0M-6.0M Hard Stone / hour");
            default -> null;
        };
    }
    public static boolean supported(MiningMethod m) { return gem(m) != null || block(m) != null; }
    /** Hours the method's rate is shown and valued over (the Mining Cat developer's per-method windows; one hour otherwise). */
    public static double windowHours(MiningMethod m) { Block b = block(m); return b == null ? 1 : b.windowHours(); }
    /** Unit of value(): "Flawless / hour" for gems, else the product per window, for example "Coal / 30 min". */
    public static String unit(MiningMethod m) {
        Block b = block(m);
        if (b == null) return "Flawless / hour";
        return b.unit() + (b.windowHours() == 1 ? " / hour" : " / " + fmt(b.windowHours() * 60) + " min");
    }
    /** Price ids of the methods' rated products. */
    public static Set<String> priceIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MiningMethod m : MiningMethod.values()) { Block b = block(m); if (b != null) ids.add(b.product()); }
        return ids;
    }
    /** The method's rate over its value window (Flawless / hour for gems, product per window otherwise). */
    public static double value(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability) {
        return block(m) != null ? blocks(m, stats, efficiency, ability).perWindow() : gems(m, stats, efficiency, ability).flawlessPerHour();
    }

    private static final Pattern ABILITY = Pattern.compile("Ability: (.+?)\\s+RIGHT CLICK");
    private static final Pattern BOOST = Pattern.compile("Grants \\+([0-9.]+)%[^A-Za-z0-9]*Mining Speed for ([0-9.]+)s");
    private static final Pattern COOLDOWN = Pattern.compile("Cooldown: ([0-9.]+)s");
    /** The ability paragraph of a tool's lore, or null when the lore shows none. */
    public static Ability ability(Item tool) {
        if (tool == null) return null;
        String text = String.join(" ", tool.lore);
        Matcher name = ABILITY.matcher(text);
        if (!name.find()) return null;
        String rest = text.substring(name.end());
        int next = rest.indexOf("Ability:");
        if (next >= 0) rest = rest.substring(0, next);
        Matcher boost = BOOST.matcher(rest), cooldown = COOLDOWN.matcher(rest);
        boolean b = boost.find();
        return new Ability(name.group(1).strip(), b ? Double.parseDouble(boost.group(1)) : null, b ? Double.parseDouble(boost.group(2)) : null,
                cooldown.find() ? Double.parseDouble(cooldown.group(1)) : null);
    }

    /** Break time in ticks (wiki Mining Speed): round(30 x strength / speed), at least 4, or 1 above 60 x strength. */
    public static int ticks(double speed, double strength) { return ticks(speed, strength, 60); }
    /** As ticks(speed, strength) with the block's instamine factor (60 or 30 x strength, wiki Block Strength). */
    public static int ticks(double speed, double strength, double instamineFactor) {
        if (speed > instamineFactor * strength) return 1;
        if (speed <= 0) return Integer.MAX_VALUE;
        return (int)Math.max(4, Math.round(30 * strength / speed));
    }

    /** Lowest Mining Speed that breaks the block in fewer ticks than now, or NaN when already at 1 tick. */
    public static double nextTickSpeed(double speed, double strength) { return nextTickSpeed(speed, strength, 60); }
    public static double nextTickSpeed(double speed, double strength, double instamineFactor) {
        int now = ticks(speed, strength, instamineFactor);
        if (now <= 1) return Double.NaN;
        if (now <= 4) return Math.floor(instamineFactor * strength) + 1; // Below the 4-tick soft cap only instamining is faster.
        return Math.floor(30 * strength / (now - .5)) + 1;
    }

    /**
     * Block methods: ticks on the ground and airborne (Mining Speed / 5, wiki Block Strength's x5 airborne penalty), mixed
     * by the route's airborne share; Mining Speed Boost as for gems; drops = base x (1 + (Mining + type Fortune) / 100),
     * which also applies to Mining Spread blocks (wiki Mining Fortune).
     */
    public static BlockRate blocks(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability) {
        Block b = block(m);
        if (b == null) throw new IllegalArgumentException("No block rate model for " + m.label + " yet");
        if (!Double.isFinite(efficiency) || efficiency < 0 || efficiency > 100) throw new IllegalArgumentException("Efficiency must be 0 to 100");
        List<String> assumptions = new ArrayList<>();
        double speed = stat(stats, Stat.MINING_SPEED);
        if (!(speed > 0)) throw new IllegalArgumentException("Mining Speed is needed for the rate");
        double boost = 0, uptime = 0;
        if (ability != null && ability.speedBoost()) {
            uptime = Math.min(1, ability.duration() / ability.cooldown());
            boost = ability.boostPercent() / 100;
            assumptions.add(ability.name() + " +" + fmt(ability.boostPercent()) + "% for " + fmt(ability.duration()) + "s every "
                    + fmt(ability.cooldown()) + "s (tool lore), used on cooldown and applied to all Mining Speed.");
        } else assumptions.add(ability == null ? "No pickaxe ability found on the tool; no speed boost." : ability.name() + " is not modelled (no rule for its effect yet); no boost.");
        double f = b.instamineFactor();
        int ground = ticks(speed, b.strength(), f), air = ticks(speed / 5, b.strength(), f);
        int boostedGround = ticks(speed * (1 + boost), b.strength(), f), boostedAir = ticks(speed * (1 + boost) / 5, b.strength(), f);
        double a = b.airShare();
        double normal = (1 - a) * ground + a * air, boosted = (1 - a) * boostedGround + a * boostedAir; // average ticks per block
        double blocksPerSecond = 20 * (uptime / boosted + (1 - uptime) / normal);
        double spread = Math.max(0, stat(stats, m.spread()));
        double blocksPerHour = 3600 * efficiency / 100 * blocksPerSecond * (1 + spread / 100);
        double fortune = Math.max(0, stat(stats, Stat.MINING_FORTUNE) + stat(stats, m.fortune));
        double drops = b.baseDrops() * (1 + fortune / 100);
        if (a > 0) assumptions.add(fmt(a * 100) + "% of blocks mined airborne (route estimate), at Mining Speed / 5; the rest on the ground.");
        assumptions.add("Every Mining Spread block is another " + b.name() + "; other blocks on the route, travel and lobby hops are part of the efficiency.");
        assumptions.add(b.unit() + " per block = " + fmt(b.baseDrops()) + " x (1 + (Mining Fortune + " + m.fortune.label + ") / 100).");
        return new BlockRate(b, speed, ground, air, boostedGround, boostedAir, uptime, blocksPerHour, drops, blocksPerHour * drops, assumptions);
    }

    public static GemRate gems(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability) {
        Gem gem = gem(m);
        if (gem == null) throw new IllegalArgumentException("No rate model for " + m.label + " yet");
        if (!Double.isFinite(efficiency) || efficiency < 0 || efficiency > 100) throw new IllegalArgumentException("Efficiency must be 0 to 100");
        List<String> assumptions = new ArrayList<>();
        double speed = stat(stats, Stat.MINING_SPEED);
        int ticks = ticks(speed, gem.strength), boosted = ticks;
        double uptime = 0;
        if (ability != null && ability.speedBoost()) {
            uptime = Math.min(1, ability.duration() / ability.cooldown());
            boosted = ticks(speed * (1 + ability.boostPercent() / 100), gem.strength);
            assumptions.add(ability.name() + " +" + fmt(ability.boostPercent()) + "% for " + fmt(ability.duration()) + "s every "
                    + fmt(ability.cooldown()) + "s (tool lore), used on cooldown and applied to all Mining Speed.");
        } else assumptions.add(ability == null ? "No pickaxe ability found on the tool; no speed boost." : ability.name() + " is not modelled; no speed boost.");
        double blocksPerSecond = ticks == Integer.MAX_VALUE ? 0 : 20 * (uptime / boosted + (1 - uptime) / ticks);
        double spread = Math.max(0, stat(stats, Stat.GEMSTONE_SPREAD));
        double blocksPerHour = 3600 * efficiency / 100 * blocksPerSecond * (1 + spread / 100);
        double fortune = Math.max(0, stat(stats, Stat.MINING_FORTUNE) + stat(stats, Stat.GEMSTONE_FORTUNE));
        double pristine = Math.clamp(stat(stats, Stat.PRISTINE), 0, 100);
        double rough = BASE_ROUGH * (1 + pristine * .79) * (1 + fortune / 100);
        assumptions.add("Every Gemstone Spread block is another gemstone; no travel time beyond the efficiency setting.");
        if (m == MiningMethod.GROTTO) assumptions.add("All mining time is on Jasper with the ability used on Jasper. The other gems a grotto route also mines, "
                + "grotto size, griefed veins and the Magma Fields share are not modelled (unknown; they lower the real Jasper rate).");
        assumptions.add("Rough per block = 4 x (1 + 0.79 x Pristine) x (1 + (Mining + Gemstone Fortune) / 100); Flawless = 512,000 Rough.");
        return new GemRate(gem, speed, ticks, boosted, uptime, blocksPerHour, rough, blocksPerHour * rough / ROUGH_PER_FLAWLESS, assumptions);
    }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long)v) : String.valueOf(Math.round(v * 100) / 100.0); }
}

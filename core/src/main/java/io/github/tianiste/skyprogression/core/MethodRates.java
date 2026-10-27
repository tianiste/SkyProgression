package io.github.tianiste.skyprogression.core;

import java.util.*;
import java.util.function.DoubleUnaryOperator;
import java.util.regex.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Method rate engine for every method with a model; rules and sources are in docs/CALCULATIONS.md ("Rates: ..." sections, "Rates: pickaxe
 * abilities, Heat and Cold", "Rates: secondary drops"). Averages mining over the method's value window, including the pickaxe ability cycle,
 * at the player's efficiency (share of time spent breaking blocks), after the Heat or Cold of the zone.
 */
public final class MethodRates {
    private MethodRates() {}

    /**
     * Pickaxe ability as the tool's lore shows it (or filled in from the wiki level tables); boost, duration, cooldown and the
     * Maniac Miner Fortune per block are null when not known.
     */
    public record Ability(String name, Double boostPercent, Double duration, Double cooldown, Double fortunePerBlock) {
        public Ability(String name, Double boostPercent, Double duration, Double cooldown) { this(name, boostPercent, duration, cooldown, null); }
        public boolean speedBoost() { return name.equals(MSB) && boostPercent != null && duration != null && cooldown != null && cooldown > 0; }
        /** Maniac Miner with its Fortune stack, duration and cooldown known. */
        public boolean maniac() { return name.equals(MANIAC) && fortunePerBlock != null && duration != null && cooldown != null && cooldown > 0; }
        public Ability withCooldown(double c) { return new Ability(name, boostPercent, duration, c, fortunePerBlock); }
        /** Ability level from the wiki tables (Mining Speed Boost +200/250/300%, Maniac Miner +5/10/15 per block), 0 when it matches none. */
        public int level() {
            for (int l = 1; l <= 3; l++)
                if (speedBoost() && boostPercent == MSB_BOOST[l - 1] || maniac() && fortunePerBlock == MANIAC_FORTUNE[l - 1]) return l;
            return 0;
        }
    }
    public static final String MSB = "Mining Speed Boost", MANIAC = "Maniac Miner";
    /** Wiki Mining Speed Boost: +200/250/300% Mining Speed for 10/15/20 s at levels 1-3, 120 s cooldown. */
    static final double[] MSB_BOOST = {200, 250, 300}, MSB_DURATION = {10, 15, 20};
    /** Wiki Maniac Miner: +5/10/15 Mining Fortune per block broken for 25/30/35 s at levels 1-3, capped at 500, 20 Mana per block, 120 s cooldown. */
    static final double[] MANIAC_FORTUNE = {5, 10, 15}, MANIAC_DURATION = {25, 30, 35};
    public static final double MANIAC_CAP = 500, MANIAC_MANA = 20;
    public static Ability speedBoost(int level, double cooldown) { return new Ability(MSB, MSB_BOOST[level - 1], MSB_DURATION[level - 1], cooldown); }
    public static Ability maniacMiner(int level, double cooldown) { return new Ability(MANIAC, null, MANIAC_DURATION[level - 1], cooldown, MANIAC_FORTUNE[level - 1]); }
    /** The other pickaxe ability at the same level and cooldown (the HOTM tree selects one; they never stack), or null. */
    public static Ability alternative(Ability a) {
        if (a == null || a.level() == 0) return null;
        return a.speedBoost() ? maniacMiner(a.level(), a.cooldown()) : speedBoost(a.level(), a.cooldown());
    }

    /**
     * One gemstone of the method: block strength (wiki Block Strength), the flawless item, and the method guide's
     * reference rate with the efficiency it assumes and a short note on what the reference covers.
     */
    public record Gem(String name, double strength, String flawless, double guideFlawlessPerHour, double guideEfficiency, String guideNote) {}
    /** kept = share of the rate left after Heat or Cold (1 when neither applies); flawlessPerHour already includes it. */
    public record GemRate(Gem gem, double speed, int ticks, int boostedTicks, double uptime, double blocksPerHour,
                          double roughPerBlock, double flawlessPerHour, double kept, List<String> assumptions) {}

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
     * base drops per block, the priced product, and the value window: windowHours with its description, or one Mining
     * Speed Boost cycle (abilityCycle; one hour when the tool has no Mining Speed Boost). dropNote is null when the base
     * drop per block is known, else says why the rate is shown at that base drop. Strength 0 means the wiki lists none and
     * the block is assumed instamined.
     */
    public record Block(String name, double strength, double instamineFactor, double airShare, double baseDrops, String product, String unit,
                        double windowHours, String window, boolean abilityCycle, String guideNote, String dropNote) {
        public Block(String name, double strength, double instamineFactor, double airShare, double baseDrops, String product, String unit,
                     double windowHours, String window, boolean abilityCycle, String guideNote) {
            this(name, strength, instamineFactor, airShare, baseDrops, product, unit, windowHours, window, abilityCycle, guideNote, null);
        }
    }
    /** kept = share of the rate left after Heat or Cold (1 when neither applies); perHour already includes it. */
    public record BlockRate(Block block, double speed, int groundTicks, int airTicks, int boostedGroundTicks, int boostedAirTicks, double uptime,
                            double blocksPerHour, double dropsPerBlock, double perHour, double windowHours, double kept, List<String> assumptions) {
        public double perWindow() { return perHour * windowHours; }
    }

    public static Block block(MiningMethod m) {
        return switch (m) {
            // Coal guide (Oct 2026): rough estimates, "likely to make in between/less than what is written".
            case COAL -> new Block("Coal Ore", 30, 60, .08, 1, "COAL", "Coal", .5, "30 minutes (two lobby hops)", false,
                    "Coal guide, hypermax rough estimates: 4.0M-9.0M Coal plus 3.0M-6.0M Hard Stone / hour");
            // Obsidian guide: "around 3.5m/h" with max Magical Power and decent skill; mined on the floor and walls.
            case OBSIDIAN -> new Block("Obsidian", 500, 60, 0, 1, "OBSIDIAN", "Obsidian", 1, "hour", true,
                    "Obsidian guide: around 3.5M Obsidian / hour with max Magical Power and decent skill");
            // Wiki Tungsten / Umber / Glacite: 5,600 / 5,600 / 6,000 strength, Breaking Power 9; Dwarven Metal Fortune applies.
            case TUNGSTEN -> new Block("Tungsten", 5600, 60, 0, 1, "TUNGSTEN", "Tungsten", 1, "hour", false, "No Tungsten guide rate supplied",
                    "Base drops: Cobblestone 1, Clay 3 (wiki Tungsten); the vein mix is unknown, so the rate is per 1 base drop");
            case UMBER -> new Block("Umber", 5600, 60, 0, 1, "UMBER", "Umber", 1, "hour", false, "No Umber guide rate supplied",
                    "Base drops: Terracotta 1, Brown Terracotta 2, Smooth Red Sandstone 3 (wiki Umber); the vein mix is unknown, so the rate is per 1 base drop");
            case SHAFTING -> new Block("Glacite (Packed Ice)", 6000, 60, 0, 1, "GLACITE", "Glacite", 1, "hour", false, "No Glacite guide rate supplied",
                    "Base drop per Packed Ice is not stated on the wiki; shown per 1 base drop. Mineshaft loot and gemstones are not counted");
            // Wiki Block Strength: Pure Gold 600 (instamine above 36,000); wiki Mines of Divan changelog (Oct 2023): base Gold 5-7.
            case GOLD -> new Block("Pure Gold", 600, 60, 0, 6, "GOLD_INGOT", "Gold", 1, "hour", false, "No Gold guide rate yet (the guide's rates are pending)",
                    "Base drop 5-7 Gold per Pure Gold block (wiki Mines of Divan changelog, October 2023), shown at the average 6");
            // Non-ore blocks instamine above 30 x strength (wiki Block Strength): End Stone 30 (901), Cobblestone 20 (601).
            case END_STONE -> new Block("End Stone", 30, 30, 0, 1, "ENDER_STONE", "End Stone", 1, "hour", false, "End Stone guide: 11M-16M / hour");
            case COBBLESTONE -> new Block("Cobblestone", 20, 30, 0, 1, "COBBLESTONE", "Cobblestone", 1, "hour", false, "Cobblestone guide: 13M-16M / hour");
            // No wiki strength (only a theorized vanilla hardness); instamined at guide speeds (owner: 20 blocks/s is doable).
            case MYCELIUM -> new Block("Mycelium", 0, 30, 0, 1, m.product, "Mycelium", 1, "hour", false, "Mycelium/Red Sand guide: 4.6M Mycelium / hour");
            case RED_SAND -> new Block("Red Sand", 0, 30, 0, 1, m.product, "Red Sand", 1, "hour", false, "Mycelium/Red Sand guide: 4.8M Red Sand / hour");
            default -> null;
        };
    }
    public static boolean supported(MiningMethod m) { return gem(m) != null || block(m) != null; }
    /**
     * Hours the method's rate is shown over (the Mining Cat developer's per-method windows; one hour otherwise). A Mining
     * Speed Boost cycle is the ability's cooldown, which starts when the ability is used.
     */
    public static double windowHours(MiningMethod m, Ability ability) {
        Block b = block(m);
        if (b == null) return 1;
        return b.abilityCycle() ? ability != null && ability.speedBoost() ? ability.cooldown() / 3600 : 1 : b.windowHours();
    }
    /** Unit of value(): "Flawless / hour" for gems, else the product per window, for example "Coal / 30 min". */
    public static String unit(MiningMethod m, Ability ability) {
        Block b = block(m);
        if (b == null) return "Flawless / hour";
        if (b.abilityCycle() && ability != null && ability.speedBoost()) return b.unit() + " / ability cycle (" + fmt(ability.cooldown()) + "s)";
        double h = windowHours(m, ability);
        return b.unit() + (h == 1 ? " / hour" : " / " + fmt(h * 60) + " min");
    }
    /** Price ids of the methods' rated products. */
    public static Set<String> priceIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MiningMethod m : MiningMethod.values()) { Block b = block(m); if (b != null) ids.add(b.product()); }
        ids.addAll(DROP_IDS);
        return ids;
    }
    /** The method's hourly rate (Flawless / hour for gems, product / hour otherwise) in its usual zone; scale by windowHours to show it. */
    public static double perHour(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability) {
        return perHour(m, stats, efficiency, ability, m.defaultZone());
    }
    /** As perHour, in the zone (Heat in the Magma Fields, Cold in the Glacite Tunnels and Mineshafts; null = none). */
    public static double perHour(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability, StatContext.Zone zone) {
        check(efficiency);
        DoubleUnaryOperator at = rateAt(m, stats, stat(stats, Stat.MINING_SPEED), efficiency, ability);
        return at.applyAsDouble(1) * exposure(stats, zone, at, false).kept();
    }

    private static final Pattern ABILITY = Pattern.compile("Ability: (.+?)\\s+RIGHT CLICK");
    private static final Pattern BOOST = Pattern.compile("Grants \\+([0-9.]+)%[^A-Za-z0-9]*Mining Speed for ([0-9.]+)s");
    private static final Pattern COOLDOWN = Pattern.compile("Cooldown: ([0-9.]+)s");
    private static final Pattern STACK = Pattern.compile("\\+([0-9.]+)[^A-Za-z0-9]*Mining Fortune per block broken for ([0-9.]+)s");
    /** The ability paragraph of a tool's lore, or null when the lore shows none. */
    public static Ability ability(Item tool) {
        if (tool == null) return null;
        String text = String.join(" ", tool.lore);
        Matcher name = ABILITY.matcher(text);
        if (!name.find()) return null;
        String rest = text.substring(name.end());
        int next = rest.indexOf("Ability:");
        if (next >= 0) rest = rest.substring(0, next);
        Matcher boost = BOOST.matcher(rest), cooldown = COOLDOWN.matcher(rest), stack = STACK.matcher(rest);
        boolean b = boost.find(), st = !b && stack.find();
        return new Ability(name.group(1).strip(), b ? Double.parseDouble(boost.group(1)) : null, b ? Double.parseDouble(boost.group(2)) : st ? Double.parseDouble(stack.group(2)) : null,
                cooldown.find() ? Double.parseDouble(cooldown.group(1)) : null, st ? Double.parseDouble(stack.group(1)) : null);
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

    private static void check(double efficiency) {
        if (!Double.isFinite(efficiency) || efficiency < 0 || efficiency > 100) throw new IllegalArgumentException("Efficiency must be 0 to 100");
    }

    /** One rate evaluation at a given Mining Speed, without explanations: ticks, ability uptime, blocks and drops per block. */
    private record Core(int ground, int air, int boostedGround, int boostedAir, double uptime, double blocksPerHour, double dropsPerBlock,
                        double maniacFortune, double maniacBlocks) {
        double perHour() { return blocksPerHour * dropsPerBlock; }
    }

    /** Hourly rate (product, or Flawless for gems) as a function of a Mining Speed factor, for Cold's speed loss. */
    private static DoubleUnaryOperator rateAt(MiningMethod m, Map<Stat, Double> stats, double speed, double efficiency, Ability a) {
        Block b = block(m);
        if (b != null) {
            if (!(speed > 0)) throw new IllegalArgumentException("Mining Speed is needed for the rate");
            return f -> blockCore(b, m, stats, speed * f, efficiency, a).perHour();
        }
        Gem g = gem(m);
        if (g == null) throw new IllegalArgumentException("No rate model for " + m.label + " yet");
        return f -> gemCore(g, stats, speed * f, efficiency, a).perHour() / ROUGH_PER_FLAWLESS;
    }

    /** Ability uptime: duration / cooldown for Mining Speed Boost and Maniac Miner (used on cooldown), else 0. */
    private static double uptime(Ability a) { return a != null && (a.speedBoost() || a.maniac()) ? Math.min(1, a.duration() / a.cooldown()) : 0; }

    /**
     * Maniac Miner's average Fortune per block while active and the blocks broken per use: +x per mined block, applied from the
     * next block, capped at 500 (wiki Maniac Miner). Only the mined block is assumed to add a stack, not its Spread blocks.
     */
    private static double[] maniac(Ability a, double efficiency, double minedPerSecond) {
        if (a == null || !a.maniac()) return new double[]{0, 0};
        double n = a.duration() * efficiency / 100 * minedPerSecond, x = a.fortunePerBlock();
        if (!(n > 1) || x <= 0) return new double[]{0, n};
        double k = Math.ceil(MANIAC_CAP / x); // block i gets min(cap, x (i - 1)); blocks k + 1 onward are at the cap
        double avg = n <= k ? x * (n - 1) / 2 : (x * k * (k - 1) / 2 + MANIAC_CAP * (n - k)) / n;
        return new double[]{avg, n};
    }

    /**
     * Block methods: ticks on the ground and airborne (Mining Speed / 5, wiki Block Strength's x5 airborne penalty), mixed by the
     * route's airborne share; Mining Speed Boost changes the ticks while active, Maniac Miner adds its average Fortune stack to the
     * blocks broken while active; drops = base x (1 + (Mining + type Fortune) / 100), which also applies to Spread blocks (wiki Mining Fortune).
     */
    private static Core blockCore(Block b, MiningMethod m, Map<Stat, Double> stats, double speed, double efficiency, Ability a) {
        double boost = a != null && a.speedBoost() ? a.boostPercent() / 100 : 0, uptime = uptime(a), f = b.instamineFactor();
        boolean known = b.strength() > 0;
        int ground = known ? ticks(speed, b.strength(), f) : 1, air = known ? ticks(speed / 5, b.strength(), f) : 1;
        int boostedGround = known ? ticks(speed * (1 + boost), b.strength(), f) : 1, boostedAir = known ? ticks(speed * (1 + boost) / 5, b.strength(), f) : 1;
        double s = b.airShare();
        double normal = (1 - s) * ground + s * air, boosted = (1 - s) * boostedGround + s * boostedAir; // average ticks per block
        double perSecond = 20 * (uptime / boosted + (1 - uptime) / normal);
        double blocksPerHour = 3600 * efficiency / 100 * perSecond * (1 + Math.max(0, stat(stats, m.spread())) / 100);
        double[] mm = maniac(a, efficiency, 20 / normal);
        double fortune = Math.max(0, stat(stats, Stat.MINING_FORTUNE) + stat(stats, m.fortune) + uptime * mm[0]);
        return new Core(ground, air, boostedGround, boostedAir, uptime, blocksPerHour, b.baseDrops() * (1 + fortune / 100), mm[0], mm[1]);
    }

    private static Core gemCore(Gem g, Map<Stat, Double> stats, double speed, double efficiency, Ability a) {
        double uptime = uptime(a);
        int ticks = ticks(speed, g.strength), boosted = a != null && a.speedBoost() ? ticks(speed * (1 + a.boostPercent() / 100), g.strength) : ticks;
        double perSecond = ticks == Integer.MAX_VALUE ? 0 : 20 * (uptime / boosted + (1 - uptime) / ticks);
        double blocksPerHour = 3600 * efficiency / 100 * perSecond * (1 + Math.max(0, stat(stats, Stat.GEMSTONE_SPREAD)) / 100);
        double[] mm = maniac(a, efficiency, ticks == Integer.MAX_VALUE ? 0 : 20.0 / ticks);
        double fortune = Math.max(0, stat(stats, Stat.MINING_FORTUNE) + stat(stats, Stat.GEMSTONE_FORTUNE) + uptime * mm[0]);
        double pristine = Math.clamp(stat(stats, Stat.PRISTINE), 0, 100);
        return new Core(ticks, ticks, boosted, boosted, uptime, blocksPerHour, BASE_ROUGH * (1 + pristine * .79) * (1 + fortune / 100), mm[0], mm[1]);
    }

    public static BlockRate blocks(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability) {
        return blocks(m, stats, efficiency, ability, m.defaultZone());
    }
    public static BlockRate blocks(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability, StatContext.Zone zone) {
        Block b = block(m);
        if (b == null) throw new IllegalArgumentException("No block rate model for " + m.label + " yet");
        check(efficiency);
        double speed = stat(stats, Stat.MINING_SPEED);
        if (!(speed > 0)) throw new IllegalArgumentException("Mining Speed is needed for the rate");
        Core c = blockCore(b, m, stats, speed, efficiency, ability);
        Exposure x = exposure(stats, zone, rateAt(m, stats, speed, efficiency, ability), true);
        List<String> assumptions = new ArrayList<>();
        abilityNotes(assumptions, ability, c);
        if (x.note() != null) assumptions.add(x.note());
        if (b.strength() <= 0) assumptions.add("The wiki lists no block strength for " + b.name() + "; assumed instamined (1 tick, 20 blocks per second while breaking).");
        double a = b.airShare();
        if (a > 0) assumptions.add(fmt(a * 100) + "% of blocks mined airborne (route estimate), at Mining Speed / 5; the rest on the ground.");
        else assumptions.add("All blocks mined on the ground (the airborne share of the route is unknown; airborne blocks take 5x longer).");
        assumptions.add("Every Mining Spread block is another " + b.name() + "; other blocks on the route, travel and lobby hops are part of the efficiency.");
        assumptions.add(b.unit() + " per block = " + fmt(b.baseDrops()) + " x (1 + (Mining Fortune + " + m.fortune.label + (c.maniacFortune() > 0 ? " + Maniac Miner average" : "") + ") / 100).");
        if (b.dropNote() != null) assumptions.add(b.dropNote() + ". Upgrade gains do not depend on it.");
        double perHour = c.perHour() * x.kept();
        alternativeNote(assumptions, m, stats, speed, efficiency, ability, zone, perHour, b.unit());
        return new BlockRate(b, speed, c.ground(), c.air(), c.boostedGround(), c.boostedAir(), c.uptime(), c.blocksPerHour(), c.dropsPerBlock(), perHour,
                windowHours(m, ability), x.kept(), assumptions);
    }

    public static GemRate gems(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability) {
        return gems(m, stats, efficiency, ability, m.defaultZone());
    }
    public static GemRate gems(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability, StatContext.Zone zone) {
        Gem gem = gem(m);
        if (gem == null) throw new IllegalArgumentException("No rate model for " + m.label + " yet");
        check(efficiency);
        double speed = stat(stats, Stat.MINING_SPEED);
        Core c = gemCore(gem, stats, speed, efficiency, ability);
        Exposure x = exposure(stats, zone, rateAt(m, stats, speed, efficiency, ability), true);
        List<String> assumptions = new ArrayList<>();
        abilityNotes(assumptions, ability, c);
        if (x.note() != null) assumptions.add(x.note());
        assumptions.add("Every Gemstone Spread block is another gemstone; no travel time beyond the efficiency setting.");
        if (m == MiningMethod.GROTTO) assumptions.add("All mining time is on Jasper with the ability used on Jasper. The other gems a grotto route also mines are a "
                + "secondary drop (guide ratio); grotto size, griefed veins and the Magma Fields share are not modelled (unknown; they lower the real Jasper rate).");
        assumptions.add("Rough per block = 4 x (1 + 0.79 x Pristine) x (1 + (Mining + Gemstone Fortune" + (c.maniacFortune() > 0 ? " + Maniac Miner average" : "")
                + ") / 100); Flawless = 512,000 Rough.");
        double flawless = c.perHour() / ROUGH_PER_FLAWLESS * x.kept();
        alternativeNote(assumptions, m, stats, speed, efficiency, ability, zone, flawless, "Flawless");
        return new GemRate(gem, speed, c.ground(), c.boostedGround(), c.uptime(), c.blocksPerHour(), c.dropsPerBlock(), flawless, x.kept(), assumptions);
    }

    private static void abilityNotes(List<String> out, Ability a, Core c) {
        if (a == null) out.add("No pickaxe ability found on the tool; no speed boost or Fortune stack.");
        else if (a.speedBoost()) out.add(a.name() + " +" + fmt(a.boostPercent()) + "% for " + fmt(a.duration()) + "s every " + fmt(a.cooldown())
                + "s (tool lore boost; cooldown from the Pickaxe Ability Cooldown ledger), used on cooldown and applied to all Mining Speed.");
        else if (a.maniac()) out.add(a.name() + " +" + fmt(a.fortunePerBlock()) + " Mining Fortune per block broken, up to +" + fmt(MANIAC_CAP) + ", for "
                + fmt(a.duration()) + "s every " + fmt(a.cooldown()) + "s (wiki Maniac Miner), used on cooldown: about " + fmt(Math.round(c.maniacBlocks()))
                + " blocks per use, average +" + fmt(Math.round(c.maniacFortune() * 10) / 10.0) + " Fortune while active (" + fmt(Math.round(c.uptime() * 1000) / 10.0)
                + "% of blocks). Only the mined block is assumed to add a stack; its Spread blocks get the Fortune. Needs about "
                + fmt(Math.round(c.maniacBlocks() * MANIAC_MANA)) + " Mana (20 per block); what happens without enough Mana is not documented, so the full duration is assumed.");
        else out.add(a.name() + " is not modelled (no rule for its effect yet); no boost.");
    }
    /** The other pickaxe ability at the same level, with the same stats, gear and cooldown. */
    private static void alternativeNote(List<String> out, MiningMethod m, Map<Stat, Double> stats, double speed, double efficiency, Ability a,
                                        StatContext.Zone zone, double now, String unit) {
        Ability alt = alternative(a);
        if (alt == null) return;
        DoubleUnaryOperator at = rateAt(m, stats, speed, efficiency, alt);
        double other = at.applyAsDouble(1) * exposure(stats, zone, at, false).kept();
        out.add("With " + alt.name() + " " + alt.level() + " instead (same level and cooldown; the HOTM tree selects one ability, they never stack): "
                + fmt(Math.round(other * 100) / 100.0) + " " + unit + " / hour" + (now > 0 ? " (" + (other >= now ? "+" : "") + fmt(Math.round((other / now - 1) * 1000) / 10.0) + "%)" : "")
                + ", same gear and stats.");
    }

    /** Share of the rate kept after Heat or Cold over a session, with its explanation (null when neither applies or not asked). */
    public record Exposure(double kept, String note) {}
    /** Wiki Heat: +0.1 per Heat Tick at Y 64 and below in the Crystal Hollows, one tick every 1 x (1 + 0.01 x Heat Resistance) s; set on fire at 90; water -5 / s. */
    static final double HEAT_STOP = 90, HEAT_PER_TICK = .1, WATER_COOLING = 5;
    /** Wiki Cold: -1 every 5 x (1 + 0.01 x Cold Resistance) / (1 + Cold Vulnerability) s; Mining Speed -0.5% per Cold beyond Cold Resistance; FROSTBITE at -90. */
    static final int COLD_STOP = 90;
    static Exposure exposure(Map<Stat, Double> stats, StatContext.Zone zone, DoubleUnaryOperator rateAt, boolean explain) {
        if (zone == StatContext.Zone.MAGMA_FIELDS) {
            double hr = Math.max(0, stat(stats, Stat.HEAT_RESISTANCE)), tick = 1 + hr / 100;
            double build = HEAT_STOP / HEAT_PER_TICK * tick, cool = HEAT_STOP / WATER_COOLING, kept = build / (build + cool);
            return new Exposure(kept, !explain ? null : "Heat (wiki Heat): +0.1 every " + fmt(Math.round(tick * 100) / 100.0) + "s in the Magma Fields (1 x (1 + Heat Resistance "
                    + fmt(hr) + " / 100)), so 90 Heat, where you are set on fire, after " + fmt(Math.round(build / 6) / 10.0) + " min; cooling to 0 in Crystal Hollows water "
                    + "(-5 per second) takes " + fmt(cool) + "s. Mining time kept: " + fmt(Math.round(kept * 1000) / 10.0) + "%. The walk to water is not counted (unknown); "
                    + "Heat Armor's immunity and the Heated reforge's Heat bonus are not modelled.");
        }
        if (zone == StatContext.Zone.CRYSTAL_HOLLOWS)
            return new Exposure(1, !explain ? null : "Heat builds only at Y 64 and below in the Crystal Hollows (wiki Heat); how much of this route is that deep is unknown, so no Heat loss is applied.");
        boolean shaft = zone == StatContext.Zone.GLACITE_MINESHAFTS;
        if (!shaft && zone != StatContext.Zone.GLACITE_TUNNELS) return new Exposure(1, null);
        double cr = Math.max(0, stat(stats, Stat.COLD_RESISTANCE)), base = rateAt.applyAsDouble(1), t = 0, sum = 0;
        for (int cold = 0; cold < COLD_STOP; cold++) { // time spent at each Cold level, from 0 to -90
            double vulnerability = shaft ? .25 * Math.floor(t / 150) : 0, dwell = 5 * (1 + cr / 100) / (1 + vulnerability);
            double loss = Math.max(0, cold - cr) * .005;
            sum += dwell * (loss > 0 ? rateAt.applyAsDouble(Math.max(0, 1 - loss)) : base);
            t += dwell;
        }
        double kept = base > 0 ? sum / (t * base) : 1;
        return new Exposure(kept, !explain ? null : "Cold (wiki Cold): -1 every " + fmt(Math.round(5 * (1 + cr / 100) * 100) / 100.0) + "s (5 x (1 + Cold Resistance "
                + fmt(cr) + " / 100))" + (shaft ? ", faster as Cold Vulnerability rises by 0.25 every 150 s in a mineshaft" : "")
                + "; Mining Speed -0.5% per Cold beyond your Cold Resistance. Mined from 0 to -90 Cold (the FROSTBITE warning; -100 kills) in "
                + fmt(Math.round(t / 6) / 10.0) + " min, then reset at the Dwarven Base Camp" + (shaft ? " (leaving the shaft)" : "") + ". Rate kept: "
                + fmt(Math.round(kept * 1000) / 10.0) + "%. The reset trip is not counted (unknown); Portable Campfires and the Tungsten Regulator are not modelled.");
    }

    /**
     * A secondary drop: units per hour (NaN = amount unknown) of a bundle priced by its contents (price id -> count per unit; negative
     * counts are costs such as corpse keys). lowestOf values a unit at its cheapest content (mix unknown). source names where the
     * amount comes from and what it assumes.
     */
    public record Drop(String name, String unit, double perHour, Map<String, Double> contents, boolean lowestOf, String source) {}

    /** Coal guide midpoints: 3-6M Hard Stone to 4-9M Coal per hour (a route estimate, not a mechanic). */
    static final double HARD_STONE_PER_COAL = 4.5 / 6.5;
    /** Fairy Grotto guide, Mansion: 3.1 Breaking Power 7 Flawless per 12 Flawless Jasper per hour (Palace 4.6 per 11). */
    static final double GROTTO_OTHER_PER_JASPER = 3.1 / 12;
    static final List<String> BP7_FLAWLESS = List.of("FLAWLESS_AMBER_GEM", "FLAWLESS_SAPPHIRE_GEM", "FLAWLESS_JADE_GEM", "FLAWLESS_AMETHYST_GEM");
    /** Wiki Frozen Corpse loot tables: "id amount weight" per row (Frostbitten Dye, under 0.001% per roll, left out). */
    static final List<String> LAPIS_LOOT = new ArrayList<>(), METAL_LOOT = new ArrayList<>();
    static {
        for (String g : List.of("ONYX", "PERIDOT", "CITRINE", "AQUAMARINE")) {
            LAPIS_LOOT.addAll(List.of("FLAWED_" + g + "_GEM 20 1000", "FLAWED_" + g + "_GEM 40 500", "FINE_" + g + "_GEM 1 250", "FINE_" + g + "_GEM 2 125"));
            METAL_LOOT.addAll(List.of("FINE_" + g + "_GEM 4 700", "FINE_" + g + "_GEM 8 350", "FINE_" + g + "_GEM 16 175", "FLAWLESS_" + g + "_GEM 1 25"));
        }
        LAPIS_LOOT.addAll(List.of("SUSPICIOUS_SCRAP 1 500", "GOBLIN_EGG 1 800", "GOBLIN_EGG 2 400", "GOBLIN_EGG 4 200"));
        for (String egg : List.of("GREEN", "YELLOW", "RED")) LAPIS_LOOT.addAll(List.of("GOBLIN_EGG_" + egg + " 1 700", "GOBLIN_EGG_" + egg + " 2 350", "GOBLIN_EGG_" + egg + " 4 175"));
        for (String e : List.of("UMBER", "TUNGSTEN", "GLACITE")) LAPIS_LOOT.addAll(List.of("ENCHANTED_" + e + " 1 500", "ENCHANTED_" + e + " 2 250", "ENCHANTED_" + e + " 4 125"));
        LAPIS_LOOT.addAll(List.of("REFINED_UMBER 1 25", "REFINED_TUNGSTEN 1 25", "GLACITE_AMALGAMATION 1 25", "UMBER_KEY 1 25", "TUNGSTEN_KEY 1 25",
                "GLACITE_JEWEL 1 500", "GLACITE_JEWEL 2 250", "BEJEWELED_HANDLE 1 100", "BEJEWELED_HANDLE 2 50", "BEJEWELED_HANDLE 4 25"));
        METAL_LOOT.addAll(List.of("SUSPICIOUS_SCRAP 2 700", "SUSPICIOUS_SCRAP 4 350", "ENCHANTMENT_ICE_COLD_1 1 80", "GOBLIN_EGG_BLUE 1 80",
                "REFINED_UMBER 1 20", "REFINED_TUNGSTEN 1 20", "GLACITE_AMALGAMATION 1 20", "UMBER_PLATE 1 5", "TUNGSTEN_PLATE 1 5", "UMBER_KEY 1 10",
                "TUNGSTEN_KEY 1 10", "MITHRIL_PLATE 1 10", "FROZEN_SCUTE 1 4", "DWARVEN_OS_METALLIC_MINIS 1 20", "SKELETON_KEY 1 4", "CAGED_WISP 1 2"));
        for (String g : List.of("OPAL", "ONYX", "PERIDOT", "CITRINE", "AQUAMARINE")) METAL_LOOT.add(g + "_CRYSTAL 1 40");
    }
    /** Every price id a secondary drop reads. */
    public static final Set<String> DROP_IDS = new LinkedHashSet<>();
    static {
        DROP_IDS.add("HARD_STONE");
        DROP_IDS.addAll(BP7_FLAWLESS);
        for (String row : LAPIS_LOOT) DROP_IDS.add(row.split(" ")[0]);
        for (String row : METAL_LOOT) DROP_IDS.add(row.split(" ")[0]);
    }
    /** Expected items per corpse: rolls x weight / total weight x amount, per id. */
    static Map<String, Double> loot(List<String> table, double rolls) {
        double total = table.stream().mapToDouble(r -> Double.parseDouble(r.split(" ")[2])).sum();
        Map<String, Double> out = new LinkedHashMap<>();
        for (String row : table) {
            String[] p = row.split(" ");
            out.merge(p[0], rolls * Double.parseDouble(p[2]) / total * Double.parseDouble(p[1]), Double::sum);
        }
        return out;
    }

    /**
     * Mineshafts spawned per hour by the method's blocks (wiki Glacite Mineshafts): a pity counter starts at 2,000 and drops by each
     * block's Block Quality (Glacite, Umber, Tungsten 4; halved for Spread blocks), plus a 0.05% chance per block.
     */
    static double shaftsPerHour(double blocksPerHour, double spread) {
        double s = Math.max(0, spread) / 100, quality = 4 + 2 * s, p = 1 - Math.pow(1 - .0005, 1 + s);
        double pity = Math.ceil(2000 / quality), perShaft = (1 - Math.pow(1 - p, pity)) / p; // mined blocks (with their Spread) per shaft
        return blocksPerHour / (1 + s) / perShaft;
    }

    /** Secondary drops of the method in the zone, each with its source and assumptions; amounts follow the modelled rate. */
    public static List<Drop> secondary(MiningMethod m, Map<Stat, Double> stats, double efficiency, Ability ability, StatContext.Zone zone) {
        List<Drop> out = new ArrayList<>();
        switch (m) {
            case COAL -> out.add(new Drop("Hard Stone", "Hard Stone", perHour(m, stats, efficiency, ability, zone) * HARD_STONE_PER_COAL, Map.of("HARD_STONE", 1.0), false,
                    "About 0.69 Hard Stone per Coal, from the Coal guide's midpoints (3-6M Hard Stone to 4-9M Coal / hour). A route estimate, not a mechanic: "
                            + "the wiki gives no Hard Stone base drop or Fortune type."));
            case GROTTO -> {
                Map<String, Double> gems = new LinkedHashMap<>();
                for (String id : BP7_FLAWLESS) gems.put(id, 1.0);
                out.add(new Drop("Other Flawless gems", "Flawless", perHour(m, stats, efficiency, ability, zone) * GROTTO_OTHER_PER_JASPER, gems, true,
                        "About 0.26 Breaking Power 7 Flawless per Flawless Jasper, the Fairy Grotto guide's Mansion ratio (3.1 per 12; Palace 4.6 per 11), from mining "
                                + "the gems around a grotto. Valued at the cheapest of Amber, Sapphire, Jade and Amethyst (mix unknown)."));
            }
            case SHAFTING -> {
                BlockRate r = blocks(m, stats, efficiency, ability, zone);
                double shafts = shaftsPerHour(r.blocksPerHour() * r.kept(), stat(stats, m.spread())), corpses = shafts * 2;
                Map<String, Double> lapis = loot(LAPIS_LOOT, 4.5), umber = loot(METAL_LOOT, 5.5), tungsten = loot(METAL_LOOT, 5.5);
                umber.merge("UMBER_KEY", -1.0, Double::sum);
                tungsten.merge("TUNGSTEN_KEY", -1.0, Double::sum);
                String shared = "About " + fmt(Math.round(shafts * 10) / 10.0) + " mineshafts / hour from the pity counter (wiki Glacite Mineshafts: 2,000 minus Block "
                        + "Quality 4 per Glacite block, 2 per Spread block, plus a 0.05% chance per block), assuming the modelled Glacite is mined in the Glacite Tunnels, "
                        + "where blocks count. 1-3 corpses per shaft, 2 on average; 50% Lapis, 25% Umber, 25% Tungsten (wiki Frozen Corpse). Not counted: Surveyor, "
                        + "Vanguard corpses (Fairy shafts, Skeleton Key), extra-corpse chances, Glacite Powder and HOTM XP, shaft gemstones; time in the shaft is part of the efficiency.";
                out.add(new Drop("Lapis corpse loot", "corpse", corpses * .5, lapis, false, "4.5 rolls (3-6) on the Lapis table, no key. " + shared));
                out.add(new Drop("Umber corpse loot", "corpse", corpses * .25, umber, false, "5.5 rolls (4-7) on the Umber/Tungsten table, minus one Umber Key bought. " + shared));
                out.add(new Drop("Tungsten corpse loot", "corpse", corpses * .25, tungsten, false, "5.5 rolls (4-7) on the Umber/Tungsten table, minus one Tungsten Key bought. " + shared));
            }
            default -> {}
        }
        if (m != MiningMethod.COAL && (m.gems() || m == MiningMethod.TUNGSTEN || m == MiningMethod.UMBER || m == MiningMethod.SHAFTING || m == MiningMethod.GOLD))
            out.add(new Drop("Hard Stone", "Hard Stone", Double.NaN, Map.of("HARD_STONE", 1.0), false,
                    "Amount unknown: how much Hard Stone this route mines is not documented, so none is counted."));
        return out;
    }

    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long)v) : String.valueOf(Math.round(v * 100) / 100.0); }
}

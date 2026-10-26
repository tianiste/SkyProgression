package io.github.tianiste.skyprogression.core;

import java.util.*;
import java.util.regex.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Method rate engine. Implemented for Magma Fields gemstones (MF Gems / Ruby / Topaz); rules and sources are
 * in docs/CALCULATIONS.md ("Rates: Magma Fields gemstones"). Averages one hour of mining, including the
 * Mining Speed Boost cycle, at the player's efficiency (share of time spent breaking blocks).
 */
public final class MethodRates {
    private MethodRates() {}

    /** Pickaxe ability as the tool's lore shows it; boost, duration and cooldown are null when not shown. */
    public record Ability(String name, Double boostPercent, Double duration, Double cooldown) {
        public boolean speedBoost() { return name.equals("Mining Speed Boost") && boostPercent != null && duration != null && cooldown != null && cooldown > 0; }
    }
    /** One gemstone of the method: block strength (wiki Block Strength), the flawless item and the guide's reference rate. */
    public record Gem(String name, double strength, String flawless, double guideFlawlessPerHour) {}
    public record GemRate(Gem gem, double speed, int ticks, int boostedTicks, double uptime, double blocksPerHour,
                          double roughPerBlock, double flawlessPerHour, List<String> assumptions) {}

    /** Average of the 3-5 Rough base yield per gemstone block (wiki Gemstone). */
    public static final double BASE_ROUGH = 4;
    public static final double ROUGH_PER_FLAWLESS = 80 * 80 * 80;
    /** Guide reference efficiency: the Magma Fields guide's rates use 60% Block Efficiency. */
    public static final double GUIDE_EFFICIENCY = 60;

    public static Gem gem(MiningMethod m) {
        return switch (m) {
            case MF_RUBY -> new Gem("Ruby", 2300, "FLAWLESS_RUBY_GEM", 23.5);
            case MF_TOPAZ -> new Gem("Topaz", 3800, "FLAWLESS_TOPAZ_GEM", 16.5);
            case MF_GEMS -> new Gem("Amber (also Sapphire, Jade, Amethyst)", 3000, "FLAWLESS_AMBER_GEM", 20.1);
            default -> null;
        };
    }
    public static boolean supported(MiningMethod m) { return gem(m) != null; }

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
    public static int ticks(double speed, double strength) {
        if (speed > 60 * strength) return 1;
        if (speed <= 0) return Integer.MAX_VALUE;
        return (int)Math.max(4, Math.round(30 * strength / speed));
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
        assumptions.add("Rough per block = 4 x (1 + 0.79 x Pristine) x (1 + (Mining + Gemstone Fortune) / 100); Flawless = 512,000 Rough.");
        return new GemRate(gem, speed, ticks, boosted, uptime, blocksPerHour, rough, blocksPerHour * rough / ROUGH_PER_FLAWLESS, assumptions);
    }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long)v) : String.valueOf(Math.round(v * 100) / 100.0); }
}

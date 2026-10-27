package io.github.tianiste.skyprogression.core;

import com.google.gson.JsonObject;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Coins per hour across methods (docs/CALCULATIONS.md "Coins per hour across methods"): each method's hourly item rate from
 * MethodRates, on the automatic stats with the best owned gear and tool for that method in its usual zone, times a Coflnet
 * quote for the rated product. A missing quote leaves the coins unknown; the item rate is still shown.
 */
public final class MethodComparison {
    private MethodComparison() {}

    /** Wire format of the companion's {@code /compare} endpoint: one entry per method with a rate model. */
    public static final class Report {
        public String player = "", profile = "", profileId = "", fetchedAt = "";
        public boolean godPotion = true;
        public List<Entry> methods = new ArrayList<>();
        public Entry entry(MiningMethod m) { return methods.stream().filter(e -> e.method == m).findFirst().orElse(null); }
    }
    /** Totals of every supported stat; complete is false when a counted source of a method stat has an unknown value. */
    public static final class Entry {
        public MiningMethod method;
        public Map<Stat, Double> stats = new EnumMap<>(Stat.class);
        public boolean complete = true;
        public MethodRates.Ability ability;
        public String tool = "", zone = "", hotmTree = "mining";
    }

    /** Which price values the items: a Coflnet Bazaar figure or the official NPC sell price. */
    public enum PriceMode {
        INSTANT_SELL("instant-sell", "Coflnet sell estimate: selling now into the highest buy order"),
        SELL_OFFER("sell offer", "Coflnet buy quote: listing a sell order at the current lowest offer; fills only when someone buys"),
        NPC_SELL("NPC sell", "Hypixel items resource npc_sell_price: selling to an NPC merchant, no buyer needed");
        public final String label, meaning;
        PriceMode(String label, String meaning) { this.label = label; this.meaning = meaning; }
    }

    /**
     * One method's row: items per hour of the priced product, coins per hour (null = price unknown), the efficiency and
     * model used, how the value window was normalized to one hour, and notes on the drops. error is set when the rate
     * itself cannot be computed (then itemsPerHour is NaN).
     */
    public record Row(MiningMethod method, String item, String priceId, double itemsPerHour, Double coinsPerItem, Double coinsPerHour,
                      String priceNote, double efficiency, String model, String window, String drops, boolean lowerBound,
                      boolean statsComplete, String tool, String error) {}

    /** Bazaar item that the method's rate counts: the Flawless gem for gem methods, else the block product. */
    public static String priceId(MiningMethod m) {
        var gem = MethodRates.gem(m);
        if (gem != null) return gem.flawless();
        var b = MethodRates.block(m);
        return b == null ? null : b.product();
    }
    public static String itemName(MiningMethod m) {
        var gem = MethodRates.gem(m);
        if (gem != null) return "Flawless " + gem.name().split(" ")[0];
        var b = MethodRates.block(m);
        return b == null ? m.label : b.unit();
    }
    /** Short label of the rate model, as on the Rates tab. */
    public static String model(MiningMethod m) {
        if (MethodRates.gem(m) != null) return "gem tick model + Mining Speed Boost";
        var b = MethodRates.block(m);
        if (b == null) return "no rate model";
        if (b.strength() <= 0) return "instamined block (no wiki strength)";
        return "block tick model" + (b.airShare() > 0 ? " + airborne share" : "") + " + Mining Speed Boost";
    }
    /** How the method's value window becomes one hour (the Mining Cat developer's windows; MININGCAT.md). */
    public static String window(MiningMethod m, MethodRates.Ability ability) {
        var b = MethodRates.block(m);
        if (b == null) return "one-hour average including the Mining Speed Boost cycle";
        if (b.abilityCycle()) return ability != null && ability.speedBoost()
                ? "valued per Mining Speed Boost cycle (" + fmt(ability.cooldown()) + "s); per hour = " + fmt(3600 / ability.cooldown()) + " cycles"
                : "per hour (no Mining Speed Boost on the tool, so no cycle)";
        if (b.windowHours() != 1) return "valued per " + b.window() + "; per hour = " + fmt(1 / b.windowHours()) + " x that, hop time inside the efficiency";
        return "per hour";
    }
    /** Drops note: which drops the rate counts and whether the item rate is a lower bound. */
    public static String drops(MiningMethod m) {
        return switch (m) {
            case MF_GEMS, MF_RUBY, MF_TOPAZ -> "Rough gems from Fortune and Pristine, counted as Flawless (512,000 Rough)";
            case GROTTO -> "Jasper only; the route's other gems (guide: 3.1-4.6 Flawless / hour) are not counted";
            case COAL -> "Coal only; Hard Stone (guide: 3-6M / hour) is not counted";
            case TUNGSTEN, UMBER -> "at least: shown per 1 base drop, while vein blocks drop 1-3 (wiki; mix unknown)";
            case SHAFTING -> "Glacite only, base drop assumed 1 (not on the wiki); mineshaft loot not counted";
            case GOLD -> "Gold at the average base drop of 6 (5-7, wiki Mines of Divan)";
            default -> "the mined block only (base drop 1)";
        };
    }
    public static boolean lowerBound(MiningMethod m) { return m == MiningMethod.TUNGSTEN || m == MiningMethod.UMBER; }

    /** Coins per item in the mode, or null when there is no usable figure (never 0). */
    public static Double coinsPerItem(Price p, PriceMode mode) {
        if (p == null) return null;
        double v = switch (mode) { case INSTANT_SELL -> p.sell; case SELL_OFFER -> p.available ? p.buy : 0; case NPC_SELL -> p.npc; };
        return v > 0 && Double.isFinite(v) ? v : null;
    }
    /**
     * Coins per priced item of the method. The items resource lists no NPC price for Flawless gems, but Rough, Flawed and Fine
     * sell for the same amount per Rough (Rough 3, Flawed 240 = 80 x 3, Fine 19,200 for Ruby), so a gem method's NPC value per
     * Flawless is the Rough gem's NPC price x 512,000.
     */
    public static Double coinsPerItem(Map<String, Price> prices, MiningMethod m, PriceMode mode) {
        if (mode == PriceMode.NPC_SELL && MethodRates.gem(m) != null) {
            Double rough = coinsPerItem(prices.get(m.product), mode);
            return rough == null ? null : rough * MethodRates.ROUGH_PER_FLAWLESS;
        }
        return coinsPerItem(prices.get(priceId(m)), mode);
    }
    /** Items whose NPC price the companion attaches: block products and the Rough gems of gem methods. */
    public static Set<String> npcIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MiningMethod m : MiningMethod.values()) if (MethodRates.supported(m)) ids.add(MethodRates.gem(m) != null ? m.product : priceId(m));
        return ids;
    }
    /** Where the mode's figure comes from, for labels. */
    public static String priceSource(Map<String, Price> prices, MiningMethod m, PriceMode mode) {
        boolean gem = MethodRates.gem(m) != null;
        Price p = prices.get(mode == PriceMode.NPC_SELL && gem ? m.product : priceId(m));
        if (p == null) return "";
        if (mode != PriceMode.NPC_SELL) return p.source + ", " + p.updatedAt + "; before Bazaar tax";
        return "Hypixel items resource, fetched " + p.npcUpdatedAt + (gem ? "; Rough NPC price x 512,000 per Flawless" : "");
    }

    public static Row row(MiningMethod m, Map<Stat, Double> stats, boolean complete, double efficiency, MethodRates.Ability ability,
                          Map<String, Price> prices, PriceMode mode, String tool) {
        String id = priceId(m);
        double items = Double.NaN; String error = "";
        try { items = MethodRates.perHour(m, stats, efficiency, ability); }
        catch (IllegalArgumentException e) { error = e.getMessage(); }
        Price p = prices.get(id);
        Double each = coinsPerItem(prices, m, mode);
        Double coins = each == null || !Double.isFinite(items) ? null : items * each;
        String note = each != null ? fmt(each) + " each, " + mode.label + " (" + priceSource(prices, m, mode) + ")"
                : mode == PriceMode.NPC_SELL ? "No NPC sell price listed for " + (MethodRates.gem(m) != null ? m.product : id) + "; coins unknown"
                : p == null ? "No Coflnet quote for " + id + "; coins unknown"
                : "Coflnet has no " + mode.label + " figure for " + id + "; coins unknown";
        if (each != null && mode != PriceMode.NPC_SELL && p.sell > 0 && p.buy > 2 * p.sell) note += "; buy and sell quotes differ more than 2x (thin market), so sell offers may not fill";
        return new Row(m, itemName(m), id, items, each, coins, note, efficiency, model(m), window(m, ability), drops(m), lowerBound(m), complete, tool, error);
    }
    /** Highest coins per hour first; unknown coins after every known value, then by item rate errors last. */
    public static List<Row> rank(Collection<Row> rows) {
        List<Row> out = new ArrayList<>(rows);
        out.sort(Comparator.comparing((Row r) -> r.error().isEmpty() ? 0 : 1)
                .thenComparing(r -> r.coinsPerHour() == null ? 1 : 0)
                .thenComparing(r -> r.coinsPerHour() == null ? 0 : -r.coinsPerHour()));
        return out;
    }

    /**
     * Rate gain of one stat change in a method: rate(stats + delta, cooldown x cooldownFactor) / rate(stats) - 1, hourly.
     * Null when either rate cannot be computed. Used to show that one upgrade is worth different amounts in different methods.
     */
    public static Double gain(MiningMethod m, Map<Stat, Double> stats, double efficiency, MethodRates.Ability ability, Map<Stat, Double> delta, double cooldownFactor) {
        try {
            double before = MethodRates.perHour(m, stats, efficiency, ability);
            Map<Stat, Double> after = new EnumMap<>(Stat.class);
            for (Stat s : Stat.values()) after.put(s, stat(stats, s) + stat(delta, s));
            var a = ability;
            if (a != null && cooldownFactor != 1 && a.cooldown() != null) a = new MethodRates.Ability(a.name(), a.boostPercent(), a.duration(), a.cooldown() * cooldownFactor);
            double now = MethodRates.perHour(m, after, efficiency, a);
            return before > 0 ? now / before - 1 : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    /** Companion side: every rated method's totals with its best owned gear, best tool and usual zone (God Potion as chosen). */
    public static Report of(JsonObject member, Loadout.Owned owned, boolean godPotion) {
        Report r = new Report();
        r.godPotion = godPotion;
        for (MiningMethod m : MiningMethod.values()) {
            if (!MethodRates.supported(m)) continue;
            StatContext usual = m.usualContext();
            StatContext context = new StatContext(usual.zone(), usual.target(), godPotion);
            Loadout best = Loadout.bestFor(owned, m, Loadout.bestTool(owned, m));
            Entry e = new Entry();
            e.method = m;
            e.hotmTree = StatBreakdown.chooseTree(member, best, context, m);
            e.tool = best.tool == null ? "" : best.tool.name;
            e.zone = context.zone() == null ? "" : context.zone().label;
            e.ability = StatBreakdown.effectiveAbility(member, best, context);
            List<Stat> relevant = m.relevantStats();
            for (Stat s : StatBreakdown.RATE_STATS) {
                Breakdown b = StatBreakdown.compute(member, best, context, s);
                e.stats.put(s, b.total());
                if (relevant.contains(s) && !b.complete()) e.complete = false;
            }
            r.methods.add(e);
        }
        return r;
    }
    private static String fmt(double v) { return v == Math.rint(v) ? String.format(Locale.US, "%,d", (long)v) : String.format(Locale.US, "%,.2f", v).replaceAll("\\.?0+$", ""); }
}

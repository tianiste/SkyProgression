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
     * itself cannot be computed (then itemsPerHour is NaN). secondary holds the valued secondary drops; totalCoinsPerHour adds
     * their known coins to coinsPerHour (null when the main product's coins are unknown).
     */
    public record Row(MiningMethod method, String item, String priceId, double itemsPerHour, Double coinsPerItem, Double coinsPerHour,
                      String priceNote, double efficiency, String model, String window, String drops, boolean lowerBound,
                      boolean statsComplete, String tool, String error, List<DropValue> secondary, Double totalCoinsPerHour) {}
    /**
     * A valued secondary drop: coins per unit and per hour in the price mode (null = unknown), atLeast when some contents had no
     * price and were left out (named in note).
     */
    public record DropValue(MethodRates.Drop drop, Double coinsPerUnit, Double coinsPerHour, boolean atLeast, String note) {}

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
        if (MethodRates.gem(m) != null) return "gem tick model + pickaxe ability" + (heat(m) ? " + Heat" : "");
        var b = MethodRates.block(m);
        if (b == null) return "no rate model";
        if (b.strength() <= 0) return "instamined block (no wiki strength)";
        return "block tick model" + (b.airShare() > 0 ? " + airborne share" : "") + " + pickaxe ability" + (heat(m) ? " + Heat" : cold(m) ? " + Cold" : "");
    }
    private static boolean heat(MiningMethod m) { return m.defaultZone() == StatContext.Zone.MAGMA_FIELDS; }
    private static boolean cold(MiningMethod m) { return m.defaultZone() == StatContext.Zone.GLACITE_TUNNELS || m.defaultZone() == StatContext.Zone.GLACITE_MINESHAFTS; }
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
            case GROTTO -> "Jasper, plus the route's other Breaking Power 7 Flawless gems at the guide's ratio (secondary)";
            case COAL -> "Coal, plus Hard Stone at the Coal guide's ratio (secondary)";
            case TUNGSTEN -> "at base drop 3: the guide's route mines only Clay (3 per block, wiki) outside mineshafts; Hard Stone amount unknown";
            case UMBER -> "at base drop 3: the guide's route mines mainly Smooth Red Sandstone (3 per block, wiki), so a little high; Hard Stone amount unknown";
            case SHAFTING -> "Glacite, base drop assumed 1 (not on the wiki), plus Frozen Corpse loot from the mineshafts it spawns (secondary)";
            case GOLD -> "Gold at the average base drop of 6 (5-7, wiki Mines of Divan)";
            default -> "the mined block only (base drop 1)";
        };
    }
    /** No rated product is a lower bound now that Tungsten and Umber use the guide route's base drop; kept for the interface. */
    public static boolean lowerBound(MiningMethod m) { return false; }

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
        for (String id : MethodRates.DROP_IDS) ids.add(id.startsWith("FLAWLESS_") ? "ROUGH_" + id.substring(9) : id);
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

    /**
     * Coins for one item in the mode. Flawless gems have no NPC price, so at NPC sell they count as the Rough gem's price x
     * 512,000 (Rough, Flawed and Fine pay the same per Rough).
     */
    static Double coinsFor(Map<String, Price> prices, String id, PriceMode mode) {
        if (mode == PriceMode.NPC_SELL && id.startsWith("FLAWLESS_")) {
            Double rough = coinsPerItem(prices.get("ROUGH_" + id.substring(9)), mode);
            return rough == null ? null : rough * MethodRates.ROUGH_PER_FLAWLESS;
        }
        return coinsPerItem(prices.get(id), mode);
    }
    /**
     * Values a secondary drop in the mode. Costs (negative counts, such as corpse keys) are paid at the Coflnet buy quote whatever
     * the mode; an unknown cost leaves the value unknown. Unpriced contents are left out and mark the value "at least".
     */
    public static DropValue value(MethodRates.Drop d, Map<String, Price> prices, PriceMode mode) {
        Double unit = null; boolean atLeast = false; List<String> missing = new ArrayList<>();
        if (d.lowestOf()) {
            for (String id : d.contents().keySet()) {
                Double c = coinsFor(prices, id, mode);
                if (c == null) { missing.add(id); continue; }
                unit = unit == null ? c : Math.min(unit, c);
            }
            if (!missing.isEmpty()) unit = null; // an unpriced gem might be the cheapest
        } else {
            double sum = 0; boolean any = false, costUnknown = false;
            for (var e : d.contents().entrySet()) {
                if (e.getValue() < 0) {
                    Price p = prices.get(e.getKey());
                    if (p == null || !(p.buy > 0) || !p.available) { costUnknown = true; missing.add(e.getKey() + " (cost)"); }
                    else sum += e.getValue() * p.buy;
                    continue;
                }
                Double c = coinsFor(prices, e.getKey(), mode);
                if (c == null) { missing.add(e.getKey()); atLeast = true; }
                else { sum += e.getValue() * c; any = true; }
            }
            unit = costUnknown || !any ? null : sum;
            if (unit == null) atLeast = false;
        }
        // A corpse whose loot is worth less than its key is left unopened: 0, not a loss (only when every content is priced).
        boolean skipped = unit != null && unit < 0 && !atLeast;
        if (skipped) unit = 0.0;
        Double hourly = unit == null || !Double.isFinite(d.perHour()) ? null : unit * d.perHour();
        String names = String.join(", ", missing.subList(0, Math.min(4, missing.size()))) + (missing.size() > 4 ? ", ..." : "");
        boolean signUnknown = unit != null && unit < 0; // a loss with unpriced loot left out: whether it beats the key is not known
        if (signUnknown) { hourly = null; unit = null; atLeast = false; }
        String note = skipped ? "skipped: the expected loot is worth less than the key at these prices, so it counts 0"
                : signUnknown ? "coins unknown: the priced loot is worth less than the key; the unpriced " + names + " decide whether opening it pays"
                : !Double.isFinite(d.perHour()) ? "amount unknown"
                : unit == null ? (missing.isEmpty() ? "coins unknown" : "coins unknown: no price for " + names)
                : atLeast ? "at least: " + missing.size() + " unpriced item" + (missing.size() == 1 ? "" : "s") + " left out (" + names + ")" : "";
        return new DropValue(d, unit, hourly, atLeast, note);
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
        List<DropValue> secondary = new ArrayList<>();
        if (error.isEmpty()) for (var d : MethodRates.secondary(m, stats, efficiency, ability, m.defaultZone())) secondary.add(value(d, prices, mode));
        Double total = coins == null ? null : coins + secondary.stream().filter(v -> v.coinsPerHour() != null).mapToDouble(DropValue::coinsPerHour).sum();
        return new Row(m, itemName(m), id, items, each, coins, note, efficiency, model(m), window(m, ability), drops(m), lowerBound(m), complete, tool, error,
                secondary, total);
    }
    /** Highest coins per hour first; unknown coins after every known value, then by item rate errors last. */
    public static List<Row> rank(Collection<Row> rows) {
        List<Row> out = new ArrayList<>(rows);
        out.sort(Comparator.comparing((Row r) -> r.error().isEmpty() ? 0 : 1)
                .thenComparing(r -> r.totalCoinsPerHour() == null ? 1 : 0)
                .thenComparing(r -> r.totalCoinsPerHour() == null ? 0 : -r.totalCoinsPerHour()));
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
            if (a != null && cooldownFactor != 1 && a.cooldown() != null) a = a.withCooldown(a.cooldown() * cooldownFactor);
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
            Pause.point();
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

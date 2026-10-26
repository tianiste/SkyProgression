package io.github.tianiste.skyprogression.core;

import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/** Independent alternatives against the same baseline; recommendations are not a cumulative shopping list. */
public final class ProgressionEngine {
    private final List<Item> catalog;
    public ProgressionEngine(List<Item> catalog) { this.catalog = List.copyOf(catalog); }
    public Map<String, Item> bestOwned(Profile p, MiningMethod method, Stat focus) {
        Map<String, Item> result = new LinkedHashMap<>();
        for (Item item : catalog) {
            if (!item.applies(method) || p.owned.getOrDefault(item.id, 0) == 0) continue;
            Item old = result.get(item.group);
            if (old == null || score(item, method, focus) > score(old, method, focus)) result.put(item.group, item);
        }
        return result;
    }
    private double score(Item item, MiningMethod m, Stat focus) {
        if (focus != null) return stat(item.stats, focus);
        return stat(item.stats, Stat.MINING_FORTUNE) + stat(item.stats, m.fortune)
                + (m.gems() ? stat(item.stats, Stat.PRISTINE) * 100 + stat(item.stats, Stat.GEMSTONE_SPREAD) : stat(item.stats, Stat.MINING_SPREAD))
                + (m.instant ? 0 : stat(item.stats, Stat.MINING_SPEED) * .01);
    }
    public Map<Stat, Double> subtotal(Profile p, MiningMethod m) {
        Map<Stat, Double> result = new EnumMap<>(Stat.class);
        bestOwned(p, m, null).values().forEach(i -> i.stats.forEach((s, v) -> result.merge(s, v, Double::sum)));
        return result;
    }
    public List<Overview> overview(Profile p, MiningMethod m, Stat stat) {
        var current = bestOwned(p, m, stat);
        Map<String, Item> maximum = new LinkedHashMap<>();
        for (Item i : catalog) if (i.applies(m) && Model.stat(i.stats, stat) > 0) {
            Item old = maximum.get(i.group);
            if (old == null || Model.stat(i.stats, stat) > Model.stat(old.stats, stat)) maximum.put(i.group, i);
        }
        return maximum.entrySet().stream().map(e -> new Overview(e.getKey(), current.get(e.getKey()), e.getValue(),
                current.containsKey(e.getKey()) ? Model.stat(current.get(e.getKey()).stats, stat) : 0,
                Model.stat(e.getValue().stats, stat))).toList();
    }
    public List<Upgrade> upgrades(Profile p, MiningMethod m, Stat focus, Map<Stat, Double> baseline, Map<String, Price> prices) {
        if (!p.inventoryEnabled) return List.of(); // Missing API is not an empty inventory.
        var owned = bestOwned(p, m, focus);
        List<Upgrade> result = new ArrayList<>();
        for (Item i : catalog) {
            if (!i.applies(m)) continue;
            Item current = owned.get(i.group);
            if (current != null && i.id.equals(current.id)) continue;
            Map<Stat, Double> delta = new EnumMap<>(Stat.class);
            for (Stat s : Stat.values()) delta.put(s, stat(i.stats, s) - (current == null ? 0 : stat(current.stats, s)));
            double gain = focus == null ? yieldGain(m, baseline, delta) : stat(delta, focus);
            if (!Double.isFinite(gain) || gain <= 0) continue;
            boolean have = p.owned.getOrDefault(i.id, 0) > 0;
            Price quote = prices.get(i.id);
            Double cost = null;
            if (have) cost = 0.0;
            else if (quote != null && quote.available && quote.buy > 0 && Double.isFinite(quote.buy)) cost = quote.buy;
            String blocked = p.hotm < i.hotm ? "Requires HOTM " + i.hotm : "";
            if ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode)) blocked = "No market purchases on " + p.mode;
            result.add(new Upgrade(i, current, delta, cost, gain, have, blocked));
        }
        result.sort(Comparator.comparing((Upgrade u) -> !u.blocked().isEmpty()).thenComparingDouble(Upgrade::costPerUnit));
        return result;
    }
    /** Drop-only model. Does not pretend speed, cold, route, abilities or tick thresholds are linear yield. */
    public static double yieldMultiplier(MiningMethod m, Map<Stat, Double> stats) {
        double fortune = Math.max(0, stat(stats, Stat.MINING_FORTUNE) + stat(stats, m.fortune));
        double spread = Math.max(0, stat(stats, m.gems() ? Stat.GEMSTONE_SPREAD : Stat.MINING_SPREAD));
        double pristine = m.gems() ? Math.clamp(stat(stats, Stat.PRISTINE), 0, 100) : 0;
        return (1 + fortune / 100) * (1 + spread / 100) * (1 + pristine * .79);
    }
    public static double yieldGain(MiningMethod m, Map<Stat, Double> baseline, Map<Stat, Double> delta) {
        Map<Stat, Double> after = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) after.put(s, stat(baseline, s) + stat(delta, s));
        return (yieldMultiplier(m, after) / yieldMultiplier(m, baseline) - 1) * 100;
    }
    public record Rate(double itemsPerHour, double coinsPerHour, double coleweightPerHour) {}
    public static Rate rate(MiningMethod m, Map<Stat, Double> stats, double efficiency, double blocksPerSecond,
                            double baseDrops, double coinsPerItem, double coleweightPerItem) {
        double[] values = {efficiency, blocksPerSecond, baseDrops, coinsPerItem, coleweightPerItem};
        for (double v : values) if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("Enter finite non-negative values");
        if (efficiency > 100) throw new IllegalArgumentException("Efficiency must be 0 to 100");
        for (double v : stats.values()) if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("Stats must be non-negative");
        double items = 3600 * efficiency / 100 * blocksPerSecond * baseDrops * yieldMultiplier(m, stats);
        if (!Double.isFinite(items * coinsPerItem) || !Double.isFinite(items * coleweightPerItem)) throw new IllegalArgumentException("Values are too large");
        return new Rate(items, items * coinsPerItem, items * coleweightPerItem);
    }
}

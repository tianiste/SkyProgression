package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import io.github.tianiste.skyprogression.core.StatContext.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * Profile-wide upgrades read from the member data: essence shop perks, attribute shards and HOTM perk levels.
 * Each source is a chain of levels; an option is a target level with the chain's full counted stats there and the
 * total resource needed from the current level. Essence and shards can be bought (bazaar); HOTM powder cannot, so
 * a HOTM option is free when the unspent powder covers it and otherwise unpriced (grinding).
 * Costs: NEU constants essenceshops.json (100..1000 essence per level), attribute_shards.json (bazaar shard IDs;
 * per-level shards match the wiki cumulative table) and hotmlayout.json (powder per level: floor((level + 2)^e),
 * Mining Master floor((level + 8)^5), where level is the current level).
 */
public final class ProfileUpgrades {
    /**
     * resource: what is spent ("ESSENCE_GOLD", "SHARD_FLITTER", "POWDER_GEMSTONE"); priceId: bazaar ID to buy the
     * part not covered by the player's balance, or null when it cannot be bought.
     */
    public record Option(String chain, int rank, String label, String group, Map<Stat, Double> stats, String resource, double amount, String priceId, String note) {}

    /** Plan state: current counted stats per chain, unspent balances, and what each chain has already spent. */
    public record State(Map<String, Map<Stat, Double>> chains, Map<String, Double> available, Map<String, Double> paid, List<Option> options) {
        public static final State EMPTY = new State(Map.of(), Map.of(), Map.of(), List.of());
        /** Units still to pay for an option after earlier steps of its chain. */
        public double remaining(Option o) { return Math.max(0, o.amount() - paid.getOrDefault(o.chain(), 0.0)); }
        /** Coins: balance first, the rest at the quote; 0 when covered; null when it must be ground or is unpriced. */
        public Double cost(Option o, Map<String, Model.Price> prices) {
            double need = Math.max(0, remaining(o) - available.getOrDefault(o.resource(), 0.0));
            if (need == 0) return 0.0;
            Model.Price p = o.priceId() == null ? null : prices.get(o.priceId());
            return p == null || !p.available || p.buy <= 0 || !Double.isFinite(p.buy) ? null : need * p.buy;
        }
        public State take(Option o) {
            Map<String, Map<Stat, Double>> c = new HashMap<>(chains); c.put(o.chain(), o.stats());
            double used = remaining(o), have = available.getOrDefault(o.resource(), 0.0);
            Map<String, Double> a = new HashMap<>(available); a.put(o.resource(), Math.max(0, have - used));
            Map<String, Double> p = new HashMap<>(paid); p.put(o.chain(), o.amount());
            return new State(c, a, p, options.stream().filter(x -> !x.chain().equals(o.chain()) || x.rank() > o.rank()).toList());
        }
    }

    private static final Map<String, String> ESSENCE_TYPE = Map.of("eager_miner", "GOLD", "rhinestone_infusion", "DIAMOND");
    private static final int[] ESSENCE_COSTS = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
    private static final Map<String, String> SHARDS = Map.of("fortunate_miner", "SHARD_FLITTER", "rotten_pickaxe", "SHARD_MINER_ZOMBIE",
            "quartz_speed", "SHARD_QUARTZFANG", "mossy_box", "SHARD_HIDEONLEAF", "devouring_spread", "SHARD_GEMZIE", "deep_technique", "SHARD_BAL");
    /** HOTM perk powder and cost exponent (hotmlayout.json); Mining Master uses offset 8, others 2. */
    private record PerkCost(String powder, double offset, double exponent) {}
    private static final Map<String, PerkCost> PERK_COSTS = Map.ofEntries(
            Map.entry("mining_speed", new PerkCost("MITHRIL", 2, 3)), Map.entry("speedy_mineman", new PerkCost("GEMSTONE", 2, 3.2)),
            Map.entry("professional", new PerkCost("GEMSTONE", 2, 2.3)), Map.entry("strong_arm", new PerkCost("GLACITE", 2, 2.3)),
            Map.entry("mining_fortune", new PerkCost("MITHRIL", 2, 3.05)), Map.entry("fortunate_mineman", new PerkCost("GEMSTONE", 2, 3.2)),
            Map.entry("rags_to_riches", new PerkCost("GLACITE", 2, 3.05)), Map.entry("gem_lover", new PerkCost("GEMSTONE", 2, 4)),
            Map.entry("old_school", new PerkCost("GEMSTONE", 2, 4)), Map.entry("blockhead", new PerkCost("GEMSTONE", 2, 4)),
            Map.entry("metal_head", new PerkCost("GLACITE", 2, 4)), Map.entry("efficient_miner", new PerkCost("MITHRIL", 2, 2.6)),
            Map.entry("mole", new PerkCost("GEMSTONE", 2, 2.2)), Map.entry("mining_master", new PerkCost("GLACITE", 8, 5)));

    public static State state(JsonObject member, String tree, StatContext context) {
        Map<String, Map<Stat, Double>> chains = new HashMap<>();
        Map<String, Double> available = new HashMap<>();
        List<Option> options = new ArrayList<>();
        essence(member, context, chains, available, options);
        attributes(member, context, chains, options);
        hotm(member, tree, context, chains, available, options);
        return new State(chains, available, new HashMap<>(), options);
    }
    private static double counted(double value, Condition c, StatContext context) { return c.none() || c.satisfiedBy(context) ? value : 0; }
    private static Map<Stat, Double> one(Stat s, double v) { Map<Stat, Double> m = new EnumMap<>(Stat.class); if (v != 0) m.put(s, v); return m; }

    private static void essence(JsonObject member, StatContext context, Map<String, Map<Stat, Double>> chains, Map<String, Double> available, List<Option> out) {
        JsonElement perks = at(member, "player_data", "perks");
        if (!perks.isJsonObject()) return;
        Condition islands = Condition.zones(StatContext.MINING_ISLANDS, "On Mining Islands");
        for (StatBreakdown.Essence e : StatBreakdown.ESSENCE) {
            String type = ESSENCE_TYPE.get(e.id()), resource = "ESSENCE_" + type;
            available.put(resource, number(at(member, "currencies", "essence", type, "current"), 0));
            int level = (int)number(at(perks, e.id()), 0);
            String chain = "essence:" + e.id();
            chains.put(chain, one(e.stat(), counted(e.perLevel() * level, islands, context)));
            double amount = 0;
            for (int target = level + 1; target <= e.maxLevel(); target++) {
                amount += ESSENCE_COSTS[target - 1];
                out.add(new Option(chain, target, e.name().replaceFirst(" \\(", " " + target + " ("), "Essence",
                        one(e.stat(), counted(e.perLevel() * target, islands, context)), resource, amount, resource,
                        "Essence shop perk; your essence balance is used first."));
            }
        }
    }
    private static void attributes(JsonObject member, StatContext context, Map<String, Map<Stat, Double>> chains, List<Option> out) {
        JsonElement stacks = at(member, "attributes", "stacks");
        if (!stacks.isJsonObject()) return;
        for (StatBreakdown.Attribute a : StatBreakdown.ATTRIBUTES) {
            String shard = SHARDS.get(a.id());
            int[] cumulative = StatBreakdown.ATTRIBUTE_SHARDS.get(a.rarity());
            int have = (int)number(at(stacks, a.id()), 0), level = 0;
            for (int needed : cumulative) if (have >= needed) level++;
            String chain = "attribute:" + a.id();
            chains.put(chain, one(a.stat(), counted(a.perLevel() * level, a.condition(), context)));
            for (int target = level + 1; target <= cumulative.length; target++)
                out.add(new Option(chain, target, a.name() + " " + target, "Attribute", one(a.stat(), counted(a.perLevel() * target, a.condition(), context)),
                        shard, cumulative[target - 1] - have, shard, "Syphon " + (cumulative[target - 1] - have) + " shards (bazaar)."));
        }
    }
    private static void hotm(JsonObject member, String tree, StatContext context, Map<String, Map<Stat, Double>> chains, Map<String, Double> available, List<Option> out) {
        JsonElement nodes = at(member, "skill_tree", "nodes", tree);
        if (!nodes.isJsonObject()) nodes = at(member, "mining_core", "nodes");
        if (!nodes.isJsonObject()) return;
        for (String powder : List.of("MITHRIL", "GEMSTONE", "GLACITE")) {
            String p = powder.toLowerCase(Locale.ROOT);
            JsonElement total = at(member, "mining_core", "powder_" + p);
            if (!total.isJsonNull()) available.put("POWDER_" + powder, Math.max(0, number(total, 0) - number(at(member, "mining_core", "powder_spent_" + p), 0)));
        }
        for (StatBreakdown.Perk perk : StatBreakdown.PERKS) {
            PerkCost cost = PERK_COSTS.get(perk.id());
            int level = (int)number(at(nodes, perk.id()), 0);
            // Locked perks need the tree path and HOTM tier; only levelling unlocked perks is proposed.
            if (cost == null || level <= 0 || level >= perk.maxLevel()) continue;
            String chain = "hotm:" + tree + ":" + perk.id();
            chains.put(chain, one(perk.stat(), counted(perk.at(level), perk.condition(), context)));
            double amount = 0;
            Set<Integer> targets = new TreeSet<>(List.of(level + 1, level + 5, level + 10, level + 25, perk.maxLevel()));
            int from = level;
            for (int target : targets) {
                if (target > perk.maxLevel()) continue;
                for (int l = from; l < target; l++) amount += Math.floor(Math.pow(l + cost.offset, cost.exponent));
                from = target;
                String resource = "POWDER_" + cost.powder;
                out.add(new Option(chain, target, perk.name() + " " + level + " -> " + target, "HOTM",
                        one(perk.stat(), counted(perk.at(target), perk.condition(), context)), resource, amount, null,
                        String.format(Locale.ROOT, "%,.0f %s Powder; free if your unspent powder covers it, otherwise grind it.", amount,
                                cost.powder.charAt(0) + cost.powder.substring(1).toLowerCase(Locale.ROOT))));
            }
        }
    }
    public static Set<String> priceIds() {
        Set<String> ids = new LinkedHashSet<>(List.of("ESSENCE_GOLD", "ESSENCE_DIAMOND"));
        ids.addAll(SHARDS.values());
        return ids;
    }
}

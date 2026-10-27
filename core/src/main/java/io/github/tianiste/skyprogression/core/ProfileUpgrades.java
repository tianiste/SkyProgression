package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import io.github.tianiste.skyprogression.core.StatContext.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * Profile-wide upgrades read from the member data: essence shop perks, attribute shards, HOTM perk levels and unlocks,
 * and the Mining skill and collection tiers that grant mining stats. Each source is a chain of levels; an option is a
 * target level with the chain's full counted stats there and the total resource needed from the current level.
 * Essence and shards can be bought (bazaar); powder, tokens, skill XP and collection items cannot, so such an option is
 * free when the player's balance covers it and otherwise unpriced (grinding).
 * Costs: NEU constants essenceshops.json (100..1000 essence per level), attribute_shards.json (bazaar shard IDs;
 * per-level shards match the wiki cumulative table) and hotmlayout.json (tree positions; powder per level:
 * floor((level + 2)^e), Mining Master floor((level + 8)^5), where level is the current level).
 */
public final class ProfileUpgrades {
    /**
     * resource: what is spent ("ESSENCE_GOLD", "SHARD_FLITTER", "POWDER_GEMSTONE", "MINING_XP"); priceId: bazaar ID to
     * buy the part not covered by the player's balance, or null when it cannot be bought (grinding).
     * bonus: the stats with the Blue Cheese Goblin Omelette's +1 level (HOTM perks only, else null); unlock: HOTM nodes
     * ("tree:perk") this option unlocks, one Token of the Mountain each; tier: HOTM tier it needs (0 = none).
     */
    public record Option(String chain, int rank, String label, String group, Map<Stat, Double> stats, String resource, double amount, String priceId, String note,
                         Map<Stat, Double> bonus, List<String> unlock, int tier) {
        public Option {
            if (unlock == null) unlock = List.of();
        }
        Option(String chain, int rank, String label, String group, Map<Stat, Double> stats, String resource, double amount, String priceId, String note) {
            this(chain, rank, label, group, stats, resource, amount, priceId, note, null, List.of(), 0);
        }
        /** Cannot be bought: powder, tokens, skill XP or collection items. */
        public boolean grinding() { return priceId == null; }
    }

    /**
     * Plan state: counted stats per chain (without the omelette's bonus) and with it (HOTM chains), whether the evaluated
     * drill holds a Blue Cheese Goblin Omelette, unspent balances ("TOKEN" = Tokens of the Mountain), what each chain
     * has already spent, and the unlocked HOTM nodes.
     */
    public record State(Map<String, Map<Stat, Double>> chains, Map<String, Map<Stat, Double>> alternate, boolean omelette, Map<String, Double> available,
                        Map<String, Double> paid, Set<String> unlocked, List<Option> options) {
        public static final State EMPTY = new State(Map.of(), Map.of(), false, Map.of(), Map.of(), Set.of(), List.of());
        public State {
            if (alternate == null) alternate = Map.of();
            if (unlocked == null) unlocked = Set.of();
        }
        /** Units still to pay for an option after earlier steps of its chain. */
        public double remaining(Option o) { return Math.max(0, o.amount() - paid.getOrDefault(o.chain(), 0.0)); }
        /** Tokens of the Mountain still needed: unlock nodes not yet unlocked. */
        public int tokens(Option o) { return (int)o.unlock().stream().filter(n -> !unlocked.contains(n)).count(); }
        /** The chain's counted stats now, with the omelette's bonus when it is held. */
        public Map<Stat, Double> current(String chain) {
            return omelette && alternate.containsKey(chain) ? alternate.get(chain) : chains.getOrDefault(chain, Map.of());
        }
        public Map<Stat, Double> stats(Option o) { return omelette && o.bonus() != null ? o.bonus() : o.stats(); }
        /** Coins: balance first, the rest at the quote; 0 when covered; null when it must be ground or is unpriced. */
        public Double cost(Option o, Map<String, Model.Price> prices) {
            if (tokens(o) > available.getOrDefault("TOKEN", 0.0)) return null;
            double need = Math.max(0, remaining(o) - available.getOrDefault(o.resource(), 0.0));
            if (need == 0) return 0.0;
            Model.Price p = o.priceId() == null ? null : prices.get(o.priceId());
            return p == null || !p.available || p.buy <= 0 || !Double.isFinite(p.buy) ? null : need * p.buy;
        }
        /**
         * Mining stats the Blue Cheese Goblin Omelette adds: +1 level on every unlocked HOTM perk (wiki: it "adds +1 level
         * to all unlocked Heart of the Mountain perks; it's able to bypass the level cap").
         */
        public Map<Stat, Double> omeletteGain() {
            Map<Stat, Double> out = new EnumMap<>(Stat.class);
            alternate.forEach((chain, with) -> {
                Map<Stat, Double> without = chains.getOrDefault(chain, Map.of());
                for (Stat s : Stat.values()) {
                    double d = with.getOrDefault(s, 0.0) - without.getOrDefault(s, 0.0);
                    if (d != 0) out.merge(s, d, Double::sum);
                }
            });
            return out;
        }
        public State withOmelette(boolean held) { return new State(chains, alternate, held, available, paid, unlocked, options); }
        public State take(Option o) {
            Map<String, Map<Stat, Double>> c = new HashMap<>(chains); c.put(o.chain(), o.stats());
            Map<String, Map<Stat, Double>> alt = new HashMap<>(alternate);
            if (o.bonus() != null) alt.put(o.chain(), o.bonus());
            double used = remaining(o), have = available.getOrDefault(o.resource(), 0.0);
            Map<String, Double> a = new HashMap<>(available); a.put(o.resource(), Math.max(0, have - used));
            a.put("TOKEN", Math.max(0, available.getOrDefault("TOKEN", 0.0) - tokens(o)));
            Map<String, Double> p = new HashMap<>(paid); p.put(o.chain(), o.amount());
            Set<String> u = new HashSet<>(unlocked); u.addAll(o.unlock());
            return new State(c, alt, omelette, a, p, u, options.stream().filter(x -> !x.chain().equals(o.chain()) || x.rank() > o.rank()).toList());
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

    /** omelette: the evaluated drill holds a Blue Cheese Goblin Omelette, so HOTM perks count one level higher. */
    public static State state(JsonObject member, String tree, StatContext context, boolean omelette) {
        Map<String, Map<Stat, Double>> chains = new HashMap<>(), alternate = new HashMap<>();
        Map<String, Double> available = new HashMap<>();
        Set<String> unlocked = new HashSet<>();
        List<Option> options = new ArrayList<>();
        essence(member, context, chains, available, options);
        attributes(member, context, chains, options);
        hotm(member, tree, context, chains, alternate, available, unlocked, options);
        skill(member, chains, options);
        collections(member, chains, options);
        return new State(chains, alternate, omelette, available, new HashMap<>(), unlocked, options);
    }
    private static double counted(double value, Condition c, StatContext context) { return c.none() || c.satisfiedBy(context) ? value : 0; }
    private static Map<Stat, Double> one(Stat s, double v) { Map<Stat, Double> m = new EnumMap<>(Stat.class); if (v != 0) m.put(s, v); return m; }

    private static void essence(JsonObject member, StatContext context, Map<String, Map<Stat, Double>> chains, Map<String, Double> available, List<Option> out) {
        JsonElement perks = at(member, "player_data", "perks");
        if (!perks.isJsonObject()) return;
        Condition islands = Condition.zones(StatContext.MINING_ISLANDS, "On Mining Islands");
        for (StatBreakdown.Essence e : StatBreakdown.ESSENCE) {
            String type = ESSENCE_TYPE.get(e.id()), resource = "ESSENCE_" + type;
            if (type == null) continue; // shown in the ledger only (no rate use)
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
            if (shard == null) continue; // shown in the ledger only (no rate use)
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
    /**
     * HOTM tree positions (NEU hotmlayout.json, x 0..6 and y 0..9; tier = 10 - y). A perk can be unlocked when it is next
     * to an unlocked perk (same row or column, one step) and the HOTM tier reaches its row; each unlock costs one Token
     * of the Mountain. Mining Speed (tier 1) is the first perk.
     */
    private static final Map<String, int[]> LAYOUT = new HashMap<>();
    static {
        String[] rows = {"mining_speed 3 9", "mining_speed_boost 1 8", "precision_mining 2 8", "mining_fortune 3 8", "titanium_insanium 4 8", "pickobulus 5 8",
                "luck_of_the_cave 1 7", "efficient_miner 3 7", "quick_forge 5 7", "sky_mall 0 6", "old_school 1 6", "professional 2 6", "mole 3 6",
                "gem_lover 4 6", "seasoned_mineman 5 6", "front_loaded 6 6", "daily_grind 1 5", "core_of_the_mountain 3 5", "daily_powder 5 5",
                "anomalous_desire 0 4", "blockhead 1 4", "subterranean_fisher 2 4", "keep_it_cool 3 4", "lonesome_miner 4 4", "great_explorer 5 4",
                "maniac_miner 6 4", "speedy_mineman 1 3", "powder_buff 3 3", "fortunate_mineman 5 3", "miners_blessing 0 2", "no_stone_unturned 1 2",
                "strong_arm 2 2", "steady_hand 3 2", "warm_heart 4 2", "surveyor 5 2", "mineshaft_mayhem 6 2", "metal_head 1 1", "rags_to_riches 3 1",
                "eager_adventurer 5 1", "gemstone_infusion 0 0", "crystalline 1 0", "gifts_from_the_departed 2 0", "mining_master 3 0",
                "dead_mans_chest 4 0", "vanguard_seeker 5 0", "sheer_force 6 0"};
        for (String row : rows) { String[] f = row.split(" "); LAYOUT.put(f[0], new int[]{Integer.parseInt(f[1]), Integer.parseInt(f[2])}); }
    }
    /** Tokens of the Mountain per HOTM tier 1..10; Core of the Mountain levels 1, 5, 7 add 1 and level 10 adds 2 (wiki Heart of the Mountain). */
    private static final int[] TIER_TOKENS = {1, 2, 2, 2, 2, 2, 3, 2, 2, 2};
    static int tier(String perk) { int[] at = LAYOUT.get(perk); return at == null ? 0 : 10 - at[1]; }
    /** Fewest locked nodes to unlock, ending at the perk, starting next to any unlocked node (or at the tier 1 perk). */
    static List<String> path(String perk, Set<String> open) {
        Map<String, String> from = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        if (open.isEmpty()) { queue.add("mining_speed"); from.put("mining_speed", null); }
        else for (String n : new TreeSet<>(open)) if (LAYOUT.containsKey(n)) for (String next : neighbours(n))
            if (!open.contains(next) && !from.containsKey(next)) { from.put(next, null); queue.add(next); }
        while (!queue.isEmpty()) {
            String n = queue.poll();
            if (n.equals(perk)) {
                LinkedList<String> out = new LinkedList<>();
                for (String at = n; at != null; at = from.get(at)) out.addFirst(at);
                return out;
            }
            for (String next : neighbours(n)) if (!open.contains(next) && !from.containsKey(next)) { from.put(next, n); queue.add(next); }
        }
        return null;
    }
    private static List<String> neighbours(String perk) {
        int[] at = LAYOUT.get(perk);
        List<String> out = new ArrayList<>();
        for (var e : LAYOUT.entrySet()) if (Math.abs(e.getValue()[0] - at[0]) + Math.abs(e.getValue()[1] - at[1]) == 1) out.add(e.getKey());
        out.sort(null);
        return out;
    }
    private static double powder(PerkCost cost, int from, int to) {
        double amount = 0;
        for (int l = from; l < to; l++) amount += Math.floor(Math.pow(l + cost.offset, cost.exponent));
        return amount;
    }
    private static void hotm(JsonObject member, String tree, StatContext context, Map<String, Map<Stat, Double>> chains, Map<String, Map<Stat, Double>> alternate,
                             Map<String, Double> available, Set<String> unlocked, List<Option> out) {
        JsonElement nodes = at(member, "skill_tree", "nodes", tree);
        if (!nodes.isJsonObject()) nodes = at(member, "mining_core", "nodes");
        if (!nodes.isJsonObject()) return;
        for (String powder : List.of("MITHRIL", "GEMSTONE", "GLACITE")) {
            String p = powder.toLowerCase(Locale.ROOT);
            JsonElement unspent = at(member, "mining_core", "powder_" + p); // already net of powder_spent_<type>
            if (!unspent.isJsonNull()) available.put("POWDER_" + powder, Math.max(0, number(unspent, 0)));
        }
        Set<String> open = new HashSet<>();
        for (String perk : LAYOUT.keySet()) if (number(at(nodes, perk), 0) > 0) open.add(perk);
        open.forEach(n -> unlocked.add(tree + ":" + n));
        // Tokens: tier rewards plus Core of the Mountain levels, minus one per unlocked perk (Core of the Mountain itself
        // is free). Fixture check: tier 10, Core of the Mountain 10 and 26 unlocked perks give 25 = tokens_spent.mountain.
        double xp = number(at(member, "skill_tree", "experience", "mining"), number(at(member, "mining_core", "experience"), -1));
        int tier = 0;
        if (xp >= 0) for (int i = 0; i < HOTM_XP.length; i++) if (xp >= HOTM_XP[i]) tier = i + 1;
        int core = (int)number(at(nodes, "core_of_the_mountain"), 0), tokens = 0;
        for (int i = 0; i < tier; i++) tokens += TIER_TOKENS[i];
        tokens += (core >= 1 ? 1 : 0) + (core >= 5 ? 1 : 0) + (core >= 7 ? 1 : 0) + (core >= 10 ? 2 : 0);
        available.put("TOKEN", (double)Math.max(0, tokens - open.stream().filter(n -> !n.equals("core_of_the_mountain")).count()));
        for (StatBreakdown.Perk perk : StatBreakdown.PERKS) {
            PerkCost cost = PERK_COSTS.get(perk.id());
            if (cost == null) continue;
            int level = (int)number(at(nodes, perk.id()), 0);
            String chain = "hotm:" + tree + ":" + perk.id(), resource = "POWDER_" + cost.powder;
            if (level > 0) {
                chains.put(chain, one(perk.stat(), counted(perk.at(level), perk.condition(), context)));
                alternate.put(chain, one(perk.stat(), counted(perk.at(level + 1), perk.condition(), context)));
            }
            if (level >= perk.maxLevel()) continue;
            List<String> unlock = List.of();
            int needs = 0, from = level;
            String prefix = perk.name() + " " + level + " -> ", how = "";
            Set<Integer> targets = new TreeSet<>(List.of(level + 1, level + 5, level + 10, level + 25, perk.maxLevel()));
            if (level <= 0) {
                List<String> route = path(perk.id(), open);
                if (route == null) continue;
                unlock = route.stream().map(n -> tree + ":" + n).toList();
                needs = route.stream().mapToInt(ProfileUpgrades::tier).max().orElse(0);
                from = 1;
                prefix = "Unlock " + perk.name() + (route.size() > 1 ? " (via " + String.join(", ", route.subList(0, route.size() - 1).stream().map(ProfileUpgrades::title).toList()) + ")" : "") + " -> ";
                how = route.size() + " Token" + (route.size() > 1 ? "s" : "") + " of the Mountain, HOTM " + needs + ". ";
                targets = new TreeSet<>(List.of(1, 5, 10, 25, perk.maxLevel()));
            }
            for (int target : targets) {
                if (target > perk.maxLevel() || target <= level) continue;
                double amount = powder(cost, from, target);
                out.add(new Option(chain, target, prefix + target, "HOTM", one(perk.stat(), counted(perk.at(target), perk.condition(), context)), resource, amount, null,
                        how + String.format(Locale.ROOT, "%,.0f %s Powder; free if your unspent powder and tokens cover it, otherwise grind them.", amount,
                                cost.powder.charAt(0) + cost.powder.substring(1).toLowerCase(Locale.ROOT)),
                        one(perk.stat(), counted(perk.at(target + 1), perk.condition(), context)), unlock, needs));
            }
        }
    }
    private static String title(String perk) {
        StringBuilder b = new StringBuilder();
        for (String w : perk.split("_")) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }
    /** Mining skill: 4 Mining Fortune per level (Spelunker); XP from the official skills resource. Grinding only. */
    private static void skill(JsonObject member, Map<String, Map<Stat, Double>> chains, List<Option> out) {
        JsonElement xp = at(member, "player_data", "experience", "SKILL_MINING");
        JsonElement levels = at(Catalog.progressionRules(), "mining", "levels");
        Integer level = StatBreakdown.miningLevel(member);
        if (xp.isJsonNull() || !levels.isJsonArray() || level == null) return;
        double have = number(xp, 0);
        chains.put("skill:mining", one(Stat.MINING_FORTUNE, 4.0 * level));
        Map<Integer, Double> required = new HashMap<>();
        for (JsonElement rule : levels.getAsJsonArray()) required.put((int)number(at(rule, "level"), 0), number(at(rule, "totalExpRequired"), 0));
        for (int target : new TreeSet<>(List.of(level + 1, level + 5, level + 10, required.keySet().stream().max(Integer::compare).orElse(0)))) {
            if (target <= level || !required.containsKey(target)) continue;
            double need = Math.max(0, required.get(target) - have);
            out.add(new Option("skill:mining", target, "Mining " + level + " -> " + target, "Skill", one(Stat.MINING_FORTUNE, 4.0 * target), "MINING_XP",
                    need, null, String.format(Locale.ROOT, "Grinding: %,.0f more Mining XP (+4 Mining Fortune per level).", need)));
        }
    }
    /** Collection tiers whose unlock rewards include a mining stat (official collections resource). Grinding only. */
    private static void collections(JsonObject member, Map<String, Map<Stat, Double>> chains, List<Option> out) {
        JsonElement counts = at(member, "collection");
        JsonElement rules = at(Catalog.progressionRules(), "collections");
        if (!counts.isJsonObject() || !rules.isJsonObject()) return;
        for (var entry : rules.getAsJsonObject().entrySet()) {
            double count = number(at(counts, entry.getKey()), 0);
            String name = string(at(entry.getValue(), "name"), entry.getKey());
            Map<Stat, Double> total = new EnumMap<>(Stat.class);
            String chain = "collection:" + entry.getKey();
            boolean reached = true;
            for (JsonElement tier : at(entry.getValue(), "tiers").getAsJsonArray()) {
                Map<Stat, Double> reward = rewards(at(tier, "unlocks"));
                double required = number(at(tier, "amountRequired"), Double.MAX_VALUE);
                if (count < required && reached) { reached = false; chains.put(chain, new EnumMap<>(total)); }
                reward.forEach((s, v) -> total.merge(s, v, Double::sum));
                if (reached || reward.isEmpty()) continue;
                int rank = (int)number(at(tier, "tier"), 0);
                out.add(new Option(chain, rank, name + " collection " + rank, "Collection", new EnumMap<>(total), "COLLECTION_" + entry.getKey(),
                        required - count, null, String.format(Locale.ROOT, "Grinding: %,.0f more %s (%,.0f of %,.0f).", required - count, name, count, required)));
            }
        }
    }
    private static Map<Stat, Double> rewards(JsonElement unlocks) {
        Map<Stat, Double> out = new EnumMap<>(Stat.class);
        if (!unlocks.isJsonArray()) return out;
        for (JsonElement u : unlocks.getAsJsonArray()) for (Stat s : StatBreakdown.SUPPORTED) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\+([0-9.]+)[^A-Za-z0-9]*" + java.util.regex.Pattern.quote(s.label) + "$").matcher(string(u, ""));
            if (m.find()) out.merge(s, Double.parseDouble(m.group(1)), Double::sum);
        }
        return out;
    }
    public static Set<String> priceIds() {
        Set<String> ids = new LinkedHashSet<>(List.of("ESSENCE_GOLD", "ESSENCE_DIAMOND"));
        ids.addAll(SHARDS.values());
        return ids;
    }
}

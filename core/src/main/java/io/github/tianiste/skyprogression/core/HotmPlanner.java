package io.github.tianiste.skyprogression.core;

import java.util.*;
import io.github.tianiste.skyprogression.core.ProfileUpgrades.Node;

/**
 * HOTM tree planner: the perk allocation with the best rate for a method, within the player's HOTM tier, Tokens of the
 * Mountain and total powder, valued through MethodRates like the Upgrades tab. A reset is free and refunds every token and
 * all powder except Core of the Mountain, whose level is kept (wiki Heart of the Mountain: "Removed cost of resetting the
 * Heart of the Mountain tree", 2025-07-03; "Tier and Core of the Mountain levels are kept"). Rules and sources:
 * docs/CALCULATIONS.md ("HOTM tree planner").
 *
 * Search (bounded): pick nodes greedily by rate gain per token with every taken stat perk at its maximum (shortest path of
 * locked nodes, Core of the Mountain free), starting from Mining Speed and from the current tree; drop nodes that add
 * nothing; then for each taken perk plan again without it and keep the better tree. Levels are then cut to the lowest level
 * that keeps the rate (the target tree), and when the powder does not cover that, levelled greedily by gain per powder.
 * Both pickaxe abilities are planned (the selected one needs its node) and the better is chosen.
 */
public final class HotmPlanner {
    private HotmPlanner() {}

    /**
     * levels: unlocked nodes (not Core of the Mountain) with their level; disabled: nodes toggled off; budget: powder per type
     * after a reset (unspent + spent), a type missing when unknown; tokens: earned in total; selected: the API's ability id.
     */
    public record Input(String tree, int tier, int core, int tokens, Map<String, Integer> levels, Set<String> disabled,
                        Map<String, Double> unspent, Map<String, Double> budget, boolean omelette, String selected) {}
    /** A planned tree: node levels, the pickaxe ability, the hourly rate (null when the rate model fails), powder and tokens it uses. */
    public record Tree(Map<String, Integer> levels, String ability, Double rate, Map<String, Double> powder, int tokens) {}
    /** One node whose level changes (0 = locked); disabled: toggled off now, so it has to be switched on. */
    public record Change(String id, String name, int from, int to, boolean disabled) {}
    /**
     * before: hourly rate of the current tree and ability; now: best tree the powder covers (null when the powder is unknown);
     * target: best tree with enough powder; other: best tree with the other ability (null when there is none to compare).
     * reset: the plan lowers or removes something, so it starts from a reset (all powder back); otherwise it only adds levels
     * from the unspent powder. need / have: powder per type the plan uses and what pays for it (budget after a reset, else
     * unspent); missing: what the target tree still needs on top (null = unknown). A type missing from have is unknown.
     */
    public record Plan(String unit, Double before, Tree now, Tree target, Tree other, Map<String, Double> need, Map<String, Double> have,
                       Map<String, Double> missing, int tokens, List<Change> changes, boolean reset, List<String> notes) {
        /** Rate gain of the plan the powder covers (else the target) over the current tree, in %; null when unknown. */
        public Double gain() { return gain(now != null ? now : target); }
        public Double targetGain() { return gain(target); }
        private Double gain(Tree t) { return before == null || before <= 0 || t == null || t.rate() == null ? null : (t.rate() / before - 1) * 100; }
    }

    private static final String START = "mining_speed", CORE = "core_of_the_mountain", MSB_NODE = "mining_speed_boost", MANIAC_NODE = "maniac_miner";
    private static final double EPS = 1e-9;
    private static final Map<String, StatBreakdown.Perk> PERKS = new LinkedHashMap<>();
    static { for (StatBreakdown.Perk p : StatBreakdown.PERKS) if (ProfileUpgrades.NODES.containsKey(p.id())) PERKS.put(p.id(), p); }

    /**
     * Plans the tree for the method. baseline: the method's counted stats with the current tree (as the Upgrades tab uses them);
     * ability: the tool's pickaxe ability as rated (null when none). Null when the method has no rate model or the HOTM data is missing.
     */
    public static Plan plan(MiningMethod m, Input in, Map<Stat, Double> baseline, double efficiency, MethodRates.Ability ability, StatContext context) {
        if (in == null || in.tier() < 1 || !MethodRates.supported(m) || baseline == null) return null;
        Eval ev = new Eval(m, in, baseline, efficiency, context);
        Map<String, Integer> current = new TreeMap<>(in.levels());
        Double before = ev.rate(current, in.disabled(), ability);
        List<String> notes = new ArrayList<>();
        // The selected ability's node must be in the tree; the other ability is rated at the same level and cooldown.
        String selectedNode = ability == null ? null : ability.name().equals(MethodRates.MSB) ? MSB_NODE : ability.name().equals(MethodRates.MANIAC) ? MANIAC_NODE : null;
        MethodRates.Ability other = selectedNode == null ? null : MethodRates.alternative(ability);
        Result best = search(ev, in, selectedNode, ability), alt = null;
        if (other != null) {
            Result r = search(ev, in, selectedNode.equals(MSB_NODE) ? MANIAC_NODE : MSB_NODE, other);
            if (r != null && (best == null || better(r.shown(), best.shown()))) { alt = best; best = r; } else alt = r;
        } else if (ability == null) notes.add("No pickaxe ability is shown on the tool, so the planner rates the tree without one.");
        else if (selectedNode == null || ability.level() == 0) notes.add("The tool's ability (" + ability.name() + ") has no rated alternative, so only it is planned.");
        if (best == null) return null;
        Tree now = best.now(), target = best.target(), shown = best.shown();
        if (before != null && (shown.rate() == null || shown.rate() <= before * (1 + 1e-6))) {
            // Nothing beats the current tree: keep it rather than propose a reset for no gain.
            Tree kept = new Tree(Collections.unmodifiableMap(current), ability == null ? "" : ability.name(), before, powder(current), tokens(current.keySet()));
            notes.add(0, "Your current tree is already the best the planner finds for this method.");
            return new Plan(unit(m), before, kept, kept, alt == null ? null : alt.shown(), Map.of(), Map.of(), Map.of(), in.tokens(), List.of(), false, notes);
        }
        boolean reset = lowers(current, shown.levels()), targetReset = lowers(current, target.levels());
        Map<String, Double> need = need(current, shown.levels(), reset), have = reset ? in.budget() : in.unspent(), missing = new TreeMap<>();
        Map<String, Double> targetHave = targetReset ? in.budget() : in.unspent();
        need(current, target.levels(), targetReset).forEach((type, amount) -> {
            Double h = targetHave.get(type);
            missing.put(type, h == null ? null : Math.max(0, amount - h));
        });
        List<Change> changes = new ArrayList<>();
        for (String id : ProfileUpgrades.NODES.keySet()) {
            int from = current.getOrDefault(id, 0), to = shown.levels().getOrDefault(id, 0);
            boolean off = to > 0 && PERKS.containsKey(id) && in.disabled().contains(id);
            if (from != to || off) changes.add(new Change(id, ProfileUpgrades.NODES.get(id).name(), from, to, off));
        }
        if (!in.selected().isEmpty() && !shown.ability().isEmpty() && !shown.ability().equals(abilityName(in.selected()))) notes.add("Select " + shown.ability() + " in the HOTM tree.");
        if (now == null) notes.add("Your powder is not in the API (or a type is missing), so only the full target tree is shown.");
        int spare = in.tokens() - shown.tokens();
        if (spare > 0) notes.add(spare + " Token" + (spare > 1 ? "s" : "") + " of the Mountain left: no perk the rate model values is in reach; spend them on what you like.");
        return new Plan(unit(m), before, now, target, alt == null ? null : alt.shown(), need, have, missing, in.tokens(), changes, reset, notes);
    }
    private static boolean lowers(Map<String, Integer> from, Map<String, Integer> to) {
        return from.entrySet().stream().anyMatch(e -> to.getOrDefault(e.getKey(), 0) < e.getValue());
    }
    /** Powder per type: the whole tree after a reset, else only the added levels. */
    private static Map<String, Double> need(Map<String, Integer> from, Map<String, Integer> to, boolean reset) {
        if (reset) return powder(to);
        Map<String, Double> out = new TreeMap<>();
        to.forEach((id, level) -> {
            Node n = ProfileUpgrades.NODES.get(id);
            int was = from.getOrDefault(id, 0);
            if (n != null && level > Math.max(1, was)) out.merge(n.powder(), n.powder(Math.max(1, was), level), Double::sum);
        });
        return out;
    }
    private static Map<String, Double> powder(Map<String, Integer> levels) {
        Map<String, Double> powder = new TreeMap<>();
        for (var e : levels.entrySet()) {
            Node n = ProfileUpgrades.NODES.get(e.getKey());
            if (n != null && e.getValue() > 1) powder.merge(n.powder(), n.powder(1, e.getValue()), Double::sum);
        }
        return powder;
    }
    private static String abilityName(String node) {
        Node n = ProfileUpgrades.NODES.get(node);
        return n == null ? node : n.name();
    }
    private static String unit(MiningMethod m) {
        MethodRates.Block b = MethodRates.block(m);
        return b == null ? "Flawless / hour" : b.unit() + " / hour";
    }
    private static boolean better(Tree a, Tree b) {
        Double x = a.rate(), y = b.rate();
        if (x == null) return false;
        if (y == null) return true;
        return x > y * (1 + EPS) || Math.abs(x - y) <= y * EPS && a.tokens() < b.tokens();
    }

    /** now: what the powder covers (null when the powder is unknown); target: with enough powder. */
    private record Result(Tree now, Tree target) {
        Tree shown() { return now != null ? now : target; }
    }

    /** Rates a tree: the baseline without the current perk stats plus the tree's (with the Blue Cheese +1 level when held). */
    private static final class Eval {
        final MiningMethod m; final Input in; final double efficiency; final StatContext context;
        final Map<Stat, Double> base = new EnumMap<>(Stat.class);
        Eval(MiningMethod m, Input in, Map<Stat, Double> baseline, double efficiency, StatContext context) {
            this.m = m; this.in = in; this.efficiency = efficiency; this.context = context;
            base.putAll(baseline);
            perkStats(in.levels(), in.disabled()).forEach((s, v) -> base.merge(s, -v, Double::sum));
        }
        Map<Stat, Double> perkStats(Map<String, Integer> levels, Set<String> off) {
            Map<Stat, Double> out = new EnumMap<>(Stat.class);
            for (StatBreakdown.Perk p : PERKS.values()) {
                int level = levels.getOrDefault(p.id(), 0);
                if (level <= 0 || off.contains(p.id())) continue;
                StatContext.Condition c = p.condition();
                double v = c.none() || c.satisfiedBy(context) ? p.at(level + (in.omelette() ? 1 : 0)) : 0;
                if (v != 0) out.merge(p.stat(), v, Double::sum);
            }
            return out;
        }
        Double rate(Map<String, Integer> levels, Set<String> off, MethodRates.Ability a) {
            Map<Stat, Double> stats = new EnumMap<>(base);
            perkStats(levels, off).forEach((s, v) -> stats.merge(s, v, Double::sum));
            try {
                double r = MethodRates.perHour(m, stats, efficiency, a);
                return Double.isFinite(r) ? r : null;
            } catch (IllegalArgumentException e) { return null; }
        }
        Double rate(Map<String, Integer> levels, MethodRates.Ability a) { return rate(levels, Set.of(), a); }
    }

    private static boolean allowed(String id, Input in) {
        if (id.equals(CORE)) return in.tier() >= 5;
        Node n = ProfileUpgrades.NODES.get(id);
        return n != null && n.tier() <= in.tier();
    }
    private static List<String> neighbours(String id) {
        int[] at = position(id);
        List<String> out = new ArrayList<>();
        for (String other : nodes()) { int[] p = position(other); if (Math.abs(p[0] - at[0]) + Math.abs(p[1] - at[1]) == 1) out.add(other); }
        return out;
    }
    private static List<String> nodes() { List<String> all = new ArrayList<>(ProfileUpgrades.NODES.keySet()); all.add(CORE); return all; }
    private static int[] position(String id) { Node n = ProfileUpgrades.NODES.get(id); return n == null ? new int[]{3, 5} : new int[]{n.x(), n.y()}; }
    private static final Map<String, List<String>> NEIGHBOURS = new HashMap<>();
    static { for (String id : nodes()) NEIGHBOURS.put(id, neighbours(id)); }
    private static int tokens(Set<String> open) { return (int)open.stream().filter(n -> !n.equals(CORE)).count(); }

    /** Locked nodes to unlock, fewest tokens first (Core of the Mountain costs none), from the open set to the node; null when unreachable. */
    private static List<String> path(String target, Set<String> open, Input in) {
        if (open.contains(target)) return List.of();
        Map<String, String> from = new HashMap<>();
        Map<String, Integer> cost = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String n : open) for (String next : NEIGHBOURS.get(n)) if (!open.contains(next) && allowed(next, in)) {
            int c = next.equals(CORE) ? 0 : 1;
            if (c < cost.getOrDefault(next, Integer.MAX_VALUE)) { cost.put(next, c); from.put(next, null); if (c == 0) queue.addFirst(next); else queue.addLast(next); }
        }
        Set<String> done = new HashSet<>();
        while (!queue.isEmpty()) {
            String n = queue.pollFirst();
            if (!done.add(n)) continue;
            if (n.equals(target)) {
                LinkedList<String> out = new LinkedList<>();
                for (String at = n; at != null; at = from.get(at)) out.addFirst(at);
                return out;
            }
            for (String next : NEIGHBOURS.get(n)) {
                if (open.contains(next) || !allowed(next, in)) continue;
                int c = cost.get(n) + (next.equals(CORE) ? 0 : 1);
                if (c < cost.getOrDefault(next, Integer.MAX_VALUE)) { cost.put(next, c); from.put(next, n); if (next.equals(CORE)) queue.addFirst(next); else queue.addLast(next); }
            }
        }
        return null;
    }
    private static boolean connected(Set<String> open) {
        if (!open.contains(START)) return false;
        Set<String> seen = new HashSet<>(List.of(START));
        Deque<String> queue = new ArrayDeque<>(seen);
        while (!queue.isEmpty()) for (String next : NEIGHBOURS.get(queue.poll())) if (open.contains(next) && seen.add(next)) queue.add(next);
        return seen.size() == open.size();
    }
    /** Stat perks at their maximum (banned ones at 1), every other node at 1; Core of the Mountain is not a level here. */
    private static Map<String, Integer> maxed(Set<String> open, Set<String> banned) {
        Map<String, Integer> out = new TreeMap<>();
        for (String id : open) if (!id.equals(CORE)) out.put(id, PERKS.containsKey(id) && !banned.contains(id) ? ProfileUpgrades.NODES.get(id).max() : 1);
        return out;
    }
    private static Double rate(Eval ev, Set<String> open, Set<String> banned, MethodRates.Ability a) { return ev.rate(maxed(open, banned), a); }

    /** Adds the stat perk with the best rate gain per token (with its path) until nothing gains or the tokens run out. */
    private static Set<String> grow(Eval ev, Input in, Set<String> seed, Set<String> banned, MethodRates.Ability a) {
        Set<String> open = new HashSet<>(seed);
        while (true) {
            Double now = rate(ev, open, banned, a);
            if (now == null || now <= 0) return open;
            List<String> best = null; double bestScore = 0;
            for (String id : PERKS.keySet()) {
                if (open.contains(id) || banned.contains(id) || !allowed(id, in)) continue;
                List<String> p = path(id, open, in);
                if (p == null) continue;
                Set<String> next = new HashSet<>(open); next.addAll(p);
                int added = tokens(next) - tokens(open);
                if (tokens(next) > in.tokens() || added <= 0) continue;
                Double r = rate(ev, next, banned, a);
                if (r == null) continue;
                double score = (r / now - 1) / added;
                if (score > bestScore + EPS) { bestScore = score; best = p; }
            }
            if (best == null) return open;
            open.addAll(best);
        }
    }
    /** Removes nodes the tree does not need (keeps it connected and the rate), most recently placed rows first. */
    private static Set<String> prune(Eval ev, Set<String> open, String required, Set<String> banned, MethodRates.Ability a) {
        Set<String> out = new HashSet<>(open);
        boolean changed = true;
        while (changed) {
            changed = false;
            Double now = rate(ev, out, banned, a);
            if (now == null) return out;
            for (String id : new TreeSet<>(out)) {
                if (id.equals(START) || id.equals(required) || id.equals(CORE)) continue;
                Set<String> next = new HashSet<>(out); next.remove(id);
                if (!connected(next)) continue;
                Double r = rate(ev, next, banned, a);
                if (r != null && r >= now * (1 - EPS)) { out = next; changed = true; break; }
            }
        }
        return out;
    }
    private static Set<String> build(Eval ev, Input in, Set<String> seed, String required, Set<String> banned, MethodRates.Ability a) {
        Set<String> open = new HashSet<>(seed);
        if (!open.contains(START)) open.add(START);
        if (required != null && !open.contains(required)) {
            List<String> p = path(required, open, in);
            if (p == null) return null;
            open.addAll(p);
        }
        if (tokens(open) > in.tokens() || !connected(open)) return null;
        return prune(ev, grow(ev, in, open, banned, a), required, banned, a);
    }

    /** The best tree with this ability (its node required), or null when the ability's node cannot be reached. */
    private static Result search(Eval ev, Input in, String required, MethodRates.Ability a) {
        List<Set<String>> starts = new ArrayList<>();
        Set<String> fresh = build(ev, in, Set.of(START), required, Set.of(), a);
        if (fresh != null) starts.add(fresh);
        // The current tree as a second start, without nodes above the tier and only when it fits the tokens and stays connected.
        Set<String> currentNodes = new HashSet<>();
        for (String id : in.levels().keySet()) if (allowed(id, in)) currentNodes.add(id);
        if (in.core() > 0 && in.tier() >= 5) currentNodes.add(CORE);
        if (connected(currentNodes) && tokens(currentNodes) <= in.tokens()) {
            Set<String> fromCurrent = build(ev, in, prune(ev, currentNodes, required, Set.of(), a), required, Set.of(), a);
            if (fromCurrent != null) starts.add(fromCurrent);
        }
        if (starts.isEmpty()) return null;
        Set<String> best = null; Double bestRate = null;
        for (Set<String> s : starts) {
            Double r = rate(ev, s, Set.of(), a);
            if (r != null && (bestRate == null || r > bestRate * (1 + EPS) || Math.abs(r - bestRate) <= bestRate * EPS && tokens(s) < tokens(best))) { best = s; bestRate = r; }
        }
        if (best == null) return null;
        // Local improvement: plan again without one taken stat perk (its token goes elsewhere); keep the better tree.
        for (int round = 0; round < 2; round++) {
            boolean improved = false;
            for (String id : new TreeSet<>(best)) {
                if (!PERKS.containsKey(id) || id.equals(START) || id.equals(required)) continue;
                Set<String> ban = Set.of(id);
                Set<String> t = build(ev, in, Set.of(START), required, ban, a);
                if (t == null) continue;
                t = prune(ev, grow(ev, in, t, Set.of(), a), required, Set.of(), a); // the banned perk may come back as a bridge worth levelling
                Double r = rate(ev, t, Set.of(), a);
                if (r != null && r > bestRate * (1 + EPS)) { best = t; bestRate = r; improved = true; }
            }
            if (!improved) break;
        }
        // Target levels: each stat perk at the lowest level that keeps the rate of the maxed tree.
        Map<String, Integer> levels = maxed(best, Set.of());
        Double top = ev.rate(levels, a);
        if (top == null) return null;
        for (String id : PERKS.keySet()) {
            if (!levels.containsKey(id)) continue;
            int lo = 1, hi = levels.get(id);
            while (lo < hi) {
                int mid = (lo + hi) / 2;
                levels.put(id, mid);
                Double r = ev.rate(levels, a);
                if (r != null && r >= top * (1 - EPS)) hi = mid; else lo = mid + 1;
            }
            levels.put(id, lo);
        }
        String name = required == null ? a == null ? "" : a.name() : abilityName(required);
        Tree target = tree(ev, levels, name, a, best);
        // What the powder covers now: types within budget keep the target levels; the rest are levelled by gain per powder.
        boolean known = target.powder().entrySet().stream().allMatch(e -> e.getValue() <= 0 || in.budget().containsKey(e.getKey()));
        Tree now = null;
        if (known) {
            Set<String> short_ = new HashSet<>();
            target.powder().forEach((type, need) -> { if (need > in.budget().get(type) + EPS) short_.add(type); });
            if (short_.isEmpty()) now = target;
            else {
                Map<String, Integer> l = new TreeMap<>(levels);
                Map<String, Double> left = new HashMap<>();
                for (String type : short_) left.put(type, in.budget().get(type));
                for (String id : PERKS.keySet()) if (l.containsKey(id) && short_.contains(ProfileUpgrades.NODES.get(id).powder())) l.put(id, 1);
                for (int step = 0; step < 500; step++) {
                    Double cur = ev.rate(l, a);
                    if (cur == null) break;
                    String bestId = null; int bestTo = 0; double bestScore = 0, bestCost = 0;
                    for (String id : PERKS.keySet()) {
                        Node n = ProfileUpgrades.NODES.get(id);
                        if (!l.containsKey(id) || !short_.contains(n.powder())) continue;
                        int from = l.get(id), cap = levels.get(id);
                        for (int to : new TreeSet<>(List.of(from + 1, from + 5, from + 10, from + 25, cap))) {
                            if (to <= from || to > cap) continue;
                            double cost = n.powder(from, to);
                            if (cost > left.get(n.powder()) + EPS) continue;
                            Map<String, Integer> next = new TreeMap<>(l); next.put(id, to);
                            Double r = ev.rate(next, a);
                            if (r == null || r <= cur * (1 + EPS)) continue;
                            double score = (r / cur - 1) / Math.max(1, cost / Math.max(1, in.budget().get(n.powder())));
                            if (score > bestScore) { bestScore = score; bestId = id; bestTo = to; bestCost = cost; }
                        }
                    }
                    if (bestId == null) break;
                    String type = ProfileUpgrades.NODES.get(bestId).powder();
                    left.put(type, left.get(type) - bestCost);
                    l.put(bestId, bestTo);
                }
                now = tree(ev, l, name, a, best);
            }
        }
        return keep(ev, in, best, now, target, a);
    }
    /**
     * Keeps what the player has where it costs nothing the plan needs: planned nodes go back up to their current level, then
     * current nodes next to the tree are kept (at their level, or at 1) while tokens are left, as long as the powder left after
     * both the plan and the target covers it. Stat perks never lower the rate, so the plan's rate can only stay or rise.
     */
    private static Result keep(Eval ev, Input in, Set<String> open, Tree now, Tree target, MethodRates.Ability a) {
        Map<String, Double> spare = new HashMap<>();
        for (var e : in.budget().entrySet()) {
            double used = Math.max(target.powder().getOrDefault(e.getKey(), 0.0), now == null ? Double.POSITIVE_INFINITY : now.powder().getOrDefault(e.getKey(), 0.0));
            spare.put(e.getKey(), e.getValue() - used);
        }
        Set<String> tree = new HashSet<>(open);
        Map<String, Integer> t = new TreeMap<>(target.levels()), n = now == null ? null : new TreeMap<>(now.levels());
        int left = in.tokens() - tokens(tree);
        for (String id : ProfileUpgrades.NODES.keySet()) {
            int have = in.levels().getOrDefault(id, 0);
            if (!tree.contains(id) || have <= 1) continue;
            raise(id, have, t, n, spare);
        }
        for (boolean added = true; added && left > 0; ) {
            added = false;
            for (String id : ProfileUpgrades.NODES.keySet()) {
                int have = in.levels().getOrDefault(id, 0);
                if (left <= 0 || have <= 0 || tree.contains(id) || !allowed(id, in) || NEIGHBOURS.get(id).stream().noneMatch(tree::contains)) continue;
                tree.add(id); left--; added = true;
                t.put(id, 1); if (n != null) n.put(id, 1);
                raise(id, have, t, n, spare);
            }
        }
        String name = target.ability();
        return new Result(n == null ? null : tree(ev, n, name, a, tree), tree(ev, t, name, a, tree));
    }
    private static void raise(String id, int level, Map<String, Integer> target, Map<String, Integer> now, Map<String, Double> spare) {
        Node node = ProfileUpgrades.NODES.get(id);
        int from = Math.max(target.getOrDefault(id, 1), now == null ? 0 : now.getOrDefault(id, 1));
        if (level <= from) return;
        // The extra powder over the larger of the two trees' levels must fit what neither of them uses.
        double cost = node.powder(from, level);
        if (cost > spare.getOrDefault(node.powder(), 0.0)) return;
        spare.merge(node.powder(), -cost, Double::sum);
        target.put(id, level);
        if (now != null) now.put(id, level);
    }
    private static Tree tree(Eval ev, Map<String, Integer> levels, String ability, MethodRates.Ability a, Set<String> open) {
        return new Tree(Collections.unmodifiableMap(new TreeMap<>(levels)), ability, ev.rate(levels, a), powder(levels), tokens(open));
    }
}

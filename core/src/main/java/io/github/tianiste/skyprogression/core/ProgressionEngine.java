package io.github.tianiste.skyprogression.core;

import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/** Independent alternatives against the same baseline; recommendations are not a cumulative shopping list. */
public final class ProgressionEngine {
    private final List<Item> catalog;
    public ProgressionEngine(List<Item> catalog) { this.catalog = List.copyOf(catalog); }
    public Map<String, Item> bestOwned(Profile p, MiningMethod method, Stat focus) {
        Map<String, Item> result = new LinkedHashMap<>();
        List<Item> candidates=new ArrayList<>(p.gear);
        for(Item base:catalog)if(p.gear.stream().noneMatch(g->g.id.equals(base.id)))candidates.add(base);
        for (Item item : candidates) {
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
                current.containsKey(e.getKey()) ? catalog.stream().filter(i->i.id.equals(current.get(e.getKey()).id))
                        .mapToDouble(i->Model.stat(i.stats,stat)).findFirst().orElse(0) : 0,
                Model.stat(e.getValue().stats, stat))).toList();
    }
    /**
     * Player settings the rate model needs: efficiency (%) and the evaluated tool's pickaxe ability (null when none),
     * plus the evaluated pet's counted stats and the pet alternatives (null / empty when unknown).
     */
    /** resale: modifier-aware sale quotes of the evaluated copies by instance key and "pet" (StatsReport.resale). */
    public record RateBasis(double efficiency, MethodRates.Ability ability, Map<Stat, Double> pet, List<PetUpgrades.Option> petOptions, ProfileUpgrades.State profile,
                            Map<String, Price> resale) {
        public RateBasis(double efficiency, MethodRates.Ability ability) { this(efficiency, ability, null, List.of(), ProfileUpgrades.State.EMPTY); }
        public RateBasis(double efficiency, MethodRates.Ability ability, Map<Stat, Double> pet, List<PetUpgrades.Option> petOptions, ProfileUpgrades.State profile) {
            this(efficiency, ability, pet, petOptions, profile, Map.of());
        }
        public RateBasis {
            if (petOptions == null) petOptions = List.of();
            if (profile == null) profile = ProfileUpgrades.State.EMPTY;
            if (resale == null) resale = Map.of();
        }
        RateBasis with(MethodRates.Ability a) { return new RateBasis(efficiency, a, pet, petOptions, profile, resale); }
        /**
         * After taking a pet option: it becomes the pet; a new pet drops the pet-item options for the old one, and a pet-item
         * swap drops every pet option (their fees and carried item assume the old item).
         */
        RateBasis withPet(PetUpgrades.Option taken) {
            return new RateBasis(efficiency, ability, taken.stats(), taken.heldItem() ? List.of() : petOptions.stream().filter(o -> o != taken && !o.heldItem()).toList(), profile, resale);
        }
        RateBasis withProfile(ProfileUpgrades.Option taken) { return new RateBasis(efficiency, ability, pet, petOptions, profile.take(taken), resale); }
        RateBasis withOmelette(boolean held) { return new RateBasis(efficiency, ability, pet, petOptions, profile.withOmelette(held), resale); }
    }
    /** How a method's upgrades are valued: its rate model when it has one, else the drop multiplier. */
    public static boolean rateModel(MiningMethod m) { return MethodRates.supported(m); }

    public List<Upgrade> upgrades(Profile p, MiningMethod m, Stat focus, Map<Stat, Double> baseline, Map<String, Price> prices) {
        return upgrades(p, m, focus, baseline, prices, null);
    }
    public List<Upgrade> upgrades(Profile p, MiningMethod m, Stat focus, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis) {
        if (!p.inventoryEnabled) return List.of(); // Missing API is not an empty inventory.
        // Levels of one source (HOTM perk, essence perk, attribute, Efficiency, Prismatic, Polarvoid, one gem slot) are
        // alternatives: list only the best value of each; the plan still steps through them.
        Map<String, List<Upgrade>> chains = new LinkedHashMap<>();
        for (Candidate c : candidates(p, bestOwned(p, m, focus), m, focus, baseline, prices, basis))
            chains.computeIfAbsent(listKey(c), k -> new ArrayList<>()).add(c.upgrade());
        List<Upgrade> result = new ArrayList<>();
        for (List<Upgrade> chain : chains.values()) {
            Upgrade best = chain.stream().min(Comparator.comparing((Upgrade u) -> !u.blocked().isEmpty()).thenComparing(u -> u.cost() == null)
                    .thenComparingDouble(Upgrade::costPerUnit).thenComparingDouble(u -> u.cost() == null ? u.gain() : -u.gain())).orElseThrow(); // unpriced: nearest level
            if (chain.size() > 1) best.item().source += " (" + (chain.size() - 1) + " other level" + (chain.size() > 2 ? "s" : "") + " of this hidden; the plan steps through them.)";
            result.add(best);
        }
        result.sort(Comparator.comparing((Upgrade u) -> !u.blocked().isEmpty()).thenComparingDouble(Upgrade::costPerUnit));
        return result;
    }

    /** One plan step: the upgrade taken (gain against the previous step), the running gain and cost, and the rate after it. */
    /** totalResale: known sale value of everything the steps so far replace; unpricedResale: replaced items without a sale price. */
    public record Step(Upgrade upgrade, double totalGain, double totalCost, Double rate, double totalResale, int unpricedResale) {}
    /**
     * Step-by-step plan: take the available upgrade with the lowest known cost per 1%, apply it (gear, stats, ability,
     * used materials), recompute every candidate from the new state, repeat. Blocked and unpriced upgrades are skipped.
     * Two passes: the greedy one, and one with a two-step lookahead (pairs that pay off only together, and "A then B" against
     * "B alone" before a step is taken). The lookahead plan is kept only when it is no worse at equal spend.
     */
    public List<Step> plan(Profile p, MiningMethod m, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis, int maxSteps) {
        if (!p.inventoryEnabled) return List.of();
        List<Step> greedy = plan(p, m, baseline, prices, basis, maxSteps, false);
        if (maxSteps <= 0) return greedy;
        List<Step> ahead = plan(p, m, baseline, prices, basis, maxSteps, true);
        return noWorse(ahead, greedy) ? ahead : greedy;
    }
    private List<Step> plan(Profile p, MiningMethod m, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis, int maxSteps, boolean lookahead) {
        // A step that replaces what an earlier step bought (a second reforge on one item, a new helmet after enchanting
        // the old one) would pay twice: ban the earlier step and plan again, so the later choice is taken directly.
        Set<String> banned = new HashSet<>();
        List<Step> steps;
        int restarts = 0;
        while ((steps = plan(p, m, baseline, prices, basis, maxSteps, banned, false, lookahead)) == null && ++restarts <= 2 * maxSteps) {}
        return steps != null ? steps : plan(p, m, baseline, prices, basis, maxSteps, banned, true, lookahead);
    }
    /**
     * Whether plan a is no worse than plan b at equal spend: at each plan's total cost, a's running gain (its last step within
     * that spend) is at least b's.
     */
    static boolean noWorse(List<Step> a, List<Step> b) {
        if (a.isEmpty()) return b.isEmpty();
        for (double spend : new double[] {a.getLast().totalCost(), b.isEmpty() ? 0 : b.getLast().totalCost()})
            if (gainAt(a, spend) < gainAt(b, spend) - 1e-9) return false;
        return true;
    }
    private static double gainAt(List<Step> steps, double spend) {
        double gain = 0;
        for (Step s : steps) if (s.totalCost() <= spend * (1 + 1e-12) + 1e-6) gain = Math.max(gain, s.totalGain());
        return gain;
    }
    /** What a plan pass changes as it goes: the profile copy (owned items), the gear by slot, the effective stats and the rate basis. */
    private final class PlanState {
        final Profile state; final Map<String, Item> owned; Map<Stat, Double> stats; RateBasis current;
        PlanState(Profile state, Map<String, Item> owned, Map<Stat, Double> stats, RateBasis current) { this.state = state; this.owned = owned; this.stats = stats; this.current = current; }
        PlanState copy() {
            Profile p = new Profile();
            p.mode = state.mode; p.hotm = state.hotm; p.enchantingLevel = state.enchantingLevel; p.inventoryEnabled = true;
            p.owned = new HashMap<>(state.owned); p.collections = state.collections; p.milestones = state.milestones;
            Map<String, Item> gear = new LinkedHashMap<>();
            owned.forEach((group, item) -> gear.put(group, item.copy()));
            return new PlanState(p, gear, plus(stats, Map.of()), current);
        }
        List<Candidate> candidates(MiningMethod m, Map<String, Price> prices, boolean keepZero) { return ProgressionEngine.this.candidates(state, owned, m, null, stats, prices, current, keepZero); }
        /** Applies a step (each part of a pair or bundle in turn); the owned items it used are gone for later steps. */
        void commit(Candidate c) {
            if (c.parts() != null) c.parts().forEach(this::apply);
            else apply(c);
            c.materials().forEach((id, n) -> state.owned.computeIfPresent(id, (k, have) -> Math.max(0, have - n)));
        }
        private void apply(Candidate c) {
            Upgrade u = c.upgrade();
            if (c.pet() != null) current = current.withPet(c.pet());
            else if (c.profile() != null) current = current.withProfile(c.profile());
            else if (c.set() != null) c.set().forEach(i -> owned.put(i.group, i.copy()));
            else if (c.apply() != null) {
                c.apply().accept(u.current());
                boolean omelette = StatBreakdown.blueCheese(u.current());
                if (current != null && "DRILL".equals(u.current().category) && omelette != current.profile().omelette()) current = current.withOmelette(omelette);
                u.delta().forEach((st, v) -> u.current().stats.merge(st, v, Double::sum));
            } else owned.put(u.item().group, u.item().copy());
            stats = plus(stats, u.delta());
            if (u.cooldownFactor() != 1 && current != null && current.ability() != null && current.ability().cooldown() != null) {
                var a = current.ability();
                current = current.with(a.withCooldown(a.cooldown() * u.cooldownFactor()));
            }
        }
    }
    /** Lookahead bounds: candidates considered for pairs (best by cost per 1%, and cheapest without a gain of their own), and re-picks per step. */
    private static final int PAIR_POOL = 10, RECHECKS = 3;
    /** One plan pass; null when a conflict was found (its earlier step is then added to banned). */
    private List<Step> plan(Profile p, MiningMethod m, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis, int maxSteps, Set<String> banned,
                            boolean ignoreConflicts, boolean lookahead) {
        Profile profile = new Profile();
        profile.mode = p.mode; profile.hotm = p.hotm; profile.enchantingLevel = p.enchantingLevel; profile.inventoryEnabled = true;
        profile.owned = new HashMap<>(p.owned); profile.collections = p.collections; profile.milestones = p.milestones;
        Map<String, Item> gear = new LinkedHashMap<>();
        bestOwned(p, m, null).forEach((group, item) -> gear.put(group, item.copy()));
        PlanState now = new PlanState(profile, gear, plus(baseline, Map.of()), basis);
        double start = metric(m, now.stats, now.current), total = 0, resale = 0;
        int unpriced = 0;
        List<Step> steps = new ArrayList<>();
        List<Candidate> taken = new ArrayList<>();
        while (steps.size() < maxSteps) {
            List<Candidate> all = now.candidates(m, prices, lookahead);
            Set<String> skipped = new HashSet<>(banned);
            Candidate best = choose(all, skipped, m, now, lookahead);
            // "A then B" against "B alone": if the step after A would throw A away, take the next choice instead.
            for (int i = 0; lookahead && best != null && i < RECHECKS; i++) {
                PlanState next = now.copy();
                Candidate again = find(next.candidates(m, prices, true), best);
                if (again == null) break;
                next.commit(again);
                Candidate after = choose(next.candidates(m, prices, false), banned, m, next, false);
                if (after == null || Collections.disjoint(replaces(after), owns(best))) break;
                skipped.add(id(best));
                Candidate other = choose(all, skipped, m, now, true);
                if (other == null) break;
                best = other;
            }
            if (best == null) break;
            if (!ignoreConflicts) for (Candidate earlier : taken) {
                if (Collections.disjoint(replaces(best), owns(earlier))) continue;
                banned.add(id(earlier));
                return null;
            }
            taken.add(best);
            Upgrade u = best.upgrade();
            now.commit(best);
            total += u.cost();
            if (!u.resaleNote().isEmpty()) { if (u.resale() != null) resale += u.resale(); else unpriced++; }
            double rate = metric(m, now.stats, now.current);
            steps.add(new Step(u, (rate / start - 1) * 100, total, u.rateAfter(), resale, unpriced));
        }
        return steps;
    }
    /** The candidate (or, for a pair or bundle, the same parts) in a list made from another copy of the state; null when gone. */
    private static Candidate find(List<Candidate> list, Candidate c) {
        if (c.parts() == null || c.upgrade().item().id.startsWith("bundle:")) return list.stream().filter(x -> id(x).equals(id(c))).findFirst().orElse(null);
        List<Candidate> parts = new ArrayList<>();
        for (Candidate part : c.parts()) {
            Candidate x = find(list, part);
            if (x == null) return null;
            parts.add(x);
        }
        return new Candidate(c.upgrade(), null, c.materials(), null, null, null, parts);
    }
    /**
     * The next step: the single upgrade with the lowest cost per 1% (ties: larger gain); with lookahead, also every pair of
     * independent upgrades from a bounded pool, taken together when the pair's cost per 1% beats every single upgrade (an
     * upgrade with no gain of its own, such as Mining Speed short of a tick threshold, can pay off with another).
     */
    private Candidate choose(List<Candidate> all, Set<String> skipped, MiningMethod m, PlanState now, boolean pairs) {
        List<Candidate> usable = all.stream().filter(c -> c.upgrade().blocked().isEmpty() && c.upgrade().cost() != null && !skipped.contains(id(c))).toList();
        Candidate best = null;
        for (Candidate c : usable) {
            Upgrade u = c.upgrade();
            if (u.gain() <= 1e-9) continue;
            if (best == null || u.costPerUnit() < best.upgrade().costPerUnit()
                    || u.costPerUnit() == best.upgrade().costPerUnit() && u.gain() > best.upgrade().gain()) best = c;
        }
        if (!pairs) return best;
        List<Candidate> pool = new ArrayList<>(usable.stream().filter(c -> c.upgrade().gain() > 1e-9 && c.parts() == null)
                .sorted(Comparator.comparingDouble(c -> c.upgrade().costPerUnit())).limit(PAIR_POOL).toList());
        pool.addAll(usable.stream().filter(c -> c.upgrade().gain() <= 1e-9 && c.parts() == null)
                .sorted(Comparator.comparingDouble(c -> c.upgrade().cost())).limit(PAIR_POOL).toList());
        double here = metric(m, now.stats, now.current), bestRatio = best == null ? Double.POSITIVE_INFINITY : best.upgrade().costPerUnit();
        Candidate pair = null;
        for (int i = 0; i < pool.size(); i++) for (int j = i + 1; j < pool.size(); j++) {
            Candidate a = pool.get(i), b = pool.get(j);
            if (!independent(a, b) || !materialsFit(a, b, now.state)) continue;
            Map<Stat, Double> delta = plus(a.upgrade().delta(), b.upgrade().delta());
            double factor = a.upgrade().cooldownFactor() * b.upgrade().cooldownFactor();
            RateBasis basis = now.current;
            if (factor != 1 && basis != null && basis.ability() != null && basis.ability().cooldown() != null)
                basis = basis.with(basis.ability().withCooldown(basis.ability().cooldown() * factor));
            double gain = (metric(m, plus(now.stats, delta), basis) / here - 1) * 100;
            // Only pairs that gain clearly more together than one after the other (a tick threshold reached); others are two steps.
            double apart = ((1 + Math.max(0, a.upgrade().gain()) / 100) * (1 + Math.max(0, b.upgrade().gain()) / 100) - 1) * 100;
            if (!(gain > apart * 1.01 + 1e-6)) continue;
            double cost = a.upgrade().cost() + b.upgrade().cost();
            if (cost / gain < bestRatio * (1 - 1e-9)) { bestRatio = cost / gain; pair = combine(List.of(a, b), null, gain, cost, a.upgrade().owned() && b.upgrade().owned(), ""); }
        }
        return pair != null ? pair : best;
    }
    /** Two upgrades that can be added together: different slots, parts or level chains, no pet or HOTM-level pair, nothing that rescales the other. */
    private static boolean independent(Candidate a, Candidate b) {
        if (a.parts() != null || b.parts() != null) return false;
        if (a.pet() != null && b.pet() != null || a.profile() != null && b.profile() != null) return false;
        if (!Collections.disjoint(owns(a), owns(b)) && !(a.apply() != null && b.apply() != null)) return false;
        if (!Collections.disjoint(replaces(a), owns(b)) || !Collections.disjoint(replaces(b), owns(a))) return false;
        for (Candidate c : List.of(a, b)) {
            String label = c.upgrade().item().name;
            if (label.startsWith("Recombobulator") || label.contains(GearUpgrades.partName(GearUpgrades.BLUE_CHEESE))) return false; // rescales reforge/gems or HOTM levels
        }
        if (a.apply() != null && b.apply() != null && a.upgrade().current() == b.upgrade().current()) {
            String ka = gearKind(a.upgrade().item().name), kb = gearKind(b.upgrade().item().name);
            return !listKey(a).equals(listKey(b)) && (ka == null || !ka.equals(kb));
        }
        // A replacement item and an in-place upgrade of the item it replaces do not add up.
        for (Candidate[] x : new Candidate[][] {{a, b}, {b, a}})
            if (x[0].apply() != null && x[1].apply() == null && x[1].pet() == null && x[1].profile() == null && owns(x[1]).contains("slot:" + x[0].upgrade().current().group)) return false;
        return true;
    }
    /** Owned items both upgrades count as theirs must be there twice. */
    private static boolean materialsFit(Candidate a, Candidate b, Profile p) {
        for (var e : a.materials().entrySet()) {
            Integer other = b.materials().get(e.getKey());
            if (other != null && e.getValue() + other > p.owned.getOrDefault(e.getKey(), 0)) return false;
        }
        return true;
    }
    /**
     * Several upgrades taken as one step (a pair, or a bundle such as a full drill build): stats and cooldown factors
     * add up, the cost is the sum (or the bundle's own cost), the sale credits add when all are known.
     */
    private static Candidate combine(List<Candidate> parts, String name, double gain, Double cost, boolean owned, String detail) {
        Upgrade first = parts.getFirst().upgrade();
        Map<Stat, Double> delta = new EnumMap<>(Stat.class);
        double factor = 1;
        Map<String, Integer> materials = new HashMap<>();
        StringJoiner names = new StringJoiner(" + "), ids = new StringJoiner("+"), sources = new StringJoiner(" | ");
        Double credit = 0.0; StringJoiner notes = new StringJoiner("; ");
        boolean modification = true, anyResale = false;
        for (Candidate c : parts) {
            Upgrade u = c.upgrade();
            u.delta().forEach((st, v) -> delta.merge(st, v, Double::sum));
            factor *= u.cooldownFactor();
            c.materials().forEach((k, v) -> materials.merge(k, v, Integer::sum));
            names.add(u.item().name); ids.add(id(c)); sources.add(u.item().name + ": " + u.item().source);
            modification &= u.item().modification;
            if (!u.resaleNote().isEmpty()) { anyResale = true; notes.add(u.resaleNote()); credit = credit == null || u.resale() == null ? null : credit + u.resale(); }
        }
        delta.values().removeIf(v -> v == 0);
        Item display = new Item();
        display.id = (name == null ? "pair:" : "bundle:") + ids;
        display.name = name == null ? names.toString() : name;
        display.group = first.item().group; display.icon = first.item().icon; display.modification = modification;
        display.source = (name == null ? "Taken together: each gains less on its own. " : "") + (detail.isEmpty() ? sources.toString() : detail);
        Double after = first.rateBefore() == null ? null : first.rateBefore() * (1 + gain / 100);
        Upgrade u = new Upgrade(display, first.current(), delta, cost, gain, owned, "", factor, first.rateBefore(), after);
        if (anyResale) u = u.withResale(new Resale.Credit(credit, notes.toString()));
        return new Candidate(u, null, materials, null, null, null, List.copyOf(parts));
    }
    /** Rate (MethodRates.unit) when the method has a usable rate model, else the drop multiplier. */
    private static double metric(MiningMethod m, Map<Stat, Double> stats, RateBasis basis) {
        Double r = basis != null && rateModel(m) ? perHour(m, stats, basis, 1) : null; // hourly, so a changing ability cycle cannot skew the total
        return r != null && r > 0 ? r : yieldMultiplier(m, stats);
    }

    /**
     * An upgrade plus what applying it needs: the gear change (null for a replacement item), the owned items it uses, and
     * for a pair or bundle the upgrades it is made of (parts, applied in turn; null otherwise).
     */
    private record Candidate(Upgrade upgrade, java.util.function.Consumer<Item> apply, Map<String, Integer> materials, PetUpgrades.Option pet, ProfileUpgrades.Option profile, List<Item> set,
                             List<Candidate> parts) {
        Candidate(Upgrade upgrade, java.util.function.Consumer<Item> apply, Map<String, Integer> materials) { this(upgrade, apply, materials, null, null, null, null); }
        Candidate(Upgrade upgrade, java.util.function.Consumer<Item> apply, Map<String, Integer> materials, PetUpgrades.Option pet, ProfileUpgrades.Option profile, List<Item> set) {
            this(upgrade, apply, materials, pet, profile, set, null);
        }
    }
    /** Which part of an item an in-place upgrade replaces (null for added levels, recombobulation and coating). */
    private static String gearKind(String label) {
        if (label.contains("[") && label.contains("]")) return "gem:" + label.substring(label.indexOf('[') + 1, label.indexOf(']'));
        if (label.contains(" reforge")) return "reforge";
        if (label.contains("Fuel Tank")) return "fuel";
        if (label.contains("Drill Engine")) return "engine";
        if (label.contains("Omelette") || label.contains("Seasoning")) return "module";
        return null;
    }
    private static String id(Candidate c) { return c.set() != null ? "set:" + c.upgrade().item().name : c.upgrade().item().id; }
    /** Investments a step makes: the slot of the gear it changes or fills, and the part it fits. */
    private static Set<String> owns(Candidate c) {
        Upgrade u = c.upgrade();
        Set<String> keys = new HashSet<>();
        if (c.parts() != null) { c.parts().forEach(x -> keys.addAll(owns(x))); return keys; }
        if (c.apply() != null) {
            keys.add("slot:" + u.current().group);
            String kind = gearKind(u.item().name);
            if (kind != null) keys.add(u.current().instance + "|" + kind);
        } else if (c.set() != null) c.set().forEach(i -> keys.add("slot:" + i.group));
        else if (c.pet() != null) keys.add("pet");
        else if (c.profile() == null) keys.add("slot:" + u.item().group);
        return keys;
    }
    /** What a step throws away: a part it swaps out, the item in a slot it fills, or the current pet. */
    private static Set<String> replaces(Candidate c) {
        Upgrade u = c.upgrade();
        if (c.parts() != null) { Set<String> keys = new HashSet<>(); c.parts().forEach(x -> keys.addAll(replaces(x))); return keys; }
        if (c.apply() != null) {
            String kind = gearKind(u.item().name);
            return kind == null ? Set.of() : Set.of(u.current().instance + "|" + kind);
        }
        if (c.set() != null) { Set<String> keys = new HashSet<>(); c.set().forEach(i -> keys.add("slot:" + i.group)); return keys; }
        if (c.pet() != null) return c.pet().heldItem() ? Set.of() : Set.of("pet");
        if (c.profile() != null) return Set.of();
        return Set.of("slot:" + u.item().group);
    }
    /** List grouping: levels of one source share a key; everything else is its own entry. */
    private static String listKey(Candidate c) {
        if (c.profile() != null) return "profile:" + c.profile().chain();
        if (c.apply() != null) {
            String label = c.upgrade().item().name, kind = gearKind(label);
            if (kind != null && kind.startsWith("gem:")) return c.upgrade().current().instance + "|" + kind;
            java.util.regex.Matcher level = java.util.regex.Pattern.compile("^(Efficiency|Prismatic|Polarvoid Books) ").matcher(label);
            if (level.find()) return c.upgrade().current().instance + "|" + level.group(1);
        }
        return "own:" + id(c) + "|" + System.identityHashCode(c);
    }
    private static final List<String> ARMOR = List.of("Helmet", "Chestplate", "Leggings", "Boots");
    private static final List<String> EQUIPMENT = List.of("Necklace", "Cloak", "Belt", "Gloves");
    /** A set with a mining set bonus: item ID prefix and the four slots it fills. */
    private record SetDef(String prefix, String name, List<String> groups) {}
    private static final List<SetDef> SETS = List.of(new SetDef("MINER_OUTFIT_", "Miner Outfit", ARMOR), new SetDef("GLACITE_", "Glacite", ARMOR),
            new SetDef("MINERAL_", "Mineral", ARMOR), new SetDef("GLOSSY_MINERAL_", "Glossy Mineral", ARMOR),
            new SetDef("MITHRIL_", "Mithril (Dwarf Wannabe)", EQUIPMENT), new SetDef("TITANIUM_", "Titanium (True Dwarf)", EQUIPMENT));
    /**
     * Set bonuses with mining stats for the gear worn (item lore in the NEU repository; wiki Mineral and Glossy Mineral
     * Armor): Miner Outfit full set +40 Mining Speed; Glacite full set +2 Mining Speed per Mining level; Mineral +100 and
     * Glossy Mineral +150 Mining Spread per piece on ores and blocks; Mithril equipment (Dwarf Wannabe) +30 Mining Speed
     * and +5 Mining Fortune, Titanium equipment (True Dwarf) +50 and +40, both in the Dwarven Mines only (counted for
     * methods mined there by default: Coal, Gold). Goblin (Intelligence) and Yog (kills) depend on values the engine
     * does not have and are not counted.
     */
    static Map<Stat, Double> setBonus(Map<String, Item> worn, MiningMethod m, Profile p) {
        Map<String, Integer> count = new HashMap<>();
        for (SetDef set : SETS) for (String group : set.groups()) {
            Item i = worn.get(group);
            if (i != null && i.id.startsWith(set.prefix())) count.merge(set.prefix(), 1, Integer::sum);
        }
        Map<Stat, Double> out = new EnumMap<>(Stat.class);
        if (count.getOrDefault("MINER_OUTFIT_", 0) == 4) out.merge(Stat.MINING_SPEED, 40.0, Double::sum);
        if (count.getOrDefault("GLACITE_", 0) == 4) {
            double level = p.milestones.stream().filter(x -> x.name().equals("Mining skill")).mapToDouble(Milestone::current).findFirst().orElse(0);
            out.merge(Stat.MINING_SPEED, 2 * level, Double::sum);
        }
        if (m.target() == StatContext.Target.ORE || m.target() == StatContext.Target.BLOCK) {
            out.merge(Stat.MINING_SPREAD, 100.0 * count.getOrDefault("MINERAL_", 0), Double::sum);
            out.merge(Stat.MINING_SPREAD, 150.0 * count.getOrDefault("GLOSSY_MINERAL_", 0), Double::sum);
        }
        if (m.defaultZone() == StatContext.Zone.DWARVEN_MINES) {
            if (count.getOrDefault("MITHRIL_", 0) == 4) { out.merge(Stat.MINING_SPEED, 30.0, Double::sum); out.merge(Stat.MINING_FORTUNE, 5.0, Double::sum); }
            if (count.getOrDefault("TITANIUM_", 0) == 4) { out.merge(Stat.MINING_SPEED, 50.0, Double::sum); out.merge(Stat.MINING_FORTUNE, 40.0, Double::sum); }
        }
        out.values().removeIf(v -> v == 0);
        return out;
    }
    /**
     * With a rate basis and no stat focus, methods with a rate model rank by rate after / rate before - 1 (so Mining
     * Speed counts only when it changes ticks and ability cooldown counts through uptime); other methods use the drop
     * multiplier, which gives speed and cooldown no value.
     */
    private List<Candidate> candidates(Profile p, Map<String, Item> owned, MiningMethod m, Stat focus, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis) {
        return candidates(p, owned, m, focus, baseline, prices, basis, false);
    }
    /** Whether a candidate is left out: no finite gain, or no gain (kept with keepZero when it improves a stat or the cooldown, for pairs). */
    private static boolean skip(double gain, Map<Stat, Double> delta, double cooldownFactor, boolean keepZero) {
        if (!Double.isFinite(gain)) return true;
        if (gain > 1e-9) return false;
        return !keepZero || gain < -1e-9 || delta.values().stream().noneMatch(v -> v > 0) && cooldownFactor >= 1;
    }
    /** keepZero: also return upgrades without a gain of their own (gain 0) that improve a stat, for the plan's pair lookahead. */
    private List<Candidate> candidates(Profile p, Map<String, Item> owned, MiningMethod m, Stat focus, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis,
                                       boolean keepZero) {
        List<Candidate> result = new ArrayList<>();
        Double before = focus == null && basis != null && rateModel(m) ? rate(m, baseline, basis, 1) : null;
        if (before != null && !(before > 0)) before = null; // No rate to compare against; fall back to drops.
        for(Item gear:owned.values()) {
          List<Candidate> parts=new ArrayList<>();
          List<GearUpgrades.Plan> plans=new ArrayList<>();
          for(var proposal:GearUpgrades.forItem(gear,m,p,prices,basis==null?Map.of():basis.profile().omeletteGain())) {
            Double after=before==null?null:rate(m,plus(baseline,proposal.delta()),basis,proposal.cooldownFactor());
            double gain=before!=null?(after==null?Double.NaN:(after/before-1)*100):focus==null?yieldGain(m,baseline,proposal.delta()):stat(proposal.delta(),focus);
            boolean drillPart="DRILL".equals(gear.category)&&focus==null&&gearKind(proposal.label())!=null&&Double.isFinite(gain)&&gain>=-1e-9;
            if(skip(gain,proposal.delta(),proposal.cooldownFactor(),keepZero)&&!drillPart)continue;
            var cost=GearUpgrades.cost(proposal.plan(),p,prices);
            Item display=new Item();display.id="upgrade:"+gear.instance+":"+proposal.label();display.name=proposal.label();
            display.modification=true;display.source=cost.detail();display.icon=UpgradeIcons.proposal(proposal);
            String blocked=proposal.blocked().isEmpty()&&!cost.locked().isEmpty()?"Craft needs "+cost.locked()+" first":proposal.blocked();
            if(!cost.materialsOwned()&&("ironman".equalsIgnoreCase(p.mode)||"bingo".equalsIgnoreCase(p.mode)))blocked="Gather missing materials on "+p.mode;
            Resale.Credit credit=proposal.returned().isEmpty()?null:Resale.credit(null,proposal.returned(),Map.of(),prices);
            Candidate c=new Candidate(new Upgrade(display,gear,proposal.delta(),cost.coins(),gain,cost.materialsOwned(),blocked,proposal.cooldownFactor(),before,after).withResale(credit),
                    proposal.apply(),cost.used());
            if(drillPart&&blocked.isEmpty()&&cost.coins()!=null){parts.add(c);plans.add(proposal.plan());}
            if(!skip(gain,proposal.delta(),proposal.cooldownFactor(),keepZero))result.add(c);
          }
          Candidate build=drillBuild(parts,plans,m,p,prices,baseline,basis,before);
          if(build!=null)result.add(build);
        }
        for (Item i : catalog) {
            if (!i.applies(m)) continue;
            Item current = owned.get(i.group);
            if (current != null && i.id.equals(current.id)) continue;
            Map<Stat, Double> delta = new EnumMap<>(Stat.class);
            for (Stat s : Stat.values()) delta.put(s, stat(i.stats, s) - (current == null ? 0 : stat(current.stats, s)));
            if (ARMOR.contains(i.group) || EQUIPMENT.contains(i.group)) {
                Map<String, Item> worn = new HashMap<>(owned); worn.put(i.group, i);
                setBonus(worn, m, p).forEach((s, v) -> delta.merge(s, v, Double::sum));
                setBonus(owned, m, p).forEach((s, v) -> delta.merge(s, -v, Double::sum));
            }
            Double after = before == null ? null : rate(m, plus(baseline, delta), basis, 1);
            double gain = before != null ? (after == null ? Double.NaN : (after / before - 1) * 100) : focus == null ? yieldGain(m, baseline, delta) : stat(delta, focus);
            if (skip(gain, delta, 1, keepZero)) continue;
            boolean have = p.owned.getOrDefault(i.id, 0) > 0;
            Double cost = null;
            Item shown = i;
            String locked = "";
            Map<String, Integer> used = have ? Map.of(i.id, 1) : Map.of();
            if (have) cost = 0.0;
            else {
                Recipes.Path path = Recipes.acquire(i.id, 1, p, prices);
                cost = path.coins();
                locked = path.locked();
                used = path.used();
                if (!path.detail().isEmpty()) { shown = i.copy(); shown.source = path.detail(); }
            }
            String blocked = p.hotm < i.hotm ? "Requires HOTM " + i.hotm : locked.isEmpty() ? "" : "Craft needs " + locked + " first";
            if ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode)) blocked = "No market purchases on " + p.mode;
            Resale.Credit credit = current == null ? null : Resale.credit(current.instance.isBlank() ? null : current.instance, current.id, resale(basis), prices);
            result.add(new Candidate(new Upgrade(shown, current, delta, cost, gain, have, blocked, 1, before, after).withResale(credit), null, used));
        }
        // Full sets whose set bonus has mining stats: all four pieces at once.
        for (SetDef set : SETS) {
            List<Item> pieces = new ArrayList<>();
            for (String group : set.groups()) catalog.stream().filter(i -> i.group.equals(group) && i.id.startsWith(set.prefix())).findFirst().ifPresent(pieces::add);
            if (pieces.size() != set.groups().size() || pieces.stream().allMatch(i -> owned.get(i.group) != null && owned.get(i.group).id.equals(i.id))) continue;
            Map<String, Item> worn = new HashMap<>(owned);
            Map<Stat, Double> delta = new EnumMap<>(Stat.class);
            for (Item i : pieces) {
                Item current = owned.get(i.group);
                for (Stat s : Stat.values()) delta.merge(s, stat(i.stats, s) - (current == null ? 0 : stat(current.stats, s)), Double::sum);
                worn.put(i.group, i);
            }
            setBonus(worn, m, p).forEach((s, v) -> delta.merge(s, v, Double::sum));
            setBonus(owned, m, p).forEach((s, v) -> delta.merge(s, -v, Double::sum));
            delta.values().removeIf(v -> v == 0);
            Double after = before == null ? null : rate(m, plus(baseline, delta), basis, 1);
            double gain = before != null ? (after == null ? Double.NaN : (after / before - 1) * 100) : focus == null ? yieldGain(m, baseline, delta) : stat(delta, focus);
            if (!Double.isFinite(gain) || gain <= 1e-9) continue;
            Double cost = 0.0; boolean have = true; int hotm = 0;
            Set<String> locked = new LinkedHashSet<>();
            Map<String, Integer> left = new HashMap<>(p.owned), used = new HashMap<>();
            for (Item i : pieces) {
                hotm = Math.max(hotm, i.hotm);
                if (owned.get(i.group) != null && owned.get(i.group).id.equals(i.id)) continue;
                if (left.getOrDefault(i.id, 0) > 0) { left.merge(i.id, -1, Integer::sum); used.merge(i.id, 1, Integer::sum); continue; }
                have = false;
                Recipes.Path path = Recipes.acquire(i.id, 1, p, prices, left);
                cost = cost == null || path.coins() == null ? null : cost + path.coins();
                path.used().forEach((k, v) -> { left.merge(k, -v, Integer::sum); used.merge(k, v, Integer::sum); });
                if (!path.locked().isEmpty()) locked.add(path.locked());
            }
            Item display = new Item(); display.id = pieces.getFirst().id; display.name = "Full " + set.name() + " set";
            display.group = pieces.getFirst().group; display.source = "All four pieces, for the set bonus; owned pieces cost nothing.";
            String blocked = p.hotm < hotm ? "Requires HOTM " + hotm : locked.isEmpty() ? "" : "Craft needs " + String.join(", ", locked) + " first";
            if (!have && ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode))) blocked = "No market purchases on " + p.mode;
            Resale.Credit credit = null;
            for (Item i : pieces) {
                Item old = owned.get(i.group);
                if (old == null || old.id.equals(i.id)) continue;
                Resale.Credit c = Resale.credit(old.instance.isBlank() ? null : old.instance, old.id, resale(basis), prices);
                credit = credit == null ? c : new Resale.Credit(credit.credit() == null || c.credit() == null ? null : credit.credit() + c.credit(),
                        credit.credit() == null ? credit.note() : c.credit() == null ? c.note() : "replaced pieces at their sale values");
            }
            result.add(new Candidate(new Upgrade(display, null, delta, cost, gain, have, blocked, 1, before, after).withResale(credit), null, used, null, null, pieces));
        }
        // Pets: option stats minus the evaluated pet's, both counted in the same context.
        if (basis != null && basis.pet() != null) for (PetUpgrades.Option o : basis.petOptions()) {
            Map<Stat, Double> delta = new EnumMap<>(Stat.class);
            for (Stat s : Stat.values()) {
                double d = o.stats().getOrDefault(s, 0.0) - basis.pet().getOrDefault(s, 0.0);
                if (d != 0) delta.put(s, d);
            }
            Double after = before == null ? null : rate(m, plus(baseline, delta), basis, 1);
            double gain = before != null ? (after == null ? Double.NaN : (after / before - 1) * 100) : focus == null ? yieldGain(m, baseline, delta) : stat(delta, focus);
            if (skip(gain, delta, 1, keepZero)) continue;
            Item display = new Item(); display.id = "pet:" + o.priceId(); display.name = o.label(); display.group = "Pet"; display.icon = UpgradeIcons.pet(o);
            boolean have = o.owned() || o.heldItem() && p.owned.getOrDefault(o.priceId(), 0) > 0;
            String fees = o.fees() == null ? "the pet item fee is unknown (rarity of " + o.returned() + " not known)"
                    : o.fees() > 0 ? String.format(Locale.ROOT, "%,.0f coins in fees", o.fees()) : "";
            display.source = o.heldItem()
                    ? (have ? "Pet item you own" : "Pet item") + (o.returned().isEmpty() ? "" : "; replacing gives the current " + StartWith.petItem(o.returned()) + " back")
                            + (fees.isEmpty() ? "." : " (" + fees + ").")
                    : "Auction House, level 100" + (o.carried().isEmpty() ? "." : "; keeps your " + StartWith.petItem(o.carried())
                            + ", moved with a Super Scrubber (" + fees + ").");
            Price quote = prices.get(o.priceId());
            Double price = have ? Double.valueOf(0) : quote != null && quote.available && quote.buy > 0 && Double.isFinite(quote.buy) ? Double.valueOf(quote.buy) : null;
            Double cost = price == null || o.fees() == null ? null : price + o.fees();
            if (price == null) display.source += quote == null ? " Price unknown: no quote for it yet." : o.heldItem()
                    ? " Price unknown: nobody is selling one right now." : " Price unknown: no level-100 one of this rarity is listed right now.";
            String blocked = !have && ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode)) ? "No market purchases on " + p.mode : "";
            Resale.Credit credit = o.heldItem() ? (o.returned().isEmpty() ? null : Resale.credit(null, o.returned(), Map.of(), prices))
                    : o.carried().isEmpty() ? Resale.credit("pet", null, resale(basis), prices)
                    : new Resale.Credit(null, "No sale price for the replaced pet without its " + StartWith.petItem(o.carried()));
            result.add(new Candidate(new Upgrade(display, null, delta, cost, gain, have, blocked, 1, before, after).withResale(credit), null, Map.of(), o, null, null));
        }
        // Essence perks, attribute shards and HOTM perk levels: target level stats minus the chain's current stats.
        if (basis != null) for (ProfileUpgrades.Option o : basis.profile().options()) {
            Map<Stat, Double> now = basis.profile().current(o.chain()), then = basis.profile().stats(o);
            Map<Stat, Double> delta = new EnumMap<>(Stat.class);
            for (Stat s : Stat.values()) {
                double d = then.getOrDefault(s, 0.0) - now.getOrDefault(s, 0.0);
                if (d != 0) delta.put(s, d);
            }
            Double after = before == null ? null : rate(m, plus(baseline, delta), basis, 1);
            double gain = before != null ? (after == null ? Double.NaN : (after / before - 1) * 100) : focus == null ? yieldGain(m, baseline, delta) : stat(delta, focus);
            if (skip(gain, delta, 1, keepZero)) continue;
            Double cost = basis.profile().cost(o, prices);
            double have = basis.profile().available().getOrDefault(o.resource(), 0.0), need = basis.profile().remaining(o);
            Item display = new Item(); display.id = "profile:" + o.chain() + ":" + o.rank(); display.name = o.label(); display.group = o.group(); display.icon = UpgradeIcons.option(o);
            display.modification = true;
            if (o.grinding() && cost == null) display.category = "GRIND";
            display.source = o.note() + (o.resource().startsWith("ESSENCE_") || o.resource().startsWith("SHARD_") || o.resource().startsWith("POWDER_")
                    ? String.format(Locale.ROOT, " Needs %,.0f %s, %,.0f available.", need, o.resource(), have) : "");
            int tokens = basis.profile().tokens(o);
            if (tokens > 0) display.source += String.format(Locale.ROOT, " Needs %d Token%s of the Mountain, %,.0f unspent.", tokens, tokens > 1 ? "s" : "",
                    basis.profile().available().getOrDefault("TOKEN", 0.0));
            String blocked = o.priceId() != null && need > have && ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode))
                    ? "No bazaar purchases on " + p.mode : p.hotm < o.tier() ? "Reach HOTM " + o.tier() + " first (grinding)" : "";
            result.add(new Candidate(new Upgrade(display, null, delta, cost, gain, cost != null && cost == 0, blocked, 1, before, after), null, Map.of(), null, o, null));
        }
        return result;
    }
    /**
     * Full drill build: the best part of each kind the drill can still take (engine, fuel tank, module, reforge, each gem
     * slot; best = largest rate gain alone, then the larger stat change), fitted together as one upgrade. Parts that add
     * nothing alone (Mining Speed short of a tick threshold) can count together. Owned materials are shared over the build.
     */
    private Candidate drillBuild(List<Candidate> parts, List<GearUpgrades.Plan> plans, MiningMethod m, Profile p, Map<String, Price> prices,
                                 Map<Stat, Double> baseline, RateBasis basis, Double before) {
        Map<String, Integer> pick = new LinkedHashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            String kind = gearKind(parts.get(i).upgrade().item().name);
            Integer old = pick.get(kind);
            if (old == null || better(parts.get(i).upgrade(), parts.get(old).upgrade())) pick.put(kind, i);
        }
        if (pick.size() < 2) return null;
        List<Candidate> chosen = new ArrayList<>();
        Map<String, Integer> materials = new LinkedHashMap<>();
        double fees = 0;
        Map<Stat, Double> delta = new EnumMap<>(Stat.class);
        double factor = 1;
        for (int i : pick.values()) {
            Candidate c = parts.get(i);
            chosen.add(c);
            plans.get(i).materials().forEach((k, v) -> materials.merge(k, v, Integer::sum));
            fees += plans.get(i).fees();
            c.upgrade().delta().forEach((st, v) -> delta.merge(st, v, Double::sum));
            factor *= c.upgrade().cooldownFactor();
        }
        Double after = before == null ? null : rate(m, plus(baseline, delta), basis, factor);
        double gain = before != null ? (after == null ? Double.NaN : (after / before - 1) * 100) : yieldGain(m, baseline, delta);
        if (!Double.isFinite(gain) || gain <= 1e-9) return null;
        var cost = GearUpgrades.cost(new GearUpgrades.Plan(materials, fees, "Fit each part in turn (drill anvil, Reforge Anvil, Gemstone Grinder)."), p, prices);
        if (cost.coins() == null) return null;
        Candidate build = combine(chosen, "Full drill build (" + chosen.size() + " parts)", gain, cost.coins(), cost.materialsOwned(),
                chosen.stream().map(c -> c.upgrade().item().name).reduce((a, b) -> a + " + " + b).orElse("") + ". " + cost.detail());
        return new Candidate(build.upgrade(), null, cost.used(), null, null, null, build.parts());
    }
    private static boolean better(Upgrade a, Upgrade b) {
        if (Math.abs(a.gain() - b.gain()) > 1e-9) return a.gain() > b.gain();
        double x = a.delta().values().stream().mapToDouble(Double::doubleValue).sum() + 1000 * (1 - a.cooldownFactor());
        double y = b.delta().values().stream().mapToDouble(Double::doubleValue).sum() + 1000 * (1 - b.cooldownFactor());
        return x > y;
    }
    private static Map<String, Price> resale(RateBasis basis) { return basis == null ? Map.of() : basis.resale(); }
    private static Map<Stat, Double> plus(Map<Stat, Double> baseline, Map<Stat, Double> delta) {
        Map<Stat, Double> after = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) after.put(s, stat(baseline, s) + stat(delta, s));
        return after;
    }
    /** Rate over the method's value window (MethodRates.unit) with the ability cooldown scaled by cooldownFactor; null when the stats are invalid. */
    /** The window is the baseline's (before the upgrade's cooldown change), so before and after share one window and the gain stays hourly. */
    private static Double rate(MiningMethod m, Map<Stat, Double> stats, RateBasis basis, double cooldownFactor) {
        Double r = perHour(m, stats, basis, cooldownFactor);
        return r == null ? null : r * MethodRates.windowHours(m, basis.ability());
    }
    private static Double perHour(MiningMethod m, Map<Stat, Double> stats, RateBasis basis, double cooldownFactor) {
        var a = basis.ability();
        if (a != null && cooldownFactor != 1 && a.cooldown() != null) a = a.withCooldown(a.cooldown() * cooldownFactor);
        try { return MethodRates.perHour(m, stats, basis.efficiency(), a); }
        catch (IllegalArgumentException e) { return null; }
    }
    /** Drop-only model. Does not pretend speed, cold, route, abilities or tick thresholds are linear yield. */
    public static double yieldMultiplier(MiningMethod m, Map<Stat, Double> stats) {
        double fortune = Math.max(0, stat(stats, Stat.MINING_FORTUNE) + stat(stats, m.fortune));
        double spread = Math.max(0, stat(stats, m.gems() ? Stat.GEMSTONE_SPREAD : Stat.MINING_SPREAD));
        double pristine = m.gems() ? Math.clamp(stat(stats, Stat.PRISTINE), 0, 100) : 0;
        return (1 + fortune / 100) * (1 + spread / 100) * (1 + pristine * .79);
    }
    public static double yieldGain(MiningMethod m, Map<Stat, Double> baseline, Map<Stat, Double> delta) {
        Map<Stat, Double> after = plus(baseline, delta);
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

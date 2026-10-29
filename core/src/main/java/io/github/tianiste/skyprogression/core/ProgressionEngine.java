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
        boolean outer = RATES.get() == null;
        if (outer) RATES.set(new HashMap<>());
        try {
            List<Step> greedy = plan(p, m, baseline, prices, basis, maxSteps, false);
            if (maxSteps <= 0) return greedy;
            List<Step> ahead = plan(p, m, baseline, prices, basis, maxSteps, true);
            return noWorse(ahead, greedy) ? ahead : greedy;
        } finally { if (outer) RATES.remove(); }
    }
    /**
     * Rates already computed during one plan call: both passes and the lookahead's re-picks score the same stats many times
     * (a Glacite rate walks 90 Cold levels). Keyed by every input of MethodRates.perHour; per thread, cleared after the plan.
     */
    private static final ThreadLocal<Map<RateKey, Optional<Double>>> RATES = new ThreadLocal<>();
    private record RateKey(MiningMethod m, Map<Stat, Double> stats, double efficiency, MethodRates.Ability ability) {}
    private List<Step> plan(Profile p, MiningMethod m, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis, int maxSteps, boolean lookahead) {
        // A step that replaces what an earlier step bought (a second reforge on one item, a new helmet after enchanting
        // the old one) would pay twice: ban the earlier step and plan again, so the later choice is taken directly.
        Set<String> banned = new HashSet<>();
        Trail trail = new Trail();
        List<Step> steps;
        int restarts = 0;
        while ((steps = plan(p, m, baseline, prices, basis, maxSteps, banned, false, lookahead, trail)) == null && ++restarts <= 2 * maxSteps) {}
        return steps != null ? steps : plan(p, m, baseline, prices, basis, maxSteps, banned, true, lookahead, trail);
    }
    /**
     * What the passes of one plan saw at each step, so a restart (one more banned step) does not score every candidate again:
     * the steps whose pick comes out the same with the new ban are replayed from these lists, and scoring resumes at the
     * first step that changes. before: the state before each step (kept untouched); all: the candidates scored there;
     * after: for the lookahead, the candidates after taking a pick in a copy of that state (empty when the pick is gone there).
     * Recorded candidates are only read when replaying (their gear may have changed since); every commit is made with
     * candidates scored on the live state.
     */
    private final class Trail {
        final List<PlanState> before = new ArrayList<>();
        final List<List<Candidate>> all = new ArrayList<>();
        final List<Map<String, Optional<List<Candidate>>>> after = new ArrayList<>();
        final List<Candidate> chosen = new ArrayList<>();
        final List<Step> steps = new ArrayList<>();
        double start;
        /** How many recorded steps pick the same with this ban. */
        int replay(Set<String> banned, MiningMethod m, boolean lookahead) {
            for (int i = 0; i < chosen.size(); i++) {
                Map<String, Optional<List<Candidate>>> known = after.get(i);
                Candidate pick = decide(all.get(i), banned, m, before.get(i), lookahead, c -> known.get(id(c)));
                if (pick == null || pick == UNKNOWN || !id(pick).equals(id(chosen.get(i)))) return i;
            }
            return chosen.size();
        }
        /** Keeps the first n steps, and the state and lookahead lists seen before step n (they do not depend on the ban). */
        void cut(int n) {
            chosen.subList(n, chosen.size()).clear();
            steps.subList(n, steps.size()).clear();
            if (before.size() > n + 1) before.subList(n + 1, before.size()).clear();
            if (all.size() > n) all.subList(n, all.size()).clear();
            if (after.size() > n + 1) after.subList(n + 1, after.size()).clear();
        }
    }
    /** Stands for a pick that depends on a lookahead list not recorded yet (replay only). */
    private static final Candidate UNKNOWN = new Candidate(null, null, Map.of());
    /**
     * The pick at one step: the cheapest per 1% (with lookahead also pairs), then "A then B" against "B alone": if the step
     * after A would throw A away, the next choice instead. afterTaking: the candidates after taking a pick in a copy of the
     * state (empty when the pick is gone there), or null when not known, which returns UNKNOWN.
     */
    private Candidate decide(List<Candidate> all, Set<String> banned, MiningMethod m, PlanState now, boolean lookahead,
                                    java.util.function.Function<Candidate, Optional<List<Candidate>>> afterTaking) {
        Set<String> skipped = new HashSet<>(banned);
        Candidate best = choose(all, skipped, m, now, lookahead);
        for (int i = 0; lookahead && best != null && i < RECHECKS; i++) {
            Optional<List<Candidate>> next = afterTaking.apply(best);
            if (next == null) return UNKNOWN;
            if (next.isEmpty()) break;
            Candidate after = choose(next.get(), banned, m, null, false);
            if (after == null || Collections.disjoint(replaces(after), owns(best)) || best.upgrade().cost() <= STEPPING_STONE * after.upgrade().cost()) break;
            skipped.add(id(best));
            Candidate other = choose(all, skipped, m, now, true);
            if (other == null) break;
            best = other;
        }
        return best;
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
        PlanState copy() { return copy(null); }
        /** moved: filled with this state's gear item -> the copy's, so a candidate scored here can be applied to the copy. */
        PlanState copy(Map<Item, Item> moved) {
            Profile p = new Profile();
            p.mode = state.mode; p.hotm = state.hotm; p.enchantingLevel = state.enchantingLevel; p.inventoryEnabled = true;
            p.owned = new HashMap<>(state.owned); p.collections = state.collections; p.milestones = state.milestones;
            Map<String, Item> gear = new LinkedHashMap<>();
            owned.forEach((group, item) -> {
                Item copy = item.copy();
                gear.put(group, copy);
                if (moved != null) moved.put(item, copy);
            });
            return new PlanState(p, gear, plus(stats, Map.of()), current);
        }
        List<Candidate> candidates(MiningMethod m, Map<String, Price> prices, boolean keepZero) { return ProgressionEngine.this.candidates(state, owned, m, null, stats, prices, current, keepZero); }
        /** Applies a step (each part of a pair or bundle in turn); the owned items it used are gone for later steps. */
        void commit(Candidate c) { commit(c, null); }
        /** moved: for a candidate scored on another state this is a copy of, that state's gear item -> this state's (null: scored here). */
        void commit(Candidate c, Map<Item, Item> moved) {
            if (c.parts() != null) c.parts().forEach(x -> apply(x, moved));
            else apply(c, moved);
            c.materials().forEach((id, n) -> state.owned.computeIfPresent(id, (k, have) -> Math.max(0, have - n)));
        }
        private void apply(Candidate c, Map<Item, Item> moved) {
            Upgrade u = c.upgrade();
            Item gear = moved == null || u.current() == null ? u.current() : moved.get(u.current());
            if (c.pet() != null) current = current.withPet(c.pet());
            else if (c.profile() != null) current = current.withProfile(c.profile());
            else if (c.set() != null) c.set().forEach(i -> owned.put(i.group, i.copy()));
            else if (c.apply() != null) {
                c.apply().accept(gear);
                boolean omelette = StatBreakdown.blueCheese(gear);
                if (current != null && "DRILL".equals(gear.category) && omelette != current.profile().omelette()) current = current.withOmelette(omelette);
                u.delta().forEach((st, v) -> gear.stats.merge(st, v, Double::sum));
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
    /** An earlier step that a later one replaces stays in the plan when it cost at most this share of the replacement. */
    private static final double STEPPING_STONE = 0.1;
    /** One plan pass; null when a conflict was found (its earlier step is then added to banned). */
    private List<Step> plan(Profile p, MiningMethod m, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis, int maxSteps, Set<String> banned,
                            boolean ignoreConflicts, boolean lookahead, Trail trail) {
        int same = trail.replay(banned, m, lookahead);
        trail.cut(same);
        PlanState now;
        if (!trail.before.isEmpty()) now = trail.before.get(same).copy();
        else {
            Profile profile = new Profile();
            profile.mode = p.mode; profile.hotm = p.hotm; profile.enchantingLevel = p.enchantingLevel; profile.inventoryEnabled = true;
            profile.owned = new HashMap<>(p.owned); profile.collections = p.collections; profile.milestones = p.milestones;
            Map<String, Item> gear = new LinkedHashMap<>();
            bestOwned(p, m, null).forEach((group, item) -> gear.put(group, item.copy()));
            now = new PlanState(profile, gear, plus(baseline, Map.of()), basis);
            trail.start = metric(m, now.stats, now.current);
        }
        double start = trail.start;
        Step last = same == 0 ? null : trail.steps.get(same - 1);
        double total = last == null ? 0 : last.totalCost(), resale = last == null ? 0 : last.totalResale();
        int unpriced = last == null ? 0 : last.unpricedResale();
        List<Step> steps = new ArrayList<>(trail.steps);
        List<Candidate> taken = new ArrayList<>(trail.chosen);
        while (steps.size() < maxSteps) {
            int at = steps.size();
            if (trail.before.size() == at) trail.before.add(now.copy());
            List<Candidate> all = now.candidates(m, prices, lookahead);
            trail.all.add(all);
            if (trail.after.size() == at) trail.after.add(new HashMap<>());
            Map<String, Optional<List<Candidate>>> known = trail.after.get(at);
            PlanState here = now;
            Candidate best = decide(all, banned, m, now, lookahead, c -> known.computeIfAbsent(id(c), k -> {
                // c was scored on this state, so it is applied to a copy directly (its upgrades act only on the gear item they get).
                Map<Item, Item> moved = new IdentityHashMap<>();
                PlanState next = here.copy(moved);
                next.commit(c, moved);
                return Optional.of(next.candidates(m, prices, false));
            }));
            if (best == null) break;
            if (!ignoreConflicts) for (Candidate earlier : taken) {
                if (Collections.disjoint(replaces(best), owns(earlier))) continue;
                // A cheap stepping stone (a lantern before Will-o'-wisp, a cheap reforge before an expensive one) is kept:
                // it pays off until the replacement is affordable, and dropping it would make the plan start with the big buy.
                if (earlier.upgrade().cost() <= STEPPING_STONE * best.upgrade().cost()) continue;
                banned.add(id(earlier));
                return null;
            }
            taken.add(best);
            Upgrade u = best.upgrade();
            now.commit(best);
            total += u.cost();
            if (!u.resaleNote().isEmpty()) { if (u.resale() != null) resale += u.resale(); else unpriced++; }
            double rate = metric(m, now.stats, now.current);
            Step step = new Step(u, (rate / start - 1) * 100, total, u.rateAfter(), resale, unpriced);
            steps.add(step);
            trail.chosen.add(best);
            trail.steps.add(step);
        }
        return steps;
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
    /** The gem slot in a socket upgrade's label, e.g. "(Mining slot 1)". */
    private static final java.util.regex.Pattern GEM_SLOT = java.util.regex.Pattern.compile("\\(([A-Za-z]+ slot \\d+)\\)");
    /** Which part of an item an in-place upgrade replaces (null for added levels, recombobulation and coating). */
    private static String gearKind(String label) {
        var slot = GEM_SLOT.matcher(label);
        if (slot.find()) return "gem:" + slot.group(1);
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
        Pause.point();
        List<Candidate> result = new ArrayList<>();
        Double before = focus == null && basis != null && rateModel(m) ? rate(m, baseline, basis, 1) : null;
        if (before != null && !(before > 0)) before = null; // No rate to compare against; fall back to drops.
        for(Item gear:owned.values()) {
          List<Candidate> parts=new ArrayList<>();
          List<GearUpgrades.Plan> plans=new ArrayList<>();
          Map<Stat,Double> omelette=basis==null?Map.of():basis.profile().omeletteGain();
          var proposals=GearUpgrades.forItem(gear,m,p,prices,omelette);
          for(var proposal:proposals) {
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
          for(var proposal:proposals)if(GearUpgrades.forged(proposal)) {
            Candidate forged=forgeBundle(gear,proposal,m,p,prices,omelette,baseline,basis,before);
            if(forged!=null)result.add(forged);
          }
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
    /**
     * A forge upgrade (Divan's Drill from a DR-X655) with the best gem for each gem slot the new tool adds, taken together:
     * alone the forge leaves those slots empty, and their gems only exist after it (Mining Cat lists the drill "with the
     * next lines" the same way). Each slot's gem is the one with the lowest cost per 1% after the forge.
     */
    private Candidate forgeBundle(Item gear, GearUpgrades.Proposal forge, MiningMethod m, Profile p, Map<String, Price> prices, Map<Stat, Double> omelette,
                                  Map<Stat, Double> baseline, RateBasis basis, Double before) {
        if (!forge.blocked().isEmpty()) return null;
        Item forged = gear.copy();
        forge.apply().accept(forged);
        Map<String, Integer> had = new HashMap<>();
        if (gear.slots != null) for (Slot slot : gear.slots) if (slot != null) had.merge(slot.slot_type, 1, Integer::sum);
        Map<Stat, Double> afterForge = plus(baseline, forge.delta());
        Double base = before == null ? null : rate(m, afterForge, basis, 1);
        Map<String, GearUpgrades.Proposal> pick = new LinkedHashMap<>();
        Map<String, Double> perUnit = new HashMap<>();
        for (var gem : GearUpgrades.forItem(forged, m, p, prices, omelette)) {
            String kind = gearKind(gem.label());
            if (kind == null || !kind.startsWith("gem:") || !gem.blocked().isEmpty()) continue;
            String[] slot = kind.substring(4).split(" slot ");
            if (Integer.parseInt(slot[1]) <= had.getOrDefault(slot[0].toUpperCase(Locale.ROOT), 0)) continue; // a slot the old tool has
            Double coins = GearUpgrades.cost(gem.plan(), p, prices).coins();
            if (coins == null) continue;
            double gain = base != null ? (rate(m, plus(afterForge, gem.delta()), basis, 1) / base - 1) * 100 : yieldGain(m, afterForge, gem.delta());
            if (!(gain > 1e-9)) continue;
            double ratio = coins / gain;
            if (!perUnit.containsKey(kind) || ratio < perUnit.get(kind)) { perUnit.put(kind, ratio); pick.put(kind, gem); }
        }
        if (pick.isEmpty()) return null;
        List<GearUpgrades.Proposal> chosen = new ArrayList<>(List.of(forge));
        chosen.addAll(pick.values());
        Map<String, Integer> materials = new LinkedHashMap<>();
        double fees = 0;
        Map<Stat, Double> delta = new EnumMap<>(Stat.class);
        List<Candidate> parts = new ArrayList<>();
        for (var c : chosen) {
            c.plan().materials().forEach((k, v) -> materials.merge(k, v, Integer::sum));
            fees += c.plan().fees();
            c.delta().forEach((st, v) -> delta.merge(st, v, Double::sum));
            var cost = GearUpgrades.cost(c.plan(), p, prices);
            Item display = new Item(); display.id = "upgrade:" + gear.instance + ":" + c.label(); display.name = c.label();
            display.modification = true; display.source = cost.detail(); display.icon = UpgradeIcons.proposal(c);
            parts.add(new Candidate(new Upgrade(display, gear, c.delta(), cost.coins(), 0, cost.materialsOwned(), "", 1, before, null), c.apply(), cost.used()));
        }
        Double after = before == null ? null : rate(m, plus(baseline, delta), basis, 1);
        double gain = before != null ? (after == null ? Double.NaN : (after / before - 1) * 100) : yieldGain(m, baseline, delta);
        if (!Double.isFinite(gain) || gain <= 1e-9) return null;
        var cost = GearUpgrades.cost(new GearUpgrades.Plan(materials, fees, forge.plan().instruction() + " Then socket the new slots (Gemstone Grinder)."), p, prices);
        if (cost.coins() == null) return null;
        String tool = forge.label().substring(0, forge.label().indexOf(" ("));
        Candidate bundle = combine(parts, tool + " + gems in its " + pick.size() + " new slot" + (pick.size() == 1 ? "" : "s"), gain, cost.coins(), cost.materialsOwned(),
                chosen.stream().map(GearUpgrades.Proposal::label).reduce((a, b) -> a + " + " + b).orElse("") + ". " + cost.detail());
        return new Candidate(bundle.upgrade(), null, cost.used(), null, null, null, bundle.parts());
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
        Map<RateKey, Optional<Double>> memo = RATES.get();
        RateKey key = null;
        if (memo != null) {
            key = new RateKey(m, new EnumMap<>(stats), basis.efficiency(), a);
            Optional<Double> hit = memo.get(key);
            if (hit != null) return hit.orElse(null);
        }
        Double r;
        try { r = MethodRates.perHour(m, stats, basis.efficiency(), a); }
        catch (IllegalArgumentException e) { r = null; }
        if (memo != null) memo.put(key, Optional.ofNullable(r));
        return r;
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

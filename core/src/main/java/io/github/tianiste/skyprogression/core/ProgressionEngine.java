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
    public record RateBasis(double efficiency, MethodRates.Ability ability, Map<Stat, Double> pet, List<PetUpgrades.Option> petOptions, ProfileUpgrades.State profile) {
        public RateBasis(double efficiency, MethodRates.Ability ability) { this(efficiency, ability, null, List.of(), ProfileUpgrades.State.EMPTY); }
        public RateBasis {
            if (petOptions == null) petOptions = List.of();
            if (profile == null) profile = ProfileUpgrades.State.EMPTY;
        }
        RateBasis with(MethodRates.Ability a) { return new RateBasis(efficiency, a, pet, petOptions, profile); }
        /** After taking a pet option: it becomes the pet; a new pet drops the pet-item options for the old one. */
        RateBasis withPet(PetUpgrades.Option taken) {
            return new RateBasis(efficiency, ability, taken.stats(), petOptions.stream().filter(o -> o != taken && !o.heldItem()).toList(), profile);
        }
        RateBasis withProfile(ProfileUpgrades.Option taken) { return new RateBasis(efficiency, ability, pet, petOptions, profile.take(taken)); }
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
                    .thenComparingDouble(Upgrade::costPerUnit).thenComparing(Comparator.comparingDouble(Upgrade::gain).reversed())).orElseThrow();
            if (chain.size() > 1) best.item().source += " (" + (chain.size() - 1) + " other level" + (chain.size() > 2 ? "s" : "") + " of this hidden; the plan steps through them.)";
            result.add(best);
        }
        result.sort(Comparator.comparing((Upgrade u) -> !u.blocked().isEmpty()).thenComparingDouble(Upgrade::costPerUnit));
        return result;
    }

    /** One plan step: the upgrade taken (gain against the previous step), the running gain and cost, and the rate after it. */
    public record Step(Upgrade upgrade, double totalGain, double totalCost, Double rate) {}
    /**
     * Step-by-step plan: take the available upgrade with the lowest known cost per 1%, apply it (gear, stats, ability,
     * used materials), recompute every candidate from the new state, repeat. Blocked and unpriced upgrades are skipped.
     */
    public List<Step> plan(Profile p, MiningMethod m, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis, int maxSteps) {
        if (!p.inventoryEnabled) return List.of();
        // A step that replaces what an earlier step bought (a second reforge on one item, a new helmet after enchanting
        // the old one) would pay twice: ban the earlier step and plan again, so the later choice is taken directly.
        Set<String> banned = new HashSet<>();
        List<Step> steps;
        int restarts = 0;
        while ((steps = plan(p, m, baseline, prices, basis, maxSteps, banned)) == null && ++restarts <= 2 * maxSteps) {}
        return steps != null ? steps : plan(p, m, baseline, prices, basis, maxSteps, banned, true);
    }
    private List<Step> plan(Profile p, MiningMethod m, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis, int maxSteps, Set<String> banned) {
        return plan(p, m, baseline, prices, basis, maxSteps, banned, false);
    }
    /** One greedy pass; null when a conflict was found (its earlier step is then added to banned). */
    private List<Step> plan(Profile p, MiningMethod m, Map<Stat, Double> baseline, Map<String, Price> prices, RateBasis basis, int maxSteps, Set<String> banned, boolean ignoreConflicts) {
        Profile state = new Profile();
        state.mode = p.mode; state.hotm = p.hotm; state.enchantingLevel = p.enchantingLevel; state.inventoryEnabled = true;
        state.owned = new HashMap<>(p.owned);
        Map<String, Item> owned = new LinkedHashMap<>();
        bestOwned(p, m, null).forEach((group, item) -> owned.put(group, item.copy()));
        Map<Stat, Double> stats = plus(baseline, Map.of());
        RateBasis current = basis;
        double start = metric(m, stats, current), total = 0;
        List<Step> steps = new ArrayList<>();
        List<Candidate> taken = new ArrayList<>();
        while (steps.size() < maxSteps) {
            Candidate best = null;
            for (Candidate c : candidates(state, owned, m, null, stats, prices, current)) {
                Upgrade u = c.upgrade();
                if (!u.blocked().isEmpty() || u.cost() == null || banned.contains(id(c))) continue;
                if (best == null || u.costPerUnit() < best.upgrade().costPerUnit()
                        || u.costPerUnit() == best.upgrade().costPerUnit() && u.gain() > best.upgrade().gain()) best = c;
            }
            if (best == null) break;
            if (!ignoreConflicts) for (Candidate earlier : taken) {
                if (Collections.disjoint(replaces(best), owns(earlier))) continue;
                banned.add(id(earlier));
                return null;
            }
            taken.add(best);
            Upgrade u = best.upgrade();
            if (best.pet() != null) current = current.withPet(best.pet());
            else if (best.profile() != null) current = current.withProfile(best.profile());
            else if (best.set() != null) best.set().forEach(i -> owned.put(i.group, i.copy()));
            else if (best.apply() != null) {
                best.apply().accept(u.current());
                u.delta().forEach((s, v) -> u.current().stats.merge(s, v, Double::sum));
                best.materials().forEach((id, n) -> state.owned.computeIfPresent(id, (k, have) -> Math.max(0, have - n)));
            } else owned.put(u.item().group, u.item().copy());
            stats = plus(stats, u.delta());
            if (u.cooldownFactor() != 1 && current != null && current.ability() != null && current.ability().cooldown() != null) {
                var a = current.ability();
                current = current.with(new MethodRates.Ability(a.name(), a.boostPercent(), a.duration(), a.cooldown() * u.cooldownFactor()));
            }
            total += u.cost();
            double now = metric(m, stats, current);
            steps.add(new Step(u, (now / start - 1) * 100, total, u.rateAfter()));
        }
        return steps;
    }
    /** Rate (Flawless / hour) when the method has a usable rate model, else the drop multiplier. */
    private static double metric(MiningMethod m, Map<Stat, Double> stats, RateBasis basis) {
        Double r = basis != null && rateModel(m) ? rate(m, stats, basis, 1) : null;
        return r != null && r > 0 ? r : yieldMultiplier(m, stats);
    }

    /** An upgrade plus what applying it needs: the gear change (null for a replacement item) and the materials used. */
    private record Candidate(Upgrade upgrade, java.util.function.Consumer<Item> apply, Map<String, Integer> materials, PetUpgrades.Option pet, ProfileUpgrades.Option profile, List<Item> set) {
        Candidate(Upgrade upgrade, java.util.function.Consumer<Item> apply, Map<String, Integer> materials) { this(upgrade, apply, materials, null, null, null); }
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
        List<Candidate> result = new ArrayList<>();
        Double before = focus == null && basis != null && rateModel(m) ? rate(m, baseline, basis, 1) : null;
        if (before != null && !(before > 0)) before = null; // No rate to compare against; fall back to drops.
        for(Item gear:owned.values()) for(var proposal:GearUpgrades.forItem(gear,m,p,prices)) {
            Double after=before==null?null:rate(m,plus(baseline,proposal.delta()),basis,proposal.cooldownFactor());
            double gain=before!=null?(after==null?Double.NaN:(after/before-1)*100):focus==null?yieldGain(m,baseline,proposal.delta()):stat(proposal.delta(),focus);
            if(!Double.isFinite(gain)||gain<=1e-9)continue;
            var cost=GearUpgrades.cost(proposal.plan(),p,prices);
            Item display=new Item();display.id="upgrade:"+gear.instance+":"+proposal.label();display.name=proposal.label();
            display.modification=true;display.source=cost.detail();
            String blocked=proposal.blocked();
            if(!cost.materialsOwned()&&("ironman".equalsIgnoreCase(p.mode)||"bingo".equalsIgnoreCase(p.mode)))blocked="Gather missing materials on "+p.mode;
            result.add(new Candidate(new Upgrade(display,gear,proposal.delta(),cost.coins(),gain,cost.materialsOwned(),blocked,proposal.cooldownFactor(),before,after),
                    proposal.apply(),proposal.plan().materials()));
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
            if (!Double.isFinite(gain) || gain <= 1e-9) continue;
            boolean have = p.owned.getOrDefault(i.id, 0) > 0;
            Price quote = prices.get(i.id);
            Double cost = null;
            if (have) cost = 0.0;
            else if (quote != null && quote.available && quote.buy > 0 && Double.isFinite(quote.buy)) cost = quote.buy;
            String blocked = p.hotm < i.hotm ? "Requires HOTM " + i.hotm : "";
            if ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode)) blocked = "No market purchases on " + p.mode;
            result.add(new Candidate(new Upgrade(i, current, delta, cost, gain, have, blocked, 1, before, after), null, Map.of()));
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
            for (Item i : pieces) {
                hotm = Math.max(hotm, i.hotm);
                if (owned.get(i.group) != null && owned.get(i.group).id.equals(i.id) || p.owned.getOrDefault(i.id, 0) > 0) continue;
                have = false;
                Price quote = prices.get(i.id);
                cost = cost == null || quote == null || !quote.available || !(quote.buy > 0) || !Double.isFinite(quote.buy) ? null : cost + quote.buy;
            }
            Item display = new Item(); display.id = pieces.getFirst().id; display.name = "Full " + set.name() + " set";
            display.group = pieces.getFirst().group; display.source = "All four pieces, for the set bonus; owned pieces cost nothing.";
            String blocked = p.hotm < hotm ? "Requires HOTM " + hotm : "";
            if (!have && ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode))) blocked = "No market purchases on " + p.mode;
            result.add(new Candidate(new Upgrade(display, null, delta, cost, gain, have, blocked, 1, before, after), null, Map.of(), null, null, pieces));
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
            if (!Double.isFinite(gain) || gain <= 1e-9) continue;
            Item display = new Item(); display.id = "pet:" + o.priceId(); display.name = o.label(); display.group = "Pet";
            display.source = o.heldItem() ? "Pet item; replaces the current pet item" : "Auction House, level 100; the current pet item is not carried over";
            Price quote = prices.get(o.priceId());
            Double cost = o.owned() ? 0.0 : quote != null && quote.available && quote.buy > 0 && Double.isFinite(quote.buy) ? quote.buy : null;
            String blocked = !o.owned() && ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode)) ? "No market purchases on " + p.mode : "";
            result.add(new Candidate(new Upgrade(display, null, delta, cost, gain, o.owned(), blocked, 1, before, after), null, Map.of(), o, null, null));
        }
        // Essence perks, attribute shards and HOTM perk levels: target level stats minus the chain's current stats.
        if (basis != null) for (ProfileUpgrades.Option o : basis.profile().options()) {
            Map<Stat, Double> now = basis.profile().chains().getOrDefault(o.chain(), Map.of());
            Map<Stat, Double> delta = new EnumMap<>(Stat.class);
            for (Stat s : Stat.values()) {
                double d = o.stats().getOrDefault(s, 0.0) - now.getOrDefault(s, 0.0);
                if (d != 0) delta.put(s, d);
            }
            Double after = before == null ? null : rate(m, plus(baseline, delta), basis, 1);
            double gain = before != null ? (after == null ? Double.NaN : (after / before - 1) * 100) : focus == null ? yieldGain(m, baseline, delta) : stat(delta, focus);
            if (!Double.isFinite(gain) || gain <= 1e-9) continue;
            Double cost = basis.profile().cost(o, prices);
            double have = basis.profile().available().getOrDefault(o.resource(), 0.0), need = basis.profile().remaining(o);
            Item display = new Item(); display.id = "profile:" + o.chain() + ":" + o.rank(); display.name = o.label(); display.group = o.group();
            display.modification = true;
            display.source = o.note() + String.format(Locale.ROOT, " Needs %,.0f %s, %,.0f available.", need, o.resource(), have);
            String blocked = o.priceId() != null && need > have && ("ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode))
                    ? "No bazaar purchases on " + p.mode : "";
            result.add(new Candidate(new Upgrade(display, null, delta, cost, gain, cost != null && cost == 0, blocked, 1, before, after), null, Map.of(), null, o, null));
        }
        return result;
    }
    private static Map<Stat, Double> plus(Map<Stat, Double> baseline, Map<Stat, Double> delta) {
        Map<Stat, Double> after = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) after.put(s, stat(baseline, s) + stat(delta, s));
        return after;
    }
    /** Flawless / hour with the ability cooldown scaled by cooldownFactor; null when the stats are invalid for the model. */
    private static Double rate(MiningMethod m, Map<Stat, Double> stats, RateBasis basis, double cooldownFactor) {
        var a = basis.ability();
        if (a != null && cooldownFactor != 1 && a.cooldown() != null) a = new MethodRates.Ability(a.name(), a.boostPercent(), a.duration(), a.cooldown() * cooldownFactor);
        try { return MethodRates.gems(m, stats, basis.efficiency(), a).flawlessPerHour(); }
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

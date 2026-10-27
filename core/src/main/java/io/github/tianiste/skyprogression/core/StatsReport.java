package io.github.tianiste.skyprogression.core;

import com.google.gson.JsonObject;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Wire format of the companion's {@code /stats} endpoint: the ledger for every supported stat in one
 * context, the gear it was computed for, and the tools the player can choose instead of the automatic one.
 * By default the gear is the best owned for the method and the tool is the best owned tool.
 */
public final class StatsReport {
    public String player = "", profile = "", profileId = "", fetchedAt = "", method = "";
    /** Tool key used (instance UUID or item ID); empty when no tool is owned or readable. */
    public String tool = "", zone = "", target = "";
    /** True when the tool was picked automatically; false when the player chose it. */
    public boolean toolAuto, bestGear = true, godPotion = true;
    public List<Tool> tools = new ArrayList<>();
    /** Armor, equipment, tool and pet of the evaluated loadout, with where each copy is stored. */
    public List<Tool> loadout = new ArrayList<>();
    public int accessories;
    /** HOTM tree used ("mining", "mining_2"..) and how many trees the profile has. */
    public String hotmTree = "mining";
    public int hotmTrees = 1;
    /** Pickaxe ability shown on the evaluated tool's lore, for the rate engine; null when none is shown. */
    public MethodRates.Ability ability;
    /** Counted stats of the evaluated pet (empty without a pet, null when not catalogued) and the pet alternatives. */
    public Map<Stat, Double> petStats;
    public List<PetUpgrades.Option> petOptions = new ArrayList<>();
    /** Essence perk, attribute and HOTM perk levels as upgrade options, with balances (see ProfileUpgrades). */
    public ProfileUpgrades.State profileUpgrades;
    public List<Ledger> stats = new ArrayList<>();
    /** Sale queries for the evaluated copies (instance key, or "pet"), and the quotes the companion found for them. */
    public Map<String, Resale.Query> resaleQueries = new LinkedHashMap<>();
    public Map<String, Price> resale = new HashMap<>();

    public record Tool(String key, String name, String location) {}
    public static final class Ledger {
        public Stat stat;
        public double flat, multiplier = 1, total;
        /** The total is a lower bound while sources that count have unknown values. */
        public boolean complete;
        /**
         * Best attainable total with this loadout (Breakdown.attainable); null when a counted source's maximum is unknown,
         * then attainableKnown is the total with those sources at their current value.
         */
        public Double attainable;
        public double attainableKnown;
        public int unknownMaxima;
        /** Totals with what is worn now and with the best owned gear (same tool and context). */
        public Double equipped, bestOwned;
        public List<Breakdown.Contribution> contributions = new ArrayList<>();
        public List<String> notes = new ArrayList<>();
    }

    public Ledger ledger(Stat stat) { return stats.stream().filter(l -> l.stat == stat).findFirst().orElse(null); }

    public static StatsReport of(JsonObject member, Loadout.Owned owned, MiningMethod method, String tool, boolean bestGear, StatContext context) {
        return of(member, owned, method, tool, bestGear, context, null);
    }
    /** perks: current mayor and minister perks (election resource), listed as temporary sources; null when unknown. */
    public static StatsReport of(JsonObject member, Loadout.Owned owned, MiningMethod method, String tool, boolean bestGear, StatContext context,
                                 List<StatBreakdown.MayorPerk> perks) {
        StatsReport r = new StatsReport();
        for (Item item : owned.tools()) r.tools.add(new Tool(key(item), item.name, item.location));
        r.tools.sort(Comparator.comparing(Tool::name).thenComparing(Tool::location));
        r.toolAuto = tool == null || tool.isBlank();
        String chosen = r.toolAuto ? Loadout.bestTool(owned, method) : tool;
        Loadout equipped = Loadout.equipped(owned, chosen), best = Loadout.bestFor(owned, method, chosen);
        Loadout main = bestGear ? best : equipped;
        r.hotmTree = StatBreakdown.chooseTree(member, main, context, method);
        StatBreakdown.chooseTree(member, bestGear ? equipped : best, context, method);
        r.hotmTrees = StatBreakdown.hotmTrees(member).size();
        r.method = method.name(); r.bestGear = bestGear;
        r.tool = main.tool == null ? "" : key(main.tool);
        r.zone = context.zone() == null ? "" : context.zone().name();
        r.target = context.target() == null ? "" : context.target().name();
        r.godPotion = context.godPotion();
        List<Item> pieces = new ArrayList<>(main.armor); pieces.addAll(main.equipment);
        if (main.tool != null) pieces.add(main.tool);
        for (Item item : pieces) r.loadout.add(new Tool(key(item), item.name, item.location));
        if (main.pet != null) r.loadout.add(new Tool("pet", main.pet.label(), main.pet.active() ? "active pet" : "pets menu"));
        r.accessories = main.accessories.size();
        List<Item> sellable = new ArrayList<>(pieces); sellable.addAll(main.accessories);
        for (Item item : sellable) if (!item.instance.isBlank()) r.resaleQueries.put(item.instance, Resale.of(item));
        if (main.pet != null) r.resaleQueries.put("pet", Resale.of(main.pet));
        r.ability = StatBreakdown.effectiveAbility(member, main, context);
        boolean drill = main.tool != null && main.tool.category.equals("DRILL");
        r.profileUpgrades = ProfileUpgrades.state(member, r.hotmTree, context, StatBreakdown.blueCheese(main.tool));
        if (main.petsKnown) {
            r.petStats = StatBreakdown.petStats(main.pet, context, drill);
            if (r.petStats != null) r.petOptions.addAll(PetUpgrades.options(owned, main.pet, context, drill));
        }
        for (Stat stat : StatBreakdown.SUPPORTED) {
            Breakdown b = StatBreakdown.compute(member, main, context, stat);
            StatBreakdown.mayor(b, perks);
            Ledger l = new Ledger();
            l.stat = stat; l.flat = b.flat(); l.multiplier = b.multiplier(); l.total = b.total(); l.complete = b.complete();
            l.attainable = b.attainable(); l.attainableKnown = b.attainableKnown(); l.unknownMaxima = b.unknownMaxima().size();
            l.contributions.addAll(b.contributions); l.notes.addAll(new LinkedHashSet<>(b.notes));
            l.equipped = bestGear ? StatBreakdown.compute(member, equipped, context, stat).total() : l.total;
            l.bestOwned = bestGear ? l.total : StatBreakdown.compute(member, best, context, stat).total();
            r.stats.add(l);
        }
        return r;
    }
    private static String key(Item item) { return item.instance.isBlank() ? item.id : item.instance; }
}

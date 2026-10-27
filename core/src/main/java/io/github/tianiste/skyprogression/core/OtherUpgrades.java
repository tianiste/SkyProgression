package io.github.tianiste.skyprogression.core;

import com.google.gson.JsonElement;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Other upgrades and progression, apart from the purchasable list: bonuses the profile API never shows (the player
 * has to skip what they already have), progress that has to be ground (HOTM, skill, collections, pet levels, Compact,
 * forge-only items), and the guide-checklist stage for orientation. Gains go through the same rate model as the
 * Upgrades list (drop multiplier when no rate can be computed); grinding is ranked by gain alone, never by coins.
 * Rules and sources: docs/CALCULATIONS.md "Other upgrades and progression".
 */
public final class OtherUpgrades {
    private OtherUpgrades() {}
    /**
     * gain: % in the method (null = not modelled); cost: coins to buy (bonuses) or for the materials (forge items),
     * null when unknown or not applicable. effect: what it gives; requirement: what has to be reached first;
     * distance: how far the profile is; how: where and how to get it; note: caveats.
     */
    public record Entry(String id, String name, String group, Map<Stat, Double> delta, Double gain, Double cost,
                        String effect, String requirement, String distance, String how, String note) {}
    /** name and the reasons for it; next: what the checklist lists after it, with "(you own one)" where the API shows it. */
    public record Stage(String name, List<String> because, String next, List<String> nextItems) {}
    /** rated: gains come from the rate model (false = drop multiplier); hidden: bonuses that change nothing here. */
    public record Report(List<Entry> unchecked, List<Entry> grinding, Stage stage, boolean rated, int hidden) {}

    /** A bonus the API never reports. units of priceId buy it; stats apply where the method's zone allows. */
    private record Bonus(String id, String name, String priceId, int units, Map<Stat, Double> stats, String effect, String how, String note) {}
    // Wiki item pages (fetched 2026-10-27): each Dwarven O's cereal gives +2 of its Fortune for the first 5 eaten.
    private static final List<Bonus> CEREALS = List.of(
            new Bonus("DWARVEN_OS_GEMSTONE_GRAHAMS", "Dwarven O's Gemstone Grahams x5", "DWARVEN_OS_GEMSTONE_GRAHAMS", 5, Map.of(Stat.GEMSTONE_FORTUNE, 10.0),
                    "+2 Gemstone Fortune per bowl, first 5 bowls: +10", "Bazaar, or Crystal Nucleus runs (0.47% per run).", ""),
            new Bonus("DWARVEN_OS_ORE_OATS", "Dwarven O's Ore Oats x5", "DWARVEN_OS_ORE_OATS", 5, Map.of(Stat.ORE_FORTUNE, 10.0),
                    "+2 Ore Fortune per bowl, first 5 bowls: +10", "Bazaar, or Mining Events in the Dwarven Mines.", ""),
            new Bonus("DWARVEN_OS_BLOCK_BRAN", "Dwarven O's Block Bran x5", "DWARVEN_OS_BLOCK_BRAN", 5, Map.of(Stat.BLOCK_FORTUNE, 10.0),
                    "+2 Block Fortune per bowl, first 5 bowls: +10", "Bazaar, or Scatha kills (10%).", ""),
            new Bonus("DWARVEN_OS_METALLIC_MINIS", "Dwarven O's Metallic Minis x5", "DWARVEN_OS_METALLIC_MINIS", 5, Map.of(Stat.DWARVEN_METAL_FORTUNE, 10.0),
                    "+2 Dwarven Metal Fortune per bowl, first 5 bowls: +10", "Bazaar, or Umber/Tungsten (0.31%) and Vanguard (3.41%) corpses.", ""));
    /** Common attribute shards for level 10 (StatBreakdown.ATTRIBUTE_SHARDS), Epic for level 10. */
    private static final int COMMON_MAX = 96, EPIC_MAX = 32;

    public static Set<String> priceIds() {
        Set<String> ids = new LinkedHashSet<>();
        CEREALS.forEach(c -> ids.add(c.priceId()));
        ids.addAll(List.of("SHARD_HIDEONSUN", "SHARD_NILBOG", "ESSENCE_FOSSIL", "ENCHANTMENT_COMPACT_1"));
        return ids;
    }

    /** Bonuses for the method's zone: cereals, Gold Box, Royal Riches (Mines of Divan only), Dwarven Expertise (Mining Islands). */
    private static List<Bonus> bonuses(MiningMethod m) {
        List<Bonus> out = new ArrayList<>(CEREALS);
        String attribute = " Attributes are in the API (attributes.stacks), but this one's key has never been observed, so a missing key is not proof you lack it.";
        // Wiki Hideonsun Shard: Gold Box (Common) +5 to +50 Ore Fortune.
        out.add(new Bonus("SHARD_HIDEONSUN", "Gold Box X (Hideonsun Shard)", "SHARD_HIDEONSUN", COMMON_MAX, Map.of(Stat.ORE_FORTUNE, 50.0),
                "+5 Ore Fortune per level, level 10: +50", "Syphon 96 Hideonsun Shards (Bazaar, or hunting Hideonsun on Galatea).", attribute.strip()));
        // Wiki Nilbog Shard: Royal Riches (Epic) +5 to +50 Mining Speed and +2.5 to +25 Ore Fortune in the Mines of Divan.
        if (m.defaultZone() == StatContext.Zone.CRYSTAL_HOLLOWS)
            out.add(new Bonus("SHARD_NILBOG", "Royal Riches X (Nilbog Shard)", "SHARD_NILBOG", EPIC_MAX, Map.of(Stat.MINING_SPEED, 50.0, Stat.ORE_FORTUNE, 25.0),
                    "+5 Mining Speed and +2.5 Ore Fortune per level in the Mines of Divan, level 10: +50 / +25",
                    "Syphon 32 Nilbog Shards (Bazaar); needs Hunting 15.",
                    "Counts only in the Mines of Divan; the gain assumes you mine there." + attribute));
        // Wiki Essence Shops/Fossil: Dwarven Expertise +2 Dwarven Metal Fortune per level on Mining Islands, 5,500 essence in total.
        if (m.defaultZone() != StatContext.Zone.OTHER)
            out.add(new Bonus("ESSENCE_FOSSIL", "Dwarven Expertise 10 (Fossil Essence shop)", "ESSENCE_FOSSIL", 5_500, Map.of(Stat.DWARVEN_METAL_FORTUNE, 20.0),
                    "+2 Dwarven Metal Fortune per level on Mining Islands, level 10: +20", "Fossil Essence shop, 5,500 Fossil Essence from level 0.",
                    "The perk's API key has not been identified, so your level is not read; the price assumes level 0 and no essence held."));
        return out;
    }

    public static Report of(ProgressionEngine engine, Profile p, MiningMethod m, StatsReport stats, Map<Stat, Double> baseline, double efficiency,
                            Map<String, Price> prices, ProgressionEngine.RateBasis basis) {
        MethodRates.Ability ability = basis == null ? null : basis.ability();
        boolean rated = MethodRates.supported(m) && MethodComparison.gain(m, baseline, efficiency, ability, Map.of(), 1) != null;
        java.util.function.Function<Map<Stat, Double>, Double> gain = delta -> {
            if (!rated) return ProgressionEngine.yieldGain(m, baseline, delta);
            Double g = MethodComparison.gain(m, baseline, efficiency, ability, delta, 1);
            return g == null ? null : g * 100;
        };
        boolean noMarket = "ironman".equalsIgnoreCase(p.mode) || "bingo".equalsIgnoreCase(p.mode);
        List<Entry> unchecked = new ArrayList<>();
        int hidden = 0;
        for (Bonus b : bonuses(m)) {
            Double g = gain.apply(b.stats());
            if (g == null || g <= 1e-9) { hidden++; continue; }
            Double each = Recipes.quote(b.priceId(), prices);
            unchecked.add(new Entry(b.id(), b.name(), "Bonus", b.stats(), g, each == null ? null : each * b.units(), b.effect(), "",
                    "Can't check: skip if you already have it", b.how(), (b.note() + (noMarket ? " No Bazaar on " + p.mode + "." : "")).strip()));
        }
        unchecked.sort(Comparator.comparing((Entry e) -> -e.gain()));

        List<Entry> grinding = new ArrayList<>();
        if (basis != null) profile(p, basis.profile(), gain, grinding);
        if (stats != null && stats.petLevel != null) pet(stats.petLevel, stats.petStats, gain, grinding);
        if (stats != null) compact(p, stats.tool, m, grinding);
        if (p.inventoryEnabled) forged(engine, p, m, prices, gain, grinding);
        grinding.sort(Comparator.comparing((Entry e) -> e.gain() == null).thenComparing(e -> e.gain() == null ? 0 : -e.gain()));
        return new Report(unchecked, grinding, stage(p), rated, hidden);
    }

    /** HOTM perk levels and unlocks, Mining skill and collections that powder, tokens, XP or items must pay for: each chain's nearest such level. */
    private static void profile(Profile p, ProfileUpgrades.State state, java.util.function.Function<Map<Stat, Double>, Double> gain, List<Entry> out) {
        Map<String, Entry> nearest = new LinkedHashMap<>();
        Map<String, Integer> rank = new HashMap<>();
        for (ProfileUpgrades.Option o : state.options()) {
            boolean tier = p.hotm < o.tier();
            if (!o.grinding() || state.cost(o, Map.of()) != null && !tier) continue;
            if (rank.containsKey(o.chain()) && rank.get(o.chain()) <= o.rank()) continue;
            Map<Stat, Double> now = state.current(o.chain()), then = state.stats(o), delta = new EnumMap<>(Stat.class);
            for (Stat s : Stat.values()) { double d = then.getOrDefault(s, 0.0) - now.getOrDefault(s, 0.0); if (d != 0) delta.put(s, d); }
            Double g = gain.apply(delta);
            if (g == null || g <= 1e-9) continue;
            double have = state.available().getOrDefault(o.resource(), 0.0), need = Math.max(0, state.remaining(o) - have);
            int tokens = state.tokens(o);
            double unspent = state.available().getOrDefault("TOKEN", 0.0);
            StringJoiner distance = new StringJoiner("; ");
            if (o.resource().startsWith("POWDER_") && need > 0)
                distance.add(String.format(Locale.ROOT, "%,.0f more %s Powder (%,.0f unspent)", need, title(o.resource().substring(7)), have));
            else if (!o.resource().startsWith("POWDER_") && need > 0) distance.add(o.note().replaceFirst("^Grinding: ", "").replaceFirst("\\.$", ""));
            if (tokens > unspent) distance.add(String.format(Locale.ROOT, "%,.0f more Token%s of the Mountain (%,.0f unspent)", tokens - unspent, tokens - unspent > 1 ? "s" : "", unspent));
            if (tier) distance.add("HOTM " + p.hotm + " of " + o.tier());
            String how = switch (o.group()) {
                case "HOTM" -> "Mine for powder and HOTM XP; tokens come from HOTM tiers and Core of the Mountain.";
                case "Skill" -> "Mining XP from mining blocks.";
                case "Collection" -> "Mine or collect the item; the collection counts items picked up.";
                default -> "";
            };
            rank.put(o.chain(), o.rank());
            nearest.put(o.chain(), new Entry("profile:" + o.chain() + ":" + o.rank(), o.label(), o.group(), delta, g, null, statText(delta),
                    tier ? "HOTM " + o.tier() : "", distance.length() == 0 ? "Ready" : distance.toString(), how, ""));
        }
        out.addAll(nearest.values());
    }

    /** Levelling the evaluated pet (and its item) to 100; XP from the pet level table. */
    private static void pet(StatsReport.PetLevel pet, Map<Stat, Double> now, java.util.function.Function<Map<Stat, Double>, Double> gain, List<Entry> out) {
        Map<Stat, Double> delta = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) {
            double d = pet.statsAtMax().getOrDefault(s, 0.0) - (now == null ? 0 : now.getOrDefault(s, 0.0));
            if (d != 0) delta.put(s, d);
        }
        Double g = gain.apply(delta);
        if (g == null || g <= 1e-9) return;
        double need = Math.max(0, pet.expAtMax() - pet.exp());
        out.add(new Entry("pet-level", pet.label().replaceFirst(", level \\d+\\)", ")") + " " + pet.level() + " -> 100", "Pet", delta, g, null, statText(delta), "",
                String.format(Locale.ROOT, "%,.0f more pet XP (%,.0f of %,.0f)", need, pet.exp(), pet.expAtMax()),
                "Keep the pet active while you gain Mining XP.",
                "The pet level table is community data (NEU), not yet cross-checked against an official table."));
    }

    // Wiki Compact: tier I..X, +1 Mining Wisdom per tier, enchanted item chance and blocks mined needed per tier.
    private static final double[] COMPACT_CHANCE = {0.25, 0.27, 0.29, 0.31, 0.33, 0.36, 0.40, 0.44, 0.50, 0.57};
    private static final long[] COMPACT_BLOCKS = {0, 100, 500, 1_500, 5_000, 15_000, 50_000, 150_000, 500_000, 1_000_000};
    /** Compact on the evaluated tool: blocks mined from `compact_blocks` and the enchantment level. */
    private static void compact(Profile p, String toolKey, MiningMethod m, List<Entry> out) {
        if (toolKey == null || toolKey.isBlank()) return;
        Item tool = p.gear.stream().filter(i -> toolKey.equals(i.instance) || i.instance.isBlank() && toolKey.equals(i.id)).findFirst().orElse(null);
        if (tool == null) return;
        int level = tool.enchantments.getOrDefault("compact", 0);
        if (level >= 10) return;
        JsonElement mined = ProfileParser.at(tool.extra, "compact_blocks");
        double blocks = mined.isJsonNull() ? 0 : ProfileParser.number(mined, 0);
        String effect = level == 0
                ? String.format(Locale.ROOT, "Compact I: +1 Mining Wisdom, %.2f%% enchanted item chance; up to X: +10, %.2f%%", COMPACT_CHANCE[0], COMPACT_CHANCE[9])
                : String.format(Locale.ROOT, "Compact %d -> X: +%d Mining Wisdom, enchanted item chance %.2f%% -> %.2f%%", level, 10 - level, COMPACT_CHANCE[level - 1], COMPACT_CHANCE[9]);
        String distance = level == 0 ? "Not on " + tool.name
                : String.format(Locale.ROOT, "%,.0f more blocks for Compact %d, %,.0f for X (%,.0f mined)", Math.max(0, COMPACT_BLOCKS[level] - blocks), level + 1,
                        Math.max(0, COMPACT_BLOCKS[9] - blocks), blocks);
        String note = m.gems() ? "The enchanted item chance does not work on Gemstones (wiki), so only Wisdom changes here."
                : "Rate gain not modelled: how the enchanted item roll combines with Fortune and Spread is not stated on the wiki. Wisdom does not change rates.";
        out.add(new Entry("compact", "Compact on " + tool.name, "Tool", Map.of(Stat.MINING_WISDOM, (double)(10 - level)), m.gems() ? 0.0 : null, null, effect,
                level == 0 ? "Compact I on the tool" : "", distance,
                level == 0 ? "Compact I from Elizabeth (4,000 Bits) or the Bazaar; it levels up as the tool mines (gemstones count)." : "Levels up as the tool mines blocks (gemstones count).",
                note));
    }

    /**
     * Items that only a forge makes (no buy quote, a forge recipe) and beat the owned item of their slot. A Power accessory
     * is valued with Perfect Jade, Amber and Topaz in its slots at its rarity (accessory gems give half stats).
     */
    private static void forged(ProgressionEngine engine, Profile p, MiningMethod m, Map<String, Price> prices,
                               java.util.function.Function<Map<Stat, Double>, Double> gain, List<Entry> out) {
        Map<String, Item> owned = engine.bestOwned(p, m, null);
        for (Item i : Catalog.load()) {
            Recipes.Recipe r = Recipes.recipe(i.id);
            if (r == null || !r.type().equals("forge") || Recipes.quote(i.id, prices) != null || !i.applies(m) || p.owned.getOrDefault(i.id, 0) > 0) continue;
            Map<Stat, Double> stats = new EnumMap<>(Stat.class); stats.putAll(i.stats);
            List<String> gems = new ArrayList<>();
            if (i.group.equals("Power accessory") && i.slots != null) for (Slot s : i.slots) {
                if (!List.of("JADE", "AMBER", "TOPAZ").contains(s.slot_type)) continue;
                Stat stat = switch (s.slot_type) { case "JADE" -> Stat.MINING_FORTUNE; case "AMBER" -> Stat.MINING_SPEED; default -> Stat.PRISTINE; };
                double v = GearUpgrades.gemValue(s.slot_type, "PERFECT", i.tier, true);
                if (Double.isFinite(v) && v > 0) { stats.merge(stat, v, Double::sum); gems.add("PERFECT_" + s.slot_type + "_GEM"); }
            }
            Item current = owned.get(i.group);
            Map<Stat, Double> delta = new EnumMap<>(Stat.class);
            for (Stat s : Stat.values()) {
                double d = stats.getOrDefault(s, 0.0) - (current == null ? 0 : current.stats.getOrDefault(s, 0.0));
                if (d != 0) delta.put(s, d);
            }
            Double g = gain.apply(delta);
            if (g == null || g <= 1e-9) continue;
            StringJoiner missing = new StringJoiner(", ");
            Double materials = 0.0;
            for (var in : r.inputs().entrySet()) {
                int need = (int)Math.ceil(in.getValue()), have = Math.min(need, Math.max(0, p.owned.getOrDefault(in.getKey(), 0)));
                if (have >= need) continue;
                missing.add((need - have) + " " + in.getKey());
                Double q = Recipes.quote(in.getKey(), prices);
                materials = materials == null || q == null ? null : materials + q * (need - have);
            }
            for (String gem : gems) {
                Double q = Recipes.quote(gem, prices);
                materials = materials == null || q == null ? null : materials + q;
            }
            StringJoiner inputs = new StringJoiner(", ");
            r.inputs().forEach((id, n) -> inputs.add((int)Math.ceil(n) + " " + id + (p.owned.getOrDefault(id, 0) > 0 ? " (owned)" : "")));
            String requirement = r.requires().isEmpty() ? "" : r.requires().replace("HotM", "HOTM");
            String distance = (p.hotm < r.hotm() ? "HOTM " + p.hotm + " of " + r.hotm() + "; " : "") + (missing.length() == 0 ? "All forge inputs owned" : "Missing " + missing);
            out.add(new Entry("forge:" + i.id, i.name, i.group, delta, g, materials, statText(delta) + (gems.isEmpty() ? "" : " with Perfect " + String.join(", ",
                    gems.stream().map(x -> title(x.substring(8, x.length() - 4))).toList()) + " in its slots"), requirement, distance,
                    "Forge from " + inputs + " (" + Recipes.duration(r.duration()) + "); not sold on the Auction House or Bazaar.",
                    current == null ? "" : "Replaces " + current.name + "." + (gems.isEmpty() ? "" : " Gems in the old accessory can be moved over instead of bought.")));
        }
    }

    /** One checklist step for orientation: text and, where the item has a known ID, that ID (owned when the API shows it). */
    private record Goal(String text, String id) {}
    private static final List<Goal> EARLY = List.of(new Goal("Clean Divan armor", "DIVAN_HELMET"), new Goal("DR-X555", "TITANIUM_DRILL_3"), new Goal("HOTM 7", null),
            new Goal("Mithril equipment", "MITHRIL_NECKLACE"), new Goal("Mineral Talisman", "MINERAL_TALISMAN"), new Goal("Mithril Lantern", "MITHRIL_LANTERN"),
            new Goal("Dwarven O's cereals x5 each", null), new Goal("Titanium equipment", "TITANIUM_NECKLACE"), new Goal("Sunny Side Goblin Omelette", "GOBLIN_OMELETTE_SUNNY_SIDE"),
            new Goal("Forge the DR-X555 into the DR-X655", "TITANIUM_DRILL_4"));
    private static final List<Goal> MID = List.of(new Goal("Max Rhinestone Infusion", null), new Goal("Jungle Amulet", "JUNGLE_AMULET"),
            new Goal("Artifact of Power with Perfect Topaz", "POWER_ARTIFACT"), new Goal("Deep Technique (32 Bal shards)", null), new Goal("Glacite Lantern", "GLACITE_LANTERN"),
            new Goal("Titanium Relic", "TITANIUM_RELIC"), new Goal("Ruby, Sapphire, then Amber drill engines", "AMBER_POLISHED_DRILL_ENGINE"),
            new Goal("Gemstone, then Perfectly-Cut fuel tank", "PERFECTLY_CUT_FUEL_TANK"), new Goal("Dwarven Handwarmers", "DWARVEN_HANDWARMERS"),
            new Goal("Quick Claw", "PET_ITEM_QUICK_CLAW"), new Goal("Dwarven Metal Talisman", "DWARVEN_METAL"), new Goal("Collections and Mining 60", null),
            new Goal("Will-o'-wisp", "WILL_O_WISP"), new Goal("Pendant of Divan", "DIVAN_PENDANT"));
    private static final List<Goal> END = List.of(new Goal("Freezing Spread (32 Littlefoot shards)", null), new Goal("Divan's Drill", "DIVAN_DRILL"),
            new Goal("Divan's Powder Coating on armor and Pendant", "DIVAN_POWDER_COATING"), new Goal("Relic of Power", "POWER_RELIC"),
            new Goal("Glossy Mineral Talisman", "GLOSSY_MINERAL_TALISMAN"), new Goal("Recombobulated Dwarven Handwarmers", null), new Goal("Speed Relic", "SPEED_RELIC"),
            new Goal("Ultimate DNA (24 Galaxy Fish shards)", null));
    /**
     * Stage on the supplied guide checklist (docs/references/PROGRESSION-CHECKLIST.md), from its stage headings only: Beginning =
     * clean Divan armor, DR-X555, HOTM 7; Middle = Divan armor, DR-X655, HOTM 7-10; End = DR-X655 or Divan's Drill, Pendant
     * of Divan, HOTM 10. Owned means anywhere the API shows (storage included). Orientation only, not a fixed order.
     */
    static Stage stage(Profile p) {
        java.util.function.Predicate<String> has = id -> p.owned.getOrDefault(id, 0) > 0 || p.gear.stream().anyMatch(i -> i.id.equals(id));
        int divan = (int)List.of("DIVAN_HELMET", "DIVAN_CHESTPLATE", "DIVAN_LEGGINGS", "DIVAN_BOOTS").stream().filter(has).count();
        boolean x655 = has.test("TITANIUM_DRILL_4"), divanDrill = has.test("DIVAN_DRILL"), x555 = has.test("TITANIUM_DRILL_3") || x655 || divanDrill;
        boolean pendant = has.test("DIVAN_PENDANT");
        List<String> because = new ArrayList<>();
        because.add("HOTM " + p.hotm);
        because.add(divan == 4 ? "Divan armor" : divan + " of 4 Divan pieces");
        because.add(divanDrill ? "Divan's Drill" : x655 ? "DR-X655" : x555 ? "DR-X555" : "no DR-X555 or better");
        if (pendant) because.add("Pendant of Divan");
        String name, next; List<Goal> goals;
        if (p.hotm >= 10 && pendant && (x655 || divanDrill)) { name = "End game"; next = "The checklist ends here; the end-game steps you may still miss:"; goals = END; }
        else if ((x655 || divanDrill) && divan == 4) { name = "Mid game"; next = "End-game items usually come next:"; goals = END; }
        else if (divan == 4 || x555 || p.hotm >= 7) { name = "Early game"; next = "Mid-game items usually come next:"; goals = MID; }
        else { name = "Before the checklist"; next = "The checklist starts at clean Divan armor, a DR-X555 and HOTM 7; early steps:"; goals = EARLY; }
        if (!p.inventoryEnabled) because.add("Inventory API off: gear unknown");
        List<String> items = new ArrayList<>();
        for (Goal g : goals) items.add(g.text() + (g.id() != null && has.test(g.id()) ? " (you own one)" : ""));
        return new Stage(name, because, next, items);
    }

    private static String statText(Map<Stat, Double> delta) {
        StringJoiner out = new StringJoiner(", ");
        delta.forEach((s, v) -> out.add((v > 0 ? "+" : "") + fmt(v) + (s.percent ? "% " : " ") + s.label));
        return out.toString();
    }
    private static String fmt(double v) { return Math.rint(v) == v ? String.format(Locale.ROOT, "%,.0f", v) : String.format(Locale.ROOT, "%,.2f", v).replaceAll("0+$", ""); }
    private static String title(String s) { return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT); }
}

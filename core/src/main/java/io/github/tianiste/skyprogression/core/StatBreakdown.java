package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;
import io.github.tianiste.skyprogression.core.Breakdown.*;
import io.github.tianiste.skyprogression.core.StatContext.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * Builds the per-source ledger of a mining stat for a loadout. Rules and their sources are listed
 * in docs/CALCULATIONS.md ("Stat breakdown"). Gear contributes its displayed lore total once; drill
 * parts and Polarvoid, which the lore lists outside that total, are separate contributions.
 */
public final class StatBreakdown {
    private StatBreakdown() {}
    public static final Set<Stat> SUPPORTED = EnumSet.of(Stat.MINING_FORTUNE, Stat.MINING_SPEED, Stat.GEMSTONE_FORTUNE,
            Stat.ORE_FORTUNE, Stat.DWARVEN_METAL_FORTUNE, Stat.BLOCK_FORTUNE, Stat.MINING_SPREAD, Stat.GEMSTONE_SPREAD, Stat.PRISTINE);

    /** HOTM perk effect: base + perLevel x level (wiki Heart of the Mountain perk templates). */
    record Perk(String id, String name, Stat stat, double base, double perLevel, int maxLevel, Condition condition) {
        double at(int level) { return level <= 0 ? 0 : base + perLevel * level; }
    }
    static final List<Perk> PERKS = List.of(
            new Perk("mining_speed", "Mining Speed", Stat.MINING_SPEED, 0, 20, 50, Condition.NONE),
            new Perk("speedy_mineman", "Speedy Mineman", Stat.MINING_SPEED, 0, 40, 50, Condition.NONE),
            new Perk("professional", "Professional", Stat.MINING_SPEED, 50, 5, 140, Condition.target(Target.GEMSTONE, "While mining Gemstones")),
            new Perk("strong_arm", "Strong Arm", Stat.MINING_SPEED, 0, 5, 100, Condition.target(Target.DWARVEN_METAL, "While mining Dwarven Metals")),
            new Perk("mining_fortune", "Mining Fortune", Stat.MINING_FORTUNE, 0, 2, 50, Condition.NONE),
            new Perk("fortunate_mineman", "Fortunate Mineman", Stat.MINING_FORTUNE, 0, 3, 50, Condition.NONE),
            new Perk("rags_to_riches", "Rags to Riches", Stat.MINING_FORTUNE, 0, 4, 50, Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In Glacite Mineshafts")),
            new Perk("gem_lover", "Gem Lover", Stat.GEMSTONE_FORTUNE, 20, 4, 20, Condition.NONE),
            new Perk("old_school", "Old-School", Stat.ORE_FORTUNE, 0, 5, 20, Condition.NONE),
            new Perk("blockhead", "Blockhead", Stat.BLOCK_FORTUNE, 0, 5, 20, Condition.NONE),
            new Perk("metal_head", "Metal Head", Stat.DWARVEN_METAL_FORTUNE, 0, 5, 20, Condition.NONE),
            new Perk("efficient_miner", "Efficient Miner", Stat.MINING_SPREAD, 0, 3, 100, Condition.NONE),
            new Perk("mole", "Mole", Stat.MINING_SPREAD, 50 - 350.0 / 199, 350.0 / 199, 200, Condition.target(Target.HARD_STONE, "While mining Hard Stone")),
            new Perk("mining_master", "Mining Master", Stat.PRISTINE, 0, .1, 10, Condition.NONE));
    /**
     * Front Loaded (HOTM 4): +150 Gemstone Fortune and +250 Mining Speed for the first 2,500 Gemstones each
     * day. The node key is assumed to be `front_loaded`; it has not been observed in an API response.
     */
    static final Map<Stat, Double> FRONT_LOADED = Map.of(Stat.GEMSTONE_FORTUNE, 150.0, Stat.MINING_SPEED, 250.0);
    /** Sky Mall grants one random daily buff on Mining Islands; the day's buff is not in the API. */
    static final Map<Stat, Double> SKY_MALL = Map.of(Stat.MINING_FORTUNE, 50.0, Stat.MINING_SPEED, 100.0);
    /** `mining_core.current_daily_effect` codes; dig_speed matched the in-game Sky Mall +100 on 2026-10-26. */
    static final Map<Stat, String> SKY_MALL_CODES = Map.of(Stat.MINING_SPEED, "dig_speed");

    /** Attribute: rarity (shard cost table) and linear effect per level up to 10 (wiki shard pages). */
    record Attribute(String id, String name, String rarity, Stat stat, double perLevel, Condition condition) {}
    static final List<Attribute> ATTRIBUTES = List.of(
            new Attribute("fortunate_miner", "Fortunate Miner (Flitter Shard)", "COMMON", Stat.MINING_FORTUNE, 2.5, Condition.NONE),
            new Attribute("rotten_pickaxe", "Rotten Pickaxe (Miner Zombie Shard)", "COMMON", Stat.MINING_SPEED, 3, Condition.NONE),
            new Attribute("quartz_speed", "Quartz Speed (Quartzfang Shard)", "RARE", Stat.MINING_SPEED, 10, Condition.unchecked("While any Weather is active on Mining Islands")),
            new Attribute("mossy_box", "Mossy Box (Hideonleaf Shard)", "COMMON", Stat.BLOCK_FORTUNE, 5, Condition.NONE),
            new Attribute("devouring_spread", "Devouring Spread (Gemzie Shard)", "EPIC", Stat.GEMSTONE_SPREAD, .25, Condition.NONE),
            // Expected value of a per-block roll: level% chance of +1 Pristine on that block; not in the stat menu.
            new Attribute("deep_technique", "Deep Technique (Bal Shard)", "EPIC", Stat.PRISTINE, .01,
                    Condition.unchecked("Per-block chance of +1 Pristine (expected value shown); not part of the stat total")));

    /** Essence shop perks (`player_data.perks`): linear per level (wiki Essence Shops/Gold and /Diamond). */
    record Essence(String id, String name, Stat stat, double perLevel, int maxLevel) {}
    static final List<Essence> ESSENCE = List.of(
            new Essence("eager_miner", "Eager Miner (Gold Essence)", Stat.MINING_SPEED, 10, 10),
            new Essence("rhinestone_infusion", "Rhinestone Infusion (Diamond Essence)", Stat.GEMSTONE_FORTUNE, 2, 10));
    /** Cumulative shards for levels 1..10 per rarity (wiki Attributes leveling table). */
    static final Map<String, int[]> ATTRIBUTE_SHARDS = Map.of(
            "COMMON", new int[]{1, 4, 9, 15, 22, 30, 40, 54, 72, 96},
            "UNCOMMON", new int[]{1, 3, 6, 10, 15, 21, 28, 36, 48, 64},
            "RARE", new int[]{1, 3, 6, 9, 13, 17, 22, 28, 36, 48},
            "EPIC", new int[]{1, 2, 4, 6, 9, 12, 16, 20, 25, 32},
            "LEGENDARY", new int[]{1, 2, 3, 5, 7, 9, 12, 15, 19, 24});

    /**
     * Pet stats and perks per level for rarities minimumTier..maximumTier, only where the wiki's level 1 and
     * level 100 values agree with a linear rate. {@code drill}: applies only with a Drill (Scatha Drill Infusion).
     */
    record PetStat(String type, String minimumTier, String maximumTier, Stat stat, double perLevel, String perk, boolean drill, Condition condition) {
        PetStat(String type, String minimumTier, Stat stat, double perLevel) { this(type, minimumTier, "MYTHIC", stat, perLevel, "", false, Condition.NONE); }
        boolean fits(String tier) { return TIERS.indexOf(tier) >= TIERS.indexOf(minimumTier) && TIERS.indexOf(tier) <= TIERS.indexOf(maximumTier); }
    }
    static final List<PetStat> PET_STATS = List.of(
            new PetStat("BAL", "EPIC", Stat.MINING_FORTUNE, 1),
            new PetStat("BAL", "EPIC", "EPIC", Stat.PRISTINE, .02, "Furnace", false, Condition.zones(EnumSet.of(Zone.MAGMA_FIELDS), "In the Magma Fields")),
            new PetStat("BAL", "LEGENDARY", "MYTHIC", Stat.PRISTINE, .03, "Furnace", false, Condition.zones(EnumSet.of(Zone.MAGMA_FIELDS), "In the Magma Fields")),
            new PetStat("SCATHA", "RARE", Stat.MINING_FORTUNE, 1.25),
            new PetStat("SCATHA", "RARE", Stat.MINING_SPEED, 1),
            new PetStat("SCATHA", "EPIC", "EPIC", Stat.GEMSTONE_FORTUNE, 1, "Drill Infusion", true, Condition.NONE),
            new PetStat("SCATHA", "LEGENDARY", "MYTHIC", Stat.GEMSTONE_FORTUNE, 1.25, "Drill Infusion", true, Condition.NONE),
            new PetStat("ARMADILLO", "LEGENDARY", "MYTHIC", Stat.MINING_SPREAD, 3, "Long Claws", false, Condition.target(Target.HARD_STONE, "While mining Hard Stone")),
            new PetStat("MITHRIL_GOLEM", "COMMON", Stat.MINING_FORTUNE, .25),
            new PetStat("SILVERFISH", "COMMON", Stat.MINING_FORTUNE, .2),
            // NEU petnums (level 1 and 100 linear): Bee's Busy Buzz Buzz adds its value again to the pet's Mining Fortune.
            new PetStat("BEE", "RARE", "RARE", Stat.MINING_FORTUNE, .2, "", false, Condition.NONE),
            new PetStat("BEE", "EPIC", "LEGENDARY", Stat.MINING_FORTUNE, .3, "", false, Condition.NONE),
            new PetStat("BEE", "MYTHIC", "MYTHIC", Stat.MINING_FORTUNE, .4, "", false, Condition.NONE),
            new PetStat("BEE", "RARE", "RARE", Stat.MINING_FORTUNE, .2, "Busy Buzz Buzz", false, Condition.NONE),
            new PetStat("BEE", "EPIC", "LEGENDARY", Stat.MINING_FORTUNE, .3, "Busy Buzz Buzz", false, Condition.NONE),
            new PetStat("BEE", "MYTHIC", "MYTHIC", Stat.MINING_FORTUNE, .4, "Busy Buzz Buzz", false, Condition.NONE),
            new PetStat("GLACITE_GOLEM", "COMMON", "LEGENDARY", Stat.MINING_SPEED, 1.25, "", false, Condition.NONE),
            new PetStat("GLACITE_GOLEM", "RARE", "RARE", Stat.MINING_FORTUNE, .75, "Iceborn", false, Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In the Glacite Mineshafts")),
            new PetStat("GLACITE_GOLEM", "EPIC", "LEGENDARY", Stat.MINING_FORTUNE, 1, "Iceborn", false, Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In the Glacite Mineshafts")),
            new PetStat("MOLE", "LEGENDARY", "LEGENDARY", Stat.MINING_SPEED, 1.5, "", false, Condition.NONE));
    private static final List<String> TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

    private static final Pattern BRACKET = Pattern.compile("^(.+?):\\s*[+-]?[0-9,.]+\\s*\\[\\+([0-9,.]+)\\]");
    private static final Pattern STAT_LINE = Pattern.compile("^[A-Z][A-Za-z' ]+:\\s*[+-]?[0-9]");
    private static final Pattern SET_BONUS = Pattern.compile("Full Set Bonus: (.+?) \\((\\d+)/(\\d+)\\)");
    /** Bonus that grows with each piece worn; counted once per set, per piece worn in the loadout. */
    private static final Pattern TIERED_BONUS = Pattern.compile("Tiered Bonus: (.+?) \\((\\d+)/(\\d+)\\)");
    /** Per-piece values (wiki Mineral and Glossy Mineral Armor: +100/+150 Mining Spread per piece on ores and blocks). */
    private static final Map<String, Double> TIERED_PER_PIECE = Map.of("Mineralworks", 100.0, "Glossy Mineralworks", 150.0);

    public static Breakdown compute(JsonObject member, Loadout loadout, StatContext context, Stat stat) {
        if (!SUPPORTED.contains(stat)) throw new IllegalArgumentException(stat.label + " breakdown is not implemented yet");
        Breakdown b = new Breakdown(stat, loadout.kind, context);
        b.notes.addAll(loadout.notes);
        gear(b, loadout, miningLevel(member));
        pet(b, loadout);
        hotm(b, member, loadout.hotmTree, blueCheese(loadout.tool));
        if (stat == Stat.MINING_FORTUNE) skill(b, member);
        collections(b, member);
        attributes(b, member);
        essence(b, member);
        temporary(b, member);
        assumedBuffs(b, member);
        untrackable(b);
        return b;
    }

    // ---- gear ----------------------------------------------------------------------------------

    private static void gear(Breakdown b, Loadout l, Integer miningLevel) {
        if (!l.inventoryKnown) {
            b.contributions.add(new Contribution("Gear", "Armor, equipment, tool and accessories", b.stat, null, null, "",
                    State.COUNTED, Confidence.UNKNOWN, "Inventory API data is missing or unreadable; enable the Inventory API."));
            return;
        }
        if (l.tool == null) b.contributions.add(new Contribution("Tool", "Mining tool", b.stat, null, null, "",
                State.COUNTED, Confidence.MANUAL, "The API does not expose the held item; select the tool you mine with."));
        Set<String> setBonuses = new HashSet<>();
        for (Item item : l.armor) itemContributions(b, "Armor", item, l, setBonuses, miningLevel);
        for (Item item : l.equipment) itemContributions(b, "Equipment", item, l, setBonuses, miningLevel);
        if (l.tool != null) itemContributions(b, "Tool", l.tool, l, setBonuses, miningLevel);
        for (Item item : l.accessories) itemContributions(b, "Accessory", item, l, setBonuses, miningLevel);
        if (l.accessories.stream().anyMatch(i -> i.id.equals("HASTE_ARTIFACT")) && b.stat == Stat.MINING_SPEED)
            b.contributions.add(new Contribution("Accessory", "Haste III (Haste Artifact effect)", b.stat, 150.0, 150.0,
                    "Only when no stronger Haste effect is active", State.SITUATIONAL, Confidence.KNOWN,
                    "Potion-style effect on Mining System islands; not part of the item's stat line."));
    }

    /** Value the item contributes unconditionally (stat line plus drill parts), for choosing a best-owned copy. */
    static double itemValue(Item item, Stat stat) {
        Breakdown scratch = new Breakdown(stat, Loadout.Kind.BEST_OWNED, StatContext.UNSPECIFIED);
        itemContributions(scratch, "", item, null, new HashSet<>(), null);
        return scratch.total();
    }

    private static void itemContributions(Breakdown b, String category, Item item, Loadout l, Set<String> setBonuses, Integer miningLevel) {
        Double line = item.stats.get(b.stat);
        if (line != null) b.contributions.add(new Contribution(category, item.name, b.stat, line, null, "",
                State.COUNTED, Confidence.KNOWN, "Displayed stat line (includes base, enchantments, reforge and gemstones)."));
        bracket(b, category, item);
        boolean drill = item.extra.keySet().stream().anyMatch(k -> k.startsWith("drill_part_"));
        for (List<String> paragraph : paragraphs(item.lore)) {
            String header = paragraph.getFirst(), text = String.join(" ", paragraph);
            if (header.startsWith("Ability:") || paragraph.stream().anyMatch(s -> STAT_LINE.matcher(s).find())) continue;
            Matcher amount = Pattern.compile("\\+([0-9][0-9,]*(?:\\.[0-9]+)?)(%?)[^A-Za-z0-9+%]*" + Pattern.quote(b.stat.label) + "\\b").matcher(text);
            if (!amount.find()) continue;
            double value = Double.parseDouble(amount.group(1).replace(",", ""));
            boolean percent = !amount.group(2).isEmpty();
            String source = header.contains("+") ? item.name + " lore bonus" : item.name + ": " + header;
            Matcher tiered = TIERED_BONUS.matcher(text);
            if (tiered.find()) {
                String name = tiered.group(1);
                if (!setBonuses.add("tiered:" + name)) continue; // one bonus per set, not per piece
                int needed = Integer.parseInt(tiered.group(3)), stored = Integer.parseInt(tiered.group(2));
                long pieces = l == null ? 0 : l.gear().stream().filter(i -> i.lore.stream().anyMatch(s -> s.startsWith("Tiered Bonus: " + name + " "))).count();
                // Stored lore shows the bonus for the pieces worn when it was saved; prefer the wiki per-piece value.
                Double perPiece = TIERED_PER_PIECE.containsKey(name) ? TIERED_PER_PIECE.get(name) : stored > 0 ? value / stored : null;
                Condition condition = zoneCondition(text);
                if (perPiece == null) b.contributions.add(new Contribution(category, name + " tiered bonus", b.stat, null, null, condition.text(),
                        State.COUNTED, Confidence.UNKNOWN, "Per-piece value unknown."));
                else if (pieces == 0) b.contributions.add(new Contribution(category, name + " tiered bonus", b.stat, 0.0, perPiece * needed,
                        condition.text(), State.MISSING, Confidence.KNOWN, "0/" + needed + " pieces in this loadout."));
                else add(b, category, name + " tiered bonus (" + pieces + "/" + needed + ")", perPiece * pieces, perPiece * needed, condition,
                        Confidence.KNOWN, fmt(perPiece) + " per piece worn.");
                continue;
            }
            Matcher set = SET_BONUS.matcher(text);
            if (set.find()) {
                if (!setBonuses.add(set.group(1))) continue; // one bonus per set, not per piece
                int needed = Integer.parseInt(set.group(3));
                long pieces = l == null ? 0 : l.gear().stream().filter(i -> i.lore.stream().anyMatch(s -> s.startsWith("Full Set Bonus: " + set.group(1) + " "))).count();
                Condition condition = zoneCondition(text);
                if (text.toLowerCase(Locale.ROOT).contains("per mining skill level")) {
                    // Glacite Expert Miner: "+2 Mining Speed per Mining Skill level unlocked".
                    double perLevel = value, maximum = perLevel * number(at(Catalog.progressionRules(), "mining", "maxLevel"), 60);
                    if (pieces < needed) b.contributions.add(new Contribution(category, set.group(1) + " set bonus", b.stat, 0.0, maximum,
                            condition.text(), State.MISSING, Confidence.KNOWN, pieces + "/" + needed + " pieces in this loadout."));
                    else if (miningLevel == null) b.contributions.add(new Contribution(category, set.group(1) + " set bonus", b.stat, null, maximum,
                            condition.text(), State.COUNTED, Confidence.UNKNOWN, "Mining skill level missing (Skills API off?)."));
                    else add(b, category, set.group(1) + " set bonus", perLevel * miningLevel, maximum, condition, Confidence.KNOWN,
                            fmt(perLevel) + " per Mining level (level " + miningLevel + ").");
                    continue;
                }
                if (pieces < needed) b.contributions.add(new Contribution(category, set.group(1) + " set bonus", b.stat, 0.0, value,
                        condition.text(), State.MISSING, Confidence.KNOWN, pieces + "/" + needed + " pieces in this loadout."));
                else add(b, category, set.group(1) + " set bonus", value, value, condition, Confidence.KNOWN, "Full set equipped.");
                continue;
            }
            if (percent && header.equals("Auspicious Bonus")) {
                b.contributions.add(new Contribution(Breakdown.ADDITIVE_PERCENT, item.name + ": Auspicious Bonus", b.stat, value, value, "",
                        State.COUNTED, Confidence.KNOWN, "Percentage buffs add up, then multiply the flat total."));
                continue;
            }
            if (percent) {
                b.contributions.add(new Contribution(category, source, b.stat, null, null, "", State.COUNTED, Confidence.UNKNOWN,
                        "Lore grants +" + amount.group(1) + "% " + b.stat.label + "; what the percentage applies to is not verified."));
                continue;
            }
            Condition condition = zoneCondition(text);
            if (!condition.none()) add(b, category, source, value, null, condition, Confidence.KNOWN, "Conditional lore bonus.");
            else if (drill) add(b, category, header + " (drill part)", value, null, Condition.NONE, Confidence.KNOWN,
                    "Listed in the drill lore outside the stat line total.");
            else b.contributions.add(new Contribution(category, source, b.stat, null, null, "", State.COUNTED, Confidence.UNKNOWN,
                    "Lore paragraph lists +" + amount.group(1) + "; not verified to be outside the stat line total."));
        }
    }

    /** "[+N]" after a stat line is shown outside the total; on drills it is the Polarvoid bonus. */
    private static void bracket(Breakdown b, String category, Item item) {
        int books = (int)number(at(item.extra, "polarvoid"), 0);
        Double expected = null;
        if (books > 0 && b.stat == Stat.MINING_SPEED) expected = 10.0 * Math.min(books, 5);
        else if (books > 0 && b.stat == Stat.MINING_FORTUNE) expected = 5.0;
        Double shown = null;
        for (String line : item.lore) {
            Matcher m = BRACKET.matcher(line);
            if (m.find() && m.group(1).equals(b.stat.label)) shown = Double.parseDouble(m.group(2).replace(",", ""));
        }
        String source = item.name + ": Polarvoid " + books;
        if (shown != null && expected != null && shown.equals(expected))
            b.contributions.add(new Contribution(category, source, b.stat, shown, b.stat == Stat.MINING_SPEED ? 50.0 : 5.0, "",
                    State.COUNTED, Confidence.KNOWN, "Shown in brackets outside the stat line total; matches the Polarvoid Book rule."));
        else if (shown != null || expected != null)
            b.contributions.add(new Contribution(category, shown != null && books == 0 ? item.name + ": bracketed bonus" : source, b.stat,
                    null, null, "", State.COUNTED, Confidence.UNKNOWN,
                    "Bracketed lore value " + shown + " does not match the expected Polarvoid bonus " + expected + "."));
    }

    private static List<List<String>> paragraphs(List<String> lore) {
        List<List<String>> out = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lore) {
            if (line.isBlank()) { if (!current.isEmpty()) out.add(current); current = new ArrayList<>(); }
            else current.add(line);
        }
        if (!current.isEmpty()) out.add(current);
        return out;
    }

    private static Condition zoneCondition(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("in the dwarven mines")) return Condition.zones(StatContext.DWARVEN_MINES_ONLY, "In the Dwarven Mines (not the Glacite Tunnels)");
        if (lower.contains("on mining islands")) return Condition.zones(StatContext.MINING_ISLANDS, "On Mining Islands");
        if (lower.contains("in the magma fields")) return Condition.zones(EnumSet.of(Zone.MAGMA_FIELDS), "In the Magma Fields");
        if (lower.contains("in the crystal hollows")) return Condition.zones(StatContext.CRYSTAL_HOLLOWS, "In the Crystal Hollows");
        if (lower.contains("glacite mineshaft")) return Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In Glacite Mineshafts");
        // Block-type conditions on lore bonuses (e.g. Mineral armor's spread on ores and blocks).
        if (lower.contains("mining hard stone")) return Condition.target(Target.HARD_STONE, "While mining Hard Stone");
        if (lower.contains("mining gemstones")) return Condition.target(Target.GEMSTONE, "While mining Gemstones");
        if (lower.contains("mining dwarven metals")) return Condition.target(Target.DWARVEN_METAL, "While mining Dwarven Metals");
        if (lower.matches(".*\\bores (and|or) blocks\\b.*")) return Condition.targets(EnumSet.of(Target.ORE, Target.BLOCK), "While mining ores or blocks");
        if (lower.contains("while ") || lower.contains("when ") || lower.contains("during ") || lower.contains("for every "))
            return Condition.unchecked(text);
        return Condition.NONE;
    }

    private static void add(Breakdown b, String category, String source, Double value, Double maximum, Condition condition, Confidence confidence, String note) {
        State state = condition.none() || condition.satisfiedBy(b.context) ? State.COUNTED : State.SITUATIONAL;
        b.contributions.add(new Contribution(category, source, b.stat, value, maximum, condition.text(), state, confidence, note));
    }

    // ---- pet -----------------------------------------------------------------------------------

    /**
     * Catalogued pet value for a stat (base stat, perks and Quick Claw) in a context, or null if any part is
     * unknown. A drill is assumed for drill-only perks.
     */
    static Double petValue(Loadout.Pet pet, Stat stat, StatContext context) { return petValue(pet, stat, context, true); }
    static Double petValue(Loadout.Pet pet, Stat stat, StatContext context, boolean drill) {
        Breakdown scratch = new Breakdown(stat, Loadout.Kind.BEST_OWNED, context);
        petContributions(scratch, pet, drill);
        return scratch.complete() ? scratch.total() : null;
    }
    /** Counted value of every supported stat for a pet (zeros for no pet), or null when any part is unknown. */
    public static Map<Stat, Double> petStats(Loadout.Pet pet, StatContext context, boolean drill) {
        Map<Stat, Double> out = new EnumMap<>(Stat.class);
        if (pet == null) return out;
        if (PET_STATS.stream().noneMatch(s -> s.type.equals(pet.type()))) return null;
        for (Stat stat : SUPPORTED) {
            Double v = petValue(pet, stat, context, drill);
            if (v == null) return null;
            if (v != 0) out.put(stat, v);
        }
        return out;
    }
    /** Catalogued pets: type to the rarities whose catalogued stats differ from the rarity below (others add nothing). */
    public static Map<String, List<String>> petTiers() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (PetStat s : PET_STATS) out.putIfAbsent(s.type, new ArrayList<>());
        out.forEach((type, tiers) -> {
            String previous = null;
            for (String tier : TIERS) {
                String key = PET_STATS.stream().filter(s -> s.type.equals(type) && s.fits(tier)).map(s -> s.stat + ":" + s.perLevel + ":" + s.perk).sorted().toList().toString();
                if (key.equals("[]")) continue;
                if (!key.equals(previous)) tiers.add(tier);
                previous = key;
            }
        });
        return out;
    }

    private static void pet(Breakdown b, Loadout l) {
        if (!l.petsKnown) {
            b.contributions.add(new Contribution("Pet", "Pet", b.stat, null, null, "", State.COUNTED, Confidence.UNKNOWN, "Pet data missing from the API response."));
            return;
        }
        if (l.pet == null) { b.notes.add("No active pet."); return; }
        petContributions(b, l.pet, l.tool != null && l.tool.category.equals("DRILL"));
    }

    private static void petContributions(Breakdown b, Loadout.Pet pet, boolean drill) {
        boolean catalogued = PET_STATS.stream().anyMatch(s -> s.type.equals(pet.type()));
        if (!catalogued) b.contributions.add(new Contribution("Pet", pet.label(), b.stat, null, null, "", State.COUNTED,
                Confidence.UNKNOWN, "This pet's stats are not catalogued yet."));
        for (PetStat s : PET_STATS) {
            if (!s.type.equals(pet.type()) || s.stat != b.stat || !s.fits(pet.tier())) continue;
            String source = s.perk.isEmpty() ? pet.label() : pet.label() + ": " + s.perk;
            double value = s.perLevel * pet.level();
            if (s.drill && !drill) b.contributions.add(new Contribution("Pet", source, b.stat, value, s.perLevel * 100, "With a Drill",
                    State.SITUATIONAL, Confidence.KNOWN, "Applies only while mining with a Drill."));
            else add(b, "Pet", source, value, s.perLevel * 100, s.condition, Confidence.KNOWN, s.perLevel + " per pet level.");
        }
        String held = pet.heldItem();
        if (held.equals("PET_ITEM_QUICK_CLAW")) {
            if (b.stat == Stat.MINING_FORTUNE || b.stat == Stat.MINING_SPEED) b.contributions.add(new Contribution("Pet", "Quick Claw (pet item)", b.stat, (double)(pet.level() / 2), 50.0, "",
                    State.COUNTED, Confidence.KNOWN, "+1 per 2 pet levels."));
        } else if (!held.isEmpty() && !held.contains("_SKILL_BOOST_"))
            b.contributions.add(new Contribution("Pet", held + " (pet item)", b.stat, null, null, "", State.COUNTED,
                    Confidence.UNKNOWN, "Held item effect is not catalogued yet."));
    }

    // ---- profile-wide sources ------------------------------------------------------------------

    /** HOTM trees in the response: "mining" plus the extra Loadouts trees "mining_2".. (Loadouts, June 2026). */
    public static List<String> hotmTrees(JsonObject member) {
        JsonElement nodes = at(member, "skill_tree", "nodes");
        if (!nodes.isJsonObject()) return List.of("mining");
        List<String> trees = new ArrayList<>(nodes.getAsJsonObject().keySet().stream().filter(k -> k.matches("mining(_[0-9]+)?")).sorted().toList());
        return trees.isEmpty() ? List.of("mining") : trees;
    }
    public static String treeLabel(String tree) { return "HOTM tree " + (tree.equals("mining") ? "1" : tree.substring(tree.indexOf('_') + 1)); }
    /**
     * Picks the HOTM tree that gives the method the most value (the method weighting of {@link Loadout#weigh})
     * and stores it on the loadout. The API does not say which tree is active.
     */
    public static String chooseTree(JsonObject member, Loadout l, StatContext context, MiningMethod m) {
        String best = "mining"; double bestScore = Double.NEGATIVE_INFINITY;
        for (String tree : hotmTrees(member)) {
            l.hotmTree = tree;
            double score = Loadout.weigh(m, s -> compute(member, l, context, s).total());
            if (score > bestScore + 1e-9) { best = tree; bestScore = score; }
        }
        l.hotmTree = best;
        return best;
    }
    /** The drill holds a Blue Cheese Goblin Omelette: +1 level on every unlocked HOTM perk, past the cap (wiki). */
    public static boolean blueCheese(Item tool) {
        return tool != null && "goblin_omelette_blue_cheese".equals(string(at(tool.extra, "drill_part_upgrade_module"), ""));
    }
    private static void hotm(Breakdown b, JsonObject member, String tree, boolean omelette) {
        JsonElement nodes = at(member, "skill_tree", "nodes", tree);
        if (!nodes.isJsonObject()) nodes = at(member, "mining_core", "nodes");
        if (!nodes.isJsonObject()) {
            b.contributions.add(new Contribution("HOTM", "Heart of the Mountain perks", b.stat, null, null, "", State.COUNTED,
                    Confidence.UNKNOWN, "No HOTM tree in the API response."));
            return;
        }
        for (Perk perk : PERKS) {
            if (perk.stat != b.stat) continue;
            int level = (int)number(at(nodes, perk.id), 0);
            if (level <= 0) {
                b.contributions.add(new Contribution("HOTM", perk.name, b.stat, 0.0, perk.at(perk.maxLevel), perk.condition.text(),
                        State.MISSING, Confidence.KNOWN, "Not unlocked."));
                continue;
            }
            Condition condition = enabled(nodes, perk.id) ? perk.condition : Condition.unchecked("Perk toggled off in the HOTM tree");
            int effective = level + (omelette ? 1 : 0);
            add(b, "HOTM", perk.name + " " + level + (omelette ? " (+1 Blue Cheese)" : ""), perk.at(effective), perk.at(perk.maxLevel + 1), condition, Confidence.KNOWN,
                    omelette ? "Blue Cheese Goblin Omelette on the drill: +1 level." : "");
        }
        Double frontLoaded = FRONT_LOADED.get(b.stat);
        if (frontLoaded != null) {
            if (number(at(nodes, "front_loaded"), 0) <= 0) b.contributions.add(new Contribution("HOTM", "Front Loaded", b.stat, 0.0, frontLoaded,
                    "First 2,500 Gemstones mined each day", State.MISSING, Confidence.KNOWN, "Not in this tree (API key front_loaded assumed, not yet observed)."));
            else add(b, "HOTM", "Front Loaded", frontLoaded, frontLoaded, Condition.unchecked("First 2,500 Gemstones mined each day"),
                    Confidence.KNOWN, "The day's count is not in the API.");
        }
        int skyMall = (int)number(at(nodes, "sky_mall"), 0);
        if (skyMall <= 0 || !SKY_MALL.containsKey(b.stat)) return;
        String today = string(at(member, "mining_core", "current_daily_effect"), "");
        String code = SKY_MALL_CODES.get(b.stat);
        Condition condition;
        String note;
        if (!enabled(nodes, "sky_mall")) { condition = Condition.unchecked("Perk toggled off in the HOTM tree"); note = ""; }
        else if (code != null && code.equals(today)) {
            b.contributions.add(new Contribution("HOTM", "Sky Mall", b.stat, SKY_MALL.get(b.stat), SKY_MALL.get(b.stat),
                    "On Mining Islands, today only", State.TEMPORARY, Confidence.KNOWN,
                    "Today's buff (" + today + "); changes every SkyBlock day (20 minutes), so it is not counted."));
            return;
        } else {
            condition = Condition.unchecked("Only on days the random Sky Mall buff is " + b.stat.label + ", on Mining Islands");
            note = today.isEmpty() ? "The day's buff is not in the API response." : "Today's buff is " + today + ".";
        }
        add(b, "HOTM", "Sky Mall", SKY_MALL.get(b.stat), SKY_MALL.get(b.stat), condition, Confidence.KNOWN, note);
    }
    private static boolean enabled(JsonElement nodes, String perk) {
        return !"false".equals(string(at(nodes, "toggle_" + perk), "true"));
    }

    /** Mining skill level from the skill XP, or null when the XP is missing. */
    static Integer miningLevel(JsonObject member) {
        JsonElement xp = at(member, "player_data", "experience", "SKILL_MINING");
        JsonElement levels = at(Catalog.progressionRules(), "mining", "levels");
        if (xp.isJsonNull() || !levels.isJsonArray()) return null;
        int level = 0;
        for (JsonElement rule : levels.getAsJsonArray())
            if (number(xp, 0) >= number(at(rule, "totalExpRequired"), Double.MAX_VALUE)) level = Math.max(level, (int)number(at(rule, "level"), 0));
        return level;
    }
    private static void skill(Breakdown b, JsonObject member) {
        JsonElement xp = at(member, "player_data", "experience", "SKILL_MINING");
        JsonObject rules = Catalog.progressionRules();
        JsonElement levels = at(rules, "mining", "levels");
        int maximum = (int)number(at(rules, "mining", "maxLevel"), 60);
        if (xp.isJsonNull() || !levels.isJsonArray()) {
            b.contributions.add(new Contribution("Skill", "Mining skill", b.stat, null, 4.0 * maximum, "", State.COUNTED,
                    Confidence.UNKNOWN, "Mining skill XP missing (Skills API off?)."));
            return;
        }
        int level = 0;
        for (JsonElement rule : levels.getAsJsonArray())
            if (number(xp, 0) >= number(at(rule, "totalExpRequired"), Double.MAX_VALUE)) level = Math.max(level, (int)number(at(rule, "level"), 0));
        b.contributions.add(new Contribution("Skill", "Mining " + level, b.stat, 4.0 * level, 4.0 * maximum, "", State.COUNTED,
                Confidence.KNOWN, "4 per Mining level."));
    }

    private static void collections(Breakdown b, JsonObject member) {
        JsonElement counts = at(member, "collection");
        JsonElement rules = at(Catalog.progressionRules(), "collections");
        if (!rules.isJsonObject()) return;
        Pattern reward = Pattern.compile("^\\+([0-9.]+)[^A-Za-z0-9]*" + Pattern.quote(b.stat.label) + "$");
        for (var entry : rules.getAsJsonObject().entrySet()) {
            double current = 0, maximum = 0;
            double count = counts.isJsonObject() ? number(at(counts, entry.getKey()), 0) : -1;
            for (JsonElement tier : at(entry.getValue(), "tiers").getAsJsonArray()) {
                JsonElement unlocks = at(tier, "unlocks");
                if (!unlocks.isJsonArray()) continue;
                for (JsonElement unlock : unlocks.getAsJsonArray()) {
                    Matcher m = reward.matcher(string(unlock, ""));
                    if (!m.find()) continue;
                    double value = Double.parseDouble(m.group(1));
                    maximum += value;
                    if (count >= number(at(tier, "amountRequired"), Double.MAX_VALUE)) current += value;
                }
            }
            if (maximum == 0) continue;
            String name = string(at(entry.getValue(), "name"), entry.getKey()) + " collection";
            if (count < 0) b.contributions.add(new Contribution("Collection", name, b.stat, null, maximum, "", State.COUNTED,
                    Confidence.UNKNOWN, "Collection count missing (Collections API off?)."));
            else b.contributions.add(new Contribution("Collection", name, b.stat, current, maximum, "",
                    current > 0 ? State.COUNTED : State.MISSING, Confidence.KNOWN, "Collection tier rewards."));
        }
    }

    private static void attributes(Breakdown b, JsonObject member) {
        JsonElement stacks = at(member, "attributes", "stacks");
        for (Attribute a : ATTRIBUTES) {
            if (a.stat != b.stat) continue;
            if (!stacks.isJsonObject()) {
                b.contributions.add(new Contribution("Attribute", a.name, b.stat, null, a.perLevel * 10, a.condition.text(),
                        State.COUNTED, Confidence.UNKNOWN, "Attribute data missing from the API response."));
                continue;
            }
            int shards = (int)number(at(stacks, a.id), 0), level = 0;
            for (int needed : ATTRIBUTE_SHARDS.get(a.rarity)) if (shards >= needed) level++;
            if (level == 0) b.contributions.add(new Contribution("Attribute", a.name, b.stat, 0.0, a.perLevel * 10, a.condition.text(),
                    State.MISSING, Confidence.KNOWN, "No shards syphoned."));
            else add(b, "Attribute", a.name + " " + level, a.perLevel * level, a.perLevel * 10, a.condition, Confidence.KNOWN,
                    shards + " shards syphoned.");
        }
    }

    private static void essence(Breakdown b, JsonObject member) {
        JsonElement perks = at(member, "player_data", "perks");
        Condition islands = Condition.zones(StatContext.MINING_ISLANDS, "On Mining Islands");
        for (Essence e : ESSENCE) {
            if (e.stat != b.stat) continue;
            double maximum = e.perLevel * e.maxLevel;
            if (!perks.isJsonObject()) {
                b.contributions.add(new Contribution("Essence", e.name, b.stat, null, maximum, islands.text(),
                        State.COUNTED, Confidence.UNKNOWN, "Essence perks missing from the API response."));
                continue;
            }
            int level = (int)number(at(perks, e.id), 0);
            if (level == 0) b.contributions.add(new Contribution("Essence", e.name, b.stat, 0.0, maximum, islands.text(),
                    State.MISSING, Confidence.KNOWN, "Not purchased."));
            else add(b, "Essence", e.name.replaceFirst(" \\(", " " + level + " ("), e.perLevel * level, maximum, islands, Confidence.KNOWN,
                    fmt(e.perLevel) + " per level.");
        }
    }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long)v) : String.valueOf(v); }

    /** Effects the API reports; their stat mapping is not verified, so values stay unknown. */
    private static void temporary(Breakdown b, JsonObject member) {
        for (String field : List.of("active_effects", "temp_stat_buffs")) {
            JsonElement effects = at(member, "player_data", field);
            if (!effects.isJsonArray()) continue;
            for (JsonElement effect : effects.getAsJsonArray()) {
                String name = effect.isJsonObject() ? string(at(effect, "effect"), string(at(effect, "key"), effect.toString())) : string(effect, effect.toString());
                b.contributions.add(new Contribution("Temporary", name, b.stat, null, null, "", State.TEMPORARY, Confidence.UNKNOWN,
                        "Reported by the API (" + field + "); its " + b.stat.label + " effect is not mapped yet."));
            }
        }
    }

    /**
     * God Potion: Spelunker V (+25 Mining Fortune) and Haste IV (+200 Mining Speed) per the wiki God
     * Potion, Spelunker Potion and Haste Potion pages. Not in the API, so it is an assumption the
     * context can switch off. Booster Cookie (profile.cookie_buff_active) grants Wisdom and Magic
     * Find only, so it is listed at zero.
     */
    private static void assumedBuffs(Breakdown b, JsonObject member) {
        if (b.stat != Stat.MINING_FORTUNE && b.stat != Stat.MINING_SPEED) return;
        double god = b.stat == Stat.MINING_FORTUNE ? 25 : 200;
        String effect = b.stat == Stat.MINING_FORTUNE ? "Spelunker V" : "Haste IV";
        Condition condition = b.context.godPotion() ? Condition.NONE : Condition.unchecked("Requires an active God Potion");
        add(b, "Buff", "God Potion (" + effect + ")", god, god, condition, Confidence.MANUAL,
                "Assumed active; the API does not report potion effects.");
        JsonElement cookie = at(member, "profile", "cookie_buff_active");
        if (!cookie.isJsonNull())
            b.contributions.add(new Contribution("Buff", "Booster Cookie", b.stat, 0.0, 0.0, "",
                    "true".equals(string(cookie, "false")) ? State.COUNTED : State.MISSING, Confidence.KNOWN,
                    "Grants Wisdom and Magic Find, no " + b.stat.label + "."));
    }

    /** Sources the profile API never reports. Values are left unknown; ranges are in the note. */
    private static void untrackable(Breakdown b) {
        String cereal = "Permanent consumable, +2 per bowl (wiki lists a maximum of 10); not observed in the API.";
        Map<String, String> sources = switch (b.stat) {
            case MINING_FORTUNE -> Map.of(
                "Lanterns / Will-o'-wisp", "+10 to +25 for 5 minutes while deployed nearby.",
                "Weather", "+25 (Breeze) or +50 (Mist / Rockfall) in the affected zone.",
                "Better Together", "+20 to +100 with other players in the zone.",
                "Mining Fiesta", "+25% Mining Fortune during Cole's event; temporary, not counted.",
                "Fortunate Festivity (Doug)", "+5 to +25 during the Mining Fiesta; temporary, not counted.",
                "Filet O' Fortune / Century Cake", "+20 / +5; whether the API reports them is not verified.");
            case MINING_SPEED -> Map.of(
                "Lanterns / Will-o'-wisp", "+20 to +100 for 5 minutes while deployed nearby.",
                "Weather", "+100 (Breeze) or +250 (Mist / Rockfall / Snowstorm) in the affected zone.",
                "Better Together", "Up to +1,250 with other players in the zone.");
            case GEMSTONE_FORTUNE -> Map.of("Dwarven O's Gemstone Grahams", cereal);
            case ORE_FORTUNE -> Map.of("Dwarven O's Ore Oats", cereal);
            case BLOCK_FORTUNE -> Map.of("Dwarven O's Block Bran", cereal);
            case DWARVEN_METAL_FORTUNE -> Map.of("Dwarven O's Metallic Minis", cereal,
                "Dwarven Expertise (Researcher Beryl)", "Shop perk, +2 per level up to +20 on Mining Islands; its API key is not identified.");
            case PRISTINE -> Map.of("Chilled Pristine Potato", "+0.1 in Glacite Mineshafts; not observed in the API.");
            case GEMSTONE_SPREAD -> Map.of("Will-o'-wisp", "+2.5 while deployed nearby (lantern line).");
            case MINING_SPREAD -> Map.of("Sheer Force (pickaxe ability)", "+200 for 20 to 30 seconds while active; ability uptime, not a stat.");
            default -> Map.of();
        };
        new TreeMap<>(sources).forEach((name, note) -> b.contributions.add(new Contribution("Untrackable", name, b.stat, null, null, "",
                State.UNTRACKABLE, Confidence.UNKNOWN, note)));
    }
}

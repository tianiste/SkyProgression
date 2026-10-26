package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;
import io.github.tianiste.skyprogression.core.Breakdown.*;
import io.github.tianiste.skyprogression.core.StatContext.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * Builds the Mining Fortune / Mining Speed ledger for a loadout. Rules and their sources are listed
 * in docs/CALCULATIONS.md ("Stat breakdown"). Gear contributes its displayed lore total once; drill
 * parts and Polarvoid, which the lore lists outside that total, are separate contributions.
 */
public final class StatBreakdown {
    private StatBreakdown() {}
    public static final Set<Stat> SUPPORTED = EnumSet.of(Stat.MINING_FORTUNE, Stat.MINING_SPEED);

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
            new Perk("rags_to_riches", "Rags to Riches", Stat.MINING_FORTUNE, 0, 4, 50, Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In Glacite Mineshafts")));
    /** Sky Mall grants one random daily buff on Mining Islands; the day's buff is not in the API. */
    static final Map<Stat, Double> SKY_MALL = Map.of(Stat.MINING_FORTUNE, 50.0, Stat.MINING_SPEED, 100.0);
    /** `mining_core.current_daily_effect` codes; dig_speed matched the in-game Sky Mall +100 on 2026-10-26. */
    static final Map<Stat, String> SKY_MALL_CODES = Map.of(Stat.MINING_SPEED, "dig_speed");

    /** Attribute: rarity (shard cost table) and linear effect per level up to 10 (wiki shard pages). */
    record Attribute(String id, String name, String rarity, Stat stat, double perLevel, Condition condition) {}
    static final List<Attribute> ATTRIBUTES = List.of(
            new Attribute("fortunate_miner", "Fortunate Miner (Flitter Shard)", "COMMON", Stat.MINING_FORTUNE, 2.5, Condition.NONE),
            new Attribute("rotten_pickaxe", "Rotten Pickaxe (Miner Zombie Shard)", "COMMON", Stat.MINING_SPEED, 3, Condition.NONE),
            new Attribute("quartz_speed", "Quartz Speed (Quartzfang Shard)", "RARE", Stat.MINING_SPEED, 10, Condition.unchecked("While any Weather is active on Mining Islands")));
    /** Cumulative shards for levels 1..10 per rarity (wiki Attributes leveling table). */
    static final Map<String, int[]> ATTRIBUTE_SHARDS = Map.of(
            "COMMON", new int[]{1, 4, 9, 15, 22, 30, 40, 54, 72, 96},
            "UNCOMMON", new int[]{1, 3, 6, 10, 15, 21, 28, 36, 48, 64},
            "RARE", new int[]{1, 3, 6, 9, 13, 17, 22, 28, 36, 48},
            "EPIC", new int[]{1, 2, 4, 6, 9, 12, 16, 20, 25, 32},
            "LEGENDARY", new int[]{1, 2, 3, 5, 7, 9, 12, 15, 19, 24});

    /** Pet base stats per level, only where the wiki's level 1 and level 100 values agree with a linear rate. */
    record PetStat(String type, String minimumTier, Stat stat, double perLevel) {}
    static final List<PetStat> PET_STATS = List.of(
            new PetStat("BAL", "EPIC", Stat.MINING_FORTUNE, 1),
            new PetStat("SCATHA", "RARE", Stat.MINING_FORTUNE, 1.25),
            new PetStat("SCATHA", "RARE", Stat.MINING_SPEED, 1),
            new PetStat("MITHRIL_GOLEM", "COMMON", Stat.MINING_FORTUNE, .25),
            new PetStat("SILVERFISH", "COMMON", Stat.MINING_FORTUNE, .2));
    private static final List<String> TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");

    private static final Pattern BRACKET = Pattern.compile("^(.+?):\\s*[+-]?[0-9,.]+\\s*\\[\\+([0-9,.]+)\\]");
    private static final Pattern STAT_LINE = Pattern.compile("^[A-Z][A-Za-z' ]+:\\s*[+-]?[0-9]");
    private static final Pattern SET_BONUS = Pattern.compile("Full Set Bonus: (.+?) \\((\\d+)/(\\d+)\\)");

    public static Breakdown compute(JsonObject member, Loadout loadout, StatContext context, Stat stat) {
        if (!SUPPORTED.contains(stat)) throw new IllegalArgumentException(stat.label + " breakdown is not implemented yet");
        Breakdown b = new Breakdown(stat, loadout.kind, context);
        b.notes.addAll(loadout.notes);
        gear(b, loadout);
        pet(b, loadout);
        hotm(b, member);
        if (stat == Stat.MINING_FORTUNE) { skill(b, member); collections(b, member); }
        attributes(b, member);
        essence(b, member);
        temporary(b, member);
        untrackable(b);
        return b;
    }

    // ---- gear ----------------------------------------------------------------------------------

    private static void gear(Breakdown b, Loadout l) {
        if (!l.inventoryKnown) {
            b.contributions.add(new Contribution("Gear", "Armor, equipment, tool and accessories", b.stat, null, null, "",
                    State.COUNTED, Confidence.UNKNOWN, "Inventory API data is missing or unreadable; enable the Inventory API."));
            return;
        }
        if (l.tool == null) b.contributions.add(new Contribution("Tool", "Mining tool", b.stat, null, null, "",
                State.COUNTED, Confidence.MANUAL, "The API does not expose the held item; select the tool you mine with."));
        Set<String> setBonuses = new HashSet<>();
        for (Item item : l.armor) itemContributions(b, "Armor", item, l, setBonuses);
        for (Item item : l.equipment) itemContributions(b, "Equipment", item, l, setBonuses);
        if (l.tool != null) itemContributions(b, "Tool", l.tool, l, setBonuses);
        for (Item item : l.accessories) itemContributions(b, "Accessory", item, l, setBonuses);
        if (l.accessories.stream().anyMatch(i -> i.id.equals("HASTE_ARTIFACT")) && b.stat == Stat.MINING_SPEED)
            b.contributions.add(new Contribution("Accessory", "Haste III (Haste Artifact effect)", b.stat, 150.0, 150.0,
                    "Only when no stronger Haste effect is active", State.SITUATIONAL, Confidence.KNOWN,
                    "Potion-style effect on Mining System islands; not part of the item's stat line."));
    }

    /** Value the item contributes unconditionally (stat line plus drill parts), for choosing a best-owned copy. */
    static double itemValue(Item item, Stat stat) {
        Breakdown scratch = new Breakdown(stat, Loadout.Kind.BEST_OWNED, StatContext.UNSPECIFIED);
        itemContributions(scratch, "", item, null, new HashSet<>());
        return scratch.total();
    }

    private static void itemContributions(Breakdown b, String category, Item item, Loadout l, Set<String> setBonuses) {
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
            Matcher set = SET_BONUS.matcher(text);
            if (set.find()) {
                if (!setBonuses.add(set.group(1))) continue; // one bonus per set, not per piece
                int needed = Integer.parseInt(set.group(3));
                long pieces = l == null ? 0 : l.gear().stream().filter(i -> i.lore.stream().anyMatch(s -> s.startsWith("Full Set Bonus: " + set.group(1) + " "))).count();
                Condition condition = zoneCondition(text);
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
        Double expected = books <= 0 ? null : b.stat == Stat.MINING_SPEED ? 10.0 * Math.min(books, 5) : b.stat == Stat.MINING_FORTUNE ? 5.0 : null;
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
        if (lower.contains("in the crystal hollows")) return Condition.zones(EnumSet.of(Zone.CRYSTAL_HOLLOWS), "In the Crystal Hollows");
        if (lower.contains("glacite mineshaft")) return Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In Glacite Mineshafts");
        if (lower.contains("while ") || lower.contains("when ") || lower.contains("during ") || lower.contains("for every "))
            return Condition.unchecked(text);
        return Condition.NONE;
    }

    private static void add(Breakdown b, String category, String source, Double value, Double maximum, Condition condition, Confidence confidence, String note) {
        State state = condition.none() || condition.satisfiedBy(b.context) ? State.COUNTED : State.SITUATIONAL;
        b.contributions.add(new Contribution(category, source, b.stat, value, maximum, condition.text(), state, confidence, note));
    }

    // ---- pet -----------------------------------------------------------------------------------

    /** Catalogued pet value for a stat (base stat plus Quick Claw), or null if any part is unknown. */
    static Double petValue(Loadout.Pet pet, Stat stat) {
        Breakdown scratch = new Breakdown(stat, Loadout.Kind.BEST_OWNED, StatContext.UNSPECIFIED);
        petContributions(scratch, pet);
        return scratch.complete() ? scratch.total() : null;
    }

    private static void pet(Breakdown b, Loadout l) {
        if (!l.petsKnown) {
            b.contributions.add(new Contribution("Pet", "Pet", b.stat, null, null, "", State.COUNTED, Confidence.UNKNOWN, "Pet data missing from the API response."));
            return;
        }
        if (l.pet == null) { b.notes.add("No active pet."); return; }
        petContributions(b, l.pet);
    }

    private static void petContributions(Breakdown b, Loadout.Pet pet) {
        boolean catalogued = PET_STATS.stream().anyMatch(s -> s.type.equals(pet.type()));
        PET_STATS.stream().filter(s -> s.type.equals(pet.type()) && s.stat == b.stat).findFirst().ifPresentOrElse(s -> {
            if (TIERS.indexOf(pet.tier()) >= TIERS.indexOf(s.minimumTier))
                b.contributions.add(new Contribution("Pet", pet.label(), b.stat, s.perLevel * pet.level(), s.perLevel * 100, "",
                        State.COUNTED, Confidence.KNOWN, s.perLevel + " per pet level."));
        }, () -> {
            if (!catalogued) b.contributions.add(new Contribution("Pet", pet.label(), b.stat, null, null, "", State.COUNTED,
                    Confidence.UNKNOWN, "This pet's stats are not catalogued yet."));
        });
        String held = pet.heldItem();
        if (held.equals("PET_ITEM_QUICK_CLAW"))
            b.contributions.add(new Contribution("Pet", "Quick Claw (pet item)", b.stat, (double)(pet.level() / 2), 50.0, "",
                    State.COUNTED, Confidence.KNOWN, "+1 per 2 pet levels."));
        else if (!held.isEmpty() && !held.contains("_SKILL_BOOST_"))
            b.contributions.add(new Contribution("Pet", held + " (pet item)", b.stat, null, null, "", State.COUNTED,
                    Confidence.UNKNOWN, "Held item effect is not catalogued yet."));
    }

    // ---- profile-wide sources ------------------------------------------------------------------

    private static void hotm(Breakdown b, JsonObject member) {
        JsonElement nodes = at(member, "skill_tree", "nodes", "mining");
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
            add(b, "HOTM", perk.name + " " + level, perk.at(level), perk.at(perk.maxLevel), condition, Confidence.KNOWN, "");
        }
        int skyMall = (int)number(at(nodes, "sky_mall"), 0);
        if (skyMall <= 0) return;
        String today = string(at(member, "mining_core", "current_daily_effect"), "");
        String code = SKY_MALL_CODES.get(b.stat);
        Condition condition;
        String note;
        if (!enabled(nodes, "sky_mall")) { condition = Condition.unchecked("Perk toggled off in the HOTM tree"); note = ""; }
        else if (code != null && code.equals(today)) {
            condition = Condition.zones(StatContext.MINING_ISLANDS, "On Mining Islands");
            note = "Today's buff (" + today + "); changes every SkyBlock day (20 minutes).";
        } else {
            condition = Condition.unchecked("Only on days the random Sky Mall buff is " + b.stat.label + ", on Mining Islands");
            note = today.isEmpty() ? "The day's buff is not in the API response." : "Today's buff is " + today + ".";
        }
        add(b, "HOTM", "Sky Mall", SKY_MALL.get(b.stat), SKY_MALL.get(b.stat), condition, Confidence.KNOWN, note);
    }
    private static boolean enabled(JsonElement nodes, String perk) {
        return !"false".equals(string(at(nodes, "toggle_" + perk), "true"));
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
        if (b.stat != Stat.MINING_SPEED) return;
        JsonElement perks = at(member, "player_data", "perks");
        Condition islands = Condition.zones(StatContext.MINING_ISLANDS, "On Mining Islands");
        if (!perks.isJsonObject()) {
            b.contributions.add(new Contribution("Essence", "Eager Miner (Gold Essence)", b.stat, null, 100.0, islands.text(),
                    State.COUNTED, Confidence.UNKNOWN, "Essence perks missing from the API response."));
            return;
        }
        int level = (int)number(at(perks, "eager_miner"), 0);
        if (level == 0) b.contributions.add(new Contribution("Essence", "Eager Miner (Gold Essence)", b.stat, 0.0, 100.0, islands.text(),
                State.MISSING, Confidence.KNOWN, "Not purchased."));
        else add(b, "Essence", "Eager Miner " + level + " (Gold Essence)", 10.0 * level, 100.0, islands, Confidence.KNOWN, "10 per level.");
    }

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

    /** Sources the profile API never reports. Values are left unknown; ranges are in the note. */
    private static void untrackable(Breakdown b) {
        Map<String, String> sources = b.stat == Stat.MINING_FORTUNE ? Map.of(
                "Spelunker potion", "+5 to +25 (Spelunker I-V, including God Potion).",
                "Lanterns / Will-o'-wisp", "+10 to +25 for 5 minutes while deployed nearby.",
                "Weather", "+25 (Breeze) or +50 (Mist / Rockfall) in the affected zone.",
                "Better Together", "+20 to +100 with other players in the zone.",
                "Mining Fiesta", "+25% Mining Fortune during Cole's event; needs election data, not the profile.",
                "Filet O' Fortune / Century Cake", "+20 / +5; whether the API reports them is not verified.")
                : Map.of(
                "Haste potion", "+50 to +200 (Haste I-IV) without a stronger Haste effect.",
                "Lanterns / Will-o'-wisp", "+20 to +100 for 5 minutes while deployed nearby.",
                "Weather", "+100 (Breeze) or +250 (Mist / Rockfall / Snowstorm) in the affected zone.",
                "Better Together", "Up to +1,250 with other players in the zone.");
        new TreeMap<>(sources).forEach((name, note) -> b.contributions.add(new Contribution("Untrackable", name, b.stat, null, null, "",
                State.UNTRACKABLE, Confidence.UNKNOWN, note)));
    }
}

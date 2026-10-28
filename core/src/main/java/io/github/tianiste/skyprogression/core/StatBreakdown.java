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
    public static final Set<Stat> SUPPORTED = EnumSet.allOf(Stat.class);
    /** Stats the rate models and upgrade ranking read; the comparison and Start with tabs compute only these. */
    public static final Set<Stat> RATE_STATS = EnumSet.of(Stat.MINING_FORTUNE, Stat.MINING_SPEED, Stat.GEMSTONE_FORTUNE,
            Stat.ORE_FORTUNE, Stat.DWARVEN_METAL_FORTUNE, Stat.BLOCK_FORTUNE, Stat.MINING_SPREAD, Stat.GEMSTONE_SPREAD, Stat.PRISTINE,
            Stat.HEAT_RESISTANCE, Stat.COLD_RESISTANCE);
    /** Display groups (SkyCompass stat groups as a feature spec, docs/references/SKYCOMPASS.md). */
    public static final Map<String, List<Stat>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("General", List.of(Stat.MINING_FORTUNE, Stat.MINING_SPEED, Stat.MINING_SPREAD, Stat.MINING_WISDOM, Stat.BREAKING_POWER, Stat.PICKAXE_ABILITY_COOLDOWN));
        GROUPS.put("Gemstone", List.of(Stat.GEMSTONE_FORTUNE, Stat.GEMSTONE_SPREAD, Stat.PRISTINE));
        GROUPS.put("Blocks & Ores", List.of(Stat.BLOCK_FORTUNE, Stat.ORE_FORTUNE));
        GROUPS.put("Dwarven Metal", List.of(Stat.DWARVEN_METAL_FORTUNE));
        GROUPS.put("Resistance", List.of(Stat.HEAT_RESISTANCE, Stat.COLD_RESISTANCE));
        GROUPS.put("Powder", List.of(Stat.ALL_POWDER, Stat.MITHRIL_POWDER, Stat.GEMSTONE_POWDER, Stat.GLACITE_POWDER));
    }

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
            // Tier 9 / Tier 8 templates and NEU hotmlayout.json: 4 Mining Speed and 0.1 Gemstone Spread per level, max 100, in Glacite Mineshafts.
            new Perk("eager_adventurer", "Eager Adventurer", Stat.MINING_SPEED, 0, 4, 100, Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In Glacite Mineshafts")),
            new Perk("steady_hand", "Steady Hand", Stat.GEMSTONE_SPREAD, 0, .1, 100, Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In Glacite Mineshafts")),
            new Perk("gem_lover", "Gem Lover", Stat.GEMSTONE_FORTUNE, 20, 4, 20, Condition.NONE),
            new Perk("old_school", "Old-School", Stat.ORE_FORTUNE, 0, 5, 20, Condition.NONE),
            new Perk("blockhead", "Blockhead", Stat.BLOCK_FORTUNE, 0, 5, 20, Condition.NONE),
            new Perk("metal_head", "Metal Head", Stat.DWARVEN_METAL_FORTUNE, 0, 5, 20, Condition.NONE),
            new Perk("efficient_miner", "Efficient Miner", Stat.MINING_SPREAD, 0, 3, 100, Condition.NONE),
            new Perk("mole", "Mole", Stat.MINING_SPREAD, 50 - 350.0 / 199, 350.0 / 199, 200, Condition.target(Target.HARD_STONE, "While mining Hard Stone")),
            new Perk("mining_master", "Mining Master", Stat.PRISTINE, 0, .1, 10, Condition.NONE),
            new Perk("seasoned_mineman", "Seasoned Mineman", Stat.MINING_WISDOM, 5, .1, 100, Condition.NONE),
            new Perk("keep_it_cool", "Keep It Cool", Stat.HEAT_RESISTANCE, 0, .4, 50, Condition.NONE),
            new Perk("warm_heart", "Warm Heart", Stat.COLD_RESISTANCE, 0, .4, 50, Condition.NONE),
            new Perk("powder_buff", "Powder Buff", Stat.ALL_POWDER, 0, 1, 50, Condition.NONE));
    /**
     * Mineshaft Mayhem (HOTM 8): one random buff on entering a Glacite Mineshaft (wiki Tier 8 template). Node key
     * `mineshaft_mayhem` as in NEU hotmlayout.json (docs/references/API-FIELDS.md).
     */
    static final Map<Stat, Double> MINESHAFT_MAYHEM = Map.of(Stat.MINING_FORTUNE, 100.0, Stat.MINING_SPEED, 200.0,
            Stat.COLD_RESISTANCE, 10.0, Stat.PICKAXE_ABILITY_COOLDOWN, 25.0);
    /**
     * Front Loaded (HOTM 4): +150 Gemstone Fortune and +250 Mining Speed for the first 2,500 Gemstones each
     * day. Node key `front_loaded` as in NEU hotmlayout.json (docs/references/API-FIELDS.md).
     */
    static final Map<Stat, Double> FRONT_LOADED = Map.of(Stat.GEMSTONE_FORTUNE, 150.0, Stat.MINING_SPEED, 250.0);
    /** Sky Mall grants one random daily buff on Mining Islands; the day's buff is not in the API. */
    static final Map<Stat, Double> SKY_MALL = Map.of(Stat.MINING_FORTUNE, 50.0, Stat.MINING_SPEED, 100.0,
            Stat.PICKAXE_ABILITY_COOLDOWN, 20.0, Stat.ALL_POWDER, 15.0);
    /**
     * `mining_core.current_daily_effect` codes; dig_speed matched the in-game Sky Mall +100 on 2026-10-26. powder_bonus is
     * mapped to the +15% Powder buff by its name only (seen in the API, not yet compared with the game).
     */
    static final Map<Stat, String> SKY_MALL_CODES = Map.of(Stat.MINING_SPEED, "dig_speed", Stat.ALL_POWDER, "powder_bonus");

    static final String FIESTA = "While Cole's Mining Fiesta is active";
    static final String MINES_OF_DIVAN = "In the Mines of Divan (Crystal Hollows)";
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
                    Condition.unchecked("Per-block chance of +1 Pristine (expected value shown); not part of the stat total")),
            new Attribute("cavern_wisdom", "Cavern Wisdom (Cavernshade Shard)", "EPIC", Stat.MINING_WISDOM, .5, Condition.NONE),
            new Attribute("pickaxe_cooldown", "Pickaxe Cooldown (Abyssal Miner Shard)", "EPIC", Stat.PICKAXE_ABILITY_COOLDOWN, 1, Condition.NONE),
            new Attribute("yog_membrane", "Yog Membrane (Yog Shard)", "COMMON", Stat.HEAT_RESISTANCE, 1, Condition.NONE),
            new Attribute("cold_resistance", "Cold Resistance (Wumpa Shard)", "LEGENDARY", Stat.COLD_RESISTANCE, .5, Condition.NONE),
            new Attribute("atomized_mithril", "Atomized Mithril (Star Sentry Shard)", "RARE", Stat.MITHRIL_POWDER, 1, Condition.NONE),
            new Attribute("atomized_crystals", "Atomized Crystals (Thyst Shard)", "RARE", Stat.GEMSTONE_POWDER, 1, Condition.NONE),
            new Attribute("atomized_glacite", "Atomized Glacite (Stalagmight Shard)", "RARE", Stat.GLACITE_POWDER, 1, Condition.NONE),
            // Packrat Shard (wiki): only while Cole's Mining Fiesta perk is active, a temporary buff.
            new Attribute("fiesta_fortune", "Fiesta Fortune (Packrat Shard)", "UNCOMMON", Stat.GEMSTONE_FORTUNE, 5, Condition.unchecked(FIESTA)),
            // Wiki Attributes / shard pages: Galaxy Fish +1-10 Mining Fortune, Snoozle +1-10 Mining Spread, Hideonsun +5-50 Ore
            // Fortune, Nilbog +5-50 Mining Speed and +2.5-25 Ore Fortune in the Mines of Divan, Gimmiegold +1-10 Ore Fortune on a
            // Goblin pet, Littlefoot +0.5 Gemstone Spread per (30 - level) Cold Resistance in the Glacite Mineshafts (see attributes()).
            new Attribute("ultimate_dna", "Ultimate DNA (Galaxy Fish Shard)", "LEGENDARY", Stat.MINING_FORTUNE, 1, Condition.NONE),
            new Attribute("sleepy_spread", "Sleepy Spread (Snoozle Shard)", "RARE", Stat.MINING_SPREAD, 1, Condition.NONE),
            new Attribute("gold_box", "Gold Box (Hideonsun Shard)", "COMMON", Stat.ORE_FORTUNE, 5, Condition.NONE),
            new Attribute("royal_riches", "Royal Riches (Nilbog Shard)", "EPIC", Stat.MINING_SPEED, 5, Condition.unchecked(MINES_OF_DIVAN)),
            new Attribute("royal_riches", "Royal Riches (Nilbog Shard)", "EPIC", Stat.ORE_FORTUNE, 2.5, Condition.unchecked(MINES_OF_DIVAN)),
            new Attribute("greedy_goblin", "Greedy Goblin (Gimmiegold Shard)", "RARE", Stat.ORE_FORTUNE, 1, Condition.unchecked("With a Goblin pet active")),
            new Attribute("freezing_spread", "Freezing Spread (Littlefoot Shard)", "EPIC", Stat.GEMSTONE_SPREAD, 0,
                    Condition.zones(EnumSet.of(Zone.GLACITE_MINESHAFTS), "In Glacite Mineshafts")));
    // attributes.stacks keys are NEU attribute_shards.json internal names (ATTRIBUTE_SHARD_<KEY>) in lower case; all 84 keys on
    // the test profile follow it, and every key above and in ECHOES is listed there (docs/references/API-FIELDS.md).
    /**
     * Echo attributes (wiki Cuboa, Wyvern, Iguana and Tiamat shards): Echo of Boxes / Wisdom / Atomized make their family
     * +2% per level stronger (rarity, family); Echo of Echoes makes those echoes +5% per level stronger. Matches the
     * community guide totals: Mossy Box 50 x (1 + 0.2 x 1.5) = 65, Cavern Wisdom 6.5, Atomized 13%.
     */
    record Echo(String id, String name, String rarity, Set<String> family) {}
    static final List<Echo> ECHOES = List.of(
            new Echo("echo_of_boxes", "Echo of Boxes (Cuboa Shard)", "UNCOMMON", Set.of("mossy_box", "gold_box")),
            new Echo("echo_of_wisdom", "Echo of Wisdom (Wyvern Shard)", "LEGENDARY", Set.of("cavern_wisdom")),
            new Echo("echo_of_atomized", "Echo of Atomized (Iguana Shard)", "EPIC", Set.of("atomized_mithril", "atomized_crystals", "atomized_glacite")));
    static final String ECHO_OF_ECHOES = "echo_of_echoes";
    static int attributeLevel(JsonElement stacks, String id, String rarity) {
        int shards = (int)number(at(stacks, id), 0), level = 0;
        for (int needed : ATTRIBUTE_SHARDS.get(rarity)) if (shards >= needed) level++;
        return level;
    }
    /** The echo that boosts an attribute (null if none). */
    static Echo echoFor(String attribute) { return ECHOES.stream().filter(e -> e.family.contains(attribute)).findFirst().orElse(null); }
    /** Extra share the echoes add to an attribute's value: echo x (1 + Echo of Echoes); 0 when not boosted. */
    static double echoBonus(JsonElement stacks, String attribute) {
        Echo echo = echoFor(attribute);
        if (echo == null || !stacks.isJsonObject()) return 0;
        return .02 * attributeLevel(stacks, echo.id, echo.rarity) * (1 + .05 * attributeLevel(stacks, ECHO_OF_ECHOES, "LEGENDARY"));
    }
    /** Which powder sources the attribute boosts (wiki Powder Sources tables). */
    static final Map<String, String> ATTRIBUTE_SCOPE = Map.of("atomized_mithril", "Boosts Mithril Powder from mining Mithril only.",
            "atomized_crystals", "Boosts Gemstone Powder from Gemstones, Treasure Chests and the Crystal Nucleus.",
            "atomized_glacite", "Boosts Glacite Powder; which sources is not confirmed on the wiki.");

    /**
     * Essence shop perks (`player_data.perks`, wiki Essence Shops/Gold, /Diamond, /Ice, Fossil Essence, Crimson Essence): linear
     * per level unless {@code values} lists each level's total; {@code where}: the perk's condition (most: on Mining Islands).
     */
    record Essence(String id, String name, Stat stat, double perLevel, int maxLevel, double[] values, Condition where) {
        Essence(String id, String name, Stat stat, double perLevel, int maxLevel) { this(id, name, stat, perLevel, maxLevel, null, ISLANDS); }
        double at(int level) { return level <= 0 ? 0 : values != null ? values[Math.min(level, values.length) - 1] : perLevel * level; }
    }
    static final Condition ISLANDS = Condition.zones(StatContext.MINING_ISLANDS, "On Mining Islands");
    static final List<Essence> ESSENCE = List.of(
            new Essence("eager_miner", "Eager Miner (Gold Essence)", Stat.MINING_SPEED, 10, 10),
            new Essence("rhinestone_infusion", "Rhinestone Infusion (Diamond Essence)", Stat.GEMSTONE_FORTUNE, 2, 10),
            new Essence("dwarven_training", "Dwarven Training (Gold Essence)", Stat.MINING_WISDOM, 0, 3, new double[]{3, 5, 10}, ISLANDS),
            new Essence("frozen_skin", "Frozen Skin (Ice Essence)", Stat.COLD_RESISTANCE, 1, 5, null, Condition.NONE),
            new Essence("dwarven_expertise", "Dwarven Expertise (Fossil Essence)", Stat.DWARVEN_METAL_FORTUNE, 2, 10),
            // Stacks per Glacial mob killed, up to 10, until you leave the Mineshaft: +0.5 per kill per level (2.5 at level 5).
            new Essence("cut_loose", "Cut Loose (Fossil Essence)", Stat.COLD_RESISTANCE, 5, 5, null,
                    Condition.unchecked("Per Glacial mob killed in the current Glacite Mineshaft, shown at the 10-kill cap")),
            new Essence("fungus_fortuna", "Fungus Fortuna (Crimson Essence)", Stat.MINING_FORTUNE, 2, 10, null, Condition.unchecked("While mining Mycelium")),
            new Essence("harena_fortuna", "Harena Fortuna (Crimson Essence)", Stat.MINING_FORTUNE, 2, 10, null, Condition.unchecked("While mining Red Sand")));
    // player_data.perks keys are the NEU essenceshops.json keys (all nine shop perks on the test profile match; API-FIELDS.md).
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
            new PetStat("MOLE", "LEGENDARY", "LEGENDARY", Stat.MINING_SPEED, 1.5, "", false, Condition.NONE),
            // Wiki Heat/Cold Resistance and Mining Wisdom source tables; NEU petnums for the powder perks (level 1 and 100 linear).
            new PetStat("BAL", "EPIC", Stat.HEAT_RESISTANCE, 1.5),
            new PetStat("GLACITE_GOLEM", "COMMON", "LEGENDARY", Stat.COLD_RESISTANCE, .1, "", false, Condition.NONE),
            new PetStat("SILVERFISH", "RARE", "RARE", Stat.MINING_WISDOM, .2, "Experienced Burrower", false, Condition.NONE),
            new PetStat("SILVERFISH", "EPIC", "EPIC", Stat.MINING_WISDOM, .25, "Experienced Burrower", false, Condition.NONE),
            new PetStat("SILVERFISH", "LEGENDARY", "MYTHIC", Stat.MINING_WISDOM, .3, "Experienced Burrower", false, Condition.NONE),
            new PetStat("MITHRIL_GOLEM", "LEGENDARY", "MYTHIC", Stat.MITHRIL_POWDER, .2, "The Smell Of Powder", false, Condition.NONE),
            new PetStat("SCATHA", "LEGENDARY", "MYTHIC", Stat.GEMSTONE_POWDER, .2, "Bejeweled Eyes", false, Condition.NONE),
            new PetStat("GLACITE_GOLEM", "COMMON", "COMMON", Stat.GLACITE_POWDER, .1, "Powder-powered", false, Condition.NONE),
            new PetStat("GLACITE_GOLEM", "UNCOMMON", "RARE", Stat.GLACITE_POWDER, .15, "Powder-powered", false, Condition.NONE),
            new PetStat("GLACITE_GOLEM", "EPIC", "LEGENDARY", Stat.GLACITE_POWDER, .2, "Powder-powered", false, Condition.NONE),
            // Wiki pet pages (level 1 and 100 linear). Per-corpse, per-player and Crimson Isle perks need state the API lacks.
            new PetStat("GLACITE_GOLEM", "LEGENDARY", "LEGENDARY", Stat.PRISTINE, .01, "Frozen Perfection", false,
                    Condition.unchecked("Per Frozen Corpse looted in the current Glacite Mineshaft (one corpse shown)")),
            new PetStat("GOBLIN", "LEGENDARY", Stat.ORE_FORTUNE, 1),
            new PetStat("GOBLIN", "LEGENDARY", "MYTHIC", Stat.MINING_SPEED, 2.5, "Grunt Work", false, Condition.target(Target.ORE, "While mining Ores")),
            new PetStat("GOBLIN", "LEGENDARY", "MYTHIC", Stat.MINING_SPREAD, 1, "Fetid Thief", false, Condition.unchecked(MINES_OF_DIVAN)),
            new PetStat("SNAIL", "LEGENDARY", "MYTHIC", Stat.MINING_SPREAD, 4, "Slimy Reach", false, Condition.target(Target.BLOCK, "While mining blocks")),
            new PetStat("KUUDRA", "EPIC", "MYTHIC", Stat.MINING_FORTUNE, 1, "Kuudra Fortune", false, Condition.unchecked("On the Crimson Isle")),
            new PetStat("MAMMOTH", "LEGENDARY", Stat.COLD_RESISTANCE, .1),
            new PetStat("MAMMOTH", "LEGENDARY", "MYTHIC", Stat.MINING_FORTUNE, .3, "Corpse Crusher", false,
                    Condition.unchecked("Per Frozen Corpse looted in the current Glacite Mineshaft (one corpse shown)")),
            new PetStat("BABY_YETI", "MYTHIC", Stat.COLD_RESISTANCE, .1),
            new PetStat("PENGUIN", "LEGENDARY", Stat.COLD_RESISTANCE, .15),
            new PetStat("PENGUIN", "LEGENDARY", "MYTHIC", Stat.COLD_RESISTANCE, .01, "Chilly Reception", false,
                    Condition.unchecked("Per player within 30 blocks, up to 10 (one player shown)")));
    /** Wiki Hephaestus Relic (Legendary pet item): +50% to the pet's stats; counted on base stats (the community guide totals match). */
    static final String HEPHAESTUS = "HEPHAESTUS_RELIC";
    /** Wiki Snail Pet, Slow and Steady (Rare+): +1 Block Fortune per N Speed above 100; N at level 100 by rarity, -0.03 per level. */
    static final Map<String, Double> SNAIL_SPEED_PER_FORTUNE = Map.of("RARE", 3.0, "EPIC", 3.0, "LEGENDARY", 2.0, "MYTHIC", 2.0);
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
        attributes(b, member, loadout);
        essence(b, member);
        consumables(b, member);
        ability(b, member, loadout);
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
        if (l.tool != null) toolExtras(b, l.tool);
        if (l.accessories.stream().anyMatch(i -> i.id.equals("HASTE_ARTIFACT")) && b.stat == Stat.MINING_SPEED)
            b.contributions.add(new Contribution("Accessory", "Haste III (Haste Artifact effect)", b.stat, 150.0, 150.0,
                    "Only when no stronger Haste effect is active", State.SITUATIONAL, Confidence.KNOWN,
                    "Potion-style effect on Mining System islands; not part of the item's stat line."));
    }

    private static final Pattern TANK_LINE = Pattern.compile("^-([0-9.]+)% Pickaxe Ability Cooldown$");
    /**
     * Tool sources outside the stat lines: the fuel tank's Pickaxe Ability Cooldown reduction (wiki Drill Parts, shown as
     * "-4% Pickaxe Ability Cooldown" in the tank paragraph) and an Amber Power Scroll (wiki Power Scrolls: +30 Mining Speed for
     * 5 s when the RIGHT CLICK ability is used, not boosted by Mining Speed Boost).
     */
    private static void toolExtras(Breakdown b, Item tool) {
        boolean drill = tool.category.equals("DRILL");
        if (b.stat == Stat.PICKAXE_ABILITY_COOLDOWN && drill) {
            String tank = string(at(tool.extra, "drill_part_fuel_tank"), "");
            Double rule = GearUpgrades.FUEL_TANKS.get(tank), shown = null;
            for (String line : tool.lore) { Matcher m = TANK_LINE.matcher(line); if (m.matches()) shown = Double.parseDouble(m.group(1)); }
            if (tank.isEmpty()) b.contributions.add(new Contribution("Tool", "Fuel tank (drill part)", b.stat, 0.0, 10.0, "",
                    State.MISSING, Confidence.KNOWN, "No fuel tank; the Perfectly-Cut Fuel Tank gives -10%."));
            else if (rule == null) b.contributions.add(new Contribution("Tool", GearUpgrades.PART_NAMES.getOrDefault(tank, tank), b.stat, shown, 10.0, "",
                    State.COUNTED, shown == null ? Confidence.UNKNOWN : Confidence.KNOWN, "Fuel tank not in the Drill Parts table; value from the lore if shown."));
            else b.contributions.add(new Contribution("Tool", GearUpgrades.PART_NAMES.getOrDefault(tank, tank) + " (drill part)", b.stat, rule * 100, 10.0, "",
                    State.COUNTED, Confidence.KNOWN, shown != null && Math.abs(shown - rule * 100) < 1e-9 ? "Matches the drill lore." : "Wiki Drill Parts value."));
        }
        boolean ability = tool.lore.stream().anyMatch(s -> s.startsWith("Ability:") && s.contains("RIGHT CLICK"));
        if (b.stat == Stat.MINING_SPEED && ability) {
            String scroll = string(at(tool.extra, "power_ability_scroll"), "");
            if (scroll.equals("AMBER_POWER_SCROLL")) b.contributions.add(new Contribution("Tool", "Amber Power Scroll", b.stat, 30.0, 30.0,
                    "For 5 s after using the pickaxe ability", State.SITUATIONAL, Confidence.KNOWN, "Not boosted by Mining Speed Boost; not part of the stat total."));
            else b.contributions.add(new Contribution("Tool", "Amber Power Scroll", b.stat, 0.0, 30.0, "For 5 s after using the pickaxe ability",
                    State.MISSING, Confidence.KNOWN, scroll.isEmpty() ? "No power scroll on this tool." : "This tool holds " + scroll + ", which gives no Mining Speed."));
        }
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
            if (percent && b.stat.percent) {
                // Powder buffs are percentages themselves ("Grants +20% Mithril Powder"), so the value is the stat.
                add(b, category, source, value, null, zoneCondition(text), Confidence.KNOWN, "Lore bonus.");
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
            else if (drill) add(b, category, header + " (drill part)", value, partMaximum(header, b.stat, value), Condition.NONE, Confidence.KNOWN,
                    "Listed in the drill lore outside the stat line total.");
            else b.contributions.add(new Contribution(category, source, b.stat, null, null, "", State.COUNTED, Confidence.UNKNOWN,
                    "Lore paragraph lists +" + amount.group(1) + "; not verified to be outside the stat line total."));
        }
    }

    /**
     * Best value of the drill part's slot for a stat (wiki Drill Parts via GearUpgrades.ENGINES / MODULES): any engine for an
     * engine paragraph, any stat module for a module paragraph; null for other paragraphs.
     */
    private static Double partMaximum(String header, Stat stat, double value) {
        int index = stat == Stat.MINING_SPEED ? 0 : stat == Stat.MINING_FORTUNE ? 1 : -1;
        if (index < 0) return null;
        Collection<double[]> slot = header.contains("Drill Engine") ? GearUpgrades.ENGINES.values()
                : header.contains("Omelette") || header.contains("Egg Part") || header.contains("Seasoning") ? GearUpgrades.MODULES.values() : null;
        return slot == null ? null : Math.max(value, slot.stream().mapToDouble(v -> v[index]).max().orElse(0));
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
            if (s.perk.isEmpty() && pet.heldItem().equals(HEPHAESTUS))
                add(b, "Pet", "Hephaestus Relic (pet item)", value * .5, s.perLevel * 50, s.condition, Confidence.KNOWN, "+50% of the pet's base stat.");
        }
        if (b.stat == Stat.BLOCK_FORTUNE && pet.type().equals("SNAIL") && SNAIL_SPEED_PER_FORTUNE.containsKey(pet.tier()))
            b.contributions.add(new Contribution("Pet", pet.label() + ": Slow and Steady", b.stat, null, null,
                    "+1 per " + fmt(SNAIL_SPEED_PER_FORTUNE.get(pet.tier()) + .03 * (100 - pet.level())) + " Speed above 100",
                    State.SITUATIONAL, Confidence.UNKNOWN, "Your Speed stat is not in the API."));
        String held = pet.heldItem();
        if (held.equals(HEPHAESTUS)) return;
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
            add(b, "HOTM", perk.name + " " + level + (omelette ? " (+1 Blue Cheese)" : ""), perk.at(effective), perk.at(perk.maxLevel + (omelette ? 1 : 0)), condition, Confidence.KNOWN,
                    omelette ? "Blue Cheese Goblin Omelette on the drill: +1 level." : "");
        }
        Double frontLoaded = FRONT_LOADED.get(b.stat);
        if (frontLoaded != null) {
            if (number(at(nodes, "front_loaded"), 0) <= 0) b.contributions.add(new Contribution("HOTM", "Front Loaded", b.stat, 0.0, frontLoaded,
                    "First 2,500 Gemstones mined each day", State.MISSING, Confidence.KNOWN, "Not in this tree."));
            else add(b, "HOTM", "Front Loaded", frontLoaded, frontLoaded, Condition.unchecked("First 2,500 Gemstones mined each day"),
                    Confidence.KNOWN, "The day's count is not in the API.");
        }
        Double mayhem = MINESHAFT_MAYHEM.get(b.stat);
        if (mayhem != null) {
            String text = "One random buff on entering a Glacite Mineshaft";
            if (number(at(nodes, "mineshaft_mayhem"), 0) <= 0) b.contributions.add(new Contribution("HOTM", "Mineshaft Mayhem", b.stat, 0.0, mayhem,
                    text, State.MISSING, Confidence.KNOWN, "Not in this tree."));
            else add(b, "HOTM", "Mineshaft Mayhem", mayhem, mayhem, Condition.unchecked(text), Confidence.KNOWN,
                    "Which buff you rolled is not in the API.");
        }
        if (b.stat == Stat.PICKAXE_ABILITY_COOLDOWN || b.stat.percent) {
            int core = (int)number(at(nodes, "core_of_the_mountain"), 0);
            // Core of the Mountain rewards (wiki Tier 5 template): ability level at 2, base powder per block at 4 / 6 / 8.
            if (b.stat == Stat.PICKAXE_ABILITY_COOLDOWN) b.notes.add("Core of the Mountain " + core + "/10 (" + treeLabel(tree) + "): "
                    + (core >= 2 ? "+1 pickaxe ability level" : "no extra pickaxe ability level yet (level 2)") + "; it changes the ability's boost and duration, not its cooldown.");
            else if (b.stat == Stat.ALL_POWDER) b.notes.add("Core of the Mountain " + core + "/10 (" + treeLabel(tree) + ") adds base powder per block mined: "
                    + "+1 Mithril (level 4) " + (core >= 4 ? "yes" : "no") + ", +2 Gemstone (level 6) " + (core >= 6 ? "yes" : "no")
                    + ", +3 Glacite (level 8) " + (core >= 8 ? "yes" : "no") + ". Base amounts, not part of this percentage.");
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

    private static void attributes(Breakdown b, JsonObject member, Loadout l) {
        JsonElement stacks = at(member, "attributes", "stacks");
        for (Attribute a : ATTRIBUTES) {
            if (a.stat != b.stat) continue;
            boolean freezing = a.id.equals("freezing_spread");
            Double maximum = freezing ? null : a.perLevel * 10;
            if (!stacks.isJsonObject()) {
                b.contributions.add(new Contribution("Attribute", a.name, b.stat, null, maximum, a.condition.text(),
                        State.COUNTED, Confidence.UNKNOWN, "Attribute data missing from the API response."));
                continue;
            }
            int shards = (int)number(at(stacks, a.id), 0), level = attributeLevel(stacks, a.id, a.rarity);
            String scope = ATTRIBUTE_SCOPE.containsKey(a.id) ? " " + ATTRIBUTE_SCOPE.get(a.id) : "";
            if (level == 0) {
                b.contributions.add(new Contribution("Attribute", a.name, b.stat, 0.0, maximum, a.condition.text(),
                        State.MISSING, Confidence.KNOWN, "No shards syphoned." + scope));
                continue;
            }
            Condition condition = a.condition;
            if (a.id.equals("greedy_goblin") && l.pet != null)
                condition = l.pet.type().equals("GOBLIN") ? Condition.NONE : Condition.unchecked("With a Goblin pet active (not this loadout's pet)");
            if (freezing) {
                // +0.5 Gemstone Spread per (30 - level) Cold Resistance: every 29 at level 1, every 20 at level 10.
                int per = 30 - level;
                Breakdown cold = compute(member, l, b.context, Stat.COLD_RESISTANCE);
                Double value = cold.complete() ? .5 * Math.floor(cold.total() / per) : null;
                add(b, "Attribute", a.name + " " + level, value, null, condition, value == null ? Confidence.UNKNOWN : Confidence.KNOWN,
                        "+0.5 per " + per + " Cold Resistance" + (value == null ? "; Cold Resistance is incomplete." : " (" + fmt(cold.total()) + " counted here)."));
                continue;
            }
            add(b, "Attribute", a.name + " " + level, a.perLevel * level, maximum, condition, Confidence.KNOWN,
                    shards + " shards syphoned." + scope);
            Echo echo = echoFor(a.id);
            if (echo == null) continue;
            double bonus = echoBonus(stacks, a.id);
            String text = echo.name + " on " + a.name.replaceFirst(" \\(.*", "");
            if (bonus <= 0) b.contributions.add(new Contribution("Attribute", text, b.stat, 0.0, a.perLevel * level * .3, condition.text(),
                    State.MISSING, Confidence.KNOWN, "Makes the attribute up to 20% stronger (30% with Echo of Echoes X)."));
            else add(b, "Attribute", text, a.perLevel * level * bonus, a.perLevel * level * .3, condition, Confidence.KNOWN,
                    String.format(Locale.ROOT, "+%.1f%% from the echoes (Echo of Echoes included).", bonus * 100));
        }
    }

    private static void essence(Breakdown b, JsonObject member) {
        JsonElement perks = at(member, "player_data", "perks");
        for (Essence e : ESSENCE) {
            if (e.stat != b.stat) continue;
            double maximum = e.at(e.maxLevel);
            Condition where = e.where;
            if (!perks.isJsonObject()) {
                b.contributions.add(new Contribution("Essence", e.name, b.stat, null, maximum, where.text(),
                        State.COUNTED, Confidence.UNKNOWN, "Essence perks missing from the API response."));
                continue;
            }
            int level = (int)number(at(perks, e.id), 0);
            if (level == 0) b.contributions.add(new Contribution("Essence", e.name, b.stat, 0.0, maximum, where.text(),
                    State.MISSING, Confidence.KNOWN, "Not purchased."));
            else add(b, "Essence", e.name.replaceFirst(" \\(", " " + level + " ("), e.at(level), maximum, where, Confidence.KNOWN,
                    e.values != null ? "Level " + level + " of " + e.maxLevel + "." : fmt(e.perLevel) + " per level.");
        }
    }
    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long)v) : String.valueOf(v); }

    /**
     * Permanent consumables the API exposes. Reaper Pepper (wiki): +1 Cold Resistance per pepper, up to 5. Key
     * `player_data.reaper_peppers_eaten` as read by Skyblocker and NEU; the API omits it until a pepper is eaten.
     */
    private static void consumables(Breakdown b, JsonObject member) {
        if (b.stat != Stat.COLD_RESISTANCE) return;
        if (!at(member, "player_data").isJsonObject()) b.contributions.add(new Contribution("Consumable", "Reaper Pepper", b.stat, null, 5.0, "",
                State.COUNTED, Confidence.UNKNOWN, "Player data missing from the API response."));
        else {
            int eaten = (int)Math.min(5, number(at(member, "player_data", "reaper_peppers_eaten"), 0));
            b.contributions.add(new Contribution("Consumable", "Reaper Pepper x" + eaten, b.stat, (double)eaten, 5.0, "",
                    eaten > 0 ? State.COUNTED : State.MISSING, Confidence.KNOWN, "+1 per pepper eaten, up to 5."));
        }
    }

    /** Mining Speed Boost cooldown before reductions (wiki Mining Speed Boost: 120 s at every level). */
    public static final double MSB_COOLDOWN = 120;
    /**
     * Pickaxe ability notes and the Maniac Miner Breaking Power (wiki Breaking Power: +1 while the ability is active,
     * not permanent). The selected ability is `skill_tree.selected_ability.mining`.
     */
    private static void ability(Breakdown b, JsonObject member, Loadout l) {
        String selected = string(at(member, "skill_tree", "selected_ability", "mining"), "");
        if (b.stat == Stat.BREAKING_POWER && selected.equals("maniac_miner"))
            b.contributions.add(new Contribution("HOTM", "Maniac Miner (pickaxe ability)", b.stat, 1.0, 1.0, "While the ability is active",
                    State.SITUATIONAL, Confidence.KNOWN, "Not permanent; not part of the stat total."));
        if (b.stat != Stat.PICKAXE_ABILITY_COOLDOWN) return;
        MethodRates.Ability lore = MethodRates.ability(l.tool);
        b.notes.add("Selected pickaxe ability: " + (selected.isEmpty() ? "not in the API" : selected.replace('_', ' '))
                + ". Reductions multiply: Mining Speed Boost " + fmt(MSB_COOLDOWN) + " s x (1 - each reduction).");
        if (lore != null && lore.cooldown() != null) b.notes.add("The tool lore shows " + fmt(lore.cooldown()) + " s; the lore of a pickaxe without a fuel tank shows the same,"
                + " so it includes the attribute but not the fuel tank. The rate models use the ledger value.");
    }

    /**
     * The tool's pickaxe ability for the rate models, with the Mining Speed Boost cooldown taken from the Pickaxe Ability
     * Cooldown ledger (120 s x product of (1 - counted reductions)) instead of the lore, which leaves out the fuel tank.
     * Falls back to the lore when the ledger is incomplete or the ability is not Mining Speed Boost.
     */
    public static MethodRates.Ability effectiveAbility(JsonObject member, Loadout l, StatContext context) {
        MethodRates.Ability lore = MethodRates.ability(l.tool);
        if (lore == null || !lore.speedBoost() && !lore.name().equals(MethodRates.MANIAC)) return lore;
        Breakdown cooldown = compute(member, l, context, Stat.PICKAXE_ABILITY_COOLDOWN);
        Double ledger = cooldown.complete() ? MSB_COOLDOWN * (1 - cooldown.total() / 100) : null;
        if (lore.speedBoost()) return ledger == null ? lore : lore.withCooldown(ledger);
        // Maniac Miner (wiki): level 1, +1 at Core of the Mountain 2 and +1 with a Blue Cheese omelette, up to 3; 120 s cooldown like every ability.
        if (lore.maniac()) return ledger == null ? lore : lore.withCooldown(ledger);
        JsonElement nodes = at(member, "skill_tree", "nodes", l.hotmTree == null ? "mining" : l.hotmTree);
        if (!nodes.isJsonObject()) nodes = at(member, "mining_core", "nodes");
        int level = Math.min(3, 1 + (number(at(nodes, "core_of_the_mountain"), 0) >= 2 ? 1 : 0) + (blueCheese(l.tool) ? 1 : 0));
        double c = ledger != null ? ledger : lore.cooldown() != null ? lore.cooldown() : MSB_COOLDOWN;
        return MethodRates.maniacMiner(level, c);
    }

    /** Potion effects per level by `active_effects` key (wiki Haste, Spelunker, Cold Resistance and Mining XP Boost potions). */
    static final Map<String, Map<Stat, double[]>> EFFECTS = Map.of(
            "haste", Map.of(Stat.MINING_SPEED, new double[]{50, 100, 150, 200}),
            "spelunker", Map.of(Stat.MINING_FORTUNE, new double[]{5, 10, 15, 20, 25}),
            "cold_resistance", Map.of(Stat.COLD_RESISTANCE, new double[]{2.5, 5, 7.5, 10}),
            "mining_xp_boost", Map.of(Stat.MINING_WISDOM, new double[]{5, 10, 20}));
    /**
     * Effects the API reports (`active_effects`, `paused_effects`, `temp_stat_buffs`). Known potion keys map to their
     * stat (key names assumed from the potion names; the observed profile reported none). Never counted.
     */
    private static void temporary(Breakdown b, JsonObject member) {
        for (String field : List.of("active_effects", "paused_effects", "temp_stat_buffs")) {
            JsonElement effects = at(member, "player_data", field);
            if (!effects.isJsonArray()) continue;
            String where = field.equals("paused_effects") ? "Paused, not active now" : "";
            for (JsonElement effect : effects.getAsJsonArray()) {
                String name = effect.isJsonObject() ? string(at(effect, "effect"), string(at(effect, "key"), effect.toString())) : string(effect, effect.toString());
                Map<Stat, double[]> known = EFFECTS.get(name.toLowerCase(Locale.ROOT));
                if (known != null) {
                    double[] levels = known.get(b.stat);
                    if (levels == null) continue;
                    int level = (int)Math.max(1, Math.min(levels.length, number(at(effect, "level"), 1)));
                    b.contributions.add(new Contribution("Temporary", name + " " + level, b.stat, levels[level - 1], levels[levels.length - 1], where,
                            State.TEMPORARY, Confidence.KNOWN, "Reported by the API (" + field + "); temporary, so not counted."));
                    continue;
                }
                Double amount = null;
                if (effect.isJsonObject() && b.stat.name().equalsIgnoreCase(string(at(effect, "stat"), ""))) amount = number(at(effect, "amount"), Double.NaN);
                else if (effect.isJsonObject() && !string(at(effect, "stat"), "").isEmpty()) continue;
                b.contributions.add(new Contribution("Temporary", name, b.stat, amount == null || amount.isNaN() ? null : amount, null, where,
                        State.TEMPORARY, Confidence.UNKNOWN, "Reported by the API (" + field + "); its " + b.stat.label + " effect is not mapped yet."));
            }
        }
    }

    /** A perk of the current mayor or minister, from the official election resource (v2/resources/skyblock/election). */
    public record MayorPerk(String mayor, String name, String description, boolean minister) {}
    /**
     * Mayor and minister perks as TEMPORARY sources: amounts are read from the perk description ("Gain +25 Mining Wisdom,
     * +25% Mining Fortune"). Since 2026-08-25 the Mining Fiesta lasts the whole mayorship (wiki Cole), so it is active
     * whenever the perk is. Applies on public islands; never counted (owner decision 2026-10-26).
     */
    public static void mayor(Breakdown b, List<MayorPerk> perks) {
        if (perks == null) return;
        for (MayorPerk p : perks) {
            if (p.name().equals("Mining Fiesta")) b.contributions.replaceAll(c -> c.state() == State.SITUATIONAL && c.condition().equals(FIESTA)
                    ? new Contribution(c.category(), c.source(), c.stat(), c.value(), c.maximum(), "During the Mining Fiesta (active now)", State.TEMPORARY,
                    c.confidence(), "The Fiesta runs now (election data); temporary buffs are not counted.") : c);
            String text = GearReader.clean(p.description());
            Matcher m = Pattern.compile("\\+([0-9][0-9,]*(?:\\.[0-9]+)?)(%?)[^A-Za-z0-9+%]*" + Pattern.quote(b.stat.label) + "\\b").matcher(text);
            if (!m.find()) continue;
            boolean percent = !m.group(2).isEmpty() && !b.stat.percent;
            double value = Double.parseDouble(m.group(1).replace(",", ""));
            if (p.name().equals("Mining Fiesta")) b.contributions.removeIf(c -> c.state() == State.UNTRACKABLE && c.source().equals("Mining Fiesta"));
            b.contributions.add(new Contribution(percent ? Breakdown.ADDITIVE_PERCENT : "Mayor", (p.minister() ? "Minister " : "Mayor ") + p.mayor() + ": " + p.name(),
                    b.stat, value, value, "On public islands during the term", State.TEMPORARY, Confidence.KNOWN,
                    "From the official election data; temporary buffs are not counted."));
        }
    }

    /**
     * God Potion: Spelunker V (+25 Mining Fortune) and Haste IV (+200 Mining Speed) per the wiki God
     * Potion, Spelunker Potion and Haste Potion pages. Not in the API, so it is an assumption the
     * context can switch off. Booster Cookie (profile.cookie_buff_active) grants Wisdom and Magic
     * Find only, so it is listed at zero.
     */
    private static void assumedBuffs(Breakdown b, JsonObject member) {
        if (b.stat != Stat.MINING_FORTUNE && b.stat != Stat.MINING_SPEED && b.stat != Stat.MINING_WISDOM) return;
        // God Potion holds Spelunker V, Haste IV and Mining XP Boost III (wiki God Potion, Mining Wisdom source table).
        double god = switch (b.stat) { case MINING_FORTUNE -> 25; case MINING_SPEED -> 200; default -> 20; };
        String effect = switch (b.stat) { case MINING_FORTUNE -> "Spelunker V"; case MINING_SPEED -> "Haste IV"; default -> "Mining XP Boost III"; };
        Condition condition = b.context.godPotion() ? Condition.NONE : Condition.unchecked("Requires an active God Potion");
        add(b, "Buff", "God Potion (" + effect + ")", god, god, condition, Confidence.MANUAL,
                "Assumed active; the API does not report potion effects.");
        JsonElement cookie = at(member, "profile", "cookie_buff_active");
        boolean active = "true".equals(string(cookie, "false"));
        // Booster Cookie: +25 Mining Wisdom (wiki Mining Wisdom); read from the API (owner decision 2026-10-26).
        double value = b.stat == Stat.MINING_WISDOM ? 25 : 0;
        if (!cookie.isJsonNull())
            b.contributions.add(new Contribution("Buff", "Booster Cookie", b.stat, active ? value : 0.0, value, "",
                    active ? State.COUNTED : State.MISSING, Confidence.KNOWN,
                    value > 0 ? (active ? "Active (profile.cookie_buff_active)." : "Not active.") : "Grants Wisdom and Magic Find, no " + b.stat.label + "."));
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
            case MINING_WISDOM -> Map.of(
                "Celestial Mason Jar", "+3 (mixin); not observed in the API.",
                "Mining XP Boost potion", "+5 / +10 / +20; counted only as part of the assumed God Potion.",
                "Mining Fiesta", "+25 Mining Wisdom during Cole's Mining Fiesta; temporary, not counted.");
            case HEAT_RESISTANCE -> Map.of("Lanterns / Will-o'-wisp", "+5 (Titanium), +10 (Glacite), +15 (Will-o'-wisp) while deployed nearby.");
            case COLD_RESISTANCE -> Map.of(
                "Lanterns / Will-o'-wisp", "+5 (Glacite) or +10 (Will-o'-wisp) while deployed nearby.",
                "Cold Resistance potion", "+2.5 per level up to +10; potions are not reported unless listed under Temporary.",
                "Corpse-Flavored Century Cake", "+1; whether the API reports it is not verified.",
                "Cut Loose (Fossil Essence)", "+0.5 to +2.5 per Glacial mob killed in a Mineshaft, up to 10 stacks; stacks are not in the API.");
            case ALL_POWDER -> Map.of("2x Powder (mining event)", "Doubles powder while the event runs; temporary, not counted.");
            case MITHRIL_POWDER -> Map.of("Powder Pie", "+15% Mithril Powder for 1 hour; whether the API reports it is not verified.");
            default -> Map.of();
        };
        new TreeMap<>(sources).forEach((name, note) -> b.contributions.add(new Contribution("Untrackable", name, b.stat, null, null, "",
                State.UNTRACKABLE, Confidence.UNKNOWN, note)));
    }
}

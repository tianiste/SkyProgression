# Calculation scope and sources

The calculation engine is independent of Minecraft in `core/`.

## Base-item alternatives

Each catalog group is mutually exclusive: armor slots, tool, equipment slots, accessory
families and lantern. Owning both accessory tiers does not stack their stats.
For a stat query, the best owned item is the one with the highest value of that stat.
For method queries, the first version uses a coarse selection score: relevant fortune
plus relevant spread, plus 100 times Pristine for gems, plus 0.01 times speed for non-instant methods.
This selection heuristic is not an optimizer and may disagree with a fully modeled setup.

Base-item replacement deltas compare target base stats against the selected owned copy's displayed stats (or base stats when unavailable). All alternatives compare
to the same baseline; do not sum every recommendation into one shopping list. Unmet HOTM
requirements appear after available upgrades. Missing prices sort last. Corrupt or absent
inventory data disables recommendations rather than interpreting the inventory as empty.

Cost per stat point = cash outlay / positive stat delta. The outlay uses the cheaper of buying and the item's recipe
("Buy or craft/forge" below). Net cost after selling the replaced item is shown next to it ("Replacement resale
credit"); ranking stays on the outlay. Modifier transfers are not assumed. Supported socket fees are included.

## Drop and rate model

Fortune multiplier = `1 + (Mining Fortune + relevant specialized Fortune) / 100`.
Spread multiplier = `1 + relevant Spread / 100` (assumes extra blocks can be mined).
Pristine multiplier for gems = `1 + 0.79 * Pristine`; otherwise 1.
Combined yield is the product of those multipliers.

Estimated improvement % = `100 * (yield after / yield before - 1)`.
Cost per 1% = `purchase cost / estimated improvement %`.
Speed/cold-only changes are available in stat queries, not assigned imaginary drop gains.
This drop multiplier ranks every method without a rate model (all except MF Gems, MF Ruby, MF Topaz, Grotto,
Coal, Gold, Obsidian, Tungsten, Umber, Shafting, End Stone, Cobblestone, Mycelium and Red Sand, so today only a rate
that cannot be computed, for example Mining Speed 0, falls back to it); those use "Upgrade ranking by rate" below. The Upgrades tab names the model in use.

Items/hour = `3600 * efficiency/100 * active blocks/sec * base drops/block * yield`.
Coins/hour and Coleweight/hour multiply that rate by the user-entered per-item values.
These are gross scenario estimates. They are not session tracking or the bot's proprietary calculations.
The automatic coins/hour of every method is in "Coins per hour across methods" below.

## Data sources

- Hypixel item definitions: <https://api.hypixel.net/v2/resources/skyblock/items>
  (base item snapshot fetched 2026-10-26).
- Hypixel profile/inventory API and encoding: <https://api.hypixel.net/>
- Mining-level XP and collection tiers use official `/v2/resources/skyblock/skills`
  and `/v2/resources/skyblock/collections` snapshots from 2026-10-26.
- Coflnet `GET /api/item/price/{itemTag}/current`:
  <https://sky.coflnet.com/api/index.html>. Quotes request listings at least 120 seconds old.
- Hosted price bundle (SkyProgression API, <https://github.com/tianiste/SkyProgression-API>): Bazaar items from Hypixel's
  public `v2/skyblock/bazaar` (top of the order book: `buy` = lowest sell offer, `sell` = highest buy order, the same
  meaning as Coflnet's figures, Bazaar tax not included), Auction House items and level-100 pets from the Coflnet endpoints
  below, NPC sell prices from the items resource, mayor perks from the election resource. The local companion still asks
  Coflnet for every item. Either way an ID without a quote is left out and reads as unknown, never 0.

## Where calculations run

Since 2026-10-27 every report (stat ledger, method comparison, start with, upgrades, rates) is calculated in the mod by
`core/` on its worker threads (`PlayerData` holds the decoded profile and each profile's owned items). The backend, the
hosted API or the local companion, only passes through the raw Hypixel profiles response (`/profile`, cached 5 minutes,
older copies served while refreshing), resolves names (`/uuid`), serves the price bundle (`/prices`) and modifier-aware
sale quotes (`/resale`). The endpoint names below that used to be companion endpoints (`/stats`, `/compare`, `/start`) now
name the mod's report keys; the wire formats are the same classes.
- Pristine expected-drop model:
  <https://hypixelskyblock.minecraft.wiki/w/Pristine>.
- Lantern increments were supplied in the user's Mining Cat screenshot. They are explicitly
  modeled as mutually exclusive deployed bonuses and should be rechecked after game patches.
- Fabric 26.1 migration: <https://fabricmc.net/2026/03/14/261.html>.

Mining Cat and SkyCompass inspired the requested workflow. Their code and computed profile
output are not bundled, scraped as a dataset, or used as an undocumented runtime dependency.

## Extending the mod

`core/src/main/resources/mining-items.json` is generated by `./gradlew :core:extractCatalog` from the official
items resource (see "Item catalog" below); hand-checked entries are kept by the generator. The reforge table is
generated by `./gradlew :core:extractNeu`. A complete gear evaluator needs modifier parsing, condition-aware
stat sources, mutually compatible slots, actual equipped loadouts, ability uptime,
break-speed thresholds, pet math, craft/upgrade recipes and method-specific rates.
Those are separate work from bumping the Minecraft version.

For Minecraft updates: update `gradle.properties`, adjust the client adapter if needed,
run `./gradlew build :service:distZip`, then test in-game. Do not widen the supported
Minecraft version range before testing it. The core and service have no Minecraft imports.

## Individual gear upgrades (0.2.0)

UUID deduplication prevents the same gear copy appearing twice across containers.
Displayed lore totals replace base values rather than adding modifiers a second time.
Lore can contain context-dependent values: it is not a complete effective stat calculator.
Fortune, Efficiency/Silex, Prismatic and Polarvoid proposals use incremental stat deltas.
Gemstone deltas use quality and item rarity, including recombobulation and accessory scaling.
Occupied flexible slots retain their current gem type. Unknown qualities are skipped.
Costs deduct owned materials independently per alternative; no shared shopping-list allocation.
Slot unlock ingredients and coins come from official Hypixel item metadata. Removal fees
are included; a removed gem is given back to the player (wiki Gemstone, <https://hypixel-skyblock.fandom.com/wiki/Gemstone>:
"this will return the gemstone to the player") and is credited at its sale value in the net cost. Missing ingredient prices make
the whole cost unknown. Enchantment XP costs are not converted to coins.

Rule references:
- https://hypixelskyblock.minecraft.wiki/w/Enchantment_Fortune
- https://hypixelskyblock.minecraft.wiki/w/Enchantment_Efficiency
- https://hypixelskyblock.minecraft.wiki/w/Enchantment_Prismatic
- https://hypixelskyblock.minecraft.wiki/w/Silex
- https://hypixelskyblock.minecraft.wiki/w/Polarvoid_Book
- https://hypixelskyblock.minecraft.wiki/w/Gemstone
- https://hypixelskyblock.minecraft.wiki/w/Gemstone_Slot

## Stat breakdown: Mining Fortune and Mining Speed

`core/` `Loadout`, `StatContext`, `StatBreakdown` and `Breakdown` build a source ledger. The mod calculates it as
`/stats?ign=&profile=&method=&gear=best|equipped&tool=&zone=&target=&god=` (`StatsReport`:
ledger per supported stat, equipped and best-owned totals, the gear used, owned tools). Owner decision
2026-10-26: by default the gear is the best owned for the method (`Loadout.bestFor`) and the tool is the
best owned tool (`Loadout.bestTool`). Items are scored like the upgrade ranking: Mining Fortune + the
method's fortune type + Pristine x 100 / Spread + Mining Speed x 0.01 (speed ignored for instant blocks).
Slots are chosen independently, so a set bonus can be broken. Owned storage includes the Loadouts
armor/equipment sets. The best catalogued pet is chosen; an
active pet whose stats are not catalogued is kept because it cannot be compared. The mod's Breakdown tab shows it; automatic totals feed
Upgrades and Rates unless the player typed a manual value for that stat. The zone defaults to the
method's usual location and the block target follows the method's fortune type (`MiningMethod`).

Loadouts:
- **Equipped**: `inv_armor`, `equipment_contents`, the accessory bag and the active pet, plus a tool
  the player selects (the API does not expose the held item). Without a selected tool, the ledger
  carries a MANUAL "Mining tool" entry with no value. Stored items are never equipped implicitly.
- **Best owned**: a separate, hypothetical choice of one copy per slot from all readable storage.
  It also picks one accessory per family and the best catalogued pet. It is not what the player wears.
- Accessories of one catalogued family do not stack. The highest-rarity copy counts; uncatalogued
  accessories are deduplicated by item ID. Accessories carried in the inventory are not counted.

Contribution states: COUNTED, SITUATIONAL (owned, condition not met or not checkable), MISSING,
TEMPORARY (reported by the API) and UNTRACKABLE (never in the API). Confidence: KNOWN, UNKNOWN, MANUAL.
The total sums only COUNTED entries with a known value. An unknown value stays null and is listed
as unresolved, so the total is then a lower bound. A context (zone, block target) decides whether
location or block conditions are met. Conditions the API cannot check are always situational.

Rules:
- Gear: the displayed lore stat line counts once and replaces base catalog stats. On the
  Titanium Drill DR-X655 the line `Mining Fortune: +235 [+5] (+20) (+50)` decomposes as
  120 base + 45 Fortune IV + 20 Auspicious + 50 Perfect Jade = 235. Speed decomposes as
  1600 + 210 Efficiency X + 75 + 100 = 1985. So the bracketed `[+N]` is outside the total. It equals
  the Polarvoid Book bonus (+10 Mining Speed per book up to 5, +5 Mining Fortune once), and is
  counted separately only when the bracket matches that rule. Drill engine and upgrade-module
  paragraphs (e.g. Ruby-Polished Drill Engine +250/+30, Sunny Side omelette +50) are also outside
  the total and count separately. Lore paragraphs with a condition ("while ...") are situational.
  Percentage buffs are summed and applied once to the flat total: total = flat x (1 + sum%/100).
  This was confirmed by the owner's in-game stat menu on 2026-10-26: 1,814.5 flat x (25% Mining
  Fiesta + 1% Auspicious Bonus = 1.26) = 2,286.27. The Auspicious Bonus counts as +1%; other
  percentage lore bonuses stay UNKNOWN. Full Set Bonus paragraphs count once per set, and only when the loadout
  holds every piece.
- Haste Artifact: Haste III effect +150 Mining Speed, situational (only without a stronger Haste).
- Pet: level from `exp` and rarity using the pet XP table (`pet-levels.json`, NotEnoughUpdates-REPO
  `constants/pets.json`). Catalogued pets: Bal (Epic+, 1 Mining Fortune per level), Scatha (Rare+,
  1.25 Fortune / 1 Speed per level), Mithril Golem (0.25 Fortune), Silverfish (0.2 Fortune). Mole
  and Glacite Golem stay unknown because the wiki's level 1 and level 100 values disagree with a
  linear rate. Quick Claw: +1 Fortune and +1 Speed per 2 pet levels. Other held items are unknown.
- HOTM tree: the profile can hold several trees (`mining`, `mining_2`..`mining_5`, from Loadouts) and
  the API does not say which is active. The tree with the most value for the method (Mining Fortune +
  Speed x 0.01, speed ignored for instant blocks) is used, as the Mining Cat developer also does.
- HOTM (`skill_tree.nodes.<tree>`; `toggle_<perk>` false = situational, not counted): Mining Speed
  20/level, Speedy Mineman 40/level, Professional 50+5/level on Gemstones, Strong Arm 5/level on
  Dwarven Metals, Mining Fortune 2/level, Fortunate Mineman 3/level, Rags to Riches 4/level in
  Glacite Mineshafts, Sky Mall +50 Fortune / +100 Speed on the matching random day. The day's buff
  is `mining_core.current_daily_effect`: `dig_speed` counts +100 Speed on Mining Islands (matched
  in game 2026-10-26); the Fortune code is not yet observed, so that buff stays situational.
  Eager Adventurer +4 Mining Speed/level and Steady Hand +0.1 Gemstone Spread/level (max 100 each) in Glacite
  Mineshafts (wiki `Template:Heart_of_the_Mountain/List/Tier_9`, `/Tier_8`; NEU `hotmlayout.json`). Front Loaded
  (+150 Gemstone Fortune, +250 Mining Speed, first 2,500 Gemstones a day, `/Tier_4`) and Mineshaft Mayhem are situational.
  A drill holding the Blue Cheese Goblin Omelette (`drill_part_upgrade_module` = `goblin_omelette_blue_cheese`) adds
  +1 level to every unlocked perk above, past the level cap (wiki <https://hypixelskyblock.minecraft.wiki/w/Blue_Cheese_Goblin_Omelette>:
  "adds +1 level to all unlocked Heart of the Mountain perks; it's able to bypass the level cap"); the maximum shown
  is therefore max level + 1. The wiki lists capped maxima with it, e.g. Mining Speed level 51 (1,020 Mining Speed).
- Mining skill: 4 Mining Fortune per level (level from the official skills resource).
- Collections: "+N Mining Fortune" unlock rewards for reached tiers from the official collections resource.
- Attributes (`attributes.stacks` = shards syphoned, levelled by the wiki rarity table; effect
  linear per level): Fortunate Miner 2.5 Fortune, Rotten Pickaxe 3 Speed, Quartz Speed 10 Speed
  during Weather (situational).
- Essence: Eager Miner (Gold Essence) 10 Mining Speed per level on Mining Islands.
- Buffs (owner decision 2026-10-26): temporary buffs are never counted. Mining Fiesta, Fortunate
  Festivity, weather (Mist +50 Fortune / +250 Speed), lanterns, Better Together and Sky Mall stay
  out of the total, even when today's Sky Mall buff is known (shown as TEMPORARY). The God Potion
  is assumed active and counted: Spelunker V +25 Mining Fortune, Haste IV +200 Mining Speed. It is
  marked MANUAL because the API does not report potions, and `StatContext.withGodPotion(false)`
  switches it off. Booster Cookie (`profile.cookie_buff_active`) is listed at 0: it grants Wisdom
  and Magic Find only.
- `active_effects` / `temp_stat_buffs` entries are TEMPORARY with unknown values (mapping unverified).
- Sources: https://hypixelskyblock.minecraft.wiki/w/God_Potion, https://hypixelskyblock.minecraft.wiki/w/Spelunker_Potion,
  https://hypixelskyblock.minecraft.wiki/w/Haste_Potion, https://hypixelskyblock.minecraft.wiki/w/Booster_Cookie,
  https://hypixelskyblock.minecraft.wiki/w/Weather.

Sources (fetched 2026-10-26):
- https://hypixelskyblock.minecraft.wiki/w/Mining_Fortune and https://hypixelskyblock.minecraft.wiki/w/Mining_Speed (source tables)
- https://hypixelskyblock.minecraft.wiki/w/Heart_of_the_Mountain (perk templates `Template:Heart_of_the_Mountain/List/Tier_N`)
- https://hypixelskyblock.minecraft.wiki/w/Polarvoid_Book, https://hypixelskyblock.minecraft.wiki/w/Quick_Claw,
  https://hypixelskyblock.minecraft.wiki/w/Bal_Pet, https://hypixelskyblock.minecraft.wiki/w/Dwarven_Geode
- https://hypixelskyblock.minecraft.wiki/w/Attributes, https://hypixelskyblock.minecraft.wiki/w/Flitter_Shard,
  https://hypixelskyblock.minecraft.wiki/w/Miner_Zombie_Shard, https://hypixelskyblock.minecraft.wiki/w/Quartzfang_Shard
- https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/blob/master/constants/pets.json (pet XP table)

## Stat breakdown: method stats

Added 2026-10-26. The same ledger, states, loadouts and `/stats` report now cover Gemstone, Ore, Dwarven
Metal and Block Fortune, Mining Spread, Gemstone Spread and Pristine (`StatBreakdown.SUPPORTED`). The
Breakdown tab shows the method's stats (`MiningMethod.relevantStats`: Mining Fortune, Mining Speed, the
method's fortune type, its spread, and Pristine for gems) plus the focused stat. All automatic totals feed
Upgrades and Rates; a value typed in Type Stats overrides that stat. Cold Resistance became automatic on 2026-10-27 (next section).

Context: the Magma Fields are a zone of their own (inside the Crystal Hollows, so "in the Crystal Hollows"
bonuses also apply there) and the default for MF Gems/Ruby/Topaz. Block Fortune methods use the new
`BLOCK` target. Lore paragraphs "while mining Hard Stone / Gemstones / Dwarven Metals" and "ores and blocks"
become block-target conditions (checked against the method's target) instead of always situational.

Best gear, best pet and best HOTM tree use one method weighting (`Loadout.weigh`): Mining Fortune + the
method's fortune type + the method's spread + Pristine x 100 (gems) + Mining Speed x 0.01 (not instant).
Pets are valued in the method's usual zone and target, so Bal's Furnace counts for Magma Fields methods.

Rules (fetched 2026-10-26):
- Gear: the lore stat line for each stat counts once (as for Fortune/Speed). Pristine on the Divan pieces and
  the DR-X655 comes from that line (Topaz gems, Prismatic V = 2.5 inside the drill's 4.5).
- HOTM (`skill_tree.nodes.<tree>`): Gem Lover 20 + 4/level Gemstone Fortune; Old-School 5/level Ore Fortune;
  Blockhead 5/level Block Fortune; Metal Head 5/level Dwarven Metal Fortune; Efficient Miner 3/level Mining
  Spread; Mole 50 + (level - 1) x 350/199 Mining Spread on Hard Stone; Mining Master 0.1/level Pristine.
  Front Loaded (+150 Gemstone Fortune, +250 Mining Speed for the first 2,500 Gemstones each day) is
  situational; node key `front_loaded` (NEU `hotmlayout.json`, see [API-FIELDS](references/API-FIELDS.md#key-verification-2026-10-28)),
  so absence is shown as missing. Sky Mall applies to Fortune/Speed only.
  Sources: <https://hypixelskyblock.minecraft.wiki/w/Template:Heart_of_the_Mountain/List/Tier_3>, `/Tier_4`,
  `/Tier_6`, `/Tier_9`, `/Tier_10`; perk names from <https://hypixelskyblock.minecraft.wiki/w/Gemstone_Fortune>,
  <https://hypixelskyblock.minecraft.wiki/w/Block_Fortune>, <https://hypixelskyblock.minecraft.wiki/w/Ore_Fortune>,
  <https://hypixelskyblock.minecraft.wiki/w/Dwarven_Metal_Fortune>, <https://hypixelskyblock.minecraft.wiki/w/Mining_Spread>,
  <https://hypixelskyblock.minecraft.wiki/w/Pristine>.
- Pets (<https://hypixelskyblock.minecraft.wiki/w/Bal_Pet>, <https://hypixelskyblock.minecraft.wiki/w/Scatha_Pet>,
  <https://hypixelskyblock.minecraft.wiki/w/Armadillo_Pet>): Bal Furnace 0.02 (Epic) / 0.03 (Legendary) Pristine
  per level in the Magma Fields; Scatha Drill Infusion 1 (Epic) / 1.25 (Legendary) Gemstone Fortune per level
  with a Drill (situational with another tool); Armadillo Long Claws 3 Mining Spread per level on Hard Stone
  (Legendary/Mythic). Quick Claw adds to Fortune and Speed only.
- Attributes (wiki rarity tables, <https://hypixelskyblock.minecraft.wiki/w/Attributes/List/Epic>,
  <https://hypixelskyblock.minecraft.wiki/w/Hideonleaf_Shard>): Mossy Box (Hideonleaf, Common) 5 Block Fortune
  per level; Devouring Spread (Gemzie, Epic) 0.25 Gemstone Spread per level; Deep Technique (Bal shard, Epic)
  is a per-block level% chance of +1 Pristine, shown as situational with its expected value (0.01 x level)
  and not added to the stat total (the in-game menu does not include it).
- Essence (<https://hypixelskyblock.minecraft.wiki/w/Essence_Shops/Diamond>): Rhinestone Infusion 2 Gemstone
  Fortune per level on Mining Islands. Under Pressure (Strength), Dwarven Training (Wisdom) and Treasures of
  the Earth (Worm/Scatha chance) give none of these stats.
- God Potion and Booster Cookie lines appear on Fortune/Speed only.
- Not in the API (untrackable, listed with ranges): Dwarven O's cereals (Gemstone Grahams, Ore Oats, Block
  Bran, Metallic Minis; +2 per bowl), Chilled Pristine Potato, Will-o'-wisp Gemstone Spread, Sheer Force (ability uptime, not a stat).
- Added 2026-10-27 (wiki shard, pet and essence pages; API keys confirmed 2026-10-28 against NEU `attribute_shards.json`,
  `essenceshops.json` and `hotmlayout.json`, see [API-FIELDS](references/API-FIELDS.md#key-verification-2026-10-28)): attributes Ultimate DNA +1/level Mining Fortune, Sleepy Spread +1/level
  Spread, Gold Box +5/level Ore Fortune, Royal Riches +5 Speed / +2.5 Ore Fortune per level (Mines of Divan,
  situational), Greedy Goblin +1/level Ore Fortune with a Goblin pet, Freezing Spread +0.5 Gemstone Spread per
  (30 - level) Cold Resistance in Mineshafts; Echo of Boxes / Wisdom / Atomized +2%/level of their family, times
  (1 + 5%/level Echo of Echoes). Essence: Dwarven Expertise +2/level Dwarven Metal Fortune (Mining Islands), Cut
  Loose +0.5 Cold Resistance per kill per level up to 10 kills, Fungus/Harena Fortuna +2/level Mining Fortune on
  Mycelium/Red Sand (situational: the block is not a context target). Pets: Goblin (Ore Fortune 1/level, Grunt Work
  2.5 Speed/level on Ores, Fetid Thief 1 Spread/level in the Mines of Divan), Snail (Slimy Reach 4 Spread/level on
  blocks; Slow and Steady Block Fortune per Speed above 100 is unknown, Speed is not in the API), Kuudra (1 Fortune/level
  on the Crimson Isle), Mammoth / Baby Yeti / Penguin Cold Resistance, Mammoth and Glacite Golem per-corpse perks and
  Penguin per-player Cold Resistance (situational, one shown). Hephaestus Relic: +50% of the pet's base stats.
- Not encoded yet: Lapidary outside the lore line, Front Loaded's day count. Crow: the wiki gives it only weapon
  ability cooldown, so the community claim that it beats Bal for pickaxe cooldown is not encoded.

Fixture check (equipped, DR-X655, MF Gems context) against the owner's in-game menu of 2026-10-26:
Mining Spread 300 = 300, Gemstone Spread 2.5 = 2.5, Dwarven Metal Fortune 5 = 5, Pristine 13.4 = 13.4
before Bal's Furnace (the menu was not taken in the Magma Fields; with it 16.31). Gemstone Fortune 180 vs
195, Ore Fortune 100 vs 110, Block Fortune 115 vs 125: the gaps are unexplained. The flat +10 Ore and +10
Block fit consumed Dwarven O's cereals (untrackable); the +15 Gemstone Fortune matches the unexplained +15
Mining Fortune and may be one temporary source. Not tuned.

## Stat breakdown: other stats, powder buffs, temporary buffs, attainable maximum

Added 2026-10-27. The ledger, states and `/stats` now cover every `Stat`: Mining Wisdom, Breaking Power, Pickaxe Ability
Cooldown, Heat and Cold Resistance (no longer manual) and the powder buffs All / Mithril / Gemstone / Glacite Powder (percent
stats). The Breakdown tab has a **Stats** picker: Method (the method's stats plus the focused one, as before), the groups
General, Gemstone, Blocks & Ores, Dwarven Metal, Resistance, Powder (`StatBreakdown.GROUPS`, SkyCompass's groups as a feature
spec), or All. The comparison and Start with tabs compute only the rate stats (`StatBreakdown.RATE_STATS`).

Sources (wiki pages fetched 2026-10-27 through the wiki API; <https://hypixelskyblock.minecraft.wiki/w/...>):
- Gear: stat lines `Mining Wisdom: +9`, `Heat Resistance: +10`, `Cold Resistance: +5` count once as for Fortune; tools show
  `Breaking Power 9` without a colon (`GearReader`). Lore paragraphs "+N% ... Mithril Powder" count as the percentage itself.
- Pickaxe Ability Cooldown (wiki Heart of the Mountain, "Cooldown Reductions": reductions stack **multiplicatively**, so the
  total is 1 - product(1 - r)): fuel tank by `drill_part_fuel_tank` (wiki Drill Parts 2/4/6/10%, checked against the tank
  paragraph `-4% Pickaxe Ability Cooldown`), Pickaxe Cooldown attribute (Abyssal Miner Shard, Epic, 1% per level), Sky Mall
  -20% (situational, or temporary on its day), Mineshaft Mayhem -25% (situational; key `mineshaft_mayhem`). Fixture:
  Titanium-Infused tank 4% and attribute 10 -> 13.6% (SkyCompass shows the plain sum, 14%).
- Mining Wisdom (wiki Mining Wisdom): Seasoned Mineman 5 + 0.1/level (Tier 4 template, max 100); Cavern Wisdom (Cavernshade,
  Epic, 0.5/level); Dwarven Training (Gold Essence, `dwarven_training`, 3 / 5 / 10 on Mining Islands, wiki Essence
  Shops/Gold); Silverfish Experienced Burrower 0.2 / 0.25 / 0.3 per level (Rare / Epic / Legendary); God Potion Mining XP
  Boost III +20 (assumed with the God Potion toggle); Booster Cookie +25 when `profile.cookie_buff_active` (owner decision:
  the cookie is read from the API, so it counts). Fixture: 9 drill + 15 + 5 + 10 = 39 (= SkyCompass) + God Potion 20 + Cookie
  25 = 84. Echo of Wisdom / Echo of Echoes boost Cavern Wisdom (see the
  attribute echoes below). Not encoded: Magic 8 Ball, Glacial talismans on Jerry's Workshop.
- Breaking Power (wiki Breaking Power): the tool's line; Maniac Miner +1 while active when it is the selected ability
  (`skill_tree.selected_ability.mining`, situational). The Breakdown tab says whether the method's requirement is met.
- Heat Resistance (wiki Heat Resistance): gear lines (Divan +10 each, Blazing reforge +2), Keep It Cool 0.4/level (Tier 6,
  max 50), Bal 1.5 per level (also NEU petnums), Yog Membrane (Yog Shard, Common, 1/level). Fixture 204.5 (= SkyCompass).
  Lanterns are untrackable.
- Cold Resistance (wiki Cold Resistance): gear lines (Dwarven Handwarmers +5), Warm Heart 0.4/level (Tier 8, max 50), Glacite
  Golem 0.1/level, Cold Resistance attribute (Wumpa Shard, Legendary, 0.5/level; key `cold_resistance`), Frozen Skin
  (Ice Essence, 1/level up to 5; key `frozen_skin`), Reaper Pepper (+1 each up to 5; `player_data.reaper_peppers_eaten`, omitted until
  the first pepper, so absent counts as 0 eaten), Mineshaft Mayhem +10 (situational). Fixture 5.4 (= SkyCompass). Lanterns, potions, Cut
  Loose stacks and the Corpse-Flavored Century Cake are untrackable.
- Powder buffs (wiki Powder Sources tables, Tier 7 template): Powder Buff 1%/level (All Powder, max 50); Atomized Mithril
  (Star Sentry, Rare), Atomized Crystals (Thyst, Rare), Atomized Glacite (Stalagmight, Rare; key `atomized_glacite`)
  at 1%/level, with the sources each one boosts in the note; Mithril Golem The Smell Of Powder 0.2%/level (Legendary+), Scatha
  Bejeweled Eyes 0.2%/level (Legendary+), Glacite Golem Powder-powered 0.1 / 0.15 / 0.2 %/level (NEU petnums and item lore);
  Sky Mall +15% Powder (`current_daily_effect` `powder_bonus`, mapped by name, temporary). Fixture 25 / 10 / 10 / 0
  (= SkyCompass). Core of the Mountain's base powder per block (level 4 / 6 / 8, Tier 5 template) is a note, not a percentage.
- Peak of the Mountain: Core of the Mountain levels (`core_of_the_mountain` in each tree, `mining_2..5` included) give no
  stat (wiki Tier 5 template): level 2 adds a pickaxe ability level, levels 1/5/7/10 add tokens (already used by the HOTM
  unlock options), levels 4/6/8 add base powder. Shown as notes on the cooldown and All Powder ledgers.
- Scrolls (wiki Power Scrolls): only the Amber Power Scroll touches mining (+30 Mining Speed for 5 s after the RIGHT CLICK
  ability, not boosted by Mining Speed Boost); read from the tool's `power_ability_scroll`, situational, never counted.
- Permanent consumables the API exposes: Reaper Pepper only (above). Dwarven O's cereals stay untrackable.

Temporary buffs (owner decision 2026-10-26: listed, never counted):
- Mayor and minister perks from the official election resource (`v2/resources/skyblock/election`, no key; part of the price
  bundle, refreshed every 20 minutes; null while unreadable). Each perk description is parsed for "+N [%] <stat>"; percentages of
  Fortune are additive % lines. Since 2026-08-25 the Mining Fiesta lasts the whole mayorship (wiki Cole, Mining Fiesta), so
  Cole's perk means the Fiesta is on: +25% Mining Fortune and +25 Mining Wisdom (live check 2026-10-27, Cole is mayor). It
  replaces the untrackable Mining Fiesta line, and Fiesta Fortune (Packrat Shard, Uncommon, 5 Gemstone Fortune/level, wiki)
  turns from situational into temporary.
- `active_effects` / `paused_effects` / `temp_stat_buffs`: known potion keys `haste` (50/level), `spelunker` (5/level),
  `cold_resistance` (2.5/level), `mining_xp_boost` (5 / 10 / 20) map to their stat (key names assumed from the potion
  names; the observed profile reported none). A `temp_stat_buffs` entry naming a stat with an amount uses that amount;
  anything else stays temporary with an unknown value. The God Potion and anything the API never reports stay "Not in the API".

Rates: Mining Speed Boost uptime now uses the cooldown ledger, 120 s (wiki, every level) x product(1 - counted reductions),
via `StatBreakdown.effectiveAbility` (Breakdown, Compare, Start with, Upgrades). The DR-X655 and a Golden Pickaxe without a
tank both show `Cooldown: 108s` (= 120 x 0.9, the attribute), so the lore leaves out the fuel tank; the ledger gives 103.68 s.
Fixture effect: MF Gems 12.04 -> 12.14 Flawless/h, Grotto 6.72 -> 6.80, Tungsten +1%, Coal +0.1%. Since 2026-10-27 Heat Resistance
(Magma Fields) and Cold Resistance (Glacite Tunnels and Mineshafts) feed the rates through the wiki Heat and Cold build-up ("Rates:
pickaxe abilities, Heat and Cold"). Wisdom, Breaking Power (beyond the existing tool requirement) and powder have no rate use yet.

Attainable maximum (`Breakdown.attainable`, `StatsReport.Ledger.attainable/attainableKnown/unknownMaxima`):
- Each row shows current / its source's own maximum, or "maxed" with a check mark. The maximum keeps the source: the same
  pet at level 100, the same HOTM perk at its maximum level (+1 only when the Blue Cheese omelette is fitted, since it shares
  the module slot with Sunny Side), the drill's engine or module slot at the best part for that stat (wiki Drill Parts),
  attributes at level 10, essence perks, collections and skill at their maxima, the fuel tank slot at -10%.
- The stat's attainable total keeps this loadout (one tool, one pet, one armor and equipment set, one HOTM tree): counted
  sources at their maxima plus missing attributes, essence perks (only where their zone applies), collections, skill,
  consumables, Booster Cookie and an empty fuel tank slot. Missing HOTM perks (tokens limit one tree), set bonuses (other
  armor), other pets/tools, situational, temporary and untrackable sources are not added, so it is never a maxed account.
- Gear stat lines have no maximum from a primary source (it depends on reforge, gems, enchantments and recombobulation), so
  any stat with a counted gear line shows "Attainable: unknown, at least X" with X = the total with those lines as they are.
  Fixture: Fortune unknown (at least 1,842.7), Cold unknown (at least 40), cooldown 19% (tank 10% and attribute 10%), All
  Powder 50%, Mithril / Gemstone / Glacite Powder 10%.

## Upgrade ranking by rate

Methods with a `MethodRates` model (every method) rank upgrades on the Upgrades
tab by gain % = 100 x (rate after / rate before - 1), with rate = Flawless / hour for the gem methods, Coal per 30
minutes, Obsidian per Mining Speed Boost cycle, the others per hour (sections below). The gain is always computed on the hourly rate; the
window only scales what is shown, and before and after use the baseline's window (a cooldown upgrade shortens the
cycle, which would otherwise look like a loss). The plan's running total is hourly too. Before and
after use the same effective stats as the Rates tab (automatic totals; Type Stats overrides still win), the
efficiency from Type Stats and the evaluated tool's Mining Speed Boost; the upgrade's stat delta is added to
those stats. Each card shows the gain and the rate before and after in the method's unit, which the Upgrades header also names. Cost per 1% = cost / gain %, as
before, and every card stays an independent alternative against the same baseline; the step-by-step plan below
is the cumulative view.
- Mining Speed has value only when it changes the ticks per block, normally or during the ability (ticks are
  rounded; see the break-time rule). Upgrades with no rate change are left out, as zero-gain upgrades were before.
  The Rates tab shows the next tick threshold: Mining Speed > 30 x strength / (ticks - 0.5), or above 60 x
  strength when already at the 4-tick soft cap.
- Ability changes count through uptime = duration / cooldown. Fuel tanks (wiki Drill Parts,
  <https://hypixelskyblock.minecraft.wiki/w/Drill_Parts>): Mithril-Infused 2%, Titanium-Infused 4%, Gemstone 6%,
  Perfectly-Cut 10% Pickaxe Ability Cooldown reduction; one tank per drill. Replacing tank a with b scales the
  cooldown by (1 - b) / (1 - a), which assumes reductions multiply (wiki Mining Speed Boost). That ratio is the
  same whether or not the lore cooldown already includes the current tank (see the open question below), so only
  the baseline is uncertain. Fixture check: Titanium-Infused to Gemstone gives +0.42% on MF Gems; a Mining Cat
  MF Gems page showed +0.39% for the same change (different baseline; comparison only). The tank is priced at its
  cheaper of its buy quote and its forge recipe; the old tank is given back and credited in the net cost.
- If the rate cannot be computed (for example Mining Speed 0), the method falls back to the drop multiplier
  and says so. The Stats tab (cheapest stat points) is unchanged.
- Drill engines and upgrade modules (wiki Drill Parts): Mithril-Plated +75 Speed/+5 Fortune, Titanium-Plated
  +150/+15, Ruby-Polished +250/+30, Sapphire-Polished +400/+50, Amber-Polished +600/+100; Sunny Side Goblin Omelette
  +50 Fortune (quadruple fuel use, not priced); Starfall Seasoning +25/+10 (doubled within 20 blocks of a fallen star,
  not counted). One engine and one module per drill, so a swap's delta is new part minus current part. The fixture's
  Ruby-Polished +250/+30 matches its lore, and the Mining Cat MF Gems page's engine deltas (+150/+20 to Sapphire,
  +200/+50 Sapphire to Amber) match these tables. Fuel-only omelettes are not proposed. Which drills accept which
  parts is not stated on the wiki; every drill is assumed to accept every part.
- Swapping a part: the current part of that kind is removed first for 9,999 coins and given back (wiki
  <https://hypixelskyblock.minecraft.wiki/w/Amber-Polished_Drill_Engine> and the Blue Cheese page: "can then be removed
  via the same menu for 9,999 Coins, which gives the item back to the player"). The fee is added to the outlay; the
  returned part is credited at its Coflnet sell value in the net cost. The fee for fuel tanks is assumed to be the same.
- Blue Cheese Goblin Omelette (module; forge, HOTM 5, 20 h): delta = the +1 level on every unlocked HOTM perk in the
  evaluated tree, counted in the method's context, minus the current module's stats (Sunny Side's +50 Mining Fortune is
  lost). Fixture (MF Gems): +65 Mining Speed, +5 Mining Fortune, +4 Gemstone Fortune, +0.1 Pristine, so with the -50
  Fortune it loses to the fixture's Sunny Side and is not listed. When the drill already holds it, other modules lose
  that gain. In the plan, fitting or removing it switches every HOTM option to the level +1 values.

## Step-by-step upgrade plan

The Upgrades tab (method view) shows a plan of up to 12 steps. Each step takes the available upgrade with the lowest
known cost per 1% (ties: larger gain), applies it and recomputes every candidate from the new state:
- the gear copy changes (enchantment level, Polarvoid count, drill part, socketed gem and unlocked slot), so the next
  proposal for that item starts from there (Prismatic 4 -> 5 after 3 -> 4, no second fuel tank of the same kind);
- a replacement item becomes the slot's item, so later deltas compare against it;
- the effective stats add the step's delta, a fuel tank scales the ability cooldown, and owned materials used by
  the step are no longer available to later steps.
Gain per step is against the state after the previous step; the total is rate (or drop multiplier) now / at the
start - 1. Blocked and unpriced upgrades are skipped, not assumed free. A replacement base item has no known
modifiers, so no in-place upgrades are proposed for it afterwards. Prices are the current quotes for every step
(no market impact). Steps are chosen on cash outlay; the plan card also totals the sale value of what the steps
replace and the net cost, and counts replaced items without a sale price. The plan never pays twice for one slot: when the
best next step would replace what an earlier step bought (a second reforge, gem, fuel tank, engine or module on one
item; a new item in a slot whose item an earlier step enchanted, coated or replaced; a new pet after a pet step or
Quick Claw), the earlier step is banned and the plan is recomputed, so the later choice is taken directly. Added
levels (Efficiency, Prismatic, Polarvoid, HOTM, essence, attributes) build on each other and are not conflicts.

The Upgrades list shows one entry per level chain (each HOTM perk, essence perk, attribute, Efficiency, Prismatic,
Polarvoid and gem slot): the lowest cost per 1% among its levels, with a note of how many were hidden. Different
reforges, drill parts, items and pets stay separate entries.

## Buy or craft/forge

`mining-recipes.json` is generated by `./gradlew :core:extractRecipes` (pinned to NEU commit `f19539bb`) from the
NotEnoughUpdates repository `items/<ID>.json` (MIT, Copyright (c) 2020 Moulberry) for every priced item: its forge recipe
(inputs, count, duration) or crafting-table recipe, and its `crafttext` unlock. "HotM N" becomes a HOTM tier; "<Collection>
<tier>" becomes the amount from the official collections resource (<https://api.hypixel.net/v2/resources/skyblock/collections>).
For each item or material to get, the cost is the cheaper known of:
- buying: the Coflnet buy quote x amount;
- crafting/forging: ceil(amount / recipe count) crafts, each input from owned items first, the rest at its buy quote.
The recipe is used only when its unlock is met (HOTM tier from the profile; collection count from `collection`; a
collection requirement with the Collections API off is not checked and the recipe is not used). An unknown input price
makes the recipe unknown; if both paths are unknown the price stays unknown. The card says which path was taken, both
totals and the forge time. Limits: one recipe level (inputs are bought, not crafted in turn); inputs used by a recipe
are not removed from the plan's owned materials; forge slots, forge time value and bazaar order prices are not modelled.
Prices change, so no guide's buy-vs-craft claim is assumed. Live check 2026-10-26: Gemstone Fuel Tank forge 48.3M vs
buy 53.7M; Amber-Polished engine buy 260M vs forge 286M; Glossy Mineral Talisman craft 26.9M with an owned Mineral
Talisman vs buy 32.0M.

## Replacement resale credit

Cards show the cash outlay and, when the upgrade replaces something, the net cost = outlay - sale value of what it
replaces. Ranking and the plan's choices use the outlay; the net is shown beside it.
- Replaced gear (armor, equipment, tool, accessories) and the replaced pet: the mod asks its backend's `/resale` (which asks
  Coflnet, caching filter lists 1 hour and quotes 15 minutes; the mod caches quotes 15 minutes and waits at most 1.5 s
  before showing the report, swapping in late quotes when they arrive) for the lowest
  BIN listing matching the evaluated copy (`/api/item/price/<tag>/bin`): Rarity, Recombobulated, Reforge,
  PowderCoating (drills and Divan items), DrillPartEngine/FuelTank/UpgradeModule; pets Rarity, PetLevel (exact) and
  PetItem. Only filters listed by `/api/filter/options?itemTag=` for that item are used, matched case-insensitively
  (Coflnet writes reforges as "jaded" on Divan armor and "Auspicious" on drills); the card names the filters used and
  those that could not be applied. Gems and enchantments are not filtered. This is an asking price, before Auction
  House fees; a sale may fetch less.
- Without a matching listing, gear falls back to Coflnet's `sell` value for the plain item ID, labelled "your copy's
  modifiers not priced". A pet without a matching listing has no sale price.
- Returned drill parts and removed gems: Coflnet's `sell` value for that item ID (bazaar instant sell; for auction items
  the value Coflnet reports).
- No sale price: the card says so and shows no net cost. Pet items moved off a replaced pet are not credited.

## Item catalog

Generated from the official items resource <https://api.hypixel.net/v2/resources/skyblock/items>: every item whose
stats include Mining Speed, Mining/Gemstone/Ore/Dwarven Metal/Block Fortune or Pristine, except SPECIAL and
UNOBTAINABLE rarities (Zoom/Alpha/Elle's pickaxes), 100 items. Stats, rarity, category, gemstone slots and the HOTM
requirement come from the resource; collection and skill requirements are written into the source text (only HOTM
blocks an upgrade). Set bonuses (Goblin, Heat, Yog, Divan...) are not modelled. Kept by hand because the resource has
no mining stats for them: Mithril/Titanium necklace, cloak, belt and gauntlet, Mithril/Titanium/Glacite lanterns,
Will-o'-wisp (Spread is also absent from the resource). Accessory families (only the highest rarity counts) are by
name except Mineral, Titanium, Haste and Speed accessories.

Tools (pickaxes, drills, gauntlet) apply only to methods their Breaking Power can mine (wiki
<https://hypixelskyblock.minecraft.wiki/w/Breaking_Power>): Coal/End Stone/Cobblestone 1, Gold 3, Obsidian 4, Ruby 6,
MF Gems and Topaz 8 (Topaz, the hardest Magma Fields gem), Jasper/Umber/Tungsten/Glacite 9. Red Sand and Mycelium are
not listed there, so tools stay unassigned to them as before. Gemstone Drill 1 (BP 7) therefore no longer counts for MF Gems.

## Reforges

Reforge stats per rarity come from the NotEnoughUpdates repository (MIT, Copyright (c) 2020 Moulberry)
`constants/reforgestones.json` and `reforges.json`, pinned to commit `f19539bb`, filtered to reforges with mining
stats (`core/src/main/resources/mining-reforges.json`). Check: the fixture drill's Auspicious at Mythic shows "(+20)"
Mining Fortune in its lore, matching the table. Pickaxe reforges apply to pickaxes, drills and gauntlets; armor
reforges (Jaded, Dimensional) to the four armor pieces; equipment reforges (Royal, Blazing) to necklace, cloak, belt,
gloves and bracelet. A proposal swaps to a stone reforge at the item's effective rarity (recombobulation included):
delta = new reforge stats - current reforge stats; a current reforge missing from the table counts as zero mining
stats. Cost = stone + Blacksmith fee at that rarity. Random Blacksmith reforges (Excellent, Fortunate, Sturdy,
Unyielding) are only used to read the current stats. Abilities: Fleet's +50 Mining Spread towards Blocks counts on
Block Fortune methods; Heated (Heat), Glacial (Cold), Stellar (Fallen Stars), Lustrous (Mining Fiesta), Mithraic
(Mithril/Titanium) and Scraped (matching Breaking Power) are conditional and not counted; Auspicious "+1% Mining
Fortune" is unclear (additive or multiplicative) and not counted.

## Recombobulation, gem swaps, powder coating, Power accessories

- Recombobulator 3000 (item lore: "Permanently increases the rarity of an item", once per item; API `rarity_upgrades`):
  base stats do not change; the reforge (table above) and socketed Jade/Amber/Topaz take their values at the next
  rarity. Delta = (reforge + gems at next rarity) - (at the current rarity). Offered when it adds a mining stat.
- Gem type swaps: flexible (Mining/Universal) slots may switch to another mining gem type. Delta = new gem - the old
  gem's stat (lost); the removal fee of the old gem's quality is included. A non-mining old gem loses no mining stat.
- Divan's Powder Coating (item lore, NEU `items/DIVAN_POWDER_COATING.json`; wiki): +500 Mining Speed on a drill,
  +10 Mining Fortune on each Armor of Divan piece and the Pendant of Divan; once per item (API
  `divan_powder_coating`, as read by SkyHanni and Coflnet).
- Power Talisman/Ring/Artifact/Relic are in the catalog for their gem slots (items resource); they have no mining
  stats of their own, so only socket proposals come from them. Accessory gems give half stats.

## Essence perks, attributes, HOTM perk levels

The stat report reads each source's level and the balances, and sends target levels with their counted stats in the
method's context (same conditions as the Breakdown) and the total cost from the current level:
- Essence perks: Eager Miner (Gold Essence, +10 Mining Speed per level) and Rhinestone Infusion (Diamond Essence, +2
  Gemstone Fortune per level), 100, 200, ... 1,000 essence for levels 1..10 (NEU `constants/essenceshops.json`). The
  essence balance (`currencies.essence.<TYPE>.current`) is used first; the rest is priced at the bazaar quote.
- Attributes: shards per level from the cumulative table above (NEU `attribute_shards.json` gives the same per-level
  counts); bazaar IDs from that file (SHARD_FLITTER, SHARD_MINER_ZOMBIE, SHARD_QUARTZFANG, SHARD_HIDEONLEAF,
  SHARD_GEMZIE, SHARD_BAL). Shards in the Hunting Box are not read, so every shard is bought. Quartz Speed (weather)
  and Deep Technique (per-block roll) are not counted and therefore never proposed.
- HOTM perks: levelling perks already unlocked in the evaluated tree, to the next level, +5, +10, +25 and max.
  Powder per level from NEU `constants/hotmlayout.json`: floor((level + 2)^e) with e = 3 (Mining Speed), 3.05
  (Mining Fortune, Rags to Riches), 3.2 (Speedy/Fortunate Mineman), 2.3 (Professional, Strong Arm), 4 (Gem Lover,
  Old-School, Blockhead, Metal Head), 2.6 (Efficient Miner), 2.2 (Mole); Mining Master floor((level + 8)^5); level is
  the current level. Unspent powder = `powder_<type>` (the API value is already net of `powder_spent_<type>`; owner check: 21.2M
Gemstone Powder in game). An option is free when the unspent powder
  covers it, otherwise it is shown as grinding (no coin price) and the plan skips it.
- HOTM perk unlocks: a locked perk with a mining stat is unlocked along the shortest path of locked nodes from an
  unlocked one (orthogonal neighbours on the tree grid; positions from NEU `constants/hotmlayout.json`, tier = 10 - row).
  Each unlocked node costs one Token of the Mountain; the path needs the highest tier on it. Tokens (wiki Heart of the
  Mountain): tiers 1..10 give 1, 2, 2, 2, 2, 2, 3, 2, 2, 2 (20 in total) and Core of the Mountain levels 1, 5 and 7 give 1
  and level 10 gives 2; unspent = earned - unlocked perks other than Core of the Mountain (fixture check: tier 10, Core
  10, 26 unlocked perks -> 25 = `tokens_spent.mountain`). Options: unlock (level 1, tokens only), then levels 5, 10, 25
  and max with powder from level 1 (same formula). Free when the tokens and unspent powder cover it, otherwise grinding;
  below the path's tier it is blocked as "Reach HOTM N first (grinding)". The plan tracks unlocked nodes and tokens, so
  a shared path is paid once. Not modelled: levelling Core of the Mountain for tokens, resetting the tree.
- Mining skill (grinding): +4 Mining Fortune per level; options for the next level, +5, +10 and 60, with the Mining XP
  still needed (official skills resource). Collections (grinding): tiers whose rewards include a mining stat. In the
  official collections resource (checked 2026-10-26) these are only Tungsten, Umber, Glacite and Obsidian (+1 Mining
  Fortune at some tiers); each option shows the items still needed. Grinding entries have no coin price, sort after
  priced ones and are skipped by the plan; the list shows each chain's nearest level.
In the plan a taken level replaces the chain's stats, the chain's earlier spending is subtracted from its later
levels, and balances shrink by what was used.

## Set bonuses

Armor set bonuses with mining stats (item lore in the NEU repository; wiki Mineral and Glossy Mineral Armor):
Miner Outfit full set +40 Mining Speed; Glacite full set +2 Mining Speed per Mining level; Mineral +100 and Glossy
Mineral +150 Mining Spread per piece on ores and blocks (Ore and Block Fortune methods); Mithril equipment (necklace,
cloak, belt, gauntlet) Dwarf Wannabe +30 Mining Speed and +5 Mining Fortune, Titanium equipment True Dwarf +50 and
+40, both only in the Dwarven Mines (counted for Coal and Gold, whose default zone is the Dwarven Mines). Every armor piece swap adds
the bonus it completes and subtracts the one it breaks, and each set is also offered as a full four-piece purchase
(owned pieces free). Mixed Mineral/Glossy pieces are counted per set (whether they combine is unknown). Not counted:
Goblin Smart Miner (converts Intelligence, not known to the engine) and Yog Absorb (+1 Mining Speed per 5 Yog kills).
The Breakdown counts the same values: a full-set bonus whose lore says "per Mining Skill level" (Glacite Expert
Miner) is the lore number x the Mining level from the skill XP (unknown when the XP is missing). A "Tiered Bonus"
(Mineralworks, Glossy Mineralworks) is counted once per set as the per-piece value x pieces in the loadout; the stored
lore number reflects the pieces worn when the item was saved, so the wiki per-piece value is used, or the stored
value / stored count for other tiered sets.

## Pets and pet items

The stat report carries the evaluated pet's counted stats (same ledger rules as the Breakdown, in the method's zone and
target) and the alternatives. Alternatives are each catalogued pet at level 100, one option per rarity whose
catalogued stats differ from the rarity below (Bal Epic/Legendary, Scatha Rare/Epic/Legendary, Bee Rare/Epic/Mythic,
Glacite Golem Common/Rare/Epic, Mithril Golem, Silverfish, Armadillo and Mole one each), and a Quick Claw for the
current pet (+1 Mining Fortune and Speed per 2 levels). delta = option stats - current pet stats. A new pet is
assumed to come without a pet item (the current item is not credited), so a Quick Claw pet can lose to a bare one.
Price: lowest Auction House BIN at that rarity and level 100 (Coflnet `/api/item/price/PET_<TYPE>/bin?Rarity=..&PetLevel=100`);
an owned level-100 copy of that rarity costs nothing. After a pet step, the plan drops pet-item options for the old pet.
New pet stats (NEU petnums, linear between level 1 and 100): Bee Mining Fortune 0.2/0.3/0.3/0.4 per level (Rare/Epic/
Legendary/Mythic) plus the same again from Busy Buzz Buzz ("Grants +X of each to your pet"); Glacite Golem Mining
Speed 1.25 per level, Iceborn Mining Fortune 0.75 (Rare) or 1 (Epic/Legendary) per level in the Glacite Mineshafts;
Mole (Legendary) Mining Speed 1.5 per level. Not counted: Glacite Golem Frozen Perfection (Pristine per looted corpse,
situational), and perks without mining stats. Pets that need other pet items or perks not listed stay unknown.

## Rates: Magma Fields gemstones

`core/` `MethodRates` (MF Gems, MF Ruby, MF Topaz), shown on the Rates tab as "automatic rate" from the
effective stats (automatic totals, overridden by Type Stats), the efficiency in Type Stats (default 65%)
and the pickaxe ability on the evaluated tool. The manual rate scenario stays next to it. The value window
is a one-hour average that includes the Mining Speed Boost cycle (the Mining Cat developer named windows
only for Obsidian, Shafting/metals and Coal).

- Gem and block strength (wiki Block Strength, <https://hypixelskyblock.minecraft.wiki/w/Block_Strength>):
  Ruby 2,300; Amber, Sapphire, Jade, Amethyst 3,000 (MF Gems uses these); Topaz 3,800.
- Break time (wiki Mining Speed, <https://hypixelskyblock.minecraft.wiki/w/Mining_Speed>):
  ticks = round(30 x strength / Mining Speed), at least 4 ticks (soft cap); 1 tick when Mining Speed exceeds
  60 x strength (gemstone blocks). No extra tick per block is added (see the guide check below).
  Cross-checked against the community tick-threshold guide: all 210 table thresholds match
  (`docs/references/TICK-THRESHOLDS.md`, which also lists the block strengths for other methods).
- Ability: read from the tool lore (`Ability: Mining Speed Boost`, `Grants +250% Mining Speed for 15s`,
  `Cooldown: 108s`). Uptime = duration / cooldown; during it the speed is multiplied by (1 + boost). It is
  assumed to apply to all counted Mining Speed. Reference values (wiki Mining Speed Boost,
  <https://hypixelskyblock.minecraft.wiki/w/Mining_Speed_Boost>): level 1/2/3 = +200/250/300% for 10/15/20 s,
  120 s cooldown, reductions multiply. The fixture's lore shows 108 s = 120 x 0.9 (Pickaxe Cooldown 10);
  since 2026-10-27 the rates use the cooldown ledger instead (103.68 s with the tank; see "Stat breakdown: other stats"). Maniac Miner and
  the Magma Fields Heat: "Rates: pickaxe abilities, Heat and Cold".
- Blocks / hour = 3600 x efficiency x 20 x (uptime / boosted ticks + (1 - uptime) / ticks) x (1 + Gemstone
  Spread / 100). Every spread block is assumed to be another gemstone; travel time is part of the efficiency.
- Rough per block = 4 x (1 + 0.79 x Pristine) x (1 + (Mining Fortune + Gemstone Fortune) / 100), with 4 the
  average of the 3-5 base yield (wiki Gemstone and Pristine,
  <https://hypixelskyblock.minecraft.wiki/w/Gemstone>, <https://hypixelskyblock.minecraft.wiki/w/Pristine>).
  Flawless = 80 x 80 x 80 = 512,000 Rough.
- Coins / hour = Flawless / hour x a Coflnet quote for the Flawless gem, shown at both figures ("Coins per hour across
  methods" below). No sale price is invented.

Guide check (Magma Fields guide, rates "up to" at 60% Block Efficiency, computed with a third-party calculator):
Ruby 23.5, Amber/Sapphire/Jade/Amethyst 20.1, Topaz 16.5 Flawless / hour. With an estimated hypermax
(Mining Speed about 9,700 on gems, MSB level 2 with 97 s cooldown, Pristine 17.8, Mining + Gemstone Fortune
about 2,730, Spread 2.5) the model gives about 19.6 Amber / hour at 60%. The guide's per-gem ratios do not pin
down the tick model by themselves. The guide states 60% while the Mining Cat developer described guide
averages at 65%; we keep the guide's own 60% for the reference and 65% as the player's default.

Fixture (Debotes, best owned gear, DR-X655, Magma Fields, 65%): Mining Speed 8,000 (11 ticks on Amber, 4
during MSB), Pristine 16.31, Fortune 1,767 + 180: about 12.0 Amber, 13.9 Ruby and 10.3 Topaz Flawless / hour.
Not validated against a measured session yet.

## Rates: Fairy Grotto

`MethodRates` for Grotto applies the Magma Fields rules above to Jasper. Default efficiency 65% (the guide's
"roughly 65%"), default zone Crystal Hollows; pick Magma Fields in the zone picker for a Magma Fields grotto
(Bal and other Magma Fields bonuses then count).
- Jasper strength 4,800 (wiki Block Strength, same for block and pane), Breaking Power 9, 3-5 Rough per block
  (wiki Jasper Gemstone, <https://hypixelskyblock.minecraft.wiki/w/Jasper_Gemstone>); Flawless Jasper =
  512,000 Rough, priced at a `FLAWLESS_JASPER_GEM` quote (both figures).
- 4 ticks (the soft cap) needs Mining Speed above 30 x 4,800 / 4.5 = 32,000, matching the Fairy Grotto guide's
  "32000 Mining Speed required to mine Japer in 4 Ticks" with Mining Speed Boost level 2. The tick-threshold
  guide lists it as "> 32000", consistent with our rounding (exactly 32,000 = 4.5, which rounds up to 5 ticks).
- All mining time is assumed to be on Jasper with the ability used on Jasper (the guide recommends routing so).
  The surrounding Breaking Power 7 gems are a secondary drop at the guide's ratio ("Rates: secondary drops"). Unknown and not modelled:
  their share of mining time, grotto size and
  griefed veins, the Magma Fields share of a partly Magma Fields grotto, ping and TPS. These lower the real
  Jasper rate; they belong in the efficiency until measured.

Guide reference (Fairy Grotto guide, owner-supplied, `service-data/guides/fairy-grotto.txt`; roughly 65%
efficiency, minimal lag, 0% Magma Fields): Mansion up to 12 Flawless Jasper / hour plus 3.1 other Flawless
gems / hour; Palace up to 11 plus 4.6; full Magma Fields grottos roughly x1.15. Estimated hypermax in the
Crystal Hollows (Mining Speed about 9,700, MSB level 2 with 97 s cooldown, Pristine 14.8 without Bal,
Mining + Gemstone Fortune about 2,730, Gemstone Spread 2.5): 15 ticks, 4 during MSB, about 12.8 Jasper / hour
at 65%, against the guide's 12 for a route that also mines other gems. Not validated against a measured session.

Fixture (Debotes, best owned gear, DR-X655, Crystal Hollows, 65%): Mining Speed 8,000 (18 ticks, 5 during MSB
because 28,000 is below 32,000; next tick at 8,229), about 6.7 Flawless Jasper / hour.

## Rates: Coal

`MethodRates.blocks` for Coal, on the Rates tab as "automatic rate" and used for the Upgrades ranking. Default
efficiency 82.5% (instamine blocks; Mining Cat developer, 2026-10-26). Value window: 30 minutes with two lobby hops (the
Mining Cat developer's window for Coal). The model has no lobby-hop time of its own: hops, travel, Hard Stone and aiming
are part of the efficiency, so the 30-minute value is half the hourly rate.
- Coal Ore strength 30; ores, Obsidian, gemstones and Dwarven Metal instamine above 60 x strength (1,800, so 1,801),
  other blocks above 30 x strength (wiki Block Strength, <https://hypixelskyblock.minecraft.wiki/w/Block_Strength>,
  checked 2026-10-26). Otherwise ticks = round(30 x strength / Mining Speed), at least 4 (soft cap).
- Airborne: the wiki applies a x5 break-time penalty when "the player's feet are not on the ground". Modelled as Mining
  Speed / 5, which reproduces the Coal guide's 9,001 airborne instamine threshold (and 7,501 for Hard Stone, strength 50).
  Below 9,001 an airborne Coal block hits the 4-tick soft cap.
- Share of blocks mined airborne: 8%. Source: the Mining Cat Coal page ("coal mined in the air (about 8% of the time)
  takes 4x longer"). This is a route estimate, not a mechanic; whether it is a share of blocks or of time is not stated.
  We read it as blocks. Average ticks per block = 0.92 x ground ticks + 0.08 x airborne ticks.
- Mining Speed Boost from the tool lore, as for gems: blocks / second = 20 x (uptime / boosted average ticks +
  (1 - uptime) / average ticks). Maniac Miner (the Coal guide's Blue Cheese swap drill) is modelled when it is the tool's ability, and
  listed as the alternative otherwise ("Rates: pickaxe abilities, Heat and Cold"; fixture +3.3% over Mining Speed Boost).
- Blocks / hour = 3600 x efficiency x blocks / second x (1 + Mining Spread / 100). The wiki Mining Spread page says Spread
  mines adjacent blocks; it does not say whether only blocks of the same type. We assume each Spread block is Coal Ore.
- Coal per block = 1 x (1 + (Mining Fortune + Ore Fortune) / 100) (wiki Mining Fortune: every 100 is one more set of drops,
  the remainder a chance at one more; Ore Fortune adds on ores; Fortune also applies to Spread blocks). Base drop 1 Coal.
- Coins = Coal x a Coflnet quote for `COAL` (both figures, "Coins per hour across methods"). Hard Stone (the guide's 3-6M / hour) and
  crafting into enchanted items are not counted.

Guide check (Coal guide, owner-supplied `service-data/guides/coal.txt`, October 2026: "rough estimates", 4.0M-9.0M Coal
and 3.0M-6.0M Hard Stone / hour). An assumed hypermax with Mining Speed above 9,001, Fortune + Ore Fortune about 2,700 and
Spread 400 gives about 8.3M Coal / hour at 82.5%, inside the guide's range.

Fixture (Debotes, best owned gear, DR-X655, Crystal Hollows default context, 82.5%): Mining Speed 7,250 (instamine on the
ground, 4 ticks airborne; 8,750 x 3.5 during MSB instamines airborne too), Fortune 1,767 + Ore 100, Spread 300: about
3.89M Coal / hour, 1.95M per 30 minutes. Reaching 9,001 Mining Speed would add about 20% (the ability already covers
14% of the time). Not validated against a measured session.

Mining Cat Coal pages (comparison only; `docs/references/MININGCAT.md`):
- Fortune rows: ours = delta / (100 + Fortune + Ore Fortune), for example Flawless Jade +35 = 1.78% on 1,767 + 100.
  Mining Cat shows 1.8% (fits its counted 1,724.5). Same rule; the baselines differ by the 1% Auspicious multiplier and
  God Potion Fortune we count.
- Spread rows differ: ours gives Snoozle VIII (+8 Spread) 8 / 400 = 2.0%, Mining Cat 1.6%; Snoozle IX-X (+2) 0.5% against
  0.39%. Their numbers fit a Spread efficiency of about 0.8 (`RATE-MODEL.md`). We found no mechanic for that (perhaps missing
  neighbor blocks on a route), so we keep the plain Spread rule and record the difference.
- Speed: Mining Cat says 7,380 is "1,621 short of the 9,001 needed to instamine coal while falling". Our threshold is the
  same 9,001; our fixture speed is 7,250 (the 130 difference is not yet explained).

## Rates: Obsidian

`MethodRates.blocks` for Obsidian with the Coal rules above, except:
- Obsidian strength 500, instamine above 60 x 500 = 30,000 Mining Speed (wiki Block Strength names Obsidian among the
  60 x blocks). Ticks otherwise round(15,000 / Mining Speed), at least 4: every Mining Speed above 3,334 gives the
  4-tick soft cap, so Mining Speed has value only when Mining Speed Boost lifts it above 30,000
  (speed x (1 + boost) > 30,000; above 8,571 with +250%). The Rates tab shows that threshold.
- Mined on the ground (the Obsidian guide's route is floor and walls); airborne share 0, with the share recorded as
  unknown. Default efficiency 65% (not an instamine block).
- Obsidian per block = 1 x (1 + (Mining Fortune + Block Fortune) / 100) (wiki Mining Fortune; Obsidian takes Block
  Fortune in our method table). Coins at a Coflnet `OBSIDIAN` quote (both figures, "Coins per hour across methods"); crafting into Null Ovoids is not counted.
- Value window: one Mining Speed Boost cycle, the ability's cooldown from the tool lore (the Mining Cat developer's
  window for Obsidian, "from full cooldown until it ends"; we read one cycle as one cooldown, which starts when the
  ability is used). Without a Mining Speed Boost the window is one hour.
- Not modelled (unknown or no rule yet): the guide's pet swap to Scatha (Unalloyed Speed) for the ability and to Snail
  otherwise, Rogue Sword / Weirder Tuba movement speed (walking, not Mining Speed), and Will-o'-wisp uptime. Movement
  belongs in the efficiency.

Guide check (Obsidian guide by Mycelium Larper, last edit 2026-10-25: "around 3.5m/h" with max Magical Power and decent
skill). The earlier check assumed Spread 400 and got 2.6M. The guide's gear has much more Spread: full Glossy Mineral
armor +600 on ores and blocks (wiki Glossy Mineral Armor), a Fleet drill +100 on blocks, 310 general (General Mining Stat
guide), and Snail +400 on blocks outside the ability. With that setup (Fortune about 2,090 incl. 450 Block, Mining Speed
Boost 3, 108 s cooldown) the engine gives 4.4M (Scatha the whole cycle) to 5.5M (Snail the whole cycle) at 65%, above the
guide. The route is walking-limited (floor and walls, Rogue Sword and Tuba for movement speed), so 65% is probably too high
for it; the guide's 3.5M matches about 50%. Not tuned: the efficiency stays the player's input. See
[GUIDE-COMPARISON.md](references/GUIDE-COMPARISON.md).

Fixture (Debotes, best owned gear, DR-X655, Crystal Hollows, 65%): Mining Speed 7,250 (4 ticks, 25,375 during the
ability, still 4 ticks), Fortune 1,767 + Block 115, Spread 300: about 0.93M Obsidian / hour, 27,800 per 108 s cycle.
8,572 Mining Speed would add about 41%. Not validated against a measured session.

## Rates: Tungsten, Umber, Glacite (Shafting)

`MethodRates.blocks` with the Coal rules, per hour (the Mining Cat developer's window for Shafting and the Dwarven
Metals), default efficiency 65%, all blocks mined on the ground (airborne share unknown).
- Strength: Tungsten 5,600 (all block types), Umber 5,600 (all types), Glacite 6,000 (Packed Ice), Breaking Power 9
  (wiki Tungsten, Umber, Glacite and Block Strength pages, checked 2026-10-26). Ticks = round(30 x strength / Mining
  Speed), at least 4: Tungsten/Umber reach 4 ticks above 37,333, Glacite above 40,000. Mining Speed Boost changes the
  ticks during the ability, so speed counts at every tick threshold, as for gems.
- Fortune: Mining Fortune + Dwarven Metal Fortune (wiki Dwarven Metal Fortune lists Mithril, Titanium, Umber, Tungsten and
  Glacite; every 100 adds 100% drops). Mining Spread applies to Dwarven Metals (wiki Mining Spread).
- Base drops are not one number. Tungsten: Cobblestone 1, Clay 3; Umber: Terracotta 1, Brown Terracotta 2, Smooth Red
  Sandstone 3 (wiki). The Dwarven Metal guide (flora, Mining Cult, 16 Aug 2026, last edit 27 Oct) says to mine only Clay
  for Tungsten outside mineshafts and mainly Smooth Red Sandstone for Umber (Brown Terracotta too during Mining Speed
  Boost), so both are rated at **3 per block**, labelled a guide route assumption (2026-10-28; before: 1 per block, shown
  as a lower bound). Umber's real average is a little lower; Cobblestone mined in Tungsten shafts lowers Tungsten's.
  Upgrade gains are ratios and do not depend on it. The wiki states no base drop for Glacite (shown per 1).
- Cold applies in both zones (wiki Cold; "Rates: pickaxe abilities, Heat and Cold"). Shafting adds Frozen Corpse loot from the mineshafts
  its Glacite spawns ("Rates: secondary drops"); mineshaft gemstones are not counted. Hard Stone: amount unknown.
- No guide rate was supplied for these methods (`METHOD-GUIDES.md`, "Missing guides").

Fixture (Debotes, best owned gear, DR-X655, 65%): Glacite Tunnels, Mining Speed 7,255 (23 ticks, 7 during the ability),
Fortune 1,767 + Dwarven Metal 5, Spread 300: about 200,700 Tungsten or Umber / hour per base drop. Glacite Mineshafts
(Glacite): 25 ticks, 7 during the ability, Fortune 1,771: about 190,600 / hour per base drop. Not validated. With the
2026-10-28 base drop of 3 and the Cold rules, the fixture shows about 496,000 Tungsten or Umber / hour (27M and 34M coins
/ hour at instant-sell quotes of that day), so they now rank above the gem methods on that profile; unvalidated.

## Rates: Gold, End Stone, Cobblestone

`MethodRates.blocks` with the Coal rules, per hour (no Mining Cat window was named for these), all blocks on the ground
(airborne share unknown).
- Gold (Mines of Divan): Pure Gold strength 600, instamine above 60 x 600 = 36,000 (wiki Block Strength; the Gold guide's
  36,001). Otherwise 4 ticks above 4,000, so Mining Speed counts only when Mining Speed Boost lifts it above 36,000.
  The guide's ability-loadout swap and the bugged Goblin/Strong Arm Gold bonuses it relies on are not modelled. Base drop
  5-7 Gold per block (wiki Mines of Divan changelog, 9 Oct 2023: "from 2-4 to 5-7"), shown at the average 6; coins at
  a `GOLD_INGOT` quote (both figures). Fortune: Mining Fortune + Ore Fortune, as for ores. Open: the same changelog page says (1 Aug
  2023) only Redstone and Emerald in the Mines are affected by Mining Fortune; whether that still holds for Pure Gold is
  not confirmed. The Gold guide lists Fortune gear and the Mining Cat Gold page values Fortune, so Fortune is counted.
  Default efficiency 65%. No guide rate (the guide's rates are pending).
- End Stone: strength 30, instamine above 30 x 30 = 900 (wiki; guide 901). Cobblestone: strength 20, above 600 (wiki;
  guide 601). Both drop 1 block (vanilla drop), take Mining Fortune + Block Fortune and default to 82.5%. Above the
  instamine speed, Mining Speed adds nothing (1 tick per block, 20 per second while breaking).
- Maniac Miner, which both guides use (Blue Cheese on the main drill), is modelled from the wiki table ("Rates: pickaxe abilities, Heat
  and Cold"); with the fixture's level 2 instead of Mining Speed Boost it adds 6.9% on End Stone and Cobblestone.

Guide check: End Stone guide 11-16M / hour, Cobblestone guide 13-16M / hour (FazeHunter(Ji), last edit 2026-09-30).
The earlier check assumed Spread 400 and got about 8.5M. The General Mining Stat guide's Cobblestone setup (KoenigBooHoo,
2026-08-25: 1,902 Fortune incl. 450 Block Fortune; Spread 310 general + 600 full Glossy Mineral + 100 Fleet = 1,010; wiki
Glossy Mineral Armor confirms +600 on ores and blocks) gives **14.2M / hour** at 82.5% with Maniac Miner 3 (13.2M without),
inside both guide ranges. No rule was changed; the gap was the assumed Spread. The Gold guide (flora, 2026-10-26) has
not posted rates; its full-Glossy Goblin setup (1,992 Fortune incl. 340 Ore, Spread 1,010) gives about 16-17M Gold / hour
at 65%, unchecked.

Fixture (Debotes, best owned gear, DR-X655): Gold (Crystal Hollows default context, 65%): Mining Speed 7,250 (4 ticks,
25,375 during the ability, still 4), Fortune 1,767 + Ore 100, Spread 300: about 5.5M Gold / hour. End Stone and
Cobblestone (82.5%): Mining Speed 7,150 instamines, Fortune 1,767 + Block 115, Spread 300: about 4.7M / hour.
Not validated.

Default zone (owner, 2026-10-26; Coal and Gold guides): Coal routes and the Mines of Divan are in the Crystal Hollows, so
Coal and Gold default to the Crystal Hollows context. Bonuses only "in the Dwarven Mines" (for example the full Mithril
and Titanium armor set bonuses) no longer count for them; the fixture wears neither set, so its numbers are unchanged.

## Rates: Mycelium, Red Sand

`MethodRates.blocks` with the Coal rules, per hour, default 82.5%, Mining Fortune + Block Fortune, base drop 1 (vanilla).
- The wiki Block Strength page lists no strength for Mycelium or Red Sand (only a theorized vanilla hardness of 0.6 / 0.5),
  so no instamine threshold is known. The blocks are assumed instamined (1 tick); the owner confirmed (2026-10-26) that
  20 blocks per second is doable on Mycelium, Red Sand, End Stone and Cobblestone. Mining Speed therefore has no value here.
- Not counted: the Crimson Essence perks Fungus Fortuna / Harena Fortuna the guide lists (not in our stat ledger yet),
  and the guide's "W and A/D" movement (+2.3%, part of the efficiency). Maniac Miner counts when it is the tool's ability.

Guide check (Mycelium/Red Sand guide, owner-supplied: Mycelium 4.6M / hour, Red Sand 4.8M / hour, "max fortune is 1522
including block fortune"): with Fortune 1,522 and Spread 400 the model gives 59,400 x 5 x 16.22 = about 4.8M / hour at
82.5%, matching the guide. But the guide's gear is full Glossy Mineral armor (+600 Spread on ores and blocks, wiki) on a
shovel, about Spread 910 with the general sources, and at that Spread (with Maniac Miner 3) the model gives about 10.7M,
more than twice the guide (2026-10-28 check). Unexplained: Glossy's Spread may not count on Mycelium/Red Sand, Spread may
not find enough adjacent blocks on a flat surface (the wiki says it breaks adjacent blocks and not the one below you), or
the run is slower than 20 blocks / second. Not tuned; see [GUIDE-COMPARISON.md](references/GUIDE-COMPARISON.md). Fixture (Crimson Isle as "other" zone): Fortune 1,429.5 + Block 115, Spread 300: about
3.9M / hour.

## Rates: pickaxe abilities, Heat and Cold

Added 2026-10-27. Applies to every `MethodRates` model, so the Rates tab, Compare, Start with and the Upgrades ranking all use it.

Maniac Miner (wiki Maniac Miner, <https://hypixelskyblock.minecraft.wiki/w/Maniac_Miner>, last edited 23 Oct 2026):
- Effect: +1 Breaking Power and a stack of +5 / +10 / +15 Mining Fortune per block broken (levels 1-3) for 25 / 30 / 35 s,
  capped at 500 Mining Fortune in total; 20 Mana per block broken. Every pickaxe ability has a 120 s cooldown (wiki Mining Speed
  Boost table) and the same cooldown reductions, so Maniac Miner uses the Pickaxe Ability Cooldown ledger as Mining Speed Boost does.
- Which tools: all mining tools (Drill, Pickaxe, Gemstone Gauntlet), activated with right click (wiki). Only one pickaxe ability is
  selected at a time (wiki Mining Speed Boost: "only one can be selected at a time"), so the rate uses the ability on the tool's lore
  and never stacks the two.
- Level: read from the lore (`+N Mining Fortune per block broken for Ns`, pattern assumed; no Maniac Miner lore has been observed yet),
  else level 1 + 1 at Core of the Mountain 2 + 1 with a Blue Cheese Goblin Omelette on the tool, at most 3 (wiki Mining Speed Boost
  "Upgrading" and the Blue Cheese omelette's +1 ability level).
- Rate: uptime = duration / cooldown, used on cooldown. Mining Speed and ticks are unchanged. Blocks mined per use = duration x
  efficiency x mined blocks per second; block i gets min(500, x (i - 1)) (the stack is assumed to apply from the next block), so the
  average stack per use is computed exactly; blocks broken while it is active get that average on top of their Fortune, and the
  other blocks none. Fortune is linear in drops, so drops = base x (1 + (Fortune + type Fortune + uptime x average) / 100).
- Assumed (not documented): only the mined block adds a stack, not its Spread blocks (Spread blocks still get the Fortune); Mana is
  enough for the whole duration (the wiki says each block consumes 20 Mana but not what happens without Mana; the card shows the Mana
  one use needs). Not modelled: the level-3 cap bug (+510) and the Blue Cheese +10 over the cap (wiki Bugs), the Breaking Power +1
  (only matters for blocks one tier above the tool), and the guides' swap drill (Maniac Miner duration is kept when swapping away from
  a Blue Cheese drill, the stacks are not; wiki).
- Alternative: every rate card lists the other ability at the same level and cooldown (Mining Speed Boost level n <-> Maniac Miner
  level n) with the rate change, same gear and stats. Fixture (65% / 82.5%, God Potion): Coal +3.3%, Gold and Obsidian +5.4% (their
  4-tick soft cap means Mining Speed Boost adds nothing), End Stone / Cobblestone +6.9%; MF Gems -18.3%, MF Ruby -12.8%, MF Topaz
  -25.2%, Grotto -26.3%, Tungsten / Umber -25.7%, Shafting -26%. The Magma Fields guide says to test both for 30 minutes; this is the
  model's version of that test.

Heat (wiki Heat, <https://hypixelskyblock.minecraft.wiki/w/Heat>, last edited 19 Oct 2026; wiki Heat Resistance, edited 28 Aug 2026):
- Heat rises by +0.1 every Heat Tick at Y 64 and below in the Crystal Hollows (the Magma Fields at Y 63 and below), +0.2 in Khazad-dum,
  +1 while on fire; one Heat Tick every 1 x (1 + 0.01 x Heat Resistance) seconds. At 90 the player is set on fire, at 100 takes
  increasing true damage. Crystal Hollows water removes 5 per second, a Water Bottle 10, changing worlds or dying resets it.
  Heat depletes by 1 per second elsewhere; it does not deplete while gaining (otherwise it could never build), which is how we read it.
- Model (Magma Fields zone, so MF Gems / Ruby / Topaz and a Grotto with the zone picker on Magma Fields): mine until 90 Heat, then
  cool to 0 in water: build = 90 / 0.1 x (1 + Heat Resistance / 100) s, cooling = 90 / 5 = 18 s; share of mining time kept =
  build / (build + cooling). Fixture Heat Resistance 204.5: 45.7 min of mining per 18 s, 99.3% kept (MF Gems 12.14 -> 12.06
  Flawless / hour). Without Heat Resistance: 15 min per 18 s, 98.0% kept. Each Heat Resistance point therefore has a small, real
  value in the Upgrades ranking of Magma Fields methods (+10 at 204.5 is about +0.02%).
- Unknown, left out with a label: the walk to water (route dependent); whether players hold heat near 95 for the Heated reforge (the
  Magma Fields guide says so; the Heated bonus is not in the ledger). Not modelled: Heat Armor's full immunity, the Tungsten Regulator
  and Bal's Dispersion.
- Crystal Hollows zone (Coal, Gold, Grotto, Obsidian by default): Heat only builds at Y 64 and below; how much of a route is that deep
  is unknown, so no Heat loss is applied and the card says so.

Cold (wiki Cold, <https://hypixelskyblock.minecraft.wiki/w/Cold>, last edited 23 Oct 2026; wiki Cold Resistance; wiki Glacite Mineshafts):
- In the Glacite Tunnels and Mineshafts Cold falls by 1 every 5 x (1 + 0.01 x Cold Resistance) / (1 + Cold Vulnerability) seconds.
  Cold Vulnerability is +0.25 in a Glacite Mineshaft, applied every 150 s from entry (so the build-up speeds up). Every Cold below
  minus your Cold Resistance cuts Mining Speed by 0.5%. Warnings at -25 / -50 / -75 / -90 (FROSTBITE), death at -100. The Dwarven
  Base Camp and changing worlds reset Cold to 0; a Portable Campfire adds +50 (5-minute cooldown).
- Model (Glacite Tunnels: Tungsten, Umber; Glacite Mineshafts: Shafting): mine from 0 to -90 Cold, then reset at the Base Camp
  (leaving the shaft for Shafting). The rate is averaged over the time spent at each Cold level, with Mining Speed x (1 - 0.005 x
  max(0, Cold - Cold Resistance)) through the full tick model (so a speed loss only costs where it crosses a tick threshold).
  Fixture Cold Resistance 5.4: -90 after 7.9 min in the tunnels (6.5 min in a shaft), 81.5% of the rate kept on Tungsten / Umber
  (about 202,700 -> 165,300 per base drop / hour), 82.4% on Shafting (about 192,700 -> 158,800). Cold Resistance at or above 90
  removes the loss; every point below that has value in Upgrades and Compare.
- Unknown, left out with a label: the reset trip's time and where a player really stops (we use the documented -90 warning; earlier
  resets lose less speed but cost more trips). Not modelled: Portable Campfires, the Tungsten Regulator, mob hits, water.

## Rates: secondary drops

`MethodRates.secondary` (amount per hour, contents to price, source) and `MethodComparison.value` (coins in the Price mode). Shown on
the Rates tab card ("+ name", amount, coins, source in the tooltip, "Total coins / hour") and in each Compare tooltip; Compare ranks
by the total (main product + known secondary coins). Upgrades still rank by the main product's rate: the Coal and Grotto secondary
drops scale with it, mineshaft loot follows blocks, not Fortune.
- Hard Stone, Coal: about 0.69 Hard Stone per Coal, the Coal guide's midpoints (3-6M Hard Stone to 4-9M Coal per hour). This is the
  only source for the route's Hard Stone share; it is a route estimate, not a mechanic. The wiki Hard Stone page gives strength 50
  (instamine 1,500, airborne 7,500, matching the guide's 7,501), Breaking Power 4 and NPC 1 coin, but no base drop or Fortune type.
  Fixture: about 2.70M Hard Stone / hour. Coflnet had no instant-sell figure for `HARD_STONE` (sell -1 on 2026-10-27), so its coins
  are unknown at instant-sell; NPC sell values it at 1 coin.
- Hard Stone, gem and metal methods (MF Gems / Ruby / Topaz, Grotto, Tungsten, Umber, Shafting, Gold): amount unknown (no source for
  how much Hard Stone those routes break); listed as "amount unknown, not counted".
- Other grotto gems, Grotto: about 0.26 Breaking Power 7 Flawless per Flawless Jasper, the Fairy Grotto guide's Mansion ratio (3.1 per
  12; Palace 4.6 per 11), which the guide explains as mining the Breaking Power 7 gems around a grotto. The mix is unknown, so each one
  is valued at the cheapest of Flawless Amber, Sapphire, Jade and Amethyst (unknown if any of them has no price; NPC sell = Rough NPC
  x 512,000). Fixture: 1.76 / hour, about 3.1M coins / hour at instant-sell (Sapphire 1.79M), Grotto total 30.4M -> 33.5M.
- Frozen Corpse loot, Shafting: mineshafts / hour from the pity counter (wiki Glacite Mineshafts: counter 2,000, minus each block's Block
  Quality, Glacite 4, halved for Spread blocks, plus a 0.05% chance per block), assuming the modelled Glacite is mined in the Glacite
  Tunnels where blocks count. 1-3 corpses per shaft (average 2); Lapis 50% (no key, 3-6 rolls), Umber 25% and Tungsten 25% (one key
  each, 4-7 rolls) (wiki Frozen Corpse). Expected items per corpse = rolls x weight / total weight x amount from the wiki loot tables
  (Lapis 48 rows, Umber/Tungsten 37 rows, `MethodRates.LAPIS_LOOT` / `METAL_LOOT`; Frostbitten Dye left out, under 0.001% per roll).
  Keys are a cost at the Coflnet buy quote in every mode. A corpse whose fully priced loot is worth less than its key counts 0 (left
  unopened); when some loot is unpriced and the priced part is below the key, the value is unknown. Fixture: about 12.8 shafts / hour,
  12.8 Lapis corpses worth about 177k each (2.27M / hour); Umber/Tungsten corpses are unknown because the five Crystals have no
  Coflnet quote and the priced loot (about 2.0M) is below the key (about 2.7-2.9M). Shafting total 1.1M -> 3.4M coins / hour.
  Not counted: Surveyor, Vanguard corpses (Fairy shafts, Skeleton Key), extra-corpse chances, Glacite Powder and HOTM XP, mineshaft
  gemstones, time spent inside the shaft (part of the efficiency).
- Unpriced contents are left out and the value is marked "at least"; an amount that is unknown stays "amount unknown", a price that is
  unknown stays "coins unknown"; neither is shown as 0.

Change against the guide reference rates (not tuned): MF Gems' estimated hypermax drops about 0.5% for Heat (19.6 -> about 19.5 Amber /
hour at 60%, guide 20.1). Grotto, Coal, Obsidian, Gold, End Stone, Cobblestone, Mycelium and Red Sand rates are unchanged with their
default abilities (no Heat in the Crystal Hollows zone); the guides' Maniac Miner setups for End Stone / Cobblestone would add about 6-7%
(fixture +6.9%), so the 8.5M hypermax vs the guides' 11-16M gap stays mostly unexplained. Coal's guide Hard Stone (3-6M) is now counted
through its own ratio, by construction. The Grotto's other gems match the guide's ratio by construction.

## Coins per hour across methods

`core/` `MethodComparison`; calculated in the mod (`PlayerData.compare`, key `/compare?ign=&profile=&god=`); mod **Compare** tab.
- Setup per method: the automatic totals with that method's best owned gear and best tool (`Loadout.bestFor`/`bestTool`),
  its best HOTM tree and its usual zone and block target (`MiningMethod.defaultZone`), God Potion as toggled (default on, the
  owner's decision for the Breakdown). That method's Type Stats values override the totals and its efficiency is used
  (defaults 82.5% instamine blocks and Coal, 65% otherwise; Mining Cat developer, `docs/references/MININGCAT.md`). A
  Breakdown-tab zone or tool choice for a method is not used here; the Rates tab for that method follows it.
- Items / hour = `MethodRates.perHour` (sections "Rates: ..." above). Coins / hour = items / hour x coins per item. Ranked by
  coins / hour; methods without a usable price sort after every priced one and show "coins unknown", never 0, with their
  item rate. A method whose rate cannot be computed (for example Mining Speed 0) sorts last with the reason.
- Prices: Coflnet `GET /api/item/price/{tag}/current?minAgeSeconds=120` (<https://sky.coflnet.com/api/index.html>), refreshed about
  every 15 minutes by the backend (hosted API: Bazaar items from Hypixel's Bazaar, see Data sources); each card names the quote's source and `updatedAt`. Two labelled figures:
  - instant-sell (default) = Coflnet `sell`, selling into the highest buy order now;
  - sell offer = Coflnet `buy`, the lowest current sell offer, which a new sell order matches only when someone buys.
  Both are gross: the Bazaar sales tax is not applied (its current rate and account reductions are not verified here).
  When `buy` is more than twice `sell` the card warns that sell offers may not fill (thin market). Checked 2026-10-26:
  Red Sand (`SAND:1`) 3.2 sell / 133.7 buy, Mycelium (`MYCEL`) 1.9 / 47.3, Coal 4.4 / 7.7; `ENDER_STONE` had no sell figure,
  so End Stone's instant-sell coins were unknown. Crafting into enchanted items is not counted.
- NPC sell (third mode): `npc_sell_price` from the official items resource
  (<https://api.hypixel.net/v2/resources/skyblock/items>, no key), fetched by the backend at most every six hours and
  attached to the `/prices` quotes as `npc` with its fetch time. Block products use their own entry. The resource lists no
  NPC price for Flawless or Perfect gems, but Rough, Flawed and Fine sell for the same amount per Rough (checked 2026-10-27:
  Ruby/Amber/Topaz 3 / 240 / 19,200, Jasper 4 / 320 / 25,600, which are x80 per tier), so a gem method's NPC value per Flawless
  = the Rough gem's NPC price x 512,000, labelled so. An item without `npc_sell_price` stays unknown. Values checked
  2026-10-27: Coal 1, Obsidian 7, Tungsten 10, Umber 10, Glacite 12, Gold Ingot 3, End Stone 2, Cobblestone 1, Mycelium 5,
  Red Sand (`SAND:1`) 5. Fixture at NPC sell: MF Ruby about 21.3M / hour, Red Sand and Mycelium 19.5M, MF Gems 18.5M,
  Gold 16.6M, End Stone 9.4M; Glacite, Mycelium, Red Sand and Gold Ingot sell higher to an NPC than instant-sell.
- Priced product: the Flawless gem for gem methods (Rough converted at 512,000 Rough per Flawless, as in the rate model);
  the block product otherwise (`MethodRates.Block.product`). Secondary drops (Coal's Hard Stone, the Grotto's other gems, Shafting's
  corpse loot) add their known coins to the total, which Compare ranks by ("Rates: secondary drops"). Tungsten and Umber use
  the Dwarven Metal guide route's base drop of 3 (a labelled assumption; before 2026-10-28 they were "at least" at 1). Glacite's base drop is
  assumed 1 (not on the wiki). Gold uses the average base drop 6.
- Value windows (Mining Cat developer's notes, MININGCAT.md) normalized to one hour: Coal is valued per 30 minutes with two
  lobby hops; the model has no hop time of its own (hops are inside the efficiency), so per hour = 2 x the 30-minute value.
  Obsidian is valued per Mining Speed Boost cycle (cooldown from the tool lore); per hour = 3600 / cooldown cycles, which the
  hourly rate already averages. Shafting and the Dwarven Metals are per hour; gems are a one-hour average over the ability
  cycle. The Rates tab and each Compare card state the window.
- Same upgrade, different value: the Upgrades tab (rate-ranked methods) shows each upgrade's coins / hour here
  = (rate after - rate before) per hour x coins per item, and its gain in the best method by coins / hour =
  `MethodComparison.gain` (rate with the same stat delta and cooldown factor on that method's own stats / rate - 1) x that
  method's coins / hour. The delta is the one computed against the selected method's gear, so in the best method it is
  an approximation (that method's best gear may hold a different item in the slot). The Compare tab also lists +50 Mining
  Fortune and +100 Mining Speed per method. Fixture (65% / 82.5%, God Potion): +50 Mining Fortune is +2.5% on Coal and
  +2.4% on MF Gems, while +100 Mining Speed gives 0% wherever it crosses no tick threshold.
- Fixture with live quotes (2026-10-26, instant-sell): Grotto about 29.7M coins / hour (6.7 Flawless Jasper), MF Gems 24.3M,
  MF Topaz 21.8M, MF Ruby 20.7M, Coal 17.1M (3.89M Coal), Umber 13.5M, Red Sand 12.5M, Tungsten 11.0M, Obsidian 9.5M,
  Mycelium 7.4M, Gold 3.9M, Shafting 1.3M, Cobblestone 0.5M, End Stone unknown. At sell offers Red Sand and Mycelium jump
  to 522M and 185M, which is the thin-market spread, not income. None is validated against a measured session.

Coleweight: unknown, not shown. Coleweight is defined by the community Coleweight module (GitHub `Ninjune/coleweight`,
archived 2026-07-25); its repository's `data/` folder holds no weight coefficients (checked 2026-10-26: collections, hotm,
tick data, coordinates), and the weights it used came from the author's service, which we cannot verify as current or
official. No coefficient is encoded; the manual Rates scenario still accepts a user-entered Coleweight per item.

## Start with: best owned loadout

`core/` `StartWith`; calculated in the mod (`PlayerData.start`, key `/start?ign=&profile=&method=&tool=&zone=&target=&god=`); mod **Start with** tab. Added 2026-10-27 (owner request, roadmap 4).
- Best owned loadout: the same choice as the Breakdown tab's default, without a second ranking: `Loadout.bestFor` with
  `Loadout.bestTool` (one copy per armor/equipment slot from all readable storage by the method weighting `Loadout.weigh`, one
  tool, the best catalogued pet, one accessory per family) and `StatBreakdown.chooseTree` for the HOTM tree. Slots are chosen
  independently, so a set bonus can be broken (as in the Breakdown). Accessories are listed only when their unconditional stat
  lines add one of the method's weighted stats; the rest are counted, not listed.
- Current loadout: `Loadout.equipped` (worn armor and equipment, accessory bag, active pet). The held tool is not in the API:
  the player's Tool choice (shared with the Breakdown tab) is the held tool; without one the current loadout assumes the best
  owned tool and no tool swap is listed (said so on the tab). The active HOTM tree is not in the API either: the current
  loadout uses its own best tree, and a different best tree for the best loadout is listed as a "HOTM tree" swap.
- Swaps: per armor/equipment slot, tool, pet and accessory family where the best loadout holds a different copy. Each swap is
  evaluated alone: the current loadout with only that piece changed, same tree and context, all supported stats recomputed with
  `StatBreakdown.compute`. Swaps that change none of the method's stats and not the tool ability are dropped (ties). The mod
  rates each with `MethodRates.perHour` at the method's efficiency and the tool's ability, prices it with the selected price
  mode (`MethodComparison.coinsPerItem`; unknown stays "coins unknown"), sorts by rate after the swap and shows the total of
  current vs best owned loadout. Single gains need not add up to the total (set bonuses, tick thresholds). Type Stats overrides
  are not applied (they would hide gear differences); the tab says so when the method has any.
- Storage (`StartWith.where`): `inv_armor` = worn, `equipment_contents` = worn (equipment), `inv_contents` slots 0-8 = hotbar,
  else inventory; wardrobe slot = page x 9 + slot % 9 + 1 with 36 items per page (4 rows of 9); ender chest page = slot / 45 + 1;
  backpack keys count from 0 (backpack slot key + 1); `loadout armor|equipment set N` = Loadouts set N; pets: active pet / pets
  menu. Container layouts follow the profile API's item order (owner's snapshot: a piece moved to wardrobe index 40 reads as slot
  14); not cross-checked in game yet.
- Best-method hint: the report also carries every rated method's totals with the gear worn now in that method's usual zone
  (same God Potion flag); the mod ranks them like the Compare tab (Type Stats values and efficiency per method) and shows the
  top method next to the Compare tab's top method with the best owned gear. When the Compare tab's God Potion toggle differs,
  the hint says so.
- Inventory API off: ownership of gear and accessories is unknown (shown), only the pet can be compared; pet data off is shown too.
- Owner's snapshot (2026-10-27, scratch runner): Debotes already wears the best owned armor, equipment, pet and accessories for MF
  Gems, Coal and Grotto, so only the tool can differ. With the helmet moved to the wardrobe, the necklace to the ender chest, the
  Mineral Talisman to a backpack and no active pet, MF Gems lists four swaps (helmet restores the Divan set bonus: +161.6 Mining
  Fortune, +2 Pristine; Bal with Quick Claw; necklace; talisman) with their storage locations.

## Other upgrades and progression

Added 2026-10-27. The Upgrades tab's **View** toggle switches between Purchases (unchanged) and **Other & progress**:
three cards computed by `OtherUpgrades.of` on the analysis worker (no new endpoint; `/stats` gains `petLevel`). Gains use the
same rate model and baseline as the Purchases list (`MethodComparison.gain` on the automatic stats, Type Stats overrides
included; the drop multiplier when no rate can be computed).

**Can't check: skip if you already have it.** Permanent bonuses the profile API never reports, or whose key has never been
observed; a missing field is never treated as proof the bonus is absent, and none of them is counted in the stat ledger
(they are its "Not in the API" rows). Each shows its effect, the Coflnet buy quote for the whole amount (unknown stays
"Price unknown") and the rate gain of the full bonus in the selected method; bonuses that change nothing there are counted
as hidden. Sources (wiki pages fetched 2026-10-27 through the wiki API, <https://hypixelskyblock.minecraft.wiki/w/...>):
- Dwarven O's Gemstone Grahams / Ore Oats / Block Bran / Metallic Minis (item pages): +2 Gemstone / Ore / Block / Dwarven
  Metal Fortune for each of the first 5 eaten (+10). Bought as 5 x the bazaar quote (IDs `DWARVEN_OS_*`). Drops: Crystal
  Nucleus 0.47% (Grahams), Dwarven Mines Mining Events (Oats), Scatha 10% (Bran), Umber/Tungsten 0.31% and Vanguard 3.41%
  corpses (Minis).
- Gold Box (Hideonsun Shard, Common): +5 to +50 Ore Fortune; level 10 = 96 shards (`SHARD_HIDEONSUN`, cumulative Common
  table above). Mining Cat's "Hideosun" is this shard. Read from `attributes.stacks.gold_box` (key confirmed
  2026-10-28) and offered in Upgrades with the shards still needed.
- Royal Riches (Nilbog Shard, Epic, Hunting 15): +5 to +50 Mining Speed and +2.5 to +25 Ore Fortune in the Mines of Divan;
  level 10 = 32 shards (`SHARD_NILBOG`). Listed only for methods whose usual zone is the Crystal Hollows (Coal, Gold, Grotto,
  Obsidian); the gain assumes the player mines in the Mines of Divan, which the engine has no zone for.
- Dwarven Expertise (Essence Shops/Fossil): +2 Dwarven Metal Fortune per level on Mining Islands, 5,500 Fossil Essence for
  levels 1-10 (`ESSENCE_FOSSIL`). Read from `player_data.perks.dwarven_expertise` (key
  confirmed 2026-10-28) and offered in Upgrades from the current level.
- Left out after checking: Refined Dark Cacao Truffle (the checklist's "Dark Cocoa Truffles") gives Cocoa Beans Fortune, a
  farming stat; Chilled Pristine Potato is a 1-hour buff (+0.1 Pristine in the Glacite Mineshafts), not permanent; Reaper
  Pepper is read from the API (stat ledger).

**Progress you have to grind.** Ranked by gain alone (coin cost is not part of the ranking; the right column says
"Grinding"); gains that cannot be modelled say "not modelled" and sort last. Each entry shows the gain, the requirement, the
distance from the current value and how to get it:
- HOTM perk levels and unlocks, Mining skill and collections (`ProfileUpgrades`, rules above): per chain the nearest level that
  powder, tokens, XP or collection items must pay for (or that needs a higher HOTM tier), with the powder still needed and the
  unspent amount, tokens, or XP/items left. Levels the balances already cover stay on Purchases as free.
- Pet level: the evaluated pet with its pet item at level 100 minus now (`StatBreakdown.petStats`), XP still needed from the
  pet level table (NEU `constants/pets.json`, `Loadout.petExp`). Fixture: Legendary Bal 97 -> 100 needs 4,452,467 more XP
  (20,900,763 of 25,353,230) for +5 Mining Fortune, +2 Mining Speed (+0.09 Pristine in the Magma Fields): +0.76% MF Gems,
  +0.25% Coal. Mining Cat's Coal page lists +3 Fortune and +0.15% for the same step; not tuned (different pet stat rule or baseline).
- Compact (wiki Compact): a stacking enchantment, tiers I-X at 0 / 100 / 500 / 1,500 / 5,000 / 15,000 / 50,000 / 150,000 /
  500,000 / 1,000,000 blocks mined (`compact_blocks` on the tool), +1 Mining Wisdom per tier and 0.25 / 0.27 / 0.29 / 0.31 /
  0.33 / 0.36 / 0.40 / 0.44 / 0.50 / 0.57% enchanted item chance. Compact I costs 4,000 Bits (Elizabeth) or the Bazaar. The
  chance does not work on Gemstones (wiki trivia), so gem methods show +0%; for other methods the gain is "not modelled",
  because the wiki does not say how the enchanted item roll combines with Fortune and Spread. Fixture: DR-X655 Compact 9 with
  809,548 blocks, 190,452 to X. Mining Cat shows +0.58% for Compact X on Coal; not copied.
- Forge-only items: catalog items with a forge recipe (`mining-recipes.json`) and no Coflnet buy quote that beat the owned item
  of their slot, with the recipe's HOTM tier, inputs owned or missing, forge time and the missing materials' price (shown,
  not ranked). A Power accessory is valued with Perfect Jade, Amber and Topaz in its slots at its rarity, accessory gems at half
  stats. Fixture: Relic of Power (Epic) +11.5 Mining Fortune, +23 Mining Speed, +1 Pristine over no Power accessory: +6.3% MF
  Gems, +7.4% Grotto; forged from Artifact of Power + Perfect Plate + 32 Glacite Amalgamation, 8 h, HOTM 10. Mining Cat lists
  +15 Fortune / +30 Speed with Perfect Jade and Amber; the checklist says full Perfect gems raise its rarity. Neither is verified,
  so the Epic values stand.

**Guide stage** (`OtherUpgrades.stage`): the profile is placed on the supplied checklist
(docs/references/PROGRESSION-CHECKLIST.md) from its stage headings only, using items the API shows anywhere: End game = HOTM 10,
Pendant of Divan and a DR-X655 or Divan's Drill; Mid game = all four Divan pieces and a DR-X655 or Divan's Drill; Early game =
all four Divan pieces, a DR-X555 or better, or HOTM 7; otherwise "Before the checklist". The card lists that stage's next
checklist items (expandable), marking "(you own one)" only where the API shows the item. It is orientation, not a fixed
order, and the checklist's unticked cells are not profile facts; personalized steps are the ranked lists. Fixture: Mid game
(HOTM 10, Divan armor, DR-X655).

## Session tracker

`core` `SessionTracker` (rules) and the client's `SessionWatcher` (chat and inventory glue). It measures one method's real
gains and compares them with the rate model; it only reads chat and the player's own inventory and sends nothing.

**What counts.** Each method counts its product and that product's compacted forms, converted to base items with the NEU
recipes (NotEnoughUpdates-REPO `items/*.json`, checked 2026-10-27): Enchanted X = 160 X (Coal, Gold Ingot, Obsidian, Umber,
Tungsten, Glacite, End Stone, Cobblestone, Mycelium, Red Sand); Enchanted Coal Block, Enchanted Gold Block, Enchanted Mycelium
Cube and Enchanted Red Sand Cube = 160 of the Enchanted form; gems Rough -> Flawed -> Fine -> Flawless = 80 each (Flawless =
512,000 Rough, as in the rate model); Enchanted Hard Stone = 576 Hard Stone, Concentrated Stone = 576 Enchanted Hard Stone.
Display names come from the official items resource (`/v2/resources/skyblock/items`), for example "Enchanted Gold Ingot",
"Rough Amber Gemstone". MF Gems counts Amber, Sapphire, Jade and Amethyst as its product; MF Ruby / MF Topaz their gem;
Grotto counts Jasper as the product and the four Breaking Power 7 gems as secondary (as the model's secondary drop). Hard
Stone is a secondary drop on every Crystal Hollows, Dwarven Mines and Glacite method. Mineshaft corpse loot is not counted.

**Sources of gains.**
- Sack summaries: the chat line `[Sacks] +N items. (Last Ns.)` (also `+N items, -M items.`), sent at most every 30 s. Its
  item list is the hover text: `Added items:` then lines `+20 Enchanted Gold Ingot (Enchanted Mining Sack)`; removed items
  start with `-` and are ignored. Formats from the Hypixel chat as documented in comments of open-source mods
  (SkyblockAPI `SacksAPI`, SoulMod `SackChatReader`; formats only, no code copied).
- Inventory: every 0.5 s the net base items of each counted chain in the 36 main slots are compared with the last check;
  an increase is a gain, a decrease (selling, storing) is ignored. So Compact turning 160 Coal into 1 Enchanted Coal counts
  nothing. Items are matched by their SkyBlock id (custom data `id`), else by name. No comparison while a container screen is
  open (the next check starts fresh), on a new world, or for 5 s after `/gfs`, `/sacks`, `/pickupstash`, `/storage` and similar
  commands that move items into the inventory.
- Pristine: `PRISTINE! You found ❈ Flawed Amber Gemstone x28!` (a gem symbol before the name; wiki Pristine: each Pristine
  point is +1% chance per Rough of a Flawed). Procs and their Flawed are shown, but the gems are counted only once, when they
  reach the inventory or a sack.

**Time.** Auto-start (on by default) begins a session on the first product gain, back-dated by the sack summary's window
(`Last Ns`, at most 30 s); Start begins one by hand. After 60 s without a product gain (sack summaries can be 30 s apart; set
`trackerIdleSeconds`, 10-3600, in `config/skyprogression.json`) the session pauses and its active time ends at the last
gain; the next product gain resumes it, back-dated at most to that last gain. Secondary drops count only while it runs. Stop
ends the active time at once; a sack summary arriving within its window after Stop still counts (it covers earlier mining).
Uptime = active / elapsed.

**Rates.** Measured product per hour = product in the model's unit (Flawless for gems, else the block product) / active hours.
Coins per hour values each counted family at its priced item (Flawless gem or base product) in the selected price mode, the same
quotes the Methods tab uses; an unpriced product makes the coins "no price", an unpriced secondary is left out and marks the
total "at least". The model column is the method's rate with the current stats, efficiency, tool ability and zone (as Rate now),
and its coins add the modelled secondary drops. Measured / model x efficiency is the efficiency at which the model would give
the measured rate.

**Known errors.** Moving tracked items from the inventory into sacks during a session counts them twice (once picked up, once in
the summary). Items dropped and picked up again, or received in trades outside a container screen, count as gains. Gains while
the selected method is another method are not counted. Saved sessions (Save session, `config/skyprogression-sessions.json`)
hold the method, times, counts, measured and model values and a stat/tool/zone/profile snapshot as validation evidence.

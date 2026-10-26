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

Cost per stat point = full quoted purchase cost / positive stat delta.
No resale credits, recipe discounts or modifier transfers are assumed. Supported socket fees are included.

## Drop and rate model

Fortune multiplier = `1 + (Mining Fortune + relevant specialized Fortune) / 100`.
Spread multiplier = `1 + relevant Spread / 100` (assumes extra blocks can be mined).
Pristine multiplier for gems = `1 + 0.79 * Pristine`; otherwise 1.
Combined yield is the product of those multipliers.

Estimated improvement % = `100 * (yield after / yield before - 1)`.
Cost per 1% = `purchase cost / estimated improvement %`.
Speed/cold-only changes are available in stat queries, not assigned imaginary drop gains.

Items/hour = `3600 * efficiency/100 * active blocks/sec * base drops/block * yield`.
Coins/hour and Coleweight/hour multiply that rate by the user-entered per-item values.
These are gross scenario estimates. They are not session tracking or the bot's proprietary calculations.

## Data sources

- Hypixel item definitions: <https://api.hypixel.net/v2/resources/skyblock/items>
  (base item snapshot fetched 2026-10-26).
- Hypixel profile/inventory API and encoding: <https://api.hypixel.net/>
- Mining-level XP and collection tiers use official `/v2/resources/skyblock/skills`
  and `/v2/resources/skyblock/collections` snapshots from 2026-10-26.
- Coflnet `GET /api/item/price/{itemTag}/current`:
  <https://sky.coflnet.com/api/index.html>. Quotes request listings at least 120 seconds old.
- Pristine expected-drop model:
  <https://hypixelskyblock.minecraft.wiki/w/Pristine>.
- Lantern increments were supplied in the user's Mining Cat screenshot. They are explicitly
  modeled as mutually exclusive deployed bonuses and should be rechecked after game patches.
- Fabric 26.1 migration: <https://fabricmc.net/2026/03/14/261.html>.

Mining Cat and SkyCompass inspired the requested workflow. Their code and computed profile
output are not bundled, scraped as a dataset, or used as an undocumented runtime dependency.

## Extending the mod

Add validated item data to `core/src/main/resources/mining-items.json` and add regression
tests for new rules. A complete gear evaluator needs modifier parsing, condition-aware
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
are included, and removed gems receive no resale credit. Missing ingredient prices make
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

`core/` `Loadout`, `StatContext`, `StatBreakdown` and `Breakdown` build a source ledger. The companion
serves it as `GET /stats?ign=&profile=&method=&gear=best|equipped&tool=&zone=&target=&god=` (`StatsReport`:
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
  Front Loaded, Mineshaft Mayhem and Eager Adventurer are not encoded: their API keys are unobserved.
  The Blue Cheese omelette's +1 perk level is not applied.
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

# Profile API fields for mining stat sources

Inspected 2026-10-26 from the private Debotes · Zucchini snapshot
(`service-data/debotes-profiles.json`, Hypixel `v2/skyblock/profiles`). This maps where
each Stat Breakdown source lives in the raw member object. It records field locations
and observed shapes only; no stat values or formulas are derived here.

The committed fixture `core/src/test/resources/fixtures/debotes-zucchini.json` keeps
just these fields. Regenerate it after refetching the snapshot with
`./gradlew :core:extractFixture` (see `FixtureExtractor`); it removes player, profile,
item and gem UUIDs, timestamps and skull textures, and keeps only mining tools from
the inventory and a handful of accessories.

| Source | Raw field | Notes |
| --- | --- | --- |
| HOTM tier / perks | `skill_tree.experience.mining`, `skill_tree.nodes.mining.<perk>` | Current location. Each perk has a `toggle_<perk>` flag (false = disabled, counts nothing). `mining_core.experience`/`nodes` are absent on this profile; the parser keeps them only as a legacy fallback. |
| Peak/core level | `skill_tree.nodes.mining.core_of_the_mountain` | |
| Extra HOTM trees | `skill_tree.nodes.mining_2..mining_5` | Trees from the Loadouts system (wiki "Loadouts", added June 24, 2026), which swaps HOTM trees. On this profile they hold only `core_of_the_mountain`. No field marks the active tree. |
| Loadouts gear sets | `loadout.armor.<n>.<SLOT>`, `loadout.equipment.<n>.EQUIPMENT_SLOT_<k>` | Each slot is `{type, data}` holding one base64 gzip NBT item; `equipped_set` names the worn set (empty here: the worn pieces are in `inv_armor`/`equipment_contents`). Read as owned storage. |
| Selected pickaxe ability | `skill_tree.selected_ability.mining` | e.g. `mining_speed_boost`. |
| Powder | `mining_core.powder_<type>`, `powder_spent_<type>`, `powder_<type>_total` | `powder_<type>` is the unspent powder (owner: matches the game, 21.2M Gemstone); `_total` is lower than spent here and its meaning is unknown. |
| Daily HOTM effect | `mining_core.current_daily_effect` | Likely the Sky Mall buff of the current SkyBlock day (20 real minutes): `powder_bonus` at the first snapshot, `dig_speed` (probably +100 Mining Speed) at a refetch on 2026-10-26. The code-to-buff mapping is not yet verified. |
| Mining skill | `player_data.experience.SKILL_MINING` | Skill level → base Mining Fortune. |
| Essence/other shop perks | `player_data.perks` | Mining-relevant keys observed: `dwarven_training`, `eager_miner`, `treasures_of_the_earth`, `rhinestone_infusion`, `under_pressure`. Effects still need primary-source verification. |
| Temporary effects | `player_data.active_effects`, `temp_stat_buffs`, `paused_effects`; `profile.cookie_buff_active` | Empty on this snapshot, and still empty at a 2026-10-26 refetch while the owner had a Fiesta Flask active: the Flask is not reported. God Potion/Mayor effects are not proven to appear here: keep them untrackable unless observed. |
| Attributes (Hunting shards) | `attributes.stacks.<name>` | Stack counts, not levels. Mining candidates: `fortunate_miner`, `deep_technique`, `pickaxe_cooldown`, `devouring_spread`, `glossy_fortune`, `atomized_mithril`, `atomized_crystals`, `cavern_wisdom`, `quartz_speed`. Stack→level and effect tables still need research. |
| Accessories | `inventory.bag_contents.talisman_bag` (NBT), `accessory_bag_storage` | Bag stores lore totals; `highest_magical_power`, `selected_power` and `tuning` are stored separately. |
| Armor | `inventory.inv_armor` (NBT) | Equipped set. Wardrobe is `wardrobe_contents` with `wardrobe_equipped_slot` (absent contents on this snapshot). |
| Equipment | `inventory.equipment_contents` (NBT) | Necklace/cloak/belt/gloves. Lore lists situational lines (e.g. True Dwarf set bonus "while in the Dwarven Mines"). |
| Tools | `inventory.inv_contents` etc. (NBT) | The held tool is not exposed; choosing the tool is a loadout decision. Drill parts: `drill_part_engine`, `drill_part_fuel_tank`, `drill_part_upgrade_module`; `polarvoid`, `compact_blocks`, `enchantments`, `gems`, `modifier`, `rarity_upgrades`. |
| Pets | `pets_data.pets[]` (`type`, `tier`, `exp`, `active`, `heldItem`, `candyUsed`) | Level derives from `exp` and rarity. Active pet observed: BAL Legendary with Quick Claw. |
| Collections | `collection.<ID>` | Mining collections include `MITHRIL_ORE`, `GEMSTONE_COLLECTION`, `HARD_STONE`, `GLACITE`, `TUNGSTEN`, `UMBER`. |
| Fiesta progress | `leveling.mining_fiesta_ores_mined` | Not a stat by itself. |
| Mayor / minister perks | `v2/resources/skyblock/election` → `mayor.perks[]`, `mayor.minister.perk` | Official resource, no key. Descriptions carry the amounts (2026-10-27: Cole, "Gain +25☯ Mining Wisdom, +25% Mining Fortune"). Read by the companion for temporary ledger lines. |
| Power scroll | tool `ExtraAttributes.power_ability_scroll` | e.g. `AMBER_POWER_SCROLL` (key from the wiki item ID; the fixture's tools hold none). |
| Fuel tank | tool `drill_part_fuel_tank` | Its lore paragraph shows `-4% Pickaxe Ability Cooldown`; the ability's `Cooldown: 108s` line leaves the tank out (a Golden Pickaxe without one shows 108 s too). |
| Reaper Pepper | `player_data.reaper_peppers_eaten` | Confirmed 2026-10-28 (see below); absent on this profile, read as 0 eaten. |
| HOTM perks with new stats | `seasoned_mineman`, `keep_it_cool`, `warm_heart`, `powder_buff` | Observed (100 / 25 / 1 / 25 on this profile). `mineshaft_mayhem` and `front_loaded` confirmed 2026-10-28 (see below). |
| Attributes with new stats | `cavern_wisdom`, `pickaxe_cooldown`, `yog_membrane`, `atomized_mithril`, `atomized_crystals`, `fiesta_fortune` | Observed. `cold_resistance` (Wumpa) and `atomized_glacite` (Stalagmight) confirmed 2026-10-28 (see below). |
| Essence perks with new stats | `dwarven_training` (observed, 3) | `frozen_skin` (Ice Essence) confirmed 2026-10-28 (see below). |
| Glacite progress | `glacite_player_data.fossils_donated`, `corpses_looted`, `mineshafts_entered` | Fossil donations relate to permanent rewards; effect not yet mapped. |

Observed caveats:
- Resolved 2026-10-26: `polarvoid: 5` is the Polarvoid Book count, shown as the bracketed
  `[+50]` Speed / `[+5]` Fortune on the drill's stat lines. "Prismatic V" in the lore is the
  enchantment stored as `enchantments.pristine: 5`.
- Gem objects can be `{quality: PERFECT}` (with UUID) or a plain quality string
  (`POWER_RING` uses `"RUBY_0": "FINE"`). Parsers must accept both shapes.
- Lore totals reflect the item's last rendered context; situational lines are stored as
  text and are not included in the headline stat numbers.
- Mayor perks come from `v2/resources/skyblock/election` (no key needed). On 2026-10-26 the mayor was Cole
  (Prospection, Mining Fiesta, Molten Forge). Whether the Fiesta event is running at a given time
  depends on the SkyBlock calendar and is not yet computed.

## Key verification (2026-10-28)

The stat sources added on 2026-10-27 used keys taken from in-game names. Each one was checked
against the owner's private snapshot and open data that maps the same fields. Only key names
were taken from these sources; no code or values.

Sources:
- **Snapshot:** `service-data/debotes-profiles.json` (private, Git-ignored).
- **NEU REPO:** [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) at commit
  `2a30ed91c5b427f7ff44bac9e3d8a6d1916e2f02` (2026-10-28), files `constants/attribute_shards.json`,
  `constants/essenceshops.json` and `constants/hotmlayout.json`.
- **Skyblocker:** [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) at commit `ca1163e2` (2026-10-19), file
  `src/main/java/de/hysky/skyblocker/skyblock/profileviewer2/model/PlayerData.java`. It reads `player_data.perks` and
  `player_data.reaper_peppers_eaten`.
- **NEU:** [NotEnoughUpdates](https://github.com/Not-Enough-Updates-Dev/NotEnoughUpdates) at commit `29056776`, file
  `profileviewer/data/APIDataJson.java`, `PlayerData.reaper_peppers_eaten`.

How each group was checked:
- **Attributes:** `attributes.stacks.<key>` is the shard's `internalName` (`ATTRIBUTE_SHARD_<KEY>;1`) in lower case. All 84
  keys in the snapshot match an NEU entry by this rule. Some keys are not the ability name: `maximal_torment` is "Unlimited
  Torment", for example. That is why name-based guesses had to be checked one by one.
- **Essence perks:** `player_data.perks.<key>` uses the NEU `essenceshops.json` perk keys. All nine shop perks in the
  snapshot match. One of them, `treasures_of_the_earth`, is "Treasure of the Earth" in game.
- **HOTM nodes:** `skill_tree.nodes.mining.<key>` uses the NEU `hotmlayout.json` perk keys. All 26 nodes in the snapshot match.

| Key | Status | Evidence |
| --- | --- | --- |
| `ultimate_dna` | Confirmed | NEU `ATTRIBUTE_SHARD_ULTIMATE_DNA` (Galaxy Fish, Legendary) |
| `sleepy_spread` | Confirmed | NEU `ATTRIBUTE_SHARD_SLEEPY_SPREAD` (Snoozle, Rare) |
| `gold_box` | Confirmed | NEU `ATTRIBUTE_SHARD_GOLD_BOX` (Hideonsun, Common) |
| `royal_riches` | Confirmed | NEU `ATTRIBUTE_SHARD_ROYAL_RICHES` (Nilbog, Epic) |
| `greedy_goblin` | Confirmed | NEU `ATTRIBUTE_SHARD_GREEDY_GOBLIN` (Gimmiegold, Rare) |
| `freezing_spread` | Confirmed | NEU `ATTRIBUTE_SHARD_FREEZING_SPREAD` (Littlefoot, Epic) |
| `echo_of_boxes` | Confirmed | NEU `ATTRIBUTE_SHARD_ECHO_OF_BOXES` (Cuboa, Uncommon) |
| `echo_of_wisdom` | Confirmed | NEU `ATTRIBUTE_SHARD_ECHO_OF_WISDOM` (Wyvern, Legendary) |
| `echo_of_atomized` | Confirmed | NEU `ATTRIBUTE_SHARD_ECHO_OF_ATOMIZED` (Iguana, Epic) |
| `echo_of_echoes` | Confirmed | NEU `ATTRIBUTE_SHARD_ECHO_OF_ECHOES` (Tiamat, Legendary) |
| `cold_resistance` | Confirmed | NEU `ATTRIBUTE_SHARD_COLD_RESISTANCE` (Wumpa, Legendary) |
| `atomized_glacite` | Confirmed | NEU `ATTRIBUTE_SHARD_ATOMIZED_GLACITE` (Stalagmight, Rare) |
| `dwarven_expertise` | Confirmed | NEU `essenceshops.json` `ESSENCE_FOSSIL.dwarven_expertise` (10 levels) |
| `cut_loose` | Confirmed | NEU `ESSENCE_FOSSIL.cut_loose` (5 levels) |
| `fungus_fortuna` | Confirmed | NEU `ESSENCE_CRIMSON.fungus_fortuna` (10 levels) |
| `harena_fortuna` | Confirmed | NEU `ESSENCE_CRIMSON.harena_fortuna` (10 levels) |
| `frozen_skin` | Confirmed | NEU `ESSENCE_ICE.frozen_skin` (5 levels) |
| `front_loaded` | Confirmed | NEU `hotmlayout.json` `front_loaded` (x 6, y 6, max 1) |
| `mineshaft_mayhem` | Confirmed | NEU `hotmlayout.json` `mineshaft_mayhem` (x 6, y 2, max 1) |
| `player_data.reaper_peppers_eaten` | Confirmed | Skyblocker `PlayerData` and NEU `APIDataJson.PlayerData`; both read a missing field as 0 |

None of these keys appear in the snapshot, because the owner owns none of these sources. No key was wrong, so totals did not
change. The rarities in the engine also match NEU. For Fiesta Fortune (`fiesta_fortune`, Packrat Shard), NEU and the wiki's
Packrat Shard page both give **Uncommon**.

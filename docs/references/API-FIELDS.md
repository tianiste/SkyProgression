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
| Peak/core level | `skill_tree.nodes.mining.core_of_the_mountain`; `mining_2..5` | Extra trees each hold `core_of_the_mountain`; meaning not yet researched. |
| Selected pickaxe ability | `skill_tree.selected_ability.mining` | e.g. `mining_speed_boost`. |
| Powder | `mining_core.powder_<type>`, `powder_spent_<type>`, `powder_<type>_total` | `_total` is lower than spent here; do not treat it as lifetime earned without research. |
| Daily HOTM effect | `mining_core.current_daily_effect` | e.g. `powder_bonus`. Situational. |
| Mining skill | `player_data.experience.SKILL_MINING` | Skill level → base Mining Fortune. |
| Essence/other shop perks | `player_data.perks` | Mining-relevant keys observed: `dwarven_training`, `eager_miner`, `treasures_of_the_earth`, `rhinestone_infusion`, `under_pressure`. Effects still need primary-source verification. |
| Temporary effects | `player_data.active_effects`, `temp_stat_buffs`, `paused_effects`; `profile.cookie_buff_active` | Empty on this snapshot. God Potion/Mayor effects are not proven to appear here: keep them untrackable unless observed. |
| Attributes (Hunting shards) | `attributes.stacks.<name>` | Stack counts, not levels. Mining candidates: `fortunate_miner`, `deep_technique`, `pickaxe_cooldown`, `devouring_spread`, `glossy_fortune`, `atomized_mithril`, `atomized_crystals`, `cavern_wisdom`, `quartz_speed`. Stack→level and effect tables still need research. |
| Accessories | `inventory.bag_contents.talisman_bag` (NBT), `accessory_bag_storage` | Bag stores lore totals; `highest_magical_power`, `selected_power` and `tuning` are stored separately. |
| Armor | `inventory.inv_armor` (NBT) | Equipped set. Wardrobe is `wardrobe_contents` with `wardrobe_equipped_slot` (absent contents on this snapshot). |
| Equipment | `inventory.equipment_contents` (NBT) | Necklace/cloak/belt/gloves. Lore lists situational lines (e.g. True Dwarf set bonus "while in the Dwarven Mines"). |
| Tools | `inventory.inv_contents` etc. (NBT) | The held tool is not exposed; choosing the tool is a loadout decision. Drill parts: `drill_part_engine`, `drill_part_fuel_tank`, `drill_part_upgrade_module`; `polarvoid`, `compact_blocks`, `enchantments`, `gems`, `modifier`, `rarity_upgrades`. |
| Pets | `pets_data.pets[]` (`type`, `tier`, `exp`, `active`, `heldItem`, `candyUsed`) | Level derives from `exp` and rarity. Active pet observed: BAL Legendary with Quick Claw. |
| Collections | `collection.<ID>` | Mining collections include `MITHRIL_ORE`, `GEMSTONE_COLLECTION`, `HARD_STONE`, `GLACITE`, `TUNGSTEN`, `UMBER`. |
| Fiesta progress | `leveling.mining_fiesta_ores_mined` | Not a stat by itself. |
| Glacite progress | `glacite_player_data.fossils_donated`, `corpses_looted`, `mineshafts_entered` | Fossil donations relate to permanent rewards; effect not yet mapped. |

Observed caveats:
- Resolved 2026-10-26: `polarvoid: 5` is the Polarvoid Book count, shown as the bracketed
  `[+50]` Speed / `[+5]` Fortune on the drill's stat lines. "Prismatic V" in the lore is the
  enchantment stored as `enchantments.pristine: 5`.
- Gem objects can be `{quality: PERFECT}` (with UUID) or a plain quality string
  (`POWER_RING` uses `"RUBY_0": "FINE"`). Parsers must accept both shapes.
- Lore totals reflect the item's last rendered context; situational lines are stored as
  text and are not included in the headline stat numbers.

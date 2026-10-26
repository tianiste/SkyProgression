# Mining Speed tick thresholds

Source: community tick-threshold guide supplied by the owner on 2026-10-26 (last updated 16 September
2026; math credited to catsareverycool, older guide by Ehnest), together with the wiki's
[Block Strength](https://hypixelskyblock.minecraft.wiki/w/Block_Strength) page. This file summarizes the rules and the block
strengths the guide's tables imply. It does not reproduce the full tables.

## Rules (match `MethodRates.ticks` / `nextTickSpeed`)

- 20 ticks per second. Ticks to break = `round(30 x strength / Mining Speed)`.
- Speed needed for `t` ticks: Mining Speed **greater than** `30 x strength / (t + 0.5)`. The guide's
  tables list the smallest whole number above that value. When the quotient is a whole number, they list
  that number itself as a strict "greater than" (for example Jade, 22 ticks: > 4,000).
- 4 ticks is the soft cap. Below it, only instamining (1 tick) is faster, which needs Mining Speed
  greater than `60 x strength`. Without that, the block takes 4 ticks.
- Mid-air: for blocks that can be instamined, the guide's "while in the air" values are 5 x the ground
  value + 1 (Coal ore 9,001, Hard Stone 7,501, Cyan Terracotta Hard Stone 15,001). This matches the
  airborne speed / 5 rule in `RATE-MODEL.md`.

Check (2026-10-26): every threshold in the guide's tick tables for the 13 strengths below
(210 values) equals the smallest Mining Speed our formula needs for that tick count. That
includes the Jasper 4-tick value (> 32,000), so exactly 32,000 gives 5 ticks, as we model it.

## Block strengths implied by the tables

| Strength | Blocks |
| --- | --- |
| 2,300 | Ruby |
| 3,000 | Jade, Amber, Sapphire, Amethyst, Opal |
| 3,800 | Topaz |
| 4,800 | Jasper |
| 5,200 | Onyx, Aquamarine, Citrine, Peridot |
| 5,600 | Tungsten, Umber |
| 6,000 | Glacite |
| 3,500 | Anthill (Torrhus Canyon) |
| 2,000 | Titanium |
| 1,500 | Mithril (Light Blue Wool) |
| 800 | Mithril (Prismarine) |
| 600 | Pure ores, Quartz |
| 500 | Obsidian, Sulphur, Mithril (Cyan Terracotta, Gray Wool) |
| 50 | Hard Stone (wiki; instamine 1,501) |
| 30 | Normal ores (Coal, Gold, Iron ... ore blocks; instamine 1,801), End Stone (instamine 901) |
| 20 | Cobblestone (wiki; instamine 601) |
| 15 | Stone (wiki; instamine 451) |
| 8 | Netherrack (wiki; instamine 241) |

Wiki check (Block Strength page, 2026-10-26): instamining needs Mining Speed above 60 x strength on ores, Obsidian,
gemstones and Dwarven Metal blocks, and above 30 x strength on other blocks. The guide-derived strengths for
non-ore blocks listed only with an instamine value were first computed as instamine / 60 and came out at half the
wiki value (Cobblestone 10, Stone 7.5, Netherrack 4, Hard Stone 25). The wiki values above replace them; the
thresholds themselves are unchanged. Confirmed on the wiki: Coal Ore 30, Obsidian 500, End Stone 30, Cobblestone 20,
Hard Stone 50, Pure Gold/Coal blocks 600, Tungsten and Umber 5,600, Glacite 6,000. The wiki lists no strength for
Mycelium or Red Sand (only a theorized vanilla hardness of 0.6 / 0.5).

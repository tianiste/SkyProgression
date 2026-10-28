# Mining Cult guides compared with the engine

Our own summary of the Mining Cult Discord `mining-guides` forum (owner-supplied export of 41 threads, taken 2026-10-28;
the guide authors gave the owner permission), checked against SkyProgression's rules on 2026-10-28. Guides are
community advice, not mechanics: a guide number is a lead until the wiki, the official Hypixel resources or the NEU
repository confirm it. The raw text stays private (Git-ignored `service-data/guides/discord/`); this page only
summarizes. Rules and sources are in [CALCULATIONS.md](../CALCULATIONS.md), open gaps in [LIMITATIONS.md](../LIMITATIONS.md).

## Guides used (author, last edit)

| Guide | Author (Discord display name) | Last edit |
| --- | --- | --- |
| Obsidian | Mycelium Larper | 2026-10-25 |
| Mycelium & Red Sand | Mycelium Larper | 2026-10-25 |
| Cobblestone, Endstone | FazeHunter(Ji) | 2026-09-30 |
| Updated Gold Mining | flora (miningfemcel) | 2026-10-26 (rates not posted yet) |
| Updated Dwarven Metal | flora (miningfemcel) | 2026-10-27 |
| Coal, Ores, Flint, Netherrack, Glowstone, Mithril | TheUltimateNon | 2025-02 to 2026-10-17 |
| Fairy Grotto, Routed Magma Field Gemstone, Tick Threshold, HOTM Tree, Gemstone and Mithril Powder, Nucleus Run, Fiesta, Glacite Tunnels Commission | KoenigBooHoo | 2026-07 to 2026-10-25 |
| General Mining Stat | KoenigBooHoo | 2026-08-25 (marked "no longer updated" 2026-10-12) |
| Mining gear progression, Mining Attribute (post Torrhus Canyon) | Deepfriedkappa | 2026-08-10, 2026-10-12 |
| Glacite Powder | sqoonius | 2026-10-25 |
| Scatha Farming | NamelessJu | 2026-10-22 |
| Mineshaft Mining (now titled "Outdated") | Livet | 2026-07-29 |
| Dwarven Diamond | Ghostz06 | 2025-08-13 |

## Where we agree

| Topic | Guide | Engine | Note |
| --- | --- | --- | --- |
| Tick thresholds | Tick Threshold guide tables | `MethodRates.ticks` | All 210 table values matched on 2026-10-26 ([TICK-THRESHOLDS.md](TICK-THRESHOLDS.md)); the thread is unchanged since 2026-09-19. |
| Instamine speeds | Cobble 601, End Stone 901, Gold 36,001, Coal 9,001 / Hard Stone 7,501 mid-air | Wiki strengths x 30 or x 60, airborne / 5 | Same numbers. |
| Cobblestone, End Stone rates | 13-16M and 11-16M / hour | 14.2M / hour at the General Mining Stat guide's Cobblestone setup (1,902 Fortune incl. 450 Block, Spread 1,010, Maniac Miner 3, 82.5%) | The old "8.5M hypermax" assumed Spread 400; full Glossy Mineral armor alone gives +600 on ores and blocks (wiki Glossy Mineral Armor). No rule changed. |
| Magma Fields gems, Grotto | MF 20.1-23.5, Grotto 12 / 11 Flawless / hour | Within a few % at the guides' efficiencies | Unchanged since the 2026-10-26 check. |
| Stat source values | General Mining Stat guide | `StatBreakdown` tables | Every HOTM, attribute, essence and pet value listed matched on 2026-10-27; this export is the same text. |
| Glossy Mineral set Spread | +600 on ores and blocks | +150 per piece, ores and blocks | Wiki: 150/300/450/600 by pieces. |
| Bal pickaxe cooldown | Scatha Farming guide: Bal no longer reduces it | Not counted | Wiki: Chimney perk removed in 0.27 (4 Aug 2026). |
| Crow pet | Scatha Farming guide: no longer reduces pickaxe cooldown | Not counted | Wiki: Quick Hands changed to weapon abilities in 0.27. The gear progression (2026-06-30) and Commission (2026-07) guides' -15% advice predates it. |
| Gold base drop, Fortune | Guide lists Fortune gear for Gold | 5-7 per block (wiki), Mining + Ore Fortune | Whether Fortune applies to Pure Gold is still open (wiki changelog). |

## Where we differ

| Topic | Guide | Engine | Status |
| --- | --- | --- | --- |
| Obsidian rate | about 3.5M / hour (max Magical Power, "decent skill") | 4.4M (Scatha all cycle) to 5.5M (Snail all cycle) at the guide's setup and our default 65% | Now above the guide. The old "25% below" used Spread 400 instead of about 1,010-1,410. The route is walking-limited (floor and walls, Rogue Sword, Tuba), so 65% is probably too high; 3.5M matches about 50%. Not tuned: efficiency stays editable. |
| Mycelium / Red Sand rate | 4.6M / 4.8M / hour at 1,522 Fortune | about 10.7M at the same Fortune with Glossy Spread 910, Maniac Miner, 82.5% | Unexplained. Candidates: Spread may not reach its full value on a flat surface (the wiki says Spread picks adjacent blocks and not the one below you), Mycelium/Red Sand may not count as "blocks" for Glossy Spread, or the shovel run is slower than 20 blocks / second. Not tuned. 2026-10-28 research: the wiki gives no cap (only "not beneath the player's feet"); a single 2024 forum report of a 3x3 limit would explain about 11%, not 57%. |
| Tungsten / Umber base drop | Mine only Clay (3) for Tungsten outside shafts; mainly Smooth Red Sandstone (3) for Umber | Was 1 per block (lower bound) | **Changed:** 3 per block, labelled a guide route assumption (wiki block drops). Umber is a little high (Brown Terracotta, 2, is mined during the ability). |
| Gold instamining | Goblin +250 and a bugged Strong Arm +505 on Gold: 35,246 during Mining Speed Boost | Not modelled | Bugs; not encoded. |
| Goblin pet speed | 12.5 per level (1,250) on non-pure ores, "bugged" | Wiki value | Bug; not encoded. |
| Glacite Golem | 175 Speed, 150 Fortune "(only 50 outside a Mineshaft)" | Wiki values everywhere | The guide's wording is ambiguous; kept as before. |
| Pet items on swap | Gear progression guide: pet items now come back when swapped | Was: new pets without a pet item, old item not credited | **Changed** (wiki Pet Items, 0.24.1): a replaced pet item comes back for a fee by rarity; a new pet keeps the mining pet item via a Super Scrubber. |

## New mechanics we lack

- **Movement-limited routes** (Obsidian, Mycelium/Red Sand): walking speed items (Rogue Sword, Weirder Tuba, Speed tuning,
  Speed beacon, Sunflower Butter) matter more than Mining Speed. We fold them into efficiency.
- **Pet and loadout swaps within a cycle**: Obsidian swaps Scatha (ability) and Snail (cooldown); Gold swaps loadouts for
  the ability. We rate one pet and one loadout.
- **Mana for Maniac Miner**: Mycelium (Gloomlock tech, 900-1,500 Mana), Diamond (2k Mana recommended). We assume the full
  duration.
- **Not-modelled methods with guide rates**: Dwarven Diamond 4-6.5M, Ores 2.75-6.5M, Flint 3.5-5.15M, Glowstone 1.5-2.2M
  collection / hour; Commissions 94M coins and 16M Mining XP / hour; powder methods (Gemstone powder chests up to 3,000 / hour,
  Mithril up to 2.47-3.03M powder / hour from goblin eggs, Glacite up to 0.95-1.85M / hour). None is modelled yet.
- **HOTM trees**: the HOTM Tree guide lists HOTM tier, tokens and powder (minimum and maximum) for 20 trees, useful as a
  sanity check for the HOTM planner. Its image links were still valid on 2026-10-28, which confirmed every community tree
  picture's method: Gold (Maniac Miner, MSB), Coal, Magma Fields gemstones and Fairy Grotto, Dwarven Metals MSB,
  Cobblestone, one block tree (End Stone, Mycelium/Red Sand, Netherrack, Glowstone, Gravel), and one tree for Ores and
  Dwarven Diamond. The guide also has trees for Mithril, Obsidian, Shafting, powder grinding, Commissions and Scatha.
- **Gear progression order**: the gear progression guide orders upgrades by cost-effectiveness (attribute shards, Dwarven
  O's, Jaderald, lanterns early; Divan's Drill, coatings, Relic of Power late). Our plan is a greedy cost per 1% at live
  prices, so it reorders as prices change. On the test profile the guide's early items (Mossy and Gold Box shards,
  lanterns) also come early in our plan; our first steps are Mineral armor pieces, which the guide does not list.

## Still open after this export

The export settles none of these; each stays in [LIMITATIONS.md](../LIMITATIONS.md):
- Mycelium and Red Sand block strength (the guide only says Mining Speed does not matter on a shovel).
- Airborne share of routes: only the Coal guide mentions mid-air mining (routes send you up and down), with no share.
- Hard Stone on gem and metal routes: no amounts in any guide (Coal's 3-6M / hour is the only figure).
- Maniac Miner: the wiki lists levels 1-3 and says Blue Cheese and Core of the Mountain 2 add a level, without values for
  the extra level; guides give Mana advice (900-1,500 or 2,000 Mana) but no rule for running out.
- Whether Mining Fortune applies to Pure Gold: the Gold guide lists Fortune gear but no measurement.
- Glacite Golem Mining Speed inside and outside Mineshafts (guide wording ambiguous).

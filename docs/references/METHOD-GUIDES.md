# Mining method specs from community guides

Owner-supplied Mining Cult guide texts, received 2026-10-26. Raw copies are kept privately
in Git-ignored `service-data/guides/` (third-party community content; do not commit).
This file extracts what the engine needs per method. Guides are orientation, not verified
mechanics: they go stale after patches and some values are explicitly bugs. Verify each
threshold/formula against the wiki or in-game before encoding it, and keep it editable.

## Cross-method mechanics to research and model

- **Instamine / tick thresholds.** Guides give speed needed to instamine: Cobblestone
  601, End Stone 901, Hard Stone 7,501 mid-air, Coal 9,001 mid-air ("divide by 5 when
  on the ground"), Gold 36,001, Jasper 32,000 for 4 ticks with MSB level 2. Derive from
  the wiki's block strength / tick formula rather than hardcoding these numbers.
- **Mid-air penalty.** Mining while airborne is 5x slower; Mining Cat assumes Coal is
  mined mid-air ~8% of the time on routes (coal-p6.png).
- **Pickaxe abilities.** Maniac Miner (+Fortune, needs mana: ~900 noted; Blue Cheese
  Goblin Omelette swap drill) vs Mining Speed Boost (+300%, level 2 +250%); since 0.27.2
  MSB updates stats live so drill swapping only works with two Divan's Drills; bugged to
  end early if swapping with <=5 s left (as of 2026-10-07). Cooldown reductions: Pickaxe
  Cooldown attribute -10%, fuel tanks, Bal swap (-10% effective, per mineshaft guide).
- **Efficiency.** Guides use 60% (MF Gems, SkyHelper /gemstone) and ~65% (Grotto) block
  efficiency; obsidian/cobble/endstone are route/circle based. Make it a per-method
  default with user override.
- **Known bugs guides rely on (re-verify every patch):** Goblin Pet 12.5 Speed/level
  (1,250 at 100) instead of 2.5; Strong Arm +25 Speed/level on Hard Stone (2,500 max);
  Gold guide says Goblin +250 and Strong Arm +505 on Gold.
- **Heat.** Magma Fields methods hold ~95 Heat; Heated reforge needs 89-100 Heat.
- **Conditional stats.** Bal Furnace up to +3 Pristine in Magma Fields; Glacite Golem +1
  Pristine per looted Lapis corpse in mineshafts; Quartz Speed +100 only during weather;
  Fiesta Fortune +50 only during Cole's Mining Fiesta; Devouring Spread +2.5 only on
  gemstones; Mossy Box +50 Block Fortune; Gold Box +50 Ore Fortune; Echo of Boxes
  (+20% Box attributes), Echo of Echoes (+50% Echo attributes).

## Per method

| Method | Where | Relevant stats | Ability / key setup | Guide rate |
|---|---|---|---|---|
| Coal | Crystal Hollows routes (Jungle while progressing) | Fortune, Ore Fortune, Spread, Speed vs 9,001 mid-air threshold | Maniac Miner via Blue Cheese swap drill; Goblin (speed) or Scatha; ping <=120 ms | 4-9M coal + 3-6M hard stone /h |
| Gold | Mines of Divan (Crystal Hollows) | Fortune, Ore/Dwarven Metal Fortune, Speed (36,001 instamine) | Maniac Miner (4/4 Glossy) or MSB instamine with loadout swap; Goblin + Heph Relic | not yet given |
| Fairy Grotto (Jasper) | Crystal Hollows Jungle grottos | Speed tick thresholds, Fortune, Gemstone Fortune/Speed, Pristine, Gemstone Spread | MSB; Scatha (Bal in Magma Fields); heat ~95 | Mansion 12 / Palace 11 Flawless Jasper/h at ~65% eff.; Magma Fields x1.15 |
| Magma Fields gems | Crystal Hollows Magma Fields | Gemstone Fortune/Speed, Pristine (Bal +3), Gemstone Spread, tick thresholds | MSB (or Maniac via test); Bal Quick Claw; Heated reforge | 60% eff.: Ruby 23.5, Amber/Sapphire/Jade/Amethyst 20.1, Topaz 16.5 Flawless/h |
| Obsidian | waypoint route, clockwise | Speed (heavy), Fortune, Block Fortune | MSB with Scatha (Unalloyed Speed) / Snail Quick Claw swap; Rogue Sword, Weirder Tuba | ~3.5M/h |
| End Stone | The End | Fortune, Block Fortune, Spread; 901 speed instamine | Maniac Miner, Blue Cheese on main drill; Endermite pet; intelligence for mana | 11-16M/h |
| Cobblestone | Dwarven Mines roof, run in circles | Fortune, Block Fortune, Spread; 601 speed instamine | Maniac Miner, Blue Cheese on main drill; Black Cat pet | 13-16M/h |
| Mycelium / Red Sand | Crimson Isle (wisp coords given) | Fortune, Block Fortune (+Fungus/Harena Fortuna), Spread | Maniac Miner; Scatha Unalloyed Speed, Kuudra pet; Gloomlock mana tech | Mycelium 4.6M/h, Red Sand 4.8M/h ("max fortune 1,522 incl. block fortune") |
| Mineshaft gems | Glacite Mineshafts | Gemstone stats, Pristine (Golem + corpses), Cold | Spawn via Tungsten or Mithril routes + Pickobulus, MSB inside; heavy RNG | 50-80M/h (very variable) |

Mineshaft gemstone mining is RNG-driven (shaft type, corpses, Mineshaft Mayhem roll);
model it as a scenario with user inputs rather than a fixed rate.

## Stats layout pattern

All guides repeat the same structure: drill (reforge, gems, parts, enchants), armor,
equipment, pet + pet item, accessories, consumables, essence-shop perks, collections,
attributes, HOTM tree/powder. This matches our stat-source ledger and the progression
checklist. Recommended reforges differ by method (Lustrous, Auspicious, Heated, Glacial,
Fleet, Stellar, Jaded, Blazing, Royal), so reforge choice must be method-aware.

## Missing guides

Gold rates (guide says pending), Umber, Tungsten, Glacite, Mithril/Titanium, Peridot,
Ruby/Topaz variants outside Magma Fields, Hard Stone/powder grinding.

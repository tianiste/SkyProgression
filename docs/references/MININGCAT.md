# Mining Cat comparison evidence: Debotes

User-supplied screenshots received 2026-10-26. Images are partial pages and their capture
time is not established. Preserve observations separately from verified game mechanics.
The API lookup in this session identifies the selected profile as Zucchini. The screenshot
headers show a green profile icon but no written profile name, so an exact snapshot/profile
match remains to be confirmed. Efficiency and bot-calculated effective totals were not shown.
Prices below are historical displayed examples, not quotes to hardcode into tests.

## Coal progression — page 1 of 7

Image: [coal-progression.png](miningcat/coal-progression.png). Footer: 50 upgrades + 7 extras.

- DR-X655 Universal Slot: replace Perfect Topaz with Flawless Jade, +35 Mining Fortune;
  purchase 2.12M, sale credit 14.67M, labeled free after selling; +1.8% total increase.
- Ancient Cloak with Blazing: +1 Fortune, -30 Speed; purchase 2.65M, replaces Titanium
  Cloak with 4.12M sale credit; free after selling, +0.05%.
- Snoozle Shard VIII from 0: +8 Spread, 1.86M, 1.16M per 1%, +1.6%.
- Glossy Mineral Boots: existing Mineral Boots +16 Glossy Gemstones, +50 Spread/+10 Speed;
  uses two owned Glossy Gemstones from sacks. Displayed 23.66M, 2.34M per 1% with next line,
  +11.5% total increase. Following Jaded reforge: +25 Fortune/+45 Speed, 3.1M, +1.3%.
- Snoozle X after VIII: +2 Spread, 1.33M, 3.37M per 1%, +0.39%.
- Tiamat III from 0: +1.5 Ore Fortune; Cuboa I: +1 Ore Fortune.
- Bejeweled Collar on Bal: +25 Speed/+10 Fortune, 2.91M, +0.52%.

Needed capabilities: gem-type swapping; sale credits; recipe materials from storage;
attribute levels; paired upgrades; marginal deltas after prerequisites; pet items.

## Coal Other Upgrades

Image: [coal-other.png](miningcat/coal-other.png).

**Can't check if owned:** Dwarven O's Ore Oats x5 (+10 Ore Fortune, 3.91M, +0.52%);
Hideosun Shard X (+50 Ore Fortune, 6.4M, +2.6%), both explicitly conditional on having none.

**Can't be bought:** Relic of Power with Perfect Jade + Amber (+15 Fortune/+30 Speed),
forge Artifact of Power + Perfect Plate +32 Glacite Amalgamation, HOTM 10; Compact X on
DR-X655 (+0.07% Enchanted Drop Chance; 1M mined blocks for X, +0.58% total increase);
Bal level 97 to 100 (+3 Fortune, pet XP, +0.15%).

Do not treat missing API fields as proof that uncheckable consumables are absent.

Our Other & progress view (2026-10-27, fixture, Coal): Ore Oats x5 +0.51% (Mining Cat +0.52%), Gold Box X +2.5% (+2.6%),
Bal 97 -> 100 +0.25% with +5 Fortune / +2 Speed from our pet table (Mining Cat +3 Fortune, +0.15%), Compact X "not modelled"
(Mining Cat +0.58%), Relic of Power +11.5 Fortune / +23 Speed at Epic (Mining Cat +15 / +30). Differences documented in
CALCULATIONS.md, not tuned.

## Grotto progression

Image: [grotto-progression.png](miningcat/grotto-progression.png). Visible first upgrades:

- Mithril Lantern: Lantern + Refined Mithril +16 Enchanted Emerald; +10 Fortune/+40 Speed,
  707.5K, 835.68K per 1%, +0.85%.
- Titanium Lantern after Mithril: +5 Fortune/+20 Speed, 3.66M purchase, 963.65K resale,
  6.4M per 1%, +0.42%.
- Sapphire-Polished Drill Engine: +150 Speed/+20 Fortune, replaces Ruby-Polished;
  143.61M purchase, 69.05M resale, 14.91M per 1%, +5.0%.
- Amber-Polished after Sapphire: +200 Speed/+50 Fortune, 234.09M purchase,
  138.58M resale, 15.91M per 1%, +6.0%.
- Lustrous reforge on 655: +60 Speed/+60 Fortune, 66.1M, +3.7%.
- Legendary Scatha: +125 Fortune/+100 Speed/+125 Gemstone Fortune, 382.54M,
  18.61M per 1% with next line, +20.6%.
- Quick Claw on Scatha, moved from Bal: +50 Fortune/+50 Speed, free, +6.3%.
- Glacite Lantern after Titanium; Will-o'-wisp after Glacite (+2.5 Gemstone Spread
  alongside +5 Fortune/+20 Speed). Both list previous-lantern resale credits.

Needed capabilities: forge chains; real drill modules/reforges/pets; reusable pet items;
method-specific speed effects; paired recommendation accounting.

## MF Gems progression

Image: [mf-gems-progression.png](miningcat/mf-gems-progression.png). Lower part is cut off.

- Mithril Lantern: same +10 Fortune/+40 Speed and 707.5K purchase, but +0.84% and
  845.74K per 1% (different baseline/method from Grotto).
- Titanium Lantern then Glacite Lantern: +5 Fortune/+20 Speed each, +0.42% each.
- Sapphire Engine: same +150 Speed/+20 Fortune as Grotto, but +2.3% and 32.86M per 1%.
- Amber Engine: same +200 Speed/+50 Fortune, but +4.2% and 22.97M per 1%.
- Will-o'-wisp: +5 Fortune/+20 Speed/+2.5 Gemstone Spread; +2.9%.
- Galaxy Fish III from 0: +3 Fortune, 5.96M, +0.15%.
- Gemstone Fuel Tank: existing Titanium-Infused Fuel Tank +10 Gemstone Mixtures +1
  Precursor Apparatus; +2% Pickaxe Ability Cooldown Reduction, 20.11M, +0.39%.
- Perfectly-Cut Fuel Tank after Gemstone: visible +4% ability cooldown reduction;
  remaining details are cropped and should not be inferred.

## Evidence still needed for numerical parity

Full progression pages, Other Upgrades for MF Gems/Grotto, Rates output, selected
efficiency, complete effective stat totals, exact loadout/HOTM/pet/context, and capture
time. Compare against a contemporaneous API snapshot. Market prices and owned gear can
change between captures, so differences alone are not proof of a calculation bug.

These images support targeted regression scenarios, not a license to invent mechanics
or clone proprietary calculations. Verify each rule independently and track discrepancies.

## Second capture set (2026-10-26, ~15:04)

Images in `miningcat/`: `coal-p1..p7.png` (complete Coal: 50 upgrades + 7 extras),
`gold-p1..p4.png` (Gold pages 1-4 of 6; pages 5-6 not supplied), and
`ingame-mining-stats.png` (owner's in-game Mining Stats tooltip, same session).

In-game tooltip: Breaking Power 9, Mining Speed 7,500, Spread 300, Gemstone Spread 2.5,
Pristine 13.4, Mining Fortune 2,248.47, Ore Fortune 110, Block Fortune 125, Dwarven Metal
Fortune 5, Gemstone Fortune 195. Location and active buffs at capture time are unknown.
These differ from SkyCompass's counted totals (1,724.5 Fortune / 7,150.5 Speed), so the
tooltip likely includes situational/temporary bonuses. Ask the owner for location and
active effects before treating either as the baseline.

### Coal observations

- Ranking is by marginal % gain for the method, with cost per 1%; "with the next N lines"
  marks paired/chained upgrades evaluated together. Resale credit shown for replacements.
- Fortune-only rows fit `gain% = delta / (100 + Mining Fortune + Ore Fortune)` using
  SkyCompass's counted totals (1,724.5 + 100): +35 -> 1.82% (shown 1.8), +1.5 Ore -> 0.078%
  (0.08), +5 -> 0.26% (0.26), +10 -> 0.52% (0.52), +3 -> 0.156% (0.15). Strong evidence
  the bot uses a linear Fortune multiplier on counted (not situational) stats. Rows with
  +15 show 0.76% vs 0.78% predicted; check rounding/baseline after prior steps.
- Spread rows (Snoozle +8 -> 1.6%) do not fit a simple `1 + Spread/100` on 300; spread
  model still unknown.
- Speed: page 6 states "Mining Speed 7,380 (with your Bal pet): 1,621 short of the 9,001
  needed to instamine coal while falling ... coal mined in the air (about 8% of the time)
  takes 4x longer". So speed only matters via thresholds/route assumptions for Coal.
- Maniac Miner (+500 Fortune) via a second drill with Blue Cheese Goblin Omelette; Extras
  recommend Wisdom IV on armor for the 900 mana it needs and Wither Specter shard X.
- HOTM read from profile: all % perks maxed; Mole 186/200 noted as non-% benefit.
- Buffs page (7/7): God Potion (Haste IV +200 Speed, Spelunker V +25 Fortune), Century
  Cake (+5), Filet O' Fortune (+20), Celestial Mason Jar (+15); listed as purchasable buffs.
- Same item, different value per method: Goblin Pet 0.15% for Coal vs 22.6% for Gold;
  Flawless Jade +35 is 1.8% for Coal but 2.9% for Gold (implied base ~1,207), so Gold's
  fortune baseline differs (method-specific stats/pet context). Investigate.

## Developer's description of the bot (2026-10-26)

Relayed by the owner from the Mining Cat developer's reply in the Mining Cult Discord. The developer
does not mind the comparison. The bot's code is not shared (one file per method), so this is a
description of its approach, not formulas. Treat it as comparison evidence, not as rules to copy.

- Rates: the bot was given the average rates for a hypermax setup from the method guides at 65%
  efficiency and scales from there. Not tuned to be exact for every method (Coal especially, because
  average efficiency is hard to judge).
- Efficiency guidance: about 80-85% for instant-mine blocks, 65-70% for the rest. SkyProgression's
  defaults are now 82.5% (instant) and 65% (others); the player can change them in Type Stats.
- Value window per method: Obsidian values an item over one full Mining Speed Boost cycle (from full
  cooldown until it ends); Shafting and the Dwarven Metals over a whole hour; Coal over 30 minutes
  assuming two lobby hops.
- Temporary buffs belong in an "extras" list; weather and Sky Mall are left out. This matches the
  owner's decision: temporary buffs are listed but not counted.
- HOTM: read the player's current perks; when the player has several trees, use the one that fits
  the method best.


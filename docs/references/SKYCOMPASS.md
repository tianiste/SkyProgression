# SkyCompass comparison evidence: Debotes

Owner-supplied page text from the SkyCompass Stat Breakdown (skycompass.gg/player.html),
Mining tab, profile Debotes · Zucchini, captured 2026-10-26. Comparison evidence and a
feature-scope reference only. SkyCompass offers no public API and its terms forbid
systematically scraping or republishing its computed output. Do not call its endpoints
or copy its values or maxima into rules. Its "max" values reflect its own assumptions
("best known setup for each source"), not verified game limits.

## Totals observed (current / SkyCompass max)

| Group | Stat | Current | Max |
|---|---|---|---|
| General | Mining Fortune | 1,724.5 | 2,209.5 |
| General | Mining Speed | 7,150.5 | 8,931 |
| General | Mining Spread | 300 | 313 |
| General | Mining Wisdom | 39 | 95.1 |
| General | Breaking Power | 9 | 11 |
| General | Pickaxe Ability Cooldown | 14% | 20% |
| Gemstone | Gemstone Fortune | 180 | 429 |
| Gemstone | Gemstone Mining Speed | 850 | 1,655 |
| Gemstone | Gemstone Spread | 2.5 | 2.5 |
| Gemstone | Pristine | 13.5 | 14.9 |
| Blocks & Ores | Block Fortune | 215 | 445 |
| Blocks & Ores | Block Spread | 400 | 1,050 |
| Blocks & Ores | Ore Fortune | 100 | 330 |
| Blocks & Ores | Ore Mining Speed | 0 | 250 |
| Blocks & Ores | Ore Spread | 400 | 600 |
| Dwarven Metal | Dwarven Metal Fortune | 5 | 140 |
| Dwarven Metal | Dwarven Metal Mining Speed | 5 | 505 |
| Resistance | Heat Resistance | 204.5 | 348.4 |
| Resistance | Cold Resistance | 5.4 | 105.4 |
| Powder | All Powder Buff | 25% | 70% |
| Powder | Gemstone Powder Buff | 10% | 63% |
| Powder | Mithril Powder Buff | 10% | 83% |
| Powder | Glacite Powder Buff | 0% | 43% |

## Mining Fortune breakdown observed (counted sources, current / max)

- Armor: Helmet/Chestplate/Leggings/Boots of Divan, 160 / 170 each.
- Equipment: Titanium Necklace 15 / 93; Dwarven Handwarmers 36 / 37;
  Titanium Cloak 15 / 17; Titanium Belt 15 / 17.
- Tool: Titanium Drill DR-X655 270 / 555; Sunny Side Goblin Omelette 50 / 50.
- Accessories: Mineral Talisman 3 / 6; Ring of Power 3.5 / 25.
- Pet: Bal (Legendary 97, Quick Claw) 145 / 187.5.
- HOTM: Mining Fortune 100 / 100; Fortunate Mineman 150 / 150.
- Mining level: 240 / 240.
- Collections: Tungsten 5 / 5; Glacite 4 / 4; Umber 4 / 4; Obsidian 4 / 4.
- Attributes: Fortunate Miner (Flitter Shard) 25 / 25; Ultimate DNA (Galaxy Fish) missing, +10.
- "Not counted": nothing listed.

Sum of the listed counted sources: 640 + 81 + 320 + 6.5 + 145 + 250 + 240 + 17 + 25
= 1,724.5, matching the displayed total. Useful as a sanity check of our ledger.

## Source states shown (feature spec)

- Owned but situational (not in total): HOTM Rags to Riches (mineshafts), Sky Mall
  (rotating daily buff).
- Missing, conditional: tool reforges Heated (at 100 Heat), Glacial (99 Cold), Scraped
  (Breaking Power match), Stellar (near Fallen Stars); Magic 8 Ball; Kuudra pet (Crimson
  Isle); HOTM Mineshaft Mayhem; Doug's Fortunate Festivity (Mining Fiesta); Crimson
  Essence Fungus Fortuna (Mycelium) / Harena Fortuna (Red Sand); lantern line.
- Trackable temporary: Filet O' Fortune, Century Cake of Hype (not detected, noted that
  God Potion effects are never reported); Mayor Cole Mining Fiesta (confirmed live).
- Untrackable: Spelunker V potion, Celestial Mason Jar, Fiesta Flask, Better Together,
  Extreme Weather. Shown as "neither confirmed nor ruled out".
- Upgrade lists: "Coins, best value first" (cost per stat point, e.g. Perfect Jade
  replacing Perfect Topaz on DR-X655, about 295k / point) and "Not coins, closest to
  done first" (unlocks/collections).

## Use

Verify every mechanic independently from Hypixel data, the wiki or in-game testing.
Prefer the owner's in-game stat menu as ground truth. When our results differ, record the
discrepancy and the source of truth used instead of matching SkyCompass's number.

## Comparison with our breakdown (2026-10-26)

Our engine (`StatBreakdown`, equipped loadout with the DR-X655 selected) on the fixture:
- **Mining Fortune 1,724.5**, matching SkyCompass. The per-source amounts also match. The drill's
  270 is our 235 lore line + 5 Polarvoid + 30 Ruby-Polished engine, and the omelette's 50 is separate.
  We additionally flag the drill's Auspicious Bonus (+1% Mining Fortune) as unresolved.
  SkyCompass's sum suggests it does not add it either; the mechanic is unverified.
- **Mining Speed 6,950 with no zone chosen, 7,050 in the Dwarven Mines** (adds Eager Miner +100),
  vs SkyCompass **7,150.5**. Gap 100.5. We have no SkyCompass speed breakdown, so the cause is
  unknown. Candidates we keep situational on purpose: Sky Mall +100 (random daily buff), Haste III
  from the Haste Artifact (+150). Neither explains the .5. Not tuned; needs the owner's in-game
  stat menu.

## In-game stat menu (owner screenshot, 2026-10-26, drill held)

Ground truth for Debotes · Zucchini at an unknown location and with unknown active buffs:
Breaking Power 9, Mining Speed 7,610, Mining Spread 300, Gemstone Spread 2.5, Pristine 13.4,
Mining Fortune 2,286.27, Ore Fortune 110, Block Fortune 125, Dwarven Metal Fortune 5,
Gemstone Fortune 195.

Against our engine (equipped, DR-X655 selected, Dwarven Mines context): Fortune 1,724.5
(+561.77 in game), Speed 7,050 (+560 in game).
Hypothesis, not encoded: 2,286.27 / 1.26 = 1,814.5 exactly, which suggests +25% Mining Fiesta
(Cole) and +1% Auspicious Bonus combining additively on the whole total. The remaining +90 flat
Fortune and +560 Speed would then come from temporary or situational sources (Sky Mall, potions,
lanterns, weather, Better Together). The owner had a Fiesta Flask active as well. Only x1.26 yields a clean base
(x1.51 additive gives 1,514.08; x1.25 x1.25 x1.01 gives 1,448.8), so the data fits a single +25%
(Flask and Fiesta event not stacking, or only one active) plus Auspicious +1%.
This needs confirming with the menu's per-source details
and the active buffs at capture time. Note the in-game values differ from SkyCompass's capture
(1,724.5 / 7,150.5), so buffs likely changed between captures.

### Per-source details (owner screenshots, same session)

- Mining Speed 7,610 itemized exactly. HOTM 3,100 (Speedy Mineman 2,000, Mining Speed 1,000,
  Sky Mall 100). Basic 2,140, Gemstone 907, Reforge 315 (Auspicious 75, Jaded 60 x 4), Efficiency X
  210 (these four are inside our lore totals). Drill Parts 250, Polarvoid 50, Quick Claw 48,
  Eager Miner 100, Attributes 40 (Rotten Pickaxe 30, Quartz Speed 10). Mist weather 250 and Haste
  potion effect 200 are untrackable for us. Ours: 7,050 + Sky Mall 100 + Mist 250 + Haste 200 +
  Quartz Speed 10 = 7,610. The Haste IV potion (+200) replaces the Haste Artifact's Haste III (+150).
- Mining Fortune: Flat Bonuses add up to 1,814.5 (drill 320, mining skill 240, Divan 160 x 4,
  Fortunate Mineman 150, "and 17 more" not seen). Additive Buffs: Mining Fiesta 25% + Auspicious
  Bonus 1% = x1.26, multiplied with the flat total. The Fiesta Flask is not listed, so it did not apply.
  Our flat total is 1,724.5: 90 Fortune is still unidentified. Likely candidates are weather Mist
  (+50 per the wiki) plus a Spelunker potion, lantern or similar. This needs the hidden 17 entries.

### Reconciliation with temporary buffs removed (2026-10-26)

Using wiki values: Mist +50 Fortune / +250 Speed, God Potion Spelunker V +25 Fortune and Haste IV +200 Speed.
- Speed: in-game 7,610 - Mist 250 - Sky Mall 100 - Quartz Speed 10 (needs weather) = 7,250 =
  our default total (7,050 + God Potion 200). Exact.
- Fortune flat: in-game 1,814.5 - Mist 50 = 1,764.5 vs our 1,749.5 (1,724.5 + God Potion 25).
  The remaining 15 is Fortune-only (Speed is exact, so not a Titanium Lantern, which also gives +60 Speed).
  The best candidate is Doug's Fortunate Festivity at level 3 (+5 per level, Mining Fiesta only),
  which is temporary anyway. Unconfirmed.
- Multiplier: in-game 1.26 - Mining Fiesta 0.25 = 1.01 (Auspicious), as encoded.

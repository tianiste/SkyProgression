# Changelog

## 0.4.0 - 2026-10-27

The first release that works without running anything besides the mod. Install the jar, open `/sp`, and your profile
loads through the hosted SkyProgression API.

### No companion needed

- Profiles and prices now come from the hosted API at `api.skyprogression.dev` by default. You no longer need a Hypixel
  API key or the companion window; the companion stays available for developers and players with their own key.
- Every calculation runs inside the mod, so stats, rates, upgrades, Compare and Start with all work from one load.
- Your own name is resolved from the game session, other names directly at Mojang.
- Faster loading: profiles and prices you already loaded are shown at once while a fresh copy loads in the background.
- Home tells you when a newer SkyProgression release is out (checked once per launch; nothing is downloaded).

### New interface

- Four icon tabs: **Home** (your stats, current rate, best next upgrade and a coins/hour chart of every method),
  **Gear**, **Upgrades** and **Methods**, plus a **More** page behind the settings button (or F1). Keys 1-4 switch
  tabs, R reloads.
- Gear shows your loadout as item slots: the tool outlined gold, pieces you need to fetch from storage in cyan.

### Stat breakdown

- Every mining stat is calculated from your profile and itemized by source: Mining Fortune and Speed, Gemstone, Ore,
  Block and Dwarven Metal Fortune, Mining and Gemstone Spread, Pristine, Mining Wisdom, Breaking Power, Pickaxe Ability
  Cooldown, Heat and Cold Resistance, and the powder buffs.
- Each stat and each source shows its current value next to the best you could reach, with a check mark when maxed.
  Situational, temporary (mayor, potions), missing and not-in-API sources are listed separately and never counted.
- Stats use the best gear, tool and HOTM tree you own for the selected method. Switch to your equipped gear or pick the
  tool you hold, and change the zone. Type Stats still overrides any value.

### Rates for every method

- Rate models for every mining method: Magma Fields gems, Ruby, Topaz, Fairy Grotto Jasper, Coal, Obsidian, Tungsten,
  Umber, Glacite (Shafting), Gold, End Stone, Cobblestone, Mycelium and Red Sand.
- Rates follow block strength and tick thresholds, Mining Speed Boost or Maniac Miner with your ability cooldown, the
  next speed threshold worth reaching, Heat in the Magma Fields and Cold in the Glacite Tunnels and Mineshafts.
- Secondary drops are included where they are known: Hard Stone on Coal, other gems in the Fairy Grotto and frozen
  corpse loot while shafting.

### Upgrades ranked by what they add

- Upgrades are ranked by how much they raise your rate in the selected method, with before/after rates and coins per
  hour on each card, and a step-by-step plan (buy the best value, apply it, recalculate).
- Far more upgrades: drill engines, fuel tanks and modules, reforge stones, recombobulation, gem swaps, Divan's Powder
  Coating, better base items and full mining sets with their set bonuses, level-100 pets and Quick Claw, essence perks,
  attribute shards, HOTM perk levels and unlocks, the Blue Cheese omelette, and skill and collection tiers.
- Costs use the cheaper of buying and crafting or forging, count materials you already own, and show the net cost after
  selling the item an upgrade replaces.
- **Other & progress** view: bonuses the API cannot show (Dwarven O's cereals, Gold Box, Royal Riches, Dwarven
  Expertise; skip what you already have), progress to grind ranked by rate gain (HOTM, skill, collections, pet level,
  Compact, forge-only items such as the Relic of Power) and your early/mid/end game guide stage.

### Compare and Start with

- **Compare** ranks every method by coins per hour with your own best gear, at instant-sell, sell offer or NPC prices,
  with or without a God Potion.
- **Start with** shows the best loadout you already own for a method, where each piece is stored (wardrobe slot, ender
  chest page, backpack, Loadouts set, pets menu) and which swaps from what you wear now matter most.

### Known limits

- Rates are our own models and are not validated against measured sessions for every method. Fuel costs, taxes and
  route density are not modelled.
- Unknown prices stay unknown and sort last; they are never treated as free.
- Minecraft 26.1.2 only.

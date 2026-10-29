# Changelog

## Unreleased

- Upgrade tooltips are shorter and easier to read: coloured name, stat change, gain and price on one line each, "Free"
  instead of "Owned", one short line for the best method, and recipes as a short list (up to three steps) instead of
  one long sentence.

## 0.6.0 - 2026-10-29

### Fixes

- Session tracker: gemstones that go straight into the Gemstone Sack are now counted (their names start with a gem
  symbol, which the sack summary matching missed), so mining gems with sacks starts and fills a session.

### HOTM tree planner

- New card on the Upgrades tab: the best HOTM tree for the method within your tier, Tokens of the Mountain and powder
  (a reset is free and refunds all powder except Core of the Mountain), next to your current tree. It shows the rate
  before and after, whether a reset is needed, the better pickaxe ability (Mining Speed Boost or Maniac Miner), tokens,
  powder needed per type and what is still missing, and every perk change (expand "Perk changes").
- Every HOTM node's cost and maximum is now known (NEU, checked against the wiki): Mole's powder cost uses the wiki's
  exponent (2.17883), and Keep It Cool, Warm Heart, Seasoned Mineman and Powder Buff levels appear as upgrade options.
- Companion/relay: the `hotm` view returns the plan as `planner`.

### Live gear

- Gear, Home and Start with now use the gear the game shows: worn armor, the inventory and the held drill or pickaxe
  (no more waiting for the API, and the held tool no longer needs picking), plus equipment and the active pet once you
  open the equipment or pets menu (the pet also from the tab list and summon messages). The API fills the rest. Works
  with the Inventory API off for what the game shows. Each piece is labelled Live or API with its age. Read only.

### Faster

- Stats, upgrades and the step-by-step plan are calculated noticeably faster (rules and lore patterns parsed once,
  rates reused within a plan, fewer copies while pricing recipes). Armor and equipment rows keep a fixed order.

## 0.5.0 - 2026-10-28

### Session tracker

- New on the Methods tab: measures your real collection / hour and coins / hour for the selected method and shows them
  next to the model, with active time, elapsed time and uptime. Counts inventory pickups, the [Sacks] chat summaries and
  Pristine procs (compacted items count as their base items). Starts on the first gain, pauses after 60 s idle, keeps
  running with /sp closed, optional HUD line, and Save session writes it to `config/skyprogression-sessions.json`. Read only.

### More stat sources

- Counted now: the Mineshaft HOTM perks Eager Adventurer and Steady Hand; the Ultimate DNA, Sleepy Spread, Gold Box, Royal
  Riches, Greedy Goblin and Freezing Spread attributes and the Echo attributes; the Dwarven Expertise, Cut Loose and
  Fungus / Harena Fortuna essence perks; Goblin, Snail, Kuudra, Mammoth, Baby Yeti and Penguin pets; Hephaestus Relic
  (+50% of the pet's base stats) and Bejeweled Collar.
- Gold Box and Dwarven Expertise moved from "can't check" into Upgrades.

### Upgrades

- Prices for every attribute shard and the newer pets and pet items.
- Items nobody sells (such as the Relic of Power line) are priced through their recipes, several levels deep, using what
  you own first; the card lists the recipes and total forge time. Locked recipes say what to unlock first.
- Buy or craft is compared at every recipe level, and items you own are never counted by two plan steps.
- New **Cost: buy order** toggle prices Bazaar items at buy-order prices ("fills only when a seller accepts your order").
  Instant buy stays the default.
- Pet items come back when you replace them, so a new pet keeps your Quick Claw, Hephaestus Relic or Bejeweled Collar,
  and swapping between pet items you own is offered.
- The plan can now pick a full drill build (engine, tank, module, reforge and gems together) and checks pairs of upgrades
  that only pay off together.
- Upgrade cards show the item each upgrade is about (the engine, reforge stone, gem, book, shard, essence or pet) instead
  of the drill.
- Fixed: the upgrade list failed when a pet had no price.

### Rates

- Tungsten and Umber are rated at 3 drops per block, matching the community route (Clay and Smooth Red Sandstone).

## 0.4.1 - 2026-10-27

### Minecraft versions

- SkyProgression now runs on Minecraft **26.1.1**, **26.1.2** and **26.2**. Each release has one jar per version:
  `skyprogression-0.4.1+26.1.1-26.1.2.jar` for 26.1.1 and 26.1.2, `skyprogression-0.4.1+26.2.jar` for 26.2. Install
  the one that matches your game together with Fabric API for that version.
- The update notice on Home only mentions a new release when it has a jar for your Minecraft version.

### Other

- New logo, also shown as the mod's icon in the mod list, with links to the source and issue tracker.
- SkyProgression is now licensed under the MIT License; the bundled NotEnoughUpdates data is credited with its license.
- Clearer README, and a CONTRIBUTING guide for reporting wrong numbers, building and releasing.

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

# Known limitations

Everything SkyProgression is known not to do, not to know or not to have checked yet, in one place. It covers the mod,
the hosted API (`api.skyprogression.dev` and its relay) and the website (`skyprogression.dev`). Formulas and sources are in
[CALCULATIONS.md](CALCULATIONS.md); this file lists only the gaps. Last reviewed 2026-10-28 (mod 0.4.1 plus unreleased
changes on `main`).

SkyProgression is a functional preview. Unknown values are shown as unknown, never as 0 or free, and anything below that
is "assumed" is labelled in the interface where it affects a number.

## Overall status

- **No rate model has been checked against a measured mining session.** The rate models are built from the wiki's block
  strengths, tick thresholds and drop rules plus community guide averages. The session tracker exists to collect that
  evidence; no saved sessions have been compared yet.
- **Several features have not been tried in game.** Built and compiled, but not yet confirmed in Minecraft: the session
  tracker, the stat sources added 2026-10-27 (below) and 0.4.1 on every supported Minecraft version (it has been
  confirmed on one). The stat breakdown, every rate model, the Start with tab, the Other & progress
  view and the redesigned interface were confirmed by eye in game, not number by number against the in-game stat menu.
- **One test profile.** Numbers were checked against one late-game profile. Early-game profiles, Ironman and Bingo,
  profiles with API settings off and profiles with duplicates or maxed gear have not been systematically tested.
- **Not built yet:** live gear (reading equipped gear from the game client instead of the API) and methods without a model (Mithril, Titanium, Dwarven ores such as Diamond, Flint,
  Netherrack, and Umber/Tungsten as full routes with mineshafts).

## What the Hypixel API does not tell us

- **Held item (website and companion only).** The API has no held item. On the website the tool is the best owned tool
  for the method unless you pick one, and the current loadout without a pick has no tool swap. The mod reads the held
  tool from the game (see "Live gear" below).
- **Active HOTM tree and selected ability.** Profiles can hold up to five trees (Loadouts) and none is marked active. The
  engine uses the tree with the most value for the method. The selected pickaxe ability is read from the tool's lore only.
- **Potions and most buffs.** The God Potion is assumed active (Spelunker V +25 Mining Fortune, Haste IV +200 Mining Speed)
  and can be switched off; the API never reports it. Temporary buffs (Mining Fiesta, Fortunate Festivity, weather, Sky
  Mall, lanterns, Better Together) are listed but never counted. `active_effects` and `temp_stat_buffs` entries are shown
  as temporary with unknown values because their mapping is not verified; a few potion effects are mapped by assumed key.
- **Consumables.** Dwarven O's cereals (Gemstone Grahams, Ore Oats, Block Bran, Metallic Minis), the Chilled Pristine Potato,
  Will-o'-wisp's Gemstone Spread, lanterns, Cut Loose stacks and the Corpse-Flavored Century Cake are not in the API. A cereal's active buff is not readable, so the checklist only sees stored copies.
- **Where you are.** Zone, height (Heat below Y 64 in the Crystal Hollows), Heat, Cold, Mineshaft type and today's Sky Mall
  Fortune buff (its code has not been observed) are not known. You pick the zone; each method defaults to its usual one.
- **Storage that is not read.** The museum, minions, forge slots, your auction listings, the Rift and the Hunting Box (so
  every attribute shard is priced as bought). The personal vault needs the Personal Vault API setting. Accessories only
  count in the accessory bag (the checklist also accepts the inventory).
- **API settings off.** With the Inventory, Collections or Skills API off, the affected parts are unknown, not missing:
  ownership, collection tiers and Mining level respectively. Pets, attributes, essence perks or HOTM that Hypixel does not
  send are unknown too.
- **Data age.** Hypixel's profile data lags the game by a few minutes, and our caches add up to 5 minutes (up to 30 while
  a refresh runs). Gear you swapped a moment ago may not show yet; every page shows when its data was fetched.

## Stat breakdown

- **Gear stats come from item lore,** which can carry context-dependent values; lore is not a complete stat calculator.
  Percentage lore bonuses other than the Auspicious +1% stay unknown, and whether Auspicious is additive or multiplicative
  is unconfirmed (treated as part of one summed percentage).
- **Unexplained differences on the test profile** against the in-game stat menu: Gemstone Fortune 180 vs 195, Ore Fortune
  100 vs 110, Block Fortune 115 vs 125 (likely cereals, which the API does not expose), and 15 Mining Fortune that is
  probably a temporary event buff. Against a reference tool, Mining Speed differs by 100.5 on the same profile, and the Coal
  model's speed differs from another reference by 130. None of these were tuned away.
- **Not encoded:** Lapidary outside its lore line, Front Loaded's per-day count (shown as situational), Magic 8 Ball, Glacial
  talismans on Jerry's Workshop, the Goblin set's Smart Miner, Yog's per-kill speed, the Heated reforge's Heat bonus, and
  the Crow pet (wiki: since 0.27, 4 Aug 2026, it lowers weapon ability cooldowns only; guides that recommend it for pickaxe
  cooldown predate that patch). Snail's Slow and Steady (Block Fortune per Speed above 100) is listed without a value.
- **Conditional reforge abilities are not counted:** Heated, Glacial, Stellar, Lustrous, Mithraic and Scraped. Fleet's Spread
  counts only on block methods.
- **Glacite Golem Mining Speed** is counted everywhere as the wiki gives it; a community guide says it applies only in
  Mineshafts.
- **Mixed Mineral and Glossy Mineral armor** is counted per set; whether the two sets combine is unknown.
- **Attainable maximum** keeps your loadout (one tool, pet, armor set and tree). Gear stat lines have no maximum from a
  primary source (it depends on reforge, gems, enchantments and recombobulation), so most stats show "unknown, at least X".
  Missing HOTM perks, other armor sets, other pets and tools are not added, so it is never a fully maxed account.
- **Best owned gear** is chosen slot by slot with a scoring heuristic (relevant Fortune, Pristine, Spread, a little Speed),
  not an optimizer, so it can break a set bonus or disagree with a fully modelled setup. An active pet with uncatalogued
  stats is kept because it cannot be compared.
- **Distance from a validated method maximum** is not shown because no maximum has been validated.

## Rates and coins per hour

- **Efficiency is an assumption:** 82.5% for instamined blocks and 65% otherwise by default (route travel, lobby hops and
  water trips are folded into it). You can change it.
- **Gaps against the guides** (checked 2026-10-28, [GUIDE-COMPARISON.md](references/GUIDE-COMPARISON.md)): at the guides' own
  setups Obsidian comes to 4.4-5.5M/h at our default 65% against the guide's 3.5M (the route is walking-limited, so 65% is
  probably too high for it), and Mycelium/Red Sand to about 10.7M/h against 4.6-4.8M (unexplained; Glossy Mineral Spread
  may not apply there or may not find enough adjacent blocks). No primary source gives a Spread cap on flat surfaces (the wiki
  only excludes blocks beneath your feet; one 2024 forum report suggests a 3x3 limit, which would explain about 11%), so Spread
  is not capped and Spread upgrades on Mycelium/Red Sand may be overvalued. Cobblestone and End Stone now match (14.2M/h in 11-16M).
  The Gold guide has not posted rates yet.
- **Block strength unknown:** Mycelium and Red Sand have no wiki strength and are assumed instamined.
- **Dwarven metals:** Tungsten and Umber are rated at 3 per block, the Dwarven Metal guide's route (only Clay for Tungsten
  outside mineshafts, mainly Smooth Red Sandstone for Umber). That is an assumption, a little high for Umber (Brown
  Terracotta drops 2). Glacite's base drop is not on the wiki (assumed 1). All mining is assumed on the ground; only Coal has an
  airborne share (8%).
- **Spread blocks** are assumed to be the same block (Coal Spread blocks are Coal, gem Spread blocks are gems).
- **Fairy Grotto** assumes all mining on Jasper; the other gems around a grotto are valued at the cheapest of four, from a
  guide ratio.
- **One pet and one loadout per method:** guide routes that swap pets or loadouts within an ability cycle (Obsidian:
  Scatha for the ability, Snail otherwise; Gold: an ability loadout) are rated with one pet and one loadout. Movement speed
  (Rogue Sword, Tuba, Speed tuning) is part of the efficiency.
- **Pickaxe abilities:** Mining Speed Boost is assumed to apply to all counted Mining Speed. Whether the tool lore's cooldown
  already includes the fuel tank is unknown (it only shifts the baseline). Whether exactly 32,000 Mining Speed reaches 4
  ticks on Jasper is unconfirmed. Maniac Miner's level is read with an assumed lore pattern (no lore observed); only the
  mined block adds a stack; Mana is not limited; its level-3 cap bug and the Blue Cheese bonus over the cap are not
  modelled. Whether Mining Fortune applies to Pure Gold is an open question from a changelog.
- **Heat and Cold:** Heat is applied only in the Magma Fields (the Crystal Hollows share below Y 64 is unknown); Cold in the
  Glacite Tunnels and Mineshafts, with resets at the -90 warning. The walk to water, the reset trip time, Heat Armor
  immunity, the Tungsten Regulator, Portable Campfires and mob hits are not modelled.
- **Secondary drops:** Hard Stone on gem and metal routes has no source (amount unknown, not counted); Coal's Hard Stone uses
  a guide estimate. Mineshaft corpse loot: Umber and Tungsten corpses are unknown because their Crystals have no price;
  Vanguard corpses, the Surveyor, extra-corpse chances, Glacite Powder and HOTM XP are not counted.
- **Prices:** Bazaar tax, Auction House fees and how much a sell offer actually fills are not included. Crafting products into
  enchanted forms is not counted. A method whose product has no price shows "coins unknown" and sorts last.
- **Coleweight** is not shown: its coefficients could not be verified from a primary source.
- **Comparison** uses each method's usual zone and best owned gear; the zone and tool you picked on the Gear tab are not used.

## HOTM tree planner

- **Rate-only, stat perks only.** The planner values a tree by the method's rate alone. Perks the rate model does not
  know (Sky Mall, Front Loaded, Mineshaft Mayhem, Precision Mining's aim target, Lonesome Miner, powder, Wisdom and
  Mineshaft-spawn perks) count nothing: they are bridges, or kept from your tree when tokens and powder are left. Strong
  Arm's hidden Pure Ore and airborne Hard Stone speed (wiki Tier 8) is not counted either.
- **Bounded search.** Greedy by gain per token with one-perk swaps, not an exhaustive search, so a better tree can exist;
  perks that only pay off together through a tick threshold can be missed.
- **Same ability level and cooldown.** Maniac Miner and Mining Speed Boost are compared at the tool's ability level and
  cooldown; Sky Mall's and Mineshaft Mayhem's cooldown buffs are not counted.
- **One tree, default efficiency and zone.** It plans the tree the engine evaluates (the best of your Loadouts trees), at
  the method's usual zone. The community tree pictures are not used as presets; their methods were confirmed on 2026-10-28, but they are
  only a sanity check, not wired in yet.
- **Core of the Mountain stays.** Its levels are kept by a reset (wiki); the planner never levels it for more tokens.
- **Not checked in game.** Powder costs reproduce one profile's `powder_spent` exactly; the reset refund is from the wiki
  only, and the website does not draw the plan yet (the relay returns it).

## Upgrades and costs

- **Alternatives are independent.** Each upgrade is measured against the same current state; the list is not a shopping
  list. The step-by-step plan (up to 12 steps, cheapest per 1% first) looks only two steps ahead (pairs and "A then B" against
  "B alone") from a bounded pool, at today's quotes with no market impact; longer combinations are found only as the
  predefined bundles (full sets, full drill build). Within a pair each upgrade's cost is its own. Stopgaps that the guides skip
  (Mineral before Glossy Mineral) stay in a 12-step plan when the better item is out of its budget.
- **Buy vs craft** follows NEU recipes up to 5 levels deep; forge slots and the value of forge time are not modelled, and the
  forge time shown is the sum over the whole chain. The buy-order cost mode assumes your order fills at today's top buy order (no queue time, no undercutting). A
  collection requirement with the Collections API off is not checked: the item is bought, or, when no one sells it, priced by
  its recipe and blocked until the unlock is confirmed.
- **Items with no quote at all** stay "Price unknown" with the reason: no level-100 listing of a pet at that rarity right now
  (fixture: Rare Glacite Golem), Coflnet not knowing the item (Kuudra pet), or an input with no quote and no recipe
  (Crystals, Metal Heart, Frosty Crux, Ruby and Sapphire Crystal).
- **Hosted prices follow a copy of the ID list.** The Worker prices only the IDs in its `price-ids.json`; after the mod adds
  items the list must be re-exported and the Worker deployed (the mod's build warns when the copy next to it is stale).
- **Resale credit** is the lowest matching auction listing (an asking price before fees; a sale may get less). Gems and
  enchantments are not used as filters; without a matching listing the plain item's value is used and labelled.
- **Pets:** a new pet keeps only a catalogued mining pet item (Quick Claw, Hephaestus Relic, Bejeweled Collar); which item's
  rarity sets a replacement fee is not on the wiki (the higher is used), and applying an item to an empty pet is assumed free.
  The replaced pet's sale price without its item is not quoted, so that net cost is unknown. Pet items held by your other pets
  are not offered. Pets that need other pet items or perks stay unknown.
- **Not offered as upgrades:** scrolls, levelling Core of the Mountain for tokens, resetting the HOTM tree (the planner card shows it), Quartz Speed and
  Deep Technique (situational), and modifiers on a newly bought base item (it is treated as having none).
- **Assumed:** every drill accepts every part (not stated on the wiki); the fuel tank removal fee equals the engine's 9,999.
  Enchantment XP costs are not converted to coins.
- **Other & progress:** Royal Riches' gain assumes you mine in the Mines of Divan, which has no zone; Compact's rate effect is
  not modelled; prices for essence perks whose level cannot be read assume level 0.

## Live gear (mod)

- Equipment is read only when you open the equipment menu, and the pet only from the pets menu, the tab list's Pet line
  or summon/Autopet chat messages. Until then both come from the API (labelled "API" with its age). Captures are kept in
  memory for this game session only, per profile (tab list "Profile:" line).
- The tab list and chat give no pet item: it is kept from the pets menu read or the API. Pet rarity from the tab list or
  chat is read from the name colour; pet XP is the API's (or the level's minimum XP for a pet the API does not list).
- An item the API lists as worn or in the inventory, but the game no longer shows there, is kept as "moved since the API
  read": it is somewhere the game does not show (storage) until the API catches up. Items moved into storage are not
  seen live.
- Live gear applies only to your own profile: the one the tab list names, else the one the API marks as selected.
- The accessory bag, storage, the active HOTM tree and Upgrades ownership still come from the API.
- Hypixel menu titles, the "Click to despawn" pet lore, the tab list layout and the chat texts were not checked in game
  for this release.

## Start with

- The held tool is your Tool choice, else the tool you hold in game (mod). The current HOTM tree is the best one,
  because neither the API nor the game client shows the active tree.
- Manual stat overrides (Type Stats) and temporary buffs are ignored on this tab by design.
- Wardrobe slot and ender chest page numbering in the storage labels has not been checked in game.
- With the Inventory API off only the pet can be compared.

## Session tracker

- Moving tracked items from the inventory into sacks during a session counts them twice (once picked up, once in the sack
  summary).
- Items dropped and picked up again, or received in trades outside a container screen, count as gains.
- Inventory decreases (selling, storing) and items removed from sacks are ignored; mineshaft corpse loot is not counted.
- Only the selected method counts. Sack summaries arrive at most every 30 s, so short sessions are coarse.
- The HUD setting is not saved between launches. Not yet run in game.

## Mod and versions

- Supported: Minecraft 26.1.1, 26.1.2 and 26.2 on Fabric, Java 25. Other versions (26.3 included) are not targets.
- The update notice only says a newer release exists; it does not download anything.
- Item icons carry their SkyBlock ID so texture packs can retexture them; that has not been confirmed with a pack in game.
  Upgrade icons (drill parts, reforge stones, gems, books, shards, essences, pets) are new and also unchecked in game.
- Many items (drill engines, fuel tanks, omelettes, reforge stones, gems, Tungsten, Umber) are PAPER in the official items
  resource, which draws them with Hypixel's own item models; without a texture pack these icons look like paper.
- Essence, attribute shard and pet heads come from the NEU repository (the items resource lacks them). Shards and pets keep
  our IDs (`SHARD_FLITTER`; pets as `PET` with petInfo) in custom_data, which a pack may not match. The Miner Zombie shard
  has no head there and shows the generic icon.
- The Modrinth listing may not be public yet; GitHub Releases always has the jars.

## Hosted API and relay

- **Hypixel key.** The production (application) key is still awaiting approval, so the hosted API runs on a development key
  that must be renewed by hand every few days. When it lapses, lookups fail until it is renewed. Running the local
  companion with your own key avoids this ([SETUP.md](SETUP.md#local-companion)).
- **Slow first loads.** The engine for the website runs on one small server (1/8 of a CPU core). A player's first page also
  waits for Hypixel and Mojang, so it can take a few seconds; the player's other tabs are built in the background while the
  first one is read and then answer at once. Pages for other options (another method, buy-order costs) are not built ahead
  and take a few seconds the first time; Upgrades takes longest. Cached pages answer in well under a second. The website's
  2-minute result cache is per Cloudflare location, so a repeat that lands in another location is answered from the relay's
  own page cache instead (a fraction of a second).
- **Caching.** Profiles are reused for 5 minutes (up to 30 while a refresh runs); website results for 2 minutes. Prices update
  in small steps every 3 minutes: Bazaar about every 15 minutes, NPC prices every 6 hours, the mayor every 20 minutes.
- **Limits.** Per IP, per player and overall Hypixel limits apply (for example 20 website lookups per minute per IP); a busy
  minute answers "rate limited" with a retry time. The free hosting plan caps total daily requests.
- **Mojang refuses many Cloudflare addresses,** so the mod resolves names itself and the API only falls back to the relay.
  Skins for the website need the relay to reach Mojang's session server; without it the model shows the default skin.
- **Single relay, no redundancy.** If the relay is down, the website's lookups and player pages are unavailable; the mod
  still gets profiles through the API's own key.

## Website

- **HOTM trees:** only four trees are published (MF Gems MSB and Maniac Miner, Dwarven Metals MSB, Gold MSB); the rest say
  "coming soon". Player HOTM pages compare against those only. The other trees' methods were confirmed on 2026-10-28 and
  are waiting to be transcribed and published.
- **Guides** are community guides with the date they were posted; rates and setups age as the game changes. Known stale
  parts carry a dated notice (an author's "outdated" mark, the Bal cooldown swap removed in patch 0.27); the authors' text
  itself is not rewritten.
- **Checklist:** reads the inventory, armor, equipment, ender chest, wardrobe, bags, backpacks, Loadouts sets, sacks and the
  vault (with its API on), but not the museum, minions, forge, auctions or the Rift. Applied items checked: Polarvoid
  Books, Divan's Powder Coating, power scrolls, Recombobulator, Hot Potato Books; others show "not checked". An upgraded
  item counts only when it is in the engine's recipe table. `BEJEWELED_COLLAR` is not in Hypixel's items resource, the
  `prismatic` enchant and `heroic` reforge IDs are unverified.
- **Player pages** use the mod's defaults (best owned gear, the method's usual zone and efficiency, God Potion on unless
  off). There are no manual stat overrides, no tool or zone choice and no "rank by stat points" view. Upgrade costs are the
  cash outlay only (no resale credit); the Cost option (instant-buy or buy order) works once the Worker that passes it is
  deployed. Item icons exist only for items the guides use, so many gear rows show an empty
  slot. The session tracker and anything that needs the game client stay in the mod.
- **3D model:** armor is painted on the skin's outer layer, which is thinner than real armor. FurfSky Reborn textures cover
  513 items; variants the pack picks by lore (such as Adaptive's class) and its tinted set use the base texture; other
  pieces use a material or dye colour. Without WebGL or with reduced motion a flat front view is shown.

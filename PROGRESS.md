# SkyProgression progress

Last updated: 2026-10-27 (hosted backend: calculations moved into the mod). Current release: **0.3.0**. Status: functional preview;
**not yet Mining Cat parity**. This file is the durable handoff for new chats.

## Resume here

**Deployed (2026-10-27):** the owner deployed the Worker at <https://skyprogression-api.tian-istenic34.workers.dev>;
it is now the mod's default (`HOSTED_BACKEND`). Live check: `/health` ok, cron filled the bundle (334 prices,
`complete: true`, so the regex scans fit the CPU limit), `/resale` 0.27 s then 0.16 s cached, `/prices` 50 KB in 0.16 s,
errors/Origin refusal as designed. The owner's application key was rejected from the Worker ("Invalid API key"): it is
IP-restricted (allowlist), and Workers have no fixed egress IP (static egress is not on the free plan), so it cannot be
used there. The owner then stored a personal key, which works: live end-to-end check 2026-10-27 11:50 UTC (scratch runner,
same calls as the mod): profile 0.54 s, decode 0.18 s, prices 0.14 s (334 + mayor), stats 0.08-0.16 s, compare ~0.4 s,
start ~0.4 s, resale quotes returned. Open decision: personal keys are not meant for public apps; for public release either
an application key without an allowlist (ask Hypixel) or `/profile` on a fixed-IP host (VPS/Azure) with prices staying on
Cloudflare. Workers Logs (observability)
enabled in `wrangler.jsonc` so deploys keep it on. Mojang returns 403 to the Worker, so the mod now resolves names itself (own name from the session UUID, others
at Mojang directly, backend `/uuid` as fallback). The KV namespace ID that wrangler wrote into the owner's local
`wrangler.jsonc` is deliberately not committed. **Owner next:** set the secret, then Load in game with the default config;
until then set `"backend": "http://localhost:18475"` and restart the (new) companion.

**Hosted backend (2026-10-27, in-game test pending).** Owner decision: a thin Cloudflare Worker
proxy (free plan, nothing to maintain) in <https://github.com/tianiste/SkyProgression-API>; no TypeScript engine. Every
report (`/stats`, `/compare`, `/start`, resale credit) is now calculated in the mod by `core/` on `ClientData.WORK`
(new `core` `PlayerData`: raw profile decoded once, owned items per profile; cancellation and stale-key rejection as
before; profiles older than 5 minutes refetch in the background). The mod reads `config/skyprogression.json`
`"backend"` (empty = `ClientData.HOSTED_BACKEND`, empty until deployed, so the local companion is the default). Backend
endpoints (Worker and companion alike): `/health`, `/uuid?name=`, `/profile?uuid=&fresh=1` (raw Hypixel body, 5-minute
cache, stale-while-revalidate), `/prices` (bundle: prices + mayor perks + complete), `/resale?tag=&<filters>`. The
companion lost `/report`, `/stats`, `/compare`, `/start` (an old companion makes the mod say "older than the mod").
Worker: profile pass-through (memory + Cache API), cron `*/3` builds the price bundle in KV in steps (Bazaar from Hypixel's
public bazaar every ~15 min, 175 Coflnet AH/pet quotes in batches of 40, NPC prices every 6 h, mayor every 20 min);
large resources scanned by regex (3 ms) because `JSON.parse` takes ~15 ms > the free 10 ms CPU limit; rate-limit bindings
(240/min per IP, 4/min per player, 100/min Hypixel); 503 without key; `src/price-ids.json` from `./gradlew
:core:exportPriceIds`. Verified: Worker locally with `wrangler dev` (key only in the process environment): health,
uuid, profile MISS 0.8 s then HIT 5 ms, 6 cron steps -> 334 prices + mayor, resale; the mod's data path with a scratch
runner against that Worker and a companion on 18476 (decode 0.19 s, stats 0.1-0.17 s, compare ~0.45 s, start ~0.5 s,
same totals both ways). Not run in game. **Owner next:** `npx wrangler login` in SkyProgression-API, then deploy
(steps in docs/SETUP.md "Hosted API (owner)"), apply for a Hypixel application key and `wrangler secret put
HYPIXEL_API_KEY`; put the Worker URL in `HOSTED_BACKEND` (or the config file) and check Load, Gear/Upgrades/Methods,
resale net costs filling in, and the error messages (backend down, no key, rate limited, old companion).

**Direction (2026-10-26): build our own Mining Cat + SkyCompass equivalent.** No
runtime dependency on either tool; their outputs are validation evidence only.

**Agreed plan (owner, 2026-10-26).** Treat the current Fortune/Speed numbers as correct for now and
fix them later; do not block on the in-game test. Order:
1. **Implemented (`d567858`), in-game test pending.** Automatic method stats: Gemstone/Ore/Dwarven Metal/
   Block Fortune, Mining/Gemstone Spread, Pristine in the same ledger, `/stats` and Breakdown tab (which now
   shows only the method's stats plus the focused one). Feed Upgrades/Rates; Type Stats still overrides.
   Fixture vs in-game menu: Spread 300, Gemstone Spread 2.5, Dwarven Metal 5, Pristine 13.4 match;
   Gemstone 180 vs 195, Ore 100 vs 110, Block 115 vs 125 unexplained (likely untrackable consumables/buffs).
   New: Magma Fields zone (MF default; Bal Furnace +2.91 Pristine there), Block target, one method
   weighting for gear/pet/HOTM tree. Rules and sources: CALCULATIONS.md "Stat breakdown: method stats".
2. **Gem rate engine implemented, not validated against a measured session.** `MethodRates` for MF Gems/Ruby/
   Topaz (`94517a1`) and Grotto Jasper (`f37371c`, strength 4,800) on the Rates tab: ticks from block strength,
   MSB cycle from the tool lore, wiki Pristine/Fortune drops, Flawless = 512,000 Rough, coins at the buy quote,
   next tick threshold. Owner: treat the MF rate as correct for now. Fixture at 65%: ~12.0 Amber/h, ~6.7 Jasper/h
   (Grotto 5 ticks during MSB, 28,000 < 32,000). Estimated hypermax: ~19.6 Amber/h at 60% (guide 20.1), ~12.8
   Jasper/h at 65% (guide "up to 12" Mansion, which also mines other gems).
   **Upgrades ranked by rate (`c8b6afb`, in-game test pending):** MF Gems/Ruby/Topaz and Grotto rank by rate after /
   before - 1 with before/after Flawless/h on each card; other methods keep the drop multiplier; the Upgrades header
   names the model. New fuel tank upgrades (wiki Drill Parts 2/4/6/10%) count through MSB uptime; Gemstone tank
   +0.42% on the fixture (Mining Cat page: +0.39%). Rules: CALCULATIONS.md "Upgrade ranking by rate", "Rates: Fairy Grotto".
   **Drill parts and step-by-step plan (`081b612`, in-game test pending):** engines (Mithril..Amber-Polished) and
   modules (Sunny Side, Starfall Seasoning) are upgrades; the Upgrades tab shows a 12-step plan (take best cost per
   1%, apply, recompute; materials consumed). Fixture MF Gems plan with placeholder prices: Will-o'-wisp, Amber engine,
   Perfectly-Cut tank, Glossy Mineral Talisman = +8.7% rate. Rules: CALCULATIONS.md "Step-by-step upgrade plan".
   **Owner decision (2026-10-26):** every upgrade that adds any mining stat should be an upgrade.
   **Catalog, reforges, pets (`a75f9aa`, `e6659d3`, `1a3614b`, in-game test pending):** catalog generated from the
   items resource (112 items; tools limited by Breaking Power); reforge-stone swaps from the NEU table (MIT); level-100
   pet swaps per rarity (lowest level-100 BIN) and Quick Claw. Fixture Grotto: Legendary Scatha +5.1% over the
   L97 Bal + Quick Claw; MF Gems keeps Bal. Rules: CALCULATIONS.md "Item catalog", "Reforges", "Pets and pet items".
   **More sources (`628c77c`, `5b5dc2c`, `c713a6e`, in-game test pending):** recombobulation, gem type swaps, Divan's
   Powder Coating, Power accessories' gem slots; essence perks, attribute shards and HOTM perk levels (free from
   unspent powder, else grinding); Miner Outfit/Glacite/Mineral set bonuses in armor swaps plus full-set offers.
   Rules: CALCULATIONS.md "Recombobulation, gem swaps...", "Essence perks, attributes, HOTM perk levels", "Set bonuses".
   Breakdown fix: Glacite Expert Miner = 2 x Mining level; Mineral/Glossy tiered bonus
   counted once per set (per-piece value x pieces worn). Checked with synthetic pieces (120 Speed at level 60; 3
   Mineral pieces = 300 Spread); the fixture wears neither, so its ledger is unchanged (Spread 300).
   Later (`2727183`, `16debfa`, `bbbcf3d`): True Dwarf/Dwarf Wannabe equipment set bonuses in upgrades (fixture Coal:
   Titanium Gauntlet now +14 Mining Fortune over the Handwarmers); one list entry per level chain; the plan bans an
   earlier step that a later one would replace (fixture Grotto now goes straight to Ambered instead of Lustrous then
   Ambered); powder fix: `powder_<type>` is the unspent amount (owner: 21.2M Gemstone; the overview showed ~42M and the
   HOTM options used ~377k), so fixture Mining Master 9 -> 10 is now free from 4.6M Glacite Powder.
   **Coverage completed (`c2fbd33`, `e0527a0`, `0c2db1e`, in-game test pending):** HOTM perk unlocks along the
   shortest tree path (1 token per node, path tier gate; fixture with 4 perks removed: 4 tokens, Gem Lover 1 token,
   Mining Master via Rags to Riches 2); Blue Cheese omelette as a module (+1 level on unlocked perks, also counted in the
   Breakdown when fitted; loses to the fixture's Sunny Side); Mining skill and Tungsten/Umber/Glacite/Obsidian
   collection tiers as grinding steps with the distance left; buy vs craft/forge from NEU recipes (`mining-recipes.json`,
   HOTM/collection unlocks, forge time); net cost after selling the replaced item (modifier-aware lowest BIN per copy,
   else Coflnet sell, else "no sale price"); drill part swaps add the 9,999 removal fee. Rules: CALCULATIONS.md
   "Essence perks, attributes, HOTM perk levels", "Buy or craft/forge", "Replacement resale credit".
   **Coal rate model (in-game test pending):** `MethodRates.blocks` for Coal (wiki Block Strength 30, ores instamine above
   60 x strength = 1,801, airborne Mining Speed / 5 = 9,001, 8% airborne share from the Mining Cat page, MSB from lore,
   Coal = 1 x (1 + (Mining + Ore Fortune) / 100), Spread blocks assumed Coal, default 82.5%, shown per 30 minutes with two
   lobby hops). Rates tab card; Upgrades rank by rate with before/after Coal / 30 min, header names the unit. Fixture:
   ~3.89M Coal/h (1.95M / 30 min); 9,001 Speed would add ~20%. Guide 4-9M/h hypermax; estimated hypermax ~8.3M/h.
   Differences vs Mining Cat: Spread rows (Snoozle +8: ours 2.0%, theirs 1.6%) documented, not tuned. Wiki fix: non-ore
   strengths are instamine / 30 (Cobblestone 20, not 10). Rules: CALCULATIONS.md "Rates: Coal".
   **Obsidian rate model (in-game test pending):** strength 500 (instamine > 30,000), 4-tick soft cap otherwise, so
   speed only counts when MSB lifts it above 30,000; Block Fortune; shown per ability cycle (cooldown), ranked hourly.
   Fixture ~0.93M/h at 65%; 8,572 Speed would add ~41%. Hypermax estimate ~2.6M/h vs guide ~3.5M/h (gap documented).
   **Tungsten / Umber / Shafting (Glacite) rate models (in-game test pending):** 5,600 / 5,600 / 6,000 strength, tick model
   with MSB, Dwarven Metal Fortune, per hour. Base drops vary by block (vein mix unknown; Glacite's not on the wiki), so
   rates are per 1 base drop and labelled. Fixture ~200.7k/h Tungsten/Umber, ~190.6k/h Glacite at 65%. No guide rates.
   **Gold / End Stone / Cobblestone rate models (in-game test pending):** Pure Gold 600 (instamine > 36,000, base 5-7),
   End Stone 30 and Cobblestone 20 (instamine > 30 x strength). Fixture: Gold ~5.5M/h at 65%; End Stone/Cobble ~4.7M/h at
   82.5%. Hypermax estimate ~8.5M/h vs guides 11-16M / 13-16M (gap documented; Maniac Miner not modelled). Open: whether
   Mining Fortune applies to Pure Gold (2023 changelog).
   **Mycelium / Red Sand (in-game test pending):** no wiki strength, assumed instamined (owner: 20 blocks/s is doable);
   guide-max Fortune 1,522 + Spread 400 gives ~4.8M/h, matching the guide's 4.6M / 4.8M. Every method now has a rate model.
   Coal and Gold default to the Crystal Hollows (owner); fixture numbers unchanged.
   **Owner in-game check (2026-10-27):** the stat breakdown and every method's rate model work well; treat them as correct for now.
   **Coins/hour across methods (`c4a67e6`, `4cf9fb0`, in-game test pending):** `MethodComparison` + companion `/compare` (every
   method's totals with its own best owned gear/tool in its usual zone) and a new **Compare** tab: methods ranked by coins/hour
   with items/h, efficiency, model label, value window, drops note, tool/zone and the Coflnet quote (source, time); Price toggles
   instant-sell (default, Coflnet `sell`) / sell offer (Coflnet `buy`), God Potion toggle, Pick method or click a card to select.
   Unknown prices stay "coins unknown" and sort last (End Stone had no sell figure). Rates tab cards show both figures. Upgrades
   cards show coins/h here and the same change's gain/coins in the best method; Compare lists +50 Fortune / +100 Speed per method.
   NPC sell (owner request, 2026-10-27, in-game test pending): third price mode from the official items resource's
   `npc_sell_price` (companion, 6 h cache); gems count as Rough x 512,000 (Flawless has no NPC price; Rough/Flawed/Fine
   pay the same per Rough). End Stone now has a known value (NPC 2); Glacite, Mycelium, Red Sand, Gold sell higher to NPCs.
   Coleweight: no verifiable coefficients (module archived, no weights in its repo), recorded unknown and not shown.
   Rules: CALCULATIONS.md "Coins per hour across methods".
   **Start with tab (`69e34a3`, `51907cd`, owner confirmed working in game 2026-10-27):** `StartWith` + companion `/start` + a **Start with** tab
   (between Breakdown and Rates): best owned loadout for the method by the existing choice (`Loadout.bestFor`/`bestTool`,
   `chooseTree`) with each piece's storage (worn, hotbar/inventory, wardrobe slot, ender chest page, backpack slot, Loadouts set,
   pets menu, pet item), swaps from the worn loadout only (each rated alone through `MethodRates` at the Price mode, largest gain
   first, total before/after, "already best" when none), and a best-method hint (gear worn now vs Compare's best owned).
   Held tool = the Tool choice shared with Breakdown (API has no held item; without it no tool swap). Rules: CALCULATIONS.md
   "Start with: best owned loadout".
   **Rest of the stat model (`2fbfa62`, `5d1f3b5`, `8014139`, `3544f5c`; owner: works in game 2026-10-27, detailed menu comparison still open):** Mining Wisdom, Breaking Power,
   Pickaxe Ability Cooldown (reductions multiply), Heat and Cold Resistance (no longer manual) and All/Mithril/Gemstone/Glacite
   powder buffs in the same ledger and `/stats`; Breakdown tab **Stats** picker (Method, General, Gemstone, Blocks & Ores, Dwarven
   Metal, Resistance, Powder, All). New sources: Seasoned Mineman, Keep It Cool, Warm Heart, Powder Buff, Mineshaft Mayhem, Sky
   Mall cooldown/powder, fuel tank, Amber Power Scroll, Bal/Glacite Golem/Silverfish/Mithril Golem/Scatha perks, six attributes,
   Dwarven Training, Frozen Skin, Reaper Pepper, Core of the Mountain notes. Temporary: mayor/minister perks from the official
   election resource (Cole's Fiesta +25% Fortune / +25 Wisdom live), mapped potion effects; never counted. Rates use the cooldown
   ledger (103.68 s vs lore 108 s: MF Gems 12.04 -> 12.14/h). Current vs attainable maximum per source and per stat (unknown
   while gear lines have no known maximum). Snapshot vs SkyCompass: Heat 204.5, Cold 5.4, BP 9, powder 25/10/10/0 match;
   Wisdom 84 = their 39 + God Potion 20 + Booster Cookie 25. Rules: CALCULATIONS.md "Stat breakdown: other stats, powder buffs,
   temporary buffs, attainable maximum".
   **Next:** owner restarts the companion (new `/compare`, `/start`, and `/stats` with the new stats and mayor perks) and checks the
   Breakdown tab Stats picker, the new ledgers against the in-game stat menu (Wisdom, Breaking Power, Cooldown, Heat/Cold, powder),
   the attainable lines and check marks; then the Compare tab and the Start with tab (Start with already confirmed): piece
   list and storage labels against the game (wardrobe slot and ender chest page numbering especially), swaps after moving a piece
   to storage, Held tool, Price, God Potion, Refresh, the hint, and the old-companion message. Roadmap 4's view is now built (below). Open: a measured session for the End Stone/Cobble/Obsidian guide gaps; Maniac Miner's Fortune rule;
   Fungus/Harena Fortuna in the stat ledger; Bazaar tax and NPC sale prices in coins/hour.
   **GUI redesign (2026-10-27; owner confirmed in game that it is good, 2026-10-27):** four icon tabs (Home, Gear, Upgrades, Methods) plus a More page
   behind the settings button replace the eight text tabs; nothing was removed (map in docs/DESIGN.md "Interface layout").
   Engine, companion and numbers unchanged. Owner should check in game with `build/libs/skyprogression-0.3.0.jar`: top
   bar at full and narrow widths (the second row below 860 px), Home stat bars and their tooltips, Rate now and Best next
   upgrade tiles, chart clicks, Gear slot grid (gold tool outline, cyan pieces to fetch, click detail), stat bars by category
   and the "+ N situational..." expansion, Upgrades plan/list cards and tooltips, Rank toggle, Methods rate card and
   assumptions, More page (Type Stats, Demo, gear audit), keys R/F1/1-4/PgUp/PgDn, the big rate number's scaling, short
   screens, Demo, loading, error and old-companion messages.
   **Other & progress view (2026-10-27, in-game test pending):** Upgrades tab **View** toggle (Purchases / Other & progress),
   `OtherUpgrades` on the analysis worker, `/stats` adds `petLevel`. Three parts: guide stage (Early / Mid / End game or Before the
   checklist, from the checklist's stage headings only; fixture: Mid game); "Can't check: skip if you already have it" (Dwarven
   O's Gemstone Grahams / Ore Oats / Block Bran / Metallic Minis x5, Gold Box X, Royal Riches X for Crystal Hollows methods,
   Dwarven Expertise 10; wiki-verified effects, Coflnet price, rate gain; fixture Coal: Gold Box +2.5%, Oats +0.51%); "Progress you
   have to grind" ranked by rate gain without coins (HOTM/skill/collection levels the balances don't cover, pet to level 100 with
   XP left, Compact tier and blocks left, forge-only items such as the Relic of Power with inputs, forge time and HOTM tier;
   fixture MF Gems: Relic of Power +6.3%, Bal 97 -> 100 +0.76% for 4.45M XP, Compact 9 -> X 190,452 blocks, +0%). Left out after
   checking: Refined Dark Cacao Truffle (farming), Chilled Pristine Potato (1-hour buff). Rules: CALCULATIONS.md "Other upgrades
   and progression". **Next:** owner restarts the companion (new `/stats` field) and checks the view in game: the View toggle,
   the stage card and its expandable list, the bonus and grinding cards and tooltips (effect, distance, how, notes), MF Gems vs
   Coal vs Tungsten (different bonuses), Demo and Inventory-API-off messages.
   **Owner in-game check (2026-10-27):** the Other & progress view and the faster stats loading (cached profiles and prices
   served while refreshing) are good.
   **Heat, Cold, Maniac Miner, secondary drops (`8c80f41`, in-game test pending):** rules and sources in CALCULATIONS.md "Rates: pickaxe
   abilities, Heat and Cold" and "Rates: secondary drops". Heat (Magma Fields): mine to 90, cool 18 s in water, kept = build /
   (build + 18 s) with build = 900 x (1 + Heat Resistance / 100) s; fixture 99.3% (MF Gems 12.14 -> 12.06). Cold (Glacite Tunnels,
   Mineshafts with rising Cold Vulnerability): mine 0 to -90, Mining Speed -0.5% per Cold beyond Cold Resistance through the tick
   model; fixture 81.5% Tungsten/Umber (~202.7k -> 165.3k per base drop), 82.4% Shafting. Heat/Cold Resistance are now rate stats
   (Compare, Start with) and gain value in Upgrades. Crystal Hollows zone: Heat only below Y 64, share unknown, not applied (labelled).
   Maniac Miner: wiki levels (+5/10/15 Fortune per block, 25/30/35 s, cap 500, 120 s with the cooldown ledger), level from lore or
   1 + Core of the Mountain 2 + Blue Cheese; every card lists the other ability at the same level (fixture: Coal +3.3%, End Stone/
   Cobble +6.9%, MF Gems -18.3%). Secondary drops on the Rates card and Compare tooltips, Compare ranks by the total: Coal Hard Stone
   at the Coal guide's 0.69 per Coal (route estimate; instant-sell price unknown), Grotto other BP7 Flawless at the guide's 3.1/12
   (cheapest of four; fixture +3.1M/h), Shafting Frozen Corpse loot from the wiki tables and pity counter (~12.8 shafts/h, Lapis
   2.27M/h; Umber/Tungsten corpses unknown: Crystals unpriced and priced loot below the key). Hard Stone on gem/metal routes:
   amount unknown. **Next:** owner restarts the companion (new rate stats in `/compare`) and checks the Methods rate card (Heat /
   Cold line, "+" secondary rows and tooltips, Total coins, the "instead" assumption), Compare totals and order, MF Gems vs Grotto with
   the zone on Magma Fields, Tungsten upgrades now showing Cold Resistance value, and a Maniac Miner tool if one is owned (the lore
   pattern is assumed).
3. **Implemented, in-game test pending.** Upgrade coverage: one shared list with real deltas from the current state,
   prerequisites (HOTM tier/tokens, recipe unlocks), mutual exclusions and prices, valued by each method's model, with a
   greedy sequential plan. Remaining: recursive recipes, Core of the Mountain levels for tokens, scrolls, pet-item carry-over.

**Stat Breakdown engine (mining)**: background and history below.
Use Debotes / Zucchini and the private live snapshot described below. First inspect
which raw fields expose the selected gear, drill parts, pet, HOTM perks, powder,
attributes, collections, essence shops and permanent bonuses. Build a source ledger
before integrating it into UI. Do not substitute displayed lore totals for complete
effective player stats. Target scope (feature spec from SkyCompass, see
`docs/references/SKYCOMPASS.md`):
- Stat groups: General (Fortune, Speed, Spread, Wisdom, Breaking Power, Pickaxe Ability
  Cooldown), Gemstone (Fortune, Speed, Spread, Pristine), Blocks & Ores (Block/Ore
  Fortune, Speed, Spread), Dwarven Metal (Fortune, Speed), Heat/Cold Resistance, Powder
  buffs (All/Gemstone/Mithril/Glacite).
- Each stat: current total, attainable max, itemized per source (current / max, maxed ✓).
- Source states: counted; owned-but-situational (location/condition); missing; trackable
  temporary (consumables, mayor, verified live); untrackable (never in API: neither
  confirmed nor ruled out). Only counted sources enter the total.

1. **Done (2026-10-26).** Sanitized fixture `core/src/test/resources/fixtures/debotes-zucchini.json`
   (regenerate: `./gradlew :core:extractFixture`), field map in
   `docs/references/API-FIELDS.md`, `ProfileFixtureTest`. HOTM now read from `skill_tree`.
2. **Done (2026-10-26).** `Loadout` (equipped vs best owned, selected tool), `StatContext`
   (zone/target conditions), `Breakdown` (per-source state + confidence) and `StatBreakdown`
   for Mining Fortune and Mining Speed. Rules and sources: docs/CALCULATIONS.md "Stat breakdown".
3. **Partly done.** Fixture: Fortune 1,724.5 (= SkyCompass); Speed 7,050 in the Dwarven Mines vs
   SkyCompass 7,150.5 (unexplained 100.5, see SKYCOMPASS.md). In-game menu (drill held):
   Fortune 2,286.27 / Speed 7,610; 2,286.27 = 1,814.5 x 1.26 suggests Fiesta +25% and Auspicious
   +1% additive. Confirmed by the owner's detail pages: Speed matches exactly once untrackable
   weather/potion are added. Percentage buffs (flat x (1 + sum%)) and Sky Mall `dig_speed` are now
   encoded. Owner decision: temporary buffs (Fiesta, weather, Sky Mall) are not counted; God Potion
   is assumed (Spelunker V / Haste IV) and the Booster Cookie is read from the API (no Fortune/Speed).
   Fixture defaults: Fortune 1,749.5 x 1.01 = 1,767.0; Speed 7,250 (Dwarven Mines), which equals the
   in-game 7,610 minus temporary buffs. 15 Fortune is unconfirmed (likely Fortunate Festivity, temporary).
   Open: Auspicious +1% mechanic, Mole/Glacite Golem
   pet rates, Front Loaded/Mineshaft Mayhem/Eager Adventurer API keys, Blue Cheese +1 perk level.
4. **Implemented (2026-10-26), awaiting the owner's in-game test.** Companion `GET /stats` (`StatsReport`,
   see CALCULATIONS.md "Stat breakdown") and a new **Breakdown** tab: held-tool picker (owned tools), zone
   picker (defaults to the method's usual zone), God Potion toggle, Refresh. Automatic Fortune/Speed feed
   Upgrades/Rates and the header (marked "auto"). Owner decision: default to the best owned gear and tool
   for the method (`Loadout.bestFor`/`bestTool`, commit after `0a462ce`); **Gear** toggles to equipped,
   **Tool** picks a specific tool, and a "Gear used" card lists each piece's storage location; Type Stats values override per stat, blank = automatic
   or unknown. Selections persist per profile+method in `skyprogression-inputs.json`. Older saved inputs
   hold all ten stats, so they act as overrides until cleared. Live check: `/stats` for Debotes with
   TITANIUM_DRILL_4 in the Dwarven Mines returned Speed 7,250 and Fortune 1,767.0 (= fixture). With the
   automatic best gear (Coal) the tool resolved to the DR-X655 and the gear to the worn Divan set, same totals.
   Following the Mining Cat developer's notes: the best HOTM tree for the method is used (`mining_2..5`
   are Loadouts trees; none is marked active), Loadouts armor/equipment sets count as owned storage, and
   default efficiency is 82.5% for instant-mine blocks (was 90%), 65% otherwise. The developer's value
   windows (Obsidian per MSB cycle, Shafting/metals per hour, Coal per 30 min with two lobby hops) are
   recorded for the rate engine (roadmap 2) but not implemented yet.
   **Next:** owner tests the Breakdown tab in-game (tool/zone/God Potion changes update totals without
   stutter; overrides win; Demo and companion-offline show clear messages). Then extend the ledger to
   Gemstone/Ore/Dwarven Metal/Block Fortune and Pristine so method yield stops needing typed values.
5. Document numerical discrepancies instead of tuning constants to match references.

Acceptance: supported stats derive from API inputs with source breakdowns, bonuses are
not double-counted, unsupported sources remain explicit, and loading/calculation stays
off the game thread. Full method validation
still needs matching Mining Cat rates/stat totals and the owner's in-game comparison.

**Method specs:** `docs/references/METHOD-GUIDES.md` (from Mining Cult guides; raw
copies in `service-data/guides/`). Coal page evidence: fortune % = delta /
(100 + Mining Fortune + Ore Fortune) on counted stats (see MININGCAT.md). Speed acts via
instamine/tick thresholds and mid-air share, not linearly.

**Waiting on the owner (do independent work meanwhile):** in-game stat menu screenshots
with a same-time API snapshot; SkyCompass `/api/player/mining` JSON for Debotes captured
in the browser into `service-data/`; complete Mining Cat pages (all progression pages,
Other Upgrades, Rates, efficiency/settings, command options) for MF Gems, Grotto, Coal.

## Owner decisions and available inputs

- Name SkyProgression; mining first, extensible later. Keep normal usage simple: `/sp`.
- Workspace `D:\Projekti\SkyProgression`; private repo `tianiste/SkyProgression`.
- Minecraft 26.1.2 / Fabric / Java 25; future upgrades should remain straightforward.
- Autonomous work and incremental commits/pushes are authorized. Owner tests Minecraft.
- Test IGN **Debotes**, verified selected profile **Zucchini**, mode **normal**.
- Live Mojang/Hypixel lookup succeeded on 2026-10-26; one profile returned; Inventory API
  enabled. This verifies access, not full parser/calculation parity or every API setting.
- Key saved as Windows-user-encrypted `service-data/hypixel-key.dpapi`. Never copy it
  into this file or Git. Owner plans to rotate it later. See AGENTS.md for handling.
- Private raw API snapshot: `service-data/debotes-profiles.json`; concise profile summary:
  `service-data/debotes-summary.json`. Both ignored. Refetch when testing changed gear.
- Four user-supplied bot screenshots are preserved in `docs/references/miningcat/`;
  see `docs/references/MININGCAT.md` for observations and missing evidence.
- The supplied early/mid/end progression checklist is preserved in
  `docs/references/PROGRESSION-CHECKLIST.md`. Its FALSE cells are NOT profile facts.
- No matching bot Rates output, exact efficiency or all progression pages have been
  supplied yet. Do independent work while those are absent.
- SkyCompass Stat Breakdown page text for Debotes · Zucchini (Mining tab) supplied
  2026-10-26; observations in `docs/references/SKYCOMPASS.md`. SkyCompass has no public
  API and its terms forbid scraping/republishing output: comparison evidence only.
- Owner messaged the Mining Cat developer (Mining Cult Discord) on 2026-10-26. Reply: comparison is
  fine; code is not shared; rates are guide averages for a hypermax setup at 65% efficiency; value
  windows per method; efficiency guidance; best HOTM tree per method. Summary in
  `docs/references/MININGCAT.md` ("Developer's description"). Still comparison evidence only.
- No AI/tool attribution anywhere in commits or project files (see AGENTS.md).

## Implemented and verified

- [x] Gradle/Fabric project, independent core engine and companion service; GitHub CI.
- [x] `/sp` and `/skyprogression`, five tabs, method/stat/profile selection, offline Demo.
- [x] Hypixel/Mojang profile loading and Coflnet purchase quotes via local companion.
- [x] Inventory/container parsing, UUID deduplication, ownership across supported storage,
  inventory-unavailable handling; HOTM/mining/selected collection milestones.
- [x] 57 base-item definitions and mutually exclusive gear groups. This is limited coverage.
- [x] Individual gear instances: enchantments, rarity/recombobulation, gems, unlocked slots,
  locations and displayed lore totals. Lore replaces base values, not additive duplication.
- [x] Fortune IV, Efficiency/Silex, Prismatic, Polarvoid and supported Flawless/Perfect
  Jade/Amber/Topaz upgrades; selected requirements and Ironman/Bingo warnings.
- [x] Owned-material deductions and gemstone unlock/removal fees. Unknown prices remain
  unknown. Alternatives are independent, not a cumulative purchase plan.
- [x] Manual effective stat/rate inputs, per-profile/method persistence and drop-only
  estimates. NOT automatic exact Mining Cat rates.
- [x] Native Stitch-inspired GUI: dark/gold/cyan cards, gear icons, responsive columns,
  search selectors, scroll controls, stat form and independent cost comparison bars.
- [x] Background initialization, fetching, analysis and saving; response size/time caps,
  cancellation, stale-response rejection and screen-reopen refresh. Flat backgrounds.
- [x] Windows CMD service launcher avoids PowerShell execution-policy problems.

Latest verification (Other & progress view, 2026-10-27): `./gradlew build :service:installDist` succeeded; existing tests pass,
no tests added. Scratch runner on the private snapshot with live Coflnet quotes for the new IDs: `OtherUpgrades.of` for Coal, MF
Gems, Grotto and Tungsten (under 0.01 s each), values quoted above and in CALCULATIONS.md; a lowered copy (Mining XP 2M, two
HOTM perks removed) shows Mining 23 -> 24 as grinding and the perks as free Purchases (tokens and powder cover them). In the
runner only a few prices were fetched, so tradeable lanterns and the Pendant showed as forge-only there; Coflnet has quotes for
them (checked with curl), so live only the Relic of Power stays forge-only. The companion was compiled but not run as a service;
the mod screen was not run by the agent.
Earlier verification (rest of the stat model, 2026-10-27): `./gradlew build :service:installDist` succeeded after each step;
existing tests pass, no tests added. GitHub Actions Build succeeded for `2fbfa62`, `5d1f3b5`, `8014139` (and was checked for
`3544f5c` at handoff). Scratch runner on the private snapshot with the live election resource: all 18 ledgers for MF Gems
(0.24 s per report), values quoted above and in CALCULATIONS.md; rates with lore vs ledger cooldown for MF Gems, Grotto, Coal,
Tungsten. The companion was compiled but not run as a service (the owner's companion on 18475 was not touched); the mod's
Breakdown tab was not run by the agent. Not checked: the in-game stat menu for the new stats, any `active_effects` entry shape.
Earlier verification (Start with, 2026-10-27): `./gradlew build :service:installDist` succeeded after each step; existing tests
pass, no tests added. GitHub Actions Build succeeded for `69e34a3` and `51907cd`. Scratch runner on the owner's private snapshot
(`service-data/debotes-profiles.json`): `StartWith.of` for MF Gems, Coal and Grotto (about 0.6-0.7 s each, 8-12 KB JSON, Gson
round-trip with null `from` and the ability intact); with no held tool chosen and an unowned tool chosen; and on a modified copy
(helmet to wardrobe index 40, necklace to the ender chest, Mineral Talisman to backpack 2, no active pet), which listed the four
swaps with storage labels as quoted in CALCULATIONS.md. The companion's `/start` was compiled but not run as a service (the
owner's companion on 18475 was not touched); the mod's Start with tab was not run by the agent.
Earlier verification (coins/hour across methods, 2026-10-27): `./gradlew build :service:installDist` succeeded after each
step; existing tests pass, no tests added. Scratch runner on the fixture with live Coflnet quotes: `MethodComparison.of` for
all 14 methods, a Gson round-trip of the `/compare` payload (8.9 KB, same ranking), both price modes and `gain` (numbers in
CALCULATIONS.md). The companion's `/compare` was compiled but not run as a service (the owner's companion on 18475 was not
touched); the mod's Compare tab and Upgrades line were not run by the agent.
Earlier verification (HOTM unlocks, Blue Cheese, grinding, buy vs craft, resale, 2026-10-26): `./gradlew build
:service:installDist` succeeded after each step; existing tests pass, no tests added. GitHub Actions Build succeeded for
`c2fbd33` and `e0527a0`. Scratch runner on the fixture with live Coflnet quotes for all 257 price IDs (fetched directly;
the owner's running companion was not restarted or touched): unlock paths, tokens and tier gates on a tree with four
perks removed; Mining 27 -> 28.. and Tungsten tier 9 as grinding on a lowered copy; craft/buy choices and resale credits
(old engine 71.0M, old tank 27.0M, Mineral Talisman 1.1M) as quoted in CALCULATIONS.md. Coflnet copy filters checked with
curl (DR-X655 with its parts 480M; Divan helmet with Jaded and recombobulation 50M; L97 Bal with Quick Claw: no listing).
The companion's new `/stats` resale fetch was compiled but not run against the live service. Mod screens not run by the agent.
Earlier verification (recomb, coating, gem swaps, profile perks, set bonuses, 2026-10-26): `./gradlew build
:service:installDist` succeeded after each step; existing tests pass (the maxed-drill test now also sets coating and
recombobulation; no tests added). Plans checked on the fixture (MF Gems, Obsidian) with a scratch runner: coating
+6.9% on the drill once Will-o'-wisp crosses the tick; Mining Master 9 -> 10 needs 1,419,857 Glacite Powder (first
shown as grinding from a wrong unspent value, fixed in `bbbcf3d`: 4.6M unspent, so free); Obsidian picks Glossy Mineral pieces. Mod screens not run by the agent.
Earlier verification (catalog, reforges, pets, 2026-10-26): `./gradlew build :service:installDist` succeeded after
each step; existing tests pass (the maxed-drill test now also allows reforge swaps; no tests added). Catalog
regenerated from a saved items resource; NEU table generated from commit `f19539bb`; rankings and plans checked on
the fixture with a scratch runner; Coflnet pet BIN filter checked with curl; StatsReport pet fields round-trip
through Gson. GitHub Actions Build succeeded for `a75f9aa` and `e6659d3`. Mod screens not run by the agent; the
companion was not restarted against the live API, so pet prices in the mod are unverified.
Earlier verification (rate ranking + Grotto, 2026-10-26): `./gradlew build :service:installDist` succeeded after
each step; existing tests pass (one existing drill test now sets a maxed fuel tank; no tests added). Rankings and
Grotto rates checked on the fixture with a scratch runner. GitHub Actions Build succeeded for `c8b6afb`
(run 40359432674). Mod screens not run by the agent; companion not restarted against the live API.
Earlier verification (method stats + MF gem rates, 2026-10-26): `./gradlew build :service:installDist`
succeeded after each step; existing tests pass, no tests added. Ledgers and rates checked on the fixture with
a scratch runner (values above). GitHub Actions Build succeeded for `d567858` (run 40353140752). The mod
screens were not run by the agent; the companion was not restarted against the live API for this step.
Earlier verification (Step 4 `/stats` + Breakdown tab): `./gradlew build :service:installDist` succeeded;
no tests added. Companion run locally with the stored key: `/stats` returned the fixture totals and a 400
for an unknown zone. GitHub Actions Build succeeded for `f01bdc3` (run 40347288413). The mod screens were
not run by the agent (owner tests Minecraft).
Earlier verification (Step 2 breakdown): `./gradlew build :service:installDist` succeeded; one
minimal fixture totals test added (owner asked for minimal tests; manual testing by the owner).
GitHub Actions Build succeeded for `2092b32` (run 40342758813) and `c603977`
(run 40342893779). No in-game comparison yet.
Earlier verification (Step 1 fixtures): build succeeded, **33 core tests passed**.
Verification at 0.3.0: local build succeeded; **29 core tests passed** (26 calculation/
parser tests + 3 HTTP success/size/stalled-body tests). No in-game visual or freeze
reproduction was performed by the agent. Owner confirmed an earlier build worked;
0.3.0's in-game validation is still pending. GitHub Actions run 40330025436 for commit 269f77c was verified completed successfully on 2026-10-26.

Recent milestones:
- `15f2845`: actual gear/modifier upgrades and owned materials (0.2.0).
- `b8bdbfd`: 0.2.0 coverage documentation.
- `c09874c`: native Stitch design and responsiveness changes (0.3.0).
- `269f77c`: design adaptation and freeze review documentation.
- `2092b32`: loadout model and Mining Fortune/Speed source breakdown (Step 2).
- `f01bdc3`, `03b44bc`: companion `/stats` endpoint; Breakdown tab and automatic totals (Step 4).

## Remaining roadmap, in dependency order

### 1. Accurate automatic stats and loadouts (our Stat Breakdown) — next

- [x] Stat model covering all groups listed in "Resume here", with current/max per source
  and the counted / situational / missing / trackable / untrackable states (`2fbfa62`, `3544f5c`; in-game test pending).
  Fortune/Speed (`2092b32`), type Fortunes, Spreads, Pristine (`d567858`); Wisdom, Breaking Power, Cooldown, Heat/Cold
  Resistance and powder buffs (`2fbfa62`). Gear stat lines have no known maximum, so most stat maxima show "unknown, at least".
- [x] Trackable temporary sources from the API where available (mayor/minister perks from the election resource incl. Cole's
  Mining Fiesta, mapped `active_effects`/`paused_effects`/`temp_stat_buffs`; `5d1f3b5`); God Potion and never-reported
  effects stay "Not in the API".
- [x] Companion `/stats` endpoint and an in-game breakdown view (implemented; in-game test pending).

- [x] Parse/evaluate selected and alternative valid loadouts; don't equip stored items
  implicitly or stack incompatible accessory/pet/gear alternatives (`Loadout`, `2092b32`).
- [ ] Armor/equipment/tool base stats, reforges, recombobulation, gemstones, enchants,
  powder coatings, scrolls, drill engine/tank/omelette and set bonuses.
  *Partial:* all of these are in the ledger (Blue Cheese +1 HOTM level since `c2fbd33`; Amber Power Scroll since `2fbfa62`,
  situational); scrolls are not upgrade options yet.
- [ ] Mining skill, HOTM/Peak perks and allocated powder, collections, essence shops,
  accessory contributions, permanent consumables and attributes.
  *Partial:* every mining stat from skill, HOTM perks (with Blue Cheese), collections, essence perks, accessories, attributes,
  powder buffs, Core of the Mountain (notes) and Reaper Pepper. Not encoded: Echo of Wisdom/Echoes, keys never observed
  (Frozen Skin, Wumpa, Atomized Glacite, Mineshaft Mayhem, Reaper Pepper are assumed).
- [ ] Pet rarity/level/abilities/items and method/location-dependent bonuses.
  *Partial:* level from XP; Bal/Scatha/Mithril Golem/Silverfish stats and Quick Claw.
- [ ] Model gem/ore/block/dwarven Fortune, Speed, both Spreads, Pristine and Cold
  Resistance with conditions; keep uncheckable sources explicit.
  *Partial (`d567858`, `2fbfa62`):* all of these, Cold Resistance included; Heat and Cold Resistance feed the rates since `8c80f41`.
- [ ] Full current/max breakdown with compatible, attainable targets; do not sum
  mutually exclusive maxima or call a base-item bar a maxed account.
  *Partial (`3544f5c`):* per-source and per-stat attainable with this loadout; gear stat-line maxima (reforge, gems,
  enchantments) and other-loadout maxima (best tool/pet/armor/tree) are not modelled, so they show as unknown.

### 2. Method-specific yield and rate engine

- [x] MF Gems end-to-end first; Ruby and Topaz variants next; Grotto comparison next (`MethodRates`; owner's in-game
  check 2026-10-27 works; not validated against a measured session).
- [x] Coal, Gold, Umber, Shafting, Obsidian, Tungsten, Red Sand, Mycelium, End Stone,
  Cobblestone: correct blocks, gear constraints, bonuses and default efficiency (same status; guide gaps documented).
- [x] Speed breakpoints/instant mining, abilities/cooldown uptime, spread availability,
  heat/cold, drops and Pristine effects. Document simplified route assumptions (`8c80f41`; in-game test pending).
  Tick thresholds, instamine, airborne share, Mining Speed Boost and Maniac Miner uptime (cooldown ledger), Spread, Pristine, Heat
  (Magma Fields), Cold (Glacite Tunnels/Mineshafts) and secondary drops (Coal Hard Stone, grotto gems, corpse loot). Still unknown and
  labelled: Heat below Y 64 in the Crystal Hollows zone, reset/water trip time, Hard Stone on gem/metal routes, Crystal prices.
- [x] Automatically sourced coins/hour; correct units, price source/time and explicit rate assumptions; manual scenario
  kept (`c4a67e6`, `4cf9fb0`; in-game test pending). Coleweight coefficients could not be verified: documented as unknown.
- [x] Show equal gear deltas producing different value in different methods (Upgrades cross-method line, Compare
  "Same change, different value"; fixture: +50 Fortune +2.5% Coal vs +2.4% MF Gems, +100 Speed 0% between thresholds).

### 3. Complete upgrades, costs and ordering

- [ ] Expand rule coverage from the supplied checklist, with verified names/IDs,
  requirements, applicability, delta formulas and acquisition paths.
  *Partial:* catalog (112 items), parts, reforges, pets, profile perks, HOTM unlocks, skill/collections; not scrolls.
- [x] Occupied universal-slot gem swaps (e.g. Topaz to Jade for Coal) (`628c77c`; in-game test pending).
- [x] Reforges, recombobulation, coatings, attributes, pets/items, drill modules (incl. Blue Cheese),
  accessories, equipment and permanent bonuses (implemented; in-game test pending).
- [x] Buying vs crafting/forging, owned recipe materials, required unlocks and forge time; prices compared live
  (`e0527a0`; one recipe level, inputs not crafted in turn; in-game test pending).
- [x] Replacement resale credit and modifier-aware prices where Coflnet has them; cash outlay vs net cost; "no sale
  price" when none (`0c2db1e`; in-game test pending).
- [ ] Explicit prerequisites and dependent chains; paired upgrades and transferable
  pet items; no double spending materials or reusing the same pet item twice.
  *Partial:* HOTM tier/token paths, recipe unlocks, level chains and plan conflict bans; pet items are not carried over.
- [x] True marginal gain/value ranking and an optional sequential plan with baseline
  recalculation after each step; independent alternatives separately labeled (in-game test pending).

### 4. Other Upgrades and general progression

- [x] Separate purchasable recommendations, uncheckable/manual bonuses, and things
  that require grinding/unlocks (pet levels, Compact, collections, HOTM, etc.) (Upgrades tab View: Other & progress; in-game test pending).
- [x] Use early/mid/end guide stages for orientation; personalize actual next steps (stage card from the checklist headings;
  next steps stay the engine's ranked lists; in-game test pending).
- [ ] Explain prerequisites, where/how to acquire, owned/missing quantities, source
  confidence and distance from a validated method-specific max.
  *Partial:* Other & progress entries show requirement, distance, how to get it and caveats; forge inputs owned/missing. Not
  done: distance from a validated method maximum (no validated maxima yet), source confidence per entry.
- [x] **Start with tab (owner request, 2026-10-26; `69e34a3`, `51907cd`; owner confirmed working in game 2026-10-27):** which owned gear to use
  for the selected method before buying anything (best owned armor, equipment, tool, pet with item, method accessories and
  HOTM tree, where each piece is stored, swaps from the current loadout with stat and rate change, best-method hint).

### 5. Regression/parity validation and distribution

- [ ] Validate Debotes across MF Gems, Grotto and Coal using preserved reference cases;
  obtain full bot pages, Rates, Type Stats/efficiency and in-game effective totals.
- [ ] Cover early/mid/late profiles, missing APIs, unknown prices, duplicates, maxed
  gear, alternate profiles and Ironman/Bingo without silently filling absent data.
- [ ] Owner tests 0.3 GUI scales, loading/cancelling, close/reopen and background saves.
  If freezes persist, request latest.log and a thread dump during the freeze.
- [ ] Hosted registered backend, API quota/cache strategy and deployment when owner
  provides hosting/account decisions. Ordinary users should need only the mod.
- [ ] Packaging/update workflow and tested Minecraft-version upgrades.

## Sources and reference discipline

The screenshots expose practical gaps: net resale, paired/chained recommendations,
pet-item reuse, method-dependent speed effects, attribute tiers and non-buyable upgrades.
They are snapshots, not current market quotes or verified formulas. See the reference
notes for exact examples. The checklist is a coverage backlog, not a fixed optimal order.
Do not implement questionable mechanics solely because a reference claims them.

Primary data entry points: official Hypixel item/skill/collection resources and profile
API; Coflnet prices. Existing mechanics references and formulas are in docs/CALCULATIONS.md.
SkyCompass/Mining Cat inspire the workflow; no dependency on scraping their outputs.
Our companion service is our own API: it has `/stats`; it should grow `/upgrades` and `/rates`
endpoints backed by the core engine, and later a hosted deployment (owner decision).

## Build, architecture and local workflow

- Build/test: `./gradlew build :service:installDist` (Windows: `./gradlew.bat ...`).
- Local Java: `C:\Users\hanab\AppData\Roaming\PrismLauncher\java\java-runtime-epsilon`.
  Set JAVA_HOME for the build if needed; do not hardcode this path into portable code.
- `core/`: Model, ProfileParser, GearReader, GearUpgrades, ProgressionEngine, BoundedHttp,
  resources and JUnit tests. `service/`: local HTTP companion and external data access.
- `src/main/java/.../client/`: ClientData async lifecycle, ProgressionScreen, ChoiceScreen,
  InputsScreen, Theme and command registration. Minecraft-specific code belongs here.
- `docs/SETUP.md`, `docs/CALCULATIONS.md`, `docs/DESIGN.md` explain current behavior.
- Local companion: localhost:18475. Preserve an existing user-started service; do not
  terminate unknown processes or change its key silently. Restart instructions are in setup.
- Backend: hosted SkyProgression API (Cloudflare Worker, separate repo) or the local companion; chosen in
  `config/skyprogression.json`. Until the Worker is deployed and `HOSTED_BACKEND` set, the companion is the default.
- Previous downloadable packs live in the creating chat's outputs folder; rebuild from
  repository sources in a new chat rather than relying on those temporary handoff paths.

## Handoff log

2026-10-27 (hosted backend): new `core` `PlayerData` (decode, `member`, `stats`/`start`/`compare`), `PriceIdsExporter` +
`:core:exportPriceIds`; `ClientData` rewritten around a backend URL (config file, `HOSTED_BACKEND`, `COMPANION_BACKEND`),
local report calculation, resale quotes (15-minute cache, 4 parallel, 1.5 s wait, late quotes swapped into a new map),
mayor perks from the bundle (stats recalculated once they arrive), backend-specific error messages; `ProgressionScreen`
wording. Companion: raw `/uuid`, `/profile`, bundle `/prices`, `/resale`. New repo SkyProgression-API (TypeScript,
wrangler 4.152.0, TypeScript 7.0.2, GitHub Actions type-check + dry-run deploy). Cloudflare limits checked 2026-10-27
(Workers: 10 ms CPU, 50 subrequests, 100k requests/day, 5 crons; KV: 100k reads, 1k writes/day). Open: deploy (blocked
on `wrangler login`; the owner's `cf` CLI login is not read by wrangler), Hypixel application key, CPU of the cron
steps on Cloudflare (not measurable locally), whether Mojang blocks Worker IPs (the Worker tries the services host then
api.mojang.com), Bazaar figures now top-of-book from Hypixel instead of Coflnet's (same meaning, may differ slightly).

2026-10-27 (Heat, Cold, Maniac Miner, secondary drops): `MethodRates` refactor: private `Core` evaluation (`blockCore`, `gemCore`), `rateAt`
(rate as a function of a Mining Speed factor), `exposure` (Heat/Cold kept share), `Ability.fortunePerBlock`/`maniac()`/`level()`/
`withCooldown`, `speedBoost`/`maniacMiner`/`alternative`, zone overloads of `perHour`/`blocks`/`gems` (default = the method's usual
zone; the Rates tab passes the picked zone, Upgrades and Compare use the usual zone), `GemRate.kept`/`BlockRate.kept`, `Drop`,
`secondary`, `shaftsPerHour`, `loot`, `DROP_IDS`. `MethodComparison`: `DropValue`, `value`, `coinsFor`, `Row.secondary`/
`totalCoinsPerHour`, rank by total. `StatBreakdown.RATE_STATS` adds Heat/Cold Resistance; `effectiveAbility` handles Maniac Miner.
Verification: `./gradlew build :service:installDist` passes (existing tests, none added); scratch runner on the fixture with live
Coflnet quotes for the 49 new IDs (numbers above); GitHub Actions Build succeeded for `8c80f41`. Not checked in game. Sources: wiki
Heat (19 Oct 2026), Heat Resistance, Cold (23 Oct 2026), Maniac Miner (23 Oct 2026), Mining Speed Boost, Hard Stone, Frozen Corpse,
Glacite Mineshafts; official items resource for the loot IDs. Open: Maniac Miner lore wording, Mana limits, Heat share below Y 64,
reset trip time, Crystal prices (AH only?), key crafting cost, Vanguard corpses, Surveyor.

2026-10-27 (Other & progress view): new core `OtherUpgrades` (Entry/Stage/Report, `of`, `stage`, `priceIds`), `StatsReport.petLevel`
(`PetLevel`, `Loadout.petExp`), `Recipes.recipe`/`duration` package access, new price IDs (cereals, SHARD_HIDEONSUN, SHARD_NILBOG,
ESSENCE_FOSSIL, ENCHANTMENT_COMPACT_1). `ProgressionScreen`: `otherView` toggle on Upgrades, `otherCards`, `otherIcon`, `otherTip`;
the worker skips the plan and ranking in that view. Open: Gold Box / Royal Riches / Dwarven Expertise keys, Compact's rate effect,
Relic of Power rarity with full Perfect gems, the pet XP table's official check, Mines of Divan as a zone.

2026-10-27 (GUI redesign): `ProgressionScreen` rewritten around typed card rows (Text, Meter, Split, Legend, Pair,
Entry, Slots, Big, Bar) laid out on content change; tabs HOME 0, GEAR 1, UPGRADES 2, METHODS 3, MORE 4 (F1). Gear merges
Breakdown and Start with (one Tool choice, Refresh reloads both); Upgrades has the old Stats tab as "Rank: <stat> points";
Methods merges Rates and Compare; More holds Type Stats, stat focus, Demo, the old Overview and Other cards. Analysis runs
the plan on Home and Upgrades, the gear audit on More. `Theme` gains `IconTab`, PURPLE and DIM. Verified: `./gradlew build
:service:installDist`; not seen in game. Open: owner's visual check (list in "Resume here").

2026-10-27 (rest of the stat model): `Stat` gains MINING_WISDOM, BREAKING_POWER, PICKAXE_ABILITY_COOLDOWN, HEAT_RESISTANCE and
four powder stats (appended, so Type Stats keeps its first ten); `Stat.percent`. `StatBreakdown.SUPPORTED` = all stats,
`RATE_STATS` (comparison, Start with), `GROUPS`, new perks/attributes/essence/pet rows, `toolExtras`, `consumables`, `ability`,
mapped `temporary`, `mayor`/`MayorPerk`, `effectiveAbility` (rates), `partMaximum`. `Breakdown.total` multiplies cooldown
reductions; `attainable`/`attainableKnown`/`unknownMaxima`. Companion: cached election fetch, `StatsReport.of(..., perks)`.
`ProfileUpgrades` skips essence/attributes without a cost table (ledger only). UI: Stats picker, percent units, attainable meter,
check marks. Open: assumed API keys (listed in API-FIELDS.md), Echo of Wisdom, gear maxima, Cold build-up for Glacite rates.

2026-10-27 (Start with): new `StartWith` (Report/Piece/Scenario/Swap, `of`, `where`, `petItem`), `Loadout.copy`, companion
`/start` (same parameters as `/stats` minus `gear`), `ClientData` start request (cancelled with the others, stale responses
rejected, 45 s cap, old-companion 404 message), `ProgressionScreen` Start with tab (index 4; Rates 5, Compare 6, Other 7; F1
still opens Other) with Price / Held tool / God Potion / Refresh. Open: storage numbering not checked in game; the hint's worn
gear uses the Breakdown God Potion flag while Compare has its own; Type Stats overrides are ignored on this tab by design.

2026-10-27 (coins/hour across methods): new `MethodComparison` (Report/Entry wire format, PriceMode, Row, `row`, `rank`,
`gain`, `of`), companion `/compare` (shared `member` lookup with `/stats`), `ClientData` comparison request (cancelled with
the other requests, stale responses rejected, 45 s cap, 4 MB body cap), `ProgressionScreen` Compare tab (index 5; Other is
6, F1 still opens Other), click-to-select cards, `coinLines` on the Rates tab, `crossMethodLine` on Upgrades cards. The 14
methods are every `MiningMethod` (the brief's "15" counted one extra). Open: Bazaar tax, NPC sale, sell-offer fill volume,
Breakdown zone/tool choices are not used by the comparison.

2026-10-26 (upgrade coverage): `ProfileUpgrades` gains HOTM unlock options (LAYOUT from NEU hotmlayout, BFS `path`, token
count, `Option.unlock/tier/bonus`, `State.unlocked/alternate/omelette`, `omeletteGain`), `skill` and `collections` grinding
chains. `StatBreakdown.blueCheese` applies +1 HOTM level. `GearUpgrades` adds the Blue Cheese module, the 9,999 part
removal fee and `Proposal.returned`. New `Recipes` (+ `RecipeExtractor`, `:core:extractRecipes`, `mining-recipes.json`,
`Profile.collections`) and `Resale` (`StatsReport.resaleQueries/resale`, `Upgrade.resale/resaleNote/net`,
`Step.totalResale`, companion `copyQuote` with cached Coflnet filter options, `Price.sell`). Open: fuel tank removal fee
assumed equal to engines'; PetLevel exact match rarely lists; Core of the Mountain levels as a token source.

2026-10-26 (more upgrade sources): `GearUpgrades` adds recombobulate/coating/gem swaps; Power accessories via
`CatalogExtractor.GEM_ONLY` (stat key order now follows the resource). `ProfileUpgrades` (Option/State chains with
balances and per-chain spending) built in `StatsReport.of`, carried by `RateBasis.profile`; `ProgressionEngine.setBonus`
and full-set candidates. Open: whether Mineral and Glossy pieces combine, Auspicious +1%, Hunting Box shard balance.

2026-10-26 (catalog, reforges, pets): `CatalogExtractor` (`:core:extractCatalog`) rebuilds `mining-items.json` from
the items resource, keeping hand-checked entries, groups and sources; `MiningMethod.breakingPower()`. `NeuExtractor`
(`:core:extractNeu`) writes `mining-reforges.json`; `Reforges` loads it and `GearUpgrades` proposes stone swaps.
`PetUpgrades` + `StatBreakdown.petStats/petTiers`; `StatsReport.petStats/petOptions`; `RateBasis` carries them; the
companion prices "PET_TYPE;RARITY;100" keys from Coflnet's BIN filter. New pet rows: Bee, Glacite Golem, Mole.
Open: Auspicious +1% (additive or multiplicative), whether NEU's Bee Busy Buzz Buzz stacks as assumed, set bonuses,
pet item carry-over on a pet swap.

2026-10-26 (rate ranking and Grotto): `ProgressionEngine.upgrades(..., RateBasis)` values upgrades by `MethodRates`
for MF Gems/Ruby/Topaz and Grotto (efficiency + tool ability from `/stats`); `Upgrade` carries the cooldown factor
and rate before/after. `GearUpgrades` proposes fuel tank swaps (item IDs from the official items resource).
`MethodRates.Gem` now holds its guide efficiency/note; Grotto added; `nextTickSpeed` shown on the Rates tab.
Later the same day (`081b612`): drill engines/modules as proposals; every proposal carries an apply step; new
`ProgressionEngine.plan` and `Item.copy`; plan card on the Upgrades tab. The maxed-drill test now allows trade-off
proposals only. Open/unknown: whether the lore cooldown already includes the fuel tank (affects only the baseline), whether exactly
32,000 reaches 4 ticks on Jasper, the grotto route's share of other gems and Magma Fields, drill engine and omelette
upgrades (not proposed yet).

2026-10-26 (first in-game test fixes): Load stopped at once because every report carries `"error": ""`
(fixed `e2bdbb3`); `/sp` now auto-loads the typed player once per opening (`ca9108a`); `Start-Service.cmd`
is checked out with CRLF (`0bfbcbd`). Cards now use each item's own icon with its SkyBlock ID in
custom_data so texture packs can retexture it (`08eee63`; table from `./gradlew :core:extractIcons`).
Owner confirmed loading works; icon textures with the owner's pack are not yet confirmed.

2026-10-26 (method stats and MF gem rates): extended `StatBreakdown` to all mining stats except Cold
Resistance; new HOTM perks (Gem Lover, Old-School, Blockhead, Metal Head, Efficient Miner, Mole, Mining
Master, Front Loaded with an unobserved key), pet perks (Bal Furnace, Scatha Drill Infusion, Armadillo Long
Claws), attributes (Mossy Box, Devouring Spread, Deep Technique as situational expected value), Rhinestone
Infusion and untrackable Dwarven O's cereals. Added `MethodRates` (MF gems) and Flawless gem price quotes.
Open: unexplained +15 Gemstone / +10 Ore / +10 Block Fortune in game; whether the lore cooldown includes the
fuel tank; MSB affecting all Mining Speed; Front Loaded key; Gold Box / Royal Riches keys.

2026-10-26: reviewed owner-provided Coal, Coal Other Upgrades, Grotto and MF Gems
screenshots and progression checklist. Verified Debotes/Zucchini API access; saved
private ignored fixtures and encrypted local credential. Created this ledger and
AGENTS.md. This increment documents the next work; it does not implement automatic
stats or claim screenshot parity. No new mechanics were validated from the guide yet.

2026-10-26 (later): owner decided to build our own Mining Cat + SkyCompass equivalent.
Reviewed SkyCompass Stat Breakdown for Debotes and its terms (no public API; no
scraping/republishing). Recorded it as a feature spec and comparison evidence in
`docs/references/SKYCOMPASS.md`. Mining Cat developer contacted; no reply yet.
AGENTS.md now requires no AI attribution anywhere and forbids calling SkyCompass's
internal endpoints. Next milestone reframed as our own Stat Breakdown engine.
Documentation-only change; no build was run.

2026-10-26 (Step 1, fixtures): inspected the raw snapshot and recorded where every
mining stat source lives (`docs/references/API-FIELDS.md`). Added test-scope
`FixtureExtractor` plus Gradle task `:core:extractFixture`, which re-encodes the NBT
containers with original tag types and writes a 15 KB fixture: HOTM tree/powder,
mining skill XP, mining shop perks, effects, attribute stacks, magical power, mining
collections, mining pets, armor, equipment, the Titanium Drill and 7 accessories.
Player/profile/item/gem UUIDs, timestamps and skull textures are removed; a test
decodes every container to enforce that. Found and fixed a real bug: current profiles
store HOTM under `skill_tree.experience.mining`, so the parser reported HOTM 0 for
Debotes (now 10; legacy `mining_core.experience` kept as fallback).
Verification: `./gradlew build :service:installDist` succeeded; **33 core tests passed**
(29 previous + 4 fixture tests). No live API refetch or in-game check was performed;
the fixture reflects the snapshot captured earlier on 2026-10-26.
Open questions recorded in API-FIELDS.md: attribute stack→level/effect tables, perk
effects, `polarvoid` key vs displayed "Prismatic", `_total` powder meaning.
Next: Step 2, typed loadout/context and Mining Fortune/Speed source ledger against
the fixture (acceptance in "Resume here").

2026-10-26 (live refetch): refetched `v2/skyblock/profiles` for Debotes with the stored
key (HTTP 200, success, 1 profile, Zucchini selected, Inventory API on). The raw
response now replaces `service-data/debotes-profiles.json`; the earlier copy is kept as
`service-data/debotes-profiles-20261026-1431.json` (both ignored). Regenerating the
fixture changed no values: every extracted field and inventory blob was identical, and the only diff
was number formatting (raw API E-notation vs. the earlier PowerShell-written copy).
Build succeeded, 33 core tests passed. Gear/profile state is therefore current as of
this refetch; refetch again after the owner changes gear.

2026-10-26 (Step 2, stat breakdown): added `Loadout`, `StatContext`, `Breakdown`, `StatBreakdown`
and `pet-levels.json` in core; `Item.lore` (transient) keeps cleaned lore lines. Equipped loadout =
worn armor/equipment, accessory bag, active pet and a selected tool (MANUAL entry when none).
Best owned is a separate per-slot choice. Gear lore line counts once; drill Polarvoid bracket and
part paragraphs count separately (decomposition evidence in CALCULATIONS.md). HOTM (toggles
respected), mining skill, collections, attributes, Eager Miner, pet + Quick Claw are derived;
situational/missing/untrackable sources are listed and excluded. Unknowns stay null and make the
breakdown incomplete (fixture: Auspicious Bonus +1% only). Fortune matches SkyCompass at 1,724.5;
Speed differs by 100.5 (documented, not tuned). Resolved the `polarvoid` vs "Prismatic V" question
in API-FIELDS.md. Tests: one minimal totals test per the owner's request. Not done: companion
`/stats`, UI, other stat groups. Next: owner's in-game stat menu comparison, then Step 4.

2026-10-26 (tick thresholds): owner supplied a community Mining Speed tick-threshold guide (Sept 2026).
Summarized in `docs/references/TICK-THRESHOLDS.md` (rules + implied block strengths for every method).
All 210 tick thresholds in its tables match `MethodRates.ticks`/`nextTickSpeed`; no code change needed. It
also confirms Jasper 4 ticks needs > 32,000. Non-gem rate models (roadmap 2) should take strengths and
the mid-air x5 rule from that file after confirming each strength on the wiki. Documentation only; no rebuild.

2026-10-27 (stats loading speed): owner reported slow stats loading. Causes found in the companion: a price refresh
(~300 Coflnet quotes, 3 at a time) held the same lock as the mayor-perk lookup that every `/stats` reads, so `/stats`
could wait ~14 s at startup and every 15 minutes; each `/stats` also waited for all resale quotes; and an expired
profile (5 min) was refetched while the request waited. Fixes: separate price lock; prices and mayor perks warmed at
startup; expired prices and profiles are served from cache while a background refresh runs (explicit Load/`/report`
still refetches); resale quotes wait at most 1.5 s, with `resalePending` telling the mod to re-ask (up to twice, 2.5 s
apart) and swap in the completed report. `SKYPROGRESSION_PORT` (default 18475) lets a second companion run for
development only. Measured on a temporary copy (port 18476): cold start `/report` 3.2 s, first `/stats` 1.1 s during
the price warm-up (was 13.9 s), next `/stats` 0.1-0.2 s, `/compare` 0.4 s, `/prices` 0.05 s. Build passed. Not yet checked
in game; restart the companion to pick it up.

2026-10-27: owner tested the Heat/Cold, Maniac Miner and secondary-drop rate changes (`8c80f41`) in game: looks good.

# SkyProgression progress

Last updated: 2026-10-26. Current release: **0.3.0**. Status: functional preview;
**not yet Mining Cat parity**. This file is the durable handoff for new chats.

## Resume here

**Direction (2026-10-26): build our own Mining Cat + SkyCompass equivalent.** No
runtime dependency on either tool; their outputs are validation evidence only.

**Next milestone: our own source-attributed Stat Breakdown engine (mining).**
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
2. **Next.** Implement a typed loadout/context and stat breakdown with each contribution's source,
   conditions and confidence (known/unknown/manual). Separate equipped from best owned.
3. Start with Mining Fortune and Mining Speed; compare against the owner's in-game stat
   menu first, then SkyCompass totals (1,724.5 Fortune / 7,150.5 Speed observed).
4. Expose it via our companion HTTP API (e.g. `/stats`), then wire automatic totals into
   the existing screens with a Stats breakdown view; retain optional manual overrides.
5. Document numerical discrepancies instead of tuning constants to match references.

Acceptance: supported stats derive from API inputs with source breakdowns, bonuses are
not double-counted, unsupported sources remain explicit, regression tests cover the
fixture, and loading/calculation stays off the game thread. Full method validation
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
- Owner messaged the Mining Cat developer (Mining Cult Discord) on 2026-10-26 asking
  for comparison permission, rate assumptions and any public API. No reply yet. The
  project does not depend on a reply; proceed with independent research.
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

Latest verification (Step 1 fixtures): build succeeded, **33 core tests passed**.
Verification at 0.3.0: local build succeeded; **29 core tests passed** (26 calculation/
parser tests + 3 HTTP success/size/stalled-body tests). No in-game visual or freeze
reproduction was performed by the agent. Owner confirmed an earlier build worked;
0.3.0's in-game validation is still pending. GitHub Actions run 40330025436 for commit 269f77c was verified completed successfully on 2026-10-26.

Recent milestones:
- `15f2845`: actual gear/modifier upgrades and owned materials (0.2.0).
- `b8bdbfd`: 0.2.0 coverage documentation.
- `c09874c`: native Stitch design and responsiveness changes (0.3.0).
- `269f77c`: design adaptation and freeze review documentation.

## Remaining roadmap, in dependency order

### 1. Accurate automatic stats and loadouts (our Stat Breakdown) — next

- [ ] Stat model covering all groups listed in "Resume here", with current/max per source
  and the counted / situational / missing / trackable / untrackable states.
- [ ] Trackable temporary sources from the API where available (active mayor/perks such
  as Cole's Mining Fiesta, detectable consumables); label God Potion and other
  never-reported effects as untrackable rather than absent.
- [ ] Companion `/stats` endpoint and an in-game breakdown view.

- [ ] Parse/evaluate selected and alternative valid loadouts; don't equip stored items
  implicitly or stack incompatible accessory/pet/gear alternatives.
- [ ] Armor/equipment/tool base stats, reforges, recombobulation, gemstones, enchants,
  powder coatings, scrolls, drill engine/tank/omelette and set bonuses.
- [ ] Mining skill, HOTM/Peak perks and allocated powder, collections, essence shops,
  accessory contributions, permanent consumables and attributes.
- [ ] Pet rarity/level/abilities/items and method/location-dependent bonuses.
- [ ] Model gem/ore/block/dwarven Fortune, Speed, both Spreads, Pristine and Cold
  Resistance with conditions; keep uncheckable sources explicit.
- [ ] Full current/max breakdown with compatible, attainable targets; do not sum
  mutually exclusive maxima or call a base-item bar a maxed account.

### 2. Method-specific yield and rate engine

- [ ] MF Gems end-to-end first; Ruby and Topaz variants next; Grotto comparison next.
- [ ] Coal, Gold, Umber, Shafting, Obsidian, Tungsten, Red Sand, Mycelium, End Stone,
  Cobblestone: correct blocks, gear constraints, bonuses and default efficiency.
- [ ] Speed breakpoints/instant mining, abilities/cooldown uptime, spread availability,
  heat/cold, drops and Pristine effects. Document simplified route assumptions.
- [ ] Automatically sourced coins/hour and verified Coleweight coefficients; correct
  units, price source/time and explicit rate assumptions. Manual scenario overrides.
- [ ] Validate equal gear deltas producing different value in different methods.

### 3. Complete upgrades, costs and ordering

- [ ] Expand rule coverage from the supplied checklist, with verified names/IDs,
  requirements, applicability, delta formulas and acquisition paths.
- [ ] Occupied universal-slot gem swaps (e.g. Topaz to Jade for Coal).
- [ ] Reforges, recombobulation, coatings, attributes, pets/items, drill modules,
  accessories, equipment and permanent bonuses.
- [ ] Buying vs crafting/forging, reusable inputs, owned recipe materials, required
  unlocks and forge time; avoid assuming a guide's AH-vs-craft claim stays true.
- [ ] Replacement resale credit and exact modifier-aware prices where available;
  distinguish cash outlay, net cost and uncertainty; no invented sale values.
- [ ] Explicit prerequisites and dependent chains; paired upgrades and transferable
  pet items; no double spending materials or reusing the same pet item twice.
- [ ] True marginal gain/value ranking and an optional sequential plan with baseline
  recalculation after each step. Keep independent alternatives separately labeled.

### 4. Other Upgrades and general progression

- [ ] Separate purchasable recommendations, uncheckable/manual bonuses, and things
  that require grinding/unlocks (pet levels, Compact, collections, HOTM, etc.).
- [ ] Use early/mid/end guide stages for orientation; personalize actual next steps.
- [ ] Explain prerequisites, where/how to acquire, owned/missing quantities, source
  confidence and distance from a validated method-specific max.

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
Our companion service is our own API: it should grow `/stats`, `/upgrades` and `/rates`
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
- Current normal-user limitation: companion still required. No hosted backend exists.
- Previous downloadable packs live in the creating chat's outputs folder; rebuild from
  repository sources in a new chat rather than relying on those temporary handoff paths.

## Handoff log

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

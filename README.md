# SkyProgression

A simple client-side Hypixel SkyBlock progression mod, starting with mining.
Minecraft **26.1.2**, Fabric Loader **0.20.1+**, Fabric API and Java **25**.

Open with `/skyprogression` or `/sp`.

## Continuing development

Start with [AGENTS.md](AGENTS.md) and [PROGRESS.md](PROGRESS.md). They contain the working
agreement, verified state, reference cases, remaining milestones and next concrete task.

## Development

`./gradlew build` builds the mod. `./gradlew runClient` launches a development client.
Version pins live in `gradle.properties`. The `core` module is independent of Minecraft;
only the client UI needs porting when Minecraft changes. Update the version pins, build,
test and run the client before declaring a newer version compatible.

Inspired by Mining Cat (Mycelium Larper) and SkyCompass (banana eater).
This is an independent implementation, not their code or an official integration.
Not affiliated with or endorsed by Hypixel or Mojang.

## Stitch interface (0.3.0)

The native Stitch-inspired interface has four icon tabs (Home, Gear, Upgrades, Methods) and a More page, with
profile/method selectors always in the top bar, stat bars, item icons, a coins/hour method chart, hover details and a
clearly labeled offline demo. A local companion service
loads Hypixel profiles and Coflnet price quotes. Inventory lookups and price requests
run off the Minecraft render thread and are cached.

**This is not yet full Mining Cat parity.** The item catalog is generated from the official
items resource (every obtainable item with a mining stat). The mod reads individual gear copies,
displayed stats, enchantments, gemstone slots, drill parts, reforges and rarity, and recommends
enchantments, Polarvoid, gemstones and gem type swaps, drill parts, reforge stones (NEU reforge
table), recombobulation, Divan's Powder Coating, better base items and full mining sets, level-100
pets or a Quick Claw, essence perks, attribute shards and HOTM perk levels and unlocks, the Blue Cheese
omelette, and Mining skill/collection tiers (powder, tokens, XP and collections shown as grinding), using owned
materials and balances before buying. Craftable or forgeable items use the cheaper of buying and crafting, and
cards show the net cost after selling what an upgrade replaces when a sale quote exists. The Upgrades tab's Other & progress
view lists bonuses the API can't show (Dwarven O's cereals, Gold Box, Royal Riches, Dwarven Expertise: skip what you
already have), progress to grind ranked by rate gain (HOTM, skill, collections, pet level, Compact, forge-only items such
as the Relic of Power) and the profile's early/mid/end guide stage for orientation. Gem methods rank
upgrades by their Flawless/hour gain, other methods by drops, with a step-by-step plan. Unknown
prices remain unknown. Rates for non-gem methods still need further implementation. Method stats are computed automatically from
the profile using the best gear and tool you own for the method (Gear tab; you can switch to your
equipped gear or a specific tool); Type Stats overrides them.
Read [installation and API setup](docs/SETUP.md) before testing. You do not need an API
key for Demo. Real profile lookup requires your own registered Hypixel application key
in the local companion service. The mod never asks for a key.

Read [calculation scope and sources](docs/CALCULATIONS.md) for formulas, limitations and
the version-upgrade workflow.

```powershell
.\gradlew.bat build :service:installDist
.\Start-Service.cmd
```

Build output: `build/libs/skyprogression-0.3.0.jar`.
Companion: `service/build/install/service/` (keep its `bin` and `lib` directories together).
GitHub Actions also builds the jar and companion on every push.

For development, `./gradlew runClient -Ppreview` opens the offline demo from the title
screen. This flag is off during normal play. Automated unit tests cover calculation,
price, requirement and inventory error cases. In-game testing is left to the project owner.

## Interface and responsiveness (0.3.0)

Dark framed panels, icon tabs, item icons, stat bars split by source, a method bar chart, details in hover
tooltips, searchable selectors and a two-column Type Stats form adapt the supplied Stitch designs (layout in
docs/DESIGN.md). Smaller GUI sizes stack content into one column. Use the mouse wheel, Page Up/Down or footer
arrows to scroll; R reloads, 1-4 switch tabs and F1 opens the More page when not typing.

Catalog/settings loading, HTTP client initialization, profile/price fetches, analysis,
and settings writes run off the game thread. Requests have response-size limits and
whole-response timeouts, and Load becomes Cancel during lookup. Native flat backgrounds
avoid the fullscreen blur pass. Closing/reopening during a lookup still updates results.

See [design adaptation and freeze investigation](docs/DESIGN.md). Minecraft visual and
runtime validation is left to the owner; a passing build does not prove the reported
freeze is reproduced or resolved.

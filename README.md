# SkyProgression

A simple client-side Hypixel SkyBlock progression mod, starting with mining.
Minecraft **26.1.2**, Fabric Loader **0.20.1+**, Fabric API and Java **25**.

Open with `/skyprogression` or `/sp`.

## Development

`./gradlew build` builds the mod. `./gradlew runClient` launches a development client.
Version pins live in `gradle.properties`. The `core` module is independent of Minecraft;
only the client UI needs porting when Minecraft changes. Update the version pins, build,
test and run the client before declaring a newer version compatible.

Inspired by Mining Cat (Mycelium Larper) and SkyCompass (banana eater).
This is an independent implementation, not their code or an official integration.
Not affiliated with or endorsed by Hypixel or Mojang.

## Stitch interface (0.3.0)

The native Stitch-inspired interface has Overview, Upgrades, Stats, Rates and Other tabs, method/stat/profile
selectors, progress bars and a clearly labeled offline demo. A local companion service
loads Hypixel profiles and Coflnet price quotes. Inventory lookups and price requests
run off the Minecraft render thread and are cached.

**This is not yet full Mining Cat parity.** Alongside 57 base gear items, the mod now
reads individual gear copies, displayed stats, enchantments, gemstone slots and rarity.
It recommends Fortune, Efficiency/Silex, Prismatic, Polarvoid and supported gemstone
upgrades, using owned materials before buying and including socket unlock/removal fees.
Overview includes modifier progress bars. Unknown prices remain unknown.
Pets, complete HOTM stat math, reforges, recipe/resale costing and exact method rates
still need further implementation. Effective method stats must still be entered manually.
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

Dark framed panels, gold tabs, cyan stats, gear icons, progress cards, independent cost
comparisons, searchable selectors and a two-column Type Stats form adapt the supplied
Stitch designs. Smaller GUI sizes stack content into one column. Use the mouse wheel,
Page Up/Down or footer arrows to scroll; R reloads and F1 opens help when not typing.

Catalog/settings loading, HTTP client initialization, profile/price fetches, analysis,
and settings writes run off the game thread. Requests have response-size limits and
whole-response timeouts, and Load becomes Cancel during lookup. Native flat backgrounds
avoid the fullscreen blur pass. Closing/reopening during a lookup still updates results.

See [design adaptation and freeze investigation](docs/DESIGN.md). Minecraft visual and
runtime validation is left to the owner; a passing build does not prove the reported
freeze is reproduced or resolved.

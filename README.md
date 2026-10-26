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

## First build (0.1.0)

The interface has Overview, Upgrades, Stats, Rates and Other tabs, method/stat/profile
selectors, progress bars and a clearly labeled offline demo. A local companion service
loads Hypixel profiles and Coflnet price quotes. Inventory lookups and price requests
run off the Minecraft render thread and are cached.

**This is a base-gear MVP, not full Mining Cat parity.** The initial catalog has 57 items.
Overview bars compare base item stats. Item modifiers, pets, complete HOTM stat math,
recipe/resale costing and exact method-specific rates need further implementation.
The UI labels these limits; missing prices and API data are never treated as free or zero.

Read [installation and API setup](docs/SETUP.md) before testing. You do not need an API
key for Demo. Real profile lookup requires your own registered Hypixel application key
in the local companion service. The mod never asks for a key.

Read [calculation scope and sources](docs/CALCULATIONS.md) for formulas, limitations and
the version-upgrade workflow.

```powershell
.\gradlew.bat build :service:installDist
.\Start-Service.cmd
```

Build output: `build/libs/skyprogression-0.1.0.jar`.
Companion: `service/build/install/service/` (keep its `bin` and `lib` directories together).
GitHub Actions also builds the jar and companion on every push.

For development, `./gradlew runClient -Ppreview` opens the offline demo from the title
screen. This flag is off during normal play. Automated unit tests cover calculation,
price, requirement and inventory error cases. In-game testing is left to the project owner.

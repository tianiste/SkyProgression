# SkyProgression

A client-side Hypixel SkyBlock progression mod, starting with mining. It shows every mining stat itemized by source,
your rate for each method, which gear you already own to use, and which upgrades raise your rate most per coin.
It advises only: nothing buys, equips or mines for you.

Minecraft **26.1.2**, Fabric Loader **0.20.1+**, Fabric API and Java **25**.

## Install

1. Download `skyprogression-<version>.jar` from the [latest release](https://github.com/tianiste/SkyProgression/releases/latest)
   (not the companion zip, which is optional).
2. Put it in your Fabric 26.1.2 instance's `mods` folder next to Fabric API.
3. In SkyBlock, enable **SkyBlock Menu > Settings > API Settings > Inventory API** (and the skill/collection options).
4. Open the screen with `/sp` or `/skyprogression`. Your profile loads automatically.

No API key and no extra program are needed: profiles and prices come from the hosted SkyProgression API at
`https://api.skyprogression.dev` ([source](https://github.com/tianiste/SkyProgression-API)), and every calculation runs
inside the mod. **Demo** shows the interface with clearly labelled sample data.

More detail, the tabs and troubleshooting: [docs/SETUP.md](docs/SETUP.md). What changed: [CHANGELOG.md](CHANGELOG.md).

## What it does

- **Home:** your stats for the method against the best you could reach, the current rate with coins per hour, the best
  next upgrade and every method's coins per hour.
- **Gear:** the best loadout you own for the method, where each piece is stored, and every stat split by source
  (gear, HOTM, skill, pet and more), current vs attainable maximum.
- **Upgrades:** a step-by-step plan and every upgrade ranked by rate gain, with costs (buy or craft, owned materials,
  resale of what it replaces); an **Other & progress** view for bonuses the API cannot show and progress to grind.
- **Methods:** coins per hour across methods (instant-sell, sell offer or NPC prices) and the selected method's rate
  model with its assumptions.

Rates are our own models, described with their sources in [docs/CALCULATIONS.md](docs/CALCULATIONS.md). Unknown
prices stay unknown and are never treated as free. This is not yet full parity with Mining Cat or SkyCompass.

## Own key or development: the local companion

The companion is a small local service that answers the same endpoints as the hosted API with your own Hypixel key.
It is optional. Download the `service-<version>.zip` from the release, start `Start-Service.cmd`, and set
`"backend": "http://localhost:18475"` in `config/skyprogression.json` ([details](docs/SETUP.md#local-companion)).

## Development

Start with [AGENTS.md](AGENTS.md) and [PROGRESS.md](PROGRESS.md) (working agreement, verified state, next task).

```powershell
.\gradlew.bat build :service:installDist
```

Build output: `build/libs/skyprogression-<version>.jar`; companion in `service/build/install/service/` (keep its `bin`
and `lib` together). `./gradlew runClient -Ppreview` opens the offline demo from the title screen. Version pins live in
`gradle.properties`; the `core` module is independent of Minecraft. GitHub Actions builds every push and publishes a
GitHub Release with the jar and companion zip for each `v*` tag.

Interface notes and the freeze investigation: [docs/DESIGN.md](docs/DESIGN.md). Network, disk and calculation work runs
off the render thread with bounded response sizes and timeouts.

Inspired by Mining Cat (Mycelium Larper) and SkyCompass (banana eater). This is an independent implementation, not their
code or an official integration. Not affiliated with or endorsed by Hypixel or Mojang.

# Contributing to SkyProgression

Thanks for helping. Bug reports, corrections to mining mechanics and pull requests are all welcome.

## Reporting a bug

Open an [issue](https://github.com/tianiste/SkyProgression/issues) with:

- the mod version (Home tab or `/sp`) and your Minecraft and Fabric Loader versions;
- what you did, what you expected and what happened;
- the method and tab involved, and a screenshot if it helps;
- for crashes, `logs/latest.log` (or the crash report).

**Never post an API key**, a `.env` file or the contents of `config/skyprogression.json` if you put a private backend
URL in it.

## Correcting a mechanic or a number

SkyProgression aims to be right, not to match another tool. If a stat, rate or cost looks wrong:

- say which value is off, what you expected, and where that comes from: the Hypixel SkyBlock Wiki, official Hypixel
  data, or an in-game screenshot (the stat menu or item lore) together with the profile it belongs to;
- numbers from other tools are useful as a hint, but a fix needs a primary source.

## Building

You need **Java 25**.

```bash
./gradlew build                  # mod jar: build/libs/skyprogression-<version>.jar
./gradlew :service:installDist   # optional local companion: service/build/install/service/
./gradlew runClient -Ppreview    # dev client with the offline demo
```

On Windows use `.\gradlew.bat`. Version pins live in `gradle.properties`.

## Project layout

| Path | What it is |
| --- | --- |
| `core/` | The engine: profile parsing, stat breakdown, rate models, upgrades. Plain Java, no Minecraft classes. |
| `src/main/` | The Fabric mod: screens, theme, background loading, config. |
| `service/` | The optional local companion and the profile relay for the hosted API. |
| `deploy/relay/` | Deploy kit for the fixed-IP profile relay. |
| `docs/` | Setup, calculation rules with sources, interface notes and research references. |

The hosted API (a Cloudflare Worker) lives in [SkyProgression-API](https://github.com/tianiste/SkyProgression-API).

## Guidelines for pull requests

- **Keep the game smooth.** Network, disk and heavy calculations run on background threads, never on the render
  thread, with bounded sizes and timeouts.
- **Keep the engine separate.** Game-independent logic belongs in `core/`; only UI and Minecraft glue in `src/main/`.
- **Cite sources.** Every new rule or constant gets a source (wiki page, official resource) in
  [docs/CALCULATIONS.md](docs/CALCULATIONS.md) or a comment next to it.
- **Unknown is not zero.** Keep unknown, unavailable, manual and zero values distinct, and never treat an unknown price
  as free.
- **No automation.** The mod advises; it must never buy, equip, mine or click for the player.
- **No secrets in Git.** Keys belong in environment variables or the Worker's secrets, never in code, tests or docs.
- **Match the surrounding code.** Keep changes focused, follow the existing style and the interface notes in
  [docs/DESIGN.md](docs/DESIGN.md), and make sure `./gradlew build` passes.

Regenerating bundled data (reforges, recipes, the item catalog) uses the Gradle tasks in `core/build.gradle`
(`extractNeu`, `extractRecipes`, `extractCatalog`); see the comments in the generated files.

## Releases

Releases are published automatically when `mod_version` in `gradle.properties` changes on `main`:

1. In your pull request, raise `mod_version` (for example `0.4.0` to `0.4.1`) and add a `## 0.4.1 - <date>` section to
   [CHANGELOG.md](CHANGELOG.md). That section becomes the release notes; without it the release fails.
2. When the pull request is merged, the Release workflow builds the jar, tags `v0.4.1` and publishes a GitHub Release
   (and the Modrinth version, once that is configured).

Merges that leave `mod_version` unchanged only build. The jar is named after the mod version and the Minecraft version
it targets (`minecraft_version` in `gradle.properties`), e.g. `skyprogression-0.4.1+26.1.2.jar`. Do not push `v*` tags
by hand.

By contributing you agree that your contribution is licensed under the [MIT License](LICENSE).

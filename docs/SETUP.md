# Install SkyProgression

## Mod

1. Use Minecraft **26.1.1**, **26.1.2** or **26.2**, **Fabric Loader 0.20.1 or newer**, and Java **25**.
2. Install **Fabric API** for that Minecraft version. Keep your other existing mods.
3. Download the jar for your Minecraft version from the [latest release](https://github.com/tianiste/SkyProgression/releases/latest)
   (`skyprogression-<version>+26.1.1-26.1.2.jar` for 26.1.1 and 26.1.2, `skyprogression-<version>+26.2.jar` for 26.2)
   and copy it into that instance's `mods` folder. You do not need the `service-<version>.zip` (the optional companion).
4. In SkyBlock, enable **SkyBlock Menu > Settings > API Settings > Inventory API** (and the skill/collection API
   options if available).
5. Enter `/sp` or `/skyprogression`. Your profile loads automatically; use the name field and **Load** for another
   player, the profile selector for another profile, and **Demo** for clearly labelled sample data.

That is all: no API key and no extra program. All calculations run in the mod; the hosted SkyProgression API at
`https://api.skyprogression.dev` only supplies the raw profile and prices
([source](https://github.com/tianiste/SkyProgression-API)).

## Choosing the backend

The backend is set in `config/skyprogression.json`, created on first start:

```json
{ "backend": "" }
```

- Empty (default): the hosted SkyProgression API at `https://api.skyprogression.dev` (`ClientData.HOSTED_BACKEND`).
- `"https://..."`: another hosted SkyProgression API (see [Hosted API](#hosted-api-owner)).
- `"http://localhost:18475"`: the local companion below (for development, or with your own key). A companion
  started with `SKYPROGRESSION_PORT` needs that port here too.

Names are resolved in the mod: your own name from the game session, other names directly at Mojang, with the backend's
`/uuid` as a fallback. Restart Minecraft after editing the file. The Home card shows which backend is used before you
load.

### Local companion

Optional, for developers and players who want to use their own Hypixel key. The companion fetches a profile when the mod asks. The API key stays in the companion process and is never sent to
Minecraft or committed to Git.

1. Open <https://developer.hypixel.net/> yourself and sign in.
2. Register a project for SkyProgression. Follow the dashboard's current process for a
   development key while testing, and an appropriate application key for ongoing use.
   Development keys expire. Read <https://developer.hypixel.net/policies/>.
3. In SkyBlock, enable **SkyBlock Menu > Settings > API Settings > Inventory API**.
   Enable the relevant skill/collection API options as well if available in your settings.
4. Extract the release's `service-<version>.zip` (or a development build), then double-click **`Start-Service.cmd`**.
   It starts Java directly, does not require PowerShell script permissions, and keeps
   errors visible. Alternatively, where scripts are permitted, run `Start-Service.ps1`
   in an existing PowerShell window.
   It prompts for the key using hidden input and keeps it only in memory for this service process.
   You can alternatively set `HYPIXEL_API_KEY` yourself and run the service launcher.
5. Keep the companion running. In Minecraft, enter your username and press **Load**.
   The selected SkyBlock profile loads by default. Use the profile selector for another.

The packaged service contains its libraries, but needs Java 25. The helper recognizes
Prism Launcher's standard Java installation; otherwise set `JAVA_HOME` to your Java 25 folder.

If PowerShell blocks running a script under your machine's policy, you can run the
packaged `service\bin\service.bat` after setting the environment variable yourself.
Do not put your key into Minecraft chat, a tracked file, a screenshot, or a GitHub issue.

The service is local-only at `http://localhost:18475`. Do not expose it to the internet.
It answers the same endpoints as the hosted API (`/health`, `/uuid`, `/profile`, `/prices`, `/resale`); an older
companion that still has `/stats` makes the mod say "The companion is older than the mod. Restart it."

## Hosted API (owner)

The hosted backend lives in <https://github.com/tianiste/SkyProgression-API> (its README lists endpoints, caching and
limits). It runs on Cloudflare's free Workers plan. One-time setup, done by the owner:

1. Create a Cloudflare account (free plan) and install Node.js 22 or newer.
2. In a clone of SkyProgression-API: `npm ci`, then `npx wrangler login` (opens the browser).
3. `npx wrangler deploy`. Wrangler creates the `PRICES` KV namespace on the first deploy and prints the
   `https://skyprogression-api.<your-subdomain>.workers.dev` URL. The cron trigger fills the price bundle within
   about 15 minutes; check `/health`.
4. Apply for a **Hypixel application key** on <https://developer.hypixel.net/>: personal keys are not meant for
   apps other players use. Store it with `npx wrangler secret put HYPIXEL_API_KEY` (never in a file or Git).
   Until then `/profile` answers 503 and prices still work.
5. Put the Worker URL into `ClientData.HOSTED_BACKEND` and rebuild the mod, or set it in
   `config/skyprogression.json` for testing. (Done for `skyprogression-api.tian-istenic34.workers.dev`, deployed
   2026-10-27.) Wrangler writes the KV namespace ID into the local `wrangler.jsonc`; keep it out of Git.
6. If the application key is locked to an IP allowlist (it is for the owner's key; Workers have no fixed outbound IP),
   run the profile relay on a fixed-IP server instead of storing the key in the Worker: [deploy/relay](../deploy/relay/README.md)
   (Oracle Always Free VM, reserved IP, `relay.skyprogression.dev`, Caddy, systemd), then `RELAY_URL` and `RELAY_SECRET`
   on the Worker.
7. Optional: a custom domain for the Worker (Cloudflare dashboard > the Worker > Domains & Routes) makes the
   Cache API effective for profiles; on `workers.dev` only in-memory caching helps.

## Using the tabs

The top bar always holds your username (Load), the profile and the method. Hover bars, rows and card titles for
details; lines starting with + expand on click.

- **Home:** your stats for the method as bars (current vs the best attainable with this gear; hover for the top
  sources), the current rate with coins per hour, the best next upgrade (first step of the plan) and every method's
  coins per hour as a bar chart (click a method to select it).
- **Gear:** which of your own gear to use before buying anything. The loadout is a grid of item slots (the tool
  outlined gold, pieces to fetch cyan); click one for where it is stored and its swap. The status line says whether
  you already wear the best you own, otherwise how many swaps and the rate change; each swap is listed with its gain.
  Next to it, one card per stat with a bar split by source (Gear, HOTM, Skill, Pet, other), the top sources (check
  mark when maxed) and one expandable line for situational, temporary, missing and not-in-API sources. Controls:
  **Stats** group, **Gear** best owned/equipped, **Tool** (the tool you hold; the API does not show it), **Zone**, **God
  Potion**, **Price**, **Refresh**.
- **Upgrades:** the step-by-step plan as numbered cards, then every upgrade ranked by rate gain (or drops), each with
  its icon, gain and cost; hover for replaced item, net cost after resale, before/after rate and its value in the best
  method. **Rank** switches to the cheapest points of one stat (pick it with **Stat**). Mining stats come from the
  loaded profile automatically; **Type Stats** overrides any of them (blank stays automatic or unknown).
- **Methods:** the coins per hour chart (**Price** cycles instant-sell / sell offer / NPC sell, **God Potion**, **Pick
  method**, **Refresh**), then the selected method's rate: items per hour or per its window, coins per hour at each
  price, efficiency, ticks, the next speed threshold, tool and zone, with assumptions on an expandable line. Also
  "Same change, different value" (+50 Fortune / +100 Speed per method) and the manual Type Stats scenario.
- **Session tracker** (top of Methods): select the method, then just mine; the session starts on the first gain (or press
  **Session: Start**) and pauses after 60 s without one. It counts inventory pickups, the [Sacks] chat summaries and Pristine
  procs, and shows measured items / hour and coins / hour next to the model, with active time and uptime. It keeps running
  with /sp closed; **HUD: On** shows a one-line summary in the corner. **Session: Stop**, then **Save session** writes it to
  `config/skyprogression-sessions.json`; **Reset session** clears it. Don't move mined items from your inventory into sacks
  mid-session (they would count twice).
- **More** (gear button or F1): Type Stats, stat focus, Demo, HOTM and skill progress, powder, the gear audit for the
  focused stat, profile notes and help.

Use the mouse wheel, Page Up/Down or footer arrows to scroll results. Selectors have search fields. Nothing purchases items, equips gear, or mines for you.

## Current limits

This is not yet a full replacement for Mining Cat or SkyCompass. Stats are calculated from what the API shows; sources
it never shows (some consumables, weather, held-item state) are listed as not-in-API and not counted, and Type Stats
overrides any value. Do not interpret a completed bar as a fully maxed account.

Prices are purchase quotes, not exact matching auction listings. Crafting and forging are compared
one recipe level deep; net cost subtracts a replaced item's sale quote (lowest matching BIN for your copy's
modifiers, or Coflnet's sell value) only when one exists. Unknown prices stay unknown. HOTM and Prismatic's Enchanting
requirement are checked; other requirements and soulbinding need manual checks.
Ironman/Bingo missing materials are flagged for gathering.
Automatic rates are our own model, not Mining Cat's formulas: tick thresholds, Mining Speed Boost or
Maniac Miner, cooldowns, Heat and Cold, and secondary drops (Hard Stone on Coal, other grotto gems,
mineshaft corpse loot) are modelled as described in CALCULATIONS.md; fuel costs, taxes and route density
are not. The manual scenario ignores all of these: supply measured active blocks/sec to account for speed. Spread assumes enough extra
blocks. Gems use rough-equivalent units; match the price and Coleweight units to them.
Shafting estimates Glacite, not an entire mixed mineshaft. MF Gems uses the gem inputs you enter.
No automatic Coleweight coefficient is invented (none could be verified; see CALCULATIONS.md): enter one or the
result stays unavailable. The Home rate tile and the Methods tab use the rate models in CALCULATIONS.md.

## Troubleshooting

- **Launcher closes immediately:** use `Start-Service.cmd` rather than right-clicking the
  PowerShell script. It runs Java directly and startup errors stay visible.
  Windows PowerShell may block scripts before the PowerShell launcher can run.
  No execution-policy change is needed for the CMD launcher. Do not run files inside the ZIP.
- **Could not reach the local companion:** start the service on this computer; check its terminal for errors.
- **Could not reach the SkyProgression API:** check your connection; the message names the backend in use.
- **The server has no Hypixel API key configured / key rejected:** a problem on the hosted API; try again later or use the companion with your own key.
- **Key invalid/expired:** restart with a current key. Never send the key with a bug report.
- **Inventory unavailable:** enable Inventory API and wait out the five-minute profile cache.
- **Unknown price:** Coflnet has no valid quote or could not be reached. Unknown is never free.
- **Rate limited:** wait a minute. Profiles cache for five minutes; prices for about fifteen.
- **Prices are still loading:** a freshly deployed API fills its price bundle within about 15 minutes.
- **Wrong numbers:** compare the stated base-item scope with the in-game effective total.
- **Other Minecraft versions:** each jar declares only the versions in its name (26.1.1 and 26.1.2, or 26.2). Other
  versions need a new build target and testing first.

## Updating from an earlier build

Replace the mod jar with the new one (remove the old jar). From 0.4.0 the hosted API is the default, so you can stop
using the companion. If you keep it, close the old one and start the one from the same release: the mod calculates
everything itself and needs the companion's raw endpoints.

## Your first in-game test

1. `/sp` opens, Escape closes, `/skyprogression` opens again.
2. Demo shows a visible DEMO label, progress bars and paginated upgrades.
3. Method/stat/profile selectors and Type Stats work at your normal GUI scale.
4. Save rate inputs, close and reopen: inputs remain for that profile/method.
5. Load your real profile through your backend: no demo prices should remain.
6. Compare known item ownership and HOTM with your account. Check each tab before buying anything.

Builds and unit tests are automated. In-game validation is deliberately left to you.

## Check the redesigned interface

- Open /sp at your usual GUI scale, then try a larger GUI scale. Panels should stack
  on narrow windows; content stays inside its scrollable area.
- Demo > More shows the gear audit with item icons; Upgrades shows ranked icon cards
  with tooltips. Selectors filter as you type.
- Type Stats accepts comma-separated numbers; Apply saves in the background. Cancel
  discards edits that have not been applied.
- Load with the backend unreachable: the screen should remain interactive and report
  the connection error. Cancel interrupts a pending lookup. Closing and reopening
  during a lookup should still display the completed result.
- If Minecraft freezes again, retain latest.log and a Java thread dump captured during
  the freeze. Report which action triggered it, GUI scale and other installed mods.
  Do not include API keys. This release removes identified blocking paths, but the
  original freeze has not been reproduced in a running Minecraft instance.

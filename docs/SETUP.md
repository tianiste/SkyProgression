# Install SkyProgression 0.1.0

## Mod

1. Use Minecraft **26.1.2**, **Fabric Loader 0.20.1 or newer**, and Java **25**.
2. Install **Fabric API for 26.1.2**. Keep your other existing mods.
3. Copy `skyprogression-0.1.0.jar` into that instance's `mods` folder.
   Do not install the `-sources.jar` file.
4. Start Minecraft and enter `/sp` or `/skyprogression` while in a world/server.
5. Select **Demo** to try the interface immediately. Demo inventory/prices are fabricated and labeled.

## Enable real profile lookups

The local companion application fetches a profile when you press Load. The API key stays
in the companion's process environment and is never sent to Minecraft or committed to Git.

1. Open <https://developer.hypixel.net/> yourself and sign in.
2. Register a project for SkyProgression. Follow the dashboard's current process for a
   development key while testing, and an appropriate application key for ongoing use.
   Development keys expire. Read <https://developer.hypixel.net/policies/>.
3. In SkyBlock, enable **SkyBlock Menu > Settings > API Settings > Inventory API**.
   Enable the relevant skill/collection API options as well if available in your settings.
4. Run `Start-Service.ps1` in PowerShell from the extracted test pack or repository.
   It prompts for the key using hidden input and keeps it only for this service process.
   You can alternatively set `HYPIXEL_API_KEY` yourself and run the service launcher.
5. Keep the companion running. In Minecraft, enter your username and press **Load**.
   The selected SkyBlock profile loads by default. Use the profile selector for another.

The packaged service contains its libraries, but needs Java 25. The helper recognizes
Prism Launcher's standard Java installation; otherwise set `JAVA_HOME` to your Java 25 folder.

If PowerShell blocks running a script under your machine's policy, you can run the
packaged `service\bin\service.bat` after setting the environment variable yourself.
Do not put your key into Minecraft chat, a tracked file, a screenshot, or a GitHub issue.

The service is local-only at `http://localhost:18475`. Do not expose it to the internet.
Public distribution needs an appropriately registered, hosted application backend;
this development companion is designed for the project owner's machine.

## Using the tabs

- **Overview:** HOTM, mining level and collection progress, plus the selected stat's best
  owned base item per slot, catalog target and remaining points.
- **Upgrades:** choose a method, then enter effective in-game stats in **Type Stats**.
  Ranks base-item alternatives by purchase cost per estimated 1% more drops.
- **Stats:** ranks base-item alternatives by purchase cost per point of the selected stat.
- **Rates:** enter effective stats, active blocks per second, base drops per block, efficiency,
  sale price per item and optionally Coleweight per item. Inputs are saved per profile/method.
- **Other:** known profile milestones plus a checklist of bonuses not modeled automatically.

Use the arrows to page through results. Nothing purchases items, equips gear, or mines for you.

## Important first-version limits

This is a working **base-gear MVP**, not yet a full replacement for Mining Cat or SkyCompass.
The 57-item catalog covers selected armor, equipment, tools, accessories and lanterns.
The method selector includes all requested methods, but specialized loadouts and bonuses
are not comprehensive. Do not interpret a completed bar as a fully maxed account.

Reforges, gemstone sockets/quality, enchants, drill modules, pet bonuses, HOTM perks,
attributes and temporary buffs are not automatically calculated. Enter effective totals
for estimates. Current/max bars compare catalog **base** item stats only. Owned gear can
be in storage; the mod does not claim you have it equipped or that it is usable yet.

Upgrade costs use Coflnet purchase quotes, not a matching item's exact modifiers.
Owned items are excluded from purchases. Recipe materials and resale credits are not
costed yet. HOTM is checked; other requirements and soulbinding require manual checks.
Ironman/Bingo market purchases are flagged rather than suggested as available.

Rates are scenario estimates, not Mining Cat's exact formulas. Speed thresholds, mining
abilities, cooldowns, fuel costs, taxes, route density and mineshaft loot are not modeled.
Supply measured active blocks/sec to account for speed. Spread assumes enough extra
blocks. Gems use rough-equivalent units; match the price and Coleweight units to them.
Shafting estimates Glacite, not an entire mixed mineshaft. MF Gems uses the gem inputs you enter.
No automatic Coleweight coefficient is invented: enter one or the result stays unavailable.

## Troubleshooting

- **Companion unavailable:** start the service on this computer; check its terminal for errors.
- **Key invalid/expired:** restart with a current key. Never send the key with a bug report.
- **Inventory unavailable:** enable Inventory API and wait out the five-minute profile cache.
- **Unknown price:** Coflnet has no valid quote or could not be reached. Unknown is never free.
- **Rate limited:** wait a few minutes. Profiles cache for five minutes; prices for fifteen.
- **Wrong numbers:** compare the stated base-item scope with the in-game effective total.
- **Newer Minecraft:** this jar intentionally declares 26.1.2 only. Rebuild and test before upgrading.

## Your first in-game test

1. `/sp` opens, Escape closes, `/skyprogression` opens again.
2. Demo shows a visible DEMO label, progress bars and paginated upgrades.
3. Method/stat/profile selectors and Type Stats work at your normal GUI scale.
4. Save rate inputs, close and reopen: inputs remain for that profile/method.
5. Start the service and load your real profile: no demo prices should remain.
6. Compare known item ownership and HOTM with your account. Check each tab before buying anything.

Builds and unit tests are automated. In-game validation is deliberately left to you.

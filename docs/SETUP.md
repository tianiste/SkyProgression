# Install SkyProgression 0.3.0

## Mod

1. Use Minecraft **26.1.2**, **Fabric Loader 0.20.1 or newer**, and Java **25**.
2. Install **Fabric API for 26.1.2**. Keep your other existing mods.
3. Copy `skyprogression-0.3.0.jar` into that instance's `mods` folder.
   Do not install the `-sources.jar` file.
4. Start Minecraft and enter `/sp` or `/skyprogression` while in a world/server.
5. Select **Demo** to try the interface immediately. Demo inventory/prices are fabricated and labeled.

## Enable real profile lookups

The local companion application fetches a profile when you press Load. The API key stays
in the companion process and is never sent to Minecraft or committed to Git.

1. Open <https://developer.hypixel.net/> yourself and sign in.
2. Register a project for SkyProgression. Follow the dashboard's current process for a
   development key while testing, and an appropriate application key for ongoing use.
   Development keys expire. Read <https://developer.hypixel.net/policies/>.
3. In SkyBlock, enable **SkyBlock Menu > Settings > API Settings > Inventory API**.
   Enable the relevant skill/collection API options as well if available in your settings.
4. Extract the entire test pack, then double-click **`Start-Service.cmd`**.
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
Public distribution needs an appropriately registered, hosted application backend;
this development companion is designed for the project owner's machine.

## Using the tabs

- **Overview:** HOTM, mining level and collection progress, plus the selected stat's best
  owned base item per slot, catalog target and remaining points.
- **Upgrades:** choose a method. Mining Fortune/Speed, the Gemstone/Ore/Dwarven Metal/Block Fortunes, both
  Spreads and Pristine come from the loaded profile automatically; override any of them (or enter Cold
  Resistance) in **Type Stats**. Blank fields stay automatic or unknown.
  Ranks supported gear upgrades and base-item alternatives by purchase cost per estimated 1% more drops.
- **Stats:** ranks supported gear upgrades and base-item alternatives by purchase cost per point of the selected stat.
- **Breakdown:** automatic Mining Fortune and Mining Speed itemized by source (counted, situational,
  missing, temporary, not in the API). By default it uses the best gear and tool you own for the method,
  listed with where each piece is stored; **Gear** switches to what you wear now, and **Tool** picks a
  specific tool. Choose the zone; God Potion is assumed on and can be switched off. Refresh retries.
- **Rates:** the automatic rate for the method (ticks, ability uptime, items per hour or per the method's window)
  with coins per hour at both Coflnet figures (instant-sell and sell offer, labelled, with the quote time). The
  manual scenario next to it takes active blocks per second, base drops, efficiency, a sale price and optionally
  your own Coleweight per item. Inputs are saved per profile/method.
- **Compare:** every method ranked by coins per hour, each with its own best owned gear and tool in its usual zone
  (that method's Type Stats values and efficiency apply). **Price** cycles instant-sell / sell offer / NPC sell, **God Potion**
  toggles the assumption, **Pick method** or a click on a card selects the method. A missing price stays unknown
  and sorts last. The Upgrades tab then also shows each upgrade's coins per hour here and in the best method.
- **Other:** known profile milestones plus a checklist of bonuses not modeled automatically.

Use the mouse wheel, Page Up/Down or footer arrows to scroll results. Selectors have search fields. Nothing purchases items, equips gear, or mines for you.

## Current limits

This is a working **gear-upgrade preview**, not yet a full replacement for Mining Cat or SkyCompass.
The 57-item catalog covers selected armor, equipment, tools, accessories and lanterns.
The method selector includes all requested methods, but specialized loadouts and bonuses
are not comprehensive. Do not interpret a completed bar as a fully maxed account.

Individual gear copies retain their enchantments, rarity, socket contents and displayed
item stats. Supported upgrades include Fortune IV, Efficiency/Silex, Prismatic,
Polarvoid Books, and Flawless/Perfect Jade, Amber and Topaz. Gemstone costs include
known unlock and removal fees. Owned materials reduce the amount to buy.
Recommendations are independent alternatives, not a cumulative shopping list.

Reforge upgrades, drill modules, pet bonuses, complete HOTM math, attributes and temporary
buffs are not modeled automatically. Enter effective totals for method estimates.
Displayed item lore is not your effective total. Base-item bars compare catalog base
stats; modifier bars show supported upgrades separately. Stored gear is not necessarily
equipped. New-item comparisons do not assume modifiers transfer from your old item.

Prices are purchase quotes, not exact matching auction listings. Crafting and forging are compared
one recipe level deep; net cost subtracts a replaced item's sale quote (lowest matching BIN for your copy's
modifiers, or Coflnet's sell value) only when one exists. Unknown prices stay unknown. HOTM and Prismatic's Enchanting
requirement are checked; other requirements and soulbinding need manual checks.
Ironman/Bingo missing materials are flagged for gathering.
Rates are scenario estimates, not Mining Cat's exact formulas. Speed thresholds, mining
abilities, cooldowns, fuel costs, taxes, route density and mineshaft loot are not modeled.
Supply measured active blocks/sec to account for speed. Spread assumes enough extra
blocks. Gems use rough-equivalent units; match the price and Coleweight units to them.
Shafting estimates Glacite, not an entire mixed mineshaft. MF Gems uses the gem inputs you enter.
No automatic Coleweight coefficient is invented (none could be verified; see CALCULATIONS.md): enter one or the
result stays unavailable. The automatic rate cards and the Compare tab use the rate models in CALCULATIONS.md.

## Troubleshooting

- **Launcher closes immediately:** use `Start-Service.cmd` rather than right-clicking the
  PowerShell script. It runs Java directly and startup errors stay visible.
  Windows PowerShell may block scripts before the PowerShell launcher can run.
  No execution-policy change is needed for the CMD launcher. Do not run files inside the ZIP.
- **Companion unavailable:** start the service on this computer; check its terminal for errors.
- **Key invalid/expired:** restart with a current key. Never send the key with a bug report.
- **Inventory unavailable:** enable Inventory API and wait out the five-minute profile cache.
- **Unknown price:** Coflnet has no valid quote or could not be reached. Unknown is never free.
- **Rate limited:** wait a few minutes. Profiles cache for five minutes; prices for fifteen.
- **Wrong numbers:** compare the stated base-item scope with the in-game effective total.
- **Newer Minecraft:** this jar intentionally declares 26.1.2 only. Rebuild and test before upgrading.

## Updating from an earlier build

Close the old companion and replace both the mod jar and the companion from this pack.
Remove the old mod jar from the instance, then start the new companion.

## Your first in-game test

1. `/sp` opens, Escape closes, `/skyprogression` opens again.
2. Demo shows a visible DEMO label, progress bars and paginated upgrades.
3. Method/stat/profile selectors and Type Stats work at your normal GUI scale.
4. Save rate inputs, close and reopen: inputs remain for that profile/method.
5. Start the service and load your real profile: no demo prices should remain.
6. Compare known item ownership and HOTM with your account. Check each tab before buying anything.

Builds and unit tests are automated. In-game validation is deliberately left to you.

## Check the redesigned interface

- Open /sp at your usual GUI scale, then try a larger GUI scale. Panels should stack
  on narrow windows; content stays inside its scrollable area.
- Demo > Overview shows gold/cyan progress cards and gear icons. Stats shows ranked
  cards and a cost comparison. Selectors filter as you type.
- Type Stats accepts comma-separated numbers; Apply saves in the background. Cancel
  discards edits that have not been applied.
- Load with the companion stopped: the screen should remain interactive and report
  the connection error. Cancel interrupts a pending lookup. Closing and reopening
  during a lookup should still display the completed result.
- If Minecraft freezes again, retain latest.log and a Java thread dump captured during
  the freeze. Report which action triggered it, GUI scale and other installed mods.
  Do not include API keys. This release removes identified blocking paths, but the
  original freeze has not been reproduced in a running Minecraft instance.

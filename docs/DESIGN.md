# Interface layout (2026-10-27 redesign)

The main screen has four icon tabs and a More page. Owner decisions (2026-10-27): fewer tabs, item icons where an
item is meant, stat bars, a method bar chart, one short line per card with details in hover tooltips or expandable
lines, and the existing flat palette (no blur).

- **Top bar:** "SkyProgression", the Home / Gear / Upgrades / Methods tabs (icon plus label; icon only when narrow),
  then username + Load, profile picker and method picker, the settings button (More page) and X. Below 860 px the
  username, profile and method controls move to a second row. Tab-specific controls sit in one compact row under it
  and wrap into more rows when narrow. Keys: R reload, F1 More page, 1-4 tabs, PgUp/PgDn scroll, ESC close.
- **Home:** "Your stats for <method>" with one bar per `MiningMethod.relevantStats` stat (current vs
  `Ledger.attainable`, or `attainableKnown` when unknown; hover lists the top sources; click opens Gear); a "Rate now"
  tile (big number, unit, coins/hour line); a "Best next upgrade" tile (first step of the plan); and the
  "Coins / hour by method" bar chart (best method cyan, selected gold, click selects the method).
- **Gear** (old Start with + Breakdown): "Use this loadout" as an item-slot grid (tool outlined gold, pieces to fetch
  cyan, worn gray), a status line ("Wearing the best you own" or the swap count and rate change), the clicked slot's
  detail (name, storage, source, swap) and the swaps as icon rows. A source line under the grid ("Live: N of M from the
  game | API: 4m old", green when any piece is live) and a "Source:" line in each slot tooltip say where each piece came
  from (Live, Live seen N ago for menu reads, or API with its age); the labels refresh every 10 seconds. The Tool button
  reads "Held, <tool>" when the live held tool is used. "Stats use" holds the evaluated gear, notes and the
  best-method hint in tooltips. Each stat card has one bar split by source category (Gear gold, HOTM cyan, Skill
  green, Pet purple, others muted) over the attainable total, a legend, the top four sources (check mark when maxed,
  "+ N more" expands) and one collapsed line for situational, temporary, missing and not-in-API sources.
- **Upgrades:** the step-by-step plan as numbered icon cards (gain green, cost right), then every ranked upgrade in
  the same card style; the model, assumptions, cost efficiency and resale notes are tooltips. "Rank: rate gain" /
  "Rank: <stat> points" replaces the old Stats tab. **View: Other & progress** switches to the guide stage (purple, expandable
  checklist items), "Can't check: skip if you already have it" bonuses (cyan cards: effect, gain, price) and "Progress you
  have to grind" (green cards: distance as the subtitle, gain, "Grinding"); requirement, how to get it and caveats are tooltips.
- **Methods** (old Rates + Compare): the session tracker card (measured vs model, active/elapsed, Pristine, counted items;
  its controls Start/Stop, Reset, Save, Auto-start, HUD join the tab's row; Home shows a short tile while a session exists), the bar chart, then the selected method's rate card (big number, coins at each
  price, efficiency, ticks, next threshold, tool and zone, expandable assumptions), "Same change, different value"
  and the manual Type Stats scenario.
- **More** (settings button, F1): Type Stats, stat focus, Demo; HOTM/skill meters, powder, the gear audit for the
  focused stat (old Overview), profile progression and notes, help, manual checks and Inventory API help.

Rows are typed (text, meter, stacked bar, legend, label/value pair, icon entry, slot grid, big number, bar-chart
row). Layout runs only when content changes: it wraps text, fits labels, splits tooltips and positions slots and
legend chips. Rendering draws only visible cards and rows. Wide screens use two columns (full-width cards span both,
stat cards fill the shorter column); narrow screens stack them; screens under 300 px tall drop optional cards (the
Home chart, "Stats use", the manual scenario).

# Stitch adaptation and responsiveness review

Source project: SkyProgression Mod GUI Design, 9906348672596825476.
Screens reviewed as both screenshots and HTML:
- 2873936fe9a64cf6ae083d8aff1d97a0: Upgrades
- 785c7504359947c796a701d1112caae4: Selectors and Type Stats
- e0ab39ba76ff490193432a13275470f5: Vanilla-style Overview
- f22e100e7bb3446bbca8e291bcb37ced: Wide Overview

Implemented as native Minecraft widgets and drawing calls, not embedded HTML.
Palette: background #0D0D13, panels #1B1B22, gold #FFC665, cyan #49F6F6,
green #40ED45, warm gray labels. Minecraft's font and illustrative vanilla item
icons preserve readability and avoid remote font/image work during gameplay.
Item icons come from a bundled table (`./gradlew :core:extractIcons`): the item's base material,
leather colour or head texture, with its SkyBlock ID in custom_data so texture packs retexture it.
An upgrade card shows the item the entry is about (`UpgradeIcons`): the drill part, reforge stone,
gem, enchanted book, Recombobulator, coating, essence, shard, pet or pet item; HOTM perks show their
powder (prismarine crystals, pink dye, packed ice), the Mining skill a stone pickaxe, collections
their item. Unknown IDs fall back to the gear piece's or slot's icon. Stacks are cached per ID.
Wide layouts split cards into two columns; compact layouts stack them. Small-height
layouts omit the summary strip to leave room for scrolling. Selectors are searchable.

The mockups contain illustrative mechanics, account data and buttons. The port only
shows supported live/demo values. It does not invent pet progress, powder goals,
network latency, crafting savings, cumulative upgrade gains, Forge trees or purchases.
API credentials remain in the companion; the mockup's obsolete /api new command is
not carried over. No API-online indicator is inferred from simply opening the UI.

## Freeze-related findings

1. The old singleton loaded the catalog, read settings and initialized Java HTTP/TLS
   on first access from the game thread. These now initialize on a worker.
2. The Type Stats Save action wrote settings on the game thread. It now snapshots
   inputs and serializes/writes them on an ordered disk worker. Errors remain visible.
3. Ranking and UI rebuilding were synchronous. Ranking now runs on a worker; stale
   results are rejected after tab/profile changes. Wrapping occurs only on changes;
   rendering draws only cards/lines intersecting the viewport.
4. InputStream response reads could outlive the HTTP header timeout. BoundedHttp
   caps full response bytes and elapsed time, including a stalled response body.
   Cancellation interrupts the worker and cancels the underlying HTTP future.
5. Request callbacks targeted the screen that began loading. Closing and reopening
   could leave the new screen showing stale results. A revision counter now refreshes
   whichever main screen is active. Late responses cannot replace a newer lookup/demo.
6. Default screen backgrounds could request a fullscreen blur pass. These screens
   use a plain dim background instead; there is no web renderer or per-frame I/O.

These are identified blocking/stale-state risks, not a confirmed runtime diagnosis.
The owner tests Minecraft. No live game freeze or GUI rendering was reproduced here.
Regression tests exercise successful, oversized and headers-then-stalled HTTP bodies.

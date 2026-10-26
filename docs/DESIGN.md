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

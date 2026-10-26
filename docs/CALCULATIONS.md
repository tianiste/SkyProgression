# Calculation scope and sources

The calculation engine is independent of Minecraft in `core/`.

## Base-item alternatives

Each catalog group is mutually exclusive: armor slots, tool, equipment slots, accessory
families and lantern. Owning both accessory tiers does not stack their stats.
For a stat query, the best owned item is the one with the highest value of that stat.
For method queries, the first version uses a coarse selection score: relevant fortune
plus relevant spread, plus 100 times Pristine for gems, plus 0.01 times speed for non-instant methods.
This selection heuristic is not an optimizer and may disagree with a fully modeled setup.

Base-item replacement deltas compare target base stats against the selected owned copy's displayed stats (or base stats when unavailable). All alternatives compare
to the same baseline; do not sum every recommendation into one shopping list. Unmet HOTM
requirements appear after available upgrades. Missing prices sort last. Corrupt or absent
inventory data disables recommendations rather than interpreting the inventory as empty.

Cost per stat point = full quoted purchase cost / positive stat delta.
No resale credits, recipe discounts or modifier transfers are assumed. Supported socket fees are included.

## Drop and rate model

Fortune multiplier = `1 + (Mining Fortune + relevant specialized Fortune) / 100`.
Spread multiplier = `1 + relevant Spread / 100` (assumes extra blocks can be mined).
Pristine multiplier for gems = `1 + 0.79 * Pristine`; otherwise 1.
Combined yield is the product of those multipliers.

Estimated improvement % = `100 * (yield after / yield before - 1)`.
Cost per 1% = `purchase cost / estimated improvement %`.
Speed/cold-only changes are available in stat queries, not assigned imaginary drop gains.

Items/hour = `3600 * efficiency/100 * active blocks/sec * base drops/block * yield`.
Coins/hour and Coleweight/hour multiply that rate by the user-entered per-item values.
These are gross scenario estimates. They are not session tracking or the bot's proprietary calculations.

## Data sources

- Hypixel item definitions: <https://api.hypixel.net/v2/resources/skyblock/items>
  (base item snapshot fetched 2026-10-26).
- Hypixel profile/inventory API and encoding: <https://api.hypixel.net/>
- Mining-level XP and collection tiers use official `/v2/resources/skyblock/skills`
  and `/v2/resources/skyblock/collections` snapshots from 2026-10-26.
- Coflnet `GET /api/item/price/{itemTag}/current`:
  <https://sky.coflnet.com/api/index.html>. Quotes request listings at least 120 seconds old.
- Pristine expected-drop model:
  <https://hypixelskyblock.minecraft.wiki/w/Pristine>.
- Lantern increments were supplied in the user's Mining Cat screenshot. They are explicitly
  modeled as mutually exclusive deployed bonuses and should be rechecked after game patches.
- Fabric 26.1 migration: <https://fabricmc.net/2026/03/14/261.html>.

Mining Cat and SkyCompass inspired the requested workflow. Their code and computed profile
output are not bundled, scraped as a dataset, or used as an undocumented runtime dependency.

## Extending the mod

Add validated item data to `core/src/main/resources/mining-items.json` and add regression
tests for new rules. A complete gear evaluator needs modifier parsing, condition-aware
stat sources, mutually compatible slots, actual equipped loadouts, ability uptime,
break-speed thresholds, pet math, craft/upgrade recipes and method-specific rates.
Those are separate work from bumping the Minecraft version.

For Minecraft updates: update `gradle.properties`, adjust the client adapter if needed,
run `./gradlew build :service:distZip`, then test in-game. Do not widen the supported
Minecraft version range before testing it. The core and service have no Minecraft imports.

## Individual gear upgrades (0.2.0)

UUID deduplication prevents the same gear copy appearing twice across containers.
Displayed lore totals replace base values rather than adding modifiers a second time.
Lore can contain context-dependent values: it is not a complete effective stat calculator.
Fortune, Efficiency/Silex, Prismatic and Polarvoid proposals use incremental stat deltas.
Gemstone deltas use quality and item rarity, including recombobulation and accessory scaling.
Occupied flexible slots retain their current gem type. Unknown qualities are skipped.
Costs deduct owned materials independently per alternative; no shared shopping-list allocation.
Slot unlock ingredients and coins come from official Hypixel item metadata. Removal fees
are included, and removed gems receive no resale credit. Missing ingredient prices make
the whole cost unknown. Enchantment XP costs are not converted to coins.

Rule references:
- https://hypixelskyblock.minecraft.wiki/w/Enchantment_Fortune
- https://hypixelskyblock.minecraft.wiki/w/Enchantment_Efficiency
- https://hypixelskyblock.minecraft.wiki/w/Enchantment_Prismatic
- https://hypixelskyblock.minecraft.wiki/w/Silex
- https://hypixelskyblock.minecraft.wiki/w/Polarvoid_Book
- https://hypixelskyblock.minecraft.wiki/w/Gemstone
- https://hypixelskyblock.minecraft.wiki/w/Gemstone_Slot

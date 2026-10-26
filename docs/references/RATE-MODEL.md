# Rate model (research, 2026-10-26)

Derived from the owner's guides and Debotes's Mining Cat Coal pages. The Magma Fields gemstone and Fairy
Grotto parts are implemented (`MethodRates`, docs/CALCULATIONS.md "Rates: Magma Fields gemstones", "Rates: Fairy
Grotto", "Upgrade ranking by rate"); the rest is research.
Constants marked "fitted" match Mining Cat output but are not verified game mechanics.

## 1. Break time (ticks)

    ticks = round(30 * blockStrength / miningSpeed)      (0 means instamine)
    airborne: effective speed = miningSpeed / 5
    speed needed for n ticks = 30 * blockStrength / (n + 0.5); instamine needs > 60 * strength

Implied strengths reproduce every guide threshold: Cobblestone 10 (601), End Stone 15
(901), Coal 30 (1,801 ground / 9,001 airborne), Gold block 600 (36,001), Jasper 4,800
(32,000 for 4 ticks). Verify strengths against the wiki block list before encoding.
Speed only matters when it crosses a tick boundary; between boundaries it gives 0%.

## 2. Blocks per hour

    blocksPerHour = 3600 * efficiency * blocksPerSecond * (1 + spreadEff * Spread / 100)
    ticked blocks:  blocksPerSecond = 20 / ticks          (check whether +1 tick applies)
    instamine:      blocksPerSecond = per-method movement cap (calibrate to guide rates)
    route airborne share (Coal): time factor = (1 - a) + a * slowdown; a = 0.08, slowdown 4

spreadEff = 0.8 (fitted). Snoozle +8 -> 1.6%, Snoozle +2 after VIII -> 0.39% exactly.
Ability uptime mixes two states (MSB: speed x4, so different ticks; Maniac Miner: +Fortune
while active): rate = uptime * rate(active) + (1 - uptime) * rate(normal), with uptime =
duration / (cooldown * (1 - cooldownReduction)).

## 3. Drops

    dropsPerBlock = baseDrops * (1 + (MiningFortune + typeFortune) / 100) * pristineMult
    typeFortune: Ore / Block / Gemstone / Dwarven Metal Fortune as applicable
    pristineMult (gems) = 1 + 79 * Pristine / 100   (Flawed = 80 Rough)

## 4. Ranking (Mining Cat style)

    gain% = rate(after) / rate(before) - 1        on the method's baseline loadout
    costPer1% = netCost / gain%,  netCost = purchase - resale of replaced item

Checks on Coal (Fortune 1,724.5 + Ore 100, Spread 300): Fortune +35 -> 1.82% (1.8),
+25 -> 1.30% (1.3), +5 -> 0.26% (0.26), +10 -> 0.52% (0.52); Spread +50 then Fortune +25
compounded -> 11.4% (11.5 shown). Mining Cat's Coal baseline excludes situational and
temporary bonuses (in-game tooltip showed 2,248 Fortune). Gold uses a different baseline
(Flawless Jade +35 -> 2.9% implies ~1,107 Fortune), likely a different assumed loadout.

## Open questions

- Is there an extra tick per block on ticked blocks? MF gem guide rates (Ruby 23.5, Amber/Jade/Sapphire/
  Amethyst 20.1, Topaz 16.5 Flawless/h at 60%) are matched within about 5% without one, using Flawless =
  512,000 Rough and an estimated hypermax. Their ratios alone fit several tick models; needs a measured
  session (blocks mined per minute at a known speed).
- Where does spreadEff 0.8 come from (missing neighbor blocks?), and does it vary by method?
- Per-method instamine caps for Cobble/End Stone/Mycelium/Red Sand from guide rates.
- Gold's baseline loadout; how Maniac Miner's +500 enters Coal.

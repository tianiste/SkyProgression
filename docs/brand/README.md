# SkyProgression logo

A diamond pickaxe above Minecraft's XP bar: mining, and how far along you are. It is drawn on a 16 x 16 pixel grid,
so at 16 px every grid cell is exactly one screen pixel.

## Files

| File | Use |
| --- | --- |
| `mark.svg`, `icon-<size>.png` | The symbol: mod icon, avatars, social images (16 to 512 px) |
| `mark-black.svg`, `mark-white.svg` | One-colour symbol (pickaxe and filled XP bar cut out of the tile) |
| `lockup-stacked-*-bg.*` | Symbol above the name: README header, centred layouts |
| `lockup-horizontal-*-bg.*` | Symbol beside the name: banners, page headers |
| `favicon.ico` | 16, 32 and 48 px browser icon |

`dark-bg` versions have light lettering for dark backgrounds; `light-bg` versions have dark lettering for light
backgrounds. The mod icon is `src/main/resources/assets/skyprogression/icon.png` (128 px, same as `icon-128.png`).

## Rules

- **Scale by whole numbers only** (1x, 2x, 3x...), with nearest-neighbour scaling (`image-rendering: pixelated`
  on the web). Other factors blur or unevenly size the pixels.
- **Minimum size:** symbol 16 px; lockups with the lettering at least 2 px per grid cell (horizontal 208 px wide).
- **Clear space:** at least 2 grid cells (one eighth of the symbol) on every side.
- **Colours:** tile `#1C1E26` with edge `#3A3E4C`; diamond `#B8FBFF` / `#4FE3EC` / `#1C9AA6`; handle `#A8743A` /
  `#6E4720`; XP bar `#8EF04A` on `#2F7A16`. "SKY" uses the diamond cyan.
- Don't recolour, rotate, outline, add gradients or shadows, smooth the pixels, or set the name in another typeface.
  The lettering is drawn pixel by pixel, not a font.

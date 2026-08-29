# Boss boulder pixel art

Six skins for the stone the boss rolls across the arena. Each `<style>.aseprite` is
a 64x32 RGBA source in one frame with three named layers - `rock`, `cracks`,
`detail` - and flattens exactly to the runtime sheet at

```
src/main/resources/assets/cnpcgeckoaddon/textures/entity/boulder/<style>.png
```

which is the plain `texOffs(0, 0)` unwrap of a 16x16x16 box:

| island | area (`x1,y1 - x2,y2`) |
|---|---|
| top | 16,0 - 32,16 |
| bottom | 32,0 - 48,16 |
| left | 0,16 - 16,32 |
| front | 16,16 - 32,32 |
| right | 32,16 - 48,32 |
| back | 48,16 - 64,32 |

Everything off the islands is fully clear, and everything on them is fully opaque:
the renderer draws the boulder cutout, so there is no half pixel anywhere. The
ghost skin's transparency is a colour, not an alpha.

## What the drawing has to survive

`RenderBossBoulder` dresses the boulder with **three shrunken copies of the same
cube**, turned against each other, all carrying this one unwrap. So:

* **The drawing is not allowed to know which way is up.** No horizon, no single big
  landmark - a moss cap or a lava lake would be seen three times at once, and would
  hand the rolling stone a top.
* **The faces have to join all the way round.** The right edge of `front` runs into
  the left edge of `right`, `back` wraps round into `left`, and the top and bottom
  islands join the side band on all four of their edges.
* **Magma, sculk and ghost are drawn at full bright.** Their glow is painted into
  the texture, because the engine will not shade them in a dark dungeon.

Rather than matching the seams up by hand, every texel is painted from a *solid
three-dimensional field* sampled at the point of the cube surface that the texel
actually covers. The seams then join by construction and cannot drift when a style
is retuned, and nothing in the field has an orientation to give away. The
pixel-to-surface table was read out of vanilla's `ModelPart$Cube` for a 16-cube at
offset (0, 0) on a 64x32 sheet, not guessed.

Each style is then one set of numbers over that shared drawing: how many chips the
crack network breaks the stone into, how wide the cracks run, where the style sits
on its own value ramp, and which extra pass it gets - glow, moss or speckle.

## Palettes

12 to 15 colours each, in `<style>.gpl` beside the source. Shading is a hue turn,
not a brightness slider: hollows go cool and violet, lit faces go warm.

| style | palette taken from |
|---|---|
| `sculk` | table `C` of `art/aseprite/boss_bar/sculk/build_runtime_layers.lua`, plus two slate rungs walking the same hue (a boss bar draws slate a few pixels wide, a boulder turns a whole facet through it) |
| `magma` | the five burning tones of `art/boss_bar_infernal`, with the white-hot from `art/aseprite/hook/chain_infernal.gpl`; the near-black crust is its own warm ramp, because `infernal_fire.gpl` is a sample of that art's dark backdrop rather than of its fire |
| `mossy` | `art/gui/boss_bar_moss_cave.aseprite` - its greys, its greens and its dry olive |
| `ghost` | `art/aseprite/hook/ghost.gpl`, carried two rungs further down so hollows can go dark without going grey |
| `stone`, `bone` | own palettes, kept inside the vanilla value range |

## Build

Aseprite CLI export:

```powershell
aseprite --batch --script-param projectDir="$PWD" --script scripts/build_boulder_skins.lua
```

The deterministic Pillow generator that drew the committed art needs no Aseprite.
It writes the runtime PNGs, the `.aseprite` sources, the palettes and the review
sheets, then validates what it wrote:

```powershell
python scripts/generate_boulder_skins.py
```

Draw one style with `--style magma`. Check the committed files without writing
anything with `--check`. A rebuild is byte for byte identical to what is committed.

## Previews

`preview/<style>_4x.png` shows the unwrap at 4x with a hairline round each island,
and beside it the three faces that meet at one corner assembled into the real
isometric lump - on a dungeon wall and in daylight, because the boulder is seen
against both. `preview/all_styles_4x.png` is all six side by side.

## How it was checked

`--check` fails the build on any of:

* sheet not 64x32, any half-transparent texel, any paint off the islands, any hole
  in one;
* colours outside the style's `.gpl`, a `.gpl` entry that is never drawn, or a
  count outside 10..16;
* **seams**: the mean luminance step across the twelve cube edges must be no worse
  than the mean step between neighbouring texels *inside* a face. Neighbours are
  taken across the cube, not across the sheet, so a pair straddling a seam is
  compared at its real distance. Every style currently steps *less* at its seams
  than it does over its own rock;
* **no landmark**: the largest patch of one flat colour, the most crowded the
  accent colours ever get inside one small patch of surface, and how far the
  accents pull off centre when read as directions out of the middle of the stone;
* the `.aseprite` source flattening to the committed PNG byte for byte.

Seam joins and the absence of a "this way up" were also read by eye off the
assembled views in `preview/`, which is what the numbers above stand in for.

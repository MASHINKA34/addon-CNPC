# Boss chest skins

This directory contains the layered sources and constrained palettes for the six boss chest skins. Each `.aseprite` file is a 64x64 RGBA sprite with two frames: frame 1 is the recolored vanilla chest UV, and frame 2 stores the matching 16x16 particle tile in its upper-left corner. The editable layers are `palette_recolor` and `style_accents`. Vanilla pixels are not embedded as a guide or committed separately; `normal.png` remains an external build input.

## Build

Generate the Aseprite sources, GPL palettes, runtime PNGs, particle textures, and previews with Python 3 and Pillow:

```text
python scripts/generate_chest_skins.py --vanilla <path-to-normal.png>
```

`--vanilla` is optional. Without it, the generator opens `assets/minecraft/textures/entity/chest/normal.png` directly from the Minecraft 1.21.1 client-resource jar produced by ModDevGradle, then falls back to the NeoForm/Fabric Gradle caches. The raw vanilla PNG is never copied into the repository.

The generator maps every exact opaque vanilla color. It classifies the six grayscale latch colors as a separate metal branch, orders the remaining colors by luminance, and remaps them to 14 material steps plus 6 metal steps. Style accents use at most three additional colors. A protected mask prevents accents from overwriting vanilla board-edge contrast or the latch and limits changed pixels to 10% of the opaque UV.

To validate existing output without changing it:

```text
python scripts/generate_chest_skins.py --check
```

## Outputs

- Runtime chest UVs: `src/main/resources/assets/cnpcgeckoaddon/textures/entity/chest/<style>.png`
- Particle tiles: `src/main/resources/assets/cnpcgeckoaddon/textures/block/boss_chest_<style>.png`
- Four-times-nearest previews: `art/aseprite/chest/preview/<style>_4x.png`
- Vanilla comparison sheet: `art/aseprite/chest/preview/all_styles_4x.png`

The preview places the UV and an assembled front view on both dark dungeon stone and a light stone background.

## Palette sources

- `moss_cave` samples the green-black stone and moss family used by `art/gui/boss_bar_moss_cave.aseprite` and its runtime texture.
- `infernal` takes its charred grayscale ramp from `art/boss_bar_infernal/infernal_fire.gpl`; the ember ramp matches the existing infernal hook cord family.
- `ghost` uses the cold cyan family from `art/aseprite/boss_bar_ghost_dungeon.aseprite` and `art/aseprite/hook/ghost.gpl` at full opacity.
- `sculk` uses the `C` colors from `art/aseprite/boss_bar/sculk/build_runtime_layers.lua`.
- `gilded` and `bone` use new, vanilla-style dark-wood/gold and aged-bone/sinew ramps.

Validation requires 64x64 and 16x16 RGBA8 output, exact equality with the vanilla chest alpha mask, no semi-transparent pixels, a fully opaque particle tile, 12–24 opaque chest colors, no protected accent changes, and at least 70% retained horizontal vanilla edges. The current six outputs retain 97–100% of the measured structure with 22–23 colors, and a second full build reproduces all 31 generated files byte-for-byte.

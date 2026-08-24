# Boss chest skins

This directory contains the layered sources and constrained palettes for the six boss chest skins. Each `.aseprite` file is a 64x64 RGBA sprite with two frames: frame 1 is the vanilla chest UV, and frame 2 stores the matching 16x16 particle tile in its upper-left corner. The hidden `vanilla_guide` layer is the unmodified Minecraft 1.21.1 `normal.png` layout; visible layers are `base`, `shading`, `details`, and `glow` where the style uses emissive-colored pixels.

## Build

With Aseprite CLI:

```text
aseprite --batch --script-param projectDir=<repo> --script scripts/build_chest_skins.lua
```

The machine used to create these assets did not have Aseprite CLI, so the committed sources, palettes, runtime PNGs, and previews were generated with the deterministic Pillow fallback:

```text
python scripts/generate_chest_skins.py
```

The fallback searches the ModDevGradle/Gradle cache for the Minecraft 1.21.1 client resources. An explicit template can be supplied with `--vanilla <path-to-normal.png>`. The raw vanilla PNG is not stored separately in the repository.

To validate existing output without changing it:

```text
python scripts/generate_chest_skins.py --check
```

## Outputs

- Runtime chest UVs: `src/main/resources/assets/cnpcgeckoaddon/textures/entity/chest/<style>.png`
- Particle tiles: `src/main/resources/assets/cnpcgeckoaddon/textures/block/boss_chest_<style>.png`
- Four-times-nearest previews: `art/aseprite/chest/preview/<style>_4x.png`

The preview places the UV and an assembled front view on both dark dungeon stone and a light stone background.

## Palette sources

- `moss_cave` samples the green-black stone and moss family used by `art/gui/boss_bar_moss_cave.aseprite` and its runtime texture.
- `infernal` takes its charred grayscale ramp from `art/boss_bar_infernal/infernal_fire.gpl`; the ember ramp matches the existing infernal hook cord family.
- `ghost` uses the cold cyan family from `art/aseprite/boss_bar_ghost_dungeon.aseprite` and `art/aseprite/hook/ghost.gpl` at full opacity.
- `sculk` uses the `C` colors from `art/aseprite/boss_bar/sculk/build_runtime_layers.lua`.
- `gilded` and `bone` use new, vanilla-style dark-wood/gold and aged-bone/sinew ramps.

Every style has fourteen opaque palette colors. Validation requires 64x64 and 16x16 RGBA output, exact equality with the vanilla chest alpha mask, no semi-transparent pixels, a fully opaque particle tile, and no more than sixteen colors per style.

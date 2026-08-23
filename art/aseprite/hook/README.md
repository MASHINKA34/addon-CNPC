# Hook cord pixel art

Each `<id>.aseprite` file is a 32×16 RGBA source with four timeline frames. The
left 16×16 half is the seamless cord segment and the right half is its downward
facing hook head. Runtime exports are vertical 16×64 filmstrips under
`src/main/resources/assets/cnpcgeckoaddon/textures/entity/hook/<id>/`.

The source layers are named by purpose (`base`, `shading`, `highlights`, a
style-specific detail or `glow` layer, and `head`). Matching GIMP palettes live
beside the Aseprite files. Review sheets are in `preview/`; they show all four
cord and head frames plus eight joined copies of frame 0 on dark stone, bright
sky, and checkerboard backgrounds at 8× nearest-neighbour scale.

## Build

Aseprite CLI export:

```powershell
aseprite --batch --script-param projectDir="$PWD" --script scripts/build_hook_cords.lua
```

The deterministic Pillow fallback used to create the committed art also writes
valid multi-frame `.aseprite` files, reads those sources back, and exports the
same runtime PNG layout:

```powershell
python scripts/generate_hook_cords.py
```

Build one style with `--style vine` (or another style id). Validate the existing
files without modifying them with `--check`. Rebuild everything and prove that
all `.aseprite`, `.gpl`, runtime PNG, and preview bytes stay unchanged with:

```powershell
python scripts/generate_hook_cords.py --verify
```

Validation checks the 16×64 RGBA8 non-interlaced PNG contract, restricted PNG
chunk set, four frames and named layers, top/bottom seam equality plus an
eight-segment join, 8–14 palette entries, alpha policy, cyclic frame delta, and
source-to-runtime pixel/byte equality.

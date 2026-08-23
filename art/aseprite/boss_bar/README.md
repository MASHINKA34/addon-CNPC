# Boss-bar timer artwork

Each style directory contains `timer.aseprite`, `timer.gpl`, and a stacked
`timer_preview.png` showing 5%, 50%, 95%, and a red-tinted 50% state. The three
source layers are named exactly like their runtime PNGs and are ordered back to
front: `timer_background`, `timer_fill`, `timer_frame`.

On a workstation with Aseprite CLI, export all runtime layers with:

```text
aseprite --batch --script-param projectDir=<repo> --script scripts/build_bossbar_timers.lua
```

This checkout was authored with the deterministic Pillow fallback because
Aseprite CLI was unavailable:

```text
python scripts/generate_bossbar_timers.py
python scripts/generate_bossbar_timers.py --check
```

The check enforces the contract canvas and track sizes, RGBA8 non-interlaced
PNG output without profiles, a fully opaque and horizontally uniform fill lane,
transparent fill pixels outside the lane, parent-bar-only colors, readable
normal/red/cold tints, exact layer names, and byte-identical PNG reconstruction
from every generated Aseprite source.

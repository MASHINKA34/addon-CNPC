#!/usr/bin/env python3
"""Ensure every imported Apocalyptic Bosses model has a portable idle clip."""

from __future__ import annotations

import copy
import json
from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parent.parent
ASSET_ROOT = (
    PROJECT_DIR
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "panascraftrpgmod"
)
GEO_ROOT = ASSET_ROOT / "geo"
ANIMATION_ROOT = ASSET_ROOT / "animations"

IDLE_SOURCES = {
    "lavagod": "waiting",
}


def main() -> None:
    added = 0
    failures: list[str] = []
    for model in sorted(GEO_ROOT.glob("*.geo.json")):
        name = model.name.removesuffix(".geo.json")
        animation_path = ANIMATION_ROOT / f"{name}.animation.json"
        if not animation_path.is_file():
            failures.append(f"missing animation file for {model.name}")
            continue

        document = json.loads(animation_path.read_text(encoding="utf-8-sig"))
        animations = document.get("animations")
        if not isinstance(animations, dict) or not animations:
            failures.append(f"no animation clips in {animation_path.name}")
            continue
        if "idle" in animations:
            continue

        source_name = IDLE_SOURCES.get(name)
        source = animations.get(source_name) if source_name else None
        if not isinstance(source, dict):
            failures.append(f"no reviewed idle source for {animation_path.name}")
            continue

        idle = copy.deepcopy(source)
        idle["loop"] = True
        animations["idle"] = idle
        animation_path.write_text(
            json.dumps(document, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"{animation_path.relative_to(PROJECT_DIR)}: added idle from {source_name}")
        added += 1

    if failures:
        for failure in failures:
            print(f"ERROR {failure}")
        raise SystemExit(f"Animation audit failed with {len(failures)} error(s)")
    print(f"Animation audit passed for {len(list(GEO_ROOT.glob('*.geo.json')))} models; added {added} idle clips")


if __name__ == "__main__":
    main()

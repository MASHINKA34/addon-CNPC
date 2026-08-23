#!/usr/bin/env python3
"""Print a compact renderer/model/texture cross-reference for decompiled donors."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    args = parser.parse_args()
    for donor in sorted(path for path in args.root.iterdir() if path.is_dir()):
        print(f"\n### {donor.name}")
        for path in sorted(donor.rglob("*.java")):
            if "render" not in path.stem.lower() and "client" not in path.as_posix().lower():
                continue
            source = path.read_text(encoding="utf-8", errors="replace")
            if not any(marker in source for marker in ("Renderer", "EntityRenderer", "RenderType", "getTextureLocation")):
                continue
            models = sorted(set(re.findall(r"\b([A-Z][A-Za-z0-9_]*Model)\b", source)))
            strings = re.findall(r'"([^"]+)"', source)
            textures = sorted(
                set(
                    value
                    for value in strings
                    if value.lower().endswith(".png") or "textures/" in value.lower()
                )
            )
            if models or textures:
                print(
                    f"{path.stem}\tmodels={','.join(models) or '-'}\t"
                    f"textures={','.join(textures) or '-'}"
                )


if __name__ == "__main__":
    main()

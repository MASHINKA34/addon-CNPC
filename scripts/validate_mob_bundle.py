#!/usr/bin/env python3
"""Validate bundled geometry, animation, texture mappings, and resource paths."""

from __future__ import annotations

import json
import re
from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parent.parent
RESOURCE_ROOT = PROJECT_DIR / "src" / "main" / "resources"
ASSET_ROOT = RESOURCE_ROOT / "assets"
PATH_PATTERN = re.compile(r"^[a-z0-9._/-]+$")


def resource_path(identifier: str) -> Path:
    namespace, path = identifier.split(":", 1)
    return ASSET_ROOT / namespace / Path(path)


def main() -> None:
    failures: list[str] = []
    json_count = 0
    geo_count = 0
    animation_count = 0
    png_count = 0
    for path in ASSET_ROOT.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(ASSET_ROOT).as_posix()
        if not PATH_PATTERN.fullmatch(relative):
            failures.append(f"invalid resource path: {relative}")
        if path.suffix == ".json":
            json_count += 1
            try:
                document = json.loads(path.read_text(encoding="utf-8-sig"))
            except Exception as exc:
                failures.append(f"invalid JSON {relative}: {exc}")
                continue
            if path.name.endswith(".geo.json"):
                geo_count += 1
                geometries = document.get("minecraft:geometry")
                if not isinstance(geometries, list) or not geometries:
                    failures.append(f"missing minecraft:geometry: {relative}")
                else:
                    for geometry in geometries:
                        names = [bone.get("name") for bone in geometry.get("bones", [])]
                        duplicate_names = sorted({name for name in names if names.count(name) > 1})
                        if duplicate_names:
                            failures.append(f"duplicate bones {duplicate_names}: {relative}")
            if "/animations/" in f"/{relative}":
                animation_count += 1
                if not isinstance(document.get("animations"), dict):
                    failures.append(f"missing animations object: {relative}")
        elif path.suffix == ".png":
            png_count += 1
            if path.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
                failures.append(f"invalid PNG signature: {relative}")

    mapping_path = RESOURCE_ROOT / "META-INF" / "MOBMODEL_TEXTURES.tsv"
    mappings: dict[str, str] = {}
    for line_number, line in enumerate(mapping_path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 2:
            failures.append(f"invalid mapping line {line_number}")
            continue
        model, texture = fields
        if model in mappings:
            failures.append(f"duplicate model mapping: {model}")
        mappings[model] = texture
        if not resource_path(model).is_file():
            failures.append(f"mapped model missing: {model}")
        if not resource_path(texture).is_file() and not texture.startswith("minecraft:"):
            failures.append(f"mapped texture missing: {texture}")

    print(
        f"Validated {json_count} JSON files ({geo_count} geometry, {animation_count} animation), "
        f"{png_count} PNG files, and {len(mappings)} model-texture mappings."
    )
    if failures:
        for failure in failures:
            print(f"ERROR {failure}")
        raise SystemExit(f"Validation failed with {len(failures)} error(s)")
    print("Validation passed")


if __name__ == "__main__":
    main()

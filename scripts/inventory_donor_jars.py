#!/usr/bin/env python3
"""Inventory mob-model resources in donor JARs without loading their code."""

from __future__ import annotations

import argparse
import json
import re
import zipfile
from pathlib import Path


ENTITY_KEY = re.compile(r"^entity\.([a-z0-9_.-]+)\.([a-z0-9_/.-]+)$")


def read_json(archive: zipfile.ZipFile, name: str):
    try:
        return json.loads(archive.read(name).decode("utf-8-sig"))
    except (KeyError, UnicodeDecodeError, json.JSONDecodeError):
        return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar_dir", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    inventories = []
    for jar_path in sorted(args.jar_dir.glob("*.jar")):
        with zipfile.ZipFile(jar_path) as archive:
            names = archive.namelist()
            lower_names = {name.lower(): name for name in names}
            assets = sorted({name.split("/")[1] for name in names if name.startswith("assets/") and name.count("/") >= 2})
            geos = sorted(name for name in names if name.lower().endswith(".geo.json"))
            animations = sorted(
                name for name in names
                if name.startswith("assets/")
                and "/animations/" in name.lower()
                and name.lower().endswith(".json")
            )
            entity_textures = sorted(
                name for name in names
                if name.startswith("assets/")
                and ("/textures/entity/" in name.lower() or "/textures/entities/" in name.lower())
                and name.lower().endswith(".png")
            )
            model_classes = sorted(
                name for name in names
                if name.endswith(".class")
                and ("/model/" in name.lower() or name.lower().endswith("model.class"))
                and "$" not in name
            )
            animation_classes = sorted(
                name for name in names
                if name.endswith(".class")
                and ("animation" in name.lower() or "animations" in name.lower())
                and "$" not in name
            )

            entity_keys = {}
            for namespace in assets:
                lang_name = lower_names.get(f"assets/{namespace}/lang/en_us.json")
                if not lang_name:
                    continue
                lang = read_json(archive, lang_name)
                if not isinstance(lang, dict):
                    continue
                for key, value in lang.items():
                    match = ENTITY_KEY.match(key)
                    if match:
                        entity_keys[key] = value

            license_names = sorted(
                name for name in names
                if Path(name).name.lower() in {"license", "license.txt", "license.md", "copying", "copying.txt"}
            )
            inventories.append({
                "jar": jar_path.name,
                "size_bytes": jar_path.stat().st_size,
                "assets": assets,
                "geo_count": len(geos),
                "animation_count": len(animations),
                "entity_texture_count": len(entity_textures),
                "entity_key_count": len(entity_keys),
                "model_class_count": len(model_classes),
                "animation_class_count": len(animation_classes),
                "geos": geos,
                "animations": animations,
                "entity_textures": entity_textures,
                "entity_keys": entity_keys,
                "model_classes": model_classes,
                "animation_classes": animation_classes,
                "license_files": license_names,
            })

    rendered = json.dumps(inventories, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print("jar\tassets\tgeo\tanim\ttextures\tentities\tmodel_classes\tanimation_classes")
    for item in inventories:
        print("\t".join((
            item["jar"],
            ",".join(item["assets"]),
            str(item["geo_count"]),
            str(item["animation_count"]),
            str(item["entity_texture_count"]),
            str(item["entity_key_count"]),
            str(item["model_class_count"]),
            str(item["animation_class_count"]),
        )))


if __name__ == "__main__":
    main()

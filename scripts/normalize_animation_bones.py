"""Normalize donor animation bone names to the bundled GeckoLib geometry.

Some Blockbench exports retain punctuation in animation bone keys while the
matching geometry removes it. GeckoLib resolves bones by exact name, so those
channels would otherwise be ignored when the original mod's custom animation
processor is not present.
"""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "assets"


def normalized(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


MANUAL_RENAMES = {
    ("block_factorys_bosses", "yeti_boss"): {
        "root": "root001",
        "head": "CTR_head",
        "CTR_shoulder_left": "CTR_shoulde1r_left",
    },
}


def normalize_pair(namespace: str, model: Path, animation: Path) -> int:
    geometry = json.loads(model.read_text(encoding="utf-8"))
    animation_data = json.loads(animation.read_text(encoding="utf-8"))

    geometry_names = {
        bone["name"]
        for bone in geometry["minecraft:geometry"][0].get("bones", [])
    }
    normalized_geometry: dict[str, list[str]] = {}
    for name in geometry_names:
        normalized_geometry.setdefault(normalized(name), []).append(name)

    model_name = model.name.removesuffix(".geo.json")
    manual = MANUAL_RENAMES.get((namespace, model_name), {})
    changed = 0

    for clip in animation_data.get("animations", {}).values():
        bones = clip.get("bones")
        if not isinstance(bones, dict):
            continue

        renamed: dict[str, object] = {}
        for source_name, channels in bones.items():
            target_name = source_name
            if source_name not in geometry_names:
                target_name = manual.get(source_name, source_name)
                if target_name == source_name:
                    candidates = normalized_geometry.get(normalized(source_name), [])
                    if len(candidates) == 1:
                        target_name = candidates[0]

            if target_name in renamed and target_name != source_name:
                raise ValueError(
                    f"Bone rename collision in {animation}: {source_name} -> {target_name}"
                )
            renamed[target_name] = channels
            changed += target_name != source_name

        clip["bones"] = renamed

    if changed:
        animation.write_text(
            json.dumps(animation_data, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return changed


def main() -> None:
    pairs = [
        (
            "block_factorys_bosses",
            ROOT / "block_factorys_bosses" / "geo" / "entity",
            ROOT / "block_factorys_bosses" / "animations" / "entity",
        ),
        (
            "mowziesmobs",
            ROOT / "mowziesmobs" / "geo",
            ROOT / "mowziesmobs" / "animations",
        ),
    ]

    total = 0
    for namespace, geometry_dir, animation_dir in pairs:
        for animation in sorted(animation_dir.glob("*.animation.json")):
            model = geometry_dir / animation.name.replace(".animation.json", ".geo.json")
            if model.exists():
                changed = normalize_pair(namespace, model, animation)
                if changed:
                    print(f"{animation.relative_to(ROOT)}: renamed {changed} channels")
                total += changed
    print(f"Total renamed animation bone channels: {total}")


if __name__ == "__main__":
    main()

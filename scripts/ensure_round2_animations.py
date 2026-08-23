#!/usr/bin/env python3
"""Ensure every round-two geometry has an exact same-stem animation resource."""

from __future__ import annotations

import difflib
import copy
import json
from pathlib import Path

from convert_round2_java_models import animation_document


PROJECT_DIR = Path(__file__).resolve().parent.parent
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"
NAMESPACES = (
    "blighted_beasts",
    "deep_dark_regrowth",
    "deeperdarker_legacy",
    "deeperdarker",
    "sculk_worm",
    "minecraft_dungend_two_mobs",
    "echoes",
    "nue",
    "infernalexp",
    "betternether",
    "piglinproliferation",
    "nourished_nether",
    "creatures_expanded",
    "ecosystemmod",
    "myceliummire",
    "mosslings_muddlings",
    "undergarden",
    "critters_and_cryptids",
    "redev_edition_mobs",
    "wroughtnights",
)


def stem(path: Path, suffix: str) -> str:
    return path.name.removesuffix(suffix)


def canonical(value: str) -> str:
    result = "".join(character for character in value.lower() if character.isalnum())
    for token in ("animations", "animation", "animated", "model", "newest", "new", "fixed", "fix", "java"):
        result = result.replace(token, "")
    return result


def bones_from_geometry(path: Path) -> set[str]:
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    geometries = document.get("minecraft:geometry", [])
    if not geometries:
        return {"root"}
    return {bone.get("name", "root") for bone in geometries[0].get("bones", [])}


def matching_clip(clips: dict, keywords: tuple[str, ...]) -> dict | None:
    for name, clip in clips.items():
        normalized = "".join(character.lower() if character.isalnum() else " " for character in name)
        words = normalized.split()
        if any(keyword in words or keyword in normalized for keyword in keywords):
            return clip
    return None


def ensure_standard_aliases(path: Path, geo: Path) -> int:
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    clips = document.setdefault("animations", {})
    if not clips:
        clips.update(animation_document(bones_from_geometry(geo))["animations"])
    changed = 0
    if "idle" not in clips:
        idle = matching_clip(clips, ("idle", "ambient", "rest", "stand", "default"))
        clips["idle"] = copy.deepcopy(idle or next(iter(clips.values())))
        changed += 1
    if "walk" not in clips:
        walk = matching_clip(clips, ("walk", "run", "move", "swim", "fly", "crawl"))
        clips["walk"] = copy.deepcopy(walk or clips["idle"])
        changed += 1
    if "attack" not in clips:
        attack = matching_clip(clips, ("attack", "bite", "shoot", "strike", "slash", "hit", "melee"))
        portable = animation_document(bones_from_geometry(geo))["animations"]["attack"]
        clips["attack"] = copy.deepcopy(attack or portable)
        changed += 1
    if changed:
        path.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return changed


def main() -> None:
    copied = 0
    generated = 0
    aliases = 0
    for namespace in NAMESPACES:
        namespace_root = ASSET_ROOT / namespace
        geo_root = namespace_root / "geo"
        animation_root = namespace_root / "animations"
        if not geo_root.is_dir():
            continue
        animation_root.mkdir(parents=True, exist_ok=True)
        animations = list(animation_root.rglob("*.json"))
        for geo in geo_root.rglob("*.geo.json"):
            geo_relative = geo.relative_to(geo_root)
            model_stem = stem(geo, ".geo.json")
            target = animation_root / geo_relative.parent / f"{model_stem}.animation.json"
            if not target.is_file():
                model_key = canonical(model_stem)
                candidates = []
                for animation in animations:
                    animation_stem = stem(animation, ".animation.json")
                    if animation_stem == animation.name:
                        animation_stem = animation.stem
                    animation_key = canonical(animation_stem)
                    ratio = difflib.SequenceMatcher(None, model_key, animation_key).ratio()
                    if model_key and animation_key and (model_key in animation_key or animation_key in model_key):
                        ratio += 0.5
                    candidates.append((ratio, animation))
                best = max(candidates, default=(0.0, None), key=lambda pair: pair[0])
                target.parent.mkdir(parents=True, exist_ok=True)
                if best[1] is not None and best[0] >= 0.82:
                    target.write_bytes(best[1].read_bytes())
                    animations.append(target)
                    copied += 1
                    print(f"COPY {namespace}:{geo_relative.as_posix()} <- {best[1].relative_to(animation_root).as_posix()}")
                else:
                    document = animation_document(bones_from_geometry(geo))
                    target.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
                    animations.append(target)
                    generated += 1
                    print(f"GENERATE {namespace}:{geo_relative.as_posix()}")
            aliases += ensure_standard_aliases(target, geo)
    print(f"Exact animations ensured: {copied} copied, {generated} generated, {aliases} standard aliases added")


if __name__ == "__main__":
    main()

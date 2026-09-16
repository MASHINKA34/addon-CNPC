#!/usr/bin/env python3
"""Ensure every round-three model has a paired animation file with an idle clip.

For each geometry of the four donor namespaces this script:

* writes a portable animation file when the donor shipped none for that stem;
* removes channels that address bones the geometry does not contain (props and
  effects that the donor builds at runtime), after
  ``normalize_animation_bones.py`` has already renamed the ones that only differ
  in punctuation or case;
* adds an ``idle`` alias when no clip carries that name, copied from the
  donor's ambient clip when one can be recognised and otherwise from the first
  clip, and ``walk``/``attack`` aliases only when a donor clip clearly is one.

Missing walk or attack clips are not an error; they are listed in the report.
"""

from __future__ import annotations

import copy
import json
from pathlib import Path

from convert_round2_java_models import animation_document


PROJECT_DIR = Path(__file__).resolve().parent.parent
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"
NAMESPACES = ("eeeabsmobs", "dragonforged", "threateningly_mobs",)

IDLE_KEYWORDS = ("idle", "ambient", "rest", "stand", "default", "breathing", "float", "hover")
WALK_KEYWORDS = ("walk", "run", "move", "swim", "fly", "crawl", "slither")
ATTACK_KEYWORDS = ("attack", "bite", "shoot", "strike", "slash", "hit", "melee", "punch", "smash", "swing")


def words(name: str) -> list[str]:
    normalized = "".join(character.lower() if character.isalnum() else " " for character in name)
    return normalized.split()


def matching_clip(clips: dict, keywords: tuple[str, ...]) -> tuple[str, dict] | None:
    for keyword in keywords:
        for name, clip in clips.items():
            if keyword in words(name):
                return name, clip
    return None


def geometry_bones(path: Path) -> set[str]:
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    geometries = document.get("minecraft:geometry") or []
    if not geometries:
        return set()
    return {bone.get("name") for bone in geometries[0].get("bones", []) if isinstance(bone, dict)}


def drop_unknown_bones(document: dict, bones: set[str]) -> list[str]:
    dropped: list[str] = []
    for clip_name, clip in document.get("animations", {}).items():
        channels = clip.get("bones") if isinstance(clip, dict) else None
        if not isinstance(channels, dict):
            continue
        unknown = [bone for bone in channels if bone not in bones]
        for bone in unknown:
            del channels[bone]
            dropped.append(f"{clip_name}#{bone}")
    return dropped


def ensure_aliases(clips: dict, bones: set[str]) -> list[str]:
    added: list[str] = []
    if not clips:
        clips.update(animation_document(bones)["animations"])
        added.append("idle(generated)")
        return added
    if "idle" not in clips:
        found = matching_clip(clips, IDLE_KEYWORDS)
        source_name, source = found if found else next(iter(clips.items()))
        idle = copy.deepcopy(source)
        idle["loop"] = True
        clips["idle"] = idle
        added.append(f"idle<-{source_name}")
    if "walk" not in clips:
        found = matching_clip(clips, WALK_KEYWORDS)
        if found:
            walk = copy.deepcopy(found[1])
            walk["loop"] = True
            clips["walk"] = walk
            added.append(f"walk<-{found[0]}")
    if "attack" not in clips:
        found = matching_clip(clips, ATTACK_KEYWORDS)
        if found:
            clips["attack"] = copy.deepcopy(found[1])
            added.append(f"attack<-{found[0]}")
    return added


def main() -> None:
    generated = 0
    aliases = 0
    dropped_total = 0
    models = 0
    for namespace in NAMESPACES:
        geo_root = ASSET_ROOT / namespace / "geo"
        animation_root = ASSET_ROOT / namespace / "animations"
        if not geo_root.is_dir():
            continue
        animation_root.mkdir(parents=True, exist_ok=True)
        for geo in sorted(geo_root.rglob("*.geo.json")):
            models += 1
            relative = geo.relative_to(geo_root)
            stem = geo.name.removesuffix(".geo.json")
            target = animation_root / relative.parent / f"{stem}.animation.json"
            bones = geometry_bones(geo)
            if not target.is_file():
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(
                    json.dumps(animation_document(bones), ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8",
                )
                generated += 1
                print(f"GENERATE {namespace}:{relative.as_posix()}")

            document = json.loads(target.read_text(encoding="utf-8-sig"))
            clips = document.setdefault("animations", {})
            if not isinstance(clips, dict):
                clips = document["animations"] = {}
            before = json.dumps(document, sort_keys=True)
            dropped = drop_unknown_bones(document, bones)
            added = ensure_aliases(clips, bones)
            if dropped:
                dropped_total += len(dropped)
                print(f"DROP {namespace}:{stem}: {', '.join(dropped)}")
            if added:
                aliases += len(added)
                print(f"ALIAS {namespace}:{stem}: {', '.join(added)}")
            if json.dumps(document, sort_keys=True) != before:
                target.write_text(
                    json.dumps(document, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8",
                )
    print(
        f"Round-three animations ensured for {models} models: {generated} generated, "
        f"{aliases} aliases added, {dropped_total} channels of missing bones removed"
    )


if __name__ == "__main__":
    main()

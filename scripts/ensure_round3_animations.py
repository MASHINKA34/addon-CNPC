#!/usr/bin/env python3
"""Ensure every round-three model has a paired animation file with an idle clip.

For each geometry of the four donor namespaces this script:

* writes a portable animation file when the donor shipped none for that stem;
* removes channels that address bones the geometry does not contain (props and
  effects that the donor builds at runtime), after
  ``normalize_animation_bones.py`` has already renamed the ones that only differ
  in punctuation or case;
* adds an ``idle`` alias when no clip carries that name, or when the clip of
  that name moves no bone while another clip does (the converter's empty
  placeholder, or a donor clip whose bones were all dropped): copied from the
  donor's idle-like clip when one can be recognised and otherwise from the
  first clip that moves bones;
* adds ``walk``/``attack`` aliases only when a donor clip clearly is one.

The action vocabulary below is shared with ``convert_round3_java_models.py``,
which writes its portable placeholder loops only for the actions no authored
clip covers, so an alias never competes with a placeholder of the same name.
A model whose every clip is static (the hydra body and segments of
Dragonforged, the icefairy of Threateningly Mobs) keeps its static ``idle``
and is listed as static in the report. Missing walk or attack clips are not an
error; they are listed in the report.
"""

from __future__ import annotations

import copy
import json
from pathlib import Path

from convert_round2_java_models import animation_document


PROJECT_DIR = Path(__file__).resolve().parent.parent
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"
NAMESPACES = ("eeeabsmobs", "dragonforged", "threateningly_mobs", "metus_oblita",)

IDLE_KEYWORDS = ("idle", "ambient", "rest", "stand", "default", "breathing", "float", "hover")
WALK_KEYWORDS = ("walk", "run", "move", "swim", "fly", "crawl", "slither", "chase")
ATTACK_KEYWORDS = ("attack", "atk", "bite", "shoot", "strike", "slash", "hit", "melee", "punch", "smash", "swing")
STANDARD_CLIPS = ("idle", "walk", "attack")
STANDARD_KEYWORDS = {"idle": IDLE_KEYWORDS, "walk": WALK_KEYWORDS, "attack": ATTACK_KEYWORDS}


def words(name: str) -> list[str]:
    """Lower-case letter runs of a clip name.

    ``saintp1_idle`` gives ``saintp`` and ``idle``; ``atk2`` and ``attack_1``
    give ``atk`` and ``attack``, so numbered variants read as their action.
    """
    normalized = "".join(character.lower() if character.isalpha() else " " for character in name)
    return normalized.split()


def has_channels(clip: object) -> bool:
    """True when the clip moves at least one bone (rotation, position, or scale keyframes)."""
    channels = clip.get("bones") if isinstance(clip, dict) else None
    if not isinstance(channels, dict):
        return False
    return any(
        isinstance(channel, dict) and any(channel.get(key) for key in ("rotation", "position", "scale"))
        for channel in channels.values()
    )


def matching_clip(clips: dict, keywords: tuple[str, ...], exclude: tuple[str, ...] = ()) -> tuple[str, dict] | None:
    """The first clip that moves bones and carries one of the keywords, in keyword order."""
    for keyword in keywords:
        for name, clip in clips.items():
            if name not in exclude and has_channels(clip) and keyword in words(name):
                return name, clip
    return None


def first_moving_clip(clips: dict, exclude: tuple[str, ...] = ()) -> tuple[str, dict] | None:
    for name, clip in clips.items():
        if name not in exclude and has_channels(clip):
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


def alias(clips: dict, name: str, source: tuple[str, dict], loop: bool) -> str:
    source_name, source_clip = source
    clip = copy.deepcopy(source_clip)
    if loop:
        clip["loop"] = True
    clips[name] = clip
    return f"{name}<-{source_name}"


def ensure_aliases(clips: dict, bones: set[str]) -> list[str]:
    added: list[str] = []
    if not clips:
        clips.update(animation_document(bones)["animations"])
        added.append("idle(generated)")
        return added
    if "idle" not in clips:
        found = matching_clip(clips, IDLE_KEYWORDS) or first_moving_clip(clips) or next(iter(clips.items()))
        added.append(alias(clips, "idle", found, loop=True))
    elif not has_channels(clips["idle"]):
        # A static idle (the converter's empty placeholder, or a donor clip
        # whose bones were all dropped) gives way to a clip that moves; when
        # every other clip is static too, the model keeps it and is reported
        # as static.
        found = matching_clip(clips, IDLE_KEYWORDS, STANDARD_CLIPS) or first_moving_clip(clips, STANDARD_CLIPS)
        if found:
            added.append(alias(clips, "idle", found, loop=True))
    if "walk" not in clips:
        found = matching_clip(clips, WALK_KEYWORDS, STANDARD_CLIPS)
        if found:
            added.append(alias(clips, "walk", found, loop=True))
    if "attack" not in clips:
        found = matching_clip(clips, ATTACK_KEYWORDS, STANDARD_CLIPS)
        if found:
            added.append(alias(clips, "attack", found, loop=False))
    return added


def main() -> None:
    totals = {"models": 0, "generated": 0, "dropped": 0, "idle": 0, "walk": 0, "attack": 0}
    for namespace in NAMESPACES:
        geo_root = ASSET_ROOT / namespace / "geo"
        animation_root = ASSET_ROOT / namespace / "animations"
        if not geo_root.is_dir():
            continue
        animation_root.mkdir(parents=True, exist_ok=True)
        counts = dict.fromkeys(totals, 0)
        static: list[str] = []
        for geo in sorted(geo_root.rglob("*.geo.json")):
            counts["models"] += 1
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
                counts["generated"] += 1
                print(f"GENERATE {namespace}:{relative.as_posix()}")

            document = json.loads(target.read_text(encoding="utf-8-sig"))
            clips = document.setdefault("animations", {})
            if not isinstance(clips, dict):
                clips = document["animations"] = {}
            before = json.dumps(document, sort_keys=True)
            dropped = drop_unknown_bones(document, bones)
            added = ensure_aliases(clips, bones)
            if dropped:
                counts["dropped"] += len(dropped)
                print(f"DROP {namespace}:{stem}: {', '.join(dropped)}")
            if added:
                for entry in added:
                    counts[entry.split("<-")[0].split("(")[0]] += 1
                print(f"ALIAS {namespace}:{stem}: {', '.join(added)}")
            if not has_channels(clips["idle"]):
                static.append(stem)
            if json.dumps(document, sort_keys=True) != before:
                target.write_text(
                    json.dumps(document, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8",
                )
        print(
            f"{namespace}: {counts['models']} models, {counts['generated']} generated, "
            f"{counts['idle']} idle / {counts['walk']} walk / {counts['attack']} attack aliases, "
            f"{counts['dropped']} channels of missing bones removed"
            + (f", static idle: {', '.join(static)}" if static else "")
        )
        for key in totals:
            totals[key] += counts[key]
    print(
        f"Round-three animations ensured for {totals['models']} models: {totals['generated']} generated, "
        f"{totals['idle'] + totals['walk'] + totals['attack']} aliases added "
        f"({totals['idle']} idle, {totals['walk']} walk, {totals['attack']} attack), "
        f"{totals['dropped']} channels of missing bones removed"
    )


if __name__ == "__main__":
    main()

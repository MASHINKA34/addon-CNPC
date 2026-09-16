#!/usr/bin/env python3
"""Convert the Java-rendered creatures of donor batch three to standalone GeckoLib JSON.

EEEAB's Mobs and Threateningly Mobs ship no GeckoLib geometry; Metus Oblita
renders two of its creatures with Java models as well. All of them are vanilla
``ModelPart`` layers built by Blockbench, so their cubes, pivots, and UVs are
read from the decompiled ``LayerDefinition`` builders and written as GeckoLib
geometry. Authored keyframes are converted where the donor has them:

* Threateningly Mobs and Metus Oblita use vanilla ``AnimationDefinition``
  classes under SRG names (MCreator output for Forge 1.20.1).
* EEEAB's Mobs carries its own copy of the keyframe classes under readable
  names; its models also apply static rest poses in ``setupAnim`` that this
  converter bakes into the bone rotations, because GeckoLib adds keyframes to
  the geometry pose exactly the way the donor adds them to the static one.

Procedural Java motion (sine-based walking, look-at) has no keyframes, so every
model also gets the portable idle, walk, and attack loops of the round-two
converter; authored clips of the same name win. No donor class is packaged.
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from pathlib import Path

from convert_java_modelparts import (
    Part,
    matching_paren,
    number,
    parse_animations as parse_srg_animations,
    split_args,
)
from convert_round2_java_models import (
    animation_document,
    clean_vector,
    constants,
    parse_builder_flexible,
    parse_named_animation_definitions,
    parse_pose,
    parts_to_geometry,
    write_json,
)


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
DECOMPILED = WORK_DIR / "donor_decompiled_round3"
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"

EEEAB_ROOT = "01_eeeabs_mobs/com/eeeab/eeeabsmobs/client/model"
THREATENINGLY_ROOT = "03_threateningly_mobs/net/mcreator/threateninglymobs/client/model"
METUS_ROOT = "04_metus_oblita/net/mcreator/metusoblita/client/model"


@dataclass(frozen=True)
class JavaModel:
    """One converted creature.

    ``java_class`` is the model class name; ``layer_method`` names the static
    ``LayerDefinition`` factory that builds the geometry, because EEEAB's
    ``ModelCorpse`` carries two of them. ``texture`` is the default sheet the
    donor renderer draws with, relative to the namespace. ``animation_class``
    is the MCreator animation holder (SRG keyframes); EEEAB's models name the
    clips they play in ``setupAnim`` and are resolved from there.
    """

    root: str
    java_class: str
    namespace: str
    identifier: str
    texture: str
    layer_method: str = "createBodyLayer"
    animation_class: str | None = None
    clip_prefix: str = ""


def eeeab(java_class: str, identifier: str, texture: str, layer_method: str = "createBodyLayer") -> JavaModel:
    return JavaModel(
        EEEAB_ROOT,
        f"entity/{java_class}",
        "eeeabsmobs",
        identifier,
        f"textures/entity/{texture}.png",
        layer_method,
    )


def threateningly(java_class: str, identifier: str, texture: str) -> JavaModel:
    stem = java_class.removeprefix("Model")
    return JavaModel(
        THREATENINGLY_ROOT,
        java_class,
        "threateningly_mobs",
        identifier,
        f"textures/entities/{texture}.png",
        animation_class=f"animations/{stem}Animation",
        clip_prefix=f"{stem.lower()}_",
    )


def metus(java_class: str, identifier: str, texture: str) -> JavaModel:
    stem = java_class.removeprefix("Model")
    return JavaModel(
        METUS_ROOT,
        java_class,
        "metus_oblita",
        identifier,
        f"textures/entities/{texture}.png",
        animation_class=f"animations/{stem}Animation",
        clip_prefix=f"{stem.lower()}_",
    )


MODELS: tuple[JavaModel, ...] = (
    # EEEAB's Mobs 0.98.1: every creature renderer and the sheet it binds.
    # Projectiles, effects, armor, and held-item models are not converted.
    eeeab("ModelCorpse", "java_corpse", "corpse"),
    eeeab("ModelCorpse", "java_corpse_villager", "corpse_villager", "createVillagerBodyLayer"),
    eeeab("ModelCorpseWarlock", "java_corpse_warlock", "corpse_warlock"),
    eeeab("ModelAbsImmortalSkeleton", "java_immortal_skeleton", "immortal_skeleton"),
    eeeab("ModelImmortalShaman", "java_immortal_shaman", "immortal_shaman"),
    eeeab("ModelImmortalExecutioner", "java_immortal_executioner", "immortal_executioner"),
    eeeab("ModelMagicGolem", "java_immortal_golem", "immortal_golem"),
    eeeab("ModelImmortal", "java_immortal", "immortal"),
    eeeab("ModelNamelessGuardian", "java_nameless_guardian", "nameless_guardian"),
    eeeab("ModelRealmWarden", "java_realm_warden", "realm_warden"),
    eeeab("ModelRelicObserver", "java_relic_observer", "relic_observer"),
    eeeab("ModelRelicRipper", "java_relic_ripper", "relic_ripper"),
    eeeab("ModelRelicEarthshaker", "java_relic_earthshaker", "relic_earthshaker"),
    eeeab("ModelRelicAnnihilator", "java_relic_annihilator", "relic_annihilator"),
    eeeab("ModelTester", "java_tester", "tester"),
    eeeab("ModelUnKnown", "java_unknown", "unknown"),
)


def java_texture_mappings() -> dict[str, str]:
    """Model resource -> default texture resource for every converted creature."""
    return {
        f"{model.namespace}:geo/{model.identifier}.geo.json": f"{model.namespace}:{model.texture}"
        for model in MODELS
    }


def method_body(source: str, name: str) -> str:
    match = re.search(rf"\b{re.escape(name)}\s*\([^)]*\)\s*\{{", source)
    if not match:
        raise ValueError(f"method {name} not found")
    opening = match.end() - 1
    depth = 0
    in_string = False
    escaped = False
    for index in range(opening, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise ValueError(f"unbalanced braces in {name}")


def top_level_statements(body: str) -> list[str]:
    statements: list[str] = []
    depth = 0
    start = 0
    for index, char in enumerate(body):
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            start = index + 1
        elif char == ";" and depth == 0:
            statements.append(body[start:index].strip())
            start = index + 1
    return statements


def parse_layer_geometry(source: str, layer_method: str, identifier: str) -> tuple[dict, set[str], dict[str, int]]:
    body = method_body(source, layer_method)
    layer = re.search(
        r"LayerDefinition\.(?:create|m_171565_)\([^;]+?,\s*\(int\)?\s*(\d+),\s*\(int\)?\s*(\d+)\)",
        body,
        re.S,
    )
    if not layer:
        raise ValueError(f"texture size not found in {layer_method}")
    texture_width, texture_height = int(layer.group(1)), int(layer.group(2))
    string_values, numeric_values, builders = constants(source)

    call_pattern = re.compile(
        r"(?:(?:PartDefinition)\s+([A-Za-z0-9_]+)\s*=\s*)?"
        r"([A-Za-z0-9_]+)\.(?:addOrReplaceChild|m_171599_)\("
    )
    parts: list[Part] = []
    variables: set[str] = set()
    used_names: dict[str, int] = {}
    for index, match in enumerate(call_pattern.finditer(body)):
        variable, parent_variable = match.group(1), match.group(2)
        opening = match.end() - 1
        closing = matching_paren(body, opening)
        args = split_args(body[opening + 1 : closing])
        if len(args) < 3:
            continue
        raw_name = args[0].strip()
        if raw_name.startswith('"') and raw_name.endswith('"'):
            bone_name = raw_name[1:-1]
        else:
            bone_name = string_values.get(raw_name, raw_name.lower())
        count = used_names.get(bone_name, 0) + 1
        used_names[bone_name] = count
        if count > 1:
            bone_name = f"{bone_name}_{count}"
        variable = variable or f"__part_{index}"
        parent = parent_variable if parent_variable in variables else None
        try:
            offset, rotation = parse_pose(args[2], numeric_values)
        except (ValueError, SyntaxError):
            offset, rotation = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
        parts.append(
            Part(
                variable,
                bone_name,
                parent,
                offset,
                rotation,
                parse_builder_flexible(args[1], numeric_values, builders),
            )
        )
        variables.add(variable)
    if not parts:
        raise ValueError(f"no model parts parsed from {layer_method}")
    return parts_to_geometry(parts, identifier, texture_width, texture_height)


def static_pose(source: str) -> dict[str, list[float]]:
    """Unconditional ``setStaticRotationAngle`` calls of ``setupAnim`` by bone name."""
    try:
        body = method_body(source, "setupAnim")
    except ValueError:
        return {}
    fields = dict(re.findall(r"this\.([A-Za-z0-9_]+)\s*=\s*[A-Za-z0-9_.]+\.(?:getChild|m_171324_)\(\"([^\"]+)\"\)", source))
    pose: dict[str, list[float]] = {}
    for statement in top_level_statements(body):
        match = re.fullmatch(
            r"(?:this\.)?setStaticRotationAngle\(this\.([A-Za-z0-9_]+),\s*(.+)\)", statement, re.S
        )
        if not match:
            continue
        try:
            values = [number(item) for item in split_args(match.group(2))]
        except (ValueError, SyntaxError):
            continue
        if len(values) != 3:
            continue
        bone = fields.get(match.group(1), match.group(1))
        current = pose.setdefault(bone, [0.0, 0.0, 0.0])
        for axis in range(3):
            current[axis] += values[axis]
    return pose


def bake_static_pose(geometry: dict, pose: dict[str, list[float]]) -> int:
    baked = 0
    for bone in geometry["minecraft:geometry"][0]["bones"]:
        degrees = pose.get(bone["name"])
        if not degrees:
            continue
        rotation = list(bone.get("rotation", [0, 0, 0]))
        rotation[0] -= degrees[0]
        rotation[1] -= degrees[1]
        rotation[2] += degrees[2]
        bone["rotation"] = clean_vector(rotation)
        baked += 1
    return baked


def eeeab_authored_clips(source: str, model_dir: Path, bones: set[str]) -> dict[str, dict]:
    """The clips a model plays in ``setupAnim``, read from the animation holders."""
    references = sorted(set(re.findall(r"\b(Animation[A-Za-z0-9]+)\.([A-Z][A-Z0-9_]*)\b", source)))
    cache: dict[str, dict[str, dict]] = {}
    clips: dict[str, dict] = {}
    for holder, field in references:
        holder_path = model_dir.parent / "animation" / f"{holder}.java"
        if not holder_path.is_file():
            continue
        if holder not in cache:
            cache[holder] = parse_named_animation_definitions(holder_path, bones)
        clip = cache[holder].get(field.lower())
        if clip is not None:
            clips[field.lower()] = clip
    return clips


def mcreator_authored_clips(model: JavaModel, bones: set[str]) -> dict[str, dict]:
    if not model.animation_class:
        return {}
    path = DECOMPILED / model.root / f"{model.animation_class}.java"
    if not path.is_file():
        return {}
    document, _ = parse_srg_animations(path, bones)
    clips: dict[str, dict] = {}
    for name, clip in document["animations"].items():
        lowered = name.lower()
        if model.clip_prefix and lowered.startswith(model.clip_prefix) and len(lowered) > len(model.clip_prefix):
            lowered = lowered[len(model.clip_prefix):]
        clips[lowered] = clip
    return clips


def convert(model: JavaModel) -> dict[str, int]:
    source_path = DECOMPILED / model.root / f"{model.java_class}.java"
    source = source_path.read_text(encoding="utf-8", errors="replace")
    geometry, bones, stats = parse_layer_geometry(source, model.layer_method, model.identifier)

    baked = 0
    if model.namespace == "eeeabsmobs":
        baked = bake_static_pose(geometry, static_pose(source))
        authored = eeeab_authored_clips(source, source_path.parent, bones)
    else:
        authored = mcreator_authored_clips(model, bones)

    animations = animation_document(bones)
    animations["animations"].update(authored)

    namespace_root = ASSET_ROOT / model.namespace
    write_json(namespace_root / "geo" / f"{model.identifier}.geo.json", geometry)
    write_json(namespace_root / "animations" / f"{model.identifier}.animation.json", animations)
    return stats | {"authored": len(authored), "baked": baked}


def main() -> None:
    failures: list[tuple[JavaModel, Exception]] = []
    for model in MODELS:
        try:
            stats = convert(model)
        except Exception as exc:  # noqa: BLE001 - every failure is reported below
            failures.append((model, exc))
            print(f"FAILED {model.namespace}:{model.identifier}: {exc}")
            continue
        print(
            f"{model.namespace}:{model.identifier}: {stats['bones']} bones, {stats['cubes']} cubes, "
            f"{stats['authored']} authored clips"
            + (f", {stats['baked']} static pose bones" if stats["baked"] else "")
        )
    if failures:
        raise SystemExit(f"{len(failures)} Java models failed conversion")


if __name__ == "__main__":
    main()

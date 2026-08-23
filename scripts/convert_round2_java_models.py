#!/usr/bin/env python3
"""Convert the Java-rendered mobs in donor batch two to standalone Gecko JSON.

The converter understands named Mojang classes, intermediary Fabric names,
Blockbench/MCreator output, and the helper format used by Sculk Infection.
Procedural Java motion is represented by portable idle/walk/attack loops; authored
``AnimationDefinition`` keyframes are imported when they are present.
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass, field
from pathlib import Path

from convert_java_modelparts import (
    Cube,
    Part,
    clean_number,
    clean_vector,
    matching_paren,
    number,
    split_args,
)


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
DECOMPILED = WORK_DIR / "donor_decompiled_round2"
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"


@dataclass(frozen=True)
class JavaModel:
    donor: str
    relative: str
    namespace: str
    identifier: str


MODELS = (
    JavaModel("01_blighted_beasts", "blueduck/blighted_beasts/client/model/BloaterModel.java", "blighted_beasts", "java_bloater"),
    JavaModel("01_blighted_beasts", "blueduck/blighted_beasts/client/model/GrandSkitterModel.java", "blighted_beasts", "java_grand_skitter"),
    JavaModel("01_blighted_beasts", "blueduck/blighted_beasts/client/model/ReaperModel.java", "blighted_beasts", "java_reaper"),
    JavaModel("01_blighted_beasts", "blueduck/blighted_beasts/client/model/ReverbModel.java", "blighted_beasts", "java_reverb"),
    JavaModel("01_blighted_beasts", "blueduck/blighted_beasts/client/model/SeerModel.java", "blighted_beasts", "java_seer"),
    JavaModel("01_blighted_beasts", "blueduck/blighted_beasts/client/model/UnseenModel.java", "blighted_beasts", "java_unseen"),

    JavaModel("03b_deeper_darker_latest", "com/kyanite/deeperdarker/client/model/AnglerFishModel.java", "deeperdarker", "java_angler_fish"),
    JavaModel("03b_deeper_darker_latest", "com/kyanite/deeperdarker/client/model/AngerPotModel.java", "deeperdarker", "java_anger_pot"),
    JavaModel("03b_deeper_darker_latest", "com/kyanite/deeperdarker/client/model/FearPotModel.java", "deeperdarker", "java_fear_pot"),
    JavaModel("03b_deeper_darker_latest", "com/kyanite/deeperdarker/client/model/SorrowPotModel.java", "deeperdarker", "java_sorrow_pot"),
    JavaModel("03b_deeper_darker_latest", "com/kyanite/deeperdarker/client/model/SludgeModel.java", "deeperdarker", "java_sludge"),

    JavaModel("05_dungeons_2_mobs", "net/mcreator/minecraftdungendtwomobs/client/model/ModelSculk_eye.java", "minecraft_dungend_two_mobs", "java_sculk_eye"),
    JavaModel("05_dungeons_2_mobs", "net/mcreator/minecraftdungendtwomobs/client/model/ModelSculk_great_hunger.java", "minecraft_dungend_two_mobs", "java_sculk_hunger"),
    JavaModel("05_dungeons_2_mobs", "net/mcreator/minecraftdungendtwomobs/client/model/Modelsculklings.java", "minecraft_dungend_two_mobs", "java_sculklings"),
    JavaModel("05_dungeons_2_mobs", "net/mcreator/minecraftdungendtwomobs/client/model/Modelthe_singer.java", "minecraft_dungend_two_mobs", "java_the_singer"),
    JavaModel("06_echoes", "com/mrbysco/echoes/client/model/EchoCreeperModel.java", "echoes", "java_echo_creeper"),
    JavaModel("07_nether_update_expanded", "net/mcreator/netherupdateexpanded/client/model/ModelCinderworm.java", "nue", "java_cinderworm"),
    JavaModel("07_nether_update_expanded", "net/mcreator/netherupdateexpanded/client/model/ModelFlooze.java", "nue", "java_flooze"),

    JavaModel("09_betternether", "org/betterx/betternether/entity/model/ModelEntityFirefly.java", "betternether", "java_firefly"),
    JavaModel("09_betternether", "org/betterx/betternether/entity/model/ModelEntityFlyingPig.java", "betternether", "java_flying_pig"),
    JavaModel("09_betternether", "org/betterx/betternether/entity/model/ModelNaga.java", "betternether", "java_naga"),
    JavaModel("09_betternether", "org/betterx/betternether/entity/model/ModelSkull.java", "betternether", "java_skull"),

    JavaModel("13_moss_and_monsters", "net/mcreator/ecosystemmod/client/model/Modelchomper.java", "ecosystemmod", "java_chomper"),
    JavaModel("13_moss_and_monsters", "net/mcreator/ecosystemmod/client/model/Modelcrabbutbig.java", "ecosystemmod", "java_bibcrab"),
    JavaModel("13_moss_and_monsters", "net/mcreator/ecosystemmod/client/model/Modelmossmuncher.java", "ecosystemmod", "java_mossmuncher"),
    JavaModel("13_moss_and_monsters", "net/mcreator/ecosystemmod/client/model/Modelmudskipper.java", "ecosystemmod", "java_mudskipper"),
    JavaModel("13_moss_and_monsters", "net/mcreator/ecosystemmod/client/model/Modelnibbler.java", "ecosystemmod", "java_nibbler"),
    JavaModel("13_moss_and_monsters", "net/mcreator/ecosystemmod/client/model/Modelrockcrab.java", "ecosystemmod", "java_rockcrab"),
    JavaModel("13_moss_and_monsters", "net/mcreator/ecosystemmod/client/model/Modeltreespirit.java", "ecosystemmod", "java_treespirit"),

    JavaModel("15_agers_mosslings", "net/mcreator/mosslings_muddlings/client/model/ModelMossling_hawk.java", "mosslings_muddlings", "java_mossling_hawk"),
    JavaModel("15_agers_mosslings", "net/mcreator/mosslings_muddlings/client/model/Modelmossling_1.java", "mosslings_muddlings", "java_mossling"),
    JavaModel("15_agers_mosslings", "net/mcreator/mosslings_muddlings/client/model/Modelmossling_archer.java", "mosslings_muddlings", "java_mossling_archer"),
    JavaModel("15_agers_mosslings", "net/mcreator/mosslings_muddlings/client/model/Modelmossling_brute.java", "mosslings_muddlings", "java_mossling_brute"),
    JavaModel("15_agers_mosslings", "net/mcreator/mosslings_muddlings/client/model/Modelmossling_horse.java", "mosslings_muddlings", "java_mossling_horse"),
    JavaModel("15_agers_mosslings", "net/mcreator/mosslings_muddlings/client/model/Modelmossling_warrior.java", "mosslings_muddlings", "java_mossling_warrior"),
    JavaModel("15_agers_mosslings", "net/mcreator/mosslings_muddlings/client/model/Modelstickling.java", "mosslings_muddlings", "java_stickling"),

    JavaModel("16_the_undergarden", "quek/undergarden/client/model/BruteModel.java", "undergarden", "java_brute"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/DenizenModel.java", "undergarden", "java_denizen"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/Denizen2Model.java", "undergarden", "java_denizen2"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/DwellerModel.java", "undergarden", "java_dweller"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/ForgottenGuardianModel.java", "undergarden", "java_forgotten_guardian"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/GloomperModel.java", "undergarden", "java_gloomper"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/GreaterDwellerModel.java", "undergarden", "java_greater_dweller"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/GwibModel.java", "undergarden", "java_gwib"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/GwiblingModel.java", "undergarden", "java_gwibling"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/MinionModel.java", "undergarden", "java_minion"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/MogModel.java", "undergarden", "java_mog"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/MuncherModel.java", "undergarden", "java_muncher"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/MysteriousPotModel.java", "undergarden", "java_mysterious_pot"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/NargoyleModel.java", "undergarden", "java_nargoyle"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/RotbeastModel.java", "undergarden", "java_rotbeast"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/RotbelcherModel.java", "undergarden", "java_rotbelcher"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/RotlingModel.java", "undergarden", "java_rotling"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/RotwalkerModel.java", "undergarden", "java_rotwalker"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/ScintlingModel.java", "undergarden", "java_scintling"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/SmogMogModel.java", "undergarden", "java_smog_mog"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/SploogieModel.java", "undergarden", "java_sploogie"),
    JavaModel("16_the_undergarden", "quek/undergarden/client/model/StonebornModel.java", "undergarden", "java_stoneborn"),
)


def constants(source: str) -> tuple[dict[str, str], dict[str, float], dict[str, str]]:
    string_values = dict(re.findall(r"(?:String\s+)?([A-Z][A-Z0-9_]*)\s*=\s*\"([^\"]+)\"", source))
    numeric_values: dict[str, float] = {}
    for name, expression in re.findall(
        r"(?:float|double|int)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^;]+);", source
    ):
        try:
            numeric_values[name] = number(expression)
        except Exception:
            pass
    for name, expression in re.findall(
        r"CubeDeformation\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*new CubeDeformation\(([^)]+)\)", source
    ):
        try:
            numeric_values[name] = number(expression)
        except Exception:
            pass
    builders = dict(
        re.findall(
            r"(?:CubeListBuilder|class_5606)\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([^;]+);",
            source,
        )
    )
    return string_values, numeric_values, builders


def eval_number(expression: str, values: dict[str, float]) -> float:
    cleaned = expression.strip()
    cleaned = re.sub(r"\((?:float|double|int)\)", "", cleaned).strip()
    if cleaned in values:
        return values[cleaned]
    for name, value in sorted(values.items(), key=lambda item: -len(item[0])):
        cleaned = re.sub(rf"\b{re.escape(name)}\b", repr(value), cleaned)
    return number(cleaned)


def parse_builder_flexible(builder: str, values: dict[str, float], builders: dict[str, str]) -> list[Cube]:
    builder = builders.get(builder.strip(), builder)
    uv = [0.0, 0.0]
    mirror = False
    cubes: list[Cube] = []
    method_pattern = re.compile(
        r"\.(texOffs|m_171514_|method_32101|addBox|m_171488_|method_32097|method_32098|"
        r"mirror|m_171480_|m_171555_|method_32106)\("
    )
    for match in method_pattern.finditer(builder):
        opening = match.end() - 1
        closing = matching_paren(builder, opening)
        method = match.group(1)
        args_text = builder[opening + 1 : closing]
        if method in {"texOffs", "m_171514_", "method_32101"}:
            args = split_args(args_text)
            try:
                uv = [eval_number(args[0], values), eval_number(args[1], values)]
            except (IndexError, ValueError, SyntaxError):
                pass
        elif method in {"mirror", "m_171480_", "m_171555_", "method_32106"}:
            mirror = not args_text.strip() or args_text.strip().lower() != "false"
        else:
            args = split_args(args_text)
            if len(args) < 6:
                continue
            try:
                coordinates = [eval_number(item, values) for item in args[:6]]
            except (ValueError, SyntaxError):
                continue
            inflate = 0.0
            if len(args) >= 7:
                deformation = re.search(r"(?:CubeDeformation|class_5605)\((.+)\)", args[6])
                if deformation:
                    try:
                        inflate = eval_number(deformation.group(1), values)
                    except (ValueError, SyntaxError):
                        inflate = 0.0
                elif args[6].strip() in values:
                    inflate = values[args[6].strip()]
            cubes.append(Cube(coordinates[:3], coordinates[3:6], uv.copy(), inflate, mirror))
    return cubes


def parse_pose(expression: str, values: dict[str, float]) -> tuple[list[float], list[float]]:
    if expression.strip() in {"PartPose.ZERO", "class_5603.field_27701"}:
        return [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
    match = re.search(
        r"(?:PartPose\.)?(offsetAndRotation|offset|m_171423_|m_171419_|method_32091|method_32090)\((.*)\)",
        expression,
        re.S,
    )
    if not match:
        return [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
    args = split_args(match.group(2))
    pose = [eval_number(item, values) for item in args]
    offset = (pose + [0.0, 0.0, 0.0])[:3]
    rotation_methods = {"offsetAndRotation", "m_171423_", "method_32091"}
    rotation = (pose[3:] + [0.0, 0.0, 0.0])[:3] if match.group(1) in rotation_methods else [0.0, 0.0, 0.0]
    return offset, rotation


def parse_geometry_flexible(source_path: Path, identifier: str) -> tuple[dict, set[str], dict[str, int]]:
    source = source_path.read_text(encoding="utf-8", errors="replace")
    layer_patterns = (
        r"LayerDefinition\.create\([^;]+?,\s*\(int\)?\s*(\d+),\s*\(int\)?\s*(\d+)\)",
        r"class_5607\.method_32110\([^;]+?,\s*\(int\)?\s*(\d+),\s*\(int\)?\s*(\d+)\)",
        r"LayerDefinition\.(?:create|m_171565_)\([^,]+,\s*\(int\)?\s*(\d+),\s*\(int\)?\s*(\d+)\)",
    )
    layer = next((re.search(pattern, source, re.S) for pattern in layer_patterns if re.search(pattern, source, re.S)), None)
    if not layer:
        raise ValueError(f"Texture size not found in {source_path}")
    texture_width, texture_height = int(layer.group(1)), int(layer.group(2))
    string_values, numeric_values, builders = constants(source)

    call_pattern = re.compile(
        r"(?:(?:PartDefinition|class_5610)\s+([A-Za-z0-9_]+)\s*=\s*)?"
        r"([A-Za-z0-9_]+)\.(?:addOrReplaceChild|m_171599_|method_32117)\("
    )
    parts: list[Part] = []
    variables: set[str] = set()
    used_names: dict[str, int] = {}
    for index, match in enumerate(call_pattern.finditer(source)):
        variable, parent_variable = match.group(1), match.group(2)
        opening = match.end() - 1
        closing = matching_paren(source, opening)
        args = split_args(source[opening + 1 : closing])
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
        raise ValueError(f"No model parts parsed from {source_path}")
    return parts_to_geometry(parts, identifier, texture_width, texture_height)


def parts_to_geometry(parts: list[Part], identifier: str, width: int, height: int) -> tuple[dict, set[str], dict[str, int]]:
    by_variable = {part.variable: part for part in parts}
    pivots: dict[str, list[float]] = {}

    def pivot(part: Part) -> list[float]:
        if part.variable in pivots:
            return pivots[part.variable]
        x, y, z = part.offset
        if part.parent_variable and part.parent_variable in by_variable:
            parent = pivot(by_variable[part.parent_variable])
            result = [parent[0] - x, parent[1] - y, parent[2] + z]
        else:
            result = [-x, 24.0 - y, z]
        pivots[part.variable] = result
        return result

    bones: list[dict] = []
    cube_count = 0
    for part in parts:
        part_pivot = pivot(part)
        bone: dict = {"name": part.name, "pivot": clean_vector(part_pivot)}
        if part.parent_variable and part.parent_variable in by_variable:
            bone["parent"] = by_variable[part.parent_variable].name
        if any(abs(value) > 1.0e-7 for value in part.rotation):
            bone["rotation"] = clean_vector(
                [-math.degrees(part.rotation[0]), -math.degrees(part.rotation[1]), math.degrees(part.rotation[2])]
            )
        converted_cubes = []
        for cube in part.cubes:
            x, y, z = cube.origin
            dx, dy, dz = cube.size
            converted = {
                "origin": clean_vector([part_pivot[0] - (x + dx), part_pivot[1] - (y + dy), part_pivot[2] + z]),
                "size": clean_vector(cube.size),
                "uv": clean_vector(cube.uv),
            }
            if abs(cube.inflate) > 1.0e-7:
                converted["inflate"] = clean_number(cube.inflate)
            if cube.mirror:
                converted["mirror"] = True
            converted_cubes.append(converted)
            cube_count += 1
        if converted_cubes:
            bone["cubes"] = converted_cubes
        bones.append(bone)
    geometry = geometry_document(identifier, width, height, bones)
    return geometry, {bone["name"] for bone in bones}, {"bones": len(bones), "cubes": cube_count}


def geometry_document(identifier: str, width: int, height: int, bones: list[dict]) -> dict:
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{identifier}",
                "texture_width": width,
                "texture_height": height,
            },
            "bones": bones,
        }],
    }


def portable_animation(length: float, bones: dict, loop: bool = True) -> dict:
    result: dict = {"animation_length": length, "bones": bones}
    if loop:
        result["loop"] = True
    return result


def animation_document(bones: set[str]) -> dict:
    lower = {bone: bone.lower() for bone in bones}
    legs = [bone for bone, name in lower.items() if "leg" in name or "foot" in name]
    arms = [bone for bone, name in lower.items() if "arm" in name or "claw" in name]
    wings = [bone for bone, name in lower.items() if "wing" in name]
    tails = [bone for bone, name in lower.items() if "tail" in name or "tendril" in name]
    jaws = [bone for bone, name in lower.items() if "jaw" in name or "mandible" in name or "mouth" in name]
    body = next((bone for bone, name in lower.items() if name in {"body", "root", "torso", "base", "core"}), None)

    idle_bones: dict = {}
    if body:
        idle_bones[body] = {"rotation": {"0": [0, 0, -1.5], "1": [0, 0, 1.5], "2": [0, 0, -1.5]}}
    for tail in tails[:8]:
        idle_bones[tail] = {"rotation": {"0": [0, -8, 0], "1": [0, 8, 0], "2": [0, -8, 0]}}
    for wing in wings[:12]:
        idle_bones[wing] = {"rotation": {"0": [0, 0, -12], "0.5": [0, 0, 12], "1": [0, 0, -12]}}

    walk_bones: dict = {}
    for index, leg in enumerate(legs[:16]):
        a, b = (28, -28) if index % 2 == 0 else (-28, 28)
        walk_bones[leg] = {"rotation": {"0": [a, 0, 0], "0.5": [b, 0, 0], "1": [a, 0, 0]}}
    for index, arm in enumerate(arms[:12]):
        a, b = (-24, 24) if index % 2 == 0 else (24, -24)
        walk_bones[arm] = {"rotation": {"0": [a, 0, 0], "0.5": [b, 0, 0], "1": [a, 0, 0]}}
    for tail in tails[:8]:
        walk_bones[tail] = {"rotation": {"0": [0, -15, 0], "0.5": [0, 15, 0], "1": [0, -15, 0]}}
    for wing in wings[:12]:
        walk_bones[wing] = {"rotation": {"0": [0, 0, -30], "0.25": [0, 0, 30], "0.5": [0, 0, -30], "0.75": [0, 0, 30], "1": [0, 0, -30]}}
    if not walk_bones and body:
        walk_bones[body] = {"position": {"0": [0, 0, 0], "0.5": [0, 0.8, 0], "1": [0, 0, 0]}}

    attack_bones: dict = {}
    for arm in arms[:4]:
        attack_bones[arm] = {"rotation": {"0": [0, 0, 0], "0.2": [-75, 0, 0], "0.55": [20, 0, 0], "0.8": [0, 0, 0]}}
    for jaw in jaws[:8]:
        attack_bones[jaw] = {"rotation": {"0": [0, 0, 0], "0.2": [35, 0, 0], "0.5": [-8, 0, 0], "0.8": [0, 0, 0]}}
    if not attack_bones and body:
        attack_bones[body] = {"position": {"0": [0, 0, 0], "0.2": [0, 0, -2], "0.45": [0, 0, 2], "0.8": [0, 0, 0]}}

    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": portable_animation(2.0, idle_bones),
            "walk": portable_animation(1.0, walk_bones),
            "attack": portable_animation(0.8, attack_bones, False),
        },
    }


def parse_named_animation_definitions(source_path: Path, allowed_bones: set[str]) -> dict[str, dict]:
    source = source_path.read_text(encoding="utf-8", errors="replace")
    animations: dict[str, dict] = {}
    fields = re.finditer(
        r"public static final AnimationDefinition\s+([A-Za-z0-9_]+)\s*=\s*(AnimationDefinition\.Builder\..+?\.build\(\));",
        source,
        re.S,
    )
    for field_match in fields:
        field_name, expression = field_match.groups()
        length_match = re.search(r"withLength\(\s*(?:\(float\))?\s*([^)]+)\)", expression)
        if not length_match:
            continue
        animation: dict = {"animation_length": clean_number(number(length_match.group(1)))}
        if ".looping()" in expression:
            animation["loop"] = True
        animation_bones: dict[str, dict] = {}
        for call_match in re.finditer(r"\.addAnimation\(", expression):
            opening = call_match.end() - 1
            closing = matching_paren(expression, opening)
            args = split_args(expression[opening + 1 : closing])
            if len(args) != 2:
                continue
            bone_name = args[0].strip().strip('"')
            if bone_name not in allowed_bones:
                continue
            channel = args[1]
            target_match = re.search(r"AnimationChannel\.Targets\.(ROTATION|POSITION|SCALE)", channel)
            if not target_match:
                continue
            channel_name = {
                "ROTATION": "rotation",
                "POSITION": "position",
                "SCALE": "scale",
            }[target_match.group(1)]
            track: dict[str, list[int | float]] = {}
            for frame_match in re.finditer(r"new Keyframe\(", channel):
                frame_opening = frame_match.end() - 1
                frame_closing = matching_paren(channel, frame_opening)
                frame_args = split_args(channel[frame_opening + 1 : frame_closing])
                if len(frame_args) < 2:
                    continue
                try:
                    time = number(frame_args[0])
                except Exception:
                    continue
                vector_match = re.search(r"KeyframeAnimations\.(degreeVec|posVec|scaleVec)\(", frame_args[1])
                if not vector_match:
                    continue
                vector_opening = vector_match.end() - 1
                vector_closing = matching_paren(frame_args[1], vector_opening)
                try:
                    vector = [number(item) for item in split_args(frame_args[1][vector_opening + 1 : vector_closing])]
                except Exception:
                    continue
                if channel_name in {"rotation", "position"}:
                    vector = [-vector[0], -vector[1], vector[2]]
                track[f"{time:.6f}".rstrip("0").rstrip(".")] = clean_vector(vector)
            if track:
                animation_bones.setdefault(bone_name, {})[channel_name] = track
        if animation_bones:
            animation["bones"] = animation_bones
            animations[field_name.lower()] = animation
    return animations


def merge_authored_animations(namespace: str, identifier: str, source_path: Path, names: set[str] | None = None) -> None:
    geo_path = ASSET_ROOT / namespace / "geo" / f"{identifier}.geo.json"
    animation_path = ASSET_ROOT / namespace / "animations" / f"{identifier}.animation.json"
    geo = json.loads(geo_path.read_text(encoding="utf-8"))
    bones = {bone["name"] for bone in geo["minecraft:geometry"][0]["bones"]}
    authored = parse_named_animation_definitions(source_path, bones)
    if names is not None:
        authored = {name: value for name, value in authored.items() if name in names}
    document = json.loads(animation_path.read_text(encoding="utf-8"))
    document["animations"].update(authored)
    write_json(animation_path, document)
    print(f"{namespace}:{identifier}: merged {len(authored)} authored AnimationDefinition clips")


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def biped_bones(*, thin: bool = False, piglin: bool = False, tall: bool = False) -> list[dict]:
    arm_width = 2 if thin else 4
    leg_width = 2 if thin else 4
    height = 18 if tall else 12
    leg_height = 18 if tall else 12
    bones = [
        {"name": "body", "pivot": [0, 12, 0], "cubes": [{"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]}]},
        {"name": "head", "pivot": [0, 24, 0], "cubes": [{"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0]}]},
        {"name": "right_arm", "pivot": [-5, 22, 0], "cubes": [{"origin": [-5 - arm_width, 22 - height, -1], "size": [arm_width, height, 2 if thin else 4], "uv": [40, 16]}]},
        {"name": "left_arm", "pivot": [5, 22, 0], "cubes": [{"origin": [5, 22 - height, -1], "size": [arm_width, height, 2 if thin else 4], "uv": [32, 48], "mirror": True}]},
        {"name": "right_leg", "pivot": [-2, 12, 0], "cubes": [{"origin": [-2 - leg_width, 12 - leg_height, -1], "size": [leg_width, leg_height, 2 if thin else 4], "uv": [0, 16]}]},
        {"name": "left_leg", "pivot": [2, 12, 0], "cubes": [{"origin": [2, 12 - leg_height, -1], "size": [leg_width, leg_height, 2 if thin else 4], "uv": [16, 48], "mirror": True}]},
    ]
    if piglin:
        bones[1]["cubes"].extend([
            {"origin": [-2, 26, -5], "size": [4, 3, 1], "uv": [16, 0]},
            {"origin": [-5, 26, -5], "size": [1, 2, 1], "uv": [0, 0]},
            {"origin": [4, 26, -5], "size": [1, 2, 1], "uv": [4, 0]},
        ])
        bones.extend([
            {"name": "right_ear", "parent": "head", "pivot": [-4, 29, 0], "rotation": [0, 0, -30], "cubes": [{"origin": [-10, 27, 0], "size": [6, 5, 1], "uv": [51, 6]}]},
            {"name": "left_ear", "parent": "head", "pivot": [4, 29, 0], "rotation": [0, 0, 30], "cubes": [{"origin": [4, 27, 0], "size": [6, 5, 1], "uv": [39, 6], "mirror": True}]},
        ])
    return bones


def creeper_bones() -> list[dict]:
    return [
        {"name": "body", "pivot": [0, 18, 0], "cubes": [{"origin": [-4, 6, -2], "size": [8, 12, 4], "uv": [16, 16]}]},
        {"name": "head", "pivot": [0, 24, 0], "cubes": [{"origin": [-4, 16, -4], "size": [8, 8, 8], "uv": [0, 0]}]},
        {"name": "right_front_leg", "pivot": [-2, 6, -2], "cubes": [{"origin": [-4, 0, -4], "size": [4, 6, 4], "uv": [0, 16]}]},
        {"name": "left_front_leg", "pivot": [2, 6, -2], "cubes": [{"origin": [0, 0, -4], "size": [4, 6, 4], "uv": [0, 16], "mirror": True}]},
        {"name": "right_hind_leg", "pivot": [-2, 6, 2], "cubes": [{"origin": [-4, 0, 0], "size": [4, 6, 4], "uv": [0, 16]}]},
        {"name": "left_hind_leg", "pivot": [2, 6, 2], "cubes": [{"origin": [0, 0, 0], "size": [4, 6, 4], "uv": [0, 16], "mirror": True}]},
    ]


def cow_bones() -> list[dict]:
    return [
        {"name": "body", "pivot": [0, 11, 2], "rotation": [90, 0, 0], "cubes": [{"origin": [-6, 5, -8], "size": [12, 10, 18], "uv": [18, 4]}]},
        {"name": "head", "pivot": [0, 18, -8], "cubes": [{"origin": [-4, 12, -14], "size": [8, 8, 6], "uv": [0, 0]}, {"origin": [-5, 18, -11], "size": [1, 3, 1], "uv": [22, 0]}, {"origin": [4, 18, -11], "size": [1, 3, 1], "uv": [22, 0]}]},
        {"name": "right_front_leg", "pivot": [-4, 10, -5], "cubes": [{"origin": [-6, 0, -7], "size": [4, 10, 4], "uv": [0, 16]}]},
        {"name": "left_front_leg", "pivot": [4, 10, -5], "cubes": [{"origin": [2, 0, -7], "size": [4, 10, 4], "uv": [0, 16], "mirror": True}]},
        {"name": "right_hind_leg", "pivot": [-4, 10, 7], "cubes": [{"origin": [-6, 0, 5], "size": [4, 10, 4], "uv": [0, 16]}]},
        {"name": "left_hind_leg", "pivot": [4, 10, 7], "cubes": [{"origin": [2, 0, 5], "size": [4, 10, 4], "uv": [0, 16], "mirror": True}]},
    ]


def spider_bones() -> list[dict]:
    bones = [
        {"name": "head", "pivot": [0, 15, -3], "cubes": [{"origin": [-4, 11, -7], "size": [8, 8, 8], "uv": [32, 4]}]},
        {"name": "body", "pivot": [0, 15, 2], "cubes": [{"origin": [-3, 12, -1], "size": [6, 6, 6], "uv": [0, 0]}]},
        {"name": "rear", "pivot": [0, 15, 7], "cubes": [{"origin": [-5, 11, 3], "size": [10, 8, 12], "uv": [0, 12]}]},
    ]
    for index in range(8):
        side = -1 if index % 2 == 0 else 1
        row = index // 2
        bones.append({
            "name": f"leg_{index + 1}",
            "pivot": [side * 4, 15, row * 2 - 3],
            "rotation": [0, side * (35 - row * 5), side * 35],
            "cubes": [{"origin": [side * 4 if side > 0 else -20, 14, row * 2 - 4], "size": [16, 2, 2], "uv": [18, 0], "mirror": side > 0}],
        })
    return bones


def enderman_bones() -> list[dict]:
    bones = biped_bones(thin=True, tall=True)
    bones[0]["cubes"][0] = {"origin": [-4, 18, -2], "size": [8, 12, 4], "uv": [32, 16]}
    bones[1]["pivot"] = [0, 30, 0]
    bones[1]["cubes"][0] = {"origin": [-4, 22, -4], "size": [8, 8, 8], "uv": [0, 0]}
    return bones


def bat_bones() -> list[dict]:
    return [
        {"name": "body", "pivot": [0, 15, 0], "cubes": [{"origin": [-3, 9, -2], "size": [6, 12, 5], "uv": [0, 0]}]},
        {"name": "head", "pivot": [0, 20, -1], "cubes": [{"origin": [-3, 17, -4], "size": [6, 6, 6], "uv": [0, 17]}]},
        {"name": "right_ear", "parent": "head", "pivot": [-2, 23, -1], "cubes": [{"origin": [-4, 23, -2], "size": [3, 4, 1], "uv": [24, 0]}]},
        {"name": "left_ear", "parent": "head", "pivot": [2, 23, -1], "cubes": [{"origin": [1, 23, -2], "size": [3, 4, 1], "uv": [24, 0], "mirror": True}]},
        {"name": "right_wing", "parent": "body", "pivot": [-3, 18, 0], "cubes": [{"origin": [-13, 12, 0], "size": [10, 6, 1], "uv": [0, 32]}]},
        {"name": "left_wing", "parent": "body", "pivot": [3, 18, 0], "cubes": [{"origin": [3, 12, 0], "size": [10, 6, 1], "uv": [0, 32], "mirror": True}]},
    ]


def hydrogen_jellyfish_bones() -> list[dict]:
    bones = [
        {"name": "body", "pivot": [0, 72, 0], "cubes": [{"origin": [-14, 54, -14], "size": [28, 18, 28], "uv": [0, 0]}]},
        {"name": "body_top", "parent": "body", "pivot": [0, 78, 0], "cubes": [{"origin": [-10, 72, -10], "size": [20, 6, 20], "uv": [0, 46]}]},
    ]
    for index in range(8):
        angle = index * math.pi * 2 / 8
        x, z = math.sin(angle) * 10, math.cos(angle) * 10
        first = f"leg_1_{index}"
        second = f"leg_2_{index}"
        third = f"leg_3_{index}"
        bones.extend([
            {"name": first, "parent": "body", "pivot": clean_vector([-x, 54, z]), "rotation": [0, clean_number(-math.degrees(angle)), 0], "cubes": [{"origin": clean_vector([-x - 3, 40, z - 3]), "size": [6, 14, 6], "uv": [60, 46]}]},
            {"name": second, "parent": first, "pivot": clean_vector([-x, 40, z]), "cubes": [{"origin": clean_vector([-x - 2, 12, z - 2]), "size": [4, 28, 4], "uv": [0, 72]}]},
            {"name": third, "parent": second, "pivot": clean_vector([-x, 12, z]), "cubes": [{"origin": clean_vector([-x - 1, -16, z - 1]), "size": [2, 28, 2], "uv": [16, 72]}]},
            {"name": f"wing_{index}", "parent": "body", "pivot": clean_vector([-x, 60, z]), "rotation": [0, clean_number(-math.degrees(angle)), 0], "cubes": [{"origin": clean_vector([-x - 12, 60, z]), "size": [24, 0, 24], "uv": [60, 4]}]},
        ])
    return bones


def helper_geometry(source_path: Path, identifier: str) -> tuple[dict, set[str], dict[str, int]]:
    source = source_path.read_text(encoding="utf-8", errors="replace")
    size = re.search(r"TexturedModelDataCreator\(partList,\s*(\d+),\s*(\d+)\)", source)
    if not size:
        raise ValueError(f"No helper texture size in {source_path}")
    width, height = int(size.group(1)), int(size.group(2))
    declarations = re.findall(
        r"ModelPartHelper\s+([A-Za-z0-9_]+)\s*=\s*new ModelPartHelper\(([^;]+)\);", source
    )
    parts: dict[str, Part] = {}
    constructor_uv: dict[str, list[float]] = {}
    root_variables: set[str] = set()
    for variable, args_text in declarations:
        args = split_args(args_text)
        name = args[1].strip().strip('"')
        parts[variable] = Part(variable, name, None, [0, 0, 0], [0, 0, 0], [])
        if args[0].strip() == "partList":
            root_variables.add(variable)
        if len(args) >= 4:
            constructor_uv[variable] = [number(args[2]), number(args[3])]
        else:
            constructor_uv[variable] = [0, 0]
    for variable, args_text in re.findall(r"([A-Za-z0-9_]+)\.setPivot\(([^)]+)\);", source):
        if variable in parts:
            parts[variable].offset = [number(item) for item in split_args(args_text)[:3]]
    for variable, args_text in re.findall(r"([A-Za-z0-9_]+)\.setRotateAngle\(([^)]+(?:\)[^;]*)?)\);", source):
        if variable in parts:
            try:
                parts[variable].rotation = [number(item) for item in split_args(args_text)[:3]]
            except Exception:
                pass
    for parent, child in re.findall(r"([A-Za-z0-9_]+)\.addChild\(([A-Za-z0-9_]+)\);", source):
        if parent in parts and child in parts:
            parts[child].parent_variable = parent
    cube_pattern = re.compile(r"([A-Za-z0-9_]+)\.(addCuboidGlobalUV|addCuboid)\(([^;]+)\);")
    for variable, method, args_text in cube_pattern.findall(source):
        if variable not in parts:
            continue
        args = split_args(args_text)
        try:
            if method == "addCuboid":
                uv = [number(args[0]), number(args[1])]
                values = [number(item) for item in args[2:9]]
            else:
                uv = constructor_uv[variable]
                values = [number(item) for item in args[:7]]
        except Exception:
            continue
        parts[variable].cubes.append(Cube(values[:3], values[3:6], uv, values[6]))
    ordered = list(parts.values())
    for part in ordered:
        if part.variable not in root_variables and not part.parent_variable:
            root_variables.add(part.variable)
    return parts_to_geometry(ordered, identifier, width, height)


def write_generated(namespace: str, identifier: str, bones: list[dict]) -> None:
    root = ASSET_ROOT / namespace
    geometry = geometry_document(identifier, 128 if namespace in {"betternether", "piglinproliferation"} else 64, 128 if identifier == "java_hydrogen_jellyfish" else 64, bones)
    write_json(root / "geo" / f"{identifier}.geo.json", geometry)
    write_json(root / "animations" / f"{identifier}.animation.json", animation_document({bone["name"] for bone in bones}))
    print(f"{namespace}:{identifier}: generated {len(bones)} bones")


def generate_special_models() -> None:
    # Sculk Infection's own helper-built worm, segment, and watcher models.
    helper_models = (
        ("net/sculk_worm/worm/SculkWormModel.java", "java_sculk_worm"),
        ("net/sculk_worm/worm/body/SculkWormSegmentModel.java", "java_sculk_segment"),
        ("net/sculk_worm/watcher/SculkWatcherModel.java", "java_sculk_watcher"),
    )
    for relative, identifier in helper_models:
        geometry, bones, stats = helper_geometry(DECOMPILED / "04_sculk_infection" / relative, identifier)
        root = ASSET_ROOT / "sculk_worm"
        write_json(root / "geo" / f"{identifier}.geo.json", geometry)
        write_json(root / "animations" / f"{identifier}.animation.json", animation_document(bones))
        print(f"sculk_worm:{identifier}: {stats['bones']} helper bones, {stats['cubes']} cubes")

    write_generated("sculk_worm", "java_infected_spawn", spider_bones())
    write_generated("sculk_worm", "java_infected_spider", spider_bones())
    write_generated("sculk_worm", "java_infected_creeper", creeper_bones())
    write_generated("sculk_worm", "java_infected_skeleton", biped_bones(thin=True))
    write_generated("sculk_worm", "java_infected_zombie", biped_bones())
    write_generated("sculk_worm", "java_infected_zombie_miner", biped_bones())
    write_generated("sculk_worm", "java_unstable_enderman", enderman_bones())
    write_generated("sculk_worm", "java_infected_bat", bat_bones())

    write_generated("minecraft_dungend_two_mobs", "java_soul_creeper", creeper_bones())

    # Nether Update Expanded uses vanilla piglin/cow geometry for these variants.
    for identifier in ("kral", "portlin", "piglin_villager_test", "exterminator", "burned"):
        write_generated("nue", f"vanilla_{identifier}", biped_bones(piglin=True))
    for identifier in ("crimson_moongus", "frozen_moongus", "elder_moongus", "warped_moongus", "dragon_moongus"):
        write_generated("nue", f"vanilla_{identifier}", cow_bones())

    write_generated("betternether", "java_hydrogen_jellyfish", hydrogen_jellyfish_bones())

    # ModelNaga builds its four tail segments and six additional fin planes in
    # a Java loop, so append those loop-generated parts to the parsed body/head.
    naga_geo_path = ASSET_ROOT / "betternether" / "geo" / "java_naga.geo.json"
    naga_geo = json.loads(naga_geo_path.read_text(encoding="utf-8"))
    naga_bones = naga_geo["minecraft:geometry"][0]["bones"]
    parent = "body"
    pivot_y = 15.0
    for index, (height, width, pitch) in enumerate(((4, 2, -45), (3, 2, -45), (2, 1, 0), (2, 1, 0))):
        tail_name = f"tail_{index}"
        tail_bone = {
            "name": tail_name,
            "parent": parent,
            "pivot": clean_vector([0, pivot_y, 0]),
            "cubes": [{
                "origin": clean_vector([-height / 2, pivot_y - 20, -width / 2]),
                "size": [height, 20, width],
                "uv": [40, 0],
            }],
        }
        if pitch:
            tail_bone["rotation"] = [pitch, 0, 0]
        naga_bones.append(tail_bone)
        if index < 3:
            px = 32 + (12 - height * 3)
            for side in range(2):
                spike_index = index * 2 + 2 + side
                naga_bones.append({
                    "name": f"spike_{spike_index}",
                    "parent": tail_name,
                    "pivot": clean_vector([0, pivot_y, 0]),
                    "rotation": [0, 60 if side == 0 else 120, 0],
                    "cubes": [{
                        "origin": clean_vector([-height * 3, pivot_y - 20, 0]),
                        "size": [height * 3, 20, 0],
                        "uv": [px, 22],
                    }],
                })
        parent = tail_name
        pivot_y -= 19.0
    write_json(naga_geo_path, naga_geo)
    write_json(
        ASSET_ROOT / "betternether" / "animations" / "java_naga.animation.json",
        animation_document({bone["name"] for bone in naga_bones}),
    )
    print(f"betternether:java_naga: expanded Java loop to {len(naga_bones)} bones")

    jungle = biped_bones(thin=True)
    jungle[1].setdefault("cubes", []).extend([
        {"origin": [-8, 24, 0], "size": [16, 8, 0], "uv": [24, 0]},
        {"origin": [0, 24, -8], "size": [0, 8, 16], "uv": [24, 0]},
    ])
    write_generated("betternether", "java_jungle_skeleton", jungle)

    alchemist = biped_bones(piglin=True)
    alchemist.extend([
        {"name": "belt", "parent": "body", "pivot": [0, 24, 0], "cubes": [{"origin": [-4.25, 11.75, -2.25], "size": [8.5, 12.5, 4.5], "uv": [56, 16]}]},
        {"name": "goggles", "parent": "head", "pivot": [0, 24, 0], "cubes": [{"origin": [-5, 25, -5], "size": [4, 4, 1], "uv": [42, 0]}, {"origin": [1, 25, -5], "size": [4, 4, 1], "uv": [52, 0]}]},
    ])
    write_generated("piglinproliferation", "java_piglin_alchemist", alchemist)
    traveler = biped_bones(piglin=True)
    traveler.extend([
        {"name": "jacket", "parent": "body", "pivot": [0, 24, 0], "cubes": [{"origin": [-4.25, 11.75, -2.25], "size": [8.5, 12.5, 4.5], "uv": [56, 16]}]},
        {"name": "hat_brim", "parent": "head", "pivot": [0, 27, 0], "cubes": [{"origin": [-8, 27, -8], "size": [16, 1, 16], "uv": [80, 19]}]},
    ])
    write_generated("piglinproliferation", "java_piglin_traveler", traveler)

    write_generated("ecosystemmod", "vanilla_target", biped_bones())
    write_generated("undergarden", "java_forgotten", biped_bones(thin=True))

    # Permanent and wild brutes share the author's brute mesh but use another texture.
    source_geo = ASSET_ROOT / "mosslings_muddlings" / "geo" / "java_mossling_brute.geo.json"
    source_anim = ASSET_ROOT / "mosslings_muddlings" / "animations" / "java_mossling_brute.animation.json"
    brute_geo = json.loads(source_geo.read_text(encoding="utf-8"))
    brute_geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry.java_mossling_brute_permanent"
    write_json(ASSET_ROOT / "mosslings_muddlings" / "geo" / "java_mossling_brute_permanent.geo.json", brute_geo)
    write_json(
        ASSET_ROOT / "mosslings_muddlings" / "animations" / "java_mossling_brute_permanent.animation.json",
        json.loads(source_anim.read_text(encoding="utf-8")),
    )
    print("mosslings_muddlings:java_mossling_brute_permanent: shared brute geometry")

    deeper_animation_root = (
        DECOMPILED
        / "03b_deeper_darker_latest"
        / "com/kyanite/deeperdarker/content/entities/animations"
    )
    merge_authored_animations(
        "deeperdarker",
        "java_angler_fish",
        deeper_animation_root / "AnglerFishAnimation.java",
    )
    pot_animations = deeper_animation_root / "OvercastPotAnimation.java"
    merge_authored_animations("deeperdarker", "java_anger_pot", pot_animations, {"anger_walk", "anger_idle"})
    merge_authored_animations("deeperdarker", "java_sorrow_pot", pot_animations, {"sorrow_walk"})
    merge_authored_animations(
        "undergarden",
        "java_rotbelcher",
        DECOMPILED / "16_the_undergarden/quek/undergarden/client/model/animation/RotbelcherAnimation.java",
    )


def main() -> None:
    failures = []
    for model in MODELS:
        source = DECOMPILED / model.donor / Path(model.relative)
        try:
            geometry, bones, stats = parse_geometry_flexible(source, model.identifier)
        except Exception as exc:
            failures.append((model.identifier, source, exc))
            print(f"FAILED {model.namespace}:{model.identifier}: {exc}")
            continue
        namespace_root = ASSET_ROOT / model.namespace
        write_json(namespace_root / "geo" / f"{model.identifier}.geo.json", geometry)
        write_json(namespace_root / "animations" / f"{model.identifier}.animation.json", animation_document(bones))
        print(f"{model.namespace}:{model.identifier}: {stats['bones']} bones, {stats['cubes']} cubes")
    if failures:
        raise SystemExit(f"{len(failures)} Java models failed conversion")
    generate_special_models()


if __name__ == "__main__":
    main()

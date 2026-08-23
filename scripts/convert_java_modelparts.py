#!/usr/bin/env python3
"""Convert donor Java ``ModelPart`` mobs into standalone GeckoLib assets.

Skarrier Mobs still renders ten creatures with Blockbench's generated Java
models and vanilla ``AnimationDefinition`` classes.  Arda's Sculks also uses
a Java model for its fox and vanilla geometry for two other creatures.  This
script turns those client-only definitions into portable GeckoLib JSON so the
donor mods are not runtime dependencies of the Custom NPCs addon.
"""

from __future__ import annotations

import ast
import json
import math
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"

SKARRIER_MODEL_DIR = (
    WORK_DIR
    / "skarrier_mobs_decompiled"
    / "net"
    / "mcreator"
    / "skarriermobs"
    / "client"
    / "model"
)
SKARRIER_ANIMATION_DIR = SKARRIER_MODEL_DIR / "animations"
ARDA_MODEL_DIR = (
    WORK_DIR
    / "ardas_sculks_decompiled"
    / "sculk"
    / "of"
    / "ixra"
    / "client"
    / "model"
)


@dataclass
class Cube:
    origin: list[float]
    size: list[float]
    uv: list[float]
    inflate: float = 0.0
    mirror: bool = False


@dataclass
class Part:
    variable: str
    name: str
    parent_variable: str | None
    offset: list[float]
    rotation: list[float]
    cubes: list[Cube] = field(default_factory=list)


SKARRIER_MODELS = {
    "Modelbreacher": ("java_breacher", "breacherAnimation"),
    "Modeldangle": ("java_dangle", "dangleAnimation"),
    "Modelquake": ("java_quake", "quakeAnimation"),
    "Modelserene": ("java_serene", "sereneAnimation"),
    "Modelsorcerer": ("java_sorcerer", "sorcererAnimation"),
    "Modelstone_golem": ("java_stone_golem", "stone_golemAnimation"),
    "Modeltrawler": ("java_trawler", "trawlerAnimation"),
    "Modeltunnel_gore": ("java_tunnel_gore", "tunnel_goreAnimation"),
    "Modelwrought": ("java_wrought", "wroughtAnimation"),
    "Modelzombiflore": ("java_zombiflore", "zombifloreAnimation"),
}


def split_args(text: str) -> list[str]:
    result: list[str] = []
    start = 0
    depths = {"(": 0, "[": 0, "{": 0}
    pairs = {")": "(", "]": "[", "}": "{"}
    in_string = False
    escaped = False
    for index, char in enumerate(text):
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
        elif char in depths:
            depths[char] += 1
        elif char in pairs:
            depths[pairs[char]] -= 1
        elif char == "," and not any(depths.values()):
            result.append(text[start:index].strip())
            start = index + 1
    result.append(text[start:].strip())
    return result


def matching_paren(source: str, opening: int) -> int:
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
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("Unbalanced Java parentheses")


def method_calls(source: str, names: set[str]) -> list[tuple[int, str, str]]:
    calls: list[tuple[int, str, str]] = []
    pattern = re.compile(r"\.([A-Za-z0-9_]+)\(")
    for match in pattern.finditer(source):
        name = match.group(1)
        if name not in names:
            continue
        opening = match.end() - 1
        closing = matching_paren(source, opening)
        calls.append((match.start(), name, source[opening + 1 : closing]))
    return calls


def _safe_number(node: ast.AST) -> float:
    if isinstance(node, ast.Expression):
        return _safe_number(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return float(node.value)
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        value = _safe_number(node.operand)
        return value if isinstance(node.op, ast.UAdd) else -value
    if isinstance(node, ast.BinOp) and isinstance(node.op, (ast.Add, ast.Sub, ast.Mult, ast.Div)):
        left = _safe_number(node.left)
        right = _safe_number(node.right)
        if isinstance(node.op, ast.Add):
            return left + right
        if isinstance(node.op, ast.Sub):
            return left - right
        if isinstance(node.op, ast.Mult):
            return left * right
        return left / right
    raise ValueError(ast.dump(node))


def number(expression: str) -> float:
    cleaned = expression.strip()
    cleaned = re.sub(r"\((?:float|double|int|MeshDefinition)\)", "", cleaned)
    cleaned = cleaned.replace("Math.PI", repr(math.pi))
    cleaned = re.sub(r"(?<=\d)[fFdD]\b", "", cleaned)
    return _safe_number(ast.parse(cleaned, mode="eval"))


def numbers(text: str) -> list[float]:
    return [number(item) for item in split_args(text)]


def clean_number(value: float) -> int | float:
    if abs(value) < 1.0e-7:
        return 0
    integer = round(value)
    if abs(value - integer) < 1.0e-6:
        return int(integer)
    return round(value, 6)


def clean_vector(values: Iterable[float]) -> list[int | float]:
    return [clean_number(value) for value in values]


def parse_builder(builder: str) -> list[Cube]:
    uv = [0.0, 0.0]
    mirror = False
    cubes: list[Cube] = []
    names = {
        "texOffs",
        "m_171514_",
        "addBox",
        "m_171488_",
        "mirror",
        "m_171480_",
        "m_171555_",
    }
    for _, method, args_text in method_calls(builder, names):
        if method in {"texOffs", "m_171514_"}:
            uv = numbers(args_text)[:2]
        elif method in {"mirror", "m_171480_", "m_171555_"}:
            mirror = True if not args_text.strip() else args_text.strip().lower() != "false"
        elif method in {"addBox", "m_171488_"}:
            args = split_args(args_text)
            values = [number(item) for item in args[:6]]
            inflate = 0.0
            if len(args) >= 7:
                deformation = re.search(r"CubeDeformation\((.+)\)", args[6])
                if deformation:
                    inflate = number(deformation.group(1))
            cubes.append(Cube(values[:3], values[3:6], uv.copy(), inflate, mirror))
    return cubes


def parse_geometry(source_path: Path, identifier: str) -> tuple[dict, set[str], dict[str, int]]:
    source = source_path.read_text(encoding="utf-8")
    layer = re.search(
        r"LayerDefinition\.(?:create|m_171565_)\([^,]+,\s*\(int\)?\s*(\d+),\s*\(int\)?\s*(\d+)\)",
        source,
    )
    if not layer:
        raise ValueError(f"Texture size not found in {source_path}")
    texture_width, texture_height = int(layer.group(1)), int(layer.group(2))

    parts: list[Part] = []
    statement_pattern = re.compile(
        r"PartDefinition\s+([A-Za-z0-9_]+)\s*=\s*([A-Za-z0-9_]+)\."
        r"(?:addOrReplaceChild|m_171599_)\(\"([^\"]+)\",\s*(.+),\s*"
        r"PartPose\.(offset|offsetAndRotation|m_171419_|m_171423_)\((.+)\)\);"
    )
    for match in statement_pattern.finditer(source):
        variable, parent_variable, bone_name, builder, pose_method, pose_args = match.groups()
        pose = numbers(pose_args)
        offset = pose[:3]
        rotation = pose[3:6] if pose_method in {"offsetAndRotation", "m_171423_"} else [0.0, 0.0, 0.0]
        parts.append(
            Part(
                variable,
                bone_name,
                None if parent_variable == "partdefinition" else parent_variable,
                offset,
                rotation,
                parse_builder(builder),
            )
        )
    if not parts:
        raise ValueError(f"No model parts parsed from {source_path}")

    by_variable = {part.variable: part for part in parts}
    pivots: dict[str, list[float]] = {}

    def pivot(part: Part) -> list[float]:
        if part.variable in pivots:
            return pivots[part.variable]
        x, y, z = part.offset
        if part.parent_variable:
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
        if part.parent_variable:
            bone["parent"] = by_variable[part.parent_variable].name
        if any(abs(value) > 1.0e-7 for value in part.rotation):
            bone["rotation"] = clean_vector(
                [-math.degrees(part.rotation[0]), -math.degrees(part.rotation[1]), math.degrees(part.rotation[2])]
            )
        cube_json: list[dict] = []
        for cube in part.cubes:
            x, y, z = cube.origin
            dx, dy, dz = cube.size
            converted: dict = {
                "origin": clean_vector([part_pivot[0] - (x + dx), part_pivot[1] - (y + dy), part_pivot[2] + z]),
                "size": clean_vector(cube.size),
                "uv": clean_vector(cube.uv),
            }
            if abs(cube.inflate) > 1.0e-7:
                converted["inflate"] = clean_number(cube.inflate)
            if cube.mirror:
                converted["mirror"] = True
            cube_json.append(converted)
            cube_count += 1
        if cube_json:
            bone["cubes"] = cube_json
        bones.append(bone)

    geometry = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": f"geometry.{identifier}",
                    "texture_width": texture_width,
                    "texture_height": texture_height,
                },
                "bones": bones,
            }
        ],
    }
    return geometry, {part.name for part in parts}, {"bones": len(bones), "cubes": cube_count}


def parse_animations(source_path: Path, bones: set[str]) -> tuple[dict, dict[str, int]]:
    source = source_path.read_text(encoding="utf-8")
    result: dict[str, dict] = {}
    keyframe_count = 0
    field_pattern = re.compile(
        r"public static final AnimationDefinition\s+([A-Za-z0-9_]+)\s*=\s*(.+?);",
        re.S,
    )
    target_names = {
        "f_232251_": "rotation",
        "f_232250_": "position",
        "f_232252_": "scale",
    }
    for field in field_pattern.finditer(source):
        animation_name, expression = field.groups()
        length_match = re.search(r"Builder\.m_232275_\(\(float\)([^)]+)\)", expression)
        if not length_match:
            continue
        animation: dict = {"animation_length": clean_number(number(length_match.group(1)))}
        if ".m_232274_()" in expression:
            animation["loop"] = True
        animation_bones: dict[str, dict] = {}
        for _, _, channel_args in method_calls(expression, {"m_232279_"}):
            args = split_args(channel_args)
            if len(args) != 2:
                continue
            bone_name = args[0].strip().strip('"')
            if bone_name not in bones:
                continue
            target_match = re.search(r"AnimationChannel\.Targets\.([A-Za-z0-9_]+)", args[1])
            array_match = re.search(r"new Keyframe\[\]\{(.*)\}\s*\)", args[1], re.S)
            if not target_match or not array_match:
                continue
            channel_name = target_names.get(target_match.group(1))
            if not channel_name:
                continue
            track: dict[str, list[int | float]] = {}
            keyframe_source = array_match.group(1)
            cursor = 0
            while True:
                marker = keyframe_source.find("new Keyframe(", cursor)
                if marker < 0:
                    break
                opening = keyframe_source.find("(", marker)
                closing = matching_paren(keyframe_source, opening)
                frame_args = split_args(keyframe_source[opening + 1 : closing])
                cursor = closing + 1
                if len(frame_args) < 2:
                    continue
                time = number(frame_args[0])
                vector_match = re.search(r"KeyframeAnimations\.[A-Za-z0-9_]+\((.+)\)", frame_args[1])
                if not vector_match:
                    continue
                vector = numbers(vector_match.group(1))
                if channel_name == "rotation":
                    vector = [-vector[0], -vector[1], vector[2]]
                elif channel_name == "position":
                    vector = [-vector[0], -vector[1], vector[2]]
                track[f"{time:.4f}".rstrip("0").rstrip(".")] = clean_vector(vector)
                keyframe_count += 1
            if track:
                animation_bones.setdefault(bone_name, {})[channel_name] = track
        if animation_bones:
            animation["bones"] = animation_bones
        result[animation_name] = animation
    return {"format_version": "1.8.0", "animations": result}, {
        "animations": len(result),
        "keyframes": keyframe_count,
    }


def portable_animation(animation_length: float, bones: dict[str, dict], loop: bool = True) -> dict:
    animation: dict = {"animation_length": animation_length, "bones": bones}
    if loop:
        animation["loop"] = True
    return animation


def arda_manual_assets() -> dict[str, tuple[dict, dict]]:
    fish_bones = [
        {"name": "body", "pivot": [0, 13, 0], "cubes": [{"origin": [-1.5, 10.5, -3.5], "size": [3, 5, 7], "uv": [0, 0]}]},
        {"name": "tail", "parent": "body", "pivot": [0, 13, 3.5], "cubes": [{"origin": [0, 10.5, 3.5], "size": [0, 5, 6], "uv": [20, 0]}]},
        {"name": "fin_left", "parent": "body", "pivot": [1.5, 13, 0], "rotation": [0, 0, -35], "cubes": [{"origin": [1.5, 13, -1], "size": [0, 4, 4], "uv": [2, 16]}]},
        {"name": "fin_right", "parent": "body", "pivot": [-1.5, 13, 0], "rotation": [0, 0, 35], "cubes": [{"origin": [-1.5, 13, -1], "size": [0, 4, 4], "uv": [2, 16], "mirror": True}]},
    ]
    fish_geo = geometry_document("vanilla_sculk_fish", 32, 32, fish_bones)
    swim = portable_animation(
        1.0,
        {
            "tail": {"rotation": {"0": [0, -25, 0], "0.5": [0, 25, 0], "1": [0, -25, 0]}},
            "body": {"rotation": {"0": [0, -3, 0], "0.5": [0, 3, 0], "1": [0, -3, 0]}},
        },
    )
    fish_anim = {"format_version": "1.8.0", "animations": {"idle": swim, "walk": swim, "swim": swim}}

    humanoid_bones = [
        {"name": "body", "pivot": [0, 12, 0], "cubes": [{"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]}]},
        {"name": "head", "pivot": [0, 24, 0], "cubes": [{"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0]}, {"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [32, 0], "inflate": 0.5}]},
        {"name": "right_arm", "pivot": [-5, 22, 0], "cubes": [{"origin": [-8, 10, -2], "size": [4, 12, 4], "uv": [40, 16]}]},
        {"name": "left_arm", "pivot": [5, 22, 0], "cubes": [{"origin": [4, 10, -2], "size": [4, 12, 4], "uv": [32, 48], "mirror": True}]},
        {"name": "right_leg", "pivot": [-1.9, 12, 0], "cubes": [{"origin": [-4, 0, -2], "size": [4, 12, 4], "uv": [0, 16]}]},
        {"name": "left_leg", "pivot": [1.9, 12, 0], "cubes": [{"origin": [0, 0, -2], "size": [4, 12, 4], "uv": [16, 48], "mirror": True}]},
    ]
    ghost_geo = geometry_document("vanilla_sculk_ghost", 64, 64, humanoid_bones)
    ghost_anim = basic_biped_animations("right_arm", "left_arm", "right_leg", "left_leg")
    return {
        "vanilla_sculk_fish": (fish_geo, fish_anim),
        "vanilla_sculk_ghost": (ghost_geo, ghost_anim),
    }


def deep_citizen_assets() -> tuple[dict, dict]:
    bones = [
        {"name": "body", "pivot": [0, 24, 0], "cubes": [{"origin": [-4, 12, -3], "size": [8, 12, 6], "uv": [16, 20]}, {"origin": [-4, 12, -3], "size": [8, 12, 6], "uv": [0, 38], "inflate": 0.5}]},
        {"name": "head", "pivot": [0, 24, 0], "cubes": [{"origin": [-4, 24, -4], "size": [8, 10, 8], "uv": [0, 0]}, {"origin": [-4, 24, -4], "size": [8, 10, 8], "uv": [32, 0], "inflate": 0.5}]},
        {"name": "nose", "parent": "head", "pivot": [0, 22, -4], "cubes": [{"origin": [-1, 18, -10], "size": [2, 4, 2], "uv": [24, 0]}]},
        {"name": "arms", "pivot": [0, 21, -1], "rotation": [-45, 0, 0], "cubes": [{"origin": [-4, 9, -3], "size": [8, 4, 4], "uv": [40, 38]}]},
        {"name": "right_leg", "pivot": [-2, 12, 0], "cubes": [{"origin": [-4, 0, -2], "size": [4, 12, 4], "uv": [0, 22]}]},
        {"name": "left_leg", "pivot": [2, 12, 0], "cubes": [{"origin": [0, 0, -2], "size": [4, 12, 4], "uv": [0, 22], "mirror": True}]},
    ]
    geometry = geometry_document("vanilla_deep_citizen", 64, 64, bones)
    walk = portable_animation(
        1.0,
        {
            "right_leg": {"rotation": {"0": [30, 0, 0], "0.5": [-30, 0, 0], "1": [30, 0, 0]}},
            "left_leg": {"rotation": {"0": [-30, 0, 0], "0.5": [30, 0, 0], "1": [-30, 0, 0]}},
        },
    )
    animations = {
        "format_version": "1.8.0",
        "animations": {
            "idle": portable_animation(2.0, {}),
            "walk": walk,
        },
    }
    return geometry, animations


def geometry_document(identifier: str, width: int, height: int, bones: list[dict]) -> dict:
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": f"geometry.{identifier}",
                    "texture_width": width,
                    "texture_height": height,
                },
                "bones": bones,
            }
        ],
    }


def basic_biped_animations(right_arm: str, left_arm: str, right_leg: str, left_leg: str) -> dict:
    walk_bones = {
        right_arm: {"rotation": {"0": [-25, 0, 0], "0.5": [25, 0, 0], "1": [-25, 0, 0]}},
        left_arm: {"rotation": {"0": [25, 0, 0], "0.5": [-25, 0, 0], "1": [25, 0, 0]}},
        right_leg: {"rotation": {"0": [25, 0, 0], "0.5": [-25, 0, 0], "1": [25, 0, 0]}},
        left_leg: {"rotation": {"0": [-25, 0, 0], "0.5": [25, 0, 0], "1": [-25, 0, 0]}},
    }
    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": portable_animation(2.0, {}),
            "walk": portable_animation(1.0, walk_bones),
        },
    }


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> None:
    skarrier_geo = ASSET_ROOT / "skarrier_mobs" / "geo"
    skarrier_animations = ASSET_ROOT / "skarrier_mobs" / "animations"
    for java_class, (identifier, animation_class) in SKARRIER_MODELS.items():
        geometry, bones, geometry_stats = parse_geometry(SKARRIER_MODEL_DIR / f"{java_class}.java", identifier)
        animations, animation_stats = parse_animations(
            SKARRIER_ANIMATION_DIR / f"{animation_class}.java", bones
        )
        write_json(skarrier_geo / f"{identifier}.geo.json", geometry)
        write_json(skarrier_animations / f"{identifier}.animation.json", animations)
        print(
            f"{identifier}: {geometry_stats['bones']} bones, {geometry_stats['cubes']} cubes, "
            f"{animation_stats['animations']} animations, {animation_stats['keyframes']} keyframes"
        )

    arda_geo = ASSET_ROOT / "sculks_of_arda" / "geo"
    arda_animations = ASSET_ROOT / "sculks_of_arda" / "animations"
    fox_geo, fox_bones, fox_stats = parse_geometry(ARDA_MODEL_DIR / "Modelsculkfox.java", "java_sculk_fox")
    write_json(arda_geo / "java_sculk_fox.geo.json", fox_geo)
    fox_anim = {
        "format_version": "1.8.0",
        "animations": {
            "idle": portable_animation(2.0, {"tail": {"rotation": {"0": [0, 0, -4], "1": [0, 0, 4], "2": [0, 0, -4]}}}),
            "walk": portable_animation(
                1.0,
                {
                    "leg0": {"rotation": {"0": [-25, 0, 0], "0.5": [25, 0, 0], "1": [-25, 0, 0]}},
                    "leg1": {"rotation": {"0": [25, 0, 0], "0.5": [-25, 0, 0], "1": [25, 0, 0]}},
                    "leg2": {"rotation": {"0": [25, 0, 0], "0.5": [-25, 0, 0], "1": [25, 0, 0]}},
                    "leg3": {"rotation": {"0": [-25, 0, 0], "0.5": [25, 0, 0], "1": [-25, 0, 0]}},
                },
            ),
        },
    }
    write_json(arda_animations / "java_sculk_fox.animation.json", fox_anim)
    print(f"java_sculk_fox: {fox_stats['bones']} bones, {fox_stats['cubes']} cubes, portable idle/walk")

    for identifier, (geometry, animations) in arda_manual_assets().items():
        write_json(arda_geo / f"{identifier}.geo.json", geometry)
        write_json(arda_animations / f"{identifier}.animation.json", animations)
        print(f"{identifier}: portable vanilla geometry and animations")

    citizen_geo, citizen_animations = deep_citizen_assets()
    call_geo = ASSET_ROOT / "callfromthedepth_" / "geo"
    call_animations = ASSET_ROOT / "callfromthedepth_" / "animations"
    write_json(call_geo / "vanilla_deep_citizen.geo.json", citizen_geo)
    write_json(call_animations / "vanilla_deep_citizen.animation.json", citizen_animations)
    print("vanilla_deep_citizen: portable vanilla geometry and animations")


if __name__ == "__main__":
    main()

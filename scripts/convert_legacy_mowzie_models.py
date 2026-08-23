#!/usr/bin/env python3
"""Convert Mowzie's legacy Java entity models into standalone GeckoLib assets.

The current Mowzie's Mobs jar still contains seven models implemented with
AdvancedModelRenderer instead of GeckoLib JSON.  This build-time converter
reads the decompiled constructors and MMModelAnimator keyframes.  The donor
jar and its classes are intentionally not required at game runtime.
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
SOURCE_DIR = (
    WORK_DIR
    / "mowzie_decompiled"
    / "com"
    / "bobmowzie"
    / "mowziesmobs"
    / "client"
    / "model"
    / "entity"
)
ASSET_DIR = PROJECT_DIR / "src" / "main" / "resources" / "assets" / "mowziesmobs"
GEO_DIR = ASSET_DIR / "geo"
ANIMATION_DIR = ASSET_DIR / "animations"


MODEL_FILES = {
    "ModelFrostmaw": "frostmaw",
    "ModelWroughtnaut": "wroughtnaut",
    "ModelFoliaath": "foliaath",
    "ModelFoliaathBaby": "foliaath_baby",
    "ModelGrottol": "grottol",
    "ModelNaga": "naga",
    "ModelLantern": "mmlantern",
}


@dataclass
class CubeData:
    x: float
    y: float
    z: float
    dx: float
    dy: float
    dz: float
    inflate: float
    uv: tuple[float, float]
    mirror: bool


@dataclass
class PartData:
    name: str
    pivot: list[float] = field(default_factory=lambda: [0.0, 0.0, 0.0])
    rotation: list[float] = field(default_factory=lambda: [0.0, 0.0, 0.0])
    scale: list[float] = field(default_factory=lambda: [1.0, 1.0, 1.0])
    uv: tuple[float, float] = (0.0, 0.0)
    mirror: bool = False
    parent: str | None = None
    cubes: list[CubeData] = field(default_factory=list)


def extract_braced_block(source: str, opening_brace: int) -> str:
    depth = 0
    in_string = False
    escaped = False
    for index in range(opening_brace, len(source)):
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
                return source[opening_brace + 1 : index]
    raise ValueError("Unbalanced Java braces")


def extract_method(source: str, signature_pattern: str) -> str:
    match = re.search(signature_pattern, source)
    if not match:
        return ""
    opening = source.find("{", match.end())
    return extract_braced_block(source, opening)


def split_args(text: str) -> list[str]:
    args: list[str] = []
    start = 0
    depth = 0
    for index, char in enumerate(text):
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif char == "," and depth == 0:
            args.append(text[start:index].strip())
            start = index + 1
    args.append(text[start:].strip())
    return args


def _safe_ast(node: ast.AST, variables: dict[str, float]) -> float:
    if isinstance(node, ast.Expression):
        return _safe_ast(node.body, variables)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return float(node.value)
    if isinstance(node, ast.Name) and node.id in variables:
        return float(variables[node.id])
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, (ast.UAdd, ast.USub)):
        value = _safe_ast(node.operand, variables)
        return value if isinstance(node.op, ast.UAdd) else -value
    if isinstance(node, ast.BinOp) and isinstance(
        node.op, (ast.Add, ast.Sub, ast.Mult, ast.Div, ast.Pow)
    ):
        left = _safe_ast(node.left, variables)
        right = _safe_ast(node.right, variables)
        if isinstance(node.op, ast.Add):
            return left + right
        if isinstance(node.op, ast.Sub):
            return left - right
        if isinstance(node.op, ast.Mult):
            return left * right
        if isinstance(node.op, ast.Div):
            return left / right
        return left**right
    if (
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id in {"radians", "sin", "cos"}
        and len(node.args) == 1
    ):
        return float(getattr(math, node.func.id)(_safe_ast(node.args[0], variables)))
    raise ValueError(ast.dump(node))


def number(expression: str, variables: dict[str, float] | None = None) -> float:
    variables = variables or {}
    cleaned = expression.strip()
    cleaned = re.sub(r"\((?:float|double|int)\)", "", cleaned)
    cleaned = cleaned.replace("Math.PI", repr(math.pi))
    cleaned = cleaned.replace("Mth.PI", repr(math.pi))
    cleaned = cleaned.replace("Math.toRadians", "radians")
    cleaned = re.sub(r"(?<=\d)[fFdD]\b", "", cleaned)
    tree = ast.parse(cleaned, mode="eval")
    return _safe_ast(tree, variables)


def numeric_args(text: str, variables: dict[str, float] | None = None) -> list[float]:
    return [number(arg, variables) for arg in split_args(text)]


def clean_number(value: float) -> int | float:
    if abs(value) < 1.0e-7:
        return 0
    rounded_int = round(value)
    if abs(value - rounded_int) < 1.0e-6:
        return int(rounded_int)
    return round(value, 6)


def clean_vector(values: Iterable[float]) -> list[int | float]:
    return [clean_number(value) for value in values]


def statements(block: str) -> list[str]:
    block = re.sub(r"/\*.*?\*/", "", block, flags=re.S)
    block = re.sub(r"//.*", "", block)
    return [item.strip() for item in block.split(";") if item.strip()]


def apply_numeric_mutations(block: str, parts: dict[str, PartData]) -> None:
    property_map = {
        "rotationPointX": ("pivot", 0),
        "rotationPointY": ("pivot", 1),
        "rotationPointZ": ("pivot", 2),
        "rotateAngleX": ("rotation", 0),
        "rotateAngleY": ("rotation", 1),
        "rotateAngleZ": ("rotation", 2),
    }
    pattern = re.compile(
        r"this\.([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)\s*(=|\+=|-=)\s*(.+)", re.S
    )
    for statement in statements(block):
        match = pattern.fullmatch(statement)
        if not match or match.group(1) not in parts or match.group(2) not in property_map:
            continue
        try:
            value = number(match.group(4))
        except (ValueError, SyntaxError, ZeroDivisionError):
            continue
        target_name, index = property_map[match.group(2)]
        target = getattr(parts[match.group(1)], target_name)
        if match.group(3) == "=":
            target[index] = value
        elif match.group(3) == "+=":
            target[index] += value
        else:
            target[index] -= value


def parse_geometry(java_class: str, model_name: str) -> tuple[dict, set[str], dict[str, int]]:
    source_path = SOURCE_DIR / f"{java_class}.java"
    source = source_path.read_text(encoding="utf-8")
    constructor = extract_method(source, rf"public\s+{re.escape(java_class)}\s*\(\s*\)")
    # Wroughtnaut and Lantern delegate their no-argument constructors to the
    # boolean layer constructor that contains the actual model declaration.
    if "new AdvancedModelRenderer" not in constructor:
        constructor = extract_method(
            source,
            rf"public\s+{re.escape(java_class)}\s*\(\s*boolean\s+[A-Za-z0-9_]+\s*\)",
        )
    width_match = re.search(r"textureWidth\s*=\s*(\d+)", constructor)
    height_match = re.search(r"textureHeight\s*=\s*(\d+)", constructor)
    texture_width = int(width_match.group(1)) if width_match else 64
    texture_height = int(height_match.group(1)) if height_match else 64

    parts: dict[str, PartData] = {}
    source_order: list[str] = []
    for statement in statements(constructor):
        renderer = re.fullmatch(
            r"this\.([A-Za-z0-9_]+)\s*=\s*new\s+AdvancedModelRenderer\s*\(this(?:\s*,\s*(.+?)\s*,\s*(.+?))?\)",
            statement,
            flags=re.S,
        )
        if renderer:
            name = renderer.group(1)
            uv = (0.0, 0.0)
            if renderer.group(2) is not None:
                try:
                    uv = (number(renderer.group(2)), number(renderer.group(3)))
                except (ValueError, SyntaxError):
                    pass
            parts[name] = PartData(name=name, uv=uv)
            source_order.append(name)
            continue

        rotation_point = re.fullmatch(
            r"this\.([A-Za-z0-9_]+)\.setRotationPoint\((.+)\)", statement, flags=re.S
        )
        if rotation_point and rotation_point.group(1) in parts:
            try:
                values = numeric_args(rotation_point.group(2))
            except (ValueError, SyntaxError):
                continue
            if len(values) == 3:
                parts[rotation_point.group(1)].pivot = values
            continue

        rotation = re.fullmatch(
            rf"(?:{re.escape(java_class)}\.)?setRotateAngle\(this\.([A-Za-z0-9_]+),\s*(.+)\)",
            statement,
            flags=re.S,
        )
        if rotation and rotation.group(1) in parts:
            try:
                values = numeric_args(rotation.group(2))
            except (ValueError, SyntaxError):
                continue
            if len(values) == 3:
                parts[rotation.group(1)].rotation = values
            continue

        mirror = re.fullmatch(r"this\.([A-Za-z0-9_]+)\.mirror\s*=\s*(true|false)", statement)
        if mirror and mirror.group(1) in parts:
            parts[mirror.group(1)].mirror = mirror.group(2) == "true"
            continue

        scale = re.fullmatch(
            r"this\.([A-Za-z0-9_]+)\.setScale\((.+)\)", statement, flags=re.S
        )
        if scale and scale.group(1) in parts:
            try:
                values = numeric_args(scale.group(2))
            except (ValueError, SyntaxError):
                continue
            if len(values) == 1:
                values *= 3
            if len(values) == 3:
                parts[scale.group(1)].scale = values
            continue

        chained_box = re.fullmatch(
            r"this\.([A-Za-z0-9_]+)\.setTextureOffset\((.+?),\s*(.+?)\)\.addBox\((.+)\)",
            statement,
            flags=re.S,
        )
        if chained_box and chained_box.group(1) in parts:
            try:
                u = number(chained_box.group(2))
                v = number(chained_box.group(3))
                args = split_args(chained_box.group(4))
                values = [number(value) for value in args[:6]]
            except (ValueError, SyntaxError):
                continue
            mirror_value = (
                args[6].strip().lower() == "true" if len(args) >= 7 else parts[chained_box.group(1)].mirror
            )
            parts[chained_box.group(1)].cubes.append(
                CubeData(*values, 0.0, (u, v), mirror_value)
            )
            continue

        box = re.fullmatch(
            r"this\.([A-Za-z0-9_]+)\.addBox\((.+)\)", statement, flags=re.S
        )
        if box and box.group(1) in parts:
            try:
                values = numeric_args(box.group(2))
            except (ValueError, SyntaxError):
                continue
            if len(values) >= 6:
                inflate = values[6] if len(values) >= 7 else 0.0
                part = parts[box.group(1)]
                part.cubes.append(CubeData(*values[:6], inflate, part.uv, part.mirror))
            continue

        child = re.fullmatch(
            r"this\.([A-Za-z0-9_]+)\.addChild\(this\.([A-Za-z0-9_]+)\)", statement
        )
        if child and child.group(1) in parts and child.group(2) in parts:
            parts[child.group(2)].parent = child.group(1)

    apply_numeric_mutations(constructor, parts)

    # Naga applies these unconditional authoring corrections after every pose reset.
    if java_class == "ModelNaga":
        corrections = extract_method(source, r"private\s+void\s+modelCorrections\s*\(\s*\)")
        apply_numeric_mutations(corrections, parts)
        for statement in statements(corrections):
            scale = re.fullmatch(
                r"this\.([A-Za-z0-9_]+)\.setScale\((.+)\)", statement, flags=re.S
            )
            if scale and scale.group(1) in parts:
                try:
                    values = numeric_args(scale.group(2))
                except (ValueError, SyntaxError):
                    continue
                if len(values) == 1:
                    values *= 3
                if len(values) == 3:
                    parts[scale.group(1)].scale = values

    global_pivots: dict[str, list[float]] = {}

    def global_pivot(name: str, visiting: set[str] | None = None) -> list[float]:
        if name in global_pivots:
            return global_pivots[name]
        visiting = visiting or set()
        if name in visiting:
            raise ValueError(f"Cyclic model hierarchy at {name}")
        visiting.add(name)
        part = parts[name]
        x, y, z = part.pivot
        if part.parent:
            parent_pivot = global_pivot(part.parent, visiting)
            value = [parent_pivot[0] - x, parent_pivot[1] - y, parent_pivot[2] + z]
        else:
            value = [-x, 24.0 - y, z]
        global_pivots[name] = value
        return value

    bones: list[dict] = []
    cube_count = 0
    for name in source_order:
        part = parts[name]
        pivot = global_pivot(name)
        bone: dict = {"name": name}
        if part.parent:
            bone["parent"] = part.parent
        bone["pivot"] = clean_vector(pivot)
        if any(abs(value) > 1.0e-7 for value in part.rotation):
            bone["rotation"] = clean_vector(
                [
                    -math.degrees(part.rotation[0]),
                    -math.degrees(part.rotation[1]),
                    math.degrees(part.rotation[2]),
                ]
            )
        cubes: list[dict] = []
        sx, sy, sz = part.scale
        for cube in part.cubes:
            if abs(cube.dx) < 1.0e-9 and abs(cube.dy) < 1.0e-9 and abs(cube.dz) < 1.0e-9:
                continue
            local_origin = [-(cube.x + cube.dx) * sx, -(cube.y + cube.dy) * sy, cube.z * sz]
            origin = [
                pivot[0] + local_origin[0],
                pivot[1] + local_origin[1],
                pivot[2] + local_origin[2],
            ]
            cube_json: dict = {
                "origin": clean_vector(origin),
                "size": clean_vector([cube.dx * sx, cube.dy * sy, cube.dz * sz]),
                "uv": clean_vector(cube.uv),
            }
            if abs(cube.inflate) > 1.0e-7:
                cube_json["inflate"] = clean_number(cube.inflate)
            if cube.mirror:
                cube_json["mirror"] = True
            cubes.append(cube_json)
            cube_count += 1
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)

    geometry = {
        "format_version": "1.12.0",
        "minecraft:geometry": [
            {
                "description": {
                    "identifier": f"geometry.{model_name}",
                    "texture_width": texture_width,
                    "texture_height": texture_height,
                },
                "bones": bones,
            }
        ],
    }
    stats = {"bones": len(bones), "cubes": cube_count}
    return geometry, set(parts), stats


def animation_name(expression: str) -> str:
    raw = expression.split(".")[-1]
    raw = re.sub(r"_ANIMATION$", "", raw)
    return raw.lower()


def set_key(track: dict[str, list[int | float]], time: float, vector: list[float]) -> None:
    track[f"{time:.4f}".rstrip("0").rstrip(".")] = clean_vector(vector)


def parse_keyframe_segment(segment: str, bones: set[str]) -> tuple[dict, float]:
    variables: dict[str, float] = {}
    time = 0.0
    previous: dict[str, dict[str, list[float]]] = {}
    current: dict[str, dict[str, list[float]]] | None = None
    duration = 0.0
    tracks: dict[str, dict[str, dict[str, list[int | float]]]] = {}

    def vector(pose: dict[str, dict[str, list[float]]], bone: str, channel: str) -> list[float]:
        return pose.get(bone, {}).get(channel, [0.0, 0.0, 0.0])

    def write_transition(target: dict[str, dict[str, list[float]]], ticks: float) -> None:
        nonlocal time, previous
        end = time + ticks / 20.0
        changed_bones = set(previous) | set(target)
        for bone in changed_bones:
            if bone not in bones:
                continue
            channels = set(previous.get(bone, {})) | set(target.get(bone, {}))
            for channel in channels:
                track = tracks.setdefault(bone, {}).setdefault(channel, {})
                set_key(track, time, vector(previous, bone, channel))
                set_key(track, end, vector(target, bone, channel))
        time = end
        previous = {
            bone: {channel: values[:] for channel, values in channels.items()}
            for bone, channels in target.items()
        }

    for statement in statements(segment):
        assignment = re.fullmatch(
            r"(?:final\s+)?(?:int|float|double)\s+([A-Za-z0-9_]+)\s*=\s*(.+)", statement, re.S
        )
        if assignment:
            try:
                variables[assignment.group(1)] = number(assignment.group(2), variables)
            except (ValueError, SyntaxError, ZeroDivisionError):
                pass
            continue

        start = re.search(r"animator\.startKeyframe\((.+)\)", statement, re.S)
        if start:
            try:
                duration = number(start.group(1), variables)
            except (ValueError, SyntaxError, ZeroDivisionError):
                duration = 0.0
            current = {}
            continue

        transform = re.search(
            r"animator\.(rotate|move)\(this\.([A-Za-z0-9_]+),\s*(.+)\)", statement, re.S
        )
        if transform and current is not None:
            try:
                values = numeric_args(transform.group(3), variables)
            except (ValueError, SyntaxError, ZeroDivisionError):
                continue
            if len(values) != 3:
                continue
            channel = "rotation" if transform.group(1) == "rotate" else "position"
            converted = (
                [-math.degrees(values[0]), -math.degrees(values[1]), math.degrees(values[2])]
                if channel == "rotation"
                else [-values[0], values[1], values[2]]
            )
            existing = current.setdefault(transform.group(2), {}).setdefault(channel, [0.0, 0.0, 0.0])
            for index in range(3):
                existing[index] += converted[index]
            continue

        if "animator.endKeyframe()" in statement and current is not None:
            write_transition(current, duration)
            current = None
            continue

        static = re.search(r"animator\.setStaticKeyframe\((.+)\)", statement, re.S)
        if static:
            try:
                ticks = number(static.group(1), variables)
            except (ValueError, SyntaxError, ZeroDivisionError):
                ticks = 0.0
            write_transition(previous, ticks)
            continue

        reset = re.search(r"animator\.resetKeyframe\((.+)\)", statement, re.S)
        if reset:
            try:
                ticks = number(reset.group(1), variables)
            except (ValueError, SyntaxError, ZeroDivisionError):
                ticks = 0.0
            write_transition({}, ticks)

    result_bones: dict[str, dict] = {}
    for bone, channels in tracks.items():
        result_bones[bone] = {}
        for channel, keyframes in channels.items():
            result_bones[bone][channel] = keyframes
    return result_bones, time


def loop_animation(
    bones: set[str],
    rotations: dict[str, list[float]] | None = None,
    positions: dict[str, list[float]] | None = None,
    length: float = 2.0,
) -> dict:
    result: dict[str, dict] = {}
    for channel, source in (("rotation", rotations or {}), ("position", positions or {})):
        for bone, amplitude in source.items():
            if bone not in bones:
                continue
            opposite = [-value for value in amplitude]
            result.setdefault(bone, {})[channel] = {
                "0.0": clean_vector(amplitude),
                f"{length / 2:g}": clean_vector(opposite),
                f"{length:g}": clean_vector(amplitude),
            }
    return {"loop": True, "animation_length": length, "bones": result}


def add_ambient_animations(model_name: str, bones: set[str], animations: dict[str, dict]) -> None:
    specs: dict[str, dict[str, dict[str, list[float]]]] = {
        "foliaath": {
            "rotations": {
                "stem1Base": [1.5, 0, 4], "stem2": [-2, 0, 0], "stem3": [3, 0, 0],
                "stem4": [-2, 0, 0], "headBase": [5, 0, -2], "tongue1": [4, 3, 0],
                "leaf1Head": [5, 0, 0], "leaf3Head": [-5, 0, 0], "leaf5Head": [5, 0, 0],
                "leaf7Head": [-5, 0, 0],
            }
        },
        "foliaath_baby": {
            "rotations": {
                "juvenileLeaf1": [4, 0, 0], "juvenileLeaf2": [-4, 0, 0],
                "juvenileLeaf3": [4, 0, 0], "juvenileLeaf4": [-4, 0, 0],
                "mouth1": [0, 0, 3], "mouth2": [0, 0, -3],
            }
        },
        "frostmaw": {
            "rotations": {
                "waist": [3, 2, 1], "chest": [-2, -2, -1], "headJoint": [3, 2, 0],
                "armLeftJoint": [4, 0, -2], "armRightJoint": [-4, 0, 2],
            },
            "positions": {"waist": [0, 1.5, 0]},
        },
        "grottol": {
            "rotations": {
                "body": [0, 0, 2], "clawLeftUpper": [0, 0, 12],
                "clawRightUpper": [0, 0, -12], "leg1LeftUpper": [0, 0, 8],
                "leg1RightUpper": [0, 0, -8],
            }
        },
        "mmlantern": {
            "rotations": {"body": [0, 5, 2], "stem": [3, 0, -3], "bottomBits": [-3, 0, 3]},
            "positions": {"body": [0, 1.5, 0], "bubbles": [0, -1, 0]},
        },
        "naga": {
            "rotations": {
                "shoulder1_R": [0, 0, 28], "lowerArmJoint_R": [0, 0, 18],
                "handJoint_R": [0, 0, 12], "shoulder1_L": [0, 0, -28],
                "lowerArmJoint_L": [0, 0, -18], "handJoint_L": [0, 0, -12],
                "tail1": [3, 5, 0], "tail2": [-2, -6, 0], "tail3": [2, 7, 0],
            },
            "positions": {"root": [0, 5, 0]},
        },
        "wroughtnaut": {
            "rotations": {
                "waist": [1, 3, 0], "stomachJoint": [-1, -5, 0], "head": [1, 2, 0],
                "shoulderLeft": [0, 2, 2], "shoulderRight": [0, -2, -2],
            },
            "positions": {"waist": [0, 0.7, 0]},
        },
    }
    walk_specs: dict[str, dict[str, list[float]]] = {
        "foliaath": {"stem1Base": [3, 0, 8], "headBase": [-6, 0, -4]},
        "foliaath_baby": {"juvenileLeaf1": [8, 0, 0], "juvenileLeaf3": [-8, 0, 0]},
        "frostmaw": {
            "legLeft1": [32, 0, 0], "legRight1": [-32, 0, 0], "armLeftJoint": [-20, 0, 0],
            "armRightJoint": [20, 0, 0], "headJoint": [4, 4, 0], "waist": [-4, -4, 0],
        },
        "grottol": {
            "leg1LeftJoint": [0, 20, 0], "leg2LeftJoint": [0, -18, 0], "leg3LeftJoint": [0, 16, 0],
            "leg1RightJoint": [0, -20, 0], "leg2RightJoint": [0, 18, 0], "leg3RightJoint": [0, -16, 0],
        },
        "mmlantern": {"body": [0, 8, 4], "stem": [5, 0, -5]},
        "naga": {
            "shoulder1_R": [0, 0, 42], "lowerArmJoint_R": [0, 0, 28],
            "shoulder1_L": [0, 0, -42], "lowerArmJoint_L": [0, 0, -28],
            "tail1": [4, 8, 0], "tail2": [-3, -10, 0], "tail3": [3, 12, 0],
        },
        "wroughtnaut": {
            "thighLeftJoint": [30, 12, 0], "thighRightJoint": [-30, -12, 0],
            "calfLeftJoint": [-22, 0, 0], "calfRightJoint": [22, 0, 0],
            "upperArmLeftJoint": [-15, 0, 0], "upperArmRightJoint": [15, 0, 0],
            "waist": [0, 7, 0], "stomachJoint": [0, -8, 0],
        },
    }
    spec = specs.get(model_name, {})
    idle_loop = loop_animation(
        bones,
        rotations=spec.get("rotations", {}),
        positions=spec.get("positions", {}),
        length=2.0,
    )
    if "idle" in animations:
        animations["original_idle_action"] = animations.pop("idle")
    animations["idle"] = idle_loop
    animations["idle_loop"] = idle_loop
    animations["walk"] = loop_animation(
        bones,
        rotations=walk_specs.get(model_name, spec.get("rotations", {})),
        positions={"root": [0, 0.8, 0]} if "root" in bones else {},
        length=1.0,
    )


def parse_animations(java_class: str, model_name: str, bones: set[str]) -> tuple[dict, dict[str, int]]:
    source = (SOURCE_DIR / f"{java_class}.java").read_text(encoding="utf-8")
    markers = list(re.finditer(r"(?:this\.)?animator\.setAnimation\(([^)]+)\)", source))
    animations: dict[str, dict] = {}
    counts: dict[str, int] = {}
    for index, marker in enumerate(markers):
        end = markers[index + 1].start() if index + 1 < len(markers) else len(source)
        segment = source[marker.end() : end]
        base_name = animation_name(marker.group(1))
        counts[base_name] = counts.get(base_name, 0) + 1
        name = base_name if counts[base_name] == 1 else f"{base_name}_variant_{counts[base_name]}"
        animation_bones, length = parse_keyframe_segment(segment, bones)
        if not animation_bones:
            continue
        animations[name] = {
            "animation_length": clean_number(max(length, 0.05)),
            "bones": animation_bones,
        }
    add_ambient_animations(model_name, bones, animations)
    animated_bones = {
        bone for animation in animations.values() for bone in animation.get("bones", {})
    }
    stats = {
        "animations": len(animations),
        "animated_bones": len(animated_bones),
        "unmatched_bones": len(animated_bones - bones),
    }
    result = {"format_version": "1.8.0", "animations": animations}
    return result, stats


def main() -> None:
    GEO_DIR.mkdir(parents=True, exist_ok=True)
    ANIMATION_DIR.mkdir(parents=True, exist_ok=True)
    report: dict[str, dict[str, int]] = {}
    for java_class, model_name in MODEL_FILES.items():
        geometry, bones, geometry_stats = parse_geometry(java_class, model_name)
        animations, animation_stats = parse_animations(java_class, model_name, bones)
        geo_path = GEO_DIR / f"{model_name}.geo.json"
        animation_path = ANIMATION_DIR / f"{model_name}.animation.json"
        geo_path.write_text(json.dumps(geometry, indent=2) + "\n", encoding="utf-8")
        animation_path.write_text(json.dumps(animations, indent=2) + "\n", encoding="utf-8")
        report[model_name] = geometry_stats | animation_stats
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Derive default hitbox sizes for every bundled GeckoLib model.

Minecraft gives an entity one axis aligned box: a square footprint of
``width`` by ``width`` that rises ``height`` blocks from the feet. It cannot
follow a silhouette, so the best a default can do is wrap the model closely.

Height is taken from the top of the rest pose, because the box always grows
upward from the entity position.

Width is taken from the narrower horizontal span. The footprint is square while
models rarely are, so the wider span is a length rather than a width: a T-Rex
measures 3.6 across and 9.3 nose to tail, a wither 3.9 across and 27.7 between
its outer heads. Taking the narrower span keeps the box inside the model on
both axes instead of wrapping the longest reach in a square. It is also
measured without the peripheral bones (wings, tails, capes, hair, effect
emitters) that would otherwise push the body span outward.

Width additionally drives collision, pathfinding, and entity pushing, so it is
capped: past a certain size a mob stops fitting through its own arena. The
ends of a long creature are therefore not covered by this box; covering those
needs separate hit parts rather than a wider single box.
"""

from __future__ import annotations

import json
import math
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"
OUTPUT = PROJECT_DIR / "src" / "main" / "resources" / "META-INF" / "MOBMODEL_HITBOXES.tsv"

UNITS_PER_BLOCK = 16.0

MIN_WIDTH = 0.3
MAX_WIDTH = 8.0
MIN_HEIGHT = 0.3
MAX_HEIGHT = 16.0

# Bones whose cubes stick out well past the body. They are excluded from the
# footprint but never from the height, because a raised wing is not a reason to
# widen a hitbox while a raised head is a reason to heighten one.
PERIPHERAL_TOKENS = (
    "wing", "tail", "cape", "cloak", "drape", "hair", "aura", "effect", "vfx",
    "_fx", "particle", "beam", "projectile", "hitbox", "fire_", "smoke", "glow",
    "trail", "ring", "cloud", "spike", "held", "item",
)

# If peripheral filtering removes almost everything the bone names were not
# describing peripherals at all, so the unfiltered span is used instead.
PERIPHERAL_FLOOR = 0.2

# Reviewed sizes that win over the derived ones. The addon's own humanoid and
# placeholder models keep the historical 0.7 x 2.0 default: they are what a
# plain NPC uses, and rederiving them would resize existing NPCs that were
# never part of this problem.
MANUAL: dict[str, tuple[float, float]] = {
    "cnpcgeckoaddon:geo/geo_npc.geo.json": (0.7, 2.0),
    "cnpcgeckoaddon:geo/modelnotfound.geo.json": (0.7, 2.0),
    "cnpcgeckoaddon:geo/animfilenotfound.geo.json": (0.7, 2.0),
}


def identity() -> list[list[float]]:
    return [[1.0 if row == column else 0.0 for column in range(4)] for row in range(4)]


def multiply(left: list[list[float]], right: list[list[float]]) -> list[list[float]]:
    return [
        [sum(left[row][k] * right[k][column] for k in range(4)) for column in range(4)]
        for row in range(4)
    ]


def translation(x: float, y: float, z: float) -> list[list[float]]:
    matrix = identity()
    matrix[0][3], matrix[1][3], matrix[2][3] = x, y, z
    return matrix


def rotation(degrees: list[float]) -> list[list[float]]:
    x, y, z = (math.radians(value) for value in degrees)
    cx, sx = math.cos(x), math.sin(x)
    cy, sy = math.cos(y), math.sin(y)
    cz, sz = math.cos(z), math.sin(z)
    around_x = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    around_y = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    around_z = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return multiply(multiply(around_z, around_y), around_x)


def pivoted(pivot: list[float], degrees: list[float] | None) -> list[list[float]]:
    if not degrees or not any(degrees):
        return identity()
    x, y, z = pivot
    return multiply(multiply(translation(x, y, z), rotation(degrees)), translation(-x, -y, -z))


def transform(matrix: list[list[float]], point: list[float]) -> list[float]:
    return [
        sum(matrix[row][k] * point[k] for k in range(3)) + matrix[row][3]
        for row in range(3)
    ]


def cube_corners(origin: list[float], size: list[float], inflate: float | None) -> list[list[float]]:
    grow = inflate or 0.0
    low = [origin[axis] - grow for axis in range(3)]
    high = [origin[axis] + size[axis] + grow for axis in range(3)]
    return [
        [x, y, z]
        for x in (low[0], high[0])
        for y in (low[1], high[1])
        for z in (low[2], high[2])
    ]


def bounds(path: Path, skip_peripheral: bool) -> tuple[list[float], list[float]] | None:
    document = json.loads(path.read_text(encoding="utf-8-sig"))
    geometries = document.get("minecraft:geometry")
    if not isinstance(geometries, list) or not geometries:
        return None
    bones = [bone for bone in geometries[0].get("bones", []) if isinstance(bone, dict)]
    by_name = {bone.get("name"): bone for bone in bones}
    resolved: dict[str, list[list[float]]] = {}

    def world(name: str, depth: int = 0) -> list[list[float]]:
        if name in resolved:
            return resolved[name]
        bone = by_name.get(name)
        if bone is None or depth > 64:
            return identity()
        matrix = pivoted(bone.get("pivot") or [0.0, 0.0, 0.0], bone.get("rotation"))
        parent = bone.get("parent")
        if parent and parent != name and parent in by_name:
            matrix = multiply(world(parent, depth + 1), matrix)
        resolved[name] = matrix
        return matrix

    low = [math.inf] * 3
    high = [-math.inf] * 3
    found = False
    for bone in bones:
        name = bone.get("name") or ""
        if skip_peripheral and is_peripheral(name):
            continue
        bone_matrix = world(name)
        for cube in bone.get("cubes") or []:
            origin, size = cube.get("origin"), cube.get("size")
            if not origin or not size:
                continue
            matrix = bone_matrix
            cube_rotation = cube.get("rotation")
            if cube_rotation and any(cube_rotation):
                matrix = multiply(bone_matrix, pivoted(cube.get("pivot") or origin, cube_rotation))
            for corner in cube_corners(origin, size, cube.get("inflate")):
                point = transform(matrix, corner)
                found = True
                for axis in range(3):
                    low[axis] = min(low[axis], point[axis])
                    high[axis] = max(high[axis], point[axis])
    return (low, high) if found else None


def is_peripheral(name: str) -> bool:
    lowered = name.lower()
    return any(token in lowered for token in PERIPHERAL_TOKENS)


def footprint(low: list[float], high: list[float]) -> float:
    return min(high[0] - low[0], high[2] - low[2]) / UNITS_PER_BLOCK


def measure(path: Path) -> tuple[float, float, bool] | None:
    full = bounds(path, skip_peripheral=False)
    if full is None:
        return None
    full_low, full_high = full
    full_width = footprint(full_low, full_high)

    body = bounds(path, skip_peripheral=True)
    width = full_width
    trimmed = False
    if body is not None:
        body_width = footprint(body[0], body[1])
        if body_width >= full_width * PERIPHERAL_FLOOR:
            trimmed = body_width < full_width
            width = body_width

    height = full_high[1] / UNITS_PER_BLOCK
    width = min(max(width, MIN_WIDTH), MAX_WIDTH)
    height = min(max(height, MIN_HEIGHT), MAX_HEIGHT)
    return round(width, 2), round(height, 2), trimmed


def main() -> None:
    sizes: dict[str, tuple[float, float]] = {}
    capped_width = capped_height = trimmed_count = 0
    skipped: list[str] = []

    for path in sorted(ASSET_ROOT.glob("*/geo/**/*.geo.json")):
        relative = path.relative_to(ASSET_ROOT).as_posix()
        namespace, resource = relative.split("/", 1)
        key = f"{namespace}:{resource}"
        measured = measure(path)
        if measured is None:
            skipped.append(key)
            continue
        width, height, trimmed = measured
        if width >= MAX_WIDTH:
            capped_width += 1
        if height >= MAX_HEIGHT:
            capped_height += 1
        trimmed_count += trimmed
        sizes[key] = (width, height)

    sizes.update(MANUAL)

    lines = [
        "# Default hitbox sizes for bundled mob models.",
        "# model resource<TAB>width<TAB>height, in blocks.",
        f"# Width is capped at {MAX_WIDTH} and height at {MAX_HEIGHT} because the box also drives collision.",
    ]
    lines.extend(f"{model}\t{width}\t{height}" for model, (width, height) in sorted(sizes.items()))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"Wrote {len(sizes)} hitbox sizes to {OUTPUT}")
    print(f"peripheral bones trimmed the footprint on {trimmed_count} models")
    print(f"clamped to the width cap: {capped_width}; to the height cap: {capped_height}")
    if skipped:
        print(f"skipped {len(skipped)} models without cubes:")
        for key in skipped[:10]:
            print(f"  {key}")


if __name__ == "__main__":
    main()

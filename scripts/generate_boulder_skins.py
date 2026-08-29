"""Deterministic pixel art for the six boss boulder skins.

The renderer dresses the rolling stone with three shrunken copies of one 16-cube,
all carrying the plain ``texOffs(0, 0)`` unwrap, so a single 64x32 drawing has to
hold up seen from every angle at once. Two things follow from that, and they drive
the whole script:

* every pixel is painted from a *solid* three-dimensional field, sampled at the
  point of the cube surface that the pixel actually covers. All twelve edges of the
  unwrap then join by construction instead of by hand, and the face-to-face seams
  cannot drift apart when a style is retuned.
* nothing in the field knows which way is up. The three turned copies therefore
  never line up into a repeat the eye can catch as the boulder rolls.

The pixel-to-surface table below is the vanilla cube unwrap, read straight out of
``ModelPart$Cube`` for a 16x16x16 box at offset (0, 0) on a 64x32 sheet.

Aseprite is not required: the committed art was written by this script, which also
emits openable ``.aseprite`` sources so the drawing stays editable by hand.
"""

from __future__ import annotations

import argparse
import math
import struct
import sys
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable, Sequence

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ART_DIR = ROOT / "art" / "aseprite" / "boulder"
PREVIEW_DIR = ART_DIR / "preview"
ENTITY_DIR = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "cnpcgeckoaddon"
    / "textures"
    / "entity"
    / "boulder"
)

CANVAS_SIZE = (64, 32)
TRANSPARENT = (0, 0, 0, 0)
STYLE_IDS = ("stone", "magma", "sculk", "mossy", "bone", "ghost")
LAYER_NAMES = ("rock", "cracks", "detail")
MIN_COLORS = 10
MAX_COLORS = 16
# Both caps are read against the 1536 texels of the cube surface.
MAX_FLAT_PATCH = 96
# The share of one 3.5-unit patch of surface a style's accents may ever take up, and
# how far their average direction out of the middle of the boulder may drift.
MAX_ACCENT_DENSITY = 0.95
MAX_ACCENT_BIAS = 0.22

RGB = tuple[int, int, int]
Vec3 = tuple[float, float, float]


# --------------------------------------------------------------------------------------
# The unwrap
# --------------------------------------------------------------------------------------


@dataclass(frozen=True)
class Face:
    """One 16x16 island and the cube surface it covers.

    ``place`` turns a texture coordinate into the model-space point the texel sits
    on, for a box spanning -8..8 on every axis. Model space has +Y pointing down,
    which is why the world-up face of the boulder is the one at y = -8.
    """

    name: str
    origin: tuple[int, int]
    place: Callable[[float, float], Vec3]


FACES: tuple[Face, ...] = (
    Face("top", (16, 0), lambda u, v: (u - 24.0, -8.0, 8.0 - v)),
    Face("bottom", (32, 0), lambda u, v: (u - 40.0, 8.0, 8.0 - v)),
    Face("left", (0, 16), lambda u, v: (-8.0, v - 24.0, 8.0 - u)),
    Face("front", (16, 16), lambda u, v: (u - 24.0, v - 24.0, -8.0)),
    Face("right", (32, 16), lambda u, v: (8.0, v - 24.0, u - 40.0)),
    Face("back", (48, 16), lambda u, v: (56.0 - u, v - 24.0, 8.0)),
)

def surface_points() -> dict[tuple[int, int], Vec3]:
    """Every opaque texel of the sheet, with the surface point its centre covers."""
    points: dict[tuple[int, int], Vec3] = {}
    for face in FACES:
        for row in range(16):
            for column in range(16):
                x = face.origin[0] + column
                y = face.origin[1] + row
                points[(x, y)] = face.place(x + 0.5, y + 0.5)
    return points


# --------------------------------------------------------------------------------------
# Solid fields
# --------------------------------------------------------------------------------------

MASK32 = 0xFFFFFFFF


def _hash(*values: int) -> int:
    result = 0x9E3779B9
    for value in values:
        result = ((result ^ (value & MASK32)) * 0x85EBCA6B) & MASK32
        result ^= result >> 13
    result = (result * 0xC2B2AE35) & MASK32
    return result ^ (result >> 16)


def _unit(*values: int) -> float:
    """A stable pseudo-random number in [0, 1) for an integer lattice cell."""
    return _hash(*values) / 4294967296.0


def _fade(t: float) -> float:
    return t * t * (3.0 - 2.0 * t)


def noise(point: Vec3, frequency: float, seed: int) -> float:
    """Trilinear value noise in [-1, 1], sampled on an integer lattice."""
    x, y, z = (component * frequency for component in point)
    xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
    tx, ty, tz = _fade(x - xi), _fade(y - yi), _fade(z - zi)
    total = 0.0
    for dz in (0, 1):
        wz = tz if dz else 1.0 - tz
        for dy in (0, 1):
            wy = ty if dy else 1.0 - ty
            for dx in (0, 1):
                wx = tx if dx else 1.0 - tx
                total += wx * wy * wz * _unit(xi + dx, yi + dy, zi + dz, seed)
    return total * 2.0 - 1.0


def fbm(point: Vec3, frequency: float, seed: int, octaves: int) -> float:
    """Stacked value noise in [-1, 1]; the mottling every style is built on."""
    total = 0.0
    amplitude = 1.0
    weight = 0.0
    for octave in range(octaves):
        total += amplitude * noise(point, frequency * (2.0**octave), seed + octave * 101)
        weight += amplitude
        amplitude *= 0.5
    return total / weight


def warped(point: Vec3, amount: float, frequency: float, seed: int) -> Vec3:
    """Push a sample point around, so straight cell walls come out as broken rock."""
    return (
        point[0] + amount * noise(point, frequency, seed),
        point[1] + amount * noise(point, frequency, seed + 17),
        point[2] + amount * noise(point, frequency, seed + 34),
    )


def shell_points(count: int, radius: float, seed: int) -> tuple[Vec3, ...]:
    """Cell seeds spread over a sphere that cuts the whole cube surface into chips.

    A Fibonacci spiral spaces them evenly, then a per-seed jitter takes the
    regularity back out; on the surface this reads as a mosaic of chipped facets
    with no two the same size.
    """
    result: list[Vec3] = []
    golden = math.pi * (3.0 - math.sqrt(5.0))
    for index in range(count):
        height = 1.0 - 2.0 * (index + 0.5) / count
        ring = math.sqrt(max(0.0, 1.0 - height * height))
        angle = golden * index
        direction = [math.cos(angle) * ring, height, math.sin(angle) * ring]
        for axis in range(3):
            direction[axis] += (_unit(index, seed, axis) * 2.0 - 1.0) * 0.22
        length = math.sqrt(sum(component * component for component in direction)) or 1.0
        scale = radius * (0.84 + 0.32 * _unit(index, seed, 7)) / length
        result.append((direction[0] * scale, direction[1] * scale, direction[2] * scale))
    return tuple(result)


def cell_ranks(point: Vec3, seeds: Sequence[Vec3]) -> tuple[float, float, int]:
    """Nearest and second-nearest seed distance plus the winning seed index."""
    best = second = float("inf")
    winner = 0
    for index, seed in enumerate(seeds):
        dx = point[0] - seed[0]
        dy = point[1] - seed[1]
        dz = point[2] - seed[2]
        distance = math.sqrt(dx * dx + dy * dy + dz * dz)
        if distance < best:
            second = best
            best = distance
            winner = index
        elif distance < second:
            second = distance
    return best, second, winner


# --------------------------------------------------------------------------------------
# Palettes
# --------------------------------------------------------------------------------------


@dataclass(frozen=True)
class Palette:
    """A style's whole colour set, in the order it is written to the ``.gpl``."""

    title: str
    source: str
    entries: tuple[tuple[str, RGB], ...]

    def __post_init__(self) -> None:
        names = [name for name, _ in self.entries]
        colors = [color for _, color in self.entries]
        if len(set(names)) != len(names) or len(set(colors)) != len(colors):
            raise ValueError(f"{self.title}: palette names and colours must be unique")
        if not MIN_COLORS <= len(colors) <= MAX_COLORS:
            raise ValueError(f"{self.title}: {len(colors)} colours is outside 10..16")

    def index(self, name: str) -> int:
        for position, (entry, _) in enumerate(self.entries):
            if entry == name:
                return position
        raise KeyError(f"{self.title}: no palette entry named {name!r}")

    def ramp(self, prefix: str) -> tuple[int, ...]:
        return tuple(
            position
            for position, (name, _) in enumerate(self.entries)
            if name.startswith(prefix)
        )

    @property
    def colors(self) -> tuple[RGB, ...]:
        return tuple(color for _, color in self.entries)


PALETTES: dict[str, Palette] = {
    # Free palette, kept in vanilla range: cool violet-grey in the hollows, warm
    # sandstone in the light, so the shading is a hue turn rather than a brightness
    # slider. The two stains are the iron bloom every vanilla stone variant has.
    "stone": Palette(
        "Boss Boulder - Stone",
        "own palette, vanilla stone value range",
        (
            ("crack_deep", (22, 24, 32)),
            ("crack", (32, 35, 44)),
            ("rock_00", (47, 49, 58)),
            ("rock_01", (61, 63, 70)),
            ("rock_02", (75, 77, 82)),
            ("rock_03", (90, 91, 94)),
            ("rock_04", (105, 105, 104)),
            ("rock_05", (121, 120, 115)),
            ("rock_06", (138, 135, 127)),
            ("rock_07", (156, 152, 140)),
            ("rock_08", (176, 171, 155)),
            ("stain_dark", (74, 60, 46)),
            ("stain", (105, 86, 61)),
        ),
    ),
    # The crust is its own near-black warm ramp; the fire is the infernal boss bar's
    # own five burning tones with the chain hook's white-hot on top.
    "magma": Palette(
        "Boss Boulder - Magma",
        "art/boss_bar_infernal + art/aseprite/hook/chain_infernal.gpl",
        (
            ("crack_deep", (14, 11, 12)),
            ("crack", (25, 20, 19)),
            ("rock_00", (37, 28, 25)),
            ("rock_01", (52, 38, 31)),
            ("rock_02", (71, 50, 39)),
            ("rock_03", (96, 74, 60)),
            ("ember_00", (85, 20, 14)),
            ("ember_01", (133, 19, 9)),
            ("ember_02", (188, 31, 8)),
            ("ember_03", (232, 62, 6)),
            ("ember_04", (255, 103, 7)),
            ("ember_05", (255, 190, 31)),
            ("ember_06", (255, 226, 137)),
        ),
    ),
    # Every colour is lifted unchanged from table C of the sculk boss bar builder.
    "sculk": Palette(
        "Boss Boulder - Sculk",
        "table C of art/aseprite/boss_bar/sculk/build_runtime_layers.lua",
        (
            ("crack_deep", (2, 5, 9)),
            ("crack", (4, 9, 16)),
            ("rock_00", (5, 18, 30)),
            ("rock_01", (13, 20, 28)),
            ("rock_02", (24, 35, 44)),
            # Two rungs the boss bar never needed: it draws slate a few pixels wide,
            # a boulder needs the whole facet to turn. Same hue walk as the table's.
            ("rock_03", (31, 45, 55)),
            ("rock_04", (39, 54, 64)),
            ("rock_05", (56, 74, 86)),
            ("growth_00", (3, 22, 29)),
            ("growth_01", (7, 31, 48)),
            ("growth_02", (5, 58, 74)),
            ("growth_03", (6, 79, 96)),
            ("ember_00", (8, 133, 145)),
            ("ember_01", (20, 211, 222)),
            ("ember_02", (118, 246, 238)),
        ),
    ),
    # Stone and moss both come out of the moss cave boss bar; the damp darks and the
    # dry olive are that file's own values, so the boulder matches its dungeon.
    "mossy": Palette(
        "Boss Boulder - Mossy",
        "art/gui/boss_bar_moss_cave.aseprite",
        (
            ("crack_deep", (12, 24, 21)),
            ("crack", (19, 34, 29)),
            ("rock_00", (30, 40, 41)),
            ("rock_01", (44, 54, 56)),
            ("rock_02", (58, 70, 71)),
            ("rock_03", (74, 89, 89)),
            ("rock_04", (92, 110, 110)),
            ("rock_05", (105, 134, 133)),
            ("rock_06", (138, 152, 150)),
            ("moss_00", (3, 91, 46)),
            ("moss_01", (3, 101, 50)),
            ("moss_02", (8, 151, 70)),
            ("moss_03", (21, 202, 100)),
            ("stain", (71, 87, 39)),
        ),
    ),
    # Free palette. Cold violet in the sockets, warm ivory in the light, and two
    # brown sinew tones for the stringy stuff holding the ball of bone together.
    "bone": Palette(
        "Boss Boulder - Bone",
        "own palette, vanilla bone block value range",
        (
            ("crack_deep", (22, 20, 24)),
            ("crack", (38, 33, 34)),
            ("rock_00", (56, 48, 44)),
            ("rock_01", (79, 69, 58)),
            ("rock_02", (103, 91, 74)),
            ("rock_03", (128, 114, 92)),
            ("rock_04", (152, 138, 113)),
            ("rock_05", (177, 163, 135)),
            ("rock_06", (202, 190, 160)),
            ("rock_07", (225, 215, 187)),
            ("stain_dark", (90, 74, 52)),
            ("stain", (123, 102, 71)),
        ),
    ),
    # The hook's ghost chain palette, extended downward with two colder tones so the
    # hollows can go dark without going grey. Nothing here is dimmed with alpha.
    "ghost": Palette(
        "Boss Boulder - Ghost",
        "art/aseprite/hook/ghost.gpl",
        (
            ("crack_deep", (14, 30, 44)),
            ("crack", (26, 52, 70)),
            ("rock_00", (40, 82, 102)),
            ("rock_01", (54, 116, 137)),
            ("rock_02", (66, 142, 165)),
            ("rock_03", (76, 153, 174)),
            ("rock_04", (104, 184, 201)),
            ("rock_05", (148, 217, 228)),
            ("rock_06", (182, 232, 238)),
            ("rock_07", (207, 244, 245)),
            ("ember_00", (90, 206, 226)),
            ("ember_01", (164, 240, 250)),
            ("ember_02", (245, 255, 250)),
        ),
    ),
}


# --------------------------------------------------------------------------------------
# Drawing
# --------------------------------------------------------------------------------------


@dataclass(frozen=True)
class Recipe:
    """The knobs one style turns on the shared rock-and-cracks drawing."""

    seed: int
    cells: int
    cell_radius: float
    crack_width: float
    hairline_cells: int
    hairline_width: float
    hairline_mask: float
    facet_weight: float
    mottle_weight: float
    grain_weight: float
    spread: float
    centre: float
    shoulder: float
    hollow_level: float
    accent_level: float
    accent_scale: float
    accent_ties: bool
    cold_cracks: float
    glowing: bool


# ``accent_level`` above 1.0 switches a style's speckle pass off; the glowing three
# put their detail in the crack network instead. ``cold_cracks`` is the share of the
# network left unlit, as a threshold on a slow field, and is unused when not glowing.
RECIPES: dict[str, Recipe] = {
    "stone": Recipe(
        seed=1301,
        cells=17,
        cell_radius=10.5,
        crack_width=0.95,
        hairline_cells=46,
        hairline_width=0.38,
        hairline_mask=0.02,
        facet_weight=1.00,
        mottle_weight=1.60,
        grain_weight=0.60,
        spread=1.78,
        centre=0.00,
        shoulder=0.00,
        hollow_level=-1.30,
        accent_level=0.42,
        accent_scale=0.62,
        accent_ties=False,
        cold_cracks=-9.00,
        glowing=False,
    ),
    "magma": Recipe(
        seed=2617,
        cells=15,
        cell_radius=10.0,
        crack_width=1.15,
        hairline_cells=40,
        hairline_width=0.34,
        hairline_mask=0.20,
        facet_weight=0.85,
        mottle_weight=1.40,
        grain_weight=0.55,
        spread=1.62,
        centre=0.00,
        shoulder=0.60,
        hollow_level=-1.55,
        accent_level=9.00,
        accent_scale=0.44,
        accent_ties=False,
        cold_cracks=0.10,
        glowing=True,
    ),
    "sculk": Recipe(
        seed=3803,
        cells=16,
        cell_radius=10.5,
        crack_width=1.10,
        hairline_cells=42,
        hairline_width=0.42,
        hairline_mask=0.02,
        facet_weight=0.95,
        mottle_weight=1.45,
        grain_weight=0.55,
        spread=1.86,
        centre=0.00,
        shoulder=0.70,
        hollow_level=-1.25,
        accent_level=9.00,
        accent_scale=0.44,
        accent_ties=False,
        cold_cracks=-0.28,
        glowing=True,
    ),
    "mossy": Recipe(
        seed=4409,
        cells=17,
        cell_radius=10.5,
        crack_width=0.98,
        hairline_cells=46,
        hairline_width=0.38,
        hairline_mask=0.06,
        facet_weight=1.00,
        mottle_weight=1.55,
        grain_weight=0.58,
        spread=1.88,
        centre=0.00,
        shoulder=0.00,
        hollow_level=-1.30,
        accent_level=9.00,
        accent_scale=0.44,
        accent_ties=False,
        cold_cracks=-9.00,
        glowing=False,
    ),
    "bone": Recipe(
        seed=5227,
        cells=17,
        cell_radius=10.2,
        crack_width=1.22,
        hairline_cells=38,
        hairline_width=0.36,
        hairline_mask=0.24,
        facet_weight=1.05,
        mottle_weight=1.45,
        grain_weight=0.50,
        spread=1.86,
        centre=0.06,
        shoulder=0.00,
        hollow_level=-1.45,
        accent_level=0.14,
        accent_scale=0.50,
        accent_ties=True,
        cold_cracks=-9.00,
        glowing=False,
    ),
    "ghost": Recipe(
        seed=6113,
        cells=16,
        cell_radius=10.5,
        crack_width=0.88,
        hairline_cells=40,
        hairline_width=0.40,
        hairline_mask=0.10,
        facet_weight=1.05,
        mottle_weight=1.50,
        grain_weight=0.50,
        spread=1.56,
        centre=0.34,
        shoulder=0.40,
        hollow_level=-1.05,
        accent_level=9.00,
        accent_scale=0.44,
        accent_ties=False,
        cold_cracks=-0.10,
        glowing=True,
    ),
}


@dataclass(frozen=True)
class Sample:
    """Everything the solid fields say about one texel."""

    level: float
    crack: float
    hairline: float
    mottle: float
    grain: float
    facet: float
    hollow: bool


_SAMPLE_CACHE: dict[str, dict[tuple[int, int], Sample]] = {}


def sample_surface(style_id: str) -> dict[tuple[int, int], Sample]:
    """Read all of a style's fields once, at every texel of the sheet."""
    cached = _SAMPLE_CACHE.get(style_id)
    if cached is not None:
        return cached
    recipe = RECIPES[style_id]
    walls = shell_points(recipe.cells, recipe.cell_radius, recipe.seed)
    hairlines = shell_points(recipe.hairline_cells, recipe.cell_radius, recipe.seed + 61)
    samples: dict[tuple[int, int], Sample] = {}
    for pixel, point in surface_points().items():
        # The warp is what turns flat cell walls into broken rock; it is applied to
        # the sample point, so it cannot break the joins between faces.
        broken = warped(point, 2.1, 0.11, recipe.seed + 5)
        first, second, cell = cell_ranks(broken, walls)
        wobble = 1.0 + 0.30 * noise(point, 0.34, recipe.seed + 71)
        crack = recipe.crack_width * wobble - (second - first)

        fine = warped(point, 1.1, 0.21, recipe.seed + 23)
        fine_first, fine_second, _ = cell_ranks(fine, hairlines)
        allow = fbm(point, 0.13, recipe.seed + 41, 2)
        hairline = recipe.hairline_width - (fine_second - fine_first)
        if allow < recipe.hairline_mask:
            hairline = -1.0

        facet = _unit(cell, recipe.seed, 3) * 2.0 - 1.0
        mottle = fbm(point, 0.17, recipe.seed + 11, 3)
        grain = noise(point, 0.55, recipe.seed + 29)
        level = (
            recipe.facet_weight * facet
            + recipe.mottle_weight * mottle
            + recipe.grain_weight * grain
        )
        deep = fbm(point, 0.14, recipe.seed + 53, 2) * 2.0 + 0.7 * facet
        samples[pixel] = Sample(
            level=level,
            crack=crack,
            hairline=hairline,
            mottle=mottle,
            grain=grain,
            facet=facet,
            hollow=deep < recipe.hollow_level,
        )
    _SAMPLE_CACHE[style_id] = samples
    return samples


def ramp_pick(ramp: Sequence[int], level: float, spread: float) -> int:
    """Quantise a field value onto a palette ramp, hard edged, no dithering pass."""
    position = (level / spread + 1.0) * 0.5 * len(ramp)
    return ramp[min(len(ramp) - 1, max(0, int(math.floor(position))))]


def body_ramp(palette: Palette) -> tuple[int, ...]:
    """The whole value ramp of a style, darkest first.

    The two crack tones double as the bottom of it, so a hollow deep enough to lose
    the light lands on the same black the cracks are drawn in instead of needing a
    colour of its own.
    """
    return (palette.index("crack_deep"), palette.index("crack")) + palette.ramp("rock_")


def draw_rock(style_id: str) -> dict[tuple[int, int], tuple[int, str]]:
    """The shared stone body: chipped facets, a crack network and dark hollows."""
    palette = PALETTES[style_id]
    recipe = RECIPES[style_id]
    body = body_ramp(palette)
    rock = palette.ramp("rock_")
    result: dict[tuple[int, int], tuple[int, str]] = {}
    for pixel, sample in sample_surface(style_id).items():
        position = body.index(
            ramp_pick(rock, sample.level - recipe.centre, recipe.spread)
        )
        layer = "rock"
        if sample.hollow:
            position -= 2
        if sample.hairline > 0.0:
            position -= 2
            layer = "cracks"
        if sample.crack > 0.0:
            deep = sample.crack > recipe.crack_width * 0.45
            index = palette.index("crack_deep" if deep else "crack")
            layer = "cracks"
        else:
            index = body[max(0, position)]
        result[pixel] = (index, layer)
    return result


def apply_glow(style_id: str, canvas: dict[tuple[int, int], tuple[int, str]]) -> None:
    """Light the crack network from inside, for the styles drawn at full bright.

    Magma, sculk and ghost are handed to the renderer with the lighting pass turned
    off, so the heat has to be in the drawing: the middle of a crack takes the
    hottest colour and each step outward drops one rung down the ember ramp.
    """
    palette = PALETTES[style_id]
    recipe = RECIPES[style_id]
    embers = palette.ramp("ember_")
    growth = palette.ramp("growth_")
    # Dead sculk first, then the lit vein: one ramp, so the heat falls off the whole
    # way from the middle of a crack out into the rock instead of stopping at a line.
    ramp = growth + embers
    for pixel, sample in sample_surface(style_id).items():
        # The shoulder is what carries the light out onto the crust, and it is broken
        # up so a lit crack has a ragged edge rather than a piped outline.
        edge = max(sample.crack, sample.hairline * 1.6) + recipe.shoulder * 0.55 * fbm(
            pixel_point(pixel), 0.30, recipe.seed + 113, 2
        )
        if edge <= -recipe.shoulder:
            continue
        # Not every crack is alight. A slow field decides which stretches of the
        # network burnt out, which is what keeps a lit boulder reading as rock.
        if fbm(pixel_point(pixel), 0.15, recipe.seed + 107, 2) < recipe.cold_cracks:
            continue
        heat = (edge + recipe.shoulder) / (recipe.crack_width + recipe.shoulder)
        heat += 0.10 * sample.grain
        step = int(heat * len(ramp))
        canvas[pixel] = (ramp[min(len(ramp) - 1, max(0, step))], "cracks")


def apply_moss(style_id: str, canvas: dict[tuple[int, int], tuple[int, str]]) -> None:
    """Moss in scattered patches rather than one cap.

    The field is deliberately short-wavelength: a single large mat would give the
    boulder a top, and would show up three times over on the three turned shells.
    """
    palette = PALETTES[style_id]
    recipe = RECIPES[style_id]
    moss = palette.ramp("moss_")
    dry = palette.index("stain")
    for pixel, sample in sample_surface(style_id).items():
        cover = fbm(pixel_point(pixel), 0.32, recipe.seed + 83, 2) + 0.22 * sample.mottle
        if cover < 0.16:
            continue
        # Moss bridges a crack only where it is already thick, so the crack network
        # keeps reading through the green instead of being swallowed by it.
        if canvas[pixel][1] == "cracks" and cover < 0.44:
            continue
        depth = cover + 0.30 * sample.grain + 0.20 * sample.facet
        index = dry if depth < 0.10 else ramp_pick(moss, depth - 0.42, 0.40)
        canvas[pixel] = (index, "detail")


def apply_accents(style_id: str, canvas: dict[tuple[int, int], tuple[int, str]]) -> None:
    """The iron bloom on grey stone and the sinew tying a ball of bone together."""
    palette = PALETTES[style_id]
    recipe = RECIPES[style_id]
    if recipe.accent_level > 1.0:
        return
    dark = palette.index("stain_dark")
    light = palette.index("stain")
    for pixel, sample in sample_surface(style_id).items():
        on_crack = canvas[pixel][1] == "cracks"
        # Sinew is drawn across the gaps, iron bloom only on the face of the rock.
        if on_crack != recipe.accent_ties:
            continue
        bloom = (
            fbm(pixel_point(pixel), recipe.accent_scale, recipe.seed + 97, 2)
            + 0.25 * sample.grain
        )
        if bloom < recipe.accent_level:
            continue
        canvas[pixel] = (light if sample.level > 0.0 else dark, "detail")


_POINT_CACHE: dict[tuple[int, int], Vec3] = {}


def pixel_point(pixel: tuple[int, int]) -> Vec3:
    if not _POINT_CACHE:
        _POINT_CACHE.update(surface_points())
    return _POINT_CACHE[pixel]


def render(style_id: str) -> dict[str, Image.Image]:
    """Draw one style as the named layers the ``.aseprite`` source keeps apart."""
    palette = PALETTES[style_id]
    canvas = draw_rock(style_id)
    if RECIPES[style_id].glowing:
        apply_glow(style_id, canvas)
    if style_id == "mossy":
        apply_moss(style_id, canvas)
    apply_accents(style_id, canvas)

    layers = {name: Image.new("RGBA", CANVAS_SIZE, TRANSPARENT) for name in LAYER_NAMES}
    for (x, y), (index, layer) in canvas.items():
        layers[layer].putpixel((x, y), (*palette.colors[index], 255))
    return layers


def composite(layers: dict[str, Image.Image]) -> Image.Image:
    result = Image.new("RGBA", CANVAS_SIZE, TRANSPARENT)
    for name in LAYER_NAMES:
        result.alpha_composite(layers[name])
    return result


# --------------------------------------------------------------------------------------
# Files
# --------------------------------------------------------------------------------------


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def write_gpl(style_id: str) -> None:
    palette = PALETTES[style_id]
    lines = [
        "GIMP Palette",
        f"Name: {palette.title}",
        "Columns: 8",
        f"# {palette.source}",
        "#",
    ]
    for name, color in palette.entries:
        lines.append(f"{color[0]:3d} {color[1]:3d} {color[2]:3d}\t{name}")
    path = ART_DIR / f"{style_id}.gpl"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def aseprite_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack("<H", len(encoded)) + encoded


def aseprite_chunk(chunk_type: int, payload: bytes) -> bytes:
    return struct.pack("<IH", 6 + len(payload), chunk_type) + payload


def aseprite_layer_chunk(name: str) -> bytes:
    payload = struct.pack(
        "<6HB3s", 3, 0, 0, CANVAS_SIZE[0], CANVAS_SIZE[1], 0, 255, b"\0" * 3
    )
    return aseprite_chunk(0x2004, payload + aseprite_string(name))


def aseprite_cel_chunk(layer_index: int, image: Image.Image) -> bytes:
    payload = struct.pack(
        "<HhhBHh5sHH",
        layer_index,
        0,
        0,
        255,
        2,
        0,
        b"\0" * 5,
        CANVAS_SIZE[0],
        CANVAS_SIZE[1],
    )
    return aseprite_chunk(0x2005, payload + zlib.compress(image.tobytes(), level=9))


def encode_aseprite(layers: dict[str, Image.Image]) -> bytes:
    chunks = [aseprite_layer_chunk(name) for name in LAYER_NAMES]
    chunks += [
        aseprite_cel_chunk(index, layers[name]) for index, name in enumerate(LAYER_NAMES)
    ]
    frame_size = 16 + sum(len(chunk) for chunk in chunks)
    frame = (
        struct.pack("<IHHH2sI", frame_size, 0xF1FA, len(chunks), 100, b"\0" * 2, len(chunks))
        + b"".join(chunks)
    )
    header = struct.pack(
        "<IHHHHHIHII B3sHBBhhHH84s",
        128 + len(frame),
        0xA5E0,
        1,
        CANVAS_SIZE[0],
        CANVAS_SIZE[1],
        32,
        1,
        100,
        0,
        0,
        0,
        b"\0" * 3,
        0,
        1,
        1,
        0,
        0,
        0,
        0,
        b"\0" * 84,
    )
    return header + frame


def decode_aseprite(data: bytes) -> dict[str, Image.Image]:
    file_size, magic, frames, width, height, depth = struct.unpack_from("<I5H", data, 0)
    if file_size != len(data) or magic != 0xA5E0:
        raise AssertionError("invalid Aseprite file header")
    if (frames, width, height, depth) != (1, *CANVAS_SIZE, 32):
        raise AssertionError("unexpected Aseprite canvas, frame count or colour depth")

    offset = 128
    frame_size, frame_magic, old_count = struct.unpack_from("<IHH", data, offset)
    if frame_magic != 0xF1FA:
        raise AssertionError("invalid Aseprite frame")
    count = struct.unpack_from("<I", data, offset + 12)[0] or old_count
    offset += 16
    names: list[str] = []
    cels: dict[int, Image.Image] = {}
    for _ in range(count):
        chunk_size, chunk_type = struct.unpack_from("<IH", data, offset)
        payload = offset + 6
        if chunk_type == 0x2004:
            length = struct.unpack_from("<H", data, payload + 16)[0]
            names.append(data[payload + 18:payload + 18 + length].decode("utf-8"))
        elif chunk_type == 0x2005:
            layer_index = struct.unpack_from("<H", data, payload)[0]
            cel_type = struct.unpack_from("<H", data, payload + 7)[0]
            cel_size = struct.unpack_from("<HH", data, payload + 16)
            if cel_type != 2 or cel_size != CANVAS_SIZE:
                raise AssertionError("unsupported Aseprite cel")
            raw = zlib.decompress(data[payload + 20:offset + chunk_size])
            cels[layer_index] = Image.frombytes("RGBA", CANVAS_SIZE, raw)
        offset += chunk_size
    if offset != 128 + frame_size or offset != len(data):
        raise AssertionError("Aseprite frame size mismatch")
    if tuple(names) != LAYER_NAMES or set(cels) != set(range(len(LAYER_NAMES))):
        raise AssertionError("Aseprite source has the wrong layers")
    return {name: cels[index] for index, name in enumerate(LAYER_NAMES)}


# --------------------------------------------------------------------------------------
# Previews
# --------------------------------------------------------------------------------------

# Output pixel -> source pixel, for the three faces that meet at the near top corner
# of a 2:1 isometric cube. Derived from the same unwrap table the drawing uses, so
# the assembled view shows the real joins rather than a hand-placed approximation.
ISO_FACES: tuple[tuple[str, tuple[float, ...]], ...] = (
    ("left", (1.0, 0.0, 0.0, -0.5, 1.0, -32.0)),
    ("front", (1.0, 0.0, -64.0, 0.5, 1.0, -96.0)),
    ("top", (0.5, -1.0, 32.0, 0.5, 1.0, -32.0)),
)
ISO_SIZE = (128, 128)

# A 3x5 pencil alphabet, only the letters the six style ids need.
FONT_3x5: dict[str, tuple[str, ...]] = {
    "a": ("###", "#.#", "###", "#.#", "#.#"),
    "b": ("##.", "#.#", "##.", "#.#", "##."),
    "c": (".##", "#..", "#..", "#..", ".##"),
    "e": ("###", "#..", "##.", "#..", "###"),
    "g": (".##", "#..", "#.#", "#.#", ".##"),
    "h": ("#.#", "#.#", "###", "#.#", "#.#"),
    "k": ("#.#", "##.", "#..", "##.", "#.#"),
    "l": ("#..", "#..", "#..", "#..", "###"),
    "m": ("#.#", "###", "###", "#.#", "#.#"),
    "n": ("##.", "#.#", "#.#", "#.#", "#.#"),
    "o": (".#.", "#.#", "#.#", "#.#", ".#."),
    "s": (".##", "#..", ".#.", "..#", "##."),
    "t": ("###", ".#.", ".#.", ".#.", ".#."),
    "u": ("#.#", "#.#", "#.#", "#.#", ".##"),
    "y": ("#.#", "#.#", ".#.", ".#.", ".#."),
    " ": ("...", "...", "...", "...", "..."),
}


def draw_label(image: Image.Image, text: str, origin: tuple[int, int], scale: int) -> None:
    """A 3x5 pencil font, so the contact sheet names its own styles."""
    color = (236, 240, 238, 255)
    cursor = origin[0]
    for character in text:
        glyph = FONT_3x5.get(character, FONT_3x5[" "])
        for row, line in enumerate(glyph):
            for column, cell in enumerate(line):
                if cell != "#":
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        image.putpixel(
                            (cursor + column * scale + dx, origin[1] + row * scale + dy),
                            color,
                        )
        cursor += 4 * scale
    return None


def backdrop(size: tuple[int, int], light: bool) -> Image.Image:
    """Dungeon wall on the dark panel, daylit stone on the light one."""
    base = (176, 179, 176, 255) if light else (23, 26, 29, 255)
    mortar = (139, 143, 141, 255) if light else (13, 16, 18, 255)
    fleck = (198, 200, 194, 255) if light else (38, 43, 46, 255)
    image = Image.new("RGBA", size, base)
    for y in range(size[1]):
        for x in range(size[0]):
            block_y = y // 24
            offset = 0 if block_y % 2 == 0 else 20
            if y % 24 < 2 or (x + offset) % 40 < 2:
                image.putpixel((x, y), mortar)
            elif (x * 7 + y * 13) % 71 == 0:
                image.putpixel((x, y), fleck)
    return image


def assemble(texture: Image.Image) -> Image.Image:
    """Three joined faces of the real cube, seen as a 2:1 isometric lump."""
    result = Image.new("RGBA", ISO_SIZE, TRANSPARENT)
    faces = {face.name: face for face in FACES}
    for name, matrix in ISO_FACES:
        origin = faces[name].origin
        island = texture.crop((origin[0], origin[1], origin[0] + 16, origin[1] + 16))
        island = island.resize((64, 64), Image.Resampling.NEAREST)
        panel = island.transform(ISO_SIZE, Image.Transform.AFFINE, matrix, Image.Resampling.NEAREST)
        mask = Image.new("L", (64, 64), 255).transform(
            ISO_SIZE, Image.Transform.AFFINE, matrix, Image.Resampling.NEAREST
        )
        panel.putalpha(mask)
        result.alpha_composite(panel)
    return result


def unwrap_sheet(texture: Image.Image) -> Image.Image:
    """The flat unwrap at 4x with a hairline box around each island."""
    sheet = texture.resize((256, 128), Image.Resampling.NEAREST)
    guide = Image.new("RGBA", (256, 128), TRANSPARENT)
    for face in FACES:
        x0, y0 = face.origin[0] * 4, face.origin[1] * 4
        for step in range(64):
            for pixel in (
                (x0 + step, y0),
                (x0 + step, y0 + 63),
                (x0, y0 + step),
                (x0 + 63, y0 + step),
            ):
                guide.putpixel(pixel, (255, 92, 92, 90))
    sheet.alpha_composite(guide)
    return sheet


def write_preview(style_id: str, texture: Image.Image) -> None:
    panel = (288, 320)
    preview = Image.new("RGBA", (panel[0] * 2, panel[1]), TRANSPARENT)
    sheet = unwrap_sheet(texture)
    lump = assemble(texture)
    for index, light in enumerate((False, True)):
        page = backdrop(panel, light)
        page.alpha_composite(sheet, (16, 16))
        page.alpha_composite(lump, (16, 168))
        page.alpha_composite(lump, (152, 168))
        preview.alpha_composite(page, (index * panel[0], 0))
    save_png(preview, PREVIEW_DIR / f"{style_id}_4x.png")


def write_contact_sheet() -> None:
    panel = (288, 344)
    sheet = Image.new("RGBA", (panel[0] * len(STYLE_IDS), panel[1]), TRANSPARENT)
    for index, style_id in enumerate(STYLE_IDS):
        texture = Image.open(ENTITY_DIR / f"{style_id}.png").convert("RGBA")
        page = backdrop(panel, light=False)
        page.alpha_composite(backdrop((128, 128), light=True), (152, 192))
        draw_label(page, style_id, (16, 12), 3)
        page.alpha_composite(unwrap_sheet(texture), (16, 40))
        lump = assemble(texture)
        page.alpha_composite(lump, (16, 192))
        page.alpha_composite(lump, (152, 192))
        sheet.alpha_composite(page, (index * panel[0], 0))
    save_png(sheet, PREVIEW_DIR / "all_styles_4x.png")


# --------------------------------------------------------------------------------------
# Validation
# --------------------------------------------------------------------------------------


def luminance(color: RGB) -> float:
    return 0.2126 * color[0] + 0.7152 * color[1] + 0.0722 * color[2]


def face_pixels(texture: Image.Image, face: Face) -> list[list[RGB]]:
    return [
        [
            texture.getpixel((face.origin[0] + column, face.origin[1] + row))[:3]
            for column in range(16)
        ]
        for row in range(16)
    ]


def face_of(pixel: tuple[int, int]) -> str:
    for face in FACES:
        if (
            face.origin[0] <= pixel[0] < face.origin[0] + 16
            and face.origin[1] <= pixel[1] < face.origin[1] + 16
        ):
            return face.name
    raise KeyError(f"{pixel} is not inside an island")


_NEIGHBOUR_CACHE: dict[tuple[int, int], tuple[tuple[int, int], ...]] = {}


def surface_neighbours() -> dict[tuple[int, int], tuple[tuple[int, int], ...]]:
    """Which texels touch which, measured on the cube rather than on the sheet.

    Two texels on the same face sit one model unit apart; two that straddle a cube
    edge sit about 0.71 apart, because each is half a texel back from the edge. One
    cut-off at 1.05 therefore picks up both, and nothing else - a diagonal is 1.41
    away and the far side of a seam's neighbour is 1.22.
    """
    if _NEIGHBOUR_CACHE:
        return _NEIGHBOUR_CACHE
    points = list(surface_points().items())
    for pixel, point in points:
        touching = []
        for other, spot in points:
            if other == pixel:
                continue
            distance = math.sqrt(
                sum((spot[axis] - point[axis]) ** 2 for axis in range(3))
            )
            if distance <= 1.05:
                touching.append(other)
        _NEIGHBOUR_CACHE[pixel] = tuple(touching)
    return _NEIGHBOUR_CACHE


def seam_report(texture: Image.Image) -> tuple[float, float]:
    """How hard the drawing jumps across a cube edge, against its own interior.

    Both numbers are a mean absolute luminance step between touching texels: one
    over the pairs that straddle one of the twelve cube edges, one over the pairs
    inside a face. A seam nobody can see is a seam whose step is no worse than the
    ordinary texture of the rock around it.
    """
    lookup = {pixel: texture.getpixel(pixel)[:3] for pixel in surface_points()}
    across: list[float] = []
    inside: list[float] = []
    for pixel, touching in surface_neighbours().items():
        for other in touching:
            if other < pixel:
                continue
            step = abs(luminance(lookup[pixel]) - luminance(lookup[other]))
            if face_of(pixel) == face_of(other):
                inside.append(step)
            else:
                across.append(step)
    return sum(across) / len(across), sum(inside) / len(inside)


_DISC_CACHE: dict[tuple[int, int], tuple[tuple[int, int], ...]] = {}


def surface_discs(radius: float = 3.5) -> dict[tuple[int, int], tuple[tuple[int, int], ...]]:
    """Every texel within ``radius`` model units of each texel, measured on the cube."""
    if _DISC_CACHE:
        return _DISC_CACHE
    points = list(surface_points().items())
    limit = radius * radius
    for pixel, point in points:
        near = tuple(
            other
            for other, spot in points
            if sum((spot[axis] - point[axis]) ** 2 for axis in range(3)) <= limit
        )
        _DISC_CACHE[pixel] = near
    return _DISC_CACHE


def peak_density(texture: Image.Image, wanted: set[RGB]) -> float:
    """The most crowded a set of colours ever gets inside one small patch of surface.

    One drawing dresses three turned copies of the same shape, so a solid cap of moss
    or a lake of lava is seen three times at once and hands the boulder an up. A
    network of veins spread over the whole stone scores low here; a cap scores 1.0.
    """
    lookup = {pixel: texture.getpixel(pixel)[:3] in wanted for pixel in surface_points()}
    worst = 0.0
    for near in surface_discs().values():
        hits = sum(lookup[other] for other in near)
        worst = max(worst, hits / len(near))
    return worst


def accent_bias(texture: Image.Image, wanted: set[RGB]) -> float:
    """How far off centre a style's accents sit, as a share of the whole surface.

    Each accent texel is taken as a direction out of the middle of the boulder and
    the directions are averaged. Spread evenly they cancel to nothing; gathered into
    a cap on one side - moss on a top, lava on a face - they add up towards one, and
    the boulder has been given an up that its three turned shells will repeat.
    """
    total = [0.0, 0.0, 0.0]
    count = 0
    for pixel, point in surface_points().items():
        if texture.getpixel(pixel)[:3] not in wanted:
            continue
        length = math.sqrt(sum(value * value for value in point))
        for axis in range(3):
            total[axis] += point[axis] / length
        count += 1
    if not count:
        return 0.0
    return math.sqrt(sum(value * value for value in total)) / count


def largest_blob(texture: Image.Image) -> int:
    """The biggest single-colour patch, which is the flattest the drawing ever goes."""
    points = surface_points()
    lookup = {pixel: texture.getpixel(pixel)[:3] for pixel in points}
    seen: set[tuple[int, int]] = set()
    biggest = 0
    for start in points:
        if start in seen:
            continue
        color = lookup[start]
        stack = [start]
        seen.add(start)
        size = 0
        while stack:
            pixel = stack.pop()
            size += 1
            for other in surface_neighbours()[pixel]:
                if other not in seen and lookup[other] == color:
                    seen.add(other)
                    stack.append(other)
        biggest = max(biggest, size)
    return biggest


def validate_style(style_id: str) -> str:
    palette = PALETTES[style_id]
    texture = Image.open(ENTITY_DIR / f"{style_id}.png").convert("RGBA")
    if texture.size != CANVAS_SIZE:
        raise AssertionError(f"{style_id}: the sheet must be 64x32, got {texture.size}")

    islands = set(surface_points())
    semi = 0
    outside = 0
    inside_clear = 0
    colors: set[RGB] = set()
    for y in range(CANVAS_SIZE[1]):
        for x in range(CANVAS_SIZE[0]):
            pixel = texture.getpixel((x, y))
            if 0 < pixel[3] < 255:
                semi += 1
            if (x, y) in islands:
                if pixel[3] != 255:
                    inside_clear += 1
                colors.add(pixel[:3])
            elif pixel[3] != 0:
                outside += 1
    if semi or outside or inside_clear:
        raise AssertionError(
            f"{style_id}: semi-alpha={semi}, painted outside islands={outside}, "
            f"holes inside islands={inside_clear}"
        )

    allowed = set(palette.colors)
    if not colors <= allowed:
        raise AssertionError(f"{style_id}: the sheet uses colours outside its .gpl")
    if colors != allowed:
        unused = sorted(name for name, color in palette.entries if color not in colors)
        raise AssertionError(f"{style_id}: palette entries never drawn: {unused}")
    if not MIN_COLORS <= len(colors) <= MAX_COLORS:
        raise AssertionError(f"{style_id}: {len(colors)} colours is outside 10..16")

    seam, interior = seam_report(texture)
    if seam > interior * 1.25:
        raise AssertionError(
            f"{style_id}: the cube edges jump harder than the rock does "
            f"(seam {seam:.1f} vs interior {interior:.1f})"
        )

    blob = largest_blob(texture)
    if blob > MAX_FLAT_PATCH:
        raise AssertionError(
            f"{style_id}: a flat patch of {blob} texels would read as a repeat"
        )

    # One drawing dresses three turned copies of the same shape, so a single big
    # landmark - a moss cap, a lava lake - is seen three times at once and gives the
    # boulder an up. Both the accent colours and the plain rock have to stay broken
    # up over the whole surface.
    accents = {
        color
        for name, color in palette.entries
        if name.split("_")[0] in ("ember", "growth", "moss", "stain")
    }
    crowd = peak_density(texture, accents)
    bias = accent_bias(texture, accents)
    if crowd > MAX_ACCENT_DENSITY:
        raise AssertionError(
            f"{style_id}: accents fill {crowd:.0%} of one patch and read as a solid lake"
        )
    if bias > MAX_ACCENT_BIAS:
        raise AssertionError(
            f"{style_id}: accents pull {bias:.2f} to one side and give the boulder an up"
        )

    source = decode_aseprite((ART_DIR / f"{style_id}.aseprite").read_bytes())
    if composite(source).tobytes() != texture.tobytes():
        raise AssertionError(f"{style_id}: the .aseprite source does not flatten to the PNG")

    gpl = (ART_DIR / f"{style_id}.gpl").read_text(encoding="utf-8").splitlines()
    listed = [
        tuple(int(part) for part in line.split("\t")[0].split())
        for line in gpl
        if line and not line.startswith(("GIMP", "Name:", "Columns:", "#"))
    ]
    if tuple(listed) != palette.colors:
        raise AssertionError(f"{style_id}: the .gpl does not match the drawn palette")

    return (
        f"{style_id:6s} {texture.size[0]}x{texture.size[1]} colours {len(colors):2d} "
        f"seam {seam:5.2f} interior {interior:5.2f} flat {blob:3d} crowd {crowd:4.0%} bias {bias:4.2f}"
    )


def build_style(style_id: str) -> None:
    layers = render(style_id)
    texture = composite(layers)
    save_png(texture, ENTITY_DIR / f"{style_id}.png")
    write_gpl(style_id)
    path = ART_DIR / f"{style_id}.aseprite"
    path.parent.mkdir(parents=True, exist_ok=True)
    data = encode_aseprite(layers)
    if composite(decode_aseprite(data)).tobytes() != texture.tobytes():
        raise AssertionError(f"{style_id}: the written .aseprite does not read back")
    path.write_bytes(data)
    write_preview(style_id, texture)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--style", choices=STYLE_IDS, help="build a single style")
    parser.add_argument(
        "--check", action="store_true", help="validate the committed files, write nothing"
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    styles: Iterable[str] = (args.style,) if args.style else STYLE_IDS
    if not args.check:
        for style_id in styles:
            build_style(style_id)
        write_contact_sheet()
    for style_id in styles:
        print(validate_style(style_id))
    return 0


if __name__ == "__main__":
    sys.exit(main())

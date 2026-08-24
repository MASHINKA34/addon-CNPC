from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import os
import struct
import sys
import zipfile
import zlib

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ART_DIR = ROOT / "art" / "aseprite" / "chest"
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
    / "chest"
)
BLOCK_DIR = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "cnpcgeckoaddon"
    / "textures"
    / "block"
)

CANVAS_SIZE = (64, 64)
PARTICLE_SIZE = (16, 16)
FRAME_COUNT = 2
FRAME_DURATION_MS = 100
TRANSPARENT = (0, 0, 0, 0)
VANILLA_ENTRY = "assets/minecraft/textures/entity/chest/normal.png"
STYLE_IDS = ("moss_cave", "infernal", "ghost", "sculk", "gilded", "bone")


@dataclass(frozen=True)
class Style:
    title: str
    palette: dict[str, tuple[int, int, int, int]]
    glow: bool = False


def opaque(red: int, green: int, blue: int) -> tuple[int, int, int, int]:
    return red, green, blue, 255


STYLES = {
    "moss_cave": Style(
        "Moss Cave Boss Chest",
        {
            "outline": opaque(12, 24, 21),
            "deepest": opaque(19, 34, 29),
            "shadow": opaque(39, 43, 32),
            "base": opaque(55, 61, 39),
            "mid": opaque(76, 82, 48),
            "light": opaque(104, 106, 58),
            "highlight": opaque(152, 152, 94),
            "trim_dark": opaque(43, 52, 55),
            "trim": opaque(56, 70, 70),
            "trim_light": opaque(84, 104, 105),
            "accent_dark": opaque(22, 36, 32),
            "accent": opaque(3, 101, 50),
            "accent_light": opaque(10, 136, 67),
            "hot": opaque(105, 134, 133),
        },
    ),
    "infernal": Style(
        "Infernal Boss Chest",
        {
            "outline": opaque(0, 0, 0),
            "deepest": opaque(9, 9, 9),
            "shadow": opaque(13, 13, 13),
            "base": opaque(21, 21, 21),
            "mid": opaque(23, 23, 23),
            "light": opaque(24, 25, 24),
            "highlight": opaque(42, 43, 44),
            "trim_dark": opaque(20, 23, 31),
            "trim": opaque(35, 40, 51),
            "trim_light": opaque(60, 63, 72),
            "accent_dark": opaque(101, 25, 20),
            "accent": opaque(174, 42, 26),
            "accent_light": opaque(237, 85, 27),
            "hot": opaque(255, 166, 57),
        },
        glow=True,
    ),
    "ghost": Style(
        "Ghost Dungeon Boss Chest",
        {
            "outline": opaque(19, 22, 34),
            "deepest": opaque(44, 67, 76),
            "shadow": opaque(76, 153, 174),
            "base": opaque(104, 184, 201),
            "mid": opaque(148, 217, 228),
            "light": opaque(207, 244, 245),
            "highlight": opaque(245, 255, 250),
            "trim_dark": opaque(54, 116, 137),
            "trim": opaque(66, 142, 165),
            "trim_light": opaque(148, 217, 228),
            "accent_dark": opaque(45, 100, 121),
            "accent": opaque(104, 184, 201),
            "accent_light": opaque(207, 244, 245),
            "hot": opaque(245, 255, 250),
        },
        glow=True,
    ),
    "sculk": Style(
        "Sculk Boss Chest",
        {
            "outline": opaque(2, 5, 9),
            "deepest": opaque(4, 9, 16),
            "shadow": opaque(13, 20, 28),
            "base": opaque(24, 35, 44),
            "mid": opaque(39, 54, 64),
            "light": opaque(67, 91, 99),
            "highlight": opaque(118, 126, 128),
            "trim_dark": opaque(3, 22, 29),
            "trim": opaque(5, 58, 74),
            "trim_light": opaque(6, 79, 96),
            "accent_dark": opaque(7, 31, 48),
            "accent": opaque(8, 133, 145),
            "accent_light": opaque(20, 211, 222),
            "hot": opaque(118, 246, 238),
        },
        glow=True,
    ),
    "gilded": Style(
        "Gilded Treasury Boss Chest",
        {
            "outline": opaque(18, 12, 10),
            "deepest": opaque(32, 19, 15),
            "shadow": opaque(49, 28, 20),
            "base": opaque(70, 39, 25),
            "mid": opaque(94, 52, 29),
            "light": opaque(128, 74, 38),
            "highlight": opaque(167, 103, 52),
            "trim_dark": opaque(101, 64, 14),
            "trim": opaque(151, 101, 26),
            "trim_light": opaque(218, 165, 42),
            "accent_dark": opaque(126, 82, 18),
            "accent": opaque(218, 165, 42),
            "accent_light": opaque(255, 220, 91),
            "hot": opaque(255, 241, 166),
        },
    ),
    "bone": Style(
        "Bone Boss Chest",
        {
            "outline": opaque(25, 22, 21),
            "deepest": opaque(43, 35, 32),
            "shadow": opaque(91, 82, 71),
            "base": opaque(128, 114, 93),
            "mid": opaque(174, 158, 126),
            "light": opaque(215, 204, 169),
            "highlight": opaque(238, 231, 199),
            "trim_dark": opaque(79, 51, 42),
            "trim": opaque(122, 75, 54),
            "trim_light": opaque(168, 111, 75),
            "accent_dark": opaque(63, 55, 49),
            "accent": opaque(105, 96, 78),
            "accent_light": opaque(153, 137, 104),
            "hot": opaque(231, 218, 178),
        },
    ),
}


FACES = {
    "lid_top": (14, 0, 14, 14),
    "lid_bottom": (28, 0, 14, 14),
    "lid_left": (0, 14, 14, 5),
    "lid_front": (14, 14, 14, 5),
    "lid_right": (28, 14, 14, 5),
    "lid_back": (42, 14, 14, 5),
    "body_top": (14, 19, 14, 14),
    "body_bottom": (28, 19, 14, 14),
    "body_left": (0, 33, 14, 10),
    "body_front": (14, 33, 14, 10),
    "body_right": (28, 33, 14, 10),
    "body_back": (42, 33, 14, 10),
}


def candidate_vanilla_jars() -> list[Path]:
    candidates = sorted((ROOT / "build" / "moddev" / "artifacts").glob("*minecraft-resources-aka-client-extra.jar"))
    profile = Path(os.environ.get("USERPROFILE", ""))
    if profile:
        candidates.extend(
            [
                profile / ".gradle" / "caches" / "neoformruntime" / "artifacts" / "minecraft_1.21.1_client.jar",
                profile / ".gradle" / "caches" / "fabric-loom" / "1.21.1" / "minecraft-client.jar",
            ]
        )
    return candidates


def load_vanilla_template(explicit_path: Path | None) -> tuple[Image.Image, str]:
    if explicit_path:
        image = Image.open(explicit_path).convert("RGBA")
        source = str(explicit_path)
    else:
        image = None
        source = ""
        for jar_path in candidate_vanilla_jars():
            if not jar_path.is_file():
                continue
            try:
                with zipfile.ZipFile(jar_path) as archive:
                    with archive.open(VANILLA_ENTRY) as stream:
                        image = Image.open(stream).convert("RGBA")
                        image.load()
                    source = f"{jar_path}!/{VANILLA_ENTRY}"
                    break
            except (KeyError, zipfile.BadZipFile):
                continue
        if image is None:
            raise FileNotFoundError(
                "Could not find Minecraft 1.21.1 normal chest texture; pass --vanilla PATH"
            )
    if image.size != CANVAS_SIZE:
        raise ValueError(f"Vanilla template must be 64x64, got {image.size}")
    alpha_values = set(flat_pixels(image.getchannel("A")))
    if not alpha_values <= {0, 255} or 255 not in alpha_values:
        raise ValueError("Vanilla template must use a binary alpha mask")
    return image, source


def new_layer() -> Image.Image:
    return Image.new("RGBA", CANVAS_SIZE, TRANSPARENT)


def flat_pixels(image: Image.Image):
    if hasattr(image, "get_flattened_data"):
        return image.get_flattened_data()
    return image.getdata()


def set_pixel(
    image: Image.Image,
    mask: Image.Image,
    x: int,
    y: int,
    color: tuple[int, int, int, int],
) -> None:
    if 0 <= x < 64 and 0 <= y < 64 and mask.getpixel((x, y)):
        image.putpixel((x, y), color)


def fill_box(
    image: Image.Image,
    mask: Image.Image,
    box: tuple[int, int, int, int],
    color: tuple[int, int, int, int],
) -> None:
    x0, y0, width, height = box
    for y in range(y0, y0 + height):
        for x in range(x0, x0 + width):
            set_pixel(image, mask, x, y, color)


def paint_points(
    image: Image.Image,
    mask: Image.Image,
    points: tuple[tuple[int, int], ...] | list[tuple[int, int]],
    color: tuple[int, int, int, int],
) -> None:
    for x, y in points:
        set_pixel(image, mask, x, y, color)


def paint_path(
    image: Image.Image,
    mask: Image.Image,
    points: tuple[tuple[int, int], ...] | list[tuple[int, int]],
    color: tuple[int, int, int, int],
) -> None:
    segment = Image.new("RGBA", CANVAS_SIZE, TRANSPARENT)
    ImageDraw.Draw(segment).line(points, fill=color, width=1)
    for x, y, pixel in pixel_positions(segment):
        set_pixel(image, mask, x, y, pixel)


def pixel_positions(image: Image.Image):
    alpha = image.getchannel("A")
    box = alpha.getbbox()
    if box is None:
        return
    for y in range(box[1], box[3]):
        for x in range(box[0], box[2]):
            pixel = image.getpixel((x, y))
            if pixel[3]:
                yield x, y, pixel


def shade_chest(
    layers: dict[str, Image.Image],
    mask: Image.Image,
    palette: dict[str, tuple[int, int, int, int]],
) -> None:
    shading = layers["shading"]
    for name, box in FACES.items():
        if name.endswith("top"):
            face_color = palette["mid"]
        elif name.endswith("bottom"):
            face_color = palette["shadow"]
        elif name.startswith("lid"):
            face_color = palette["mid"]
        else:
            face_color = palette["base"]
        fill_box(shading, mask, box, face_color)

        x0, y0, width, height = box
        for x in range(x0, x0 + width):
            set_pixel(shading, mask, x, y0, palette["outline"])
            set_pixel(shading, mask, x, y0 + height - 1, palette["deepest"])
        for y in range(y0, y0 + height):
            set_pixel(shading, mask, x0, y, palette["outline"])
            set_pixel(shading, mask, x0 + width - 1, y, palette["outline"])

        if height >= 10:
            split_y = y0 + height // 2
            for x in range(x0 + 1, x0 + width - 1):
                set_pixel(shading, mask, x, split_y, palette["shadow"])
            for x in range(x0 + 2, x0 + width - 2, 4):
                set_pixel(shading, mask, x, y0 + 2, palette["light"])
                set_pixel(shading, mask, x + 1, y0 + 2, palette["light"])
        elif height == 5:
            for x in range(x0 + 2, x0 + width - 2, 5):
                set_pixel(shading, mask, x, y0 + 1, palette["light"])
                set_pixel(shading, mask, x + 1, y0 + 1, palette["light"])
        else:
            for local_y in (4, 9):
                for x in range(x0 + 1, x0 + width - 1):
                    set_pixel(shading, mask, x, y0 + local_y, palette["shadow"])
            for y in range(y0 + 2, y0 + height - 2, 4):
                for x in range(x0 + 2, x0 + width - 2, 5):
                    set_pixel(shading, mask, x, y, palette["light"])
                    set_pixel(shading, mask, x + 1, y, palette["light"])

    # The latch uses the exact vanilla cube mask in the upper-left corner.
    for y in range(0, 5):
        for x in range(0, 6):
            if not mask.getpixel((x, y)):
                continue
            edge = x in (0, 5) or y in (0, 4)
            shading.putpixel((x, y), palette["trim_dark"] if edge else palette["trim"])
    paint_points(shading, mask, ((2, 1), (2, 2), (3, 1)), palette["trim_light"])


def add_trim(
    layer: Image.Image,
    mask: Image.Image,
    palette: dict[str, tuple[int, int, int, int]],
    width: int = 1,
) -> None:
    for y0, height in ((14, 5), (33, 10)):
        for left_edge, right_edge in ((13, 14), (27, 28), (41, 42), (55, 0)):
            for offset in range(width):
                for y in range(y0, y0 + height):
                    set_pixel(layer, mask, (left_edge - offset) % 56, y, palette["trim_dark"])
                    set_pixel(layer, mask, (right_edge + offset) % 56, y, palette["trim_dark"])
            for y in range(y0 + 1, y0 + height - 1):
                set_pixel(layer, mask, (left_edge - width) % 56, y, palette["trim"])
                set_pixel(layer, mask, (right_edge + width) % 56, y, palette["trim"])
        for x in range(0, 56):
            if x % 14 in (2, 11):
                set_pixel(layer, mask, x, y0 + 1, palette["trim_light"])


def style_details(
    style_id: str,
    layers: dict[str, Image.Image],
    mask: Image.Image,
    palette: dict[str, tuple[int, int, int, int]],
) -> None:
    details = layers["details"]
    glow = layers.get("glow")

    if style_id == "moss_cave":
        add_trim(details, mask, palette)
        for cluster in (
            ((3, 15), (4, 15), (4, 16), (5, 16), (5, 17)),
            ((18, 14), (19, 14), (19, 15), (20, 15), (20, 16), (21, 16)),
            ((37, 17), (38, 17), (39, 16), (40, 16)),
            ((45, 34), (46, 34), (46, 35), (47, 35), (48, 36)),
            ((15, 39), (16, 39), (17, 40), (18, 40), (18, 41)),
        ):
            paint_points(details, mask, cluster, palette["accent"])
        paint_points(details, mask, ((4, 15), (19, 14), (46, 34), (17, 39)), palette["accent_light"])
        paint_path(details, mask, ((24, 34), (24, 35), (23, 36), (23, 38), (22, 39), (22, 41)), palette["accent_dark"])
        paint_points(details, mask, ((22, 37), (23, 40), (32, 16), (51, 38)), palette["hot"])

    elif style_id == "infernal":
        add_trim(details, mask, palette)
        if glow is None:
            raise AssertionError("infernal requires glow layer")
        for path in (
            ((17, 35), (18, 35), (18, 36), (19, 37), (19, 39), (20, 40)),
            ((31, 15), (32, 15), (33, 16), (34, 16), (35, 17)),
            ((46, 36), (45, 37), (45, 38), (44, 39), (44, 41)),
            ((18, 4), (19, 5), (19, 7), (20, 8), (20, 10)),
        ):
            paint_path(glow, mask, path, palette["accent"])
        paint_points(glow, mask, ((19, 37), (19, 38), (33, 16), (45, 38), (19, 6)), palette["accent_light"])
        paint_points(glow, mask, ((19, 38), (45, 39)), palette["hot"])
        paint_points(details, mask, ((6, 16), (7, 16), (25, 41), (39, 35), (52, 17)), palette["deepest"])

    elif style_id == "ghost":
        add_trim(details, mask, palette)
        if glow is None:
            raise AssertionError("ghost requires glow layer")
        for path in (
            ((2, 17), (3, 16), (4, 16), (5, 15), (7, 15)),
            ((16, 40), (17, 39), (19, 39), (20, 38), (22, 38)),
            ((31, 36), (33, 36), (34, 35), (36, 35), (37, 34)),
            ((45, 17), (47, 17), (48, 16), (50, 16), (51, 15)),
        ):
            paint_path(glow, mask, path, palette["accent_light"])
        paint_points(glow, mask, ((7, 15), (22, 38), (37, 34), (51, 15), (2, 2), (3, 2)), palette["hot"])
        paint_points(details, mask, ((17, 34), (24, 37), (30, 40), (43, 35), (52, 40)), palette["accent_dark"])

    elif style_id == "sculk":
        add_trim(details, mask, palette)
        if glow is None:
            raise AssertionError("sculk requires glow layer")
        for cluster in (
            ((3, 34), (4, 34), (3, 35), (4, 35), (5, 36)),
            ((20, 39), (21, 38), (21, 39), (22, 39), (22, 40)),
            ((34, 15), (35, 15), (35, 16), (36, 16), (37, 17)),
            ((48, 36), (49, 36), (49, 37), (50, 37), (50, 38)),
        ):
            paint_points(details, mask, cluster, palette["trim"])
        for path in (
            ((4, 35), (6, 36), (7, 37), (9, 37), (10, 38)),
            ((21, 39), (23, 39), (24, 38), (26, 38)),
            ((35, 16), (37, 16), (38, 15), (40, 15)),
            ((49, 37), (51, 38), (53, 38), (54, 39)),
            ((16, 5), (18, 5), (19, 6), (21, 6), (22, 7)),
        ):
            paint_path(glow, mask, path, palette["accent"])
        paint_points(glow, mask, ((9, 37), (25, 38), (39, 15), (53, 38), (21, 6)), palette["accent_light"])
        paint_points(glow, mask, ((25, 38), (39, 15)), palette["hot"])

    elif style_id == "gilded":
        add_trim(details, mask, palette, width=2)
        # Gold corner plates and a centered treasury band.
        for x0, y0, width, height in FACES.values():
            if height < 5:
                continue
            corners = (
                (x0 + 1, y0 + 1), (x0 + 2, y0 + 1), (x0 + 1, y0 + 2),
                (x0 + width - 2, y0 + 1), (x0 + width - 3, y0 + 1), (x0 + width - 2, y0 + 2),
            )
            paint_points(details, mask, corners, palette["accent"])
        for y in range(34, 42):
            set_pixel(details, mask, 20, y, palette["trim"])
            set_pixel(details, mask, 21, y, palette["trim_light"])
        paint_points(details, mask, ((2, 1), (3, 1), (2, 2), (21, 35), (21, 40)), palette["hot"])

    elif style_id == "bone":
        # Bone plates are split by dark joints; sinew stitches cross selected seams.
        for x in (6, 20, 34, 48):
            for y in range(34, 43):
                set_pixel(details, mask, x, y, palette["accent_dark"])
        for x in range(1, 55):
            if x % 14 not in (0, 13):
                set_pixel(details, mask, x, 38, palette["deepest"])
        stitches = ((5, 37), (7, 39), (19, 37), (21, 39), (33, 37), (35, 39), (47, 37), (49, 39))
        paint_points(details, mask, stitches, palette["trim"])
        paint_points(details, mask, ((6, 37), (20, 37), (34, 37), (48, 37)), palette["trim_light"])
        paint_points(details, mask, ((3, 16), (10, 17), (24, 16), (39, 17), (51, 16), (18, 35), (44, 40)), palette["accent"])
        paint_points(details, mask, ((4, 16), (25, 16), (45, 40)), palette["hot"])
    else:
        raise ValueError(style_id)


def render_chest(style_id: str, vanilla: Image.Image) -> tuple[dict[str, Image.Image], Image.Image]:
    style = STYLES[style_id]
    mask = vanilla.getchannel("A")
    layer_names = ["base", "shading", "details"] + (["glow"] if style.glow else [])
    layers = {name: new_layer() for name in layer_names}
    base = layers["base"]
    for y in range(64):
        for x in range(64):
            if mask.getpixel((x, y)):
                base.putpixel((x, y), style.palette["base"])
    shade_chest(layers, mask, style.palette)
    style_details(style_id, layers, mask, style.palette)

    composite = Image.new("RGBA", CANVAS_SIZE, TRANSPARENT)
    for name in layer_names:
        composite = Image.alpha_composite(composite, layers[name])
    composite.putalpha(mask)
    return layers, composite


def tile_set(
    layer: Image.Image,
    x: int,
    y: int,
    color: tuple[int, int, int, int],
) -> None:
    if 0 <= x < 16 and 0 <= y < 16:
        layer.putpixel((x, y), color)


def render_particle(style_id: str) -> tuple[dict[str, Image.Image], Image.Image]:
    style = STYLES[style_id]
    palette = style.palette
    layer_names = ["base", "shading", "details"] + (["glow"] if style.glow else [])
    layers = {name: new_layer() for name in layer_names}
    for y in range(16):
        for x in range(16):
            layers["base"].putpixel((x, y), palette["base"])
            variation = (x * 19 + y * 31 + len(style_id) * 7) % 17
            if variation in (0, 1):
                layers["shading"].putpixel((x, y), palette["shadow"])
            elif variation == 2:
                layers["shading"].putpixel((x, y), palette["light"])

    details = layers["details"]
    glow = layers.get("glow")
    if style_id == "moss_cave":
        for x in range(16):
            tile_set(details, x, 7, palette["trim_dark"])
        paint = ((1, 1), (2, 1), (2, 2), (3, 2), (8, 8), (9, 8), (9, 9), (10, 9), (14, 4))
        for x, y in paint:
            tile_set(details, x, y, palette["accent"])
        tile_set(details, 2, 1, palette["accent_light"])
        tile_set(details, 12, 12, palette["hot"])
    elif style_id == "infernal":
        for y in range(16):
            if y % 5 == 0:
                for x in range(16):
                    tile_set(details, x, y, palette["deepest"])
        if glow is None:
            raise AssertionError
        for x, y in ((2, 0), (3, 1), (3, 2), (4, 3), (9, 7), (10, 8), (10, 9), (11, 10), (6, 13), (7, 14), (7, 15)):
            tile_set(glow, x, y, palette["accent"])
        for x, y in ((3, 2), (10, 9), (7, 14)):
            tile_set(glow, x, y, palette["hot"])
    elif style_id == "ghost":
        for x in range(16):
            tile_set(details, x, 5, palette["trim_dark"])
            tile_set(details, x, 11, palette["trim"])
        if glow is None:
            raise AssertionError
        for x, y in ((1, 4), (2, 3), (3, 3), (4, 2), (8, 10), (9, 9), (10, 9), (11, 8), (12, 8)):
            tile_set(glow, x, y, palette["accent_light"])
        tile_set(glow, 12, 8, palette["hot"])
    elif style_id == "sculk":
        for x, y in ((1, 1), (2, 1), (2, 2), (3, 2), (9, 9), (10, 9), (10, 10), (11, 10)):
            tile_set(details, x, y, palette["trim"])
        if glow is None:
            raise AssertionError
        for x, y in ((3, 2), (4, 3), (5, 3), (6, 4), (10, 10), (11, 11), (12, 11), (13, 12)):
            tile_set(glow, x, y, palette["accent"])
        tile_set(glow, 6, 4, palette["accent_light"])
        tile_set(glow, 13, 12, palette["hot"])
    elif style_id == "gilded":
        for y in (0, 7, 15):
            for x in range(16):
                tile_set(details, x, y, palette["trim_dark"])
        for x in (0, 7, 15):
            for y in range(16):
                tile_set(details, x, y, palette["trim"])
        for x, y in ((1, 1), (6, 1), (8, 1), (14, 1), (1, 8), (14, 8)):
            tile_set(details, x, y, palette["accent_light"])
        tile_set(details, 1, 1, palette["hot"])
    elif style_id == "bone":
        for x in range(16):
            tile_set(details, x, 7, palette["deepest"])
        for y in range(16):
            tile_set(details, 5, y, palette["accent_dark"])
            tile_set(details, 12, y, palette["accent_dark"])
        for x, y in ((4, 3), (6, 4), (11, 10), (13, 11), (4, 13), (6, 14)):
            tile_set(details, x, y, palette["trim"])
        for x, y in ((2, 2), (9, 4), (14, 13)):
            tile_set(details, x, y, palette["hot"])

    composite = Image.new("RGBA", CANVAS_SIZE, TRANSPARENT)
    for name in layer_names:
        composite = Image.alpha_composite(composite, layers[name])
    particle = composite.crop((0, 0, 16, 16))
    return layers, particle


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def write_gpl(style_id: str) -> None:
    style = STYLES[style_id]
    lines = ["GIMP Palette", f"Name: {style.title}", "Columns: 7", "#"]
    for role, color in style.palette.items():
        lines.append(f"{color[0]:3d} {color[1]:3d} {color[2]:3d}\t{role}")
    path = ART_DIR / f"{style_id}.gpl"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def aseprite_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack("<H", len(encoded)) + encoded


def aseprite_chunk(chunk_type: int, payload: bytes) -> bytes:
    return struct.pack("<IH", 6 + len(payload), chunk_type) + payload


def aseprite_layer_chunk(name: str, visible: bool) -> bytes:
    flags = 3 if visible else 2
    payload = struct.pack(
        "<6HB3s",
        flags,
        0,
        0,
        CANVAS_SIZE[0],
        CANVAS_SIZE[1],
        0,
        255,
        b"\0" * 3,
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


def encode_aseprite(
    vanilla: Image.Image,
    chest_layers: dict[str, Image.Image],
    particle_layers: dict[str, Image.Image],
) -> bytes:
    visible_names = list(chest_layers)
    layer_names = ["vanilla_guide", *visible_names]
    frame_images = [
        [vanilla, *(chest_layers[name] for name in visible_names)],
        [new_layer(), *(particle_layers[name] for name in visible_names)],
    ]
    frames: list[bytes] = []
    for frame_index in range(FRAME_COUNT):
        chunks: list[bytes] = []
        if frame_index == 0:
            chunks.extend(
                aseprite_layer_chunk(name, visible=name != "vanilla_guide")
                for name in layer_names
            )
        chunks.extend(
            aseprite_cel_chunk(layer_index, image)
            for layer_index, image in enumerate(frame_images[frame_index])
        )
        frame_size = 16 + sum(len(chunk) for chunk in chunks)
        frame_header = struct.pack(
            "<IHHH2sI",
            frame_size,
            0xF1FA,
            len(chunks),
            FRAME_DURATION_MS,
            b"\0" * 2,
            len(chunks),
        )
        frames.append(frame_header + b"".join(chunks))

    payload = b"".join(frames)
    header = struct.pack(
        "<IHHHHHIHII B3sHBBhhHH84s",
        128 + len(payload),
        0xA5E0,
        FRAME_COUNT,
        CANVAS_SIZE[0],
        CANVAS_SIZE[1],
        32,
        1,
        FRAME_DURATION_MS,
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
    return header + payload


def decode_aseprite(data: bytes) -> tuple[list[str], list[int], list[list[Image.Image]]]:
    file_size, magic, frame_count, width, height, depth = struct.unpack_from("<I5H", data, 0)
    if file_size != len(data) or magic != 0xA5E0:
        raise AssertionError("invalid Aseprite file header")
    if (frame_count, width, height, depth) != (FRAME_COUNT, *CANVAS_SIZE, 32):
        raise AssertionError("unexpected Aseprite canvas, frame count, or color depth")

    offset = 128
    names: list[str] = []
    flags: list[int] = []
    frames: list[list[Image.Image]] = []
    for frame_index in range(frame_count):
        frame_start = offset
        frame_size, frame_magic, old_chunk_count = struct.unpack_from("<IHH", data, offset)
        if frame_magic != 0xF1FA:
            raise AssertionError(f"invalid Aseprite frame {frame_index}")
        chunk_count = struct.unpack_from("<I", data, offset + 12)[0] or old_chunk_count
        offset += 16
        cels: dict[int, Image.Image] = {}
        for _ in range(chunk_count):
            chunk_size, chunk_type = struct.unpack_from("<IH", data, offset)
            payload = offset + 6
            if chunk_type == 0x2004:
                layer_flags = struct.unpack_from("<H", data, payload)[0]
                name_length = struct.unpack_from("<H", data, payload + 16)[0]
                name_start = payload + 18
                flags.append(layer_flags)
                names.append(data[name_start:name_start + name_length].decode("utf-8"))
            elif chunk_type == 0x2005:
                layer_index = struct.unpack_from("<H", data, payload)[0]
                cel_type = struct.unpack_from("<H", data, payload + 7)[0]
                cel_width, cel_height = struct.unpack_from("<HH", data, payload + 16)
                if cel_type != 2 or (cel_width, cel_height) != CANVAS_SIZE:
                    raise AssertionError("unsupported Aseprite cel")
                raw = zlib.decompress(data[payload + 20:offset + chunk_size])
                cels[layer_index] = Image.frombytes("RGBA", CANVAS_SIZE, raw)
            offset += chunk_size
        if offset != frame_start + frame_size:
            raise AssertionError("Aseprite frame size mismatch")
        if set(cels) != set(range(len(names))):
            raise AssertionError("Aseprite frame has missing cels")
        frames.append([cels[index] for index in range(len(names))])
    if offset != len(data):
        raise AssertionError("Aseprite file has trailing data")
    return names, flags, frames


def composite_visible_source(names: list[str], flags: list[int], cels: list[Image.Image]) -> Image.Image:
    composite = new_layer()
    for name, layer_flags, cel in zip(names, flags, cels):
        if name != "vanilla_guide" and layer_flags & 1:
            composite = Image.alpha_composite(composite, cel)
    return composite


def stone_background(size: tuple[int, int], light: bool) -> Image.Image:
    base = (178, 181, 177, 255) if light else (25, 29, 31, 255)
    mortar = (135, 140, 138, 255) if light else (14, 17, 19, 255)
    fleck = (204, 205, 196, 255) if light else (43, 49, 50, 255)
    image = Image.new("RGBA", size, base)
    draw = ImageDraw.Draw(image)
    for y in range(0, size[1], 32):
        draw.line((0, y, size[0] - 1, y), fill=mortar, width=4)
        offset = 0 if (y // 32) % 2 == 0 else 24
        for x in range(offset, size[0], 48):
            draw.line((x, y, x, min(y + 31, size[1] - 1)), fill=mortar, width=4)
    for y in range(7, size[1], 23):
        for x in range((y * 3) % 19, size[0], 37):
            draw.rectangle((x, y, x + 3, y + 3), fill=fleck)
    return image


def assembled_front(chest: Image.Image) -> Image.Image:
    result = Image.new("RGBA", (14, 15), TRANSPARENT)
    result.alpha_composite(chest.crop((14, 14, 28, 19)), (0, 0))
    result.alpha_composite(chest.crop((14, 33, 28, 43)), (0, 5))
    latch = chest.crop((1, 1, 3, 5))
    result.alpha_composite(latch, (6, 3))
    return result


def write_preview(style_id: str, chest: Image.Image) -> None:
    panel_size = (336, 256)
    preview = Image.new("RGBA", (panel_size[0] * 2, panel_size[1]), TRANSPARENT)
    uv_4x = chest.resize((256, 256), Image.Resampling.NEAREST)
    front_4x = assembled_front(chest).resize((56, 60), Image.Resampling.NEAREST)
    for panel_index, light in enumerate((False, True)):
        panel = stone_background(panel_size, light)
        panel.alpha_composite(uv_4x, (8, 0))
        panel.alpha_composite(front_4x, (272, 98))
        preview.alpha_composite(panel, (panel_index * panel_size[0], 0))
    save_png(preview, PREVIEW_DIR / f"{style_id}_4x.png")


def validate_style(style_id: str, vanilla: Image.Image) -> str:
    chest_path = ENTITY_DIR / f"{style_id}.png"
    particle_path = BLOCK_DIR / f"boss_chest_{style_id}.png"
    chest = Image.open(chest_path).convert("RGBA")
    particle = Image.open(particle_path).convert("RGBA")
    if chest.size != CANVAS_SIZE or particle.size != PARTICLE_SIZE:
        raise AssertionError(f"{style_id}: incorrect output dimensions")
    vanilla_alpha = list(flat_pixels(vanilla.getchannel("A")))
    chest_alpha = list(flat_pixels(chest.getchannel("A")))
    mask_diff = sum(a != b for a, b in zip(vanilla_alpha, chest_alpha))
    semi_alpha = sum(0 < alpha < 255 for alpha in chest_alpha)
    particle_alpha = list(flat_pixels(particle.getchannel("A")))
    particle_semi = sum(0 < alpha < 255 for alpha in particle_alpha)
    if mask_diff or semi_alpha or particle_semi or 0 in particle_alpha:
        raise AssertionError(
            f"{style_id}: mask-diff={mask_diff}, semi-alpha={semi_alpha}, "
            f"particle-semi={particle_semi}"
        )
    colors = {pixel[:3] for pixel in flat_pixels(chest) if pixel[3]}
    particle_colors = {pixel[:3] for pixel in flat_pixels(particle) if pixel[3]}
    allowed = {color[:3] for color in STYLES[style_id].palette.values()}
    if not colors <= allowed or not particle_colors <= allowed:
        raise AssertionError(f"{style_id}: output contains colors outside its GPL palette")
    if len(colors | particle_colors) > 16:
        raise AssertionError(f"{style_id}: output uses more than 16 colors")

    seam_diff = 0
    for y0, height in ((14, 5), (33, 10)):
        for left_edge, right_edge in ((13, 14), (27, 28), (41, 42), (55, 0)):
            for y in range(y0, y0 + height):
                seam_diff += chest.getpixel((left_edge, y)) != chest.getpixel((right_edge, y))
    if seam_diff:
        raise AssertionError(f"{style_id}: side-face seam-diff={seam_diff}")

    source_path = ART_DIR / f"{style_id}.aseprite"
    names, flags, frames = decode_aseprite(source_path.read_bytes())
    expected_names = ["vanilla_guide", "base", "shading", "details"]
    if STYLES[style_id].glow:
        expected_names.append("glow")
    if names != expected_names or flags[0] & 1 or any(not value & 1 for value in flags[1:]):
        raise AssertionError(f"{style_id}: incorrect Aseprite layer structure")
    if frames[0][0].tobytes() != vanilla.tobytes():
        raise AssertionError(f"{style_id}: vanilla guide differs from the loaded template")
    source_chest = composite_visible_source(names, flags, frames[0])
    source_particle = composite_visible_source(names, flags, frames[1]).crop((0, 0, 16, 16))
    if source_chest.tobytes() != chest.tobytes() or source_particle.tobytes() != particle.tobytes():
        raise AssertionError(f"{style_id}: source frames do not reproduce runtime PNG pixels")
    return (
        f"{style_id:10s} chest=64x64 particle=16x16 mask-diff={mask_diff} "
        f"semi-alpha={semi_alpha} seam-diff={seam_diff} colors={len(colors | particle_colors)}"
    )


def build_style(style_id: str, vanilla: Image.Image) -> None:
    chest_layers, chest = render_chest(style_id, vanilla)
    particle_layers, particle = render_particle(style_id)
    save_png(chest, ENTITY_DIR / f"{style_id}.png")
    save_png(particle, BLOCK_DIR / f"boss_chest_{style_id}.png")
    write_gpl(style_id)
    write_preview(style_id, chest)
    source = encode_aseprite(vanilla, chest_layers, particle_layers)
    source_path = ART_DIR / f"{style_id}.aseprite"
    source_path.parent.mkdir(parents=True, exist_ok=True)
    source_path.write_bytes(source)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate the six boss chest skin art packages")
    parser.add_argument("--vanilla", type=Path, help="Path to Minecraft 1.21.1 chest normal.png")
    parser.add_argument("--style", choices=STYLE_IDS, action="append", help="Build only selected style")
    parser.add_argument("--check", action="store_true", help="Validate existing files without rebuilding")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    vanilla, source = load_vanilla_template(args.vanilla)
    selected = tuple(args.style or STYLE_IDS)
    print(f"vanilla template: {source}")
    if not args.check:
        for style_id in selected:
            build_style(style_id, vanilla)
            print(f"generated {style_id}")
    for style_id in selected:
        print(validate_style(style_id, vanilla))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, FileNotFoundError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)

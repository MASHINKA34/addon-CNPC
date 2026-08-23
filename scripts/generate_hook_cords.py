from __future__ import annotations

import argparse
from collections.abc import Iterable
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import struct
import zlib

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ART_DIR = ROOT / "art" / "aseprite" / "hook"
PREVIEW_DIR = ART_DIR / "preview"
RESOURCE_DIR = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "cnpcgeckoaddon"
    / "textures"
    / "entity"
    / "hook"
)

FRAME_SIZE = 16
FRAME_COUNT = 4
SOURCE_SIZE = (32, 16)
FILMSTRIP_SIZE = (16, 64)
FRAME_DURATION_MS = 120
TRANSPARENT = (0, 0, 0, 0)
STYLE_IDS = ("vine", "chain_infernal", "tentacle", "ghost")


@dataclass(frozen=True)
class Style:
    palette_name: str
    palette: dict[str, tuple[int, int, int, int]]
    layers: tuple[str, ...]


STYLES = {
    "vine": Style(
        "Hook Cord - Living Vine",
        {
            "transparent": TRANSPARENT,
            "outline": (25, 43, 35, 255),
            "shadow": (38, 70, 62, 255),
            "base": (60, 102, 61, 255),
            "mid": (83, 128, 65, 255),
            "light": (137, 161, 72, 255),
            "highlight": (193, 190, 91, 255),
            "dry_dark": (82, 56, 41, 255),
            "dry": (137, 91, 55, 255),
            "wood_light": (181, 124, 67, 255),
            "thorn": (106, 73, 44, 255),
        },
        ("base", "shading", "highlights", "details", "head"),
    ),
    "chain_infernal": Style(
        "Hook Cord - Infernal Chain",
        {
            "transparent": TRANSPARENT,
            "gap": (8, 8, 11, 255),
            "outline": (20, 23, 31, 255),
            "shadow": (35, 40, 51, 255),
            "metal": (60, 63, 72, 255),
            "metal_light": (95, 91, 84, 255),
            "ember": (101, 25, 20, 255),
            "red": (174, 42, 26, 255),
            "orange": (237, 85, 27, 255),
            "hot": (255, 166, 57, 255),
            "white_hot": (255, 226, 137, 255),
        },
        ("base", "shading", "highlights", "glow", "head"),
    ),
    "tentacle": Style(
        "Hook Cord - Grasping Tentacle",
        {
            "transparent": TRANSPARENT,
            "outline": (49, 25, 61, 255),
            "shadow": (75, 35, 86, 255),
            "base": (113, 52, 111, 255),
            "mid": (151, 67, 126, 255),
            "underside": (182, 92, 146, 255),
            "sucker_dark": (94, 42, 83, 255),
            "sucker": (207, 122, 171, 255),
            "sucker_light": (239, 178, 199, 255),
            "highlight": (243, 160, 203, 255),
            "specular": (255, 218, 224, 255),
        },
        ("base", "shading", "highlights", "suckers", "head"),
    ),
    "ghost": Style(
        "Hook Cord - Ghost Chain",
        {
            "transparent": TRANSPARENT,
            "mist": (54, 116, 137, 56),
            "halo": (66, 142, 165, 80),
            "shadow": (76, 153, 174, 96),
            "base": (104, 184, 201, 124),
            "light": (148, 217, 228, 154),
            "bright": (207, 244, 245, 190),
            "hot": (245, 255, 250, 232),
        },
        ("base", "shading", "highlights", "glow", "head"),
    ),
}


def rgba(style_id: str, name: str) -> tuple[int, int, int, int]:
    return STYLES[style_id].palette[name]


def new_tile() -> Image.Image:
    return Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), TRANSPARENT)


def draw_ring(
    image: Image.Image,
    outer: tuple[int, int, int, int],
    inner: tuple[int, int, int, int],
    color: tuple[int, int, int, int],
) -> None:
    draw = ImageDraw.Draw(image)
    draw.ellipse(outer, fill=color)
    draw.ellipse(inner, fill=TRANSPARENT)


def paint_cluster(
    image: Image.Image,
    points: Iterable[tuple[int, int]],
    color: tuple[int, int, int, int],
    opaque_only: bool = False,
) -> None:
    for x, y in points:
        if 0 <= x < FRAME_SIZE and 0 <= y < FRAME_SIZE:
            if not opaque_only or image.getpixel((x, y))[3] > 0:
                image.putpixel((x, y), color)


def enforce_vertical_seam(image: Image.Image) -> None:
    for x in range(FRAME_SIZE):
        image.putpixel((x, FRAME_SIZE - 1), image.getpixel((x, 0)))


def render_vine_cord(frame: int) -> Image.Image:
    image = new_tile()
    phase = (0, 1, 0, -1)[frame]
    centers = (7, 7, 8, 8, 8, 7, 7, 6, 6, 6, 7, 7, 8, 8, 7, 7)
    colors = STYLES["vine"].palette

    for y, raw_center in enumerate(centers):
        center = raw_center + phase
        for x in range(center - 3, center + 4):
            if 0 <= x < FRAME_SIZE:
                image.putpixel((x, y), colors["mid"])
        image.putpixel((center - 3, y), colors["outline"])
        image.putpixel((center + 3, y), colors["outline"])
        image.putpixel((center - 2, y), colors["shadow"])
        if y % 5 in (0, 1):
            image.putpixel((center + 1, y), colors["base"])

    highlight_offset = (0, 1, 1, 0)[frame]
    for y0 in (2, 8, 12):
        center = centers[y0] + phase
        paint_cluster(
            image,
            ((center + highlight_offset, y0), (center + highlight_offset + 1, y0),
             (center + highlight_offset, y0 + 1), (center + highlight_offset + 1, y0 + 1)),
            colors["light"],
            opaque_only=True,
        )
    center = centers[9] + phase
    paint_cluster(image, ((center, 9), (center + 1, 9)), colors["highlight"], True)

    fiber_y = (5, 6)
    for y in fiber_y:
        center = centers[y] + phase
        paint_cluster(image, ((center - 1, y), (center, y)), colors["dry"], True)

    leaf_side = 1 if frame in (0, 1, 3) else -1
    leaf_y = 5
    center = centers[leaf_y] + phase
    leaf_x = center + leaf_side * 4
    leaf_points = (
        (leaf_x, leaf_y),
        (leaf_x + leaf_side, leaf_y),
        (leaf_x, leaf_y + 1),
        (leaf_x + leaf_side, leaf_y + 1),
    )
    paint_cluster(image, leaf_points, colors["base"])
    thorn_center = centers[11] + phase
    thorn_side = -1 if frame in (0, 3) else 1
    paint_cluster(
        image,
        ((thorn_center + thorn_side * 4, 11), (thorn_center + thorn_side * 3, 11)),
        colors["thorn"],
    )
    enforce_vertical_seam(image)
    return image


def render_vine_head(frame: int) -> Image.Image:
    colors = STYLES["vine"].palette
    image = new_tile()
    draw = ImageDraw.Draw(image)
    sway = (0, 1, 0, -1)[frame]
    path = [(7 + sway, -1), (8 + sway, 5), (11 + sway, 8), (11 + sway, 11), (7 + sway, 15)]
    draw.line(path, fill=colors["outline"], width=6, joint="curve")
    draw.line(path, fill=colors["dry"], width=4, joint="curve")
    draw.line(path[:-1], fill=colors["wood_light"], width=2, joint="curve")
    draw.polygon([(5 + sway, 5), (2 + sway, 7), (5 + sway, 8)], fill=colors["thorn"])
    draw.polygon([(11 + sway, 7), (14 + sway, 8), (11 + sway, 10)], fill=colors["thorn"])
    draw.polygon([(9 + sway, 11), (12 + sway, 12), (9 + sway, 13)], fill=colors["dry_dark"])
    paint_cluster(
        image,
        ((7 + sway, 14), (7 + sway, 15), (8 + sway, 14)),
        colors["highlight"],
        opaque_only=True,
    )
    return image


def render_chain_cord(frame: int) -> Image.Image:
    style_id = "chain_infernal"
    colors = STYLES[style_id].palette
    image = new_tile()

    draw_ring(image, (4, -3, 11, 8), (6, -1, 9, 6), colors["outline"])
    draw_ring(image, (5, -2, 10, 7), (6, -1, 9, 6), colors["metal"])
    draw_ring(image, (2, 6, 13, 14), (5, 8, 10, 12), colors["outline"])
    draw_ring(image, (3, 7, 12, 13), (5, 8, 10, 12), colors["metal"])
    draw = ImageDraw.Draw(image)
    draw.rectangle((6, 13, 9, 15), fill=colors["outline"])
    draw.rectangle((7, 13, 8, 15), fill=colors["metal"])
    draw.rectangle((6, 6, 9, 8), fill=colors["gap"])
    draw.rectangle((7, 6, 8, 8), fill=colors["shadow"])
    paint_cluster(image, ((5, 1), (5, 2), (3, 8), (4, 8), (11, 11), (12, 11)), colors["shadow"], True)
    paint_cluster(image, ((9, 0), (10, 1), (8, 7), (9, 7), (5, 13), (6, 13)), colors["metal_light"], True)

    heat_phase = (0, 1, 2, 1)[frame]
    heat_colors = (
        (colors["ember"], colors["red"], colors["orange"]),
        (colors["red"], colors["orange"], colors["hot"]),
        (colors["orange"], colors["hot"], colors["white_hot"]),
    )[heat_phase]
    cracks = (
        ((9, 2), (10, 2), (9, 3), (10, 3)),
        ((3, 9), (4, 9), (4, 10), (5, 10)),
        ((7, 13), (8, 13), (7, 14), (8, 14)),
    )
    for index, cluster in enumerate(cracks):
        paint_cluster(image, cluster, heat_colors[index], True)
    enforce_vertical_seam(image)
    return image


def render_chain_head(frame: int) -> Image.Image:
    colors = STYLES["chain_infernal"].palette
    image = new_tile()
    draw = ImageDraw.Draw(image)
    outer = [(5, 0), (11, 0), (11, 6), (14, 8), (13, 11), (10, 11), (7, 15), (3, 15), (7, 10), (8, 8), (5, 7)]
    inner = [(7, 1), (9, 1), (9, 7), (12, 8), (11, 9), (9, 9), (6, 13), (7, 9), (7, 7)]
    draw.polygon(outer, fill=colors["outline"])
    draw.polygon(inner, fill=colors["metal"])
    draw.rectangle((7, 1, 8, 5), fill=colors["metal_light"])
    draw.polygon([(4, 5), (1, 7), (5, 8)], fill=colors["shadow"])
    draw.polygon([(11, 4), (15, 5), (11, 7)], fill=colors["shadow"])
    pulse = (colors["red"], colors["hot"], colors["white_hot"], colors["hot"])[frame]
    paint_cluster(image, ((5, 13), (6, 13), (4, 14), (5, 14), (4, 15)), pulse, True)
    paint_cluster(image, ((9, 8), (10, 8), (9, 9)), colors["orange"], True)
    return image


def render_tentacle_cord(frame: int) -> Image.Image:
    colors = STYLES["tentacle"].palette
    image = new_tile()
    wave = (0, 1, 0, -1)[frame]
    offsets = (0, 0, 1, 1, 0, 0, -1, -1)
    centers: list[int] = []
    for y in range(FRAME_SIZE):
        center = 7 + offsets[(y + wave) % len(offsets)]
        centers.append(center)
        half_width = 4 if (y + frame) % 7 not in (0, 1) else 3
        for x in range(center - half_width, center + half_width + 1):
            if 0 <= x < FRAME_SIZE:
                image.putpixel((x, y), colors["mid"])
        image.putpixel((center - half_width, y), colors["outline"])
        image.putpixel((center + half_width, y), colors["outline"])
        if half_width == 4:
            image.putpixel((center - 3, y), colors["shadow"])
        for x in range(center + 1, center + half_width):
            image.putpixel((x, y), colors["underside"])

    motion = (0, 1, 2, 1)[frame]
    for base_y in (2, 7, 11):
        y = min(13, base_y + motion)
        center = centers[y]
        paint_cluster(
            image,
            ((center, y), (center + 1, y), (center, y + 1), (center + 1, y + 1)),
            colors["sucker_dark"],
            True,
        )
        paint_cluster(image, ((center + 1, y), (center + 1, y + 1)), colors["sucker"], True)

    for y0 in (3 + motion, 9 + motion):
        if y0 < 14:
            center = centers[y0]
            paint_cluster(
                image,
                ((center - 2, y0), (center - 1, y0), (center - 2, y0 + 1), (center - 1, y0 + 1)),
                colors["highlight"],
                True,
            )
            paint_cluster(image, ((center - 2, y0), (center - 1, y0)), colors["specular"], True)
    enforce_vertical_seam(image)
    return image


def render_tentacle_head(frame: int) -> Image.Image:
    colors = STYLES["tentacle"].palette
    image = new_tile()
    draw = ImageDraw.Draw(image)
    breathe = (0, 1, 0, -1)[frame]
    outer = [(3, 0), (12, 0), (12, 5), (10 + breathe, 10), (12, 12), (10, 15), (5, 15), (3, 12), (5 - breathe, 9), (3, 5)]
    inner = [(5, 1), (10, 1), (10, 5), (8 + breathe, 10), (9, 11), (8, 13), (6, 13), (5, 11), (7 - breathe, 8), (5, 5)]
    draw.polygon(outer, fill=colors["outline"])
    draw.polygon(inner, fill=colors["mid"])
    draw.polygon([(8, 1), (10, 1), (10, 6), (8, 9)], fill=colors["underside"])
    draw.ellipse((4, 10, 11, 15), fill=colors["sucker"])
    draw.ellipse((6, 11, 9, 14), fill=colors["sucker_dark"])
    draw.rectangle((5, 10, 6, 11), fill=colors["sucker_light"])
    draw.polygon([(4, 9), (1, 11), (5, 12)], fill=colors["sucker_dark"])
    draw.polygon([(11, 8), (14, 10), (10, 11)], fill=colors["sucker_dark"])
    paint_cluster(image, ((5, 2), (6, 2), (5, 3), (6, 3)), colors["highlight"], True)
    paint_cluster(image, ((5, 2), (6, 2)), colors["specular"], True)
    return image


def render_ghost_cord(frame: int) -> Image.Image:
    colors = STYLES["ghost"].palette
    image = new_tile()
    draw_ring(image, (3, -4, 12, 8), (5, -2, 10, 6), colors["halo"])
    draw_ring(image, (4, -3, 11, 7), (6, -1, 9, 5), colors["base"])
    draw_ring(image, (1, 5, 14, 15), (4, 8, 11, 12), colors["halo"])
    draw_ring(image, (2, 6, 13, 14), (5, 8, 10, 12), colors["base"])
    draw = ImageDraw.Draw(image)
    draw.rectangle((6, 13, 9, 15), fill=colors["shadow"])
    draw.rectangle((7, 13, 8, 15), fill=colors["light"])

    mist_positions = (
        ((1, 3), (2, 3), (1, 4), (2, 4)),
        ((13, 4), (14, 4), (13, 5), (14, 5)),
        ((0, 10), (1, 10), (0, 11), (1, 11)),
        ((13, 11), (14, 11), (13, 12), (14, 12)),
    )[frame]
    paint_cluster(image, mist_positions, colors["mist"])

    glow_path = (
        ((6, 0), (7, 0), (6, 1), (7, 1)),
        ((9, 1), (10, 1), (9, 2), (10, 2)),
        ((10, 7), (11, 7), (10, 8), (11, 8)),
        ((6, 7), (7, 7), (6, 8), (7, 8)),
    )[frame]
    paint_cluster(image, glow_path, colors["bright"], True)
    hot_point = glow_path[frame % len(glow_path)]
    paint_cluster(image, (hot_point,), colors["hot"], True)
    enforce_vertical_seam(image)
    return image


def render_ghost_head(frame: int) -> Image.Image:
    colors = STYLES["ghost"].palette
    image = new_tile()
    draw = ImageDraw.Draw(image)
    sway = (0, 1, 0, -1)[frame]
    halo = [(4 + sway, 0), (11 + sway, 0), (12 + sway, 7), (14 + sway, 10), (10 + sway, 12), (7 + sway, 15), (3 + sway, 15), (6 + sway, 10), (3 + sway, 7)]
    core = [(6 + sway, 0), (9 + sway, 0), (10 + sway, 7), (12 + sway, 9), (9 + sway, 10), (6 + sway, 14), (5 + sway, 14), (8 + sway, 9), (5 + sway, 6)]
    draw.polygon(halo, fill=colors["halo"])
    draw.polygon(core, fill=colors["light"])
    draw.polygon([(4 + sway, 6), (1 + sway, 8), (5 + sway, 9)], fill=colors["mist"])
    paint_cluster(image, ((5 + sway, 13), (6 + sway, 13), (5 + sway, 14), (5 + sway, 15)), colors["bright"], True)
    paint_cluster(image, ((5 + sway, 15),), colors["hot"], True)
    return image


CORD_RENDERERS = {
    "vine": render_vine_cord,
    "chain_infernal": render_chain_cord,
    "tentacle": render_tentacle_cord,
    "ghost": render_ghost_cord,
}

HEAD_RENDERERS = {
    "vine": render_vine_head,
    "chain_infernal": render_chain_head,
    "tentacle": render_tentacle_head,
    "ghost": render_ghost_head,
}


def layer_for_color(style_id: str, color_name: str) -> str:
    if color_name == "transparent":
        raise ValueError("Transparent pixels do not belong to a layer")
    mappings = {
        "vine": {
            "outline": "shading", "shadow": "shading", "base": "base", "mid": "base",
            "light": "highlights", "highlight": "highlights", "dry_dark": "details",
            "dry": "details", "wood_light": "details", "thorn": "details",
        },
        "chain_infernal": {
            "gap": "shading", "outline": "shading", "shadow": "shading", "metal": "base",
            "metal_light": "highlights", "ember": "glow", "red": "glow", "orange": "glow",
            "hot": "glow", "white_hot": "glow",
        },
        "tentacle": {
            "outline": "shading", "shadow": "shading", "base": "base", "mid": "base",
            "underside": "base", "sucker_dark": "suckers", "sucker": "suckers",
            "sucker_light": "suckers", "highlight": "highlights", "specular": "highlights",
        },
        "ghost": {
            "mist": "glow", "halo": "glow", "shadow": "shading", "base": "base",
            "light": "highlights", "bright": "highlights", "hot": "highlights",
        },
    }
    return mappings[style_id][color_name]


def split_cord_into_layers(
    style_id: str,
    cord: Image.Image,
    target_layers: dict[str, Image.Image],
) -> None:
    reverse_palette = {value: name for name, value in STYLES[style_id].palette.items()}
    for y in range(FRAME_SIZE):
        for x in range(FRAME_SIZE):
            color = cord.getpixel((x, y))
            if color[3] == 0:
                continue
            color_name = reverse_palette[color]
            target_layers[layer_for_color(style_id, color_name)].putpixel((x, y), color)


def build_source_layers(style_id: str) -> dict[str, list[Image.Image]]:
    style = STYLES[style_id]
    result = {
        layer_name: [Image.new("RGBA", SOURCE_SIZE, TRANSPARENT) for _ in range(FRAME_COUNT)]
        for layer_name in style.layers
    }
    for frame in range(FRAME_COUNT):
        cord = CORD_RENDERERS[style_id](frame)
        head = HEAD_RENDERERS[style_id](frame)
        target = {name: result[name][frame] for name in style.layers}
        split_cord_into_layers(style_id, cord, target)
        result["head"][frame].alpha_composite(head, (FRAME_SIZE, 0))
    return result


def aseprite_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack("<H", len(encoded)) + encoded


def aseprite_chunk(chunk_type: int, payload: bytes) -> bytes:
    return struct.pack("<IH", 6 + len(payload), chunk_type) + payload


def aseprite_layer_chunk(name: str) -> bytes:
    payload = struct.pack(
        "<6HB3s",
        3,
        0,
        0,
        SOURCE_SIZE[0],
        SOURCE_SIZE[1],
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
        SOURCE_SIZE[0],
        SOURCE_SIZE[1],
    )
    return aseprite_chunk(0x2005, payload + zlib.compress(image.tobytes(), level=9))


def encode_aseprite(style_id: str, layers: dict[str, list[Image.Image]]) -> bytes:
    layer_names = STYLES[style_id].layers
    frames: list[bytes] = []
    for frame_index in range(FRAME_COUNT):
        chunks: list[bytes] = []
        if frame_index == 0:
            chunks.extend(aseprite_layer_chunk(name) for name in layer_names)
        chunks.extend(
            aseprite_cel_chunk(layer_index, layers[name][frame_index])
            for layer_index, name in enumerate(layer_names)
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
    file_size = 128 + len(payload)
    header = struct.pack(
        "<IHHHHHIHII B3sHBBhhHH84s",
        file_size,
        0xA5E0,
        FRAME_COUNT,
        SOURCE_SIZE[0],
        SOURCE_SIZE[1],
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


def decode_aseprite(data: bytes) -> tuple[tuple[str, ...], list[list[Image.Image]]]:
    file_size, magic, frame_count, width, height, depth = struct.unpack_from("<I5H", data, 0)
    if file_size != len(data) or magic != 0xA5E0:
        raise ValueError("Invalid Aseprite header")
    if (frame_count, width, height, depth) != (FRAME_COUNT, *SOURCE_SIZE, 32):
        raise ValueError("Unexpected Aseprite canvas or frame count")

    offset = 128
    layer_names: list[str] = []
    decoded_frames: list[dict[int, Image.Image]] = []
    for frame_index in range(frame_count):
        frame_start = offset
        frame_size, frame_magic, old_chunks = struct.unpack_from("<IHH", data, offset)
        if frame_magic != 0xF1FA:
            raise ValueError(f"Invalid Aseprite frame {frame_index}")
        chunk_count = struct.unpack_from("<I", data, offset + 12)[0] or old_chunks
        offset += 16
        frame_cels: dict[int, Image.Image] = {}
        for _ in range(chunk_count):
            chunk_size, chunk_type = struct.unpack_from("<IH", data, offset)
            payload_offset = offset + 6
            if chunk_type == 0x2004:
                name_length = struct.unpack_from("<H", data, payload_offset + 16)[0]
                name_start = payload_offset + 18
                layer_names.append(data[name_start:name_start + name_length].decode("utf-8"))
            elif chunk_type == 0x2005:
                layer_index = struct.unpack_from("<H", data, payload_offset)[0]
                cel_type = struct.unpack_from("<H", data, payload_offset + 7)[0]
                cel_width, cel_height = struct.unpack_from("<HH", data, payload_offset + 16)
                if cel_type != 2 or (cel_width, cel_height) != SOURCE_SIZE:
                    raise ValueError("Unsupported Aseprite cel")
                raw = zlib.decompress(data[payload_offset + 20:offset + chunk_size])
                frame_cels[layer_index] = Image.frombytes("RGBA", SOURCE_SIZE, raw)
            offset += chunk_size
        if offset != frame_start + frame_size:
            raise ValueError("Aseprite frame size mismatch")
        decoded_frames.append(frame_cels)
    if offset != len(data):
        raise ValueError("Trailing Aseprite data")

    frames: list[list[Image.Image]] = []
    for frame_cels in decoded_frames:
        if set(frame_cels) != set(range(len(layer_names))):
            raise ValueError("Missing Aseprite cel")
        frames.append([frame_cels[index] for index in range(len(layer_names))])
    return tuple(layer_names), frames


def composite_source_frames(data: bytes) -> tuple[tuple[str, ...], list[Image.Image]]:
    layer_names, layer_frames = decode_aseprite(data)
    composites: list[Image.Image] = []
    for cels in layer_frames:
        composite = Image.new("RGBA", SOURCE_SIZE, TRANSPARENT)
        for cel in cels:
            composite = Image.alpha_composite(composite, cel)
        composites.append(composite)
    return layer_names, composites


def build_runtime_filmstrips(source_data: bytes) -> tuple[Image.Image, Image.Image, list[Image.Image], list[Image.Image]]:
    _, frames = composite_source_frames(source_data)
    cord_frames = [frame.crop((0, 0, 16, 16)) for frame in frames]
    head_frames = [frame.crop((16, 0, 32, 16)) for frame in frames]
    cord_strip = Image.new("RGBA", FILMSTRIP_SIZE, TRANSPARENT)
    head_strip = Image.new("RGBA", FILMSTRIP_SIZE, TRANSPARENT)
    for frame_index, (cord, head) in enumerate(zip(cord_frames, head_frames, strict=True)):
        cord_strip.alpha_composite(cord, (0, frame_index * FRAME_SIZE))
        head_strip.alpha_composite(head, (0, frame_index * FRAME_SIZE))
    return cord_strip, head_strip, cord_frames, head_frames


def encode_png(image: Image.Image) -> bytes:
    buffer = BytesIO()
    image.save(buffer, format="PNG", optimize=False, compress_level=9)
    return buffer.getvalue()


def png_chunks(data: bytes) -> list[bytes]:
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("Invalid PNG signature")
    chunks: list[bytes] = []
    offset = 8
    while offset < len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_data = data[offset + 8:offset + 8 + length]
        expected_crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        actual_crc = zlib.crc32(chunk_type + chunk_data) & 0xFFFFFFFF
        if expected_crc != actual_crc:
            raise ValueError("PNG CRC mismatch")
        chunks.append(chunk_type)
        offset += 12 + length
    if offset != len(data):
        raise ValueError("Trailing PNG data")
    return chunks


def validate_png_bytes(data: bytes, expected_size: tuple[int, int]) -> Image.Image:
    chunks = png_chunks(data)
    if chunks[0] != b"IHDR" or chunks[-1] != b"IEND" or set(chunks) != {b"IHDR", b"IDAT", b"IEND"}:
        raise ValueError(f"Unexpected PNG chunks: {chunks}")
    width, height, bit_depth, color_type, compression, filtering, interlace = struct.unpack_from(">IIBBBBB", data, 16)
    if (width, height) != expected_size:
        raise ValueError(f"Unexpected PNG dimensions: {(width, height)}")
    if (bit_depth, color_type, compression, filtering, interlace) != (8, 6, 0, 0, 0):
        raise ValueError("PNG is not non-interlaced RGBA8")
    image = Image.open(BytesIO(data))
    image.load()
    if image.mode != "RGBA" or image.size != expected_size:
        raise ValueError("Pillow decoded unexpected PNG metadata")
    return image


def background_panel(kind: str, size: tuple[int, int]) -> Image.Image:
    width, height = size
    image = Image.new("RGBA", size, (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)
    if kind == "dark_stone":
        draw.rectangle((0, 0, width, height), fill=(24, 28, 34, 255))
        for y in range(0, height, 16):
            offset = 8 if (y // 16) % 2 else 0
            draw.line((0, y, width, y), fill=(12, 15, 20, 255))
            for x in range(offset, width, 24):
                draw.line((x, y, x, min(y + 15, height)), fill=(16, 19, 24, 255))
        for y in range(4, height, 17):
            for x in range(6 + (y % 9), width, 29):
                draw.rectangle((x, y, x + 2, y + 1), fill=(39, 44, 51, 255))
    elif kind == "bright_sky":
        draw.rectangle((0, 0, width, height), fill=(145, 202, 233, 255))
        for y in range(10, height, 31):
            shift = (y * 3) % 21
            for x in range(-8 + shift, width, 34):
                draw.rectangle((x, y, x + 17, y + 4), fill=(218, 239, 247, 255))
                draw.rectangle((x + 5, y - 3, x + 12, y + 5), fill=(218, 239, 247, 255))
    elif kind == "checker":
        for y in range(0, height, 8):
            for x in range(0, width, 8):
                value = 226 if (x // 8 + y // 8) % 2 == 0 else 174
                draw.rectangle((x, y, x + 7, y + 7), fill=(value, value, value, 255))
    else:
        raise ValueError(kind)
    return image


def build_preview(cord_frames: list[Image.Image], head_frames: list[Image.Image]) -> Image.Image:
    panel_size = (144, 132)
    kinds = ("dark_stone", "bright_sky", "checker")
    sheet = Image.new("RGBA", (panel_size[0] * len(kinds), panel_size[1]), TRANSPARENT)
    for panel_index, kind in enumerate(kinds):
        panel = background_panel(kind, panel_size)
        for frame_index, cord in enumerate(cord_frames):
            panel.alpha_composite(cord, (8 + frame_index * 20, 8))
        for frame_index, head in enumerate(head_frames):
            panel.alpha_composite(head, (8 + frame_index * 20, 32))
        for segment in range(8):
            panel.alpha_composite(cord_frames[0], (96, 2 + segment * 16))
        panel.alpha_composite(head_frames[0], (118, 114))
        sheet.alpha_composite(panel, (panel_index * panel_size[0], 0))
    return sheet.resize((sheet.width * 8, sheet.height * 8), Image.Resampling.NEAREST)


def encode_gpl(style_id: str) -> bytes:
    style = STYLES[style_id]
    lines = ["GIMP Palette", f"Name: {style.palette_name}", "Columns: 4", "#"]
    for name, (red, green, blue, alpha) in style.palette.items():
        lines.append(f"{red:3d} {green:3d} {blue:3d}\t{name} a{alpha:03d}")
    return ("\n".join(lines) + "\n").encode("utf-8")


def style_artifact_paths(style_id: str) -> tuple[Path, ...]:
    runtime_dir = RESOURCE_DIR / style_id
    return (
        ART_DIR / f"{style_id}.aseprite",
        ART_DIR / f"{style_id}.gpl",
        runtime_dir / "cord.png",
        runtime_dir / "head.png",
        PREVIEW_DIR / f"{style_id}_8x.png",
    )


def build_style_artifacts(style_id: str) -> dict[Path, bytes]:
    layers = build_source_layers(style_id)
    source_data = encode_aseprite(style_id, layers)
    cord_strip, head_strip, cord_frames, head_frames = build_runtime_filmstrips(source_data)
    preview = build_preview(cord_frames, head_frames)
    paths = style_artifact_paths(style_id)
    return {
        paths[0]: source_data,
        paths[1]: encode_gpl(style_id),
        paths[2]: encode_png(cord_strip),
        paths[3]: encode_png(head_strip),
        paths[4]: encode_png(preview),
    }


def write_artifacts(artifacts: dict[Path, bytes]) -> None:
    for path, data in artifacts.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def frame_delta(first: Image.Image, second: Image.Image) -> int:
    return sum(a != b for a, b in zip(first.get_flattened_data(), second.get_flattened_data(), strict=True))


def validate_style(style_id: str) -> dict[str, object]:
    source_path, palette_path, cord_path, head_path, preview_path = style_artifact_paths(style_id)
    source_data = source_path.read_bytes()
    layer_names, source_frames = composite_source_frames(source_data)
    if layer_names != STYLES[style_id].layers:
        raise ValueError(f"{style_id}: wrong Aseprite layers {layer_names}")

    expected_cord, expected_head, cord_frames, head_frames = build_runtime_filmstrips(source_data)
    cord_data = cord_path.read_bytes()
    head_data = head_path.read_bytes()
    actual_cord = validate_png_bytes(cord_data, FILMSTRIP_SIZE)
    actual_head = validate_png_bytes(head_data, FILMSTRIP_SIZE)
    if encode_png(expected_cord) != cord_data or encode_png(expected_head) != head_data:
        raise ValueError(f"{style_id}: runtime PNGs do not reproduce from Aseprite source")
    if actual_cord.tobytes() != expected_cord.tobytes() or actual_head.tobytes() != expected_head.tobytes():
        raise ValueError(f"{style_id}: runtime pixel mismatch")

    preview_data = preview_path.read_bytes()
    validate_png_bytes(preview_data, (3456, 1056))
    expected_preview = encode_png(build_preview(cord_frames, head_frames))
    if preview_data != expected_preview:
        raise ValueError(f"{style_id}: preview is stale")

    for frame_index, frame in enumerate(cord_frames):
        top = [frame.getpixel((x, 0)) for x in range(FRAME_SIZE)]
        bottom = [frame.getpixel((x, FRAME_SIZE - 1)) for x in range(FRAME_SIZE)]
        if top != bottom:
            raise ValueError(f"{style_id}: seam mismatch in frame {frame_index}")
        tiled = Image.new("RGBA", (16, 128), TRANSPARENT)
        for segment in range(8):
            tiled.alpha_composite(frame, (0, segment * 16))
        for seam_y in range(15, 127, 16):
            if [tiled.getpixel((x, seam_y)) for x in range(16)] != [tiled.getpixel((x, seam_y + 1)) for x in range(16)]:
                raise ValueError(f"{style_id}: visible tile boundary at y={seam_y}")

    animation_frames = [
        Image.alpha_composite(cord_frames[index], head_frames[index])
        for index in range(FRAME_COUNT)
    ]
    deltas = [
        frame_delta(animation_frames[index], animation_frames[(index + 1) % FRAME_COUNT])
        for index in range(FRAME_COUNT)
    ]
    if deltas[-1] > max(deltas[:-1]) * 1.35 + 8:
        raise ValueError(f"{style_id}: frame 3 -> 0 loop jump {deltas}")

    texture_colors = set(actual_cord.get_flattened_data()) | set(actual_head.get_flattened_data())
    palette_colors = set(STYLES[style_id].palette.values())
    if not texture_colors <= palette_colors:
        raise ValueError(f"{style_id}: texture contains colors outside its palette")
    if not 8 <= len(texture_colors) <= 14:
        raise ValueError(f"{style_id}: expected 8-14 unique RGBA colors, got {len(texture_colors)}")
    alpha_values = {color[3] for color in texture_colors}
    if style_id == "ghost":
        if any(0 < alpha <= 20 for alpha in alpha_values):
            raise ValueError("ghost: dirty low-alpha pixels")
    elif not alpha_values <= {0, 255}:
        raise ValueError(f"{style_id}: unexpected partial alpha")

    gpl_lines = [
        line
        for raw_line in palette_path.read_text(encoding="utf-8").splitlines()
        if (line := raw_line.lstrip()) and line[0].isdigit()
    ]
    if len(gpl_lines) != len(STYLES[style_id].palette) or not 8 <= len(gpl_lines) <= 14:
        raise ValueError(f"{style_id}: palette file does not match style palette")
    if len(source_frames) != FRAME_COUNT:
        raise ValueError(f"{style_id}: wrong source frame count")

    return {
        "colors": len(texture_colors),
        "alpha": sorted(alpha_values),
        "loop_deltas": deltas,
        "layers": layer_names,
    }


def generate(styles: Iterable[str]) -> None:
    for style_id in styles:
        write_artifacts(build_style_artifacts(style_id))
        report = validate_style(style_id)
        print(
            f"generated {style_id}: colors={report['colors']} alpha={report['alpha']} "
            f"loop_deltas={report['loop_deltas']} layers={','.join(report['layers'])}"
        )


def check(styles: Iterable[str]) -> None:
    for style_id in styles:
        report = validate_style(style_id)
        print(
            f"validated {style_id}: colors={report['colors']} alpha={report['alpha']} "
            f"loop_deltas={report['loop_deltas']} layers={','.join(report['layers'])}"
        )


def verify_rebuild(styles: Iterable[str]) -> None:
    style_list = list(styles)
    before = {path: path.read_bytes() for style_id in style_list for path in style_artifact_paths(style_id)}
    generate(style_list)
    after = {path: path.read_bytes() for path in before}
    changed = [str(path.relative_to(ROOT)) for path in before if before[path] != after[path]]
    if changed:
        raise ValueError(f"Rebuild changed artifacts: {changed}")
    print(f"byte-identical rebuild: {', '.join(style_list)}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build deterministic hook cord pixel art")
    parser.add_argument("--style", action="append", choices=STYLE_IDS, help="Build only one style (repeatable)")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="Validate committed artifacts without writing")
    mode.add_argument("--verify", action="store_true", help="Rebuild and require byte-identical artifacts")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    styles = tuple(args.style) if args.style else STYLE_IDS
    if args.check:
        check(styles)
    elif args.verify:
        verify_rebuild(styles)
    else:
        generate(styles)


if __name__ == "__main__":
    main()

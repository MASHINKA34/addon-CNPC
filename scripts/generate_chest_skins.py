from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import os
import struct
import sys
import zipfile
import zlib

from PIL import Image, ImageDraw, ImageFilter


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
LAYER_NAMES = ("palette_recolor", "style_accents")
WOOD_STEPS = 14
METAL_STEPS = 6
MAX_ACCENT_FRACTION = 0.10

RGB = tuple[int, int, int]
RGBA = tuple[int, int, int, int]
Point = tuple[int, int]
AccentPath = tuple[int, tuple[Point, ...]]


def gradient(anchors: tuple[RGB, ...], steps: int) -> tuple[RGB, ...]:
    if len(anchors) < 2 or steps < 2:
        raise ValueError("a palette ramp needs at least two anchors and two steps")
    result: list[RGB] = []
    for index in range(steps):
        position = index * (len(anchors) - 1) / (steps - 1)
        left = min(int(position), len(anchors) - 2)
        amount = position - left
        result.append(
            tuple(
                round(anchors[left][channel] * (1.0 - amount) + anchors[left + 1][channel] * amount)
                for channel in range(3)
            )
        )
    if len(set(result)) != steps:
        raise ValueError("palette ramp contains duplicate colors")
    return tuple(result)


@dataclass(frozen=True)
class Style:
    title: str
    wood: tuple[RGB, ...]
    metal: tuple[RGB, ...]
    accents: tuple[RGB, RGB, RGB]

    @property
    def colors(self) -> tuple[RGB, ...]:
        return (*self.wood, *self.metal, *self.accents)


def make_style(
    title: str,
    wood_anchors: tuple[RGB, ...],
    metal: tuple[RGB, ...],
    accents: tuple[RGB, RGB, RGB],
) -> Style:
    if len(metal) != METAL_STEPS:
        raise ValueError(f"{title}: metal ramp must contain {METAL_STEPS} colors")
    style = Style(title, gradient(wood_anchors, WOOD_STEPS), metal, accents)
    if len(set(style.colors)) != len(style.colors):
        raise ValueError(f"{title}: palette colors must be unique")
    return style


# The first four palettes use the characteristic color families from the matching
# boss-bar source art. The wider value ranges are intentional: the vanilla texture's
# board and latch contrast must survive at game-view distance.
STYLES = {
    "moss_cave": make_style(
        "Moss Cave Boss Chest",
        ((7, 16, 14), (34, 51, 38), (84, 101, 61), (172, 177, 125)),
        (
            (52, 66, 71),
            (69, 85, 91),
            (90, 108, 114),
            (116, 136, 141),
            (153, 173, 176),
            (205, 218, 217),
        ),
        ((18, 70, 39), (3, 121, 56), (30, 174, 86)),
    ),
    "infernal": make_style(
        "Infernal Boss Chest",
        ((1, 2, 3), (16, 15, 15), (43, 38, 36), (126, 101, 84)),
        (
            (48, 53, 62),
            (65, 71, 81),
            (84, 91, 101),
            (110, 117, 127),
            (151, 157, 166),
            (207, 211, 216),
        ),
        ((111, 23, 16), (218, 55, 20), (255, 157, 46)),
    ),
    "ghost": make_style(
        "Ghost Dungeon Boss Chest",
        ((20, 29, 40), (68, 102, 116), (153, 199, 209), (238, 249, 247)),
        (
            (115, 151, 166),
            (137, 172, 185),
            (160, 195, 205),
            (186, 217, 224),
            (216, 239, 242),
            (247, 255, 255),
        ),
        ((54, 124, 151), (132, 218, 232), (241, 255, 252)),
    ),
    "sculk": make_style(
        "Sculk Boss Chest",
        ((2, 5, 9), (9, 25, 34), (29, 54, 62), (109, 133, 136)),
        (
            (53, 77, 87),
            (69, 95, 106),
            (90, 120, 130),
            (119, 151, 159),
            (159, 187, 191),
            (213, 231, 230),
        ),
        ((5, 77, 91), (8, 145, 155), (55, 228, 226)),
    ),
    "gilded": make_style(
        "Gilded Treasury Boss Chest",
        ((13, 8, 7), (38, 22, 15), (86, 48, 25), (164, 105, 52)),
        (
            (78, 48, 10),
            (111, 72, 15),
            (149, 101, 23),
            (190, 137, 32),
            (229, 180, 55),
            (255, 229, 125),
        ),
        ((124, 80, 18), (218, 164, 42), (255, 241, 166)),
    ),
    "bone": make_style(
        "Bone Boss Chest",
        ((24, 21, 20), (72, 62, 52), (150, 133, 105), (236, 229, 195)),
        (
            (101, 112, 116),
            (124, 137, 141),
            (149, 162, 166),
            (177, 190, 192),
            (207, 218, 218),
            (238, 244, 242),
        ),
        ((58, 48, 42), (104, 86, 65), (198, 177, 132)),
    ),
}


# Paths are deliberately sparse and are filtered through the protected vanilla-edge
# mask before use. Accent index 0/1/2 selects dark/main/light from Style.accents.
ACCENT_PATHS: dict[str, tuple[AccentPath, ...]] = {
    "moss_cave": (
        (1, ((15, 34), (16, 34), (16, 35), (17, 35), (17, 36))),
        (2, ((15, 34), (16, 34))),
        (1, ((23, 39), (24, 39), (24, 40), (25, 40), (25, 41))),
        (0, ((23, 40), (24, 40))),
        (1, ((2, 34), (3, 34), (3, 35), (4, 35), (4, 36))),
        (1, ((43, 15), (44, 15), (44, 16), (45, 16), (45, 17))),
        (2, ((44, 15),)),
        (1, ((15, 1), (16, 1), (16, 2), (17, 2), (17, 3))),
        (0, ((25, 7), (25, 8), (24, 9), (24, 10))),
        (1, ((31, 20), (32, 20), (32, 21), (33, 21), (33, 22))),
        (1, ((48, 34), (49, 34), (49, 35), (50, 35), (50, 36))),
    ),
    "infernal": (
        (0, ((17, 34), (18, 35), (18, 36), (19, 37), (19, 38), (20, 39), (20, 41))),
        (1, ((18, 35), (19, 36), (19, 37), (20, 38))),
        (2, ((19, 37),)),
        (0, ((30, 15), (31, 15), (32, 16), (33, 16), (34, 17))),
        (1, ((31, 15), (32, 16), (33, 16))),
        (0, ((45, 35), (46, 36), (46, 37), (47, 38), (47, 40))),
        (1, ((46, 36), (46, 37), (47, 38))),
        (0, ((18, 3), (19, 4), (19, 5), (20, 6), (20, 8), (21, 9))),
        (1, ((19, 4), (20, 5), (20, 6))),
        (0, ((3, 15), (4, 16), (5, 16), (6, 17))),
    ),
    "ghost": (
        (1, ((17, 3),)),
        (2, ((24, 3),)),
        (1, ((20, 4),)),
        (0, ((23, 10),)),
        (1, ((24, 23),)),
        (2, ((24, 25),)),
        (0, ((17, 37),)),
        (1, ((15, 15), (16, 15), (17, 15), (18, 16))),
        (2, ((15, 15), (16, 15))),
        (1, ((24, 34), (25, 34), (26, 35), (26, 36))),
        (2, ((25, 34),)),
        (1, ((1, 34), (2, 34), (3, 35), (3, 36))),
        (1, ((42, 15), (43, 15), (44, 15), (45, 16))),
        (2, ((43, 15),)),
        (1, ((14, 1), (15, 1), (16, 1), (17, 2))),
        (1, ((38, 20), (39, 20), (40, 21), (40, 22))),
        (2, ((39, 20),)),
        (0, ((19, 40), (20, 40), (21, 39), (22, 39))),
        (0, ((47, 40), (48, 40), (49, 39), (50, 39))),
    ),
    "sculk": (
        (0, ((17, 3),)),
        (1, ((18, 3),)),
        (2, ((19, 3),)),
        (1, ((23, 5),)),
        (0, ((24, 10),)),
        (1, ((24, 23),)),
        (0, ((2, 34), (3, 34), (3, 35), (4, 35))),
        (1, ((4, 35), (5, 36), (6, 36), (7, 37), (8, 37), (9, 38))),
        (2, ((8, 37),)),
        (0, ((19, 40), (20, 39), (21, 39))),
        (1, ((21, 39), (22, 38), (23, 38), (24, 37), (25, 37))),
        (2, ((24, 37),)),
        (1, ((34, 15), (35, 15), (36, 16), (37, 16), (38, 17))),
        (2, ((37, 16),)),
        (1, ((48, 35), (49, 36), (50, 36), (51, 37), (52, 37), (53, 38))),
        (1, ((16, 4), (17, 4), (18, 5), (19, 5), (20, 6), (21, 6))),
    ),
    "gilded": (
        (0, ((17, 3),)),
        (1, ((18, 3),)),
        (2, ((24, 3),)),
        (1, ((17, 9),)),
        (0, ((24, 10),)),
        (2, ((24, 23),)),
        (0, ((15, 34), (16, 34), (15, 35))),
        (1, ((16, 34), (17, 34), (16, 35))),
        (2, ((16, 34),)),
        (0, ((25, 34), (26, 34), (26, 35))),
        (1, ((24, 34), (25, 34), (25, 35))),
        (2, ((25, 34),)),
        (0, ((1, 34), (2, 34), (1, 35))),
        (1, ((2, 34), (3, 34), (2, 35))),
        (0, ((43, 34), (44, 34), (43, 35))),
        (1, ((44, 34), (45, 34), (44, 35))),
        (0, ((15, 15), (16, 15), (15, 16))),
        (1, ((16, 15), (17, 15), (16, 16))),
        (0, ((25, 15), (26, 15), (26, 16))),
        (1, ((24, 15), (25, 15), (25, 16))),
    ),
    "bone": (
        (0, ((17, 3),)),
        (0, ((21, 3),)),
        (1, ((24, 3),)),
        (0, ((18, 7),)),
        (2, ((24, 10),)),
        (1, ((24, 25),)),
        (0, ((17, 34), (17, 35), (17, 36), (17, 37), (17, 38), (17, 39), (17, 40), (17, 41))),
        (1, ((18, 35), (18, 36), (18, 37))),
        (0, ((23, 34), (23, 35), (23, 36), (23, 37), (23, 38), (23, 39), (23, 40), (23, 41))),
        (2, ((24, 35), (24, 36))),
        (0, ((3, 34), (3, 35), (3, 36), (3, 37), (3, 38), (3, 39), (3, 40), (3, 41))),
        (0, ((31, 34), (31, 35), (31, 36), (31, 37), (31, 38), (31, 39), (31, 40), (31, 41))),
        (0, ((45, 34), (45, 35), (45, 36), (45, 37), (45, 38), (45, 39), (45, 40), (45, 41))),
        (0, ((51, 34), (51, 35), (51, 36), (51, 37), (51, 38), (51, 39), (51, 40), (51, 41))),
        (1, ((17, 15), (17, 16), (17, 17))),
        (1, ((23, 15), (23, 16), (23, 17))),
    ),
}


def flat_pixels(image: Image.Image):
    if hasattr(image, "get_flattened_data"):
        return image.get_flattened_data()
    return image.getdata()


def rgba(color: RGB) -> RGBA:
    return color[0], color[1], color[2], 255


def luminance(color: tuple[int, ...]) -> float:
    return 0.299 * color[0] + 0.587 * color[1] + 0.114 * color[2]


def is_metal_source(color: RGBA) -> bool:
    return color[3] == 255 and color[0] == color[1] == color[2] and color[0] >= 96


def candidate_vanilla_jars() -> list[Path]:
    candidates = sorted(
        (ROOT / "build" / "moddev" / "artifacts").glob("*minecraft-resources-aka-client-extra.jar")
    )
    profile = Path(os.environ.get("USERPROFILE", ""))
    if profile:
        candidates.extend(
            (
                profile / ".gradle" / "caches" / "neoformruntime" / "artifacts" / "minecraft_1.21.1_client.jar",
                profile / ".gradle" / "caches" / "fabric-loom" / "1.21.1" / "minecraft-client.jar",
            )
        )
    return candidates


def load_vanilla_template(explicit_path: Path | None) -> tuple[Image.Image, str]:
    if explicit_path:
        image = Image.open(explicit_path).convert("RGBA")
        image.load()
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
        raise ValueError("Vanilla template must use a non-empty binary alpha mask")
    return image, source


def new_layer() -> Image.Image:
    return Image.new("RGBA", CANVAS_SIZE, TRANSPARENT)


def build_palette_mapping(vanilla: Image.Image, style: Style) -> dict[RGBA, RGBA]:
    source_colors = {pixel for pixel in flat_pixels(vanilla) if pixel[3]}
    metal_source = sorted((color for color in source_colors if is_metal_source(color)), key=luminance)
    wood_source = sorted(
        (color for color in source_colors if not is_metal_source(color)),
        key=lambda color: (luminance(color), color),
    )
    if len(metal_source) != METAL_STEPS:
        raise ValueError(
            f"Expected {METAL_STEPS} vanilla metal shades, found {len(metal_source)}; "
            "is this Minecraft 1.21.1 normal.png?"
        )

    def assign(source: list[RGBA], target: tuple[RGB, ...]) -> dict[RGBA, RGBA]:
        if not source:
            return {}
        return {
            color: rgba(target[round(index * (len(target) - 1) / max(1, len(source) - 1))])
            for index, color in enumerate(source)
        }

    mapping = assign(wood_source, style.wood)
    mapping.update(assign(metal_source, style.metal))
    if set(mapping) != source_colors:
        raise AssertionError("palette map does not cover every opaque vanilla color")
    return mapping


def remap_image(vanilla: Image.Image, mapping: dict[RGBA, RGBA]) -> Image.Image:
    result = new_layer()
    source = vanilla.load()
    target = result.load()
    for y in range(CANVAS_SIZE[1]):
        for x in range(CANVAS_SIZE[0]):
            color = source[x, y]
            if color[3]:
                target[x, y] = mapping[color]
    return result


def protected_mask(vanilla: Image.Image) -> Image.Image:
    alpha = vanilla.getchannel("A")
    edge = Image.new("L", CANVAS_SIZE, 0)
    edge_pixels = edge.load()
    source = vanilla.load()
    for y in range(CANVAS_SIZE[1]):
        for x in range(CANVAS_SIZE[0]):
            if not alpha.getpixel((x, y)):
                continue
            for nx, ny in ((x + 1, y), (x, y + 1)):
                if nx >= CANVAS_SIZE[0] or ny >= CANVAS_SIZE[1] or not alpha.getpixel((nx, ny)):
                    continue
                if abs(luminance(source[x, y]) - luminance(source[nx, ny])) > 8:
                    edge_pixels[x, y] = 255
                    edge_pixels[nx, ny] = 255

    metal = Image.new("L", CANVAS_SIZE, 0)
    metal_pixels = metal.load()
    for y in range(CANVAS_SIZE[1]):
        for x in range(CANVAS_SIZE[0]):
            if is_metal_source(source[x, y]):
                metal_pixels[x, y] = 255
    latch_guard = metal.filter(ImageFilter.MaxFilter(3))

    protected = Image.new("L", CANVAS_SIZE, 0)
    protected_pixels = protected.load()
    for y in range(CANVAS_SIZE[1]):
        for x in range(CANVAS_SIZE[0]):
            if edge.getpixel((x, y)) or latch_guard.getpixel((x, y)):
                protected_pixels[x, y] = 255
    return protected


def render_accents(style_id: str, vanilla: Image.Image, protected: Image.Image) -> Image.Image:
    result = new_layer()
    source_alpha = vanilla.getchannel("A")
    style = STYLES[style_id]
    for accent_index, path in ACCENT_PATHS[style_id]:
        candidate = Image.new("L", CANVAS_SIZE, 0)
        draw = ImageDraw.Draw(candidate)
        if len(path) == 1:
            draw.point(path[0], fill=255)
        else:
            draw.line(path, fill=255, width=1)
        for y in range(CANVAS_SIZE[1]):
            for x in range(CANVAS_SIZE[0]):
                if (
                    candidate.getpixel((x, y))
                    and source_alpha.getpixel((x, y))
                    and not protected.getpixel((x, y))
                ):
                    result.putpixel((x, y), rgba(style.accents[accent_index]))
    return result


def composite_layers(layers: dict[str, Image.Image]) -> Image.Image:
    result = new_layer()
    for name in LAYER_NAMES:
        result = Image.alpha_composite(result, layers[name])
    return result


def render_chest(style_id: str, vanilla: Image.Image) -> tuple[dict[str, Image.Image], Image.Image]:
    mapping = build_palette_mapping(vanilla, STYLES[style_id])
    layers = {
        "palette_recolor": remap_image(vanilla, mapping),
        "style_accents": render_accents(style_id, vanilla, protected_mask(vanilla)),
    }
    composite = composite_layers(layers)
    composite.putalpha(vanilla.getchannel("A"))
    return layers, composite


def render_particle_layers(chest_layers: dict[str, Image.Image]) -> tuple[dict[str, Image.Image], Image.Image]:
    result: dict[str, Image.Image] = {}
    material_box = (14, 19, 28, 33)
    for name in LAYER_NAMES:
        tile = chest_layers[name].crop(material_box).resize(PARTICLE_SIZE, Image.Resampling.NEAREST)
        layer = new_layer()
        layer.alpha_composite(tile, (0, 0))
        result[name] = layer
    particle = composite_layers(result).crop((0, 0, *PARTICLE_SIZE))
    if 0 in set(flat_pixels(particle.getchannel("A"))):
        raise AssertionError("selected vanilla material crop is not fully opaque")
    return result, particle


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def write_gpl(style_id: str) -> None:
    style = STYLES[style_id]
    names = (
        *(f"wood_{index:02d}" for index in range(WOOD_STEPS)),
        *(f"metal_{index:02d}" for index in range(METAL_STEPS)),
        "accent_dark",
        "accent",
        "accent_light",
    )
    lines = ["GIMP Palette", f"Name: {style.title}", "Columns: 8", "#"]
    for name, color in zip(names, style.colors):
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
        "<6HB3s",
        3,
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
    chest_layers: dict[str, Image.Image],
    particle_layers: dict[str, Image.Image],
) -> bytes:
    frame_images = (
        tuple(chest_layers[name] for name in LAYER_NAMES),
        tuple(particle_layers[name] for name in LAYER_NAMES),
    )
    frames: list[bytes] = []
    for frame_index in range(FRAME_COUNT):
        chunks: list[bytes] = []
        if frame_index == 0:
            chunks.extend(aseprite_layer_chunk(name) for name in LAYER_NAMES)
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


def decode_aseprite(data: bytes) -> tuple[list[str], list[list[Image.Image]]]:
    file_size, magic, frame_count, width, height, depth = struct.unpack_from("<I5H", data, 0)
    if file_size != len(data) or magic != 0xA5E0:
        raise AssertionError("invalid Aseprite file header")
    if (frame_count, width, height, depth) != (FRAME_COUNT, *CANVAS_SIZE, 32):
        raise AssertionError("unexpected Aseprite canvas, frame count, or color depth")

    offset = 128
    names: list[str] = []
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
                name_length = struct.unpack_from("<H", data, payload + 16)[0]
                name_start = payload + 18
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
        if set(cels) != set(range(len(LAYER_NAMES))):
            raise AssertionError("Aseprite frame has missing cels")
        frames.append([cels[index] for index in range(len(LAYER_NAMES))])
    if offset != len(data):
        raise AssertionError("Aseprite file has trailing data")
    return names, frames


def assembled_front(chest: Image.Image) -> Image.Image:
    result = Image.new("RGBA", (14, 15), TRANSPARENT)
    result.alpha_composite(chest.crop((14, 14, 28, 19)), (0, 0))
    result.alpha_composite(chest.crop((14, 33, 28, 43)), (0, 5))
    result.alpha_composite(chest.crop((1, 1, 3, 5)), (6, 3))
    return result


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


def write_all_styles_preview(vanilla: Image.Image) -> None:
    panel_size = (272, 360)
    entries = (("vanilla", vanilla),) + tuple(
        (style_id, Image.open(ENTITY_DIR / f"{style_id}.png").convert("RGBA"))
        for style_id in STYLE_IDS
    )
    preview = Image.new("RGBA", (panel_size[0] * len(entries), panel_size[1]), TRANSPARENT)
    for index, (label, chest) in enumerate(entries):
        panel = stone_background(panel_size, light=False)
        panel.alpha_composite(stone_background((panel_size[0] // 2, 80), light=True), (136, 280))
        ImageDraw.Draw(panel).text((8, 7), label, fill=(240, 243, 239, 255))
        panel.alpha_composite(chest.resize((256, 256), Image.Resampling.NEAREST), (8, 24))
        front = assembled_front(chest).resize((56, 60), Image.Resampling.NEAREST)
        panel.alpha_composite(front, (40, 290))
        panel.alpha_composite(front, (176, 290))
        preview.alpha_composite(panel, (index * panel_size[0], 0))
    save_png(preview, PREVIEW_DIR / "all_styles_4x.png")


def horizontal_structure(vanilla: Image.Image, target: Image.Image) -> float:
    vanilla_edges = 0
    kept_edges = 0
    for y in range(CANVAS_SIZE[1]):
        for x in range(CANVAS_SIZE[0] - 1):
            vanilla_edge = (
                abs(luminance(vanilla.getpixel((x, y))) - luminance(vanilla.getpixel((x + 1, y))))
                > 12
            )
            if vanilla_edge:
                vanilla_edges += 1
                target_edge = (
                    abs(luminance(target.getpixel((x, y))) - luminance(target.getpixel((x + 1, y))))
                    > 12
                )
                kept_edges += target_edge
    return 100.0 * kept_edges / max(1, vanilla_edges)


def validate_style(style_id: str, vanilla: Image.Image) -> str:
    style = STYLES[style_id]
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
    allowed = set(style.colors)
    if not colors <= allowed or not particle_colors <= allowed:
        raise AssertionError(f"{style_id}: output contains colors outside its GPL palette")
    if not 12 <= len(colors) <= 24:
        raise AssertionError(f"{style_id}: expected 12..24 opaque colors, got {len(colors)}")

    mapping = build_palette_mapping(vanilla, style)
    base = remap_image(vanilla, mapping)
    protected = protected_mask(vanilla)
    opaque_pixels = sum(alpha == 255 for alpha in vanilla_alpha)
    accent_pixels = 0
    protected_changes = 0
    for y in range(CANVAS_SIZE[1]):
        for x in range(CANVAS_SIZE[0]):
            if chest_alpha[y * CANVAS_SIZE[0] + x] and chest.getpixel((x, y)) != base.getpixel((x, y)):
                accent_pixels += 1
                protected_changes += bool(protected.getpixel((x, y)))
    accent_fraction = accent_pixels / opaque_pixels
    if accent_pixels == 0 or accent_fraction > MAX_ACCENT_FRACTION or protected_changes:
        raise AssertionError(
            f"{style_id}: accents={accent_pixels} ({accent_fraction:.1%}), "
            f"protected-changes={protected_changes}"
        )

    structure = horizontal_structure(vanilla, chest)
    if structure < 70:
        raise AssertionError(f"{style_id}: vanilla structure retained only {structure:.1f}%")

    metal_mask = [is_metal_source(pixel) for pixel in flat_pixels(vanilla)]
    metal_luma = [luminance(pixel) for pixel, selected in zip(flat_pixels(chest), metal_mask) if selected]
    body_luma = [
        luminance(pixel)
        for pixel, source_pixel in zip(flat_pixels(chest), flat_pixels(vanilla))
        if source_pixel[3] and not is_metal_source(source_pixel)
    ]
    if sum(metal_luma) / len(metal_luma) <= sum(body_luma) / len(body_luma):
        raise AssertionError(f"{style_id}: latch is not lighter than the chest body")

    source_path = ART_DIR / f"{style_id}.aseprite"
    names, frames = decode_aseprite(source_path.read_bytes())
    if names != list(LAYER_NAMES):
        raise AssertionError(f"{style_id}: incorrect Aseprite layer structure")
    source_chest = composite_layers(dict(zip(LAYER_NAMES, frames[0])))
    source_particle = composite_layers(dict(zip(LAYER_NAMES, frames[1]))).crop((0, 0, *PARTICLE_SIZE))
    if source_chest.tobytes() != chest.tobytes() or source_particle.tobytes() != particle.tobytes():
        raise AssertionError(f"{style_id}: Aseprite frames do not reproduce runtime PNG pixels")

    return (
        f"{style_id:10s} size=64x64 mask-diff={mask_diff} semi-alpha={semi_alpha} "
        f"structure={structure:.0f}% colors={len(colors)} accents={accent_fraction:.1%}"
    )


def build_style(style_id: str, vanilla: Image.Image) -> None:
    chest_layers, chest = render_chest(style_id, vanilla)
    particle_layers, particle = render_particle_layers(chest_layers)
    save_png(chest, ENTITY_DIR / f"{style_id}.png")
    save_png(particle, BLOCK_DIR / f"boss_chest_{style_id}.png")
    write_gpl(style_id)
    write_preview(style_id, chest)
    source_path = ART_DIR / f"{style_id}.aseprite"
    source_path.parent.mkdir(parents=True, exist_ok=True)
    source_path.write_bytes(encode_aseprite(chest_layers, particle_layers))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Palette-remap Minecraft 1.21.1 boss chest skins")
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
        if all((ENTITY_DIR / f"{style_id}.png").is_file() for style_id in STYLE_IDS):
            write_all_styles_preview(vanilla)
            print("generated all_styles_4x")
    for style_id in selected:
        print(validate_style(style_id, vanilla))
    all_preview = PREVIEW_DIR / "all_styles_4x.png"
    if selected == STYLE_IDS and (not all_preview.is_file() or Image.open(all_preview).size != (1904, 360)):
        raise AssertionError("all_styles_4x.png is missing or has incorrect dimensions")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, FileNotFoundError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1)

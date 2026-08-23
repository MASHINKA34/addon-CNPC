"""Generate and validate the four deterministic boss-bar timer art sets.

This is the Pillow fallback for workstations without Aseprite CLI.  It also
writes small, standards-compliant Aseprite sources so the three runtime layers
remain editable.  Every gameplay color is sampled from the matching parent
boss bar; previews alone use an opaque neutral backdrop.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from hashlib import sha256
from io import BytesIO
from pathlib import Path
import struct
import zlib

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ART_ROOT = ROOT / "art" / "aseprite" / "boss_bar"
RUNTIME_ROOT = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "cnpcgeckoaddon"
    / "textures"
    / "gui"
    / "boss_bar"
)
LAYER_NAMES = ("timer_background", "timer_fill", "timer_frame")
TRANSPARENT = (0, 0, 0, 0)


Color = tuple[int, int, int, int]


@dataclass(frozen=True)
class StyleSpec:
    style_id: str
    canvas: tuple[int, int]
    track: tuple[int, int, int, int]
    preview_scale: int
    palette: tuple[tuple[str, Color], ...]

    @property
    def colors(self) -> dict[str, Color]:
        return dict(self.palette)


def c(red: int, green: int, blue: int) -> Color:
    return red, green, blue, 255


SPECS = {
    "moss_cave": StyleSpec(
        "moss_cave",
        (260, 10),
        (31, 2, 200, 6),
        4,
        (
            ("outline", c(1, 1, 0)),
            ("track_shadow", c(12, 24, 21)),
            ("track_low", c(18, 28, 25)),
            ("track_mid", c(19, 34, 29)),
            ("track_high", c(22, 36, 32)),
            ("stone_dark", c(43, 52, 55)),
            ("stone_mid", c(56, 70, 70)),
            ("stone_light", c(84, 104, 105)),
            ("stone_high", c(105, 134, 133)),
            ("moss", c(71, 87, 39)),
        ),
    ),
    "ghost_dungeon": StyleSpec(
        "ghost_dungeon",
        (1329, 54),
        (104, 12, 1121, 30),
        1,
        (
            ("outline", c(0, 0, 0)),
            ("track_edge", c(3, 5, 16)),
            ("track_shadow", c(12, 18, 30)),
            ("track_dark", c(19, 22, 34)),
            ("track_mid", c(20, 23, 35)),
            ("stone_dark", c(39, 40, 50)),
            ("stone_low", c(53, 53, 70)),
            ("stone_mid", c(58, 58, 75)),
            ("stone_light", c(105, 108, 113)),
            ("stone_high", c(138, 138, 139)),
            ("rune", c(200, 121, 255)),
        ),
    ),
    "infernal": StyleSpec(
        "infernal",
        (182, 7),
        (14, 2, 154, 3),
        4,
        (
            ("outline", c(9, 7, 10)),
            ("track", c(20, 18, 22)),
            ("track_low", c(27, 24, 31)),
            ("track_high", c(39, 37, 42)),
            ("iron_dark", c(44, 41, 48)),
            ("iron", c(65, 61, 69)),
            ("iron_hot", c(91, 80, 76)),
            ("ember", c(190, 38, 7)),
            ("ember_hot", c(231, 68, 6)),
        ),
    ),
    "sculk": StyleSpec(
        "sculk",
        (256, 12),
        (29, 3, 198, 6),
        4,
        (
            ("outline", c(2, 5, 9)),
            ("empty", c(4, 9, 15)),
            ("empty_blue", c(5, 15, 23)),
            ("vein", c(7, 22, 32)),
            ("sculk_dark", c(3, 29, 40)),
            ("slate_dark", c(10, 29, 41)),
            ("slate", c(22, 49, 61)),
            ("slate_light", c(34, 62, 73)),
            ("slate_high", c(67, 91, 99)),
            ("turquoise", c(4, 91, 111)),
            ("cyan", c(10, 187, 194)),
        ),
    ),
}


def blank(spec: StyleSpec) -> Image.Image:
    return Image.new("RGBA", spec.canvas, TRANSPARENT)


def inclusive_rect(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], color: Color) -> None:
    draw.rectangle(box, fill=color)


def put(image: Image.Image, x: int, y: int, color: Color) -> None:
    if 0 <= x < image.width and 0 <= y < image.height:
        image.putpixel((x, y), color)


def draw_moss(spec: StyleSpec) -> dict[str, Image.Image]:
    p = spec.colors
    tx, ty, tw, th = spec.track
    background, fill, frame = (blank(spec) for _ in range(3))
    bg = ImageDraw.Draw(background)
    fg = ImageDraw.Draw(fill)
    fr = ImageDraw.Draw(frame)

    inclusive_rect(bg, (tx, ty, tx + tw - 1, ty + th - 1), p["track_mid"])
    inclusive_rect(bg, (tx, ty, tx + tw - 1, ty), p["track_high"])
    inclusive_rect(bg, (tx, ty + th - 2, tx + tw - 1, ty + th - 1), p["track_shadow"])
    for x in range(tx + 7, tx + tw - 2, 17):
        put(background, x, ty + 2, p["track_low"])
        put(background, x + 1, ty + 3, p["track_low"])

    inclusive_rect(fg, (tx, ty, tx + tw - 1, ty + th - 1), p["stone_mid"])
    inclusive_rect(fg, (tx, ty, tx + tw - 1, ty), p["stone_light"])
    inclusive_rect(fg, (tx, ty + 1, tx + tw - 1, ty + 1), p["stone_high"])
    inclusive_rect(fg, (tx, ty + th - 1, tx + tw - 1, ty + th - 1), p["stone_dark"])

    inclusive_rect(fr, (tx - 1, ty - 1, tx + tw, ty - 1), p["outline"])
    inclusive_rect(fr, (tx - 1, ty + th, tx + tw, ty + th), p["outline"])
    inclusive_rect(fr, (tx - 1, ty, tx - 1, ty + th - 1), p["outline"])
    inclusive_rect(fr, (tx + tw, ty, tx + tw, ty + th - 1), p["outline"])
    for x in range(tx + 5, tx + tw - 2, 23):
        put(frame, x, ty - 1, p["stone_light"])
    for x in range(tx + 13, tx + tw - 2, 29):
        put(frame, x, ty + th, p["moss"])
    for x in (tx - 3, tx - 2, tx + tw + 1, tx + tw + 2):
        put(frame, x, ty + 2 if x < tx else ty + 3, p["outline"])
    put(frame, tx - 2, ty + 1, p["moss"])
    put(frame, tx + tw + 1, ty + th - 2, p["moss"])
    return dict(zip(LAYER_NAMES, (background, fill, frame), strict=True))


def draw_ghost(spec: StyleSpec) -> dict[str, Image.Image]:
    p = spec.colors
    tx, ty, tw, th = spec.track
    background, fill, frame = (blank(spec) for _ in range(3))
    bg = ImageDraw.Draw(background)
    fg = ImageDraw.Draw(fill)
    fr = ImageDraw.Draw(frame)

    inclusive_rect(bg, (tx, ty, tx + tw - 1, ty + th - 1), p["track_dark"])
    inclusive_rect(bg, (tx, ty, tx + tw - 1, ty + 2), p["track_shadow"])
    inclusive_rect(bg, (tx, ty + th - 3, tx + tw - 1, ty + th - 1), p["track_edge"])
    for x in range(tx + 42, tx + tw - 20, 84):
        inclusive_rect(bg, (x, ty + 3, x + 2, ty + 8), p["track_mid"])
        inclusive_rect(bg, (x + 3, ty + 9, x + 5, ty + 14), p["track_shadow"])
        inclusive_rect(bg, (x + 6, ty + 15, x + 8, ty + 20), p["track_mid"])

    inclusive_rect(fg, (tx, ty, tx + tw - 1, ty + th - 1), p["stone_mid"])
    inclusive_rect(fg, (tx, ty, tx + tw - 1, ty + 5), p["stone_light"])
    inclusive_rect(fg, (tx, ty + 6, tx + tw - 1, ty + 8), p["stone_high"])
    inclusive_rect(fg, (tx, ty + th - 6, tx + tw - 1, ty + th - 1), p["stone_dark"])

    # Three-pixel masonry keeps the HD parent bar's larger apparent pixel size.
    inclusive_rect(fr, (tx - 4, ty - 6, tx + tw + 3, ty - 1), p["outline"])
    inclusive_rect(fr, (tx - 4, ty + th, tx + tw + 3, ty + th + 5), p["outline"])
    inclusive_rect(fr, (tx - 1, ty - 4, tx + tw, ty - 1), p["stone_mid"])
    inclusive_rect(fr, (tx - 1, ty + th, tx + tw, ty + th + 3), p["stone_dark"])
    inclusive_rect(fr, (tx - 7, ty - 2, tx - 1, ty + th + 1), p["outline"])
    inclusive_rect(fr, (tx + tw, ty - 2, tx + tw + 6, ty + th + 1), p["outline"])
    inclusive_rect(fr, (tx - 4, ty, tx - 1, ty + th - 1), p["stone_low"])
    inclusive_rect(fr, (tx + tw, ty, tx + tw + 3, ty + th - 1), p["stone_low"])
    for x in range(tx + 24, tx + tw - 18, 72):
        inclusive_rect(fr, (x, ty - 4, x + 2, ty - 1), p["stone_dark"])
        inclusive_rect(fr, (x + 3, ty - 3, x + 14, ty - 1), p["stone_light"])
        inclusive_rect(fr, (x + 18, ty + th, x + 20, ty + th + 3), p["stone_light"])
    cx = spec.canvas[0] // 2
    for offset, color in ((0, p["outline"]), (3, p["stone_low"]), (6, p["stone_light"])):
        fr.polygon(
            ((cx, ty - 11 + offset), (cx + 8 - offset // 2, ty - 3),
             (cx, ty + 2 - offset // 3), (cx - 8 + offset // 2, ty - 3)),
            fill=color,
        )
    inclusive_rect(fr, (cx - 1, ty - 5, cx + 1, ty - 2), p["rune"])
    fr.polygon(
        ((cx, ty + th - 2), (cx + 7, ty + th + 4), (cx, ty + th + 11),
         (cx - 7, ty + th + 4)),
        fill=p["outline"],
    )
    fr.polygon(
        ((cx, ty + th), (cx + 3, ty + th + 4), (cx, ty + th + 7),
         (cx - 3, ty + th + 4)),
        fill=p["stone_mid"],
    )
    put(frame, cx, ty + th + 4, p["rune"])
    return dict(zip(LAYER_NAMES, (background, fill, frame), strict=True))


def draw_infernal(spec: StyleSpec) -> dict[str, Image.Image]:
    p = spec.colors
    tx, ty, tw, th = spec.track
    background, fill, frame = (blank(spec) for _ in range(3))
    bg = ImageDraw.Draw(background)
    fg = ImageDraw.Draw(fill)
    fr = ImageDraw.Draw(frame)

    inclusive_rect(bg, (tx, ty, tx + tw - 1, ty + th - 1), p["track"])
    inclusive_rect(bg, (tx, ty, tx + tw - 1, ty), p["track_high"])
    inclusive_rect(bg, (tx, ty + th - 1, tx + tw - 1, ty + th - 1), p["track_low"])

    inclusive_rect(fg, (tx, ty, tx + tw - 1, ty), p["iron_hot"])
    inclusive_rect(fg, (tx, ty + 1, tx + tw - 1, ty + 1), p["iron"])
    inclusive_rect(fg, (tx, ty + 2, tx + tw - 1, ty + 2), p["iron_dark"])

    inclusive_rect(fr, (tx - 1, ty - 1, tx + tw, ty - 1), p["outline"])
    inclusive_rect(fr, (tx - 1, ty + th, tx + tw, ty + th), p["outline"])
    inclusive_rect(fr, (tx - 2, ty, tx - 1, ty + th - 1), p["outline"])
    inclusive_rect(fr, (tx + tw, ty, tx + tw + 1, ty + th - 1), p["outline"])
    put(frame, tx - 3, ty + 1, p["iron"])
    put(frame, tx + tw + 2, ty + 1, p["iron"])
    for x in range(tx + 20, tx + tw - 8, 37):
        put(frame, x, ty - 1, p["ember"])
    put(frame, tx + tw // 2, ty + th, p["ember_hot"])
    return dict(zip(LAYER_NAMES, (background, fill, frame), strict=True))


def draw_sculk(spec: StyleSpec) -> dict[str, Image.Image]:
    p = spec.colors
    tx, ty, tw, th = spec.track
    background, fill, frame = (blank(spec) for _ in range(3))
    bg = ImageDraw.Draw(background)
    fg = ImageDraw.Draw(fill)
    fr = ImageDraw.Draw(frame)

    inclusive_rect(bg, (tx, ty, tx + tw - 1, ty + th - 1), p["empty_blue"])
    inclusive_rect(bg, (tx, ty, tx + tw - 1, ty), p["vein"])
    inclusive_rect(bg, (tx, ty + th - 1, tx + tw - 1, ty + th - 1), p["empty"])
    for x in range(tx + 9, tx + tw - 3, 18):
        put(background, x, ty + 2, p["sculk_dark"])
        put(background, x + 1, ty + 3, p["vein"])

    inclusive_rect(fg, (tx, ty, tx + tw - 1, ty + th - 1), p["slate"])
    inclusive_rect(fg, (tx, ty, tx + tw - 1, ty), p["slate_high"])
    inclusive_rect(fg, (tx, ty + 1, tx + tw - 1, ty + 1), p["slate_light"])
    inclusive_rect(fg, (tx, ty + th - 1, tx + tw - 1, ty + th - 1), p["slate_dark"])

    inclusive_rect(fr, (tx - 1, ty - 2, tx + tw, ty - 2), p["outline"])
    inclusive_rect(fr, (tx - 1, ty - 1, tx + tw, ty - 1), p["slate_dark"])
    inclusive_rect(fr, (tx - 1, ty + th, tx + tw, ty + th), p["slate_dark"])
    inclusive_rect(fr, (tx - 1, ty + th + 1, tx + tw, ty + th + 1), p["outline"])
    inclusive_rect(fr, (tx - 2, ty, tx - 1, ty + th - 1), p["outline"])
    inclusive_rect(fr, (tx + tw, ty, tx + tw + 1, ty + th - 1), p["outline"])
    for x in range(tx + 15, tx + tw - 5, 31):
        put(frame, x, ty - 1, p["slate_light"])
    for left in (True, False):
        x0 = tx - 5 if left else tx + tw + 4
        direction = 1 if left else -1
        put(frame, x0, ty + 1, p["outline"])
        put(frame, x0 + direction, ty, p["slate"])
        put(frame, x0 + direction, ty + 2, p["turquoise"])
        put(frame, x0 + 2 * direction, ty + 3, p["cyan"])
        put(frame, x0 + direction, ty + 4, p["turquoise"])
        put(frame, x0, ty + th, p["outline"])
    put(frame, tx + 19, ty + th + 2, p["turquoise"])
    put(frame, tx + tw - 20, ty - 3, p["turquoise"])
    return dict(zip(LAYER_NAMES, (background, fill, frame), strict=True))


DRAWERS = {
    "moss_cave": draw_moss,
    "ghost_dungeon": draw_ghost,
    "infernal": draw_infernal,
    "sculk": draw_sculk,
}


def parent_colors(style_id: str) -> set[Color]:
    colors: set[Color] = set()
    for layer_name in ("background", "fill", "frame"):
        path = RUNTIME_ROOT / style_id / f"{layer_name}.png"
        with Image.open(path) as source:
            colors.update(source.convert("RGBA").get_flattened_data())
    colors.discard(TRANSPARENT)
    return colors


def validate_palette(spec: StyleSpec) -> None:
    available = parent_colors(spec.style_id)
    missing = [(name, color) for name, color in spec.palette if color not in available]
    if missing:
        raise AssertionError(f"{spec.style_id}: colors absent from parent bar: {missing}")


def tint(image: Image.Image, color: tuple[int, int, int]) -> Image.Image:
    result = image.copy()
    pixels = result.load()
    for y in range(result.height):
        for x in range(result.width):
            red, green, blue, alpha = pixels[x, y]
            if alpha:
                pixels[x, y] = (
                    red * color[0] // 255,
                    green * color[1] // 255,
                    blue * color[2] // 255,
                    alpha,
                )
    return result


def compose_timer(
    spec: StyleSpec,
    layers: dict[str, Image.Image],
    progress: float,
    tint_color: tuple[int, int, int] | None = None,
) -> Image.Image:
    tx, _ty, tw, _th = spec.track
    result = blank(spec)
    result.alpha_composite(layers["timer_background"])
    fill = layers["timer_fill"]
    if tint_color:
        fill = tint(fill, tint_color)
    width = max(0, min(tw, round(tw * progress)))
    if width:
        result.alpha_composite(fill.crop((tx, 0, tx + width, spec.canvas[1])), (tx, 0))
    result.alpha_composite(layers["timer_frame"])
    return result


def compose_parent(style_id: str) -> Image.Image:
    images = [
        Image.open(RUNTIME_ROOT / style_id / f"{name}.png").convert("RGBA")
        for name in ("background", "fill", "frame")
    ]
    result = Image.new("RGBA", images[0].size, TRANSPARENT)
    for image in images:
        result.alpha_composite(image)
        image.close()
    return result


def build_preview(spec: StyleSpec, layers: dict[str, Image.Image]) -> Image.Image:
    parent = compose_parent(spec.style_id)
    gap = 12 if spec.style_id == "ghost_dungeon" else 2
    row_gap = 12 if spec.style_id == "ghost_dungeon" else 4
    row_height = parent.height + gap + spec.canvas[1]
    states = ((0.05, None), (0.50, None), (0.95, None), (0.50, (255, 72, 72)))
    height = row_height * len(states) + row_gap * (len(states) - 1)
    preview = Image.new("RGBA", (spec.canvas[0], height), (24, 26, 34, 255))
    for index, (progress, tint_color) in enumerate(states):
        y = index * (row_height + row_gap)
        preview.alpha_composite(parent, (0, y))
        timer = compose_timer(spec, layers, progress, tint_color)
        preview.alpha_composite(timer, (0, y + parent.height + gap))
    if spec.preview_scale != 1:
        preview = preview.resize(
            (preview.width * spec.preview_scale, preview.height * spec.preview_scale),
            Image.Resampling.NEAREST,
        )
    return preview


def aseprite_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack("<H", len(encoded)) + encoded


def aseprite_chunk(chunk_type: int, payload: bytes) -> bytes:
    return struct.pack("<IH", 6 + len(payload), chunk_type) + payload


def aseprite_layer_chunk(name: str, width: int, height: int) -> bytes:
    payload = struct.pack(
        "<HHHHHHB3s", 3, 0, 0, width, height, 0, 255, b"\0" * 3
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
        image.width,
        image.height,
    )
    return aseprite_chunk(0x2005, payload + zlib.compress(image.tobytes(), level=9))


def encode_aseprite(spec: StyleSpec, layers: dict[str, Image.Image]) -> bytes:
    chunks = [aseprite_layer_chunk(name, *spec.canvas) for name in LAYER_NAMES]
    chunks.extend(
        aseprite_cel_chunk(index, layers[name])
        for index, name in enumerate(LAYER_NAMES)
    )
    frame_size = 16 + sum(map(len, chunks))
    header = struct.pack(
        "<IHHHHHIHII B3sHBBhhHH84s",
        128 + frame_size,
        0xA5E0,
        1,
        spec.canvas[0],
        spec.canvas[1],
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
        1,
        1,
        b"\0" * 84,
    )
    frame_header = struct.pack(
        "<IHHH2sI", frame_size, 0xF1FA, len(chunks), 100, b"\0\0", len(chunks)
    )
    return header + frame_header + b"".join(chunks)


def decode_aseprite(data: bytes) -> tuple[tuple[str, ...], tuple[Image.Image, ...]]:
    file_size, magic, frames, width, height, depth = struct.unpack_from("<I5H", data, 0)
    if file_size != len(data) or magic != 0xA5E0:
        raise ValueError("Invalid Aseprite header")
    if frames != 1 or depth != 32:
        raise ValueError("Expected one RGBA Aseprite frame")
    frame_size, frame_magic, old_chunks = struct.unpack_from("<IHH", data, 128)
    new_chunks = struct.unpack_from("<I", data, 140)[0]
    if frame_magic != 0xF1FA or frame_size != len(data) - 128:
        raise ValueError("Invalid Aseprite frame")
    chunk_count = new_chunks or old_chunks
    offset = 144
    names: list[str] = []
    cels: dict[int, Image.Image] = {}
    for _ in range(chunk_count):
        chunk_size, chunk_type = struct.unpack_from("<IH", data, offset)
        payload = offset + 6
        if chunk_type == 0x2004:
            name_length = struct.unpack_from("<H", data, payload + 16)[0]
            start = payload + 18
            names.append(data[start : start + name_length].decode("utf-8"))
        elif chunk_type == 0x2005:
            layer_index = struct.unpack_from("<H", data, payload)[0]
            cel_type = struct.unpack_from("<H", data, payload + 7)[0]
            cel_width, cel_height = struct.unpack_from("<HH", data, payload + 16)
            if cel_type != 2 or (cel_width, cel_height) != (width, height):
                raise ValueError("Unsupported Aseprite cel")
            raw = zlib.decompress(data[payload + 20 : offset + chunk_size])
            cels[layer_index] = Image.frombytes("RGBA", (width, height), raw)
        offset += chunk_size
    if offset != len(data) or len(cels) != len(names):
        raise ValueError("Incomplete Aseprite source")
    return tuple(names), tuple(cels[index] for index in range(len(names)))


def png_bytes(image: Image.Image) -> bytes:
    stream = BytesIO()
    image.save(stream, "PNG", optimize=False, compress_level=9)
    return stream.getvalue()


def gpl_bytes(spec: StyleSpec) -> bytes:
    lines = [
        "GIMP Palette",
        f"Name: {spec.style_id} boss-bar timer",
        "Columns: 4",
        "# Exact colors sampled from the parent boss bar.",
    ]
    for name, (red, green, blue, _alpha) in spec.palette:
        lines.append(f"{red:3d} {green:3d} {blue:3d}\t{name}")
    return ("\n".join(lines) + "\n").encode("utf-8")


def validate_layers(spec: StyleSpec, layers: dict[str, Image.Image]) -> None:
    if tuple(layers) != LAYER_NAMES:
        raise AssertionError(f"{spec.style_id}: wrong layer order")
    tx, ty, tw, th = spec.track
    for name, image in layers.items():
        if image.mode != "RGBA" or image.size != spec.canvas:
            raise AssertionError(f"{spec.style_id}/{name}: wrong canvas or mode")
        if any(alpha not in (0, 255) for alpha in image.getchannel("A").get_flattened_data()):
            raise AssertionError(f"{spec.style_id}/{name}: non-binary alpha")
    fill = layers["timer_fill"]
    for y in range(fill.height):
        for x in range(fill.width):
            alpha = fill.getpixel((x, y))[3]
            in_track = tx <= x < tx + tw and ty <= y < ty + th
            if alpha != (255 if in_track else 0):
                raise AssertionError(f"{spec.style_id}: fill mask differs at {x},{y}")
    # Every fill column must be identical, so clipping can never bisect a motif.
    reference = tuple(fill.getpixel((tx, y)) for y in range(ty, ty + th))
    for x in range(tx + 1, tx + tw):
        if tuple(fill.getpixel((x, y)) for y in range(ty, ty + th)) != reference:
            raise AssertionError(f"{spec.style_id}: fill varies horizontally")
    background = layers["timer_background"]
    if any(
        background.getpixel((x, y))[3] != 255
        for y in range(ty, ty + th)
        for x in range(tx, tx + tw)
    ):
        raise AssertionError(f"{spec.style_id}: transparent gap in empty track")
    # Verify normal, enraged-red, and invulnerability-cold fill edges are legible.
    for tint_color in (None, (255, 72, 72), (80, 160, 255)):
        sample = layers["timer_fill"] if tint_color is None else tint(layers["timer_fill"], tint_color)
        distance = max(
            sum(abs(a - b) for a, b in zip(sample.getpixel((tx, y))[:3], background.getpixel((tx, y))[:3]))
            for y in range(ty, ty + th)
        )
        if distance < 24:
            raise AssertionError(f"{spec.style_id}: tinted edge lacks contrast")


def validate_png(data: bytes, size: tuple[int, int]) -> None:
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise AssertionError("not PNG")
    width, height = struct.unpack_from(">II", data, 16)
    bit_depth, color_type, interlace = data[24], data[25], data[28]
    if (width, height) != size or (bit_depth, color_type, interlace) != (8, 6, 0):
        raise AssertionError("PNG must be non-interlaced RGBA8 at the contract size")
    if any(chunk in data for chunk in (b"iCCP", b"sRGB", b"gAMA", b"cHRM")):
        raise AssertionError("PNG contains a color profile")


def expected_files(spec: StyleSpec, layers: dict[str, Image.Image]) -> dict[Path, bytes]:
    preview = build_preview(spec, layers)
    files = {
        RUNTIME_ROOT / spec.style_id / f"{name}.png": png_bytes(image)
        for name, image in layers.items()
    }
    files[ART_ROOT / spec.style_id / "timer.aseprite"] = encode_aseprite(spec, layers)
    files[ART_ROOT / spec.style_id / "timer.gpl"] = gpl_bytes(spec)
    files[ART_ROOT / spec.style_id / "timer_preview.png"] = png_bytes(preview)
    return files


def verify_source_rebuild(spec: StyleSpec, files: dict[Path, bytes]) -> None:
    source_path = ART_ROOT / spec.style_id / "timer.aseprite"
    source_data = files[source_path]
    names, decoded = decode_aseprite(source_data)
    if names != LAYER_NAMES:
        raise AssertionError(f"{spec.style_id}: Aseprite layer names/order differ")
    for name, image in zip(names, decoded, strict=True):
        runtime_path = RUNTIME_ROOT / spec.style_id / f"{name}.png"
        rebuilt = png_bytes(image)
        if rebuilt != files[runtime_path]:
            raise AssertionError(f"{spec.style_id}: {name} does not rebuild byte-for-byte")


def build_style(spec: StyleSpec, check: bool) -> None:
    validate_palette(spec)
    layers = DRAWERS[spec.style_id](spec)
    validate_layers(spec, layers)
    files = expected_files(spec, layers)
    verify_source_rebuild(spec, files)
    for path, data in files.items():
        if path.suffix == ".png" and path.name != "timer_preview.png":
            validate_png(data, spec.canvas)
        if check:
            if not path.exists() or path.read_bytes() != data:
                raise AssertionError(f"stale or missing: {path.relative_to(ROOT)}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    verb = "checked" if check else "wrote"
    digest = sha256(b"".join(files.values())).hexdigest()[:12]
    print(f"{verb} {spec.style_id}: {len(files)} files, sha256-set={digest}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--style",
        choices=("all", *SPECS),
        default="all",
        help="Generate one style or all four (default: all).",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate committed files without changing the worktree.",
    )
    args = parser.parse_args()
    styles = tuple(SPECS) if args.style == "all" else (args.style,)
    for style_id in styles:
        build_style(SPECS[style_id], args.check)


if __name__ == "__main__":
    main()

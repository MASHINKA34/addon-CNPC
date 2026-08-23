from __future__ import annotations

from collections import Counter
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zlib
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile

import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "art" / "boss_bar_infernal"
SOURCE_PATH = SOURCE_DIR / "reference_infernal_boss_bar.png"
RESOURCE_DIR = (
    ROOT
    / "src"
    / "main"
    / "resources"
    / "assets"
    / "cnpcgeckoaddon"
    / "textures"
    / "gui"
)

MERGED_PATH = RESOURCE_DIR / "boss_bar_infernal.png"
FRAME_PATH = RESOURCE_DIR / "boss_bar_infernal_frame.png"
FILL_PATH = RESOURCE_DIR / "boss_bar_infernal_fill.png"
ASEPRITE_PATH = SOURCE_DIR / "boss_bar_infernal.aseprite"
ORA_PATH = SOURCE_DIR / "boss_bar_infernal.ora"
PALETTE_PATH = SOURCE_DIR / "infernal_fire.gpl"
PREVIEW_PATH = SOURCE_DIR / "boss_bar_infernal_preview_2x.png"

CANVAS_WIDTH = 364
CANVAS_HEIGHT = 77
TRANSPARENT = (0, 0, 0, 0)
LAYER_ORDER = ["frame", "health_fill", "empty_bar", "lava_cracks", "decorations"]
BACK_TO_FRONT = ["empty_bar", "health_fill", "frame", "lava_cracks", "decorations"]


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False)


def new_layer() -> Image.Image:
    return Image.new("RGBA", (CANVAS_WIDTH, CANVAS_HEIGHT), TRANSPARENT)


def extract_reference_sprite() -> Image.Image:
    """Remove the baked checkerboard and keep the supplied sprite pixel-for-pixel."""
    source = np.asarray(Image.open(SOURCE_PATH).convert("RGB"))
    chroma = source.max(axis=2) - source.min(axis=2)

    # The supplied RGB file has a fake transparent background made only from
    # near-white neutral squares. The artwork is either darker or chromatic.
    foreground = (source.min(axis=2) < 220) | (chroma > 18)
    y_values, x_values = np.where(foreground)
    if not len(x_values):
        raise ValueError("No boss-bar pixels found in the reference image")

    x0, x1 = int(x_values.min()), int(x_values.max()) + 1
    y0, y1 = int(y_values.min()), int(y_values.max()) + 1
    rgb_crop = source[y0:y1, x0:x1]
    alpha_crop = (foreground[y0:y1, x0:x1] * 255).astype(np.uint8)
    rgba_crop = np.dstack((rgb_crop, alpha_crop))
    sprite = Image.fromarray(rgba_crop, mode="RGBA")
    return sprite.resize((CANVAS_WIDTH, CANVAS_HEIGHT), Image.Resampling.NEAREST)


def fire_mask(pixels: np.ndarray) -> np.ndarray:
    red = pixels[:, :, 0].astype(np.int16)
    green = pixels[:, :, 1].astype(np.int16)
    blue = pixels[:, :, 2].astype(np.int16)
    alpha = pixels[:, :, 3]
    return (
        (alpha > 0)
        & (red > 70)
        & (red * 4 > green * 5)
        & (red * 3 > blue * 4)
    )


def detect_track(sprite: Image.Image) -> tuple[int, int, int, int, int]:
    pixels = np.asarray(sprite)
    fire = fire_mask(pixels)

    # Ignore end ornaments and rail cracks while locating the health fill.
    search = fire.copy()
    search[:15, :] = False
    search[60:, :] = False
    search[:, :15] = False
    search[:, 260:] = False
    row_counts = search.sum(axis=1)
    hot_rows = np.where(row_counts > row_counts.max() * 0.45)[0]
    track_y0, track_y1 = int(hot_rows.min()), int(hot_rows.max()) + 1

    column_counts = search[track_y0:track_y1].sum(axis=0)
    hot_columns = np.where(column_counts > max(3, (track_y1 - track_y0) * 0.35))[0]
    track_x0 = int(hot_columns.min())
    fill_x1 = int(hot_columns.max()) + 1

    # The reference is symmetrical: the inner lane has equal left/right insets.
    track_x1 = CANVAS_WIDTH - track_x0
    if not (0 < track_x0 < fill_x1 < track_x1 <= CANVAS_WIDTH):
        raise ValueError("Could not locate the infernal boss-bar lane")
    return track_x0, track_y0, track_x1, track_y1, fill_x1


def masked_layer(sprite: Image.Image, mask: np.ndarray) -> Image.Image:
    pixels = np.asarray(sprite).copy()
    pixels[:, :, 3] = np.where(mask, pixels[:, :, 3], 0)
    return Image.fromarray(pixels, mode="RGBA")


def build_layers(
    sprite: Image.Image,
    track: tuple[int, int, int, int, int],
) -> tuple[dict[str, Image.Image], Image.Image, Image.Image]:
    track_x0, track_y0, track_x1, track_y1, fill_x1 = track
    track_width = track_x1 - track_x0
    track_height = track_y1 - track_y0
    pixels = np.asarray(sprite)
    alpha = pixels[:, :, 3] > 0
    fire = fire_mask(pixels)

    y_grid, x_grid = np.indices((CANVAS_HEIGHT, CANVAS_WIDTH))
    track_area = (
        (x_grid >= track_x0)
        & (x_grid < track_x1)
        & (y_grid >= track_y0)
        & (y_grid < track_y1)
    )
    decoration_mask = alpha & ((x_grid < track_x0) | (x_grid >= track_x1))
    lava_mask = alpha & fire & ~track_area & ~decoration_mask
    frame_mask = alpha & ~track_area & ~decoration_mask & ~lava_mask
    health_mask = alpha & fire & track_area & (x_grid < fill_x1)

    # Stretch only the already-empty right lane under the dynamic health lane.
    empty_sample_x0 = min(fill_x1 + 4, track_x1 - 2)
    empty_sample = sprite.crop((empty_sample_x0, track_y0, track_x1, track_y1))
    empty_strip = empty_sample.resize((track_width, track_height), Image.Resampling.NEAREST)
    empty_layer = new_layer()
    empty_layer.alpha_composite(empty_strip, (track_x0, track_y0))

    health_layer = masked_layer(sprite, health_mask)
    frame_layer = masked_layer(sprite, frame_mask)
    lava_layer = masked_layer(sprite, lava_mask)
    decorations_layer = masked_layer(sprite, decoration_mask)
    layers = {
        "frame": frame_layer,
        "health_fill": health_layer,
        "empty_bar": empty_layer,
        "lava_cracks": lava_layer,
        "decorations": decorations_layer,
    }

    # Runtime frame: everything except health. Runtime fill: the supplied molten
    # pattern normalized to the full lane so its yellow tip follows current HP.
    frame_texture = new_layer()
    for name in ["empty_bar", "frame", "lava_cracks", "decorations"]:
        frame_texture = Image.alpha_composite(frame_texture, layers[name])

    fill_crop = health_layer.crop((track_x0, track_y0, fill_x1, track_y1))
    fill_texture = fill_crop.resize((track_width, track_height), Image.Resampling.NEAREST)
    return layers, frame_texture, fill_texture


def composite_layers(layers: dict[str, Image.Image]) -> Image.Image:
    merged = new_layer()
    for name in BACK_TO_FRONT:
        merged = Image.alpha_composite(merged, layers[name])
    return merged


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
        CANVAS_WIDTH,
        CANVAS_HEIGHT,
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
        CANVAS_WIDTH,
        CANVAS_HEIGHT,
    )
    return aseprite_chunk(0x2005, payload + zlib.compress(image.tobytes(), level=9))


def write_aseprite(layers: dict[str, Image.Image]) -> None:
    chunks = [aseprite_layer_chunk(name) for name in BACK_TO_FRONT]
    chunks.extend(
        aseprite_cel_chunk(index, layers[name])
        for index, name in enumerate(BACK_TO_FRONT)
    )

    frame_size = 16 + sum(len(chunk) for chunk in chunks)
    frame_header = struct.pack(
        "<IHHH2sI",
        frame_size,
        0xF1FA,
        len(chunks),
        100,
        b"\0" * 2,
        len(chunks),
    )
    frame = frame_header + b"".join(chunks)
    file_size = 128 + len(frame)
    header = struct.pack(
        "<IHHHHHIHII B3sHBBhhHH84s",
        file_size,
        0xA5E0,
        1,
        CANVAS_WIDTH,
        CANVAS_HEIGHT,
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
    ASEPRITE_PATH.write_bytes(header + frame)


def write_openraster(layers: dict[str, Image.Image], merged: Image.Image) -> None:
    temp_dir = SOURCE_DIR / "_ora_layers"
    temp_dir.mkdir(parents=True, exist_ok=True)
    top_to_bottom = list(reversed(BACK_TO_FRONT))
    layer_files: dict[str, Path] = {}
    for index, name in enumerate(top_to_bottom):
        path = temp_dir / f"{index:02d}_{name}.png"
        save_png(layers[name], path)
        layer_files[name] = path
    merged_path = temp_dir / "mergedimage.png"
    save_png(merged, merged_path)

    stack_lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<image version="0.0.1" w="{CANVAS_WIDTH}" h="{CANVAS_HEIGHT}" name="boss_bar_infernal">',
        '  <stack name="root">',
    ]
    for name in top_to_bottom:
        stack_lines.append(
            f'    <layer name="{name}" src="data/{layer_files[name].name}" '
            'visibility="visible" opacity="1.0" composite-op="svg:src-over"/>'
        )
    stack_lines.extend(["  </stack>", "</image>"])

    with ZipFile(ORA_PATH, "w") as archive:
        archive.writestr("mimetype", "image/openraster", compress_type=ZIP_STORED)
        archive.writestr("stack.xml", "\n".join(stack_lines), compress_type=ZIP_DEFLATED)
        archive.write(merged_path, "mergedimage.png", compress_type=ZIP_DEFLATED)
        archive.write(merged_path, "Thumbnails/thumbnail.png", compress_type=ZIP_DEFLATED)
        for name in top_to_bottom:
            archive.write(layer_files[name], f"data/{layer_files[name].name}", compress_type=ZIP_DEFLATED)

    for path in temp_dir.iterdir():
        path.unlink()
    temp_dir.rmdir()


def write_palette(sprite: Image.Image) -> None:
    opaque = [pixel[:3] for pixel in sprite.get_flattened_data() if pixel[3] > 0]
    common = Counter(opaque).most_common(32)
    lines = ["GIMP Palette", "Name: Infernal Boss Bar HD", "Columns: 8", "#"]
    for index, ((red, green, blue), _) in enumerate(common):
        lines.append(f"{red:3d} {green:3d} {blue:3d}\tInfernal {index + 1:02d}")
    PALETTE_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")


def validate_aseprite(layers: dict[str, Image.Image]) -> None:
    data = ASEPRITE_PATH.read_bytes()
    file_size, magic, frames, width, height, depth = struct.unpack_from("<I5H", data, 0)
    assert file_size == len(data)
    assert magic == 0xA5E0
    assert (frames, width, height, depth) == (1, CANVAS_WIDTH, CANVAS_HEIGHT, 32)

    frame_size, frame_magic, chunk_count = struct.unpack_from("<IHH", data, 128)
    assert frame_magic == 0xF1FA
    assert frame_size == len(data) - 128
    assert chunk_count == 10

    offset = 144
    names: list[str] = []
    decoded_cels: dict[int, bytes] = {}
    for _ in range(chunk_count):
        chunk_size, chunk_type = struct.unpack_from("<IH", data, offset)
        payload = offset + 6
        if chunk_type == 0x2004:
            name_length = struct.unpack_from("<H", data, payload + 16)[0]
            names.append(data[payload + 18:payload + 18 + name_length].decode("utf-8"))
        elif chunk_type == 0x2005:
            index = struct.unpack_from("<H", data, payload)[0]
            compressed = data[payload + 20:offset + chunk_size]
            decoded_cels[index] = zlib.decompress(compressed)
        offset += chunk_size

    assert names == BACK_TO_FRONT
    for index, name in enumerate(BACK_TO_FRONT):
        assert decoded_cels[index] == layers[name].tobytes()


def validate_openraster() -> None:
    with ZipFile(ORA_PATH, "r") as archive:
        assert archive.read("mimetype") == b"image/openraster"
        root = ET.fromstring(archive.read("stack.xml"))
        assert (int(root.attrib["w"]), int(root.attrib["h"])) == (CANVAS_WIDTH, CANVAS_HEIGHT)
        names = [node.attrib["name"] for node in root.find("stack").findall("layer")]
        assert names == list(reversed(BACK_TO_FRONT))


def validate_outputs(
    sprite: Image.Image,
    frame: Image.Image,
    fill: Image.Image,
    track: tuple[int, int, int, int, int],
) -> None:
    track_x0, track_y0, track_x1, track_y1, _ = track
    assert sprite.size == (CANVAS_WIDTH, CANVAS_HEIGHT)
    assert frame.size == sprite.size
    assert fill.size == (track_x1 - track_x0, track_y1 - track_y0)
    for image in [sprite, frame, fill]:
        assert image.mode == "RGBA"
        assert {pixel[3] for pixel in image.get_flattened_data()} <= {0, 255}
    assert (track_x0, track_y0, track_x1, track_y1) == (34, 27, 330, 51)


def main() -> None:
    SOURCE_DIR.mkdir(parents=True, exist_ok=True)
    sprite = extract_reference_sprite()
    track = detect_track(sprite)
    layers, frame_texture, fill_texture = build_layers(sprite, track)
    layered_preview = composite_layers(layers)

    validate_outputs(sprite, frame_texture, fill_texture, track)
    save_png(sprite, MERGED_PATH)
    save_png(frame_texture, FRAME_PATH)
    save_png(fill_texture, FILL_PATH)
    save_png(sprite.resize((CANVAS_WIDTH * 2, CANVAS_HEIGHT * 2), Image.Resampling.NEAREST), PREVIEW_PATH)
    write_aseprite(layers)
    validate_aseprite(layers)
    write_openraster(layers, layered_preview)
    validate_openraster()
    write_palette(sprite)

    print(f"runtime_canvas={CANVAS_WIDTH}x{CANVAS_HEIGHT}")
    print(f"runtime_track={track[0]},{track[1]}..{track[2]},{track[3]}")
    for path in [MERGED_PATH, FRAME_PATH, FILL_PATH, ASEPRITE_PATH, ORA_PATH, PREVIEW_PATH]:
        print(path)


if __name__ == "__main__":
    main()

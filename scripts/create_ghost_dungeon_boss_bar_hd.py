#!/usr/bin/env python3
"""Build the high-resolution, dynamically fillable ghost boss-bar textures."""

from __future__ import annotations

import argparse
import struct
import zlib
from pathlib import Path

from PIL import Image, ImageDraw


CROP = (5, 430, 1334, 691)
TEXTURE_WIDTH = 1329
TEXTURE_HEIGHT = 261

# Interior lane, in pixels of the tightly cropped reference.
LANE_X = 104
LANE_Y = 108
LANE_WIDTH = 1121
LANE_HEIGHT = 105

# The reference's ectoplasm edge occupies this source window.
CAP_SOURCE_X = 300
CAP_SOURCE_RIGHT = 390
CAP_WIDTH = CAP_SOURCE_RIGHT - CAP_SOURCE_X


def extract_reference(source_path: Path) -> Image.Image:
    """Remove the baked checkerboard without redrawing or resampling the sprite."""
    source = Image.open(source_path).convert("RGBA")
    if source.size[0] < CROP[2] or source.size[1] < CROP[3]:
        raise ValueError(f"Reference is too small: {source.size}")

    # Both checker colors and their pale antialias pixels are connected to the canvas edge.
    # Flood filling keeps enclosed silver highlights and all colored spectral-fire pixels.
    ImageDraw.floodfill(source, (0, 0), (0, 0, 0, 0), thresh=80)
    sprite = source.crop(CROP)
    if sprite.size != (TEXTURE_WIDTH, TEXTURE_HEIGHT):
        raise AssertionError(f"Unexpected crop size: {sprite.size}")
    return sprite


def build_empty_frame(sprite: Image.Image) -> Image.Image:
    """Replace only the baked health fill with a mirrored piece of the empty lane."""
    frame = sprite.copy()
    source_pixels = sprite.load()
    frame_pixels = frame.load()
    mirror_seam = 386

    for y in range(LANE_Y, LANE_Y + LANE_HEIGHT):
        for x in range(LANE_X, mirror_seam):
            sample_x = mirror_seam + (mirror_seam - 1 - x)
            frame_pixels[x, y] = source_pixels[sample_x, y]
    return frame


def build_fill_body(sprite: Image.Image) -> Image.Image:
    """Tile the reference ectoplasm itself across the full health lane."""
    tile = sprite.crop((110, LANE_Y, 310, LANE_Y + LANE_HEIGHT))
    body = Image.new("RGBA", (LANE_WIDTH, LANE_HEIGHT), (0, 0, 0, 0))
    x = 0
    while x < LANE_WIDTH:
        width = min(tile.width, LANE_WIDTH - x)
        body.alpha_composite(tile.crop((0, 0, width, tile.height)), (x, 0))
        x += width
    return body


def is_ectoplasm_edge_pixel(pixel: tuple[int, int, int, int]) -> bool:
    red, green, blue, alpha = pixel
    return (
        alpha > 0
        and green > 48
        and blue > 48
        and green - red > 16
        and blue - red > 12
        and green * 100 > red * 118
        and blue * 100 > red * 110
    )


def build_fill_cap(sprite: Image.Image) -> Image.Image:
    """Extract the source's jagged ectoplasm edge for placement at live health."""
    cap = sprite.crop((CAP_SOURCE_X, LANE_Y, CAP_SOURCE_RIGHT, LANE_Y + LANE_HEIGHT))
    cap_pixels = cap.load()
    source_pixels = sprite.load()

    edges: list[int | None] = []
    for y in range(LANE_Y, LANE_Y + LANE_HEIGHT):
        candidates = [
            x
            for x in range(320, CAP_SOURCE_RIGHT)
            if is_ectoplasm_edge_pixel(source_pixels[x, y])
        ]
        edges.append(max(candidates) + 2 if candidates else None)

    # A couple of very dark edge rows contain no cyan sample. Borrow the nearest real edge
    # instead of flattening the silhouette there.
    known_rows = [index for index, edge in enumerate(edges) if edge is not None]
    if not known_rows:
        raise AssertionError("Could not find the ectoplasm edge in the reference")
    for index, edge in enumerate(edges):
        if edge is None:
            nearest = min(known_rows, key=lambda known: abs(known - index))
            edges[index] = edges[nearest]

    for y, edge in enumerate(edges):
        assert edge is not None
        for x in range(CAP_WIDTH):
            if CAP_SOURCE_X + x > edge:
                cap_pixels[x, y] = (0, 0, 0, 0)
    return cap


def render_preview(
    frame: Image.Image,
    fill: Image.Image,
    cap: Image.Image,
    progress: float,
) -> Image.Image:
    preview = frame.copy()
    fill_width = max(0, min(LANE_WIDTH, round(LANE_WIDTH * progress)))
    if fill_width <= 0:
        return preview

    if fill_width <= CAP_WIDTH:
        preview.alpha_composite(fill.crop((0, 0, fill_width, LANE_HEIGHT)), (LANE_X, LANE_Y))
        return preview

    body_width = fill_width - CAP_WIDTH + 4
    preview.alpha_composite(fill.crop((0, 0, body_width, LANE_HEIGHT)), (LANE_X, LANE_Y))
    preview.alpha_composite(cap, (LANE_X + fill_width - CAP_WIDTH, LANE_Y))
    return preview


def build_aseprite_layers(
    frame: Image.Image,
    fill: Image.Image,
    cap: Image.Image,
    progress: float,
) -> list[tuple[str, Image.Image]]:
    """Split the HD reference into the five editable layers requested for Aseprite."""
    empty_bar = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    health_fill = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    ghost_effects = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    frame_layer = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    highlights = Image.new("RGBA", frame.size, (0, 0, 0, 0))

    frame_pixels = frame.load()
    empty_pixels = empty_bar.load()
    ghost_pixels = ghost_effects.load()
    structure_pixels = frame_layer.load()
    highlight_pixels = highlights.load()

    for y in range(TEXTURE_HEIGHT):
        for x in range(TEXTURE_WIDTH):
            pixel = frame_pixels[x, y]
            if pixel[3] == 0:
                continue
            if LANE_X <= x < LANE_X + LANE_WIDTH and LANE_Y <= y < LANE_Y + LANE_HEIGHT:
                empty_pixels[x, y] = pixel
                continue

            red, green, blue, _alpha = pixel
            is_violet = blue - green > 24 and red > 45 and blue > 75
            is_bright_silver = min(red, green, blue) > 118 and max(red, green, blue) - min(red, green, blue) < 42
            if is_ectoplasm_edge_pixel(pixel):
                ghost_pixels[x, y] = pixel
            elif is_violet or is_bright_silver:
                highlight_pixels[x, y] = pixel
            else:
                structure_pixels[x, y] = pixel

    fill_width = max(0, min(LANE_WIDTH, round(LANE_WIDTH * progress)))
    if fill_width > 0:
        if fill_width <= CAP_WIDTH:
            health_fill.alpha_composite(fill.crop((0, 0, fill_width, LANE_HEIGHT)), (LANE_X, LANE_Y))
        else:
            body_width = fill_width - CAP_WIDTH + 4
            health_fill.alpha_composite(fill.crop((0, 0, body_width, LANE_HEIGHT)), (LANE_X, LANE_Y))
            health_fill.alpha_composite(cap, (LANE_X + fill_width - CAP_WIDTH, LANE_Y))

    layers = [
        ("empty_bar", empty_bar),
        ("health_fill", health_fill),
        ("ghost_effects", ghost_effects),
        ("frame", frame_layer),
        ("highlights", highlights),
    ]
    composite = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    for _name, layer in layers:
        composite.alpha_composite(layer)
    expected = render_preview(frame, fill, cap, progress)
    if composite.tobytes() != expected.tobytes():
        raise AssertionError("Aseprite layer composite differs from the PNG preview")
    return layers


def ase_string(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack("<H", len(encoded)) + encoded


def ase_chunk(chunk_type: int, data: bytes) -> bytes:
    return struct.pack("<IH", 6 + len(data), chunk_type) + data


def ase_layer_chunk(name: str) -> bytes:
    data = struct.pack(
        "<HHHHHHB3s",
        3,
        0,
        0,
        TEXTURE_WIDTH,
        TEXTURE_HEIGHT,
        0,
        255,
        b"\x00" * 3,
    ) + ase_string(name)
    return ase_chunk(0x2004, data)


def ase_cel_chunk(layer_index: int, image: Image.Image) -> bytes:
    compressed = zlib.compress(image.tobytes(), level=9)
    data = struct.pack(
        "<HhhBHh5sHH",
        layer_index,
        0,
        0,
        255,
        2,
        0,
        b"\x00" * 5,
        TEXTURE_WIDTH,
        TEXTURE_HEIGHT,
    ) + compressed
    return ase_chunk(0x2005, data)


def write_aseprite(path: Path, layers: list[tuple[str, Image.Image]]) -> None:
    chunks = [ase_layer_chunk(name) for name, _image in layers]
    chunks.extend(ase_cel_chunk(index, image) for index, (_name, image) in enumerate(layers))
    frame_size = 16 + sum(len(chunk) for chunk in chunks)
    file_size = 128 + frame_size

    header = struct.pack(
        "<IHHHHHIHII",
        file_size,
        0xA5E0,
        1,
        TEXTURE_WIDTH,
        TEXTURE_HEIGHT,
        32,
        1,
        100,
        0,
        0,
    )
    header += struct.pack("<B3sHBBhhHH", 0, b"\x00" * 3, 0, 1, 1, 0, 0, 1, 1)
    header += b"\x00" * 84
    frame_header = struct.pack(
        "<IHHH2sI",
        frame_size,
        0xF1FA,
        len(chunks),
        100,
        b"\x00\x00",
        len(chunks),
    )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(header + frame_header + b"".join(chunks))
    if path.stat().st_size != file_size:
        raise AssertionError("Written Aseprite file size differs from its header")


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def validate(
    image: Image.Image,
    expected_size: tuple[int, int],
    label: str,
    require_transparency: bool = True,
) -> None:
    if image.mode != "RGBA" or image.size != expected_size:
        raise AssertionError(f"{label}: expected RGBA {expected_size}, got {image.mode} {image.size}")
    alpha = image.getchannel("A")
    if require_transparency and alpha.getextrema() != (0, 255):
        raise AssertionError(f"{label}: texture must contain transparent and opaque pixels")
    if not require_transparency and alpha.getextrema() != (255, 255):
        raise AssertionError(f"{label}: texture must be fully opaque")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--frame", type=Path, required=True)
    parser.add_argument("--fill", type=Path, required=True)
    parser.add_argument("--cap", type=Path, required=True)
    parser.add_argument("--clean-reference", type=Path, required=True)
    parser.add_argument("--preview", type=Path, required=True)
    parser.add_argument("--aseprite", type=Path, required=True)
    args = parser.parse_args()

    sprite = extract_reference(args.source)
    frame = build_empty_frame(sprite)
    fill = build_fill_body(sprite)
    cap = build_fill_cap(sprite)
    preview = render_preview(frame, fill, cap, progress=0.25)
    aseprite_layers = build_aseprite_layers(frame, fill, cap, progress=0.25)

    validate(sprite, (TEXTURE_WIDTH, TEXTURE_HEIGHT), "clean reference")
    validate(frame, (TEXTURE_WIDTH, TEXTURE_HEIGHT), "frame")
    validate(fill, (LANE_WIDTH, LANE_HEIGHT), "fill", require_transparency=False)
    validate(cap, (CAP_WIDTH, LANE_HEIGHT), "cap")
    validate(preview, (TEXTURE_WIDTH, TEXTURE_HEIGHT), "preview")

    save(sprite, args.clean_reference)
    save(frame, args.frame)
    save(fill, args.fill)
    save(cap, args.cap)
    save(preview, args.preview)
    write_aseprite(args.aseprite, aseprite_layers)

    print(f"Clean reference: {args.clean_reference} {sprite.size}")
    print(f"Frame: {args.frame} {frame.size}")
    print(f"Fill: {args.fill} {fill.size}")
    print(f"Cap: {args.cap} {cap.size}")
    print(f"Preview (25% health): {args.preview} {preview.size}")
    print(f"Aseprite: {args.aseprite} ({len(aseprite_layers)} layers)")


if __name__ == "__main__":
    main()

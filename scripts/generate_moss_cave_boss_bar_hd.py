"""Build the in-game moss-cave boss bar from the approved visual reference.

The reference contains a baked checkerboard. This script removes it, restores
the intended 1:5 pixel grid, quantizes without dithering, and emits both the
editable Aseprite layer inputs and the textures used by the NeoForge renderer.
"""

from __future__ import annotations

import argparse
import colorsys
from pathlib import Path

from PIL import Image


TEXTURE_WIDTH = 260
TEXTURE_HEIGHT = 37
INTERIOR_X = 31
INTERIOR_Y = 11
INTERIOR_WIDTH = 200
INTERIOR_HEIGHT = 14
REFERENCE_CROP = (19, 495, 1319, 680)
EDGE_SOURCE_X = 124
EDGE_WIDTH = 7


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--reference",
        type=Path,
        default=root / "art/references/boss_bar_moss_cave_reference.png",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=root / "src/main/resources/assets/cnpcgeckoaddon/textures/gui",
    )
    parser.add_argument(
        "--layers-dir",
        type=Path,
        default=root / "build/codex_moss_bar_layers",
    )
    parser.add_argument(
        "--preview",
        type=Path,
        default=root / "build/codex_previews/boss_bar_moss_cave_hd_4x.png",
    )
    return parser.parse_args()


def foreground_pixel(rgb: tuple[int, int, int]) -> bool:
    """Reject the near-white neutral checkerboard while retaining bright spores."""
    return min(rgb) < 230 or max(rgb) - min(rgb) > 10


def extract_reference(reference: Path) -> Image.Image:
    source = Image.open(reference).convert("RGB")
    if source.size != (1338, 1175):
        raise ValueError(
            f"Unexpected reference size {source.size}; expected (1338, 1175)"
        )

    transparent = Image.new("RGBA", source.size, (0, 0, 0, 0))
    source_pixels = source.load()
    output_pixels = transparent.load()
    for y in range(source.height):
        for x in range(source.width):
            color = source_pixels[x, y]
            if foreground_pixel(color):
                output_pixels[x, y] = (*color, 255)

    sprite = transparent.crop(REFERENCE_CROP).resize(
        (TEXTURE_WIDTH, TEXTURE_HEIGHT), Image.Resampling.NEAREST
    )

    # A few neutral checker fragments touch the generated lower silhouette.
    # Remove only those; saturated roots, moss and the central gem are preserved.
    pixels = sprite.load()
    for y in range(30, TEXTURE_HEIGHT):
        for x in range(22, 238):
            red, green, blue, alpha = pixels[x, y]
            if (
                alpha
                and max(red, green, blue) - min(red, green, blue) < 14
                and (red + green + blue) / 3 > 120
            ):
                pixels[x, y] = (0, 0, 0, 0)

    # Pixel-art palette reduction: nearest colors only, never diffusion dithering.
    alpha = sprite.getchannel("A")
    rgb = Image.new("RGB", sprite.size, (0, 0, 0))
    rgb.paste(sprite, mask=alpha)
    sprite = rgb.quantize(
        colors=256,
        method=Image.Quantize.FASTOCTREE,
        dither=Image.Dither.NONE,
    ).convert("RGBA")
    sprite.putalpha(alpha)
    return sprite


def hsv(color: tuple[int, int, int, int]) -> tuple[float, float, float]:
    red, green, blue, _alpha = color
    return colorsys.rgb_to_hsv(red / 255, green / 255, blue / 255)


def split_editable_layers(sprite: Image.Image) -> dict[str, Image.Image]:
    layers = {
        name: Image.new("RGBA", sprite.size, (0, 0, 0, 0))
        for name in (
            "empty_bar",
            "health_fill",
            "frame",
            "decorations",
            "highlights",
        )
    }
    layer_pixels = {name: image.load() for name, image in layers.items()}
    pixels = sprite.load()

    for y in range(TEXTURE_HEIGHT):
        for x in range(TEXTURE_WIDTH):
            color = pixels[x, y]
            if color[3] == 0:
                continue

            hue, saturation, value = hsv(color)
            in_interior = (
                INTERIOR_X <= x < INTERIOR_X + INTERIOR_WIDTH
                and INTERIOR_Y <= y < INTERIOR_Y + INTERIOR_HEIGHT
            )
            is_highlight = value > 0.72 and (
                saturation > 0.12
                or y in range(5, 11)
                or y in range(25, 30)
            )
            is_decoration = (
                not in_interior
                and (y <= 7 or y >= 29)
                and saturation > 0.18
                and 0.03 <= hue <= 0.48
            )

            if is_highlight:
                layer = "highlights"
            elif in_interior:
                layer = "health_fill" if x < 130 else "empty_bar"
            elif is_decoration:
                layer = "decorations"
            else:
                layer = "frame"
            layer_pixels[layer][x, y] = color

    return layers


def build_runtime_textures(sprite: Image.Image) -> dict[str, Image.Image]:
    pixels = sprite.load()

    frame = sprite.copy()
    frame_pixels = frame.load()
    for y in range(INTERIOR_Y, INTERIOR_Y + INTERIOR_HEIGHT):
        for x in range(INTERIOR_X, INTERIOR_X + INTERIOR_WIDTH):
            frame_pixels[x, y] = (0, 0, 0, 0)

    empty = Image.new("RGBA", sprite.size, (0, 0, 0, 0))
    empty_pixels = empty.load()
    for y in range(INTERIOR_Y, INTERIOR_Y + INTERIOR_HEIGHT):
        for x in range(INTERIOR_X, INTERIOR_X + INTERIOR_WIDTH):
            sample_x = 134 + (x - INTERIOR_X) % 90
            sampled = pixels[sample_x, y]
            empty_pixels[x, y] = sampled if sampled[3] else (13, 34, 29, 255)

    progress = Image.new("RGBA", sprite.size, (0, 0, 0, 0))
    progress_pixels = progress.load()
    for y in range(INTERIOR_Y, INTERIOR_Y + INTERIOR_HEIGHT):
        for x in range(INTERIOR_X, INTERIOR_X + INTERIOR_WIDTH):
            sample_x = 34 + (x - INTERIOR_X) % 85
            sampled = pixels[sample_x, y]
            progress_pixels[x, y] = sampled if sampled[3] else (5, 116, 56, 255)

    edge = sprite.crop(
        (
            EDGE_SOURCE_X,
            INTERIOR_Y,
            EDGE_SOURCE_X + EDGE_WIDTH,
            INTERIOR_Y + INTERIOR_HEIGHT,
        )
    )

    return {
        "boss_bar_moss_cave_frame.png": frame,
        "boss_bar_moss_cave_empty.png": empty,
        "boss_bar_moss_cave_progress.png": progress,
        "boss_bar_moss_cave_edge.png": edge,
    }


def main() -> None:
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    args.layers_dir.mkdir(parents=True, exist_ok=True)
    args.preview.parent.mkdir(parents=True, exist_ok=True)

    sprite = extract_reference(args.reference)
    sprite.save(args.output_dir / "boss_bar_moss_cave.png")
    sprite.resize(
        (TEXTURE_WIDTH * 4, TEXTURE_HEIGHT * 4), Image.Resampling.NEAREST
    ).save(args.preview)

    for name, image in split_editable_layers(sprite).items():
        image.save(args.layers_dir / f"{name}.png")

    for name, image in build_runtime_textures(sprite).items():
        image.save(args.output_dir / name)

    alpha_values = sorted(set(sprite.getchannel("A").get_flattened_data()))
    color_count = len(set(sprite.get_flattened_data()))
    print(
        f"Generated {TEXTURE_WIDTH}x{TEXTURE_HEIGHT} RGBA boss bar; "
        f"alpha={alpha_values}; colors={color_count}"
    )


if __name__ == "__main__":
    main()

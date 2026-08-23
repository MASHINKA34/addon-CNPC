import argparse
import struct
import zlib
from pathlib import Path

from PIL import Image


WIDTH = 1329
HEIGHT = 261
LANE_X = 104
LANE_Y = 108
LANE_WIDTH = 1121
LANE_HEIGHT = 105
NAME_X0 = 360
NAME_X1 = 970
NAME_Y0 = 147
NAME_Y1 = 174


def blank():
    return Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))


def build_layers(frame_source, fill_source):
    background = blank()
    background.alpha_composite(
        frame_source.crop((LANE_X, LANE_Y, LANE_X + LANE_WIDTH, LANE_Y + LANE_HEIGHT)),
        (LANE_X, LANE_Y),
    )

    fill = blank()
    fill.alpha_composite(fill_source, (LANE_X, LANE_Y))

    frame = frame_source.copy()
    frame_pixels = frame.load()
    for y in range(LANE_Y, LANE_Y + LANE_HEIGHT):
        for x in range(LANE_X, LANE_X + LANE_WIDTH):
            frame_pixels[x, y] = (0, 0, 0, 0)

    background_pixels = background.load()
    fill_pixels = fill.load()
    for y in range(NAME_Y0, NAME_Y1):
        for x in range(NAME_X0, NAME_X1):
            background_pixels[x, y] = (12, 18, 30, 255)
            fill_pixels[x, y] = (7, 102, 112, 255)

    return [("background", background), ("fill", fill), ("frame", frame)]


def png_save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def ase_string(value):
    encoded = value.encode("utf-8")
    return struct.pack("<H", len(encoded)) + encoded


def ase_chunk(chunk_type, data):
    return struct.pack("<IH", 6 + len(data), chunk_type) + data


def ase_layer(name):
    data = struct.pack(
        "<HHHHHHB3s",
        3,
        0,
        0,
        WIDTH,
        HEIGHT,
        0,
        255,
        b"\x00" * 3,
    ) + ase_string(name)
    return ase_chunk(0x2004, data)


def ase_cel(index, image):
    compressed = zlib.compress(image.tobytes(), level=9)
    data = struct.pack(
        "<HhhBHh5sHH",
        index,
        0,
        0,
        255,
        2,
        0,
        b"\x00" * 5,
        WIDTH,
        HEIGHT,
    ) + compressed
    return ase_chunk(0x2005, data)


def write_aseprite(path, layers):
    chunks = [ase_layer(name) for name, _ in layers]
    chunks.extend(ase_cel(index, image) for index, (_, image) in enumerate(layers))
    frame_size = 16 + sum(len(chunk) for chunk in chunks)
    file_size = 128 + frame_size
    header = struct.pack(
        "<IHHHHHIHII",
        file_size,
        0xA5E0,
        1,
        WIDTH,
        HEIGHT,
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
        raise AssertionError("aseprite size")


def composite(layers, progress):
    result = blank()
    result.alpha_composite(layers[0][1])
    width = round(WIDTH * progress)
    if width > 0:
        result.alpha_composite(layers[1][1].crop((0, 0, width, HEIGHT)), (0, 0))
    result.alpha_composite(layers[2][1])
    return result


def validate(layers):
    if [name for name, _ in layers] != ["background", "fill", "frame"]:
        raise AssertionError("layers")
    for name, image in layers:
        if image.size != (WIDTH, HEIGHT) or image.mode != "RGBA":
            raise AssertionError(name)
        if not set(image.getchannel("A").get_flattened_data()) <= {0, 255}:
            raise AssertionError(name)
    background_alpha = layers[0][1].getchannel("A").tobytes()
    fill_alpha = layers[1][1].getchannel("A").tobytes()
    if background_alpha != fill_alpha:
        raise AssertionError("coordinate masks")
    frame = layers[2][1]
    for y in range(NAME_Y0, NAME_Y1):
        for x in range(NAME_X0, NAME_X1):
            if frame.getpixel((x, y))[3] != 0:
                raise AssertionError("name area")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--frame-source", type=Path, required=True)
    parser.add_argument("--fill-source", type=Path, required=True)
    parser.add_argument("--out-dir", type=Path, required=True)
    parser.add_argument("--aseprite", type=Path, required=True)
    parser.add_argument("--preview", type=Path)
    args = parser.parse_args()

    outputs = [
        args.out_dir / "background.png",
        args.out_dir / "fill.png",
        args.out_dir / "frame.png",
        args.aseprite,
    ]
    occupied = [path for path in outputs if path.exists()]
    if occupied:
        raise FileExistsError(", ".join(str(path) for path in occupied))

    frame_source = Image.open(args.frame_source).convert("RGBA")
    fill_source = Image.open(args.fill_source).convert("RGBA")
    if frame_source.size != (WIDTH, HEIGHT):
        raise ValueError(frame_source.size)
    if fill_source.size != (LANE_WIDTH, LANE_HEIGHT):
        raise ValueError(fill_source.size)

    layers = build_layers(frame_source, fill_source)
    validate(layers)
    for name, image in layers:
        png_save(image, args.out_dir / f"{name}.png")
    write_aseprite(args.aseprite, layers)

    if args.preview:
        preview = Image.new("RGBA", (WIDTH, HEIGHT * 3 + 16), (35, 39, 50, 255))
        for index, progress in enumerate((0.0, 0.5, 1.0)):
            preview.alpha_composite(composite(layers, progress), (0, index * (HEIGHT + 8)))
        png_save(preview, args.preview)

    for path in outputs:
        print(path)


if __name__ == "__main__":
    main()

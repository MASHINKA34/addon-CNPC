from pathlib import Path

from PIL import Image, ImageDraw


WIDTH = 182
HEIGHT = 16
ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "src" / "main" / "resources" / "assets" / "cnpcgeckoaddon" / "textures" / "gui" / "boss_bar" / "infernal"

TRANSPARENT = (0, 0, 0, 0)
OUTLINE = (9, 7, 10, 255)
COAL = (20, 18, 22, 255)
COAL_MID = (28, 26, 30, 255)
COAL_LIGHT = (39, 37, 42, 255)
OBSIDIAN = (27, 24, 31, 255)
BASALT = (44, 41, 48, 255)
BASALT_LIGHT = (65, 61, 69, 255)
IRON = (59, 53, 56, 255)
IRON_LIGHT = (91, 80, 76, 255)
RUST = (105, 43, 23, 255)
DEEP_RED = (91, 13, 7, 255)
CRIMSON = (137, 25, 8, 255)
TEXT_RED = (158, 31, 9, 255)
RED = (190, 38, 7, 255)
ORANGE = (231, 68, 6, 255)
BRIGHT_ORANGE = (255, 111, 7, 255)
YELLOW = (255, 188, 29, 255)


def layer():
    return Image.new("RGBA", (WIDTH, HEIGHT), TRANSPARENT)


def mirror(points):
    return [(WIDTH - 1 - x, y) for x, y in points]


def build_background():
    image = layer()
    draw = ImageDraw.Draw(image)
    draw.rectangle((14, 4, 167, 12), fill=COAL)
    draw.line((14, 4, 167, 4), fill=COAL_LIGHT)
    draw.line((14, 12, 167, 12), fill=OBSIDIAN)
    draw.rectangle((48, 4, 133, 12), fill=COAL)
    for point in [(19, 7), (24, 10), (31, 6), (39, 9), (143, 7), (151, 10), (159, 6), (164, 9)]:
        draw.point(point, fill=COAL_MID)
    return image


def build_fill():
    image = layer()
    draw = ImageDraw.Draw(image)
    draw.rectangle((14, 4, 31, 12), fill=DEEP_RED)
    draw.rectangle((32, 4, 47, 12), fill=CRIMSON)
    draw.rectangle((48, 4, 133, 12), fill=TEXT_RED)
    draw.rectangle((134, 4, 151, 12), fill=ORANGE)
    draw.rectangle((152, 4, 167, 12), fill=BRIGHT_ORANGE)
    draw.line((14, 4, 47, 4), fill=DEEP_RED)
    draw.line((14, 12, 47, 12), fill=DEEP_RED)
    draw.line((134, 4, 151, 4), fill=RED)
    draw.line((134, 12, 158, 12), fill=RED)
    for point in [(18, 6), (21, 10), (25, 8), (29, 6), (34, 9), (38, 7), (42, 10), (46, 6)]:
        draw.point(point, fill=CRIMSON)
    for point in [(136, 7), (139, 10), (143, 6), (147, 9), (151, 7), (154, 10), (158, 6), (162, 9), (165, 7)]:
        draw.point(point, fill=BRIGHT_ORANGE)
    for point in [(154, 6), (159, 9), (163, 7), (166, 10)]:
        draw.point(point, fill=YELLOW)
    return image


def build_frame():
    image = layer()
    draw = ImageDraw.Draw(image)

    draw.rectangle((10, 0, 171, 3), fill=OUTLINE)
    draw.rectangle((11, 1, 170, 2), fill=BASALT)
    draw.line((13, 1, 168, 1), fill=BASALT_LIGHT)
    draw.line((14, 2, 167, 2), fill=OBSIDIAN)
    draw.line((13, 3, 168, 3), fill=OUTLINE)

    draw.rectangle((9, 13, 172, 15), fill=OUTLINE)
    draw.line((10, 14, 171, 14), fill=IRON)
    draw.line((13, 14, 168, 14), fill=BASALT_LIGHT)

    left_cap = [(5, 3), (13, 3), (14, 4), (14, 12), (13, 13), (5, 13), (2, 10), (2, 6)]
    left_fill = [(6, 4), (12, 4), (13, 5), (13, 11), (12, 12), (6, 12), (4, 10), (4, 6)]
    draw.polygon(left_cap, fill=OUTLINE)
    draw.polygon(left_fill, fill=BASALT)
    draw.line((6, 5, 11, 5), fill=BASALT_LIGHT)
    draw.line((5, 11, 11, 11), fill=OBSIDIAN)
    draw.line((13, 4, 13, 12), fill=OUTLINE)
    draw.polygon(mirror(left_cap), fill=OUTLINE)
    draw.polygon(mirror(left_fill), fill=BASALT)
    draw.line((WIDTH - 1 - 6, 5, WIDTH - 1 - 11, 5), fill=BASALT_LIGHT)
    draw.line((WIDTH - 1 - 5, 11, WIDTH - 1 - 11, 11), fill=OBSIDIAN)
    draw.line((WIDTH - 1 - 13, 4, WIDTH - 1 - 13, 12), fill=OUTLINE)

    horn = [(0, 0), (2, 0), (2, 2), (3, 2), (3, 4), (5, 4), (5, 5), (8, 5), (10, 7), (8, 8), (5, 7), (3, 6), (1, 4), (0, 2)]
    draw.polygon(horn, fill=OUTLINE)
    draw.line((1, 1, 1, 2), fill=BASALT_LIGHT)
    draw.line((2, 3, 4, 5), fill=BASALT)
    draw.polygon(mirror(horn), fill=OUTLINE)
    draw.line((WIDTH - 2, 1, WIDTH - 2, 2), fill=BASALT_LIGHT)
    draw.line((WIDTH - 3, 3, WIDTH - 5, 5), fill=BASALT)

    spike = [(1, 8), (4, 7), (5, 8), (4, 10), (2, 11)]
    draw.polygon(spike, fill=OUTLINE)
    draw.point((3, 8), fill=IRON_LIGHT)
    draw.polygon(mirror(spike), fill=OUTLINE)
    draw.point((WIDTH - 1 - 3, 8), fill=IRON_LIGHT)

    chain = [(4, 12), (3, 13), (4, 14), (5, 14), (6, 13), (7, 14), (8, 15), (10, 15), (11, 14), (10, 13), (9, 13)]
    for point in chain:
        draw.point(point, fill=OUTLINE)
        draw.point(mirror([point])[0], fill=OUTLINE)
    for point in [(4, 13), (5, 13), (8, 14), (9, 14)]:
        draw.point(point, fill=IRON_LIGHT)
        draw.point(mirror([point])[0], fill=IRON_LIGHT)

    for point, color in [
        ((24, 1), RUST), ((25, 2), ORANGE), ((34, 14), RED), ((35, 13), BRIGHT_ORANGE),
        ((146, 1), RED), ((147, 2), BRIGHT_ORANGE), ((156, 14), ORANGE), ((157, 13), RUST),
        ((8, 7), RED), ((9, 8), BRIGHT_ORANGE), ((8, 9), ORANGE),
    ]:
        draw.point(point, fill=color)
        if point[0] < 14:
            draw.point(mirror([point])[0], fill=color)

    for x in [18, 42, 139, 163]:
        draw.point((x, 1), fill=IRON_LIGHT)
    return image


def validate(background, fill, frame):
    for image in (background, fill, frame):
        assert image.size == (WIDTH, HEIGHT)
        assert image.mode == "RGBA"
        assert {pixel[3] for pixel in image.get_flattened_data()} <= {0, 255}
    assert background.getbbox() == (14, 4, 168, 13)
    assert fill.getbbox() == (14, 4, 168, 13)
    assert all(frame.getpixel((x, y))[3] == 0 for x in range(48, 134) for y in range(4, 13))
    assert len({fill.getpixel((x, y)) for x in range(48, 134) for y in range(4, 13)}) == 1


def main():
    background = build_background()
    fill = build_fill()
    frame = build_frame()
    validate(background, fill, frame)
    TARGET.mkdir(parents=True, exist_ok=True)
    background.save(TARGET / "background.png", "PNG", optimize=False)
    fill.save(TARGET / "fill.png", "PNG", optimize=False)
    frame.save(TARGET / "frame.png", "PNG", optimize=False)
    print(TARGET)


if __name__ == "__main__":
    main()

"""Build the GUI's pixel art, editable Aseprite sources and contract previews.

Pillow is the only dependency. All drawing uses integer pixels and flat colors.
Run with --check to validate the files on disk without rewriting them.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import zlib

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art/aseprite/gui_theme"
OUT = ROOT / "src/main/resources/assets/cnpcgeckoaddon/textures/gui/theme"
PREVIEW = ART / "preview"
SIZES = {"panel": (64, 64), "button": (200, 60), "toggle": (48, 20),
         "field": (200, 20), "scroll": (16, 48), "header": (256, 16), "icons": (256, 256)}
PALETTE = {
    "ink": "0a0a0a", "coal": "171717", "well": "161920", "stone": "1e222b",
    "fleck": "252a35", "inset": "30303f", "slate": "414052", "rim": "5d526d",
    "amethyst_dark": "35224e", "amethyst": "4e316b", "violet": "684189",
    "crystal": "af78cc", "lavender": "bca6ef", "silver": "d8c7ff",
    "teal": "64b69c", "bronze": "a37e67",
}
C = {name: (*bytes.fromhex(value), 255) for name, value in PALETTE.items()}
EMPTY = (0, 0, 0, 0)
ABILITIES = "area ranged melee fluid hook capture summon leap line blast geyser boulder boulder_rain tether gravity mark cover hazard hunt beam cocoon dash cone platform hurricane shadow seismic rift vent".split()
SECTIONS = "phases totems chest aggro_zone boss_bar health_link tuning teleport_paths effects points_zones dialogue immunities warnings sequence finish carrying".split()
NAMES = dict(enumerate(ABILITIES)) | dict(enumerate(SECTIONS, 48))
RUSSIAN = "Удар по площади|Дальний выстрел|Ближний удар|Жидкость|Крюк|Захват|Призыв|Прыжок|Линия|Взрыв|Гейзер|Валун|Камнепад|Привязь|Гравитация|Метка|Укрытие|Опасная арена|Охота|Луч|Кокон|Рывок|Конус|Платформа|Ураган|Тень|Сейсмика|Разлом|Жерло|Фазы|Тотемы|Сундук|Зона агра|Бар босса|Связка здоровья|Тонкая настройка|Пути телепорта|Эффекты|Точки и зоны|Реплики|Иммунитеты|Предупреждения|Очерёдность|Доделать|Переноска".split("|")
FONT = ImageFont.load_default_imagefont()


def canvas(size):
    return Image.new("RGBA", size, EMPTY)


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def pixels(image):
    if hasattr(image, "get_flattened_data"):
        return image.get_flattened_data()
    return image.getdata()


def text(image, xy, value, color=(255, 255, 255, 255), shadow=True):
    d = ImageDraw.Draw(image)
    if shadow:
        d.text((xy[0] + 1, xy[1] + 1), value, fill=C["ink"], font=FONT)
    d.text(xy, value, fill=color, font=FONT)


def panel():
    im = canvas(SIZES["panel"])
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 63, 63), fill=C["ink"])
    for n, color in enumerate(("slate", "rim", "amethyst_dark", "amethyst", "coal", "inset", "well"), 1):
        d.rectangle((n, n, 63 - n, 63 - n), outline=C[color])
    d.rectangle((8, 8, 55, 55), fill=C["stone"])
    # A sparse 48x48 tile with a quiet one-pixel perimeter; opposite edges match.
    for x, y in ((13, 17), (37, 29), (23, 43), (46, 48)):
        d.line((x, y, x + 2, y), fill=C["fleck"])
        d.point((x, y + 1), fill=C["well"])
    # The corner inlays stay entirely within each 8px cap.
    for ox, oy in ((0, 0), (56, 0), (0, 56), (56, 56)):
        d.polygon([(ox + 3, oy + 2), (ox + 5, oy + 4), (ox + 3, oy + 6), (ox + 1, oy + 4)], fill=C["violet"])
        d.line((ox + 2, oy + 4, ox + 3, oy + 3), fill=C["crystal"])
        d.point((ox + 3, oy + 2), fill=C["silver"])
        d.point((ox + 4, oy + 4), fill=C["lavender"])
    for x, y in ((0, 0), (63, 0), (0, 63), (63, 63)):
        im.putpixel((x, y), EMPTY)
    return [("stone_and_seamless_fill", im)]


def button():
    im = canvas(SIZES["button"])
    d = ImageDraw.Draw(im)
    for state, face, edge, shine in ((0, "inset", "amethyst", "rim"),
                                     (1, "amethyst_dark", "violet", "crystal"),
                                     (2, "stone", "fleck", "slate")):
        y = state * 20
        d.rectangle((0, y, 199, y + 19), fill=C["ink"])
        d.rectangle((1, y + 1, 198, y + 18), fill=C[edge])
        d.rectangle((2, y + 2, 197, y + 17), fill=C[face])
        d.line((2, y + 1, 197, y + 1), fill=C[shine])
        d.line((2, y + 18, 197, y + 18), fill=C["coal"])
        d.line((1, y + 2, 1, y + 16), fill=C[shine])
        d.line((198, y + 2, 198, y + 17), fill=C["well"])
        if state < 2:
            for x, col in ((2, "lavender"), (3, "violet"), (196, "amethyst_dark"), (197, "slate")):
                d.point((x, y + 2), fill=C[col])
        for x in (0, 199):
            im.putpixel((x, y), EMPTY)
            im.putpixel((x, y + 19), EMPTY)
    return [("normal_hover_disabled", im)]


def field():
    im = canvas(SIZES["field"])
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 199, 19), fill=C["ink"])
    d.rectangle((1, 1, 198, 18), outline=C["slate"])
    d.rectangle((2, 2, 197, 17), fill=C["well"])
    d.line((2, 18, 197, 18), fill=C["rim"])
    # Faceted endcaps keep all decoration outside the text's inset of 3px.
    for y, col in enumerate(("coal", "stone", "fleck", "inset", "amethyst_dark", "amethyst", "violet", "crystal", "lavender"), 4):
        d.point((1, y), fill=C[col])
        d.point((198, 19 - y), fill=C[col])
    return [("inset_frame_and_quiet_text_well", im)]


def toggle():
    im = canvas(SIZES["toggle"])
    d = ImageDraw.Draw(im)
    for ox, on in ((0, True), (24, False)):
        d.rectangle((ox + 3, 4, ox + 20, 15), fill=C["ink"])
        d.rectangle((ox + 4, 5, ox + 19, 14), fill=C["amethyst"] if on else C["well"])
        d.line((ox + 4, 4, ox + 19, 4), fill=C["rim"])
        start = ox + (12 if on else 4)
        d.rectangle((start, 5, start + 7, 14), fill=C["violet"] if on else C["slate"])
        d.line((start, 5, start + 6, 5), fill=C["lavender"] if on else C["rim"])
        if on:
            d.line([(ox + 6, 9), (ox + 8, 11), (ox + 11, 7)], fill=C["teal"], width=1)
        else:
            d.line((ox + 14, 8, ox + 17, 11), fill=C["bronze"])
            d.line((ox + 14, 11, ox + 17, 8), fill=C["bronze"])
    return [("yes_left_no_right", im)]


def scroll():
    im = canvas(SIZES["scroll"])
    d = ImageDraw.Draw(im)
    d.rectangle((4, 0, 11, 15), fill=C["ink"])
    d.rectangle((5, 0, 10, 15), fill=C["well"])
    d.line((5, 0, 5, 15), fill=C["inset"])
    d.rectangle((2, 16, 13, 31), fill=C["ink"])
    d.rectangle((3, 17, 12, 30), fill=C["amethyst"])
    d.line((3, 17, 11, 17), fill=C["crystal"])
    d.rectangle((4, 18, 11, 29), fill=C["slate"])
    # Long thumb middles tile this 12px section, including the engraved grip.
    for y in (21, 24, 27):
        d.line((5, y, 10, y), fill=C["rim"])
        d.line((5, y + 1, 10, y + 1), fill=C["inset"])
    d.polygon([(3, 37), (12, 37), (8, 43), (7, 43)], fill=C["ink"])
    d.polygon([(4, 38), (11, 38), (8, 42), (7, 42)], fill=C["crystal"])
    d.line((5, 38, 10, 38), fill=C["lavender"])
    return [("track_thumb_down_arrow", im)]


def header():
    im = canvas(SIZES["header"])
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 255, 15), fill=C["amethyst_dark"])
    d.line((0, 0, 255, 0), fill=C["ink"])
    d.line((0, 1, 255, 1), fill=C["violet"])
    d.line((0, 14, 255, 14), fill=C["amethyst"])
    d.line((0, 15, 255, 15), fill=C["ink"])
    for ox in (0, 248):
        d.line((ox, 1, ox, 14), fill=C["ink"])
        d.polygon([(ox + 4, 4), (ox + 6, 7), (ox + 4, 11), (ox + 2, 7)], fill=C["violet"])
        d.line((ox + 3, 7, ox + 4, 5), fill=C["crystal"])
        d.point((ox + 4, 4), fill=C["lavender"])
    return [("title_strip_8px_caps", im)]


def ability_colors():
    source = ROOT / "src/main/java/com/goodbird/cnpcgeckoaddon/ai/BossTelegraphUtil.java"
    body = source.read_text(encoding="utf-8").split("int[] ABILITY_COLORS = {", 1)[1].split("};", 1)[0]
    colors = re.findall(r"0x([0-9A-Fa-f]{6})", body)
    if len(colors) != len(ABILITIES):
        raise ValueError("ABILITY_COLORS changed; review the shared atlas contract")
    return [(*bytes.fromhex(value), 255) for value in colors]


def icon(index, accent):
    """Hand-placed silhouettes. S/H/A are the two theme tones and one accent."""
    im = canvas((16, 16))
    d = ImageDraw.Draw(im)
    s, h, a = C["rim"], C["silver"], accent
    def rect(box, c=s): d.rectangle(box, fill=c)
    def line(points, c=h, w=1): d.line(points, fill=c, width=w)
    def poly(points, c=s): d.polygon(points, fill=c)
    def ring(box, c=h, w=1): d.ellipse(box, outline=c, width=w)
    if index == 0:  # Ground ring with impact diamond.
        ring((2, 7, 13, 13), s, 2); poly([(7, 2), (10, 6), (7, 9), (5, 6)], a); line((7, 3, 7, 6))
    elif index == 1:
        line((3, 12, 11, 4), s, 3); line((3, 12, 11, 4)); poly([(8, 3), (13, 2), (12, 7)], a); line((2, 6, 5, 6), s)
    elif index == 2:
        poly([(10, 2), (13, 2), (13, 5), (7, 11), (5, 9)], h); line((11, 3, 6, 8), a)
        line((3, 7, 9, 13), s, 2); line((5, 10, 2, 13), a, 2)
    elif index == 3:
        poly([(8, 2), (4, 7), (3, 10), (5, 13), (10, 13), (12, 10), (11, 7)], s)
        poly([(8, 4), (5, 8), (5, 11), (9, 11), (10, 9)], a); line((6, 7, 6, 9))
    elif index == 4:
        line([(7, 2), (7, 9), (9, 12), (12, 12), (13, 9), (11, 7)], s, 2)
        line([(7, 3), (7, 9), (9, 11), (11, 11), (12, 9)], a); line((5, 2, 9, 2))
    elif index == 5:
        line([(2, 5), (2, 3), (5, 3)], h); line([(10, 3), (13, 3), (13, 5)], h)
        line([(2, 10), (2, 12), (5, 12)], h); line([(10, 12), (13, 12), (13, 10)], h)
        rect((6, 5, 9, 7), a); poly([(5, 9), (10, 9), (11, 12), (4, 12)], s)
    elif index == 6:
        rect((5, 5, 10, 10), s); rect((4, 11, 11, 13), s); rect((6, 6, 6, 7), h); rect((9, 6, 9, 7), h)
        line((7, 2, 7, 4), a); line((5, 3, 9, 3), a); line((2, 7, 2, 10), a); line((13, 7, 13, 10), a)
    elif index == 7:
        poly([(5, 5), (8, 2), (11, 5), (9, 5), (9, 10), (7, 10), (7, 5)], a)
        line([(2, 12), (4, 13), (11, 13), (13, 12)], s); line((4, 11, 5, 10)); line((10, 10, 11, 11))
    elif index == 8:
        rect((2, 6, 13, 10), s); line((2, 8, 13, 8), a, 2)
        for x in (3, 7, 11): line((x, 4, x + 1, 5)); line((x, 11, x + 1, 12))
    elif index == 9:
        poly([(7, 2), (9, 6), (13, 4), (11, 8), (13, 11), (9, 10), (7, 13), (6, 10), (2, 11), (4, 7), (3, 3), (6, 5)], a)
        poly([(7, 5), (10, 8), (7, 10), (5, 8)], s); rect((7, 7, 8, 8), h)
    elif index == 10:
        line((2, 13, 13, 13), s, 2); poly([(5, 11), (5, 6), (3, 5), (4, 3), (7, 6), (7, 2), (9, 2), (9, 6), (12, 3), (13, 5), (10, 8), (10, 11)], a)
        line((7, 7, 7, 11)); line((11, 10, 12, 11))
    elif index == 11:
        poly([(5, 3), (10, 2), (13, 6), (12, 11), (8, 13), (3, 11), (2, 7)], s)
        poly([(5, 4), (9, 3), (11, 6), (8, 8), (3, 7)], a); line([(4, 6), (5, 4), (8, 4)])
    elif index == 12:
        for x, y in ((3, 6), (9, 3), (10, 11)):
            poly([(x - 1, y), (x + 1, y - 1), (x + 3, y + 1), (x + 1, y + 3), (x - 1, y + 2)], s)
            rect((x, y, x + 1, y + 1), a); line((x, max(2, y - 3), x + 1, max(2, y - 4)))
    elif index == 13:
        ring((2, 3, 8, 9), s, 2); ring((7, 7, 13, 13), s, 2); line((5, 6, 10, 10), a, 2)
        line((3, 4, 5, 4)); line((10, 12, 12, 10))
    elif index == 14:
        poly([(7, 5), (10, 8), (7, 11), (5, 8)], a)
        line([(3, 2), (3, 5), (2, 4)], h); line((3, 5, 4, 4)); line([(12, 13), (12, 10), (11, 11)], h); line((12, 10, 13, 11))
        line((2, 10, 4, 10), s); line((11, 5, 13, 5), s)
    elif index == 15:
        ring((3, 3, 12, 12), s, 2); line((7, 2, 7, 13), a); line((2, 7, 13, 7), a); rect((6, 6, 8, 8), h)
    elif index == 16:
        poly([(2, 7), (7, 2), (13, 7)], a); rect((3, 7, 4, 13), s); rect((11, 7, 12, 13), s)
        rect((7, 8, 8, 9), h); rect((6, 11, 9, 13), s); line((4, 6, 7, 3))
    elif index == 17:
        line([(2, 6), (2, 2), (13, 2), (13, 6)], s, 2); line([(2, 9), (2, 13), (13, 13), (13, 9)], s, 2)
        poly([(4, 10), (5, 6), (7, 8), (9, 4), (11, 10), (9, 12), (6, 12)], a); line((8, 8, 8, 10))
    elif index == 18:
        for box in ((2, 4, 4, 6), (6, 2, 8, 4), (10, 4, 12, 6)): d.ellipse(box, fill=h)
        poly([(6, 7), (9, 7), (12, 11), (10, 13), (7, 11), (4, 13), (3, 10)], s); rect((6, 8, 9, 10), a)
    elif index == 19:
        poly([(2, 5), (5, 3), (7, 7), (5, 11), (2, 9)], s); line((5, 7, 13, 7), a, 2)
        line((8, 4, 12, 3)); line((8, 11, 12, 12)); rect((4, 6, 5, 8), h)
    elif index == 20:
        poly([(7, 2), (10, 4), (12, 8), (10, 12), (7, 13), (4, 11), (3, 7), (5, 3)], s)
        line((5, 4, 10, 7), h); line((4, 7, 10, 10), a); line((5, 10, 8, 12), h)
    elif index == 21:
        poly([(9, 3), (13, 7), (9, 12), (9, 9), (5, 9), (5, 6), (9, 6)], a)
        line((2, 4, 6, 4), h); line((2, 7, 3, 7), s); line((2, 11, 6, 11), h)
    elif index == 22:
        poly([(2, 8), (11, 2), (13, 5), (13, 10), (11, 13)], s)
        line((3, 8, 11, 3), h); line((3, 8, 11, 12), h); poly([(4, 8), (11, 5), (12, 8), (11, 10)], a)
    elif index == 23:
        poly([(2, 10), (7, 7), (13, 10), (8, 13)], s); line([(2, 10), (7, 12), (13, 10)], h)
        rect((7, 2, 8, 4), a); poly([(5, 4), (10, 4), (8, 7)], a)
    elif index == 24:
        line([(3, 3), (11, 3), (13, 5), (5, 5), (3, 7), (11, 7), (9, 9), (5, 9), (7, 11), (6, 13)], s, 2)
        line((4, 3, 10, 3), h); line((5, 7, 10, 7), a); line((6, 10, 7, 11), a)
    elif index == 25:
        rect((9, 2, 12, 5), a); poly([(9, 6), (12, 6), (13, 11), (8, 11)], a)
        rect((4, 4, 7, 7), s); poly([(4, 8), (7, 8), (9, 13), (2, 13)], s); rect((5, 5, 5, 5), h); rect((7, 5, 7, 5), h)
    elif index == 26:
        line([(2, 5), (5, 4), (7, 5), (10, 4), (13, 5)], s); line([(2, 11), (5, 10), (8, 12), (13, 10)], h)
        line([(8, 2), (6, 6), (9, 7), (6, 13)], a, 2)
    elif index == 27:
        poly([(8, 2), (12, 4), (11, 11), (7, 13), (3, 10), (4, 5)], s)
        poly([(8, 3), (10, 5), (8, 7), (9, 10), (6, 12), (7, 8), (5, 7)], a)
        line([(7, 3), (5, 6), (6, 7)], h); line((10, 9, 9, 11), h)
    elif index == 28:
        rect((2, 8, 13, 13), s)
        for x in (4, 7, 10): line((x, 9, x, 12), h)
        line([(4, 6), (3, 4), (4, 2)], a); line([(8, 6), (7, 4), (8, 2)], a); line([(12, 6), (11, 4), (12, 2)], a)
    elif index == 48:
        for x, y in ((2, 9), (6, 6), (10, 3)):
            rect((x, y, x + 3, 13), s); line((x, y, x + 3, y), a)
        line((2, 5, 8, 2), h)
    elif index == 49:
        rect((6, 2, 9, 13), s); rect((3, 5, 12, 8), s); rect((7, 3, 8, 4), a)
        rect((4, 6, 5, 6), h); rect((10, 6, 11, 6), h); line((6, 12, 9, 12), a)
    elif index == 50:
        rect((2, 5, 13, 13), s); rect((3, 3, 12, 7), a); line((3, 4, 11, 4), h)
        line((2, 8, 13, 8), h); rect((7, 7, 8, 10), a)
    elif index == 51:
        ring((2, 2, 13, 13), s, 2); line((7, 3, 7, 7), a); line((7, 7, 11, 5), a)
        rect((4, 9, 5, 10), h); rect((10, 9, 11, 10), h)
    elif index == 52:
        rect((2, 7, 13, 12), s); rect((3, 8, 10, 10), a); line((3, 8, 9, 8), h)
        poly([(4, 2), (6, 4), (8, 2), (10, 4), (12, 2), (11, 5), (5, 5)], h)
    elif index == 53:
        for x in (2, 9): poly([(x, 5), (x + 1, 4), (x + 2, 5), (x + 3, 4), (x + 4, 5), (x + 4, 7), (x + 2, 10), (x, 7)], a)
        line((5, 11, 10, 11), s, 2); rect((7, 10, 8, 11), h)
    elif index == 54:
        for x, y in ((3, 5), (7, 10), (12, 6)):
            line((x, 2, x, 13), s); rect((x - 1, y - 1, x + 1, y + 1), a); rect((x - 1, y - 1, x, y - 1), h)
    elif index == 55:
        rect((2, 9, 5, 12), a); rect((10, 3, 13, 6), a)
        line([(4, 8), (4, 4), (8, 4)], h); line([(8, 3), (9, 4), (8, 5)], h)
        line([(11, 8), (11, 11), (7, 11)], s)
    elif index == 56:
        rect((6, 2, 9, 3), h); rect((7, 4, 8, 5), s)
        poly([(6, 6), (9, 6), (12, 10), (11, 13), (4, 13), (3, 10)], s)
        poly([(5, 9), (10, 9), (11, 11), (10, 12), (5, 12), (4, 11)], a); rect((5, 7, 6, 8), h)
    elif index == 57:
        poly([(3, 3), (6, 4), (10, 2), (13, 3), (13, 12), (10, 11), (6, 13), (3, 12)], s)
        line((6, 5, 6, 11), h); line((10, 3, 10, 10), h); poly([(8, 5), (11, 6), (9, 10), (7, 7)], a)
    elif index == 58:
        rect((2, 3, 13, 10), s); poly([(4, 10), (7, 10), (4, 13)], s)
        line((4, 5, 11, 5), h); line((4, 8, 8, 8), a)
    elif index == 59:
        poly([(2, 3), (7, 2), (13, 3), (12, 9), (7, 13), (3, 9)], s)
        line([(4, 7), (6, 9), (10, 5)], a, 2); line((4, 4, 7, 3), h)
    elif index == 60:
        poly([(7, 2), (13, 12), (2, 12)], a); line((7, 5, 7, 8), h, 2); rect((7, 10, 8, 10), h); line((3, 12, 12, 12), s)
    elif index == 61:
        for x in (2, 7, 12): rect((x, 6, min(x + 1, 13), 10), s); rect((x, 6, min(x + 1, 13), 6), h)
        line([(4, 7), (5, 8), (4, 9)], a); line([(9, 7), (10, 8), (9, 9)], a)
    elif index == 62:
        rect((4, 3, 12, 13), s); rect((6, 2, 10, 4), h); line((6, 6, 10, 6), h)
        line([(6, 10), (7, 11), (10, 8)], a)
    elif index == 63:
        rect((4, 7, 11, 12), s); line((4, 7, 11, 7), a); line((8, 8, 8, 10), a)
        line([(2, 6), (2, 13), (13, 13), (13, 6)], h); line((7, 2, 7, 5), a); line([(5, 3), (7, 5), (9, 3)], a)
    else:
        raise ValueError(f"No icon drawing for {index}")
    # A hand-sized one-pixel cutout outline, no blur/filter/antialiasing.
    outlined = canvas((16, 16))
    for y in range(16):
        for x in range(16):
            if im.getpixel((x, y))[3]:
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                            outlined.putpixel((x + dx, y + dy), C["ink"])
    outlined.alpha_composite(im)
    return outlined


def icons():
    colors = ability_colors()
    layers = []
    for index, name in NAMES.items():
        accent = colors[index] if index < 29 else C["crystal"]
        im = canvas(SIZES["icons"])
        im.alpha_composite(icon(index, accent), ((index % 16) * 16, (index // 16) * 16))
        layers.append((f"{index:02d}_{name}", im))
    return layers


def composite(layers):
    im = canvas(layers[0][1].size)
    for _, layer in layers:
        im.alpha_composite(layer)
    return im


def chunk(kind, data):
    return struct.pack("<IH", len(data) + 6, kind) + data


def ase_string(value):
    raw = value.encode("utf-8")
    return struct.pack("<H", len(raw)) + raw


def encode_aseprite(layers):
    width, height = layers[0][1].size
    swatches = list(C.values()) + (ability_colors() if len(layers) > 1 else [])
    chunks = [chunk(0x2019, struct.pack("<III8s", len(swatches), 0, len(swatches) - 1, b"\0" * 8)
                    + b"".join(struct.pack("<H4B", 0, *color) for color in swatches))]
    for i, (name, im) in enumerate(layers):
        chunks.append(chunk(0x2004, struct.pack("<6HB3s", 3, 0, 0, width, height, 0, 255, b"\0" * 3) + ase_string(name)))
        # Crop each icon cel to its 16px cell instead of saving 45 full atlases.
        box = im.getbbox()
        if box is None:
            raise ValueError(f"Empty layer: {name}")
        cel = im.crop(box)
        chunks.append(chunk(0x2005, struct.pack("<HhhBHh5sHH", i, box[0], box[1], 255, 2, 0, b"\0" * 5, *cel.size)
                            + zlib.compress(cel.tobytes(), 9)))
    data = b"".join(chunks)
    frame = struct.pack("<IHHH2sI", 16 + len(data), 0xF1FA, len(chunks), 100, b"\0" * 2, len(chunks)) + data
    header = bytearray(128)
    struct.pack_into("<IHHHHHIH", header, 0, 128 + len(frame), 0xA5E0, 1, width, height, 32, 1, 100)
    struct.pack_into("<HBB", header, 32, len(swatches), 1, 1)
    return bytes(header) + frame


def decode_aseprite(path):
    """Validate the emitted RGBA, single-frame, normal-layer Aseprite subset."""
    data = path.read_bytes()
    total, magic, frames, w, h, depth = struct.unpack_from("<I5H", data)
    if (total, magic, frames, depth) != (len(data), 0xA5E0, 1, 32):
        raise ValueError(f"Invalid Aseprite header: {path}")
    frame_size, frame_magic, count = struct.unpack_from("<IHH", data, 128)
    if frame_magic != 0xF1FA or frame_size != len(data) - 128:
        raise ValueError(f"Invalid Aseprite frame: {path}")
    offset, names, cels = 144, [], {}
    for _ in range(count):
        size, kind = struct.unpack_from("<IH", data, offset)
        start = offset + 6
        if kind == 0x2004:
            length = struct.unpack_from("<H", data, start + 16)[0]
            names.append(data[start + 18:start + 18 + length].decode("utf-8"))
        elif kind == 0x2005:
            index, x, y, opacity, cel_type = struct.unpack_from("<HhhBH", data, start)
            cw, ch = struct.unpack_from("<HH", data, start + 16)
            if opacity != 255 or cel_type != 2:
                raise ValueError("Only opaque compressed RGBA cels are supported")
            im = canvas((w, h))
            im.alpha_composite(Image.frombytes("RGBA", (cw, ch), zlib.decompress(data[start + 20:offset + size])), (x, y))
            cels[index] = im
        offset += size
    if offset != len(data) or len(cels) != len(names):
        raise ValueError(f"Incomplete Aseprite frame: {path}")
    return [(name, cels[i]) for i, name in enumerate(names)]


def spans(length, source_length, border):
    cap = min(border, source_length // 2)
    head = min(cap, length // 2)
    tail = min(cap, length - head)
    result = [(0, 0, head)] if head else []
    step = source_length - 2 * cap or source_length
    for at in range(head, length - tail, step):
        result.append((at, cap if source_length > 2 * cap else 0, min(step, length - tail - at)))
    if tail:
        result.append((length - tail, source_length - tail, tail))
    return result


def sliced(im, size, border_x, border_y=None):
    by = border_x if border_y is None else border_y
    out = canvas(size)
    for y, v, h in spans(size[1], im.height, by):
        for x, u, w in spans(size[0], im.width, border_x):
            out.alpha_composite(im.crop((u, v, u + w, v + h)), (x, y))
    return out


def make_mockup(images):
    im = Image.new("RGBA", (320, 240), (18, 20, 28, 255))
    text(im, (15, 4), "RIFT / GUI THEME / CONTRACT MOCKUP", C["lavender"])
    im.alpha_composite(sliced(images["panel"], (292, 216), 8), (14, 20))
    im.alpha_composite(sliced(images["header"], (268, 16), 8, 0), (26, 31))
    im.alpha_composite(images["icons"].crop((176, 16, 192, 32)), (35, 31))
    text(im, (62, 33), "BOSS / PHASE 01")
    text(im, (31, 55), "Ability configuration", (208, 208, 208, 255))
    for state, title, idx in ((0, "Reality rift", 27), (1, "Configure / hover", 54), (2, "Unavailable", 59)):
        y = 73 + state * 26
        im.alpha_composite(sliced(images["button"].crop((0, state * 20, 200, state * 20 + 20)), (232, 20), 4), (31, y))
        u, v = idx % 16 * 16, idx // 16 * 16
        im.alpha_composite(images["icons"].crop((u, v, u + 16, v + 16)), (35, y + 2))
        text(im, (78, y + 4), title)
    text(im, (31, 153), "Cooldown / ticks", (208, 208, 208, 255))
    im.alpha_composite(sliced(images["field"], (158, 20), 3), (31, 168))
    text(im, (38, 172), "120_")
    im.alpha_composite(images["toggle"], (207, 168))
    text(im, (31, 199), "29 abilities / 16 menu sections", C["lavender"])
    im.alpha_composite(sliced(images["scroll"].crop((0, 0, 16, 16)), (16, 128), 0, 2), (276, 60))
    im.alpha_composite(sliced(images["scroll"].crop((0, 16, 16, 32)), (16, 34), 0, 2), (276, 90))
    im.alpha_composite(images["scroll"].crop((0, 32, 16, 48)), (276, 190))
    return im


def previews(images):
    for name, im in images.items():
        half_width = max(144, im.width + 12)
        sample = Image.new("RGBA", (half_width * 2, im.height + 26), (20, 23, 31, 255))
        d = ImageDraw.Draw(sample)
        d.rectangle((half_width, 0, sample.width - 1, sample.height - 1), fill=(208, 213, 207, 255))
        sample.alpha_composite(im, (6, 20))
        sample.alpha_composite(im, (half_width + 6, 20))
        text(sample, (6, 3), name.upper() + " / DARK", C["lavender"])
        text(sample, (half_width + 6, 3), "LIGHT", C["ink"], False)
        filename = "icons_atlas_4x.png" if name == "icons" else f"{name}_4x.png"
        save(sample.resize((sample.width * 4, sample.height * 4), Image.Resampling.NEAREST), PREVIEW / filename)
    # Each contact-sheet icon is exactly 64x64, with its index and name outside it.
    sheet = Image.new("RGBA", (700, 224), (20, 23, 31, 255))
    text(sheet, (8, 3), "ABILITIES 00-28 / SECTIONS 48-63 / 16 PX EACH", C["lavender"])
    for n, (idx, name) in enumerate(NAMES.items()):
        x, y = 8 + (n % 5) * 140, 22 + (n // 5) * 22
        u, v = idx % 16 * 16, idx // 16 * 16
        sheet.alpha_composite(images["icons"].crop((u, v, u + 16, v + 16)), (x, y))
        text(sheet, (x + 21, y + 3), f"{idx:02d} {name}", C["silver"])
    save(sheet.resize((2800, 896), Image.Resampling.NEAREST), PREVIEW / "icons_4x.png")
    mockup = make_mockup(images)
    for scale in (2, 3, 4):
        save(mockup.resize((320 * scale, 240 * scale), Image.Resampling.NEAREST), PREVIEW / f"mockup_{scale}x.png")
    tiled = sliced(images["panel"], (211, 163), 8)
    save(tiled.resize((844, 652), Image.Resampling.NEAREST), PREVIEW / "panel_tiling_4x.png")


def luminance(rgb):
    channels = [v / 3294.6 if v <= 10 else ((v / 255 + .055) / 1.055) ** 2.4 for v in rgb[:3]]
    return sum(a * b for a, b in zip(channels, (.2126, .7152, .0722)))


def check():
    report = {}
    images = {}
    for name, size in SIZES.items():
        path = OUT / f"{name}.png"
        im = Image.open(path)
        if im.mode != "RGBA" or im.size != size or path.read_bytes()[24:26] != bytes((8, 6)):
            raise AssertionError(f"{name}: expected RGBA8 {size}, got {im.mode} {im.size}")
        rgba = list(pixels(im))
        semi = sum(0 < p[3] < 255 for p in rgba)
        colors = {p for p in rgba if p[3]}
        if semi:
            raise AssertionError(f"{name}: partial alpha")
        if name in ("panel", "button", "field") and not 12 <= len(colors) <= 16:
            raise AssertionError(f"{name}: expected 12..16 colors, got {len(colors)}")
        if name != "icons" and not colors <= set(C.values()):
            raise AssertionError(f"{name}: color outside theme palette")
        layers = decode_aseprite(ART / f"{name}.aseprite")
        if composite(layers).tobytes() != im.tobytes():
            raise AssertionError(f"{name}: Aseprite/PNG mismatch")
        if name == "icons" and [n for n, _ in layers] != [f"{i:02d}_{n}" for i, n in NAMES.items()]:
            raise AssertionError("Icon layers/indices differ")
        images[name] = im
        report[name] = {"size": list(size), "semi_alpha": semi, "colors": len(colors), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        print(f"{name + '.png':12s} {im.width}x{im.height} semi-alpha {semi} colours {len(colors)}")
    atlas = images["icons"]
    colors = ability_colors()
    fingerprints = set()
    for index in range(256):
        x, y = index % 16 * 16, index // 16 * 16
        cell = atlas.crop((x, y, x + 16, y + 16))
        if (cell.getbbox() is not None) != (index in NAMES):
            raise AssertionError(f"Incorrect atlas occupancy at {index}")
        if index in NAMES:
            allowed = {C["ink"], C["rim"], C["silver"], colors[index] if index < 29 else C["crystal"]}
            if not {p for p in pixels(cell) if p[3]} <= allowed:
                raise AssertionError(f"Icon {index}: more than two theme tones and one accent")
            for y in range(16):
                for x in range(16):
                    if cell.getpixel((x, y))[3] and cell.getpixel((x, y)) != C["ink"]:
                        if x in (0, 15) or y in (0, 15):
                            raise AssertionError(f"Icon {index}: clipped 1px outline")
                        if any(not cell.getpixel((x + dx, y + dy))[3] for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))):
                            raise AssertionError(f"Icon {index}: incomplete 1px outline")
            silhouette = cell.getchannel("A").tobytes()
            if silhouette in fingerprints:
                raise AssertionError(f"Duplicate silhouette at {index}")
            fingerprints.add(silhouette)
    source = (ROOT / "src/main/java/com/goodbird/cnpcgeckoaddon/data/BossAbilityKind.java").read_text(encoding="utf-8")
    for index, name in enumerate(ABILITIES):
        if not re.search(rf"\b{name.upper()}\s*=\s*{index}\s*;", source):
            raise AssertionError(f"BossAbilityKind contract changed at {index}/{name}")
    tile = images["panel"].crop((8, 8, 56, 56))
    if tile.crop((0, 0, 1, 48)).tobytes() != tile.crop((47, 0, 48, 48)).tobytes() or tile.crop((0, 0, 48, 1)).tobytes() != tile.crop((0, 47, 48, 48)).tobytes():
        raise AssertionError("Panel tile has unequal opposite edges")
    regions = {"panel": images["panel"].crop((8, 8, 56, 56)), "field": images["field"].crop((3, 3, 197, 17))}
    regions.update({f"button_{state}": images["button"].crop((4, state * 20 + 4, 196, state * 20 + 16)) for state in range(3)})
    regions["header"] = images["header"].crop((8, 2, 248, 14))
    contrast = {}
    for name, region in regions.items():
        background = max(luminance(p) for p in pixels(region))
        contrast[name] = {label: round((luminance(rgb) + .05) / (background + .05), 2)
                          for label, rgb in (("white", (255, 255, 255)), ("light_gray", (208, 208, 208)))}
        if min(contrast[name].values()) < 4.5:
            raise AssertionError(f"Insufficient text contrast in {name}")
    report["checks"] = {"occupied_cells": list(NAMES), "unique_silhouettes": len(fingerprints), "tile_edges_match": True,
                        "aseprite_png_match": True, "text_contrast": contrast}
    print("PASS: atlas indices, 45 unique silhouettes, layer exports, palette, seamless fill and text contrast")
    return report


def build():
    ART.mkdir(parents=True, exist_ok=True)
    images = {}
    for name, draw in (("panel", panel), ("button", button), ("toggle", toggle), ("field", field), ("scroll", scroll), ("header", header), ("icons", icons)):
        layers = draw()
        images[name] = composite(layers)
        (ART / f"{name}.aseprite").write_bytes(encode_aseprite(layers))
        save(images[name], OUT / f"{name}.png")
    lines = ["GIMP Palette", "Name: Rift GUI - 16 hue-shifted colors", "Columns: 8", "# Ability accents live in icons.aseprite; see README."]
    lines += [f"{r:3d} {g:3d} {b:3d}\t{name}" for name, (r, g, b, _) in C.items()]
    (ART / "gui_theme.gpl").write_text("\n".join(lines) + "\n", encoding="utf-8")
    previews(images)
    report = check()
    (ART / "validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate existing sources and textures without writing")
    args = parser.parse_args()
    try:
        check() if args.check else build()
    except (OSError, ValueError, AssertionError, struct.error, zlib.error, IndexError) as exc:
        print(f"GUI theme: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

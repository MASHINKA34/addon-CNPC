"""Rebuild the four rift crystal skins from the UVs in the Blockbench source.

Python 3 + Pillow + NumPy. No noise, filtering, gradients or antialiasing.
The model is authored/exported in Blockbench; this script never invents its UVs.
Run normally to rebuild, --check for a read-only byte/contract audit.
"""
from __future__ import annotations

import argparse
import base64
import io
import itertools
import json
import math
import struct
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art/aseprite/rift_crystal"
MODEL = ROOT / "art/blockbench/rift_crystal/rift_crystal.bbmodel"
ASSETS = ROOT / "src/main/resources/assets/cnpcgeckoaddon"
TEXTURES = ASSETS / "textures/entity/rift_crystal"
GEO = ASSETS / "geo/rift_crystal.geo.json"
ANIMATION = ASSETS / "animations/rift_crystal.animation.json"
SIZE = (64, 64)
CLEAR = (0, 0, 0, 0)
STYLES = {
    "amethyst": ("AMETHYST", ["35224e", "4e316b", "684189", "8150a4", "995fb9", "af78cc", "c995db", "dfaee9", "f2c9f1", "bca6ef", "d8c7ff", "eee0ff", "fff1fc", "ffffff"]),
    "ember": ("EMBER", ["171717", "482634", "733040", "a33a3b", "cb4a37", "e96632", "f5893c", "ffad50", "ffd477", "ed8e47", "ffc05a", "ffe38a", "fff4bd", "ffffe1"]),
    "void": ("VOID", ["0d141c", "183b49", "205260", "286c77", "088591", "21a3a8", "43bdba", "76ded0", "acf3dd", "2a987c", "57c995", "92eaae", "c8fbc2", "ecffe1"]),
    "ice": ("ICE", ["334b7e", "496894", "6389b1", "7ba6c8", "94bfdb", "afd7e9", "c8e8f1", "ddf6f9", "f1ffff", "70b5df", "8bd4f1", "b0eaff", "dcf8ff", "ffffff"]),
}
LAYER_NAMES = ("facets", "edges", "core_and_marks", "UV_template_hidden")
FONT = dict(zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789/", [
    "010101111101101", "110101110101110", "011100100100011", "110101101101110",
    "111100110100111", "111100110100100", "011100101101011", "101101111101101",
    "111010010010111", "001001001101010", "101101110101101", "100100100100111",
    "101111111101101", "101111111111101", "010101101101010", "110101110100100",
    "010101101111011", "110101110101101", "011100010001110", "111010010010010",
    "101101101101111", "101101101101010", "101101111111101", "101101010101101",
    "101101010010010", "111001010100111", "111101101101111", "010110010010111",
    "110001010100111", "110001010001110", "101101111001001", "111100110001110",
    "011100111101111", "111001010010010", "111101111101111", "111101111001110",
    "001001010100100",
]))


def png_bytes(image):
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=False, compress_level=9)
    return stream.getvalue()


def palette(style):
    return [tuple(bytes.fromhex(c)) + (255,) for c in STYLES[style][1]]


def islands(project):
    for cube in project["elements"]:
        for face, data in cube["faces"].items():
            u, v, end_u, end_v = data["uv"]
            yield cube, face, (int(min(u, end_u)), int(min(v, end_v)), int(abs(end_u-u)), int(abs(end_v-v)))


def draw_skin(project, style):
    layers = {name: Image.new("RGBA", SIZE, CLEAR) for name in LAYER_NAMES}
    colors = palette(style)
    for cube, face, (u, v, width, height) in islands(project):
        core = cube["name"] in ("heart", "lower_point", "upper_point")
        cap = height <= 3
        orientation = ("north", "east", "south", "west", "up", "down").index(face)
        for y in range(height):
            for x in range(width):
                # Three hard planes, offset in hue. No blended transition.
                band = min(2, y * 3 // height)
                shade = (5, 4, 3)[band] if not cap else 5 + (x + y) % 2
                shade += orientation % 2
                layers["facets"].putpixel((u+x, v+y), colors[shade])
                if core:
                    shade = 9 + ((y // 3 + orientation) % 3)
                    if x == width // 2:
                        shade = 12 if 2 <= y < height-2 else 11
                    if cap:
                        shade = 10 + (x + y + orientation) % 3
                    if x == width//2 and y == height//2:
                        shade = 13
                    layers["core_and_marks"].putpixel((u+x, v+y), colors[shade])
                else:
                    # A single bright edge; the opposite edge stays coloured.
                    if x == 0:
                        edge = 7 if y < height//2 else 6
                        if y == 1:
                            edge = 8
                        layers["edges"].putpixel((u+x, v+y), colors[edge])
                    elif x == width-1 and width >= 3:
                        layers["edges"].putpixel((u+x, v+y), colors[2 + band % 2])
                    if not cap and x == width-1 and y in (height-4, height-3):
                        layers["core_and_marks"].putpixel((u+x, v+y), colors[1])
                    if style == "ember" and not cap and y in (5, 6, 7):
                        crack_x = width-1 if y != 6 else max(1, width-2)
                        if x == crack_x:
                            layers["core_and_marks"].putpixel((u+x, v+y), colors[0])
                    elif style == "void" and not cap and x == 1 and y == 4:
                        layers["core_and_marks"].putpixel((u+x, v+y), colors[12])
                    elif not cap and x == width-1 and y == height-2:
                        layers["core_and_marks"].putpixel((u+x, v+y), colors[0])
        guide = ImageDraw.Draw(layers["UV_template_hidden"])
        guide.rectangle((u, v, u+width-1, v+height-1), outline=(255, 75, 220, 255))
    merged = Image.new("RGBA", SIZE, CLEAR)
    for name in LAYER_NAMES[:-1]:
        merged.alpha_composite(layers[name])
    return layers, merged


def chunk(kind, payload):
    return struct.pack("<IH", 6+len(payload), kind) + payload


def ase_string(text):
    raw = text.encode("utf-8")
    return struct.pack("<H", len(raw)) + raw


def aseprite_bytes(layers, colors):
    # File format matches the existing boulder writer, with a hidden UV guide
    # and the actual 14-colour palette embedded as a modern palette chunk.
    chunks = []
    for i, (name, image) in enumerate(layers.items()):
        flags = 3 if i < 3 else 0
        chunks.append(chunk(0x2004, struct.pack("<6HB3s", flags, 0, 0, 64, 64, 0, 255, b"\0"*3) + ase_string(name)))
        cel = struct.pack("<HhhBHh5sHH", i, 0, 0, 255, 2, 0, b"\0"*5, 64, 64)
        chunks.append(chunk(0x2005, cel + zlib.compress(image.tobytes(), 9)))
    entries = b"".join(struct.pack("<H4B", 0, *c) for c in colors)
    chunks.append(chunk(0x2019, struct.pack("<III8s", len(colors), 0, len(colors)-1, b"\0"*8) + entries))
    frame = struct.pack("<IHHH2sI", 16+sum(map(len, chunks)), 0xF1FA, len(chunks), 100, b"\0"*2, len(chunks)) + b"".join(chunks)
    header = struct.pack("<IHHHHHIHII B3sHBBhhHH84s", 128+len(frame), 0xA5E0, 1, 64, 64, 32, 1, 100, 0, 0, 0, b"\0"*3, len(colors), 1, 1, 0, 0, 0, 0, b"\0"*84)
    return header + frame


def rotation(angles):
    x, y, z = map(math.radians, angles)
    cx, cy, cz, sx, sy, sz = math.cos(x), math.cos(y), math.cos(z), math.sin(x), math.sin(y), math.sin(z)
    rx = np.array([[1,0,0], [0,cx,-sx], [0,sx,cx]])
    ry = np.array([[cy,0,sy], [0,1,0], [-sy,0,cy]])
    rz = np.array([[cz,-sz,0], [sz,cz,0], [0,0,1]])
    # Blockbench cube/bone Euler order is ZYX.
    return rz @ ry @ rx


def transform(points, origin, angles):
    pivot = np.array(origin)
    return (points-pivot) @ rotation(angles).T + pivot


def hierarchy(project):
    groups = {g["uuid"]: g for g in project["groups"]}
    chains = {}
    def walk(nodes, parents):
        for node in nodes:
            if isinstance(node, str):
                chains[node] = parents
            else:
                walk(node["children"], [groups[node["uuid"]]] + parents)
    walk(project["outliner"], [])
    return chains


def world_points(cube, points, chains):
    points = transform(np.array(points, dtype=float), cube["origin"], cube.get("rotation", [0,0,0]))
    for group in chains[cube["uuid"]]:
        points = transform(points, group["origin"], group.get("rotation", [0,0,0]))
    return points


def faces(cube):
    x0,y0,z0 = cube["from"]
    x1,y1,z1 = cube["to"]
    # Top-left, top-right, bottom-right, bottom-left in the Blockbench UV view.
    return {
        "north": [[x1,y1,z0],[x0,y1,z0],[x0,y0,z0],[x1,y0,z0]],
        "south": [[x0,y1,z1],[x1,y1,z1],[x1,y0,z1],[x0,y0,z1]],
        "east": [[x1,y1,z1],[x1,y1,z0],[x1,y0,z0],[x1,y0,z1]],
        "west": [[x0,y1,z0],[x0,y1,z1],[x0,y0,z1],[x0,y0,z0]],
        "up": [[x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1]],
        "down": [[x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0]],
    }


def render(project, atlas, angle=30, size=(64,80), pixels_per_unit=2.6):
    """Orthographic, full-bright, nearest-texel proof from actual project geometry."""
    target = np.zeros((size[1], size[0], 4), dtype=np.uint8)
    depth = np.full((size[1], size[0]), -np.inf)
    camera = rotation([14,angle,0])
    chains = hierarchy(project)
    pixels = np.array(atlas)
    for cube in project["elements"]:
        for face, corners in faces(cube).items():
            points = (world_points(cube, corners, chains)-[0,11,0]) @ camera.T
            projected = np.stack([size[0]/2+points[:,0]*pixels_per_unit, size[1]/2-points[:,1]*pixels_per_unit], axis=1)
            uv = cube["faces"][face]["uv"]
            texcoords = np.array([[uv[0],uv[1]],[uv[2],uv[1]],[uv[2],uv[3]],[uv[0],uv[3]]])
            for ids in ((0,1,2), (0,2,3)):
                tri = projected[list(ids)]
                low = np.maximum(np.floor(tri.min(axis=0)).astype(int), [0,0])
                high = np.minimum(np.ceil(tri.max(axis=0)).astype(int), np.array(size)-1)
                if np.any(high < low):
                    continue
                matrix = np.vstack([tri.T, np.ones(3)])
                if abs(np.linalg.det(matrix)) < 1e-8:
                    continue
                yy,xx = np.mgrid[low[1]:high[1]+1,low[0]:high[0]+1]
                weights = np.linalg.solve(matrix, np.stack([xx.ravel()+.5, yy.ravel()+.5, np.ones(xx.size)]))
                zz = points[list(ids),2] @ weights
                inside = (weights.min(axis=0) >= -1e-8) & (zz > depth[yy.ravel(),xx.ravel()])
                tx = (texcoords[list(ids)].T @ weights).T
                tx[:,0] = np.clip(tx[:,0], min(uv[0],uv[2]), max(uv[0],uv[2])-1e-6)
                tx[:,1] = np.clip(tx[:,1], min(uv[1],uv[3]), max(uv[1],uv[3])-1e-6)
                tx = np.floor(tx).astype(int)
                xs,ys = xx.ravel()[inside],yy.ravel()[inside]
                target[ys,xs] = pixels[tx[inside,1],tx[inside,0]]
                depth[ys,xs] = zz[inside]
    return Image.fromarray(target)


def label(image, xy, text, color):
    for i,char in enumerate(text.upper()):
        for j,bit in enumerate(FONT.get(char, "0"*15)):
            if bit == "1":
                image.putpixel((xy[0]+i*4+j%3,xy[1]+j//3),color)


def preview(project, atlas, style):
    # Every panel is drawn at native pixel resolution, then enlarged exactly 4x.
    sheet = Image.new("RGB", (208,190), (12,14,22))
    draw = ImageDraw.Draw(sheet)
    label(sheet, (8,5), STYLES[style][0], (238,235,247))
    label(sheet, (130,5), "64x64 / 14", (159,157,180))
    for i, angle in enumerate((25,115,205)):
        view = render(project, atlas, angle)
        sheet.paste(view, (8+64*i,21), view)
        # Stylised end-stone top and obsidian edge, to judge the palette in
        # the intended arena colours (not an in-game screenshot).
        left = 20+64*i
        draw.rectangle((left,92,left+40,93), fill=(201,199,147))
        draw.rectangle((left,94,left+40,96), fill=(37,27,52))
        for offset in (3,14,27,35):
            draw.point((left+offset,92), fill=(158,159,112))
            draw.line((left+offset,95,left+offset+2,95), fill=(63,42,79))
    draw.rectangle((0,104,207,189), fill=(227,229,222))
    small = render(project, atlas, 295, (66,76), 2.5)
    sheet.paste(small, (4,110), small)
    # Real UV sheet shown alongside the model, on a light background.
    sheet.paste(atlas, (74,117), atlas)
    label(sheet, (145,112), "10b 20b", (60,66,76))
    # 70 degree vertical FOV, 240px native viewport. These are size proxies,
    # not a claim of an in-game test.
    for distance, x in ((10,146), (20,176)):
        scale = 240 / (2*math.tan(math.radians(35))*distance*16)
        tiny = render(project, atlas, 25, (26,42), scale)
        draw.rectangle((x-1,132,x+26,174), fill=(7,9,17))
        sheet.paste(tiny, (x,132), tiny)
    for i,c in enumerate(palette(style)):
        draw.rectangle((8+i*14,98,19+i*14,101), fill=c[:3])
    return sheet.resize((832,760), Image.Resampling.NEAREST)


def normalized_animation(project):
    """GeckoLib 4 numeric channels from the saved Blockbench keyframes.

    Blockbench 5 exports {vector:[...]} wrappers. Flatten those for the existing
    1.8.0 asset convention; keep BB's X/Y coordinate conversion, times and loops.
    """
    result = {"format_version":"1.8.0", "animations":{}}
    for clip in project["animations"]:
        data = {"animation_length":clip["length"], "bones":{}}
        if clip["loop"] == "loop":
            data["loop"] = True
        for animator in clip["animators"].values():
            if not animator.get("keyframes"):
                continue
            channels = {}
            for key in animator["keyframes"]:
                assert key["interpolation"] == "linear"
                values = [float(key["data_points"][0][axis]) for axis in "xyz"]
                if key["channel"] in ("position","rotation"):
                    values[0] *= -1
                if key["channel"] == "rotation":
                    values[1] *= -1
                channels.setdefault(key["channel"], {})[str(float(key["time"]))] = values
            data["bones"][animator["name"]] = channels
        result["animations"][clip["name"]] = data
    return result


def validate(project, images):
    geo = json.loads(GEO.read_text("utf-8"))
    animation = json.loads(ANIMATION.read_text("utf-8"))
    assert geo["format_version"] == "1.12.0"
    assert animation == normalized_animation(project), "Animation differs from Blockbench keyframes"
    assert set(animation["animations"]) == {"idle","collect"}
    geometry = geo["minecraft:geometry"][0]
    assert geometry["description"]["texture_width"] == geometry["description"]["texture_height"] == 64
    bones = {b["name"]:b for b in geometry["bones"]}
    assert bones["crystal"]["pivot"] == [0,0,0] and "parent" not in bones["crystal"]
    assert bones["core"]["parent"] == bones["shards"]["parent"] == "crystal"
    assert len(project["elements"]) == sum(len(b.get("cubes",[])) for b in bones.values()) <= 24
    assert project["meta"]["model_format"] == "geckolib_model"
    coverage = np.zeros((64,64), dtype=int)
    source_rects, exported_rects = [], []
    for _, _, (x,y,w,h) in islands(project):
        assert 0 <= x < x+w <= 64 and 0 <= y < y+h <= 64
        coverage[y:y+h,x:x+w] += 1
        source_rects.append((x,y,w,h))
    assert coverage.max() == 1, "UV overlap"
    for bone in bones.values():
        for cube in bone.get("cubes",[]):
            for face in cube["uv"].values():
                x,y = face["uv"]
                w,h = face["uv_size"]
                exported_rects.append((min(x,x+w),min(y,y+h),abs(w),abs(h)))
    assert sorted(source_rects) == sorted(exported_rects), "Export UV mismatch"
    chains = hierarchy(project)
    # Confirm that every exported cube still belongs to its source bone and
    # carries the exact pivot, rotation, size and per-face atlas coordinates.
    by_parent = {}
    for cube in project["elements"]:
        by_parent.setdefault(chains[cube["uuid"]][0]["name"], []).append(cube)
    for name, source_cubes in by_parent.items():
        for source, exported in zip(source_cubes,bones[name]["cubes"], strict=True):
            expected_origin = [-source["to"][0],source["from"][1],source["from"][2]]
            expected_size = np.array(source["to"])-source["from"]
            assert np.allclose(exported["origin"],expected_origin,atol=1e-5)
            assert np.allclose(exported["size"],expected_size,atol=1e-5)
            expected_pivot = np.array(source["origin"])*[-1,1,1]
            expected_rotation = np.array(source.get("rotation",[0,0,0]))*[-1,-1,1]
            assert np.allclose(exported.get("pivot",[0,0,0]),expected_pivot,atol=1e-5)
            assert np.allclose(exported.get("rotation",[0,0,0]),expected_rotation,atol=1e-5)
            for face, uv in source["faces"].items():
                x,y,u,v = uv["uv"]
                expected_uv = [u,v] if face in ("up","down") else [x,y]
                expected_size = [x-u,y-v] if face in ("up","down") else [u-x,v-y]
                assert exported["uv"][face] == {"uv":expected_uv,"uv_size":expected_size}
    bounds = []
    for c in project["elements"]:
        corners = list(itertools.product(*zip(c["from"],c["to"])))
        bounds.extend(world_points(c,corners,chains))
    points = np.array(bounds)
    low,high = points.min(axis=0),points.max(axis=0)
    assert abs(low[1]) < 1e-5, ("Base must be y=0",low)
    assert 20 <= high[1] <= 24 and max((high-low)[[0,2]]) <= 12
    assert np.linalg.norm(points[:,[0,2]],axis=1).max() <= 6, "Idle rotation exceeds width 12"
    idle,collect = animation["animations"]["idle"],animation["animations"]["collect"]
    assert idle["loop"] and idle["animation_length"] == 4
    assert not collect.get("loop",False) and collect["animation_length"] == .5
    spin = idle["bones"]["crystal"]["rotation"]
    assert all(v == [0,90*float(t),0] for t,v in spin.items())
    assert {v[1] for v in idle["bones"]["crystal"]["position"].values()} == {-1.5,0,1.5}
    assert {v[0] for v in idle["bones"]["core"]["scale"].values()} == {.9,1.1}
    assert collect["bones"]["crystal"]["scale"]["0.0"] == [1,1,1]
    assert collect["bones"]["crystal"]["scale"]["0.15"] == [1.4]*3
    offsets = {"north":[0,0,-4], "east":[4,0,0], "south":[0,0,4], "west":[-4,0,0]}
    for n in offsets:
        b = "shard_"+n
        assert bones[b]["parent"] == "shards"
        displacement = collect["bones"][b]["position"]["0.5"]
        assert displacement == offsets[n], "Shard must travel radially in parent space"
        assert 4 <= np.linalg.norm(displacement)*1.4 <= 6
    for style,image in images.items():
        a = np.array(image)
        colors = {tuple(p) for p in a.reshape(-1,4) if p[3]}
        assert image.mode == "RGBA" and image.size == SIZE
        assert not ((a[:,:,3]>0)&(a[:,:,3]<255)).any()
        assert 10 <= len(colors) <= 16 and colors <= set(palette(style))
        assert (a[:,:,3][coverage>0] == 255).all()
        assert (a[:,:,3][coverage==0] == 0).all()
        print(f"{style+'.png':16} 64x64 semi-alpha 0 colours {len(colors)}")
    print(f"bones {list(bones)} clips {list(animation['animations'])}")
    print(f"{len(project['elements'])} cubes; bounds {np.round(low,4)} .. {np.round(high,4)}; {len(source_rects)} nonoverlapping UV faces")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify without writing")
    parser.add_argument("--normalize-animation", action="store_true", help="Re-export linear clips from saved BB keyframes for GeckoLib 4")
    args = parser.parse_args()
    project = json.loads(MODEL.read_text("utf-8"))
    outputs, images, previews = {}, {}, []
    for style in STYLES:
        layers, merged = draw_skin(project,style)
        images[style] = merged
        outputs[TEXTURES/f"{style}.png"] = png_bytes(merged)
        outputs[ART/f"{style}.aseprite"] = aseprite_bytes(layers,palette(style))
        lines = ["GIMP Palette",f"Name: Rift Crystal {style}","Columns: 7","# 14 hand-picked hue-shifted colours; opaque UV faces"]
        lines += [f"{r:3} {g:3} {b:3}\t{style} {i+1:02}" for i,(r,g,b,_) in enumerate(palette(style))]
        outputs[ART/f"{style}.gpl"] = ("\n".join(lines)+"\n").encode()
        proof = preview(project,merged,style)
        previews.append(proof)
        outputs[ART/f"preview/{style}_4x.png"] = png_bytes(proof)
    gallery = Image.new("RGB", (1664,1520))
    for i,proof in enumerate(previews):
        gallery.paste(proof, ((i%2)*832,(i//2)*760))
    outputs[ART/"preview/all_rift_crystal_4x.png"] = png_bytes(gallery)
    if args.normalize_animation:
        outputs[ANIMATION] = (json.dumps(normalized_animation(project),indent=2)+"\n").encode()
    for path,content in outputs.items():
        if args.check:
            assert path.read_bytes() == content, f"Not reproducible: {path.relative_to(ROOT)}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    validate(project,images)
    if args.check:
        assert {t["name"] for t in project["textures"]} == {s+".png" for s in STYLES}
        for texture in project["textures"]:
            embedded = Image.open(io.BytesIO(base64.b64decode(texture["source"].split(",",1)[1]))).convert("RGBA")
            assert embedded.tobytes() == images[Path(texture["name"]).stem].tobytes(), "Refresh embedded Blockbench texture"
    print("PASS: " + ("read-only reproducibility and contract audit" if args.check else "rebuilt textures, sources, palettes and previews"))


if __name__ == "__main__":
    main()

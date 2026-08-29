#!/usr/bin/env python3
"""Import portable model resources from the Apocalyptic Bosses donor.

Only the GeckoLib geometry, animation JSON, and the entity textures used by
those models are copied. Donor classes, manifests, registries, data packs,
sounds, items, blocks, screens, armor layers, and mod metadata never enter the
CNPC addon.
"""

from __future__ import annotations

import hashlib
import json
import zipfile
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
DONOR_DIR = WORK_DIR / "donor_mods_apocalyptic"
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"


@dataclass(frozen=True)
class Donor:
    jar: str
    namespaces: tuple[str, ...]
    namespace_remap: tuple[tuple[str, str], ...] = ()
    extra_textures: tuple[str, ...] = ()
    excluded_textures: tuple[str, ...] = ()
    texture_height_fixes: dict[str, int] = field(default_factory=dict)


DONORS = (
    Donor(
        "panascraftrpgmod-3.6-neoforge-1.21.1.jar",
        namespaces=("panascraftrpgmod",),
        # The donor keeps its armor and projectile layer sheets next to the mob
        # textures. No imported geometry references them, so they stay out of
        # the visual bundle and out of the runtime texture search.
        excluded_textures=(
            "textures/entities/armor_leggings.png",
            "textures/entities/armor_leggings_layer_1.png",
            "textures/entities/blueflame_armor_layer_2.png",
            "textures/entities/dark_wither_armor_layer_2.png",
            "textures/entities/dragonstone_armor_layer_2.png",
            "textures/entities/leather_layer_2.png",
            "textures/entities/netherite_layer_2.png",
            "textures/entities/netherite_projectile_layer_2.png",
            "textures/entities/sculk_armor_layer_2.png",
        ),
        # tc_cyclops ships a seven frame 512x3584 filmstrip without an animation
        # mcmeta while its geometry still declares a 512x512 UV space. GeckoLib
        # normalizes UVs against the declared size, so the whole model would be
        # stretched across the strip. Declaring the real sheet height keeps
        # every UV inside the first frame, which is the pose the donor renders.
        texture_height_fixes={"geo/tc_cyclops.geo.json": 3584},
    ),
)


def remap_namespace(namespace: str, donor: Donor) -> str:
    return dict(donor.namespace_remap).get(namespace, namespace)


def is_model_texture(rest: str, donor: Donor) -> bool:
    lower = rest.lower()
    if not (lower.endswith(".png") or lower.endswith(".png.mcmeta")):
        return False
    base = lower.removesuffix(".mcmeta")
    if base in donor.excluded_textures:
        return False
    if lower.startswith(("textures/entity/", "textures/entities/")):
        return True
    return base in donor.extra_textures


def normalized_asset_path(name: str, donor: Donor) -> Path | None:
    pure = PurePosixPath(name)
    if len(pure.parts) < 3 or pure.parts[0].lower() != "assets":
        return None

    source_namespace = pure.parts[1].lower()
    if source_namespace not in donor.namespaces:
        return None

    namespace = remap_namespace(source_namespace, donor)
    rest = "/".join(pure.parts[2:]).lower().replace(" ", "_")
    if rest.startswith("geo/") and rest.endswith(".geo.json"):
        return ASSET_ROOT / namespace / Path(rest)
    if rest.startswith("animations/") and rest.endswith(".json"):
        return ASSET_ROOT / namespace / Path(rest)
    if is_model_texture(rest, donor):
        return ASSET_ROOT / namespace / Path(rest)
    return None


def apply_texture_height(payload: bytes, height: int) -> bytes:
    document = json.loads(payload.decode("utf-8-sig"))
    for geometry in document.get("minecraft:geometry", []):
        description = geometry.get("description")
        if isinstance(description, dict):
            description["texture_height"] = height
    return (json.dumps(document, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def main() -> None:
    written: dict[Path, tuple[str, str]] = {}
    totals = {"geo": 0, "animations": 0, "textures": 0}
    for donor in DONORS:
        jar_path = DONOR_DIR / donor.jar
        if not jar_path.is_file():
            raise FileNotFoundError(jar_path)

        donor_counts = {"geo": 0, "animations": 0, "textures": 0}
        repaired = 0
        with zipfile.ZipFile(jar_path) as archive:
            for entry in archive.infolist():
                target = normalized_asset_path(entry.filename, donor)
                if target is None or entry.is_dir():
                    continue

                payload = archive.read(entry)
                relative = target.relative_to(ASSET_ROOT).as_posix().split("/", 1)[1]
                height = donor.texture_height_fixes.get(relative)
                if height is not None:
                    payload = apply_texture_height(payload, height)
                    repaired += 1

                digest = hashlib.sha256(payload).hexdigest()
                previous = written.get(target)
                if previous and previous[0] != digest:
                    raise RuntimeError(
                        f"Normalized resource collision at {target}: "
                        f"{previous[1]} and {donor.jar}:{entry.filename}"
                    )

                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(payload)
                written[target] = (digest, f"{donor.jar}:{entry.filename}")

                if relative.startswith("geo/"):
                    kind = "geo"
                elif relative.startswith("animations/"):
                    kind = "animations"
                else:
                    kind = "textures"
                donor_counts[kind] += 1
                totals[kind] += 1

        print(
            f"{donor.jar}: {donor_counts['geo']} geo, "
            f"{donor_counts['animations']} animation, "
            f"{donor_counts['textures']} texture files "
            f"({repaired} geometry UV space repairs)"
        )

    print(
        f"TOTAL: {totals['geo']} geo, {totals['animations']} animation, "
        f"{totals['textures']} texture files"
    )


if __name__ == "__main__":
    main()

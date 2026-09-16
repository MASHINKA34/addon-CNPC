#!/usr/bin/env python3
"""Import portable model resources from the third donor batch.

Only client assets are copied: GeckoLib geometry, animation JSON, and the
entity textures those models use. Donor classes, manifests, registries, data
packs, sounds, recipes, language files, block and item art, and mod metadata
never enter the CNPC addon.

Two of the four donors (EEEAB's Mobs and Threateningly Mobs) ship no GeckoLib
geometry at all; their Java ``ModelPart`` creatures are converted separately by
``convert_round3_java_models.py``, so this script only imports their textures.
"""

from __future__ import annotations

import hashlib
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
DONOR_DIR = WORK_DIR / "donor_mods_round3"
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"


@dataclass(frozen=True)
class Donor:
    jar: str
    namespaces: tuple[str, ...]
    # False for donors whose creatures are Java models: only textures are
    # copied and the geometry comes from convert_round3_java_models.py.
    direct_models: bool = True
    namespace_remap: tuple[tuple[str, str], ...] = ()
    # Geometry stems (without .geo.json) that are not creature rigs: block
    # entities, item displays, hand rigs, and props whose textures live under
    # textures/block or textures/item. Their same-stem animations are skipped
    # with them.
    excluded_geo: tuple[str, ...] = ()
    # Texture sub-trees under textures/entity(ies) that no imported geometry
    # uses: projectile and effect sheets, vanilla villager profession overlays.
    excluded_texture_prefixes: tuple[str, ...] = ()


DONORS = (
    Donor(
        "01_eeeabs_mobs.jar",
        namespaces=("eeeabsmobs",),
        direct_models=False,
        # Beams, missiles, magic circles, portals, and the guardian blade
        # filmstrip belong to projectile and effect entities that are not
        # converted.
        excluded_texture_prefixes=("textures/entity/effect/",),
    ),
    Donor(
        "02_dragonforged.jar",
        namespaces=("dragonforged",),
        excluded_geo=(
            # Item and hand rigs.
            "aneled_bastard",
            "bronze_lantern",
            "copper_lantern",
            "dark_lantern",
            "elite_lantern",
            "lantern",
            "rusted_lantern",
            "oil_lamp",
            # Block entities.
            "fire_brazier",
            "soul_fire_brazier",
            "goblin_trap",
            # Props with no entity texture: a crate sheet at half the declared
            # UV size and a thrown rock that no renderer references.
            "crates_2",
            "rock_entity",
        ),
        excluded_texture_prefixes=(
            "textures/entity/villager/",
            "textures/entity/zombie_villager/",
        ),
    ),
    Donor(
        "03_threateningly_mobs.jar",
        namespaces=("threateningly_mobs",),
        direct_models=False,
        excluded_texture_prefixes=(
            "textures/entity/villager/",
            "textures/entity/zombie_villager/",
        ),
    ),
)


def remap_namespace(namespace: str, donor: Donor) -> str:
    return dict(donor.namespace_remap).get(namespace, namespace)


def is_model_texture(rest: str, donor: Donor) -> bool:
    lower = rest.lower()
    if not (lower.endswith(".png") or lower.endswith(".png.mcmeta")):
        return False
    if not lower.startswith(("textures/entity/", "textures/entities/")):
        return False
    return not lower.startswith(donor.excluded_texture_prefixes)


def model_stem(rest: str) -> str | None:
    lower = rest.lower()
    if lower.startswith("geo/") and lower.endswith(".geo.json"):
        return PurePosixPath(lower).name.removesuffix(".geo.json")
    if lower.startswith("animations/") and lower.endswith(".animation.json"):
        return PurePosixPath(lower).name.removesuffix(".animation.json")
    if lower.startswith("animations/") and lower.endswith(".json"):
        return PurePosixPath(lower).name.removesuffix(".json")
    return None


def normalized_asset_path(name: str, donor: Donor) -> Path | None:
    pure = PurePosixPath(name)
    if len(pure.parts) < 3 or pure.parts[0].lower() != "assets":
        return None

    source_namespace = pure.parts[1].lower()
    if source_namespace not in donor.namespaces:
        return None

    namespace = remap_namespace(source_namespace, donor)
    rest = "/".join(pure.parts[2:]).lower().replace(" ", "_")
    stem = model_stem(rest)
    if stem is not None:
        if not donor.direct_models or stem in donor.excluded_geo:
            return None
        return ASSET_ROOT / namespace / Path(rest)
    if is_model_texture(rest, donor):
        return ASSET_ROOT / namespace / Path(rest)
    return None


def main() -> None:
    written: dict[Path, tuple[str, str]] = {}
    totals = {"geo": 0, "animations": 0, "textures": 0}
    for donor in DONORS:
        jar_path = DONOR_DIR / donor.jar
        if not jar_path.is_file():
            raise FileNotFoundError(jar_path)

        donor_counts = {"geo": 0, "animations": 0, "textures": 0}
        with zipfile.ZipFile(jar_path) as archive:
            for entry in archive.infolist():
                target = normalized_asset_path(entry.filename, donor)
                if target is None or entry.is_dir():
                    continue

                payload = archive.read(entry)
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

                relative = target.relative_to(ASSET_ROOT).as_posix().split("/", 1)[1]
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
            f"{donor_counts['textures']} texture files"
        )

    print(
        f"TOTAL: {totals['geo']} geo, {totals['animations']} animation, "
        f"{totals['textures']} texture files"
    )


if __name__ == "__main__":
    main()

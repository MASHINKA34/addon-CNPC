#!/usr/bin/env python3
"""Import portable model resources from the Dungeons and Combat donor.

Only the GeckoLib geometry, animation JSON, and textures used by those models
are copied. Donor classes, manifests, registries, data packs, sounds, and mod
metadata never enter the CNPC addon.
"""

from __future__ import annotations

import hashlib
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
DONOR_DIR = WORK_DIR / "donor_mods_dnc"
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"


@dataclass(frozen=True)
class Donor:
    jar: str
    namespaces: tuple[str, ...]
    namespace_remap: tuple[tuple[str, str], ...] = ()
    extra_textures: tuple[str, ...] = ()


DONORS = (
    Donor(
        "dungeons_and_combat-1.2.2-forge-1.20.1.jar",
        namespaces=("dungeons_and_combat",),
        extra_textures=(
            "textures/item/corroding_flame_scepter.png",
            "textures/item/fairy_staff_animated.png",
            "textures/item/sanguinescepter.png",
            "textures/item/scepter_of_compensation.png",
            "textures/item/spell_book.png",
        ),
    ),
)


def remap_namespace(namespace: str, donor: Donor) -> str:
    return dict(donor.namespace_remap).get(namespace, namespace)


def is_model_texture(rest: str, donor: Donor) -> bool:
    lower = rest.lower()
    if not (lower.endswith(".png") or lower.endswith(".png.mcmeta")):
        return False
    if lower.startswith(("textures/entity/", "textures/entities/")):
        return True
    base = lower.removesuffix(".mcmeta")
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

#!/usr/bin/env python3
"""Import portable model resources from the second donor batch.

Only client assets are copied. Donor classes, manifests, registries, data packs,
sounds, and mod metadata never enter the CNPC addon.
"""

from __future__ import annotations

import hashlib
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
DONOR_DIR = WORK_DIR / "donor_mods_round2"
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"


@dataclass(frozen=True)
class Donor:
    jar: str
    direct_models: bool = True
    namespace_remap: tuple[tuple[str, str], ...] = ()


DONORS = (
    Donor("01_blighted_beasts.jar"),
    Donor("02_deep_dark_regrowth.jar"),
    Donor("03a_deeper_darker_gecko.jar", namespace_remap=(("deeperdarker", "deeperdarker_legacy"),)),
    Donor("03b_deeper_darker_latest.jar", direct_models=False),
    Donor("04_sculk_infection.jar", direct_models=False),
    Donor("05_dungeons_2_mobs.jar", direct_models=False),
    Donor("06_echoes.jar", direct_models=False),
    Donor("07_nether_update_expanded.jar"),
    Donor("08_infernal_expansion_redux.jar"),
    Donor("09_betternether.jar", direct_models=False),
    Donor("10_piglin_proliferation.jar", direct_models=False),
    Donor("11_netherific.jar"),
    Donor("12_creatures_expanded.jar"),
    Donor("13_moss_and_monsters.jar", direct_models=False),
    Donor("14_mycelium_mire.jar"),
    Donor("15_agers_mosslings.jar", direct_models=False),
    Donor("16_the_undergarden.jar", direct_models=False),
    Donor("17_critters_cryptids.jar"),
    Donor("18_redev_mobs.jar"),
    Donor("19_wrought_nights.jar"),
)


EXCLUDED_NAMESPACES = {"minecraft", "emi", "lambdynlights", "wover"}


def remap_namespace(namespace: str, donor: Donor) -> str:
    return dict(donor.namespace_remap).get(namespace, namespace)


def is_texture_asset(namespace: str, rest: str) -> bool:
    lower = rest.lower()
    if not (lower.endswith(".png") or lower.endswith(".png.mcmeta")):
        return False
    if lower.startswith("textures/entity/") or lower.startswith("textures/entities/"):
        return True
    if namespace == "sculk_worm":
        return lower.startswith(("textures/infection/", "textures/watcher/", "textures/worm/"))
    if namespace == "ecosystemmod" and lower.startswith("textures/"):
        suffix = lower.removeprefix("textures/")
        return "/" not in suffix
    return False


def normalized_asset_path(name: str, donor: Donor) -> Path | None:
    pure = PurePosixPath(name)
    if len(pure.parts) < 3 or pure.parts[0] != "assets":
        return None
    source_namespace = pure.parts[1].lower()
    if source_namespace in EXCLUDED_NAMESPACES:
        return None
    namespace = remap_namespace(source_namespace, donor)
    rest = "/".join(pure.parts[2:]).lower().replace(" ", "_")
    if donor.direct_models and (
        (rest.startswith("geo/") and rest.endswith(".json"))
        or (rest.startswith("animations/") and rest.endswith(".json"))
    ):
        return ASSET_ROOT / namespace / Path(rest)
    if is_texture_asset(source_namespace, rest):
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
                        f"Normalized resource collision at {target}: {previous[1]} and {donor.jar}:{entry.filename}"
                    )
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(payload)
                written[target] = (digest, f"{donor.jar}:{entry.filename}")
                relative = target.relative_to(ASSET_ROOT).as_posix()
                if "/geo/" in f"/{relative}" or relative.split("/", 1)[1].startswith("geo/"):
                    kind = "geo"
                elif "/animations/" in f"/{relative}" or relative.split("/", 1)[1].startswith("animations/"):
                    kind = "animations"
                else:
                    kind = "textures"
                donor_counts[kind] += 1
                totals[kind] += 1
        print(
            f"{donor.jar}: {donor_counts['geo']} geo, "
            f"{donor_counts['animations']} animation, {donor_counts['textures']} texture files"
        )
    print(f"TOTAL: {totals['geo']} geo, {totals['animations']} animation, {totals['textures']} texture files")


if __name__ == "__main__":
    main()

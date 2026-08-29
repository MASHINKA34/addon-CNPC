"""Normalize donor animation bone names to the bundled GeckoLib geometry.

Some Blockbench exports retain punctuation in animation bone keys while the
matching geometry removes it. GeckoLib resolves bones by exact name, so those
channels would otherwise be ignored when the original mod's custom animation
processor is not present.
"""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "assets"


def normalized(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.lower())


MANUAL_RENAMES = {
    ("block_factorys_bosses", "yeti_boss"): {
        "root": "root001",
        "head": "CTR_head",
        "CTR_shoulder_left": "CTR_shoulde1r_left",
    },
    ("dungeons_and_combat", "spell_book"): {
        "left_arm": "LeftArm",
        "right_arm": "RightArm",
    },
}

MANUAL_DROPS = {
    ("dungeons_and_combat", "failure"): {"bone2"},
    ("dungeons_and_combat", "skull"): {"weapon"},
    ("dungeons_and_combat", "sunleia"): {"center"},
    # Apocalyptic Bosses animates hitboxes, capes, cloth, and spawned weapon or
    # spell effects that the shipped geometry does not contain. Those channels
    # belong to props the donor mod builds at runtime, so they cannot resolve
    # in the standalone visual bundle.
    ("panascraftrpgmod", "abyssal_knight"): {
        "hitbox"
    },
    ("panascraftrpgmod", "basalt_guardian"): {
        "belt", "belt2", "slash2_vfx", "slash3_vfx", "slash_vfx", "w_left",
        "w_right"
    },
    ("panascraftrpgmod", "cursed_beast"): {
        "Impact", "cloud1", "cloud2", "cloud3", "cloud4", "cloud5", "cloud6",
        "spike1", "spike2", "spike3", "spike4", "spike5", "spike6", "spike7"
    },
    ("panascraftrpgmod", "cursed_beast_fixed"): {
        "Impact", "cloud1", "cloud2", "cloud3", "cloud4", "cloud5", "cloud6",
        "spike1", "spike2", "spike3", "spike4", "spike5", "spike6", "spike7"
    },
    ("panascraftrpgmod", "fixed_skeleton_boss"): {
        "ring1", "ring2", "ring3", "ring4"
    },
    ("panascraftrpgmod", "sculk_sentinel"): {
        "arrow", "arrow_projectile", "bow", "cloak1", "glow_arrow",
        "glow_arrow_projectile", "glow_circle1", "glow_circle2", "h_head",
        "hidden_blade", "hip_cloth", "left_cape1", "left_cape2", "left_cape3",
        "right_cape", "right_cape1", "right_cape3"
    },
    ("panascraftrpgmod", "sintia_the_hydra"): {
        "bone19", "bone20", "bone21", "bone22"
    },
    ("panascraftrpgmod", "tc_cyclops"): {
        "arm_drape1", "arm_drape2", "arm_drape3", "arm_drape4", "arm_drape5",
        "arm_drape6", "cape1", "cape2", "cheek_drape1", "cheek_drape2",
        "cheek_drape3", "cheek_drape4", "ears", "effect", "left_forearm", "neck",
        "pelvis_drape1", "pelvis_drape2", "right_forearm", "sphere", "staff"
    },
}


def normalize_pair(namespace: str, model: Path, animation: Path) -> int:
    geometry = json.loads(model.read_text(encoding="utf-8"))
    animation_data = json.loads(animation.read_text(encoding="utf-8"))

    geometry_names = {
        bone["name"]
        for bone in geometry["minecraft:geometry"][0].get("bones", [])
    }
    normalized_geometry: dict[str, list[str]] = {}
    for name in geometry_names:
        normalized_geometry.setdefault(normalized(name), []).append(name)

    model_name = model.name.removesuffix(".geo.json")
    manual = MANUAL_RENAMES.get((namespace, model_name), {})
    dropped = MANUAL_DROPS.get((namespace, model_name), set())
    changed = 0

    for clip in animation_data.get("animations", {}).values():
        bones = clip.get("bones")
        if not isinstance(bones, dict):
            continue

        renamed: dict[str, object] = {}
        for source_name, channels in bones.items():
            if source_name in dropped:
                changed += 1
                continue
            target_name = source_name
            if source_name not in geometry_names:
                target_name = manual.get(source_name, source_name)
                if target_name == source_name:
                    candidates = normalized_geometry.get(normalized(source_name), [])
                    if len(candidates) == 1:
                        target_name = candidates[0]

            if target_name in renamed and target_name != source_name:
                raise ValueError(
                    f"Bone rename collision in {animation}: {source_name} -> {target_name}"
                )
            renamed[target_name] = channels
            changed += target_name != source_name

        clip["bones"] = renamed

    if changed:
        animation.write_text(
            json.dumps(animation_data, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return changed


def main() -> None:
    pairs = [
        (
            "block_factorys_bosses",
            ROOT / "block_factorys_bosses" / "geo" / "entity",
            ROOT / "block_factorys_bosses" / "animations" / "entity",
        ),
        (
            "mowziesmobs",
            ROOT / "mowziesmobs" / "geo",
            ROOT / "mowziesmobs" / "animations",
        ),
        (
            "dungeons_and_combat",
            ROOT / "dungeons_and_combat" / "geo",
            ROOT / "dungeons_and_combat" / "animations",
        ),
        (
            "panascraftrpgmod",
            ROOT / "panascraftrpgmod" / "geo",
            ROOT / "panascraftrpgmod" / "animations",
        ),
    ]

    total = 0
    for namespace, geometry_dir, animation_dir in pairs:
        for animation in sorted(animation_dir.glob("*.animation.json")):
            model = geometry_dir / animation.name.replace(".animation.json", ".geo.json")
            if model.exists():
                changed = normalize_pair(namespace, model, animation)
                if changed:
                    print(f"{animation.relative_to(ROOT)}: normalized {changed} channels")
                total += changed
    print(f"Total normalized animation bone channels: {total}")


if __name__ == "__main__":
    main()

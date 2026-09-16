#!/usr/bin/env python3
"""Convert the Java-rendered creatures of donor batch three to standalone GeckoLib JSON.

EEEAB's Mobs and Threateningly Mobs ship no GeckoLib geometry; Metus Oblita
renders two of its creatures with Java models as well. All of them are vanilla
``ModelPart`` layers built by Blockbench, so their cubes, pivots, and UVs are
read from the decompiled ``LayerDefinition`` builders and written as GeckoLib
geometry. Authored keyframes are converted where the donor has them:

* Threateningly Mobs and Metus Oblita use vanilla ``AnimationDefinition``
  classes under SRG names (MCreator output for Forge 1.20.1).
* EEEAB's Mobs carries its own copy of the keyframe classes under readable
  names; its models also apply static rest poses in ``setupAnim`` that this
  converter bakes into the bone rotations, because GeckoLib adds keyframes to
  the geometry pose exactly the way the donor adds them to the static one.

Procedural Java motion (sine-based walking, look-at) has no keyframes, so the
portable idle, walk, and attack loops of the round-two converter stand in for
it, but only for the actions the donor has no authored clip for: a clip whose
name reads as idle, walk, or attack in the vocabulary of
``ensure_round3_animations.py`` keeps the placeholder out, and that script then
aliases the standard name to the authored clip. A placeholder that would move
no bone is left out as well, except ``idle``, which every model must carry. No
donor class is packaged.
"""

from __future__ import annotations

import json
import math
import re
from dataclasses import dataclass
from pathlib import Path

from convert_java_modelparts import (
    Part,
    matching_paren,
    number,
    parse_animations as parse_srg_animations,
    split_args,
)
from convert_round2_java_models import (
    animation_document,
    clean_vector,
    constants,
    parse_builder_flexible,
    parse_named_animation_definitions,
    parse_pose,
    parts_to_geometry,
    write_json,
)
from ensure_round3_animations import STANDARD_CLIPS, STANDARD_KEYWORDS, has_channels, matching_clip


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
DECOMPILED = WORK_DIR / "donor_decompiled_round3"
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"

EEEAB_ROOT = "01_eeeabs_mobs/com/eeeab/eeeabsmobs/client/model"
THREATENINGLY_ROOT = "03_threateningly_mobs/net/mcreator/threateninglymobs/client/model"
METUS_ROOT = "04_metus_oblita/net/mcreator/metusoblita/client/model"


@dataclass(frozen=True)
class JavaModel:
    """One converted creature.

    ``java_class`` is the model class name; ``layer_method`` names the static
    ``LayerDefinition`` factory that builds the geometry, because EEEAB's
    ``ModelCorpse`` carries two of them. ``texture`` is the default sheet the
    donor renderer draws with, relative to the namespace. ``animation_class``
    is the MCreator animation holder (SRG keyframes); EEEAB's models name the
    clips they play in ``setupAnim`` and are resolved from there.
    ``clip_prefix`` is the fallback for the ``<holder>_`` prefix MCreator puts
    on clip names, used when the holder's own prefix cannot be read from its
    clips (see ``holder_prefix``).
    """

    root: str
    java_class: str
    namespace: str
    identifier: str
    texture: str
    layer_method: str = "createBodyLayer"
    animation_class: str | None = None
    clip_prefix: str = ""


def eeeab(java_class: str, identifier: str, texture: str, layer_method: str = "createBodyLayer") -> JavaModel:
    return JavaModel(
        EEEAB_ROOT,
        f"entity/{java_class}",
        "eeeabsmobs",
        identifier,
        f"textures/entity/{texture}.png",
        layer_method,
    )


def threateningly(java_class: str, identifier: str, texture: str) -> JavaModel:
    stem = java_class.removeprefix("Model")
    return JavaModel(
        THREATENINGLY_ROOT,
        java_class,
        "threateningly_mobs",
        identifier,
        f"textures/entities/{texture}.png",
        animation_class=f"animations/{stem}Animation",
        clip_prefix=f"{stem.lower()}_",
    )


def metus(java_class: str, identifier: str, texture: str) -> JavaModel:
    stem = java_class.removeprefix("Model")
    return JavaModel(
        METUS_ROOT,
        java_class,
        "metus_oblita",
        identifier,
        f"textures/entities/{texture}.png",
        animation_class=f"animations/{stem}Animation",
        clip_prefix=f"{stem.lower()}_",
    )


MODELS: tuple[JavaModel, ...] = (
    # EEEAB's Mobs 0.98.1: every creature renderer and the sheet it binds.
    # Projectiles, effects, armor, and held-item models are not converted.
    eeeab("ModelCorpse", "java_corpse", "corpse"),
    eeeab("ModelCorpse", "java_corpse_villager", "corpse_villager", "createVillagerBodyLayer"),
    eeeab("ModelCorpseWarlock", "java_corpse_warlock", "corpse_warlock"),
    eeeab("ModelAbsImmortalSkeleton", "java_immortal_skeleton", "immortal_skeleton"),
    eeeab("ModelImmortalShaman", "java_immortal_shaman", "immortal_shaman"),
    eeeab("ModelImmortalExecutioner", "java_immortal_executioner", "immortal_executioner"),
    eeeab("ModelMagicGolem", "java_immortal_golem", "immortal_golem"),
    eeeab("ModelImmortal", "java_immortal", "immortal"),
    eeeab("ModelNamelessGuardian", "java_nameless_guardian", "nameless_guardian"),
    eeeab("ModelRealmWarden", "java_realm_warden", "realm_warden"),
    eeeab("ModelRelicObserver", "java_relic_observer", "relic_observer"),
    eeeab("ModelRelicRipper", "java_relic_ripper", "relic_ripper"),
    eeeab("ModelRelicEarthshaker", "java_relic_earthshaker", "relic_earthshaker"),
    eeeab("ModelRelicAnnihilator", "java_relic_annihilator", "relic_annihilator"),
    eeeab("ModelTester", "java_tester", "tester"),
    eeeab("ModelUnKnown", "java_unknown", "unknown"),

    # Threateningly Mobs 1.1.1: every model drawn by a MobRenderer, named after
    # its entity, with the sheet its renderer returns from getTextureLocation.
    # Bullet, ammo, slash-wave, summoning-circle, lightning-marker, and
    # water-impact effects are not converted.
    threateningly("Modelabyssfang", "java_abyss_fang", "abstexture_glowing"),
    threateningly("Modelaodre", "java_armor_of_desert", "aodtexture_2"),
    threateningly("Modelwyvern", "java_basalt_wyvern", "phiptheretexture"),
    threateningly("Modelblastrock", "java_blastrock", "blastrocktexture"),
    threateningly("ModelShooterCrab", "java_cannon_crab", "shootcrabtexture"),
    threateningly("ModelCastylosaurus", "java_castylosaurus", "castylosaurustexture"),
    threateningly("Modelcavehunter", "java_cave_hunter", "cavehuntertexture"),
    threateningly("ModelConfinement", "java_confinement_trap", "confinementtraptexture"),
    threateningly("Modelcopas", "java_copas", "copastexture"),
    threateningly("ModelDevourer", "java_devourer", "devotexture"),
    threateningly("Modeldiplocaulus", "java_diplocaulus", "diplocaulustexture"),
    threateningly("ModelDracoscorpius", "java_dracoscorpius", "dratexture"),
    threateningly("Modelguardian", "java_dungeon_guardian", "gu_texture"),
    threateningly("ModelEarthDrill", "java_earthdrill", "earthdrilltexture5"),
    threateningly("Modelearthloong", "java_earthloong", "woodlizardtexturere"),
    threateningly("ModelStrongZombieWarrior", "java_elite_zombie_warrior", "szwtexture"),
    threateningly("Modelexecutioner", "java_executioner", "executionertexture"),
    threateningly("Modelferoxworm", "java_ferox_worm", "feroxwormtexture"),
    threateningly("Modelfirelizard", "java_fire_lizard", "firelizardtexture"),
    threateningly("Modelflamehorn", "java_flamehorn", "flamearmortexturenosaddle"),
    threateningly("Modelflamelarva", "java_flamelarva", "flamelarvatexture"),
    threateningly("Modelflamestorm", "java_flamestorm", "fastexture"),
    threateningly("Modelfod", "java_flower_of_dragon", "fodtexture"),
    threateningly("Modeldraketamed", "java_forest_drake", "draketexture"),
    threateningly("ModelFrostbite", "java_frostbite", "rosttexture"),
    threateningly("ModelSeacucumber", "java_giant_seacucumber", "cucumbertexture"),
    threateningly("Modelpowerguard", "java_guardian_statue", "guardtexture"),
    threateningly("ModelHealingFairy", "java_healing_fairy", "healingfairytexture"),
    threateningly("Modelhippofish", "java_hippo_fish", "hippofishtexture"),
    threateningly("ModelHolyCoffin", "java_holy_coffin", "holycoffintexture"),
    threateningly("Modelhorseshoecrab", "java_horsehoe_crab", "horsecrabtexture"),
    threateningly("Modelhoegg", "java_horsehoe_egg", "hoeggtexture"),
    threateningly("Modelhydra", "java_hydra", "hydratexture_2"),
    threateningly("ModelHydraCub", "java_hydra_cub", "hydracubtexture"),
    threateningly("Modelsaint", "java_hypocritical_saint", "saints_texture"),
    threateningly("Modelicebroodmother", "java_ice_brood_mother", "icebroodmothertexture"),
    threateningly("Modelicefairyguardian", "java_ice_fairy_guardian", "icefairyguardiantexture"),
    threateningly("ModelIceWeaver", "java_ice_weaver", "iceweavertexture"),
    threateningly("Modelunknown", "java_icefairy", "icefairytexture"),
    threateningly("ModelInferno", "java_inferno", "newinfernotexture"),
    threateningly("Modelbookfairy", "java_knowledge_fairy", "bookfairytexture"),
    threateningly("Modellichnogeo", "java_lich", "lichtex2"),
    threateningly("Modellindworm", "java_lindworm", "lindwormtexture"),
    threateningly("Modellouxia", "java_louxia", "louxiatexture"),
    threateningly("ModelMegaAphid", "java_mega_aphid", "aphidtexture"),
    threateningly("Modelmoonpriest", "java_moon_priest", "moonpriesttexture"),
    threateningly("ModelMTR1", "java_mtr1", "mtr1texture"),
    threateningly("Modelnaturefairy", "java_nature_fairy", "naturefairytexture"),
    threateningly("ModelNatureHarmony", "java_nature_harmony", "harmonytexture"),
    threateningly("Modelnibbler", "java_nibbler", "nibblertexture"),
    threateningly("ModelPlagueBird", "java_plague_bird", "plaguebirdtexture"),
    threateningly("Modeltriplehead", "java_red_triplefish", "tripletexture"),
    threateningly("ModelregalhartRE", "java_regalhart", "regalharttexture"),
    threateningly("Modelriptooth", "java_riptooth", "riptoothtexture"),
    threateningly("Modelrockpill", "java_rock_cannon", "rptexture"),
    threateningly("Modelrocksnailnogeo", "java_rock_snail", "rocksnailtexture"),
    threateningly("Modelsaintsummoner", "java_saint_summoner", "saintsummonertexture"),
    threateningly("Modelsandworm", "java_sandworm", "sandwormtexture"),
    threateningly("Modelscorchgolem", "java_scorch_golem", "scorchgolemtexture"),
    threateningly("Modelmoonbutterfly", "java_shadowmoon_butterfly", "moonflytexture"),
    threateningly("ModelShadowSpider", "java_shadow_spider", "shadowspidertexture"),
    threateningly("Modeleliteminion", "java_skeleton_minion", "eliteminiontexture"),
    threateningly("Modelreaper", "java_skeleton_predator", "reapertexture"),
    threateningly("ModelSnowServant", "java_snow_servent", "snowserventtexture"),
    threateningly("Modeldrybettle", "java_solscarab_maximus", "dbettexture"),
    threateningly("Modelsteelboar", "java_steelboar", "steelboartexture"),
    threateningly("Modeltallmouse", "java_tall_mouse", "tallmousetexture"),
    threateningly("ModelTerradragon_Re", "java_terra_dragon", "terradragontexture"),
    threateningly("Modeltidespecter", "java_tide_specter", "tidespectertexture"),
    threateningly("ModelTitanRabbitReNogeo", "java_titan_rabbit", "retitanrabbittexture"),
    threateningly("Modelpaladin", "java_undead_paladin", "paladintexture"),
    threateningly("Modelvine", "java_vine", "vinetexture"),
    threateningly("Modelsandstorm", "java_windcannon", "texturestorms"),

    # Metus Oblita 1.1.6: the two creatures that are not GeckoLib models. The
    # Immolar renderer binds the alchemical reactor sheet and paints the real
    # skin through conditional layers; the hungry state is its resting look.
    metus("Modelimmolar", "java_immolar", "immolar_hungry"),
    metus("Modeljuggernaut", "java_juggernaut", "juggernaut_alpha"),
)


def java_texture_mappings() -> dict[str, str]:
    """Model resource -> default texture resource for every converted creature."""
    return {
        f"{model.namespace}:geo/{model.identifier}.geo.json": f"{model.namespace}:{model.texture}"
        for model in MODELS
    }


def method_body(source: str, name: str) -> str:
    match = re.search(rf"\b{re.escape(name)}\s*\([^)]*\)\s*\{{", source)
    if not match:
        raise ValueError(f"method {name} not found")
    opening = match.end() - 1
    depth = 0
    in_string = False
    escaped = False
    for index in range(opening, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise ValueError(f"unbalanced braces in {name}")


def top_level_statements(body: str) -> list[str]:
    statements: list[str] = []
    depth = 0
    start = 0
    for index, char in enumerate(body):
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            start = index + 1
        elif char == ";" and depth == 0:
            statements.append(body[start:index].strip())
            start = index + 1
    return statements


def parse_layer_geometry(source: str, layer_method: str, identifier: str) -> tuple[dict, set[str], dict[str, int]]:
    body = method_body(source, layer_method)
    layer = re.search(
        r"LayerDefinition\.(?:create|m_171565_)\([^;]+?,\s*\(int\)?\s*(\d+),\s*\(int\)?\s*(\d+)\)",
        body,
        re.S,
    )
    if not layer:
        raise ValueError(f"texture size not found in {layer_method}")
    texture_width, texture_height = int(layer.group(1)), int(layer.group(2))
    string_values, numeric_values, builders = constants(source)

    call_pattern = re.compile(
        r"(?:(?:PartDefinition)\s+([A-Za-z0-9_]+)\s*=\s*)?"
        r"([A-Za-z0-9_]+)\.(?:addOrReplaceChild|m_171599_)\("
    )
    parts: list[Part] = []
    variables: set[str] = set()
    used_names: dict[str, int] = {}
    for index, match in enumerate(call_pattern.finditer(body)):
        variable, parent_variable = match.group(1), match.group(2)
        opening = match.end() - 1
        closing = matching_paren(body, opening)
        args = split_args(body[opening + 1 : closing])
        if len(args) < 3:
            continue
        raw_name = args[0].strip()
        if raw_name.startswith('"') and raw_name.endswith('"'):
            bone_name = raw_name[1:-1]
        else:
            bone_name = string_values.get(raw_name, raw_name.lower())
        count = used_names.get(bone_name, 0) + 1
        used_names[bone_name] = count
        if count > 1:
            bone_name = f"{bone_name}_{count}"
        variable = variable or f"__part_{index}"
        parent = parent_variable if parent_variable in variables else None
        try:
            offset, rotation = parse_pose(args[2], numeric_values)
        except (ValueError, SyntaxError):
            offset, rotation = [0.0, 0.0, 0.0], [0.0, 0.0, 0.0]
        parts.append(
            Part(
                variable,
                bone_name,
                parent,
                offset,
                rotation,
                parse_builder_flexible(args[1], numeric_values, builders),
            )
        )
        variables.add(variable)
    if not parts:
        raise ValueError(f"no model parts parsed from {layer_method}")
    return parts_to_geometry(parts, identifier, texture_width, texture_height)


def static_pose(source: str) -> dict[str, list[float]]:
    """Unconditional ``setStaticRotationAngle`` calls of ``setupAnim`` by bone name."""
    try:
        body = method_body(source, "setupAnim")
    except ValueError:
        return {}
    fields = dict(re.findall(r"this\.([A-Za-z0-9_]+)\s*=\s*[A-Za-z0-9_.]+\.(?:getChild|m_171324_)\(\"([^\"]+)\"\)", source))
    pose: dict[str, list[float]] = {}
    for statement in top_level_statements(body):
        match = re.fullmatch(
            r"(?:this\.)?setStaticRotationAngle\(this\.([A-Za-z0-9_]+),\s*(.+)\)", statement, re.S
        )
        if not match:
            continue
        try:
            values = [number(item) for item in split_args(match.group(2))]
        except (ValueError, SyntaxError):
            continue
        if len(values) != 3:
            continue
        bone = fields.get(match.group(1), match.group(1))
        current = pose.setdefault(bone, [0.0, 0.0, 0.0])
        for axis in range(3):
            current[axis] += values[axis]
    return pose


def bake_static_pose(geometry: dict, pose: dict[str, list[float]]) -> int:
    baked = 0
    for bone in geometry["minecraft:geometry"][0]["bones"]:
        degrees = pose.get(bone["name"])
        if not degrees:
            continue
        rotation = list(bone.get("rotation", [0, 0, 0]))
        rotation[0] -= degrees[0]
        rotation[1] -= degrees[1]
        rotation[2] += degrees[2]
        bone["rotation"] = clean_vector(rotation)
        baked += 1
    return baked


def eeeab_authored_clips(source: str, model_dir: Path, bones: set[str]) -> dict[str, dict]:
    """The clips a model plays in ``setupAnim``, read from the animation holders."""
    references = sorted(set(re.findall(r"\b(Animation[A-Za-z0-9]+)\.([A-Z][A-Z0-9_]*)\b", source)))
    cache: dict[str, dict[str, dict]] = {}
    clips: dict[str, dict] = {}
    for holder, field in references:
        holder_path = model_dir.parent / "animation" / f"{holder}.java"
        if not holder_path.is_file():
            continue
        if holder not in cache:
            cache[holder] = parse_named_animation_definitions(holder_path, bones)
        clip = cache[holder].get(field.lower())
        if clip is not None:
            clips[field.lower()] = clip
    return clips


def holder_prefix(names: list[str]) -> str | None:
    """The ``<holder>_`` prefix MCreator puts on every clip of an animation holder.

    MCreator names clips after the holder the author created them in
    (``saintp1_idle``, ``ifg_walk``, ``cucumber_idle``), which is not always
    the model class, so the prefix is read from the clip names themselves: the
    token before the first underscore when every clip shares it and it is not
    itself an action word. Holders that mix prefixes (``saintp1_``, ``saint_``,
    ``saintp2_``) return None and fall back to the class stem.
    """
    tokens: set[str] = set()
    for name in names:
        token, separator, rest = name.lower().partition("_")
        if not separator or not rest:
            return None
        tokens.add(token)
    if len(tokens) != 1:
        return None
    token = tokens.pop()
    if any(token in keywords for keywords in STANDARD_KEYWORDS.values()):
        return None
    return f"{token}_"


def mcreator_authored_clips(model: JavaModel, bones: set[str]) -> dict[str, dict]:
    if not model.animation_class:
        return {}
    path = DECOMPILED / model.root / f"{model.animation_class}.java"
    if not path.is_file():
        return {}
    document, _ = parse_srg_animations(path, bones)
    prefixes = tuple(
        prefix for prefix in (holder_prefix(list(document["animations"])), model.clip_prefix) if prefix
    )
    clips: dict[str, dict] = {}
    for name, clip in document["animations"].items():
        lowered = name.lower()
        for prefix in prefixes:
            if lowered.startswith(prefix) and len(lowered) > len(prefix):
                lowered = lowered[len(prefix):]
                break
        clips[lowered] = clip
    return clips


def convert(model: JavaModel) -> dict[str, int]:
    source_path = DECOMPILED / model.root / f"{model.java_class}.java"
    source = source_path.read_text(encoding="utf-8", errors="replace")
    geometry, bones, stats = parse_layer_geometry(source, model.layer_method, model.identifier)

    baked = 0
    if model.namespace == "eeeabsmobs":
        baked = bake_static_pose(geometry, static_pose(source))
        authored = eeeab_authored_clips(source, source_path.parent, bones)
    else:
        authored = mcreator_authored_clips(model, bones)

    # A portable loop stands in only where no authored clip reads as that
    # action, so ensure_round3_animations.py can alias the standard name to the
    # authored clip instead of finding the name taken by a placeholder. A
    # placeholder that moves no bone is left out, except idle, which every
    # model must carry: it stays static only when nothing authored moves.
    portable = animation_document(bones)["animations"]
    animations: dict = {"format_version": "1.8.0", "animations": {}}
    placeholders: list[str] = []
    for name in STANDARD_CLIPS:
        if name in authored or matching_clip(authored, STANDARD_KEYWORDS[name]):
            continue
        if has_channels(portable[name]) or (name == "idle" and not any(map(has_channels, authored.values()))):
            animations["animations"][name] = portable[name]
            placeholders.append(name)
    animations["animations"].update(authored)

    namespace_root = ASSET_ROOT / model.namespace
    write_json(namespace_root / "geo" / f"{model.identifier}.geo.json", geometry)
    write_json(namespace_root / "animations" / f"{model.identifier}.animation.json", animations)
    return stats | {"authored": len(authored), "baked": baked, "portable": placeholders}


def main() -> None:
    failures: list[tuple[JavaModel, Exception]] = []
    summary: dict[str, dict[str, int]] = {}
    for model in MODELS:
        try:
            stats = convert(model)
        except Exception as exc:  # noqa: BLE001 - every failure is reported below
            failures.append((model, exc))
            print(f"FAILED {model.namespace}:{model.identifier}: {exc}")
            continue
        print(
            f"{model.namespace}:{model.identifier}: {stats['bones']} bones, {stats['cubes']} cubes, "
            f"{stats['authored']} authored clips"
            + (f", {stats['baked']} static pose bones" if stats["baked"] else "")
            + (f", portable {'/'.join(stats['portable'])}" if stats["portable"] else "")
        )
        counts = summary.setdefault(model.namespace, {"models": 0, "authored": 0, "idle": 0, "walk": 0, "attack": 0})
        counts["models"] += 1
        counts["authored"] += stats["authored"]
        for name in stats["portable"]:
            counts[name] += 1
    for namespace, counts in summary.items():
        print(
            f"{namespace}: {counts['models']} models, {counts['authored']} authored clips, portable idle for "
            f"{counts['idle']}, walk for {counts['walk']}, attack for {counts['attack']}"
        )
    if failures:
        raise SystemExit(f"{len(failures)} Java models failed conversion")


if __name__ == "__main__":
    main()

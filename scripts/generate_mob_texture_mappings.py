#!/usr/bin/env python3
"""Generate exact default-texture mappings for the bundled GeckoLib models."""

from __future__ import annotations

import re
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
WORK_DIR = PROJECT_DIR.parent
ASSET_ROOT = PROJECT_DIR / "src" / "main" / "resources" / "assets"
OUTPUT = PROJECT_DIR / "src" / "main" / "resources" / "META-INF" / "MOBMODEL_TEXTURES.tsv"

PROJECTS = {
    "legend_of_the_dwerden": WORK_DIR / "legend_of_the_dwerden_decompiled",
    "sculks_of_arda": WORK_DIR / "ardas_sculks_decompiled",
    "luminous_nether": WORK_DIR / "luminous_nether_decompiled",
    "callfromthedepth_": WORK_DIR / "callfromthedepth_decompiled",
    "fromtheshadows": WORK_DIR / "fromtheshadows_decompiled",
    "nethersentinel": WORK_DIR / "nethersentinel_decompiled",
    "skarrier_mobs": WORK_DIR / "skarrier_mobs_decompiled",
    "blighted_beasts": WORK_DIR / "donor_decompiled_round2" / "01_blighted_beasts",
    "deep_dark_regrowth": WORK_DIR / "donor_decompiled_round2" / "02_deep_dark_regrowth",
    "deeperdarker_legacy": WORK_DIR / "donor_decompiled_round2" / "03a_deeper_darker_gecko",
    "deeperdarker": WORK_DIR / "donor_decompiled_round2" / "03b_deeper_darker_latest",
    "sculk_worm": WORK_DIR / "donor_decompiled_round2" / "04_sculk_infection",
    "minecraft_dungend_two_mobs": WORK_DIR / "donor_decompiled_round2" / "05_dungeons_2_mobs",
    "echoes": WORK_DIR / "donor_decompiled_round2" / "06_echoes",
    "nue": WORK_DIR / "donor_decompiled_round2" / "07_nether_update_expanded",
    "infernalexp": WORK_DIR / "donor_decompiled_round2" / "08_infernal_expansion_redux",
    "betternether": WORK_DIR / "donor_decompiled_round2" / "09_betternether",
    "piglinproliferation": WORK_DIR / "donor_decompiled_round2" / "10_piglin_proliferation",
    "nourished_nether": WORK_DIR / "donor_decompiled_round2" / "11_netherific",
    "creatures_expanded": WORK_DIR / "donor_decompiled_round2" / "12_creatures_expanded",
    "ecosystemmod": WORK_DIR / "donor_decompiled_round2" / "13_moss_and_monsters",
    "myceliummire": WORK_DIR / "donor_decompiled_round2" / "14_mycelium_mire",
    "mosslings_muddlings": WORK_DIR / "donor_decompiled_round2" / "15_agers_mosslings",
    "undergarden": WORK_DIR / "donor_decompiled_round2" / "16_the_undergarden",
    "critters_and_cryptids": WORK_DIR / "donor_decompiled_round2" / "17_critters_cryptids",
    "redev_edition_mobs": WORK_DIR / "donor_decompiled_round2" / "18_redev_mobs",
    "wroughtnights": WORK_DIR / "donor_decompiled_round2" / "19_wrought_nights",
    "dungeons_and_combat": WORK_DIR / "donor_decompiled_dnc",
}

MANUAL = {
    "sculks_of_arda:geo/java_sculk_fox.geo.json": "sculks_of_arda:textures/entities/snow_fox.png",
    "sculks_of_arda:geo/vanilla_sculk_fish.geo.json": "sculks_of_arda:textures/entities/sculkfish.png",
    "sculks_of_arda:geo/vanilla_sculk_ghost.geo.json": "sculks_of_arda:textures/entities/sculkghost.png",
    "callfromthedepth_:geo/vanilla_deep_citizen.geo.json": "callfromthedepth_:textures/entities/depths_villager.png",
    "fromtheshadows:geo/bulldrogioth.geo.json": "fromtheshadows:textures/entity/bulldrogioth.png",
    "fromtheshadows:geo/frog.geo.json": "fromtheshadows:textures/entity/frog.png",
    "fromtheshadows:geo/nehemoth.geo.json": "fromtheshadows:textures/entity/nehemoth_retexture.png",
    "fromtheshadows:geo/plague_bearer.geo.json": "fromtheshadows:textures/entity/plague_bearer.png",
    "nethersentinel:geo/nethersentinel.geo.json": "nethersentinel:textures/entities/nethersentinel.png",
    "nethersentinel:geo/netherguardian.geo.json": "nethersentinel:textures/entities/shield.png",

    "blighted_beasts:geo/java_bloater.geo.json": "blighted_beasts:textures/entity/bloater.png",
    "blighted_beasts:geo/java_grand_skitter.geo.json": "blighted_beasts:textures/entity/skitter.png",
    "blighted_beasts:geo/java_reaper.geo.json": "blighted_beasts:textures/entity/reaper.png",
    "blighted_beasts:geo/java_reverb.geo.json": "blighted_beasts:textures/entity/reverb.png",
    "blighted_beasts:geo/java_seer.geo.json": "blighted_beasts:textures/entity/seer.png",
    "blighted_beasts:geo/java_unseen.geo.json": "blighted_beasts:textures/entity/unseen/unseen_0.png",

    "deeperdarker:geo/java_angler_fish.geo.json": "deeperdarker:textures/entity/angler_fish.png",
    "deeperdarker:geo/java_anger_pot.geo.json": "deeperdarker:textures/entity/anger_pot.png",
    "deeperdarker:geo/java_fear_pot.geo.json": "deeperdarker:textures/entity/fear_pot.png",
    "deeperdarker:geo/java_sorrow_pot.geo.json": "deeperdarker:textures/entity/sorrow_pot.png",
    "deeperdarker:geo/java_sludge.geo.json": "deeperdarker:textures/entity/sludge.png",

    "sculk_worm:geo/java_sculk_worm.geo.json": "sculk_worm:textures/worm/sculk_worm_head.png",
    "sculk_worm:geo/java_sculk_segment.geo.json": "sculk_worm:textures/worm/body/sculk_worm_body_0.png",
    "sculk_worm:geo/java_sculk_watcher.geo.json": "sculk_worm:textures/watcher/sculk_watcher_1.png",
    "sculk_worm:geo/java_infected_spawn.geo.json": "sculk_worm:textures/infection/spider/infected_spawn.png",
    "sculk_worm:geo/java_infected_spider.geo.json": "sculk_worm:textures/infection/spider/infector_spider.png",
    "sculk_worm:geo/java_infected_creeper.geo.json": "sculk_worm:textures/infection/creeper/infected_creeper.png",
    "sculk_worm:geo/java_infected_skeleton.geo.json": "sculk_worm:textures/infection/skeleton/infected_skeleton.png",
    "sculk_worm:geo/java_infected_zombie.geo.json": "sculk_worm:textures/infection/zombie/infected_zombie.png",
    "sculk_worm:geo/java_infected_zombie_miner.geo.json": "sculk_worm:textures/infection/zombie/infected_zombie.png",
    "sculk_worm:geo/java_unstable_enderman.geo.json": "minecraft:textures/entity/enderman/enderman.png",
    "sculk_worm:geo/java_infected_bat.geo.json": "sculk_worm:textures/infection/bat/infected_bat.png",

    "minecraft_dungend_two_mobs:geo/java_sculk_eye.geo.json": "minecraft_dungend_two_mobs:textures/entities/sculk_eye.png",
    "minecraft_dungend_two_mobs:geo/java_sculk_hunger.geo.json": "minecraft_dungend_two_mobs:textures/entities/sculk_hunger.png",
    "minecraft_dungend_two_mobs:geo/java_sculklings.geo.json": "minecraft_dungend_two_mobs:textures/entities/sculklings.png",
    "minecraft_dungend_two_mobs:geo/java_the_singer.geo.json": "minecraft_dungend_two_mobs:textures/entities/singer.png",
    "minecraft_dungend_two_mobs:geo/java_soul_creeper.geo.json": "minecraft_dungend_two_mobs:textures/entities/soul_creeper.png",
    "echoes:geo/java_echo_creeper.geo.json": "echoes:textures/entity/creeper/echo_creeper.png",

    "nue:geo/java_cinderworm.geo.json": "nue:textures/entities/cinderworm.png",
    "nue:geo/java_flooze.geo.json": "nue:textures/entities/flooze.png",
    "nue:geo/shroomletanimated.geo.json": "nue:textures/entities/shroomlet_brown.png",
    "nue:geo/vanilla_kral.geo.json": "nue:textures/entities/kral2.png",
    "nue:geo/vanilla_portlin.geo.json": "nue:textures/entities/zombified_piglin.png",
    "nue:geo/vanilla_piglin_villager_test.geo.json": "nue:textures/entities/kral.png",
    "nue:geo/vanilla_exterminator.geo.json": "nue:textures/entities/exterminator.png",
    "nue:geo/vanilla_burned.geo.json": "nue:textures/entities/burned.png",
    "nue:geo/vanilla_crimson_moongus.geo.json": "nue:textures/entities/crimsonmoongus.png",
    "nue:geo/vanilla_frozen_moongus.geo.json": "nue:textures/entities/frozenmoongus.png",
    "nue:geo/vanilla_elder_moongus.geo.json": "nue:textures/entities/eldermoongus.png",
    "nue:geo/vanilla_warped_moongus.geo.json": "nue:textures/entities/warpedmoongus.png",
    "nue:geo/vanilla_dragon_moongus.geo.json": "nue:textures/entities/dragonmoongus.png",

    "betternether:geo/java_firefly.geo.json": "betternether:textures/entity/firefly.png",
    "betternether:geo/java_flying_pig.geo.json": "betternether:textures/entity/flying_pig.png",
    "betternether:geo/java_hydrogen_jellyfish.geo.json": "betternether:textures/entity/jellyfish.png",
    "betternether:geo/java_jungle_skeleton.geo.json": "betternether:textures/entity/jungle_skeleton.png",
    "betternether:geo/java_naga.geo.json": "betternether:textures/entity/naga.png",
    "betternether:geo/java_skull.geo.json": "betternether:textures/entity/skull.png",
    "piglinproliferation:geo/java_piglin_alchemist.geo.json": "piglinproliferation:textures/entity/piglin/alchemist/alchemist.png",
    "piglinproliferation:geo/java_piglin_traveler.geo.json": "piglinproliferation:textures/entity/piglin/traveler/traveler.png",

    "ecosystemmod:geo/java_chomper.geo.json": "ecosystemmod:textures/chomper.png",
    "ecosystemmod:geo/java_bibcrab.geo.json": "ecosystemmod:textures/crabbutbig_new.png",
    "ecosystemmod:geo/java_mossmuncher.geo.json": "ecosystemmod:textures/mossmuncher.png",
    "ecosystemmod:geo/java_mudskipper.geo.json": "ecosystemmod:textures/mudskipper.png",
    "ecosystemmod:geo/java_nibbler.geo.json": "ecosystemmod:textures/nibbler.png",
    "ecosystemmod:geo/java_rockcrab.geo.json": "ecosystemmod:textures/2.png",
    "ecosystemmod:geo/java_treespirit.geo.json": "ecosystemmod:textures/treespirit_new.png",
    "ecosystemmod:geo/vanilla_target.geo.json": "ecosystemmod:textures/target.png",

    "mosslings_muddlings:geo/java_mossling.geo.json": "mosslings_muddlings:textures/entities/mossling1.png",
    "mosslings_muddlings:geo/java_mossling_archer.geo.json": "mosslings_muddlings:textures/entities/mossling_archer.png",
    "mosslings_muddlings:geo/java_mossling_brute.geo.json": "mosslings_muddlings:textures/entities/mossling_brute_texture.png",
    "mosslings_muddlings:geo/java_mossling_brute_permanent.geo.json": "mosslings_muddlings:textures/entities/mossling_brute_wild.png",
    "mosslings_muddlings:geo/java_mossling_hawk.geo.json": "mosslings_muddlings:textures/entities/mossling_hawk_texture.png",
    "mosslings_muddlings:geo/java_mossling_horse.geo.json": "mosslings_muddlings:textures/entities/mossling_horse_texture2.png",
    "mosslings_muddlings:geo/java_mossling_warrior.geo.json": "mosslings_muddlings:textures/entities/mossling_warrior.png",
    "mosslings_muddlings:geo/java_stickling.geo.json": "mosslings_muddlings:textures/entities/stickling.png",

    "myceliummire:geo/brutebonnet.geo.json": "myceliummire:textures/entities/brutebonnet_new.png",
    "myceliummire:geo/cds.geo.json": "myceliummire:textures/entities/cordecepts_new.png",
    "myceliummire:geo/ebombay.geo.json": "myceliummire:textures/entities/ebombay.png",
    "myceliummire:geo/gigahand.geo.json": "myceliummire:textures/entities/gigahand.png",
    "myceliummire:geo/megafung.geo.json": "myceliummire:textures/entities/megafung.png",
    "myceliummire:geo/mushstalke.geo.json": "myceliummire:textures/entities/mushstalk.png",
    "myceliummire:geo/puffshroom.geo.json": "myceliummire:textures/entities/puffshroom.png",
    "myceliummire:geo/slimem.geo.json": "myceliummire:textures/entities/smattackform2.png",
    "myceliummire:geo/susshroom_red.geo.json": "myceliummire:textures/entities/walkingmushromred.png",
    "myceliummire:geo/target.geo.json": "myceliummire:textures/entities/target.png",
    "myceliummire:geo/wangfung.geo.json": "myceliummire:textures/entities/wangfung.png",

    "undergarden:geo/java_brute.geo.json": "undergarden:textures/entity/brute.png",
    "undergarden:geo/java_denizen.geo.json": "undergarden:textures/entity/denizen.png",
    "undergarden:geo/java_denizen2.geo.json": "undergarden:textures/entity/denizen2.png",
    "undergarden:geo/java_dweller.geo.json": "undergarden:textures/entity/dweller.png",
    "undergarden:geo/java_forgotten.geo.json": "undergarden:textures/entity/forgotten.png",
    "undergarden:geo/java_forgotten_guardian.geo.json": "undergarden:textures/entity/forgotten_guardian.png",
    "undergarden:geo/java_gloomper.geo.json": "undergarden:textures/entity/gloomper.png",
    "undergarden:geo/java_greater_dweller.geo.json": "undergarden:textures/entity/greater_dweller.png",
    "undergarden:geo/java_gwib.geo.json": "undergarden:textures/entity/gwib.png",
    "undergarden:geo/java_gwibling.geo.json": "undergarden:textures/entity/gwibling.png",
    "undergarden:geo/java_minion.geo.json": "undergarden:textures/entity/minion.png",
    "undergarden:geo/java_mog.geo.json": "undergarden:textures/entity/mog.png",
    "undergarden:geo/java_muncher.geo.json": "undergarden:textures/entity/muncher.png",
    "undergarden:geo/java_mysterious_pot.geo.json": "undergarden:textures/entity/potguy.png",
    "undergarden:geo/java_nargoyle.geo.json": "undergarden:textures/entity/nargoyle.png",
    "undergarden:geo/java_rotbeast.geo.json": "undergarden:textures/entity/rotbeast.png",
    "undergarden:geo/java_rotbelcher.geo.json": "undergarden:textures/entity/rotbelcher.png",
    "undergarden:geo/java_rotling.geo.json": "undergarden:textures/entity/rotling.png",
    "undergarden:geo/java_rotwalker.geo.json": "undergarden:textures/entity/rotwalker.png",
    "undergarden:geo/java_scintling.geo.json": "undergarden:textures/entity/scintling.png",
    "undergarden:geo/java_smog_mog.geo.json": "undergarden:textures/entity/smog_mog.png",
    "undergarden:geo/java_sploogie.geo.json": "undergarden:textures/entity/sploogie.png",
    "undergarden:geo/java_stoneborn.geo.json": "undergarden:textures/entity/stoneborn.png",

    "redev_edition_mobs:geo/big_beak.geo.json": "redev_edition_mobs:textures/entities/bigbeak_texturefixed.png",
    "redev_edition_mobs:geo/bloated.geo.json": "redev_edition_mobs:textures/entities/bloated_texture.png",
    "redev_edition_mobs:geo/branchling.geo.json": "redev_edition_mobs:textures/entities/branchling_texture.png",
    "redev_edition_mobs:geo/butterfly.geo.json": "redev_edition_mobs:textures/entities/butterfly_texture.png",
    "redev_edition_mobs:geo/funguff.geo.json": "redev_edition_mobs:textures/entities/toxic_funguff_texture.png",
    "redev_edition_mobs:geo/meerkat.geo.json": "redev_edition_mobs:textures/entities/meerkat_texture.png",
    "redev_edition_mobs:geo/mossy_golem.geo.json": "redev_edition_mobs:textures/entities/mossy_golemtexturefixed2.png",
    "redev_edition_mobs:geo/regal_tiger.geo.json": "redev_edition_mobs:textures/entities/regal_tiger_texture.png",
    "redev_edition_mobs:geo/scorpion.geo.json": "redev_edition_mobs:textures/entities/scorpion_texture.png",

    # Preserve the reviewed selections that predate this generator's current
    # fallback scoring. Regeneration must not replace working bundle textures
    # merely because another similarly named donor texture scores higher.
    "callfromthedepth_:geo/agony_soul.geo.json": "callfromthedepth_:textures/entities/bs1.png",
    "callfromthedepth_:geo/citadel_guardian.geo.json": "callfromthedepth_:textures/entities/cwc.png",
    "callfromthedepth_:geo/mushroom10.geo.json": "callfromthedepth_:textures/entities/mhg.png",
    "callfromthedepth_:geo/scream.geo.json": "callfromthedepth_:textures/entities/screamer.png",
    "callfromthedepth_:geo/shield.geo.json": "callfromthedepth_:textures/entities/despair_shield.png",
    "fromtheshadows:geo/crust_armor.geo.json": "fromtheshadows:textures/armor/crust_armor.png",
    "fromtheshadows:geo/diabolium_armor.geo.json": "fromtheshadows:textures/armor/diabolium_armor_re.png",
    "fromtheshadows:geo/item/devil_splitter.geo.json": "fromtheshadows:textures/item/devil_splitter.png",
    "fromtheshadows:geo/item/thirst_for_blood.geo.json": "fromtheshadows:textures/item/thirst_for_blood.png",
    "fromtheshadows:geo/plague.geo.json": "fromtheshadows:textures/armor/plague.png",
    "legend_of_the_dwerden:geo/deep_gaitser.geo.json": "legend_of_the_dwerden:textures/entities/gaitser.png",
    "legend_of_the_dwerden:geo/deep_monolith.geo.json": "legend_of_the_dwerden:textures/entities/deep_monolith_v2.png",
    "legend_of_the_dwerden:geo/deep_salamander.geo.json": "legend_of_the_dwerden:textures/entities/deep_salamander_v2.png",
    "legend_of_the_dwerden:geo/deepborn.geo.json": "legend_of_the_dwerden:textures/entities/deepborn_new.png",
    "legend_of_the_dwerden:geo/dwerden_sensor.geo.json": "legend_of_the_dwerden:textures/entities/dwerden_sensor_v3.png",
    "legend_of_the_dwerden:geo/maw.geo.json": "legend_of_the_dwerden:textures/entities/the_maw_v3_new.png",
    "legend_of_the_dwerden:geo/mite_of_dwerden.geo.json": "legend_of_the_dwerden:textures/entities/mite.png",
    "legend_of_the_dwerden:geo/sculk_bull.geo.json": "legend_of_the_dwerden:textures/entities/sculk_bull_newest.png",
    "legend_of_the_dwerden:geo/sculk_cow.geo.json": "legend_of_the_dwerden:textures/entities/sculk_cow_newest.png",
    "legend_of_the_dwerden:geo/sculk_eye.geo.json": "legend_of_the_dwerden:textures/entities/eye.png",
    "legend_of_the_dwerden:geo/sculk_mannequin.geo.json": "legend_of_the_dwerden:textures/entities/sculk_armor_stand_new.png",
    "legend_of_the_dwerden:geo/sculk_processor.geo.json": "legend_of_the_dwerden:textures/entities/sculk_processor_v2.png",
    "legend_of_the_dwerden:geo/sculk_pusher.geo.json": "legend_of_the_dwerden:textures/entities/pudsh.png",
    "legend_of_the_dwerden:geo/sculk_scout.geo.json": "legend_of_the_dwerden:textures/entities/deep_scout.png",
    "legend_of_the_dwerden:geo/sculk_snail.geo.json": "legend_of_the_dwerden:textures/entities/sculk_snail_v2.png",
    "legend_of_the_dwerden:geo/the_instigator.geo.json": "legend_of_the_dwerden:textures/entities/the_instigator_v2.png",
    "legend_of_the_dwerden:geo/the_staring.geo.json": "legend_of_the_dwerden:textures/entities/staring_mif.png",
    "legend_of_the_dwerden:geo/warden_ghost.geo.json": "legend_of_the_dwerden:textures/entities/ghost_of_warden.png",
    "luminous_nether:geo/cultist_rider.geo.json": "luminous_nether:textures/entities/zombie_cultist_hoglin_rider.png",
    "luminous_nether:geo/ember.geo.json": "luminous_nether:textures/entities/soulember.png",
    "luminous_nether:geo/exeghost.geo.json": "luminous_nether:textures/entities/piglinexecutioner.png",
    "luminous_nether:geo/ghost.geo.json": "luminous_nether:textures/entities/newghost.png",
    "luminous_nether:geo/glider.geo.json": "luminous_nether:textures/entities/gliderorange.png",
    "luminous_nether:geo/mushling.geo.json": "luminous_nether:textures/entities/mushlinanimated.png",
    "luminous_nether:geo/mushlinking.geo.json": "luminous_nether:textures/entities/warpedking.png",
    "luminous_nether:geo/piglin_cultist.geo.json": "luminous_nether:textures/entities/zombiepiglincultist.png",
    "luminous_nether:geo/piglinexecutioner.geo.json": "luminous_nether:textures/entities/basaltexecutioner.png",
    "luminous_nether:geo/piglinghost.geo.json": "luminous_nether:textures/entities/piglin_ghost_transparent.png",
    "sculks_of_arda:geo/sculkchest_-_converted.geo.json": "sculks_of_arda:textures/block/remakesculkchest.png",
    "sculks_of_arda:geo/sculkskeleton.geo.json": "sculks_of_arda:textures/entities/2skeletonsculk.png",

    "dungeons_and_combat:geo/ammit.geo.json": "dungeons_and_combat:textures/entities/ammit_alternative.png",
    "dungeons_and_combat:geo/bloody_cultist.geo.json": "dungeons_and_combat:textures/entities/bloody_cultist.png",
    "dungeons_and_combat:geo/bloody_cultist_defender.geo.json": "dungeons_and_combat:textures/entities/bloodywarden.png",
    "dungeons_and_combat:geo/bloodymancer.geo.json": "dungeons_and_combat:textures/entities/bloodymancer_entity.png",
    "dungeons_and_combat:geo/centinel.geo.json": "dungeons_and_combat:textures/entities/centinel.png",
    "dungeons_and_combat:geo/chained.geo.json": "dungeons_and_combat:textures/entities/chained.png",
    "dungeons_and_combat:geo/chuck.geo.json": "dungeons_and_combat:textures/entities/chuck.png",
    "dungeons_and_combat:geo/corroding_flame_scepter.geo.json": "dungeons_and_combat:textures/item/corroding_flame_scepter.png",
    "dungeons_and_combat:geo/counselor_skull.geo.json": "dungeons_and_combat:textures/entities/counselor_skull.png",
    "dungeons_and_combat:geo/deserter.geo.json": "dungeons_and_combat:textures/entities/deserter_skull.png",
    "dungeons_and_combat:geo/disappointment.geo.json": "dungeons_and_combat:textures/entities/disappointment.png",
    "dungeons_and_combat:geo/ebony_crimson_-_converted.geo.json": "dungeons_and_combat:textures/entities/ebony_crimson.png",
    "dungeons_and_combat:geo/ernos.geo.json": "dungeons_and_combat:textures/entities/ernos.png",
    "dungeons_and_combat:geo/failure.geo.json": "dungeons_and_combat:textures/entities/failure.png",
    "dungeons_and_combat:geo/fairy.geo.json": "dungeons_and_combat:textures/entities/flower_fairy.png",
    "dungeons_and_combat:geo/fairystaffanimated.geo.json": "dungeons_and_combat:textures/item/fairy_staff_animated.png",
    "dungeons_and_combat:geo/flowerfairy.geo.json": "dungeons_and_combat:textures/entities/flower_fairy.png",
    "dungeons_and_combat:geo/fractured_vex.geo.json": "dungeons_and_combat:textures/entities/fractured_vex.png",
    "dungeons_and_combat:geo/ghoul.geo.json": "dungeons_and_combat:textures/entities/ghoul.png",
    "dungeons_and_combat:geo/gigantscorpion.geo.json": "dungeons_and_combat:textures/entities/gigant_scorpion.png",
    "dungeons_and_combat:geo/gravewatcher.geo.json": "dungeons_and_combat:textures/entities/gravewatcher.png",
    "dungeons_and_combat:geo/hermit_witch.geo.json": "dungeons_and_combat:textures/entities/hermit_witch.png",
    "dungeons_and_combat:geo/high_priest.geo.json": "dungeons_and_combat:textures/entities/high_priest.png",
    "dungeons_and_combat:geo/horuso.geo.json": "dungeons_and_combat:textures/entities/horuso.png",
    "dungeons_and_combat:geo/infernus.geo.json": "dungeons_and_combat:textures/entities/infernus_alternative.png",
    "dungeons_and_combat:geo/ladyblaze.geo.json": "dungeons_and_combat:textures/entities/ladyblaze.png",
    "dungeons_and_combat:geo/lilith.geo.json": "dungeons_and_combat:textures/entities/lilith.png",
    "dungeons_and_combat:geo/mimic.geo.json": "dungeons_and_combat:textures/entities/mimic.png",
    "dungeons_and_combat:geo/mummy.geo.json": "dungeons_and_combat:textures/entities/centinel.png",
    "dungeons_and_combat:geo/palehands.geo.json": "dungeons_and_combat:textures/entities/palehands.png",
    "dungeons_and_combat:geo/prisoner_skull.geo.json": "dungeons_and_combat:textures/entities/prisoner_skull.png",
    "dungeons_and_combat:geo/pyroknight.geo.json": "dungeons_and_combat:textures/entities/pyroknight.png",
    "dungeons_and_combat:geo/remanent.geo.json": "dungeons_and_combat:textures/entities/remanent.png",
    "dungeons_and_combat:geo/royal_kamath.geo.json": "dungeons_and_combat:textures/entities/royal_kamath.png",
    "dungeons_and_combat:geo/royal_skull.geo.json": "dungeons_and_combat:textures/entities/royal_skull.png",
    "dungeons_and_combat:geo/sandstone_golem.geo.json": "dungeons_and_combat:textures/entities/sandstone_golem.png",
    "dungeons_and_combat:geo/sanguinescepter.geo.json": "dungeons_and_combat:textures/item/sanguinescepter.png",
    "dungeons_and_combat:geo/scepterofcompensation.geo.json": "dungeons_and_combat:textures/item/scepter_of_compensation.png",
    "dungeons_and_combat:geo/skull.geo.json": "dungeons_and_combat:textures/entities/skull.png",
    "dungeons_and_combat:geo/skull_deserter.geo.json": "dungeons_and_combat:textures/entities/skull_deserter.png",
    "dungeons_and_combat:geo/soleia.geo.json": "dungeons_and_combat:textures/entities/soleia.png",
    "dungeons_and_combat:geo/spell_book.geo.json": "dungeons_and_combat:textures/item/spell_book.png",
    "dungeons_and_combat:geo/sunleia.geo.json": "dungeons_and_combat:textures/entities/human_soleia.png",
    "dungeons_and_combat:geo/sunleia_light_v2.geo.json": "dungeons_and_combat:textures/entities/human_soleia.png",
    "dungeons_and_combat:geo/the_monarch.geo.json": "dungeons_and_combat:textures/entities/the_monarch.png",
    "dungeons_and_combat:geo/theaberrator.geo.json": "dungeons_and_combat:textures/entities/theaberrator.png",
    "dungeons_and_combat:geo/torture.geo.json": "dungeons_and_combat:textures/entities/torture.png",
    "dungeons_and_combat:geo/weak_aberration.geo.json": "dungeons_and_combat:textures/entities/weak_aberration.png",
}
for name in (
    "breacher",
    "dangle",
    "quake",
    "serene",
    "sorcerer",
    "stone_golem",
    "trawler",
    "tunnel_gore",
    "wrought",
    "zombiflore",
):
    MANUAL[f"skarrier_mobs:geo/java_{name}.geo.json"] = f"skarrier_mobs:textures/entities/{name}.png"


def extract_method(source: str, name: str) -> str:
    match = re.search(rf"\b{name}\s*\([^)]*\)\s*\{{", source)
    if not match:
        return ""
    opening = source.find("{", match.start())
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
    return ""


def resources(source: str) -> list[str]:
    found: list[tuple[int, str]] = []
    patterns = [
        re.compile(r"new ResourceLocation\(\s*\"([^\"]+)\"\s*,\s*\"([^\"]+)\"\s*\)"),
        re.compile(r"ResourceLocation\.parse\(\s*(?:\(String\))?\s*\"([^\"]+:[^\"]+)\"\s*\)"),
        re.compile(
            r"ResourceLocation\.fromNamespaceAndPath\(\s*(?:\(String\))?\s*\"([^\"]+)\"\s*,\s*(?:\(String\))?\s*\"([^\"]+)\"\s*\)"
        ),
    ]
    for pattern in patterns:
        for match in pattern.finditer(source):
            if len(match.groups()) == 1:
                value = match.group(1)
            else:
                value = f"{match.group(1)}:{match.group(2)}"
            found.append((match.start(), value))
    return [value for _, value in sorted(found)]


def entity_default_texture(source: str, project_root: Path) -> str | None:
    generic = re.search(r"extends\s+GeoModel<([A-Za-z0-9_]+)>", source)
    if not generic:
        return None
    entity_name = generic.group(1)
    candidates = list(project_root.rglob(f"{entity_name}.java"))
    if not candidates:
        return None
    entity_source = candidates[0].read_text(encoding="utf-8")
    defaults = re.findall(r"TEXTURE\s*,\s*(?:\(Object\))?\s*\"([^\"]+)\"", entity_source)
    return defaults[0] if defaults else None


def dynamic_texture(source: str, method: str, project_root: Path) -> str | None:
    default = entity_default_texture(source, project_root)
    if not default:
        return None
    pair = re.search(
        r"new ResourceLocation\(\s*\"([^\"]+)\"\s*,\s*\"(textures/(?:entity|entities)/)\"\s*\+",
        method,
    )
    if pair:
        return f"{pair.group(1)}:{pair.group(2)}{default}.png"
    combined = re.search(r'\"([^\"]+:textures/(?:entity|entities)/)\"\s*\+', method)
    if combined:
        return f"{combined.group(1)}{default}.png"
    return None


def direct_mappings(project_root: Path) -> dict[str, str]:
    mappings: dict[str, str] = {}
    for path in project_root.rglob("*.java"):
        source = path.read_text(encoding="utf-8", errors="replace")
        if "getModelResource" not in source or "getTextureResource" not in source:
            continue
        model_method = extract_method(source, "getModelResource")
        texture_method = extract_method(source, "getTextureResource")
        models = [item for item in resources(model_method) if ":geo/" in item and item.endswith(".geo.json")]
        if not models:
            continue
        textures = [item for item in resources(texture_method) if ":textures/" in item and item.endswith(".png")]
        texture = textures[-1] if textures else dynamic_texture(source, texture_method, project_root)
        if texture:
            mappings[models[0]] = texture
    return mappings


def normalize(value: str) -> str:
    return "".join(character.lower() for character in value if character.isalnum())


def canonical(value: str) -> str:
    result = normalize(value)
    if result.startswith("model"):
        result = result[5:]
    changed = True
    while changed:
        changed = False
        for suffix in ("model", "rework", "entity", "adapted", "focused", "animation", "converted"):
            if result.endswith(suffix) and len(result) > len(suffix):
                result = result[: -len(suffix)]
                changed = True
    return result.rstrip("0123456789")


def levenshtein(left: str, right: str) -> int:
    previous = list(range(len(right) + 1))
    for row, left_char in enumerate(left, 1):
        current = [row]
        for column, right_char in enumerate(right, 1):
            current.append(
                min(
                    current[-1] + 1,
                    previous[column] + 1,
                    previous[column - 1] + (left_char != right_char),
                )
            )
        previous = current
    return previous[-1]


def auxiliary(name: str) -> bool:
    lower = name.lower()
    return any(
        token in lower
        for token in (
            "particle",
            "_eyes",
            "_layer",
            "_overlay",
            "_mask",
            "glow",
            "fullbright",
            "emissive",
            "specular",
            "_normal",
            "_shield",
            "_projectile",
        )
    ) or lower.endswith("_e")


def score(model_name: str, texture_name: str) -> int:
    model_normal = normalize(model_name)
    texture_normal = normalize(texture_name)
    model_canonical = canonical(model_name)
    texture_canonical = texture_normal.rstrip("0123456789")
    if model_normal == texture_normal:
        result = 10000
    elif model_canonical == texture_canonical:
        result = 9600
    elif (
        len(model_canonical) >= 4
        and len(texture_canonical) >= 4
        and (model_canonical in texture_canonical or texture_canonical in model_canonical)
    ):
        result = 8200 + min(len(model_canonical), len(texture_canonical)) * 1200 // max(
            len(model_canonical), len(texture_canonical)
        )
    else:
        longest = max(len(model_canonical), len(texture_canonical))
        result = 0 if not longest else (longest - levenshtein(model_canonical, texture_canonical)) * 7000 // longest
    if auxiliary(texture_name):
        result -= 5000
    return result


def fallback_mapping(namespace: str, model: Path) -> str | None:
    namespace_root = ASSET_ROOT / namespace
    textures = [
        path
        for path in namespace_root.rglob("*.png")
        if "/textures/entity/" in path.as_posix() or "/textures/entities/" in path.as_posix()
    ]
    if not textures:
        return None
    model_name = model.name.removesuffix(".geo.json")
    best = max(textures, key=lambda path: score(model_name, path.stem))
    relative = best.relative_to(namespace_root).as_posix()
    return f"{namespace}:{relative}"


def main() -> None:
    mappings: dict[str, str] = {}
    for namespace, project_root in PROJECTS.items():
        parsed = direct_mappings(project_root)
        mappings.update(parsed)
        namespace_root = ASSET_ROOT / namespace
        for model in namespace_root.rglob("*.geo.json"):
            relative = model.relative_to(namespace_root).as_posix()
            key = f"{namespace}:{relative}"
            if key not in mappings:
                fallback = fallback_mapping(namespace, model)
                if fallback:
                    mappings[key] = fallback

    # Explicitly reviewed pairs must win over renderer heuristics and fallback
    # name matching. Some donor mods ship several renderer variants for one
    # geometry and the last decompiled class is not always the base entity.
    mappings.update(MANUAL)

    lines = [
        "# Exact default textures for bundled mob models.",
        "# model resource<TAB>texture resource",
    ]
    lines.extend(f"{model}\t{texture}" for model, texture in sorted(mappings.items()))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(mappings)} mappings to {OUTPUT}")
    for namespace in PROJECTS:
        count = sum(key.startswith(f"{namespace}:") for key in mappings)
        print(f"{namespace}: {count}")


if __name__ == "__main__":
    main()

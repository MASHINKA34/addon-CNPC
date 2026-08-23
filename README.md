# CNPC-Geckolib-Addon

An addon for GeckoLib and CustomNPCs that lets you use GeckoLib models for your NPCs,
plus a configurable boss framework built on top of them.

## Installation

This is an ***addon***: install CustomNPCs and GeckoLib first, then drop the jar into the
`mods` folder of the client and the server.

## Features

- Geckolib Model as a new NPC model type, with idle, walk, hurt, attack and death animations
- Script API for driving animations on NPCs and on scripted blocks
- Warden-style sound reaction: NPCs can hear vibrations and investigate or attack the source
- Extra ranged options: custom projectile entity, keep-distance behaviour
- A boss framework with up to 8 health phases, each holding its own animations and abilities:
  - path teleporting, clone summoning, area attack, ranged attack, melee attack,
    fluid spit and a chain hook
  - per-ability target selection (main / nearest / farthest / random)
  - up to three potion effects per attack
  - configurable minion cleanup and a death explosion

## Building

```
gradlew build
```

The jar lands in `build/libs`. The two jars in `lib/` are required and are committed -
see [lib/README.md](lib/README.md).

## A note on the bundled mob models

The addon can ship GeckoLib models, animations and textures taken from other mods
(Cataclysm, Scape and Run: Parasites, Bosses' Rise, Mowzie's Mobs and others) so that NPCs
can wear them. **Those files are not in this repository.** They belong to their respective
authors and are not ours to redistribute, so everything under
`src/main/resources/assets/` except the addon's own `cnpcgeckoaddon/` namespace is
gitignored.

What this means in practice:

- A jar built from a **clean clone** contains the addon's code and GUI but none of the
  borrowed mob models. NPCs set to one of those models will fall back to the
  "model not found" placeholder.
- A jar built from a **working copy that has the asset folders on disk** contains them, and
  behaves exactly as before. The build does not care whether a file is tracked by git.

`META-INF/MOBMODELS_NOTICE.txt` lists where each bundle came from and under which licence,
and `META-INF/MOBMODEL_TEXTURES.tsv` is the model-to-texture mapping the addon generates.
Both are ours and stay tracked.

If you keep such a bundle, keep a backup of `src/main/resources/assets/` somewhere outside
the repository - git will not restore it for you.

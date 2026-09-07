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

## Tests

`gradlew build` runs the unit tests, and so does the GitHub Actions workflow in
`.github/workflows/build.yml` on every push and pull request. They boot FML but no game, so
they cover exactly what can be checked without a world:

- the save round trip of the whole boss configuration, and a reflective sweep asserting that
  every one of `BossPhaseData`'s three hundred odd fields actually reaches the tag - a field
  added to the class and the GUI but not to `readFromNBT` is silently forgotten on the next
  load, and this is what catches it;
- that every number in a boss save comes back clamped, by poisoning each one in turn with
  `Integer.MAX_VALUE` - a setting read straight out of the tag is a cooldown of two billion
  ticks or a scan radius past the world border, neither of which throws;
- that each row of the boss ability table is wired to its own phase setting and its own
  cooldown, which is the mistake a table of twenty near-identical rows invites, and that
  everything the boss can wind up also has something to carry it out - an ability that
  starts and then does nothing is invisible in play;
- the leap's arc, checked against the motion it stands for by stepping vanilla's own fall
  constants tick by tick;
- the take cover geometry: who a shelter covers, which shelters crowd each other out, how
  long the shockwave is drawn for;
- the phase thresholds and the party health scaling - the two sums a fight is decided by;
- the five artwork tables, held to the fallback every one of them promises;
- `TickQueue`'s reentrancy, its per-tick cap and its cancellation rules;
- that every mob bundle with a recorded texture table is also listed in the resolver's
  namespaces, so an imported bundle cannot end up rendering with a stretched npc skin;
- the model-to-texture name scoring;
- that `en_us` and `ru_ru` carry the same keys and that every key the sources name exists.

The gametests under `src/main/java/.../gametest` need a full CustomNPCs server and are not
part of the build.

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
`META-INF/MOBMODEL_TEXTURES.tsv` is the model-to-texture mapping the addon generates,
and `META-INF/MOBMODEL_TEXTURE_OVERRIDES.tsv` is the hand-picked list of models whose
recorded default is not the right skin. All three are ours and stay tracked.

If you keep such a bundle, keep a backup of `src/main/resources/assets/` somewhere outside
the repository - git will not restore it for you.

# Local dependencies

Neither of these mods publishes to a Maven repository, so `build.gradle` picks them up
from this folder through a `flatDir` repository. They are committed on purpose: without
them a fresh clone cannot compile.

| File | Used for | Scope |
| --- | --- | --- |
| `CustomNPCs-Unofficial-NeoForge-1.21.1.20250325.jar` | Every mixin in `mixins.cnpcgeckoaddon.json` targets CustomNPCs, and the GUI screens extend its widget classes. | `implementation` |
| `ars_nouveau-1.21.1-5.13.0.jar` | The two Ars Nouveau crash-fix mixins in `mixins.cnpcgeckoaddon.compat.json`. | `compileOnly` |

GeckoLib is *not* here - it resolves from the Cloudsmith Maven repository declared in
`build.gradle`.

## Updating one of them

Drop the new jar in, delete the old one, and update the matching line in the `dependencies`
block of `build.gradle` (the CustomNPCs entry references the file name without `.jar`).

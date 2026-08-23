# Промт 05 (код) — стили шнура хука: лиана, адские цепи, щупальце, призрачные цепи

## Контекст

Проект `D:\GEYDEV\addon-CNPC` — аддон к CustomNPCs + GeckoLib.
Minecraft 1.21.1, NeoForge 21.1.235, Java 21, GeckoLib 4.9.2. Сборка: `gradlew build`.
Комментарии в коде — на английском, коротко и про «почему», как в соседних файлах.

Файлы:
- `ai/TeleportPathController.java` — абилка «хук»: `tryStartHook` (~650), `performHook` (~679),
  `tickHookPulls` (~725), `drawHookChain` (~768);
- `data/BossPhaseData.java` — настройки фазы, там уже живут все `hook*` поля (+ NBT);
- `data/BossBarStyles.java` — **образец** реестра стилей (record + `normalize` + `values`);
- `client/gui/SubGuiBossHook.java` — экран настройки хука;
- `network/NetworkWrapper.java`, `network/PacketSyncBossBarStyle.java` — образец пакета;
- `registry/RendererRegistry.java` — регистрация клиентских рендереров;
- `lang/en_us.json` + `lang/ru_ru.json`.

Арт рисует отдельный агент (промт 06). Код обязан работать и **без** текстур.

## Что сейчас

Хук тянет жертв к боссу, а «цепь» рисуется сервером как ряд частиц `ParticleTypes.CRIT`
вдоль прямой (`drawHookChain`). Выглядит как пунктир из искр, а не как цепь.

## Задача

Добавить у фазы выбор **стиля шнура** и рисовать настоящий текстурированный шнур между
боссом и жертвой на всё время рывка.

## Реестр стилей

Новый `data/HookCordStyles.java` по образцу `BossBarStyles`:

```java
public record Style(String id, String translationKey, float width, int frames,
                    int frameTicks, boolean flow, boolean glowing, boolean translucent)
```

| id | width (блоки) | frames | frameTicks | flow | glowing | translucent |
|---|---|---|---|---|---|---|
| `particles` (дефолт, текущее поведение) | — | — | — | — | — | — |
| `vine` | 0.25 | 4 | 4 | false | false | false |
| `chain_infernal` | 0.22 | 4 | 3 | true | true | false |
| `tentacle` | 0.35 | 4 | 5 | false | false | false |
| `ghost` | 0.28 | 4 | 4 | true | true | true |

- `flow` — кадр смещается ещё и по номеру сегмента, чтобы шнур «тёк» к жертве.
- `glowing` — рисовать с `LightTexture.FULL_BRIGHT`, иначе брать освещение из мира.
- `translucent` — `RenderType.entityTranslucent`, иначе `RenderType.entityCutoutNoCull`.
- `normalize(id)`, `get(id)`, `values()` — как в `BossBarStyles`; неизвестный id → `particles`.

Пути текстур (контракт с промтом 06, менять нельзя):

```
assets/cnpcgeckoaddon/textures/entity/hook/<id>/cord.png   16 × 64 RGBA
assets/cnpcgeckoaddon/textures/entity/hook/<id>/head.png   16 × 64 RGBA
```

Обе — вертикальный filmstrip из 4 кадров 16×16, кадр 0 сверху. Кадр `cord.png` бесшовно
стыкуется сам с собой по вертикали (сегменты ставятся друг на друга), `head.png` —
наконечник остриём вниз, рисуется один раз у конца жертвы.

## Данные фазы

`BossPhaseData`: поле `hookCordStyle` (String), NBT-ключ `HookCordStyle`, дефолт
`HookCordStyles.PARTICLES`, сеттер прогоняет значение через `HookCordStyles.normalize`.
Старый NBT без ключа = `particles`, то есть у существующих боссов ничего не меняется.

## Сеть

Новый `network/PacketSyncHookCord.java`:

```
int  bossEntityId
int  victimEntityId
String styleId      // writeUtf(..., 64)
int  durationTicks  // 0 = снять шнур немедленно
```

- В `NetworkWrapper` добавить хелпер отправки всем, кто трекает сущность
  (`PacketDistributor.sendToPlayersTrackingEntity(entity, payload)` — проверь точное имя
  метода в NeoForge 21.1). `sendAll` здесь не годится.
- Сервер шлёт пакет:
  - из `performHook` для каждой жертвы — с оставшейся длительностью пулла;
  - из `tickHookPulls`, когда пулл снимается досрочно (дистанция/смерть/удаление) —
    с `durationTicks = 0`;
  - повторный хук по той же жертве просто перезаписывает запись на клиенте.
- Для стиля `particles` пакет не шлётся вовсе — работает старый `drawHookChain`.

## Клиентский рендер

`client/renderer/HookCordRenderer.java`, `@EventBusSubscriber(value = Dist.CLIENT)`:

- слушает `RenderLevelStageEvent`; стадия — `AFTER_ENTITIES` для непрозрачных стилей,
  `AFTER_TRANSLUCENT_BLOCKS` для `ghost` (можно рисовать всё в одной стадии, если так
  корректнее — тогда объясни выбор комментарием);
- хранит список активных шнуров `{bossId, victimId, styleId, expiresAtClientTick}`;
  запись выкидывается по истечении, при `durationTicks = 0` и когда любая из сущностей
  пропала из мира; полная очистка на `ClientPlayerNetworkEvent.LoggingOut`;
- геометрия:
  - старт — глаза босса (`boss.getEyePosition(partialTick)`, минус ~0.2 по Y, как в
    текущем `drawHookChain`), конец — середина жертвы
    (`victim.getPosition(partialTick).add(0, victim.getBbHeight() * 0.5, 0)`);
  - обе точки интерполировать по `partialTick`, иначе шнур будет дёргаться;
  - сегментов `Mth.clamp((int)(length * 2), 2, 64)` — как сейчас у частиц;
  - лёгкое провисание: смещать промежуточные точки вниз на
    `sag * sin(PI * t) * length`, где `sag` порядка 0.02 для цепей и 0.05 для лианы и
    щупальца (можно вынести в `Style`);
  - каждый сегмент — билборд на камеру: `right = dir.cross(toCamera).normalize().scale(width / 2)`;
  - UV: `u` от 0 до 1 поперёк, `v` — ровно диапазон одного кадра
    (`v0 = frame / frames`, `v1 = (frame + 1) / frames`); кадр
    `frame = ((clientTick / frameTicks) + (flow ? segmentIndex : 0)) % frames`;
  - позиции переводить в координаты камеры (`poseStack.translate(-camX, -camY, -camZ)`),
    вершины гнать через `MultiBufferSource` из события;
  - `head.png` — один квад у конца жертвы, развёрнутый по направлению шнура.
- Если текстура стиля не найдена в `resourceManager`, откатиться на частицы (клиентская
  `level.addParticle(ParticleTypes.CRIT, ...)`) и один раз написать в лог — мод не должен
  падать или мигать фиолетово-чёрным.

## GUI

В `SubGuiBossHook` добавить переключатель стиля шнура рядом с существующим
`HOOK_MODE_BUTTON`. Экран уже забит под завязку: строки идут с `guiTop + 18` шагом 21 до
`Done` на `guiTop + 232`. Свободной строки нет — раздели строку «тип хука» на две кнопки
по 65 px (тип и стиль шнура) либо перекомпонуй так, чтобы всё влезло и читалось.

Кнопка циклическая (`GuiButtonNop` с `String[]` лейблов), индекс ↔ id мапится через
`HookCordStyles.values()`.

## Звук

В `performHook` звук сейчас жёстко `SoundEvents.CHAIN_PLACE`. Сделать по стилю
(имена проверь по мапленным исходникам 1.21.1, не по памяти):
`vine` — что-то травяное/шуршащее, `chain_infernal` — `CHAIN_PLACE` с низким питчем,
`tentacle` — слизь/шлепок, `ghost` — `SOUL_ESCAPE`. Для `particles` оставить как есть.

## Локализация (оба файла)

```
cnpcgeckoaddon.boss.hook_cord            "Cord style"       / "Стиль шнура"
cnpcgeckoaddon.boss.hook_cord.particles  "Sparks (default)" / "Искры (по умолчанию)"
cnpcgeckoaddon.boss.hook_cord.vine       "Vine"             / "Лиана"
cnpcgeckoaddon.boss.hook_cord.chain_infernal "Infernal chain" / "Адская цепь"
cnpcgeckoaddon.boss.hook_cord.tentacle   "Tentacle"         / "Щупальце"
cnpcgeckoaddon.boss.hook_cord.ghost      "Spectral chain"   / "Призрачная цепь"
```

## Definition of done

- `gradlew build` проходит.
- Проверка в игре (шаги записать в ответ):
  1. Хук со стилем `particles` выглядит ровно как раньше (регресс не допускается).
  2. Каждый из четырёх стилей: шнур видно от босса до жертвы всё время рывка, он не
     отстаёт от движения (проверить на бегу и в прыжке), исчезает по окончании пулла.
  3. Режим `cinch` с 3+ жертвами — три отдельных шнура, ни один не «залипает».
  4. Жертва умерла/вышла из мира посреди рывка — шнур пропал, в консоли пусто.
  5. Второй игрок, который смотрит со стороны, видит те же шнуры (пакет уходит всем,
     кто трекает босса, а не только жертве).
  6. Без файлов текстур (до промта 06) — фолбэк на частицы, без спама в лог.

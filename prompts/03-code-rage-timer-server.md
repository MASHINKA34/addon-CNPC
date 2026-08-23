# Промт 03 (код, сервер) — рейдж-фаза по таймеру: все статы ×2, кроме ХП

## Контекст

Проект `D:\GEYDEV\addon-CNPC` — аддон к CustomNPCs + GeckoLib.
Minecraft 1.21.1, NeoForge 21.1.235, Java 21, GeckoLib 4.9.2. Сборка: `gradlew build`.
Комментарии в коде — на английском, коротко и про «почему», как в соседних файлах.

Файлы:
- `ai/TeleportPathController.java` — серверный тик босса (фазы, абилки, бар босса);
- `data/TeleportPathData.java` — настройки босса (+ NBT), сюда идут новые поля;
- `data/BossPhaseData.java` — настройки фазы (читать, **не** мутировать);
- `network/NetworkWrapper.java` + `network/PacketSyncBossBarStyle.java` — образец пакета;
- `client/gui/SubGuiTeleportPath.java` — главный экран босса;
- `lang/en_us.json` + `lang/ru_ru.json`.

Зависимость: после промта 01 (там появляется единая точка `endEncounter(...)`).
Рисование таймера в HUD — отдельный промт 04, он потребляет пакет из этой задачи.

## Зачем это нужно (постановка от заказчика)

Сейчас данж можно пройти в соло: наесться золотых яблок и перетанковать босса чем угодно.
Нужен enrage-таймер: с начала боя тикает обратный отсчёт, и когда он истекает, босс
**навсегда до конца энкаунтера** входит в ярость — все его боевые характеристики
удваиваются, кроме здоровья. Таймер должен быть виден игрокам (это промт 04).

## Новые поля в `TeleportPathData` (+ NBT + геттеры/сеттеры с `Mth.clamp`)

| поле | NBT-ключ | дефолт | диапазон |
|---|---|---|---|
| `rageEnabled` | `GeckoBossRageEnabled` | `false` | — |
| `rageDelayTicks` | `GeckoBossRageDelay` | 3600 (3 мин) | 100..72000 |
| `rageMultiplierPercent` | `GeckoBossRageMultiplier` | 200 | 100..1000 |
| `rageAnimation` | `GeckoBossRageAnimation` | `""` | — |
| `rageLockTicks` | `GeckoBossRageLock` | 40 | 0..1200 |

Старый NBT без ключей = дефолты (обратная совместимость обязательна).

## Логика в `TeleportPathController`

### Таймер

- Отсчёт стартует в момент **начала энкаунтера** — первый тик, когда появилась боевая цель
  (`hasCombatTarget()`), а энкаунтер до этого был сброшен. Запомнить `encounterStartedAt`.
- `rageAt = encounterStartedAt + data.getRageDelayTicks()`.
- Пока боевой цели нет, таймер **не тикает и не сбрасывается** — он замирает; полный
  сброс происходит только в `endEncounter(...)` (это ровно тот же момент, когда
  откатывается фаза). Так игрок не сбрасывает enrage коротким забегом за угол.
- По истечении: `rageActive = true`, проиграть `data.getRageAnimation()`,
  `busyUntil = Math.max(busyUntil, gameTime + data.getRageLockTicks())`,
  один раз сыграть `SoundEvents.ENDER_DRAGON_GROWL` (`SoundSource.HOSTILE`) и высыпать
  `ParticleTypes.ANGRY_VILLAGER` / `ParticleTypes.LARGE_SMOKE` вокруг босса.
- `rageActive` живёт до конца энкаунтера: смена фаз его **не** снимает.
- Публичные геттеры для HUD и для событий: `boolean isRageActive()`,
  `int rageTicksLeft()` (0, если рейдж уже активен или выключен),
  `int rageTotalTicks()`.

### Что именно удваивается

Множитель `m = data.getRageMultiplierPercent() / 100.0`. **Ни в коем случае не мутировать
`BossPhaseData`** — это persisted-NBT, изменение сохранится навсегда и удвоение станет
накопительным. Вместо этого завести в контроллере два хелпера и прогнать через них места
чтения:

```java
private int rageUp(int value)   // round(value * m), min 1  — урон, отбрасывание, сила рывка
private int rageDown(int value) // max(1, round(value / m)) — кулдауны
```

Масштабируется:
- урон: area / ranged / melee / fluid spit / hook;
- кулдауны всех абилок (`*CooldownTicks`) и интервал телепортов (`getTeleportMin/MaxDelayTicks`);
- отбрасывание area и melee, `hookPullStrength`;
- атрибуты сущности (см. ниже).

**Не** масштабируется, и это осознанно (напиши это комментарием в коде и подсказкой в GUI):
- максимальное и текущее здоровье;
- `*ActionDelayTicks` — они синхронизированы с длиной анимации, ускорение рассинхронит
  анимацию и удар;
- радиусы и дальности (`*Range`, `*Radius`), длительности эффектов, количество и лимит
  миньонов, длительность пулла хука — иначе меняется геометрия арены, а не «сила» босса.

### Атрибуты

На старте рейджа навесить transient-модификаторы, на `endEncounter`/`reset`/смерти — снять:

- `Attributes.MOVEMENT_SPEED` и `Attributes.ATTACK_DAMAGE`;
- операция `MULTIPLY_TOTAL`, значение `m - 1`;
- один и тот же `ResourceLocation` id, например `cnpcgeckoaddon:boss_rage`
  (в 1.21.1 `AttributeModifier` идентифицируется `ResourceLocation`, а не UUID —
  проверь сигнатуру по мапленным исходникам);
- снятие идемпотентно (`removeModifier` на отсутствующем модификаторе не должно падать),
  и обязано отрабатывать при выгрузке мира, иначе бонус утечёт в сохранённый NBT сущности.

## Пакет синхронизации (контракт для промта 04 — не менять произвольно)

Новый `network/PacketSyncBossTimer.java` по образцу `PacketSyncBossBarStyle`
(тот же `NetworkWrapper.typeOf(...)`, регистрация в `NetworkWrapper#register`
через `registerPacket(..., Consumer)` = commonToClient):

```
UUID eventId       // id ServerBossEvent-а босса, как в PacketSyncBossBarStyle
int  remainingTicks// сколько осталось до рейджа; 0, если рейдж уже активен
int  totalTicks    // полная длина отсчёта, для доли заполнения
byte state         // 0 = обычный отсчёт, 1 = рейдж активен, 2 = неуязвимая фаза, 3 = таймера нет
```

- `state = 2` — это неуязвимая фаза из промта 02: тогда `remainingTicks/totalTicks`
  описывают её остаток. Если промт 02 ещё не влит — поле всё равно объявить и слать 0/3.
- Слать только участникам бара (`bossEvent.getPlayers()`), не `sendAll`.
- Слать: при изменении `state`, при добавлении нового игрока в бар и не чаще чем раз в
  5 тиков при обычном отсчёте. Клиент между пакетами досчитывает сам.
- Клиентский приём — через мост в стиле `network/BossBarStyleClientBridge.java`
  (`BossTimerClientBridge`), чтобы серверный класс не тянул за собой клиентские классы.
  Обработчик на стороне клиента ставит промт 04; здесь достаточно моста-заглушки,
  который просто хранит последнее значение.

## GUI

Новый экран `client/gui/SubGuiBossRage.java` по образцу `SubGuiBossExplosion.java`:
вкл/выкл, задержка в тиках (плюс лейбл-подсказка «20 тиков = 1 секунда»), множитель в
процентах, поле анимации с кнопкой выбора (`GuiStringSelection` + `BossAnimationGuiUtil`,
как сделано для `TRANSITION_ANIMATION_FIELD` в `SubGuiTeleportPath`), lock-тики,
подсказка о том, что именно множится.

Кнопку добавить в `SubGuiTeleportPath`. Там сейчас ряды кнопок на `guiTop + 164/186/208`
и `Done` на `guiTop + 230` — свободного места нет, поэтому положи кнопку рейджа рядом с
кнопкой бара босса (две по 114 px в строке `guiTop + 208`).

## Локализация (оба файла)

```
cnpcgeckoaddon.boss.rage_settings   "Enrage timer..."     / "Таймер ярости..."
cnpcgeckoaddon.boss.rage_title      "Enrage"              / "Ярость"
cnpcgeckoaddon.boss.rage_enabled    "Enrage on timer"     / "Ярость по таймеру"
cnpcgeckoaddon.boss.rage_delay      "Time to enrage (ticks)" / "Время до ярости (тики)"
cnpcgeckoaddon.boss.rage_multiplier "Stat multiplier (%)" / "Множитель статов (%)"
cnpcgeckoaddon.boss.rage_anim       "Enrage animation"    / "Анимация ярости"
cnpcgeckoaddon.boss.rage_lock       "Lock after enrage (ticks)" / "Блокировка после ярости (тики)"
cnpcgeckoaddon.boss.rage_hint       "Doubles damage, attack rate, knockback and speed. Health is untouched."
                                    / "Множит урон, скорость атак, отбрасывание и скорость. Здоровье не трогает."
cnpcgeckoaddon.boss.rage_active     "RAGE"                / "ЯРОСТЬ"
```

## Definition of done

- `gradlew build` проходит.
- Ручной прогон (шаги записать в ответ):
  1. Босс с `rage_enabled = yes`, `rage_delay = 200`, множитель 200%.
  2. Сагрить, засечь: через 10 секунд — анимация/звук/партиклы, дальше босс бьёт заметно
     чаще и больнее; максимальное HP при этом не изменилось (`/data get entity`).
  3. Убежать, дождаться сброса энкаунтера, вернуться — отсчёт начался заново, ярость снята,
     модификаторы атрибутов исчезли (проверить `/attribute <босс> minecraft:movement_speed modifier value get`).
  4. Перезаход в мир во время ярости не должен оставлять двойную скорость навсегда.
  5. Проверить, что настройки босса в NBT не «удвоились» после нескольких боёв:
     открыть GUI фазы, урон абилок остался исходным.
- В ответе перечисли точный список мест, где применён `rageUp`/`rageDown`.

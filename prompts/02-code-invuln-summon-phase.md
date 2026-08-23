# Промт 02 (код) — фаза неуязвимости: босс только призывает приспешников

## Контекст

Проект `D:\GEYDEV\addon-CNPC` — аддон к CustomNPCs + GeckoLib.
Minecraft 1.21.1, NeoForge 21.1.235, Java 21, GeckoLib 4.9.2. Сборка: `gradlew build`.
Комментарии в коде — на английском, коротко и про «почему», как в соседних файлах.

Ключевые файлы:
- `ai/TeleportPathController.java` — серверный тик босса, фазы, все абилки;
- `data/BossPhaseData.java` — настройки одной фазы (+ NBT);
- `data/TeleportPathData.java` — настройки босса целиком (+ NBT);
- `ai/BossDeathEvents.java` — обработчики событий NeoForge для боссов;
- `ai/BossMinionUtil.java` — учёт и зачистка призванных клонов;
- `client/gui/SubGuiBossPhase.java` — меню фазы, из него открываются экраны абилок;
- `lang/en_us.json` + `lang/ru_ru.json` — обе.

Зависимость: желательно после промта 01 (там появляется `endEncounter(...)`, к которому
нужно подцепиться). Если 01 ещё не влит — сделай сброс состояния в существующем `reset()`
и в ветке `encounterOver` внутри `updatePhase`, и напиши об этом в ответе.

## Задача

Фазу можно пометить как **неуязвимую**. Пока босс в ней:
- он не получает урона;
- из всех абилок ему доступен **только призыв приспешников**;
- фаза заканчивается по таймеру и/или когда все призванные приспешники мертвы;
- после её окончания босс переходит в **следующую** фазу и снова уязвим.

## Важная деталь архитектуры (не пропустить)

Фазы сейчас выбираются исключительно по проценту здоровья
(`TeleportPathData#resolvePhaseIndex`). Неуязвимый босс не теряет HP, значит сам по себе он
из такой фазы **никогда не выйдет**. Нужен явный «пол» фазы:

- добавить в контроллер `private int forcedPhaseFloor;`
- в `updatePhase`: `highestPhaseReached = Math.max(healthPhase, forcedPhaseFloor)` (внутри
  боя по-прежнему домешивается `Math.max` с предыдущим значением);
- когда неуязвимая фаза завершилась: `forcedPhaseFloor = Math.min(index + 1, data.getPhaseCount() - 1)`;
- при завершении энкаунтера `forcedPhaseFloor = 0`;
- если неуязвимая фаза — последняя, босс просто становится уязвимым и остаётся в ней
  (флаг «эта фаза уже отработана в текущем энкаунтере»).

## Новые поля в `BossPhaseData` (+ NBT + геттеры/сеттеры с `Mth.clamp`)

| поле | NBT-ключ | дефолт | диапазон |
|---|---|---|---|
| `invulnerableEnabled` | `InvulnerableEnabled` | `false` | — |
| `invulnerableEndMode` | `InvulnerableEndMode` | 2 | 0..3 |
| `invulnerableDurationTicks` | `InvulnerableDurationTicks` | 200 | 20..12000 |
| `invulnerableAllowTeleport` | `InvulnerableAllowTeleport` | `false` | — |
| `invulnerableSummonImmediately` | `InvulnerableSummonImmediately` | `true` | — |

`invulnerableEndMode` — константы и `String[] INVULNERABLE_END_LABELS` рядом с
существующими `HOOK_MODE_*`:
`0 = TIMER` (только по таймеру), `1 = MINIONS_DEAD` (пока не перебьют всех),
`2 = TIMER_OR_MINIONS` (что раньше), `3 = TIMER_AND_MINIONS` (оба условия).

Режимы с `MINIONS_DEAD` требуют, чтобы приспешники успели появиться: условие «все мертвы»
проверяется только после того, как в этой фазе прошёл хотя бы один успешный призыв.
Если у фазы `canSummon() == false`, неуязвимость обязана вести себя как чистый `TIMER`,
иначе босс залипнет навсегда.

## Логика в `TeleportPathController`

- Состояние: `invulnerableUntil` (тик окончания), `invulnerableSummonedOnce`,
  `invulnerablePhaseIndex`. Всё это сбрасывается в `reset()` / `endEncounter(...)`.
- При входе в фазу с `invulnerableEnabled` (в том же месте, где сейчас играет
  `phaseTransitionAnimation`): выставить `invulnerableUntil = gameTime + duration`,
  если `invulnerableSummonImmediately` — обнулить `nextSummonAt`, чтобы призыв пошёл сразу
  после `busyUntil`.
- Публичный `boolean isInvulnerable()` — контроллер использует его сам, обработчик урона и
  HUD (промт 04) читают его снаружи. Плюс `int invulnerableTicksLeft()`.
- В `tryStartDueAbility`: при неуязвимости пропускать всё, кроме `tryStartSummon`
  (и телепорта, если `invulnerableAllowTeleport`). Проще всего — ранний выход в
  `tryStartDueAbility` и отдельная ветка вызова `tryStartSummon`. Не забудь: телепорт
  запускается ещё и выше по `tick()`, отдельной веткой перед `tryStartDueAbility`.
- Уже летящие хуки и активные пуллы (`activePulls`) добивают свой цикл — их не трогать.
- Проверка выхода из фазы каждый тик, по выбранному режиму. При выходе — `forcedPhaseFloor`
  (см. выше), `cancelPendingAndSchedules()`, проиграть `data.getPhaseTransitionAnimation()`
  и поставить `busyUntil = gameTime + data.getPhaseTransitionLockTicks()`.

## Отмена урона

В `ai/BossDeathEvents.java` добавить обработчик `LivingIncomingDamageEvent`
(проверь точное имя события и порядок пайплайна урона в NeoForge 21.1 по мапленным
исходникам, а не по памяти) — по образцу уже существующих обработчиков:
достать `TeleportPathController` через `IBossController`, спросить `isInvulnerable()`,
отменить событие.

- **Не отменять** урон с тегом `DamageTypeTags.BYPASSES_INVULNERABILITY` — иначе `/kill`
  перестанет работать и босс станет неубиваемым при отладке.
- Отменённый удар всё равно должен **засчитывать игрока в участники боя**: сейчас это
  делает `onLivingDamage(LivingDamageEvent.Post)` через `trackParticipant`, а после отмены
  он не сработает. Продублировать вызов `trackParticipant` в новом обработчике.
- Фидбек: не чаще раза в 5 тиков — `SoundEvents.SHIELD_BLOCK` и щепотка
  `ParticleTypes.ENCHANT` вокруг босса. Спамить на каждый удар нельзя.

## GUI

Новый экран `client/gui/SubGuiBossInvulnerable.java` по образцу `SubGuiBossHook.java`
(тот же `GuiBasic`, `setBackground("menubg.png")`, 256×256, `ITextfieldListener`,
`applyFields()` в `unFocused` и `close`).

Поля: вкл/выкл (`GuiButtonYesNo`), режим окончания (`GuiButtonNop` с массивом лейблов),
длительность в тиках, «разрешить телепорты», «призвать сразу», подсказка-лейбл.

Кнопку на него добавить в `SubGuiBossPhase`. Там сейчас 7 кнопок шириной 234 с шагом 27
от `guiTop + 46` до `guiTop + 208`, и `Done` на `guiTop + 234` — свободной строки нет.
Перестроить в две колонки по 114 px (`guiLeft + 8` и `guiLeft + 128`), чтобы влезла
восьмая кнопка и осталось место под `Done`. Ничего не должно наезжать.

## Локализация (en_us + ru_ru, одинаковый набор ключей)

```
cnpcgeckoaddon.boss.invulnerable_settings      "Immune phase..."       / "Фаза неуязвимости..."
cnpcgeckoaddon.boss.invulnerable_phase         "Immune phase - phase"  / "Фаза неуязвимости — фаза"
cnpcgeckoaddon.boss.invulnerable_enabled       "Boss takes no damage"  / "Босс не получает урона"
cnpcgeckoaddon.boss.invulnerable_end_mode      "Ends when"             / "Заканчивается"
cnpcgeckoaddon.boss.invulnerable_end_timer     "Timer runs out"        / "По таймеру"
cnpcgeckoaddon.boss.invulnerable_end_minions   "All minions are dead"  / "Когда убиты все приспешники"
cnpcgeckoaddon.boss.invulnerable_end_either    "Timer or minions"      / "Таймер или приспешники"
cnpcgeckoaddon.boss.invulnerable_end_both      "Timer and minions"     / "Таймер и приспешники"
cnpcgeckoaddon.boss.invulnerable_duration      "Duration (ticks)"      / "Длительность (тики)"
cnpcgeckoaddon.boss.invulnerable_teleport      "Allow teleports"       / "Разрешить телепорты"
cnpcgeckoaddon.boss.invulnerable_summon_now    "Summon immediately"    / "Призвать сразу"
cnpcgeckoaddon.boss.invulnerable_hint          "Only the summon ability runs. The phase then advances to the next one."
                                               / "Работает только призыв. После неё босс переходит в следующую фазу."
```

Формулировки можно улучшить, но набор ключей обязан совпадать в обоих файлах.

## Definition of done

- `gradlew build` проходит.
- Ручной прогон (шаги записать в ответ):
  1. Босс на 3 фазы: фаза 2 (порог 66%) — неуязвимая, режим `TIMER_OR_MINIONS`, 200 тиков,
     призыв включён, `MinionCloneName` заполнен.
  2. Сбить босса до 66% — он перестаёт получать урон (видно по неподвижному хп-бару),
     призывает клонов, других абилок не использует.
  3. Перебить клонов — фаза заканчивается досрочно, босс снова получает урон
     и переходит на фазу 3 (даже если HP всё ещё 66%).
  4. Повтор с режимом `TIMER`: фаза кончается ровно через заданное число тиков.
  5. `/kill` по боссу в неуязвимой фазе — босс умирает.
  6. Убежать во время неуязвимой фазы, дождаться сброса энкаунтера, вернуться:
     босс в фазе 1 и уязвим (проверка, что состояние действительно сброшено).
  7. Фаза без настроенного клона и с режимом `MINIONS_DEAD` — босс не залипает навсегда.

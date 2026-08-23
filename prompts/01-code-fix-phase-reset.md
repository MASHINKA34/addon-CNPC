# Промт 01 (код) — фаза босса не сбрасывается при потере агра

## Контекст

Проект `D:\GEYDEV\addon-CNPC` — аддон к CustomNPCs + GeckoLib.
Minecraft 1.21.1, NeoForge 21.1.235, Java 21, GeckoLib 4.9.2. Сборка: `gradlew build`.
Комментарии в коде — на английском, в тоне соседнего кода (короткое «почему», не «что»).

Серверная логика босса целиком в
`src/main/java/com/goodbird/cnpcgeckoaddon/ai/TeleportPathController.java`.
Настройки на босса — `data/TeleportPathData.java`, настройки на фазу — `data/BossPhaseData.java`,
GUI — `client/gui/SubGuiTeleportPath.java` и `client/gui/SubGuiBoss*.java`,
строки — `assets/cnpcgeckoaddon/lang/en_us.json` + `ru_ru.json` (держать синхронными).

## Баг

Игрок сагрил босса, довёл его до 2-3 фазы, убежал/умер. Босс возвращается в покой, но
**остаётся в последней фазе**: следующий бой начинается сразу с поздних абилок и поздних
таймингов. Ожидание: энкаунтер закончился — босс полностью откатился в фазу 1.

## Что уже есть (читать перед правкой)

- `TeleportPathController#updatePhase` (около строки 203) уже пытается это делать:
  считает `outOfCombatSince`, через `COMBAT_RESET_TICKS = 100` (5 секунд) объявляет
  `encounterOver` и присваивает `highestPhaseReached = healthPhase`.
- `healthPhase` берётся из `data.resolvePhaseIndex(healthPercent())`.
- `hasCombatTarget()` (около строки 304) — единственное условие «бой идёт»:
  `npc.getTarget() != null && target.isAlive()`.
- `updateNearestPlayerTarget` (около строки 250) отпускает цель, только если включён
  `targetNearestPlayer` (по умолчанию **выключен**) и выключен `keepTargetOutOfRange`.
- `reset()` (около строки 1187) — полный сброс, но вызывается только когда босс мёртв,
  удалён или у него выключен `TeleportPathData.isEnabled()`.

## Две причины, которые надо подтвердить и починить

1. **Откат фазы завязан на здоровье.** Даже когда `encounterOver == true`, фаза считается
   от текущего HP. Босс не лечится, HP осталось 30% → `resolvePhaseIndex` снова вернёт
   позднюю фазу. Сброс не происходит никогда, если у босса нет регена.
2. **`encounterOver` часто вообще не наступает.** `npc.getTarget()` не сбрасывается, когда
   игрок просто убежал: пока цель жива где угодно в мире, `hasCombatTarget()` возвращает
   `true`, `outOfCombatSince` каждый тик обнуляется. Своя проверка «отвязки» (leash)
   отсутствует.

Сначала подтверди обе гипотезы (временный лог в `updatePhase`: `healthPhase`,
`currentPhase`, `hasCombatTarget()`, `outOfCombatSince`), потом правь. Временный лог убрать.

## Что сделать

### 1. `hasCombatTarget()` — честная проверка боя

Цель считается боевой, только если она:
- жива и не `isRemoved()`;
- если это игрок — не спектатор и не в креативе;
- находится не дальше `data.getTargetSearchRadius() * 1.5` от босса (гистерезис, чтобы
  бой не «мигал» на границе радиуса).

Если цель не проходит проверку — считать, что боя нет. Саму `npc.setTarget(null)` дёргать
осторожно: CustomNPCs-овый AI может выставить цель обратно, поэтому контроллер опирается
на собственную оценку, а цель сбрасывает только при завершении энкаунтера (см. п. 2).

### 2. Единая точка `endEncounter(ServerLevel level, TeleportPathData data)`

Вынести весь откат в один метод — его будут расширять соседние задачи (неуязвимая фаза,
рейдж-таймер), поэтому он должен быть единственным местом сброса боевого состояния:

- `npc.setTarget(null)`;
- `currentPhase = 0`, `highestPhaseReached = 0`;
- `cancelPendingAndSchedules()`, `activePulls.clear()`, `busyUntil = 0`;
- зачистка миньонов (уже есть, `data.isClearMinionsOnReset()`);
- `hideBossBar()` + очистка `bossBarParticipants`;
- если `data.isResetHeal()` — вылечить босса до `getMaxHealth()`;
- если `data.isResetReturn()` — телепорт на позицию, записанную в `activate()`
  (`lockedX/lockedZ` + Y на момент старта энкаунтера);
- метод идемпотентен: повторный вызов на уже сброшенном боссе ничего не делает
  (флаг `encounterResetDone`, снимается при первом тике с боевой целью).

`updatePhase` больше **не** должен пересчитывать фазу от здоровья при `encounterOver` —
он вызывает `endEncounter`, а внутри боя фаза по-прежнему только растёт.

### 3. Новые настройки в `TeleportPathData` (+ NBT + GUI + lang)

| поле | NBT-ключ | дефолт | диапазон |
|---|---|---|---|
| `resetTicks` | `GeckoBossResetTicks` | 100 | 20..12000 |
| `resetHeal` | `GeckoBossResetHeal` | `true` | — |
| `resetReturn` | `GeckoBossResetReturn` | `false` | — |

`COMBAT_RESET_TICKS` заменить на `data.getResetTicks()`. Старый NBT без ключей = дефолты.

GUI: положить их в `client/gui/SubGuiBossTargeting.java` (там уже настройки агра) отдельной
группой, либо, если места нет, завести `SubGuiBossReset` и кнопку на него в
`SubGuiTeleportPath`. Экран 256×256, кнопки не должны наезжать друг на друга.

Ключи локализации (en + ru):
`cnpcgeckoaddon.boss.reset_ticks`, `...reset_heal`, `...reset_return`,
`...reset_hint` («Сброс энкаунтера: фаза, миньоны и таймеры откатываются.»).
Существующая подсказка `cnpcgeckoaddon.boss.minions_reset_hint` жёстко говорит про «5s» —
переписать так, чтобы не врать про настраиваемое значение.

## Definition of done

- `gradlew build` проходит.
- Ручной прогон на сервере/в одиночке, шаги записать в ответ:
  1. Босс с 3 фазами и разными абилками по фазам, `combat_only = yes`.
  2. Сагрить, снести до фазы 3, убежать за радиус агра, подождать `resetTicks`.
  3. Босс лечится до полного (если `reset_heal = yes`), бар исчезает, миньоны зачищены.
  4. Повторный агр — босс начинает с фазы 1 (проверить по абилкам/анимации).
  5. Проверить, что фаза **не** откатывается прямо в бою: хилл боссу в бою
     (`/effect give` регенерация) не должен вернуть его в раннюю фазу.
  6. Проверить, что `/kill` и смерть босса по-прежнему чистят миньонов и запускают взрыв.
- В ответе: какая из двух гипотез подтвердилась (или обе), и чем именно чинилось.

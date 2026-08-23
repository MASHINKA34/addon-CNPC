# Промт 04 (код, клиент) — полоска таймера ярости под хп-баром босса

## Контекст

Проект `D:\GEYDEV\addon-CNPC` — аддон к CustomNPCs + GeckoLib.
Minecraft 1.21.1, NeoForge 21.1.235, Java 21. Сборка: `gradlew build`.
Комментарии — на английском, коротко и про «почему».

**Зависимость: промт 03 должен быть влит** — оттуда приходит пакет `PacketSyncBossTimer`:

```
UUID eventId, int remainingTicks, int totalTicks, byte state
state: 0 = обычный отсчёт до ярости, 1 = ярость активна, 2 = неуязвимая фаза, 3 = таймера нет
```

Файлы:
- `client/gui/CustomBossBarOverlay.java` — **живой** рендер стилизованных баров босса;
- `data/BossBarStyles.java` — реестр стилей и геометрия текстур;
- `client/gui/{Sculk,Infernal,MossCave,GhostDungeon}BossBarOverlay.java` — легаси-оверлеи;
  они сами выходят через `CustomBossBarOverlay.isTracked(...)`, **их не трогать**;
- `network/BossBarStyleClientBridge.java` — образец моста «пакет → клиентский класс»;
- `lang/en_us.json` + `lang/ru_ru.json`.

## Задача

Под полоской здоровья босса рисуется вторая, более тонкая полоска — обратный отсчёт до
рейдж-фазы. Игрок должен видеть, сколько у него осталось времени, а не узнавать про ярость
по факту. Когда ярость наступила — полоска показывает состояние «ЯРОСТЬ». Когда босс в
неуязвимой фазе (промт 02) — эта же полоска показывает остаток неуязвимости.

## Приём данных

`network/BossTimerClientBridge.java` (мост, как `BossBarStyleClientBridge`) плюс клиентский
приёмник в новом `client/gui/BossTimerOverlay.java`:

- хранит `Map<UUID, TimerState>`, где `TimerState { int remainingTicks, int totalTicks, byte state, long lastPacketClientTick }`;
- между пакетами **сам уменьшает** `remainingTicks` каждый клиентский тик, по пакету —
  жёстко переустанавливает значение (чтобы отсчёт не дёргался, но и не убегал);
- чистится на `ClientPlayerNetworkEvent.LoggingOut` (как в `CustomBossBarOverlay#logout`);
- запись удаляется при `state == 3`.

## Геометрия

В `BossBarStyles.Style` добавить поля таймера (значения зафиксированы, менять нельзя —
под них рисуется арт в промте 07):

| стиль | таймер W×H | трек таймера (x, y, w, h) |
|---|---|---|
| `moss_cave` | 260 × 10 | 31, 2, 200 × 6 |
| `ghost_dungeon` | 1329 × 54 | 104, 12, 1121 × 30 |
| `infernal` | 182 × 7 | 14, 2, 154 × 3 |
| `sculk` | 256 × 12 | 29, 3, 198 × 6 |

Ширина текстуры таймера всегда равна `textureWidth()` основного бара, поэтому масштаб
берётся тот же (`scale = renderWidth / textureWidth`), а X полоски совпадает с X бара.

Текстуры (их рисует промт 07, кода они касаются только путями):

```
textures/gui/boss_bar/<style>/timer_background.png
textures/gui/boss_bar/<style>/timer_fill.png
textures/gui/boss_bar/<style>/timer_frame.png
```

**Фолбэк обязателен**: если хотя бы одного файла нет в `resourceManager` (проверка ровно
как для основного бара в `CustomBossBarOverlay#render`), рисовать плоскую полоску
`graphics.fill(...)` — тёмная подложка + заливка. Аддон должен работать до появления арта.

## Рендер

1. **Стилизованные бары.** В `CustomBossBarOverlay#render`, после рамки и текста, вызвать
   `BossTimerOverlay.draw(graphics, barX, barY + renderHeight, renderWidth, scale, style, timerState)`.
   Инкремент увеличить: `event.setIncrement(renderHeight + timerHeight + 4)` — иначе
   несколько баров наедут друг на друга.
2. **Обычные (нестилизованные) бары.** Отдельный `@SubscribeEvent(priority = EventPriority.LOW)`
   на `CustomizeGuiOverlayEvent.BossEventProgress` в `BossTimerOverlay`: если бар не
   `CustomBossBarOverlay.isTracked(...)`, но для его `eventId` есть таймер — дорисовать
   плоскую полоску под ванильной и сделать `event.setIncrement(event.getIncrement() + h)`.
   Событие при этом **не отменять**.
   Чтобы этот путь вообще получал данные, сервер должен слать `PacketSyncBossTimer` с id
   нативного бара (`npc.bossInfo`), когда стиль бара = `none`; если промт 03 этого не
   сделал — доделать в `TeleportPathController` здесь, одной строкой в месте отправки.
3. Заполнение: `progress = remainingTicks / (float) totalTicks`, полоска **убывает**
   слева направо, как и здоровье. Клип по `trackWidth`, как у основного бара
   (`graphics.blit` с обрезанным `sourceWidth`, не растягивать).
4. Текст по центру трека: `mm:ss` (`String.format("%d:%02d", ...)`, из тиков → секунды
   делением на 20). Мелкий текст на низких барах (infernal — трек 3 px) не влезет:
   рисовать подпись только если `trackHeight * scale >= font.lineHeight`, иначе выводить её
   над правым краем бара маленьким.
5. Состояния:
   - `state = 1` (ярость): вместо отсчёта — `cnpcgeckoaddon.boss.rage_active`, полоска
     полностью залита и мигает (пульс альфы или цвета по `System.currentTimeMillis()`),
     цвет — красный;
   - `state = 2` (неуязвимость): подпись `cnpcgeckoaddon.boss.invulnerable_active`,
     заливка от остатка неуязвимости, цвет — холодный (голубой/белый);
   - `state = 0`: обычный отсчёт;
   - `state = 3` или записи нет: не рисовать ничего и не менять инкремент.
6. `RenderSystem.enableBlend()` / `disableBlend()` вокруг блитов и
   `setFilter(false, false)` на текстурах — как уже сделано в `CustomBossBarOverlay`.

## Локализация (оба файла)

```
cnpcgeckoaddon.boss.rage_active         "RAGE"   / "ЯРОСТЬ"   (уже добавлен промтом 03 — не дублировать)
cnpcgeckoaddon.boss.invulnerable_active "IMMUNE" / "НЕУЯЗВИМ"
```

## Definition of done

- `gradlew build` проходит.
- Проверка в игре (шаги записать в ответ):
  1. Босс со стилем бара `sculk` и включённым рейдж-таймером: под баром идёт отсчёт,
     цифры совпадают с реальным временем до ярости.
  2. Наступила ярость — полоска красная, мигает, подпись «ЯРОСТЬ».
  3. Босс со стилем `none`: таймер рисуется под обычным баром и ничего не ломает.
  4. Два босса одновременно — два бара с двумя таймерами, не наезжают друг на друга.
  5. Временно переименовать `timer_fill.png` (или проверить до появления арта) — работает
     плоский фолбэк, игра не падает и не спамит в лог.
  6. Выйти и зайти в мир во время боя — таймер не «залипает» от старого босса.

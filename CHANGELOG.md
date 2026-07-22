# Changelog

## Release 1.9 (2026-07-18)

> **Внимание:** Версия 1.9 требует **Java 26** и **Paper/Leaf 1.21.4+**.
> Обновление с 1.8 — автоматическое, все данные (SQLite, конфиги, PDC) сохраняются.

---

### 🎨 Scoreboard & Display

- **Градиенты в скорборде** — переписан рендер строк: вместо `MessageUtil.legacy()` (терял градиенты) используется Team prefix/suffix с Component. `<gradient>`, `<rainbow>`, `<#FF00FF>` теперь работают.
- **Убран лимит 40 символов** в строках скорборда — команда может быть любой длины (Minecraft 1.21+ поддерживает).
- **Скрыты красные цифры** (9, 8, 7…) справа в скорборде через `NumberFormat.blankFormat()` (cached reflection — совместимо со всеми версиями Adventure).
- **Исправлен пинг в табе** (`v1.8.113`).

### 🔖 Placeholder System

- **Единый формат `%name%`** — все плейсхолдеры теперь используют `%...%` вместо `{...}`. Работают и через PAPI, и внутри плагина (авто-fallback).
- **PAPI интеграция** — если PlaceholderAPI установлен, все плейсхолдеры регистрируются через PAPI. Если нет — работают внутри плагина.
- **Новые плейсхолдеры:**
  - `%server_time%` — текущее время ОС сервера (`ЧЧ:ММ:СС`)
  - `%server_date%` — текущая дата ОС сервера (`ГГГГ/ММ/ДД`)
  - `%player_world%` — название мира игрока
  - `%player_coords%` — координаты игрока (`X/Y/Z`)
- **Все плейсхолдеры доступны** в табе, скорборде, боссбаре, чате, бродкастах.
- **Чат**: `{player_name}` → `%player_name%`, `{message}` → `%message%`.
- **Пинг в чате** `@everyone` / `@player`.

### 🔍 Omniscanner (Новое!)

- **Полностью новый модуль** для административного сканирования блоков, предметов и сущностей.
- **Типы сканирования:** блоки, предметы (на земле), сущности (мобы, игроки), всё сразу.
- **Асинхронное сканирование** чанков через `ChunkSnapshot` — не фризит сервер.
- **GUI-интерфейс** с результатами, сортировкой по расстоянию.
- **PDC-защита** предметов в GUI — нельзя забрать или выкинуть.
- **Ввод радиуса/типа** через чат вместо Anvil (исправлено `v1.8.130`).

### 🛠️ Admin Menu (`/mp menu`)

- **Новый GUI** для управления плагином: статистика, информация, быстрый доступ к предметам.
- **PDC-ключи** на все предметы — защита от кражи.
- **Чистка курсора** при открытии — предметы не теряются.

### ⚡ Totem Charge System

- **Тотемы с зарядами** — тотем можно зарядить через наковальню (наварка незерит-лома).
- **DataStorage:** PDC на тотеме хранит `totem_charge` (int).
- **Визуализация:** в лор тотема пишется `Charge: X/X`.
- **Активация:** если заряд > 0, тотем спасает жизнь и снимает 1 заряд. Если 0 — тотем срабатывает как обычный (расходуется).
- **Защита:** если charge=0, тотем не активируется, уход в минус заблокирован.
- **Vanilla-тотемы** тоже перехватываются.

### 🧲 Particle Accelerator

- **Новая механика** — разгон частиц с настраиваемыми параметрами.
- Интеграция с физикой блоков и структурами.

### 🧊 Block Friction

- **Кастомное трение** блоков через `PlayerMoveEvent`.
- **Velocity-мод** — изменяется скорость игрока, а не walk speed.
- **Значения:** унаследованы от ванильной скользкости (BLUE_ICE: 0.989).
- **Конфиг:** `features.block_friction.*`.

### 🔐 Auth / 2FA

- **Telegram 2FA** — полная интеграция с Telegram Bot API.
  - 2FA запрос через бота `@OakworldSRVbot`
  - 9-значный код подтверждения
  - Кнопки подтверждения в Telegram
  - Двуязычные сообщения (RU/EN)
- **Скрытие паролей** из лога консоли (фильтр на login/register).
- **Исправлены баги:** GameMode restore, GUI при входе, min password length (`v1.8.146`).
- **Tab complete** для auth-команд (`v1.8.92`).
- **Argon2 classloader fix** для Paper 1.21.4+ (`v1.8.128`).

### 🛡️ Anti-Cheat & Moderation

- `/mp check <player>` — заморозка игрока для проверки.
- `/mp uncheck <player>` — разморозка.
- **Auto-clean** — удаление дублирующихся секций `anticheat:` в config.yml.
- Исправлено выключение античита (`v1.8.153`).

### 🔨 GUI Protection (PDC)

**Глобальная защита всех GUI плагина от кражи предметов:**
- OmniscannerGUI
- AdminMenuGUI
- NotesGUI
- ChgDimGUI
- CodePanelGUI
- EnderChest GUI
- Anvil-слот (result slot)

Все предметы в GUI имеют PDC-метку. `InventoryClickEvent` блокирует перемещение помеченных предметов в инвентарь игрока. Принудительная синхронизация через `player.updateInventory()`.

### ⚙️ Infrastructure & Build

- **Java toolchain:** 25 → **26**
- **Gradle:** обновлён до совместимости с JDK 26.
- **Paper 1.21+ совместимость:**
  - `api-version: 1.21` в plugin.yml
  - Исправлен `No legacy enum constant` — загрузка Material через `Registry.MATERIAL.get()`
  - Исправлен `KnownCommands` cleanup на Paper 1.21+
  - Исправлен `PaperPluginManagerImpl` для swapjar
- **Auto-version:** `build.gradle` — единственный источник версии. При сборке подставляется в `plugin.yml`.
- **swapjar:** починена загрузка на Windows (file lock), I/O recovery, tab completion `.jar` файлов.
- **JAR копируется** в `Jar/` при каждой сборке.

### 📦 Structure Integrity

- **Новая система** целостности структур:
  - Блоки структуры получают PDC-метку `integrity`
  - При разрушении проверяется связность
  - Команда `/mp str` — управление
  - **Unbreakable Integrity Tag** — блоки структуры нельзя сломать, пока цела структура

### 🧩 LuckPerms Integration

- **Блокировка wildcard (`*`)** — `/lp permission set *` консоль предупреждает и требует подтверждение (re-type в течение 15с).
- **Логирование** при попытке выдать `*`.
- Console тоже получает предупреждение (не блокируется).

### 📊 Stats & Debug

- **MSPT** — процент вместо ms в `/mp stats`.
- **Ping** — `Средний:` → `Текущий:` в статистике.
- **Debug-логи** вынесены в отдельный файл.
- **Фильтр логгера** — подавление `Fatal error` от Paper (`v1.8.137`).

### 🧪 Bug Fixes

- **Ender Chest:** убран instant kill (теперь 1 урон), rate-limit explosion удалён, ore-to-stone replacement.
- **Item Assembler:** исправлены рецепты, frame detection.
- **NetheriteUpgrader:** `removeAttributeModifier` перед re-add.
- **Lore duplication:** исправлено задваивание лора.
- **Броня в руке:** больше не даёт защиту.
- **ConcreteBucket:** исправлен рецепт.
- **Lead Shield:** исправлен рецепт.
- **Block custom crafts:** исправлены кастомные крафты в ванильном Crafter.
- **Recipe groups:** исправлены группы рецептов.
- **Speed bugs:** исправлены баги скорости.
- **YAML:** auto-repair config.yml при InvalidConfigurationException, экранирование бэкслешей.
- **ConfigRepairManager:** автоматическое восстановление повреждённого config.yml.
- **MessagesManager:** всегда использует `messages.yml`.
- **YAML regex:** `\p{L}` заменён на хардкодные дефолты (совместимость с Groovy SimpleTemplateEngine).

### 🔄 Commits (112 коммитов с Release-1.8.0)

```
[2026-07-18] Fix scoreboard gradients: Team prefix/suffix Component
[2026-07-17] Remove 40-char truncation limit on scoreboard lines
[2026-07-17] Hide red score numbers via NumberFormat.blankFormat()
[2026-07-17] Add %server_time%, %server_date%, %player_world%, %player_coords%
[2026-07-17] Fix chat format: {player_name} → %player_name%
[2026-07-17] ALL placeholders can use any PAPI placeholder
[2026-07-17] Unify placeholder format to %name% with PAPI + fallback
[2026-07-07] Updated plugin architecture
[2026-07-07] v1.8.159: Clean plugin.yml — commands/permissions via CommandMap
[2026-07-06] v1.8.158: LuckPerms wildcard blocking + warn
[2026-07-06] v1.8.157: Block friction — exponential acceleration
[2026-07-06] v1.8.156: LP wildcard re-type confirmation
[2026-07-06] v1.8.155: Integrity — NMS fallback for Mace/Trident
[2026-07-06] v1.8.154: Block friction clamp
[2026-07-06] v1.8.153: Anti-cheat can't be disabled fix
[2026-07-06] v1.8.152: /mp togglebind — wireless redstone toggle
[2026-07-06] v1.8.151: GUI fix — NotesGUI, CodePanelGUI updateInventory
[2026-07-06] v1.8.150: Fix Commodore spam, api-version 1.21
[2026-07-05] v1.8.149: Omniscanner async ChunkSnapshot scan
[2026-07-05] v1.8.148-144: GUI PDC protection across all GUIs
[2026-07-05] v1.8.143-128: Totem system, Particle Accelerator
[2026-07-05] v1.8.127: PDC anti-theft + Anvil protection
[2026-07-04] v1.8.126-124: EnderChest fixes
[2026-07-02] v1.8.123-122: Structure Integrity, /mp setspawn/spawn
[2026-06-30] v1.8.121: Minor fixes
[2026-06-29] v1.8.120: /mp check/uncheck, BlockFriction, 2FA
[2026-06-29] v1.8.119-100: Wireless redstone, recipes, tab, auth
```

---

### Как обновиться

1. Замените `UltimateImprovments-1.8.jar` на `UltimateImprovments-1.9.jar`
2. Выполните `/mp reload`
3. При необходимости обновите `config.yml` — авто-восстановление добавит новые секции

> Если используются **плейсхолдеры в конфигах** — замените `{...}` на `%...%` (старый формат больше не поддерживается).

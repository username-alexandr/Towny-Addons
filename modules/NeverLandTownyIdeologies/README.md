# NeverLandTownyIdeologies 0.1.3

Аддон идеологий и развития городов для Towny на Paper 26.2.

## Требования

- Java 25;
- Paper или Purpur 26.2;
- Towny 0.103.2.0;
- ItemsAdder, Vault и PlaceholderAPI — необязательные интеграции.

## Установка

1. Поместите `NeverLandTownyIdeologies-0.1.3.jar` в папку `plugins`.
2. Установите Towny и настройте его экономику.
3. При необходимости установите ItemsAdder, Vault и PlaceholderAPI.
4. Запустите сервер. Плагин создаст `config.yml`, `messages.yml`, `ideologies.yml` и `towns.yml`.
5. Измените настройки и выполните `/townyideologies reload`.

## Команды

- `/t ideology` — открыть меню идеологий;
- `/t ideologies` — альтернативное имя команды;
- `/townyideologies reload` — перезагрузить конфигурацию;
- `/townyideologies info <игрок>` — посмотреть идеологию города онлайн-игрока;
- `/townyideologies set <игрок> <идеология> <1-5>` — административно установить ветку и уровень;
- `/townyideologies reset <игрок>` — сбросить идеологию города.

Выбор и улучшение доступны только мэру. По умолчанию смена уже выбранной идеологии отключена.

## Ветки

| ID | Название | Реальный бонус |
|---|---|---|
| `agriculture` | Сельское хозяйство | Дополнительные стадии роста культур на территории города |
| `industry` | Промышленность | Сокращение времени рецептов печей, плавилен и коптилен |
| `healthcare` | Здравоохранение | Дополнительное максимальное здоровье жителей |
| `infrastructure` | Инфраструктура | Настраиваемая задержка предмета после установки блока и увеличение максимальной скорости вагонетки |
| `defense` | Оборона | Настраиваемые эффекты на 15 секунд после начала боя с непрерывным обновлением |
| `trade` | Торговля | Дополнительные предметы при успешной торговле с жителями Minecraft |

Все значения бонусов задаются отдельно для уровней 1–5 в `ideologies.yml`.

## Экономика и валюта

Параметр `settings.economy.mode` в `config.yml` поддерживает четыре режима:

- `TOWN_BANK` — деньги списываются из казны города Towny;
- `PLAYER_VAULT` — деньги списываются с баланса мэра через Vault;
- `ITEM` — валютой служит обычный или ItemsAdder-предмет из инвентаря мэра;
- `COMMAND` — баланс читается через PlaceholderAPI, списание выполняется консольной командой.

Название валюты, формат суммы, причина транзакции и параметры каждого режима настраиваются в `config.yml`. Цены выбора и переходов 1→2, 2→3, 3→4, 4→5 находятся в каждой ветке `ideologies.yml`.

## ItemsAdder

ItemsAdder является необязательным. Если предмет с указанным ID не найден, меню использует ванильный `material`.

Стандартные ID иконок:

- `neverland:ideology_agriculture`;
- `neverland:ideology_industry`;
- `neverland:ideology_healthcare`;
- `neverland:ideology_infrastructure`;
- `neverland:ideology_defense`;
- `neverland:ideology_trade`.

Служебные иконки GUI задаются в `settings.itemsadder` файла `config.yml`.

## PlaceholderAPI

- `%nlti_id%`;
- `%nlti_ideology%`;
- `%nlti_ideology_plain%`;
- `%nlti_level%`;
- `%nlti_progress%`.

## Сборка

```bash
gradle clean build
```

Сборка использует Java toolchain 25 и не включает Towny, Paper или PlaceholderAPI внутрь итогового JAR.

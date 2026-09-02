# NeverLandTownyReputation 0.1.3

Система репутации между игроками, городами и нациями для Paper/Purpur 26.2 и Towny 0.103.2.0.

## Возможности

- направленная репутация игрок → игрок;
- двусторонняя репутация между городами и нациями;
- восемь уровней от «Враждебной» до «Превознесённой»;
- разблокировка торговли, контрактов, заданий, союзов и привилегий;
- отзывы игроков с ограничением по времени игры, суточным лимитом и задержкой;
- защита интеграций от повторной выдачи по уникальному ключу;
- суточные лимиты каждого источника и плавное снижение старых значений;
- GUI, PlaceholderAPI, Bukkit Services API и события изменения репутации.

## Команды

- `/t reputation` — главное меню отношений текущего города;
- `/t reputation player <игрок>`;
- `/t reputation town <город>`;
- `/t reputation nation <нация>`;
- `/t reputation endorse <игрок>`;
- `/t reputation denounce <игрок>`;
- `/t reputation top <player|town|nation>`;
- `/n reputation` — отношения текущей нации;
- `/townyreputation reload|change|set|reset|info|decay`.

Команда `/reputation` (`/rep`) сохранена как прямая запасная форма.

Towny обязателен. ItemsAdder и PlaceholderAPI необязательны.

## PlaceholderAPI

- `%townyreputation_player_relations%`, `%townyreputation_town_relations%`, `%townyreputation_nation_relations%`;
- `%townyreputation_player_score_<игрок>%`, `%townyreputation_player_tier_<игрок>%`;
- `%townyreputation_town_score_<город>%`, `%townyreputation_town_tier_<город>%`;
- `%townyreputation_nation_score_<нация>%`, `%townyreputation_nation_tier_<нация>%`;
- `%townyreputation_<слой>_feature_<возможность>_<цель>%`.

## Интеграция аддонов

`TownyReputationApi` регистрируется через Bukkit Services API. Внешний аддон может безопасно вызвать `change(...)` с источником и уникальным ключом результата, проверить `featureUnlocked(...)`, получить торговую скидку и множитель наград через `snapshot(...)`. Все изменения вызывают отменяемое событие `ReputationChangeEvent`.

## Сборка

Требуется JDK 25. Готовый файл: `NeverLandTownyReputation-0.1.1.jar`.

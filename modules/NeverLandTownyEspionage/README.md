# NeverLandTownyEspionage 0.1.2

Аддон разведки и контрразведки для Paper/Purpur 26.2 и Towny 0.103.2.0.

## Возможности

- пять настраиваемых операций: общая, казна, армия, инфраструктура и дипломатия;
- длительные операции, которые переживают перезапуск сервера;
- шанс успеха и обнаружения, перезарядка и лимит одновременно занятых агентов;
- развитие разведсети и контрразведки за деньги городской казны;
- оборонные здания `NeverLandTownyBuilds` автоматически снижают шанс вражеских агентов;
- обнаружение нападения и уведомление жителей защищающегося города;
- русские GUI, отчёты, история и PlaceholderAPI;
- ItemsAdder-иконки через необязательную интеграцию.

## Команды

- `/t spy` (`/t espionage`) — главное меню;
- `/t spy start <город> <операция>` — начать операцию;
- `/t spy reports` — отчёты;
- `/t spy active` — активные операции;
- `/t spy upgrade <network|defense>` — улучшение;
- `/townyespionage <list|complete|cancel|set|reload>` — администрирование.

## Права

- `mintespionage.use` — просмотр;
- `mintespionage.manage` — операции и улучшения (дополнительно проверяется роль Towny);
- `mintespionage.admin` — администрирование.

## PlaceholderAPI

- `%mintespionage_network_level%`
- `%mintespionage_defense_level%`
- `%mintespionage_active_operations%`
- `%mintespionage_operation_limit%`
- `%mintespionage_unread_reports%`
- `%mintespionage_defense_strength%` — суммарная защита в процентах.

Все параметры баланса, времени, стоимости, шансов, уровней зданий и GUI находятся в `config.yml` и `operations.yml`.

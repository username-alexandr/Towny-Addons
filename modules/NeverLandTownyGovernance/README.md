# NeverLandTownyGovernance 0.1.3

Аддон для Paper/Purpur 26.2 и Towny 0.103.2.0: городской совет, должности, законы и голосования.

## Основные команды

- `/t governance` — главное меню;
- `/t laws` — законы города;
- `/t vote` — открытые голосования;
- `/t council` — совет и должности;
- `/t governance propose <закон>` — предложить принятие закона;
- `/t governance repeal <закон>` — предложить отмену закона;
- `/t governance vote <ID> <yes|no|abstain>` — проголосовать;
- `/t vote <ID> <yes|no|abstain>` — короткая форма голосования;
- `/t governance appoint <должность> <игрок>` — назначить;
- `/t governance dismiss <должность> <игрок>` — снять с должности;
- `/townygovernance reload` — перезагрузить конфигурацию.

Прямая команда `/governance` повторяет функциональность `/t governance` и остаётся доступной при конфликте Towny-подкоманд.

## Интеграции

Towny обязателен. ItemsAdder и PlaceholderAPI необязательны. Через Bukkit Services API доступен `TownyGovernanceApi` с множителями строительства и идеологии для других аддонов.

### PlaceholderAPI

- `%townygovernance_town%`;
- `%townygovernance_active_laws%`;
- `%townygovernance_open_votes%`;
- `%townygovernance_council_size%`;
- `%townygovernance_construction_cost%`;
- `%townygovernance_ideology_cost%`;
- `%townygovernance_ideology_experience%`;
- `%townygovernance_tax%` и `%townygovernance_tax_mode%`;
- `%townygovernance_has_law_<ID>%`;
- `%townygovernance_office_<ID>%`.

### Права

- `townygovernance.use` — меню;
- `townygovernance.propose` — проекты решений;
- `townygovernance.vote` — голосование;
- `townygovernance.offices` — управление должностями с обязательной проверкой роли в Towny;
- `townygovernance.admin` — административные команды;
- `townygovernance.bypass` — обход ролевых ограничений.

## Сборка

Требуется JDK 25. Итоговый файл: `NeverLandTownyGovernance-0.1.1.jar`.

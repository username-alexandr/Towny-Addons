# NeverLandTownyCouncil

Четыре министра, назначаемые мэром: `economy`, `defense`, `construction`, `foreign`.

Открыть меню: `/council` или `/t ministers`. Назначить: `/council appoint <должность> <игрок>`. Снять: `/council dismiss <должность>`.

Требуется Towny. Citizens подключается опционально для проверки `HOLD_OFFICE`. Permissions задаются в `roles.<должность>.permissions`; перезагрузка — `/council reload` с `neverlandtownycouncil.admin`.

[Права, правила назначения, интеграции, API и установка](../../docs/UPDATE_0.26.0_RU.md).

Проверки: `python scripts/build_suite.py Council` из корня репозитория. Серверная проба: [scripts/council-runtime-probe](../../scripts/council-runtime-probe).

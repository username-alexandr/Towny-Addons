# NeverLandTownyDiplomacy

Отдельный аддон межгородских договоров для Towny. Семь отношений: союз, торговый договор, пакт о ненападении, вассалитет, гарантия независимости, эмбарго и санкции.

Команды `/diplomacy`, `/t diplomacy`, `/diplomacy help`. Управляют мэр и уполномоченный министр Council. Подключены Citizens, Council, Trade, Market, Taxes и Espionage.

Полные правила, permissions, настройки и обновление: [UPDATE_0.27.0_RU.md](../../docs/UPDATE_0.27.0_RU.md).

Java 25, Paper/Purpur 26.2, Towny 0.103.2.0. `gradle smokeTest` запускает проверки договоров и хранения. `scripts/build_suite.py` включает модуль в общую сборку и проверяет публичный API. Серверная проба находится в `scripts/diplomacy-runtime-probe` и предназначена только для одноразового стенда.

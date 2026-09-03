# Команды NeverLand Towny Suite 0.7.1

Основные городские функции вызываются через `/t <модуль>`. Административные команды имеют единый формат `/towny<модуль>`. Лагеря используют `/camp`, потому что могут работать без Towny.

| Модуль | Команды игроков | Администрирование |
|---|---|---|
| Территории | `/t stick`, `/t stick list`, `/t stick clear`, `/t claim` | `/townystick reload`, `/townystick give <игрок>` |
| Лагеря | `/camp`, `/camp create`, `/camp spawn`, `/camp trust`, `/camp pack` | `/townycamps edit`, `/townycamps reload`, `/townycamps info <игрок>`, `/townycamps pack <игрок>` |
| Постройки | `/t builds`, `/t wonders`, `/t inv`, `/t civic`, `/t shop` | `/buildeditor`, `/townybuilds reload`, `/townybuilds stall set\|remove\|list` |
| Идеологии | `/t ideology`, `/t ideologies` | `/townyideologies reload`, `/townyideologies info|set|reset` |
| Заказы | `/t contracts`, `/t contracts start|cancel|claim|history` | `/townycontracts start|cancel|list|reload` |
| События | `/t events`, `/t events contribute|history` | Для игрока: `/t events start|stop|list|raidwave|reload`; для консоли: `/townyevents ...` |
| Экспедиции | `/t expeditions`, `/t expeditions start|status|return|claim|history` | `/townyexpeditions list|complete|cancel|reload` |
| Торговля | `/t trade`, `/t trade propose|accept|reject|cancel|tariff|history` | `/townytrade list|complete|cancel|reload` |
| Казначейство | `/t taxes`, `/t sanctions`, `/t agreements`; для государства — `/n taxes`, `/n sanctions`, `/n agreements` | `/townytaxes policy|sanction|agreement|bank|ledger|collect|reload` |
| Разведка | `/t espionage` или `/t spy`, затем `start|reports|active|upgrade` | `/townyespionage list|complete|cancel|set|reload` |
| Управление | `/t governance`, `/t laws`, `/t vote`, `/t council` | `/townygovernance reload|resolve|enact|repeal|info` |
| Репутация | `/t reputation`, `/n reputation` | `/townyreputation reload|change|set|reset|info|decay` |
| Археология | `/t archaeology`, `/t arch`, `/t museum` | `/townyarchaeology reload|give|site|museum|info` |
| Летопись | `/t chronicles`, `/t chronicle`, `/t history` | `/townychronicles add|remove|rescan|reload` |

Прямые команды `/expedition`, `/espionage`, `/governance`, `/reputation`, `/archaeology` и `/chronicles` оставлены как запасные формы. Старые административные команды (`/mint...`, `/nlt...`, `/townstick`) тоже продолжают работать как скрытые алиасы совместимости. Для казначейства `/taxytowny` является алиасом `/townytaxes`.

## Муниципальные службы Builds

| Команда | Назначение |
|---|---|
| `/t civic status` | Состояние водной сети, территорий, маршрутов, страхования и городской лавки |
| `/t civic select <проект> pos1\|pos2\|save\|clear` | Выделить участок Лесничества/Ирригации или маршрут стены, рва и дамбы |
| `/t civic storage <forestry\|merchant_guild\|recycling_yard>` | Открыть отдельный склад муниципальной службы |
| `/t civic insurance status\|deposit <сумма>` | Проверить или пополнить страховой резерв из городской казны |
| `/t civic bulletin <текст>` | Разослать объявление жителям города через Типографию |
| `/t shop` | Открыть ближайшую городскую лавку на спавне |
| `/t shop claim <id>` / `/t shop release` | Занять или освободить торговое место |
| `/t shop stock` / `/t shop price <материал> <цена>` | Пополнить ассортимент и назначить цену |
| `/townybuilds stall set <id>` | Создать торговое место в позиции администратора |
| `/townybuilds stall remove <id>` / `list` | Удалить место или вывести список |

Для выбора территории игрок смотрит на нужный блок и поочерёдно выполняет `pos1` и `pos2`, затем `save`. Обе точки и охваченные чанки должны принадлежать городу. Лавка доступна городу после завершения Гильдии торговцев III уровня.

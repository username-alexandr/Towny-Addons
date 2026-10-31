# Меню аддонов NeverLandTowny

CraftoUI 0.2.0 использует настоящие инвентари аддонов и меняет их отображение. Меню открываются как из новых категорий, так и исходными командами. Установка нового CraftoUI не требует замены JAR аддонов.

## Разделы

| ID экрана | Название | Доступ |
|---|---|---|
| `hub` | Главное меню NeverLand | craftoui.use |
| `town` | Город | craftoui.use + права действий |
| `economy` | Заработок | craftoui.use + права действий |
| `adventures` | Приключения | craftoui.use + права действий |
| `society` | Общество | craftoui.use + права действий |
| `management` | Управление | craftoui.admin.menus + права Control |

Профиль, помощь, настройки и старый `main` сохранены. Неустановленный/недоступный аддон показан заблокированной кнопкой.

## Проверенный состав каталога

По исходникам/классам JAR найдены 38 модулей с GUI и 54 конкретных класса InventoryHolder. Один класс может обслуживать много вкладок, страниц и подтверждений, поэтому число классов **не равно** числу отдельных экранов. Точные имена классов, SHA-256 исходных JAR и узлы прав записаны в [ADDON-INVENTORY.json](ADDON-INVENTORY.json).

| Модуль NeverLandTowny | Версия JAR | Стартовая команда | Раздел | Классов GUI |
|---|---|---|---|---:|
| Builds | 0.8.27 | `/town builds` | `town` | 13 |
| Districts | 0.1.17 | `/townydistricts` | `town` | 1 |
| Population | 0.1.21 | `/townypopulation` | `town` | 1 |
| Specialization | 0.1.14 | `/townyspecialization` | `town` | 1 |
| Research | 0.1.16 | `/townyresearch` | `town` | 1 |
| Policies | 0.1.14 | `/townypolicies` | `town` | 1 |
| Resources | 0.1.22 | `/townyresources` | `town` | 1 |
| Power | 0.1.15 | `/townypower` | `town` | 1 |
| Upkeep | 0.1.18 | `/townyupkeep` | `town` | 1 |
| Logistics | 0.1.18 | `/townylogistics` | `town` | 1 |
| Ideologies | 0.1.19 | `/town ideologies` | `town` | 3 |
| Environment | 0.1.1 | `/townyenvironment` | `town` | 1 |
| Jobs | 0.1.15 | `/townyjobs` | `economy` | 1 |
| Contracts | 0.3.20 | `/town contracts` | `economy` | 2 |
| Companies | 0.1.14 | `/townycompanies` | `economy` | 1 |
| Market | 0.1.15 | `/townymarket` | `economy` | 1 |
| Trade | 0.2.17 | `/town trade` | `economy` | 2 |
| TreasuryPlus | 0.1.16 | `/townytreasury` | `economy` | 1 |
| Camps | 0.1.21 | `/camp` | `adventures` | 1 |
| Expeditions | 0.1.24 | `/expedition` | `adventures` | 1 |
| Archaeology | 0.1.20 | `/archaeology` | `adventures` | 1 |
| Events | 0.4.8 | `/town events` | `adventures` | 1 |
| Quests | 0.1.3 | `/townyquests` | `adventures` | 1 |
| Achievements | 0.1.2 | `/townyachievements` | `adventures` | 1 |
| Chronicles | 0.1.18 | `/chronicles` | `adventures` | 1 |
| Seasons | 0.1.8 | `/townyseasons` | `adventures` | 1 |
| Citizens | 0.1.11 | `/townycitizens` | `society` | 1 |
| Reputation | 0.2.9 | `/reputation` | `society` | 1 |
| Council | 0.1.13 | `/townycouncil` | `society` | 1 |
| Elections | 0.1.11 | `/elections` | `society` | 1 |
| Governance | 0.1.22 | `/governance` | `society` | 1 |
| Diplomacy | 0.1.11 | `/townydiplomacy` | `society` | 1 |
| Crime | 0.1.8 | `/townycrime` | `society` | 1 |
| Justice | 0.1.8 | `/townyjustice` | `society` | 1 |
| Army | 0.1.8 | `/townymilitary` | `society` | 1 |
| Espionage | 0.1.21 | `/espionage` | `society` | 1 |
| SiegesPlus | 0.1.8 | `/townysieges` | `society` | 1 |
| Control | 0.4.5 | `/nltadmin` | `management` | 1 |

Taxes 0.1.20 и Stick 0.1.20 присутствовали в наборе из 40 плагинов, но собственных инвентарных меню в их JAR нет; им не создавались фиктивные экраны.

## Поведение и настройка

- Карточка показывает настоящий предмет и название; панель справа — его lore с прокруткой. Пустые декоративные элементы без названия исключаются из карточек.
- ЛКМ/ПКМ/Shift передаются исходному слоту. Tab/Shift+Tab выбирает карточку, Enter/Space активирует её, Esc закрывает меню.
- Стрелки и колесо над карточками переключают страницы; колесо над описанием прокручивает длинный текст.
- «Предметы» или F6 включает сетку исходного инвентаря; F6 возвращает карточки с пустым курсором. Склады/редакторы начинают с сетки. Появление предмета на курсоре автоматически включает её.
- «Разделы» закрывает текущий инвентарь штатным способом и открывает `hub`. Оригинальные кнопки «Назад»/«Подтвердить» аддона тоже остаются карточками.
- `addons.json` задаёт владельца, точные классы, стартовую команду, право, тему и сетку. Поля: [CONFIGURATION.md](CONFIGURATION.md). Изменения — `/craftoui reload`, затем переоткрыть меню.
- Проверяется реальный плагин-владелец класса. Одно совпадение названия меню не даёт права подменять его оформление. Неизвестные классы, vanilla-сундуки и другие типы контейнеров остаются обычными.

## Что остаётся у аддона

Права и роль игрока в городе, условия доступности, цены, подтверждения, выдача наград, транзакции, состояние проектов, открытие следующих страниц и обработка предметов. CraftoUI не копирует данные и не создаёт параллельную экономику. Клики проходят через штатный Minecraft container protocol и оригинальный InventoryClickEvent.

Это общий новый интерфейс всех разрешённых инвентарей, а не отдельная переписанная бизнес-реализация каждой вкладки. Все операции каждого модуля не тестировались; перечень фактических проверок находится в [TESTS.md](TESTS.md).

## Обновление аддона

Если новая версия сохранила holder-классы, её новые страницы подхватятся автоматически. Если класс изменился, меню безопасно останется обычным: сверить новый JAR/исходники и обновить `holders`/`inventoryHolders`. Не добавлять класс только по совпадению заголовка. При отсутствии исходников можно посмотреть полные имена классов в JAR; владелец должен совпадать с `plugin.yml`.

В архив не включены исходные JAR и исходники NeverLandTowny/Towny: используйте ваш установленный набор. Каталог хранит только сведения о совместимости.

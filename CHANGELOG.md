## 2026-10-24 — понятные должности и права

- Governance 0.1.25: единый список городских должностей и министров; возможности должности перед назначением; явные действия с назначенным жителем; карточки законов и кнопки голосования.
- Council 0.1.15: назначение и снятие министров через подтверждение; проверка прав кандидата; выбор «Разрешить / Запретить / Сбросить»; объяснение наследуемого доступа.
- Все новые действия повторно проверяют полномочия. Подтверждения блокируют повторный клик, изменившиеся назначения/права и действия после закрытия окна. Форматы данных не менялись.
- Инструкция: `docs/CLEAR-CITY-MENUS-RU.md`.

## 2026-10-24 — Governance 0.1.24, объединение сборок

- Возвращено меню назначения и снятия с должностей из приложенного Governance 0.1.23; новые городские правила доступа сохранены.
- Классы меню сверены с приложенным JAR; подтверждены работа через кнопки, приоритет личных прав и сохранение после перезапуска Folia.
- В пакете заменён только Governance. Предыдущие обновления остальных модулей не пересобирались и не изменялись.

## 2026-10-24 — права мэра и раздатчики на фермах

- Council 0.1.14: городские правила доступа для жителей и четырёх должностей; 29 действий в пяти разделах, три режима, приоритет личного правила, доступ к редактору только у мэра.
- Проверки в городских службах, отдельные права отмены стройки, участия и переноса ресурсов; защита от обхода прав складскими кликами.
- Права и журнал сохраняются атомарно; устаревшие меню, смена мэра и уход из города не оставляют действующих полномочий.
- Districts 0.1.19: раздатчики могут воздействовать на враждебных мобов между разрешёнными участками одного города при отключённом PvP. Игроки, мирные мобы, NPC и чужие города исключены.
- Сохранены точки интеграции CraftoUI для Builds и публичных заказов Contracts. Подробности: `docs/MAYOR-ACCESS-AND-FARMS-RU.md`.

## Districts 0.1.18 / Control 0.4.10 — 2026-10-23

- Add `/t farm` for per-plot mob protection: confirmed local changes by town leadership/admins using Towny's persistent flag, preserving other protection flags, world restrictions and external cancellations.
- Show a colored nearby plot map, all-world farm list, coordinate bounds, temporary particle borders and entry status. Revalidate ownership, permissions and exact plot state before confirmation; refuse grouped changes.
- Add immutable map API snapshots for future client interfaces and a dedicated mob-farm audit category. Add native Folia tests for spawning/removal, permissions, UI contents and restart persistence.
- Installation and commands: `docs/MOB_FARMS_0.1.18_RU.md`.

## Builds 0.8.29 / Control 0.4.9 / Archaeology 0.1.21 — 2026-10-23

- Show personal, temporary BlockDisplay hints for the first unfinished horizontal construction layer, with layer progress and readable materials. Bound entity counts and use Folia ownership checks without loading foreign chunks.
- Add confirmed cancellation: admins return 100% of paid resources to the municipal warehouse and money to the town treasury; mayors/authorized builders pay a configurable 10% fee by default. Keep the completed previous level and restore only recorded cells.
- Persist exact purchase receipts, pending refunds and warehouse contents together. Retain overflow, block repeated payouts and require explicit reconciliation of uncertain external bank/museum operations after a crash. Old constructions require verified historical costs before cancellation.
- Persist museum artifact consumption before reporting success, advertise its durable API, and return authentic artifacts on cancellation. Builds checks this capability before charging for a wonder.
- Add the construction/refund audit category and administrator tools. Preserve existing project prices and player/town data.
- Installation, configuration, limitations and verification: `docs/BUILD_LAYERS_AND_REFUNDS_0.8.29_RU.md`.

## Suite 0.42.0 / Contracts 0.3.22 / Jobs 0.1.17 — 2026-10-22

- Publish the complete 40-addon suite with all changes since 0.41.0: economy MVP, daily randomized orders, rarity icons, expanded catalog/budget, hidden paid offers, guarded town deletion and readable confirmations.
- Hunting resolves the actual finishing damage source and player-owned projectiles. Bow/crossbow arrows, spectral arrows and thrown tridents no longer require the shooter to share the victim's Folia region.
- Explicit vanilla weapon/tool whitelist includes all seven tiers of swords, spears, axes, pickaxes, shovels and hoes, other tools, ranged weapons and the empty hand. Keep natural-spawn and anti-trap checks; read player state on its owning region.
- Move municipal kill progress to the killer's entity scheduler; retain existing city contract funding and eligibility rules. Guard salary reads the player position only on that scheduler.
- Build and verify the complete matrix before packaging. Generate fresh checksums/reports and verify every uploaded draft asset by size and SHA-256 before publishing a new release.
- Details, installation and test boundaries: `docs/UPDATE_0.42.0_RU.md` and `docs/HUNTING_0.3.22_RU.md`.

## Control 0.4.8 — 2026-10-22

- Rewrite town-deletion warnings, progress, cancellation and failure messages in plain Russian: explain which buildings can disappear and that chests keep their contents.
- Players confirm through chat buttons without a visible UUID command; console senders retain the command fallback. Simplify deletion menu descriptions and ordinary-player status/help; administrative diagnostics remain available.
- Presentation-only update; deletion rules, protection, persistence and permissions are unchanged. Details: `docs/TOWN_DELETION_TEXT_0.4.8_RU.md`.

## Control 0.4.7 / Builds 0.8.28 — 2026-10-22

- Town deletion now requires a warning and actor-bound confirmation. Admins can preserve ordinary bases and remove only matching blocks from registered Builds blueprints, or explicitly request a validated full rollback.
- Disable native Towny unclaim erasers with a persistent settings backup. Whole-plot rollback is replaced with complete snapshots, preflight validation, vertical-cut limits, hashed durable plans, region-owned writes and restart-safe compare-and-set application.
- Add town deletion menu, permission and audit category; retain native Towny city/treasury/ruins processing. Protect tile entities, system blocks, newly claimed plots and later block changes.
- Builds exports registered blueprint blocks and checks ownership during state normalization; deleted towns are skipped by building repair/migration.
- Installation, changed unclaim behavior and limitations: `docs/TOWN_DELETION_20261022_RU.md`.

## Contracts 0.3.21 — 2026-10-21

- Paid public orders disappear from the completing player's `/orders` board and accept-command suggestions. Filtering uses the accepted board day; the same template on a later daily board remains available.
- Open boards remove paid icons on the player's Folia entity scheduler without moving other icons or reopening menus. A completed-board message appears when no offers remain.
- Unclaimed rewards and pending/review payments stay visible. Orders and payment history remain stored; duplicate-acceptance and payout checks stay in effect.
- Installation and verification: `docs/COMPLETED_ORDERS_0.3.21_RU.md`.

## Contracts 0.3.20 — 2026-10-21

- Added 40 public-order templates (80 total): new deliveries, Nether/deepslate mining, hunting targets and rare fish. Default daily board grows from 12 to 18 offers.
- Default personal public-order budget increases from 1,500 to 3,000 coins per rolling 24 hours. Existing paid and pending amounts remain counted; server-wide cap and job salaries stay unchanged.
- Versioned catalog migration adds only new templates, keeps custom settings and deleted older entries, and backs up configuration to `orders.pre-catalog-v3.yml`.
- Current board and accepted conditions survive the upgrade; expanded selection applies at the next 00:00 Moscow rotation. Details: `docs/EXPANDED_ORDERS_0.3.20_RU.md`.

## Contracts 0.3.19 — 2026-10-21

- Public order icons now identify both activity and rarity without hovering: wooden/stone/iron/diamond/netherite swords and pickaxes, colored delivery boxes and distinct fishing items.
- Epic and legendary menu icons have a visual glint without actual enchantments. Tool attack attributes remain hidden; Russian rarity labels and objective names are preserved.
- Presentation-only update: accepted orders, daily board, rewards, limits and configuration remain unchanged.
- Installation and icon mapping: `docs/ORDER_ICONS_0.3.19_RU.md`.

## Contracts 0.3.18 — 2026-10-20

- `/orders`: 40 templates, 12 daily offers, five rarities with increasing requirements, deadlines and rewards.
- Durable weighted selection at 00:00 Europe/Moscow; four activity types, no duplicate offers, no restart reroll. Accepted terms/progress survive rotations.
- Stale menus cannot accept changed daily offers; cancellation cannot bypass the daily template limit. Existing rolling payout caps remain unchanged.
- One-time additive configuration migration with backup; schema 1 accepted orders remain readable. Russian rarity/difficulty labels, refresh information and reward budget in the menu.
- Contracts regression tests and native Folia validation: `docs/DAILY_ORDERS_0.3.18_RU.md`.

## Economy review — 2026-10-20

- Contracts 0.3.17 / Jobs 0.1.16: cancelled death events no longer count; final cancellation is checked on the player scheduler. Pending payouts rotate fairly past unavailable recipients.
- Contracts: full provider readiness and initial inventory receipt before delivery reservation; permission rechecked when accepting from an already open board.
- Control 0.4.6: new resident/town/nation audit listeners avoid account initialization while Towny economy is inactive.
- Nine executable regression programs and 41 native Folia checks passed; report and limits in `docs/ECONOMY_REVIEW_20261020_RU.md`.

## Contracts 0.3.16 — 2026-10-20

- Русские названия целей публичных заказов из существующего словаря, включая пользовательские переопределения.
- Иконки доски скрывают характеристики оружия и инструментов.
- Сроки показаны в днях, часах и минутах; пояснены паузы и отсчёт во время отсутствия игрока.
- `/orders mine`, принятие и завершение заказа: отдельные читаемые блоки с прогрессом, наградой, сроком и русским статусом выплаты.
- Кнопки сдачи ресурсов и получения награды; отмена подставляет команду для подтверждения. ID доступен в подсказке названия и копируется нажатием.
- Обновление: заменить предыдущий JAR Contracts на остановленном сервере. Данные и конфигурации сохраняются.

## Economy MVP — 2026-10-20 (playtest)

- Contracts 0.3.15: independent public `/orders` board for townless players, delivery/mining/hunting/fishing, immutable accepted terms, three active orders, configurable rotation and active-time deadlines. Existing city/company contract funding remains unchanged.
- Jobs 0.1.15: confirmed-action wages for seven existing professions; existing careers, XP, workplaces and building bonuses retained. Separate salary configuration; no presence/AFK salary.
- Shared earnings outbox: durable reservations, rolling per-player/server budgets, Towny account receipts, restart recovery, ambiguous-payment reconciliation and persisted delivery receipts. Towny/Vault provider readiness checks before personal account access.
- Control 0.4.5: server-income section, separate public-order/salary audit categories, diagnostics and reconciliation links; OP and delegated permission access retained.
- Taxes 0.1.20 readiness fix included. Builds 0.8.27 and Companies 0.1.14 preserved; no building-price/resource changes or automatic company payroll.
- Validation: 48 executable regression tests and 30 native checks on NeverFolia 26.2. Full client/provider gameplay acceptance remains required; see docs/ECONOMY_VALIDATION_RU.md.

## 0.30.0

- Justice 0.1.0: штрафы, публичный розыск, нативные тюрьмы Towny, обеспеченные награды и атомарный журнал платежей.
- Council 0.1.5: полномочия суда для действующего министра обороны в своём городе; сохранение пользовательских настроек.
- Проверены первая тюрьма Towny, офлайн-помилование и восстановление спорных платежей через перезапуск: 117 native assertions, 3 новых smoke-набора, 35 API.
- См. `docs/UPDATE_0.30.0_RU.md` и `docs/validation/0.30.0`.


## 0.27.0

- Diplomacy 0.1.0: семь типов городских договоров, согласование, сроки, расторжение с уведомлением и атомарный реестр.
- Вассалитет без циклов и двух сюзеренов; гарантии независимости и оборонные инциденты.
- Council 0.1.1: дипломатия мэра и министра внешней политики, отдельная кнопка меню.
- Trade 0.2.6, Market 0.1.4, Taxes 0.1.8: эмбарго, санкции и согласованные пошлины без потери оплаченных поставок.
- Espionage 0.1.9: защита договоров до оплаты и выдачи разведданных, ожидание при недоступном API.
- 56 серверных проверок с перезапуском, 60 проверок правил/хранения Diplomacy; 31 отдельный модуль.

## 0.25.0

- Elections 0.1.0: мэр Towny, выборный совет и должности Governance; периодичность, кворум, кандидаты и бюллетени.
- Citizens ограничивает право голоса и право занимать должность на всех этапах кампании.
- Ideologies 0.1.8: публичный API форм правления; монархия и автократия отключают выборы.
- Governance 0.1.10: атомарная квитанция результата, выборный совет и защита должностей от ручной замены.
- Журнал Elections сохраняется до применения; незавершённые результаты требуют REVIEW.
- Шесть тестовых классов трёх изменённых модулей и 48 проверок на Purpur с перезапуском.
- Упаковка выпуска сверяет список JAR с матрицей версий.

## 0.24.0

- Citizens 0.1.0: четыре городских статуса, срок проживания, атомарный реестр и карточка.
- Builds: доступ к городским службам зависит от статуса, включая уже открытый склад.
- Taxes: множитель существующего муниципального налога без отдельного списания.
- Governance: электорат, предложения, назначение и снятие должностных лиц зависят от статуса.
- Адаптер Passport 0.4.0 через публичный API с сохранением паспортных данных.
- 39 исполняемых тестовых классов изменённых аддонов; 42 проверки на сервере с перезапуском.

## 0.23.0

- Общий контрастный стиль всех меню: светлые заголовки, читаемые названия, переносы описаний и отдельные подсказки действий.
- Лавки: понятные пустые состояния, подписанные страницы и возврат из списка покупок.
- Торговый центр: колокол в верхней части меню; раздельная справка команд логистики.
- Казна: возврат к предыдущему меню; право `neverlandtownytreasury.export` проверяется при показе и выполнении CSV.
- Контролируемые очаги пожара на дереве и декоре, ограниченная потеря блоков без дропа, атомарный журнал последствий и ручной ремонт.
- Builds сохраняет последствия пожара при запуске, миграции и улучшении зданий через публичный API Events.
- Версии Gradle, Maven и plugin.yml синхронизированы для всех модулей.

## 0.22.1

- Durable expedition rewards and one-off caravan settlement with explicit reconciliation.
- Idempotent warehouse receipts and verified native player inventory persistence on 26.2.
- Public API capability checks, unified atomic storage and repository fault probes.
- Automatic smoke discovery and source archives without stale JARs or build caches.

# 0.22.0

- Введён общий контракт API: версия, список возможностей и диагностика состояний `не установлен`, `отключён`, `сервис недоступен`, `несовместимый API` и `ошибка вызова`.
- Убраны обращения Jobs и Trade к внутренним классам соседних плагинов; каталог зданий, склады, лагеря и остальные межмодульные функции доступны через публичные сервисы.
- Городская лавка Builds переведена на сохраняемую операцию покупки: намерение, списание, зачисление, выдача и завершение восстанавливаются без повторной оплаты или повторной выдачи.
- `CivicService` разделён на автоматизацию, эффекты и меню лавки. Добавлен единый `WarehouseApi`.
- Archaeology, Builds, Camps, Jobs и Market используют единый атомарный файловый слой и закрывают изменения после ошибки записи; повреждённая база не перезаписывается.
- CI и публикация запускают все исполняемые smoke-проверки каждого модуля. Раннер учитывает тесты Maven, аргументы конфигурационных проверок и выбирает JAR по версии из `plugin.yml`.
- Локально прошли 86 исполняемых проверок, проверка 27 JAR / 196 YAML и два запуска полного комплекта на Purpur 26.2 с перезапуском.

# 0.21.0

- Новый Jobs 0.1.0: семь городских профессий, одно рабочее место на игрока, пять уровней отдельного опыта и активные смены.
- Builds 0.8.10: публичные координаты рабочих мест, специалисты в меню зданий, усиление физических производств и городских функций; ведро воды расходует пустое ведро.
- Resources 0.1.7 и Research 0.1.2: бонус специалистов к стратегическому выпуску и скорости исследований с ограничением суммарного эффекта.
- Русские меню, проверка прав и территории, окончание смен при выходе, телепортации, смерти и перезапуске, сохранение назначений и опыта при сбое записи.
- Полный комплект 27 аддонов. [Установка и работа](docs/UPDATE_0.21.0_RU.md).

# 0.20.0

- Contracts 0.3.0: муниципальный конструктор, дорожные участки и уникальные разведанные чанки, резерв казны и безопасное восстановление операций.
- Builds 0.8.9: складские квитанции для муниципальных поставок.
- Совместимость старых шаблонов, компаний и TreasuryPlus; миграция данных до схемы 3.
- Полный комплект 26 аддонов. [Подробности](docs/UPDATE_0.20.0_RU.md).

# 0.19.0

- Новый NeverLandTownyCompanies 0.1.0: магазины, шахты, фермы и транспортные компании внутри города.
- Общий счёт, владелец, управляющие и участники; приглашения и передача владения с принятием получателем.
- Налог с общего счёта в настоящую городскую казну; задолженность блокирует новые контракты. Treasury+ учитывает поступления.
- Contracts 0.2.0: компания может взять опубликованный заказ с нулевым прогрессом. Зачёт только её участникам, вся награда — компании.
- Расчёт корпоративного заказа фиксируется до зачисления. Квитанции защищают награду и возврат городу от повторного исполнения после перезапуска.
- Русские меню, подтверждения денежных операций, журнал платежей и административная сверка неопределённых банковских результатов.
- Полный релиз содержит 26 аддонов. Рецепты, геометрия и баланс строительства не менялись.

# 0.18.1

- TreasuryPlus 0.1.1: первый опрос счетов перенесён на первый серверный тик после включения плагинов. До завершения инициализации бюджетные операции закрыты.
- Добавлен порядок загрузки после Vault, Reserve и Essentials. Исправлено отключение казны при запуске с существующими городами до готовности EssentialsX.
- Добавлен отдельный тестовый плагин для локального одноразового стенда: реальные счета Towny/Vault/EssentialsX, складские операции Market/Builds и восстановление оплаченной покупки после перезапуска.

# 0.18.0

- `/t market local` — городской рынок: город продаёт реальные товары своим жителям за личные деньги. Оплаченные покупки сохраняются до получения через `/t market orders`.
- `/t market global` — международный рынок: мэры и уполномоченные заместители закупают товары других городов в `/t inv`, оплачивая их из инфраструктурного бюджета Treasury+ или обычной казны Towny.
- Выставление сразу резервирует товар из городского склада. Частичные покупки уменьшают действительный остаток. Снятие возвращает непроданный товар; уже оплаченные покупки завершаются.
- Цена за штуку задаётся продавцом либо рассчитывается автоматически. Автоцена учитывает реальный доступный запас и завершённые покупки за последние 24 часа. По умолчанию диапазон 50–200% базовой цены, вклад одного покупателя в спрос ограничен.
- Рынок I открывает местную торговлю, каждый уровень даёт 4 места для предложений. Рынок II и гильдия I открывают международные предложения; каждый уровень гильдии даёт 4 места для экспорта. Проверяется активность зданий, санкции и ограничения импорта.
- Русские меню: каталоги, подтверждение итоговой суммы, управление предложениями, история и получение покупок. Если цена изменится до подтверждения, потребуется новое согласие. Поддерживаются точные предметы ItemsAdder и образец из руки.
- Builds 0.8.8 сохраняет рыночный резерв и квитанции вместе со складами. Журнал Market фиксирует этап перед каждой банковской операцией и личной выдачей. Неопределённые платежи и выдачи не повторяются автоматически.
- В меню Builds и Trade добавлены переходы на рынок; Trade обновлён до 0.2.1. Все 90 зданий, 11 чудес, 461 строительный этап и 1118 строк стоимости сохранены.

Пример экспорта: `/t market sell iron_ingot 500 auto global`. Для фиксированной цены 4 монеты за штуку замените `auto` на `4`.

Установите Market 0.1.0 с Builds 0.8.8; Trade 0.2.1 добавляет переход из торговой доски. [Инструкция обновления и восстановления](docs/UPDATE_0.18.0_RU.md).

Локальные сборки Market, Builds и Trade и проверки цен, конкурирующих покупок, метаданных, возвратов, платежей и выдачи прошли. Проверка на работающем Minecraft-сервере не выполнялась.


# 0.17.0

- Продавец задаёт предмет, количество, цену за партию и период 1–30 реальных дней; покупатель подтверждает неизменяемые условия. По умолчанию — раз в неделю, первая поставка через неделю после принятия.
- Пример: `/t trade contract propose Москва IRON_INGOT 500 2000 7`. Просмотр, подписание, история, пауза и отмена: `/t trade contracts`. Поддерживаются предметы Minecraft, ItemsAdder и точный образец из руки (`hand`).
- Полная партия списывается со склада продавца, деньги — из бюджета инфраструктуры покупателя. После доставки на склад покупателя продавец получает оговорённую сумму. Фиксированная цена включает всю поставку; транзитные сборы к этим прямым складским договорам не добавляются.
- Перед каждой поставкой проверяются действующие рынки, санкции, политика импорта, товар, место и деньги. Ожидание не создаёт штрафы или долг. После простоя сервера исполняется одна просроченная поставка, следующая назначается от её завершения.
- Пауза действует от каждой стороны отдельно. Отмена прекращает будущие поставки; уже начатый платёж завершается. Меню полностью на русском, с подтверждениями и проверками прав при каждом действии.
- Builds 0.8.7 хранит межгородской резерв, обе складские операции и квитанции в атомарном снимке. Журнал Trade сохраняется перед каждым банковским действием. Неопределённые результаты оплаты требуют сверки и не повторяются автоматически.
- У разовых сделок исправлено уважение общей блокировки открытого склада; в торговых меню закрыты перенос предметов перетаскиванием и обработка кликов по инвентарю игрока.

Установите Trade 0.2.0 и Builds 0.8.7 вместе. [Руководство обновления](docs/UPDATE_0.17.0_RU.md) содержит права, настройки и восстановление после сбоя. Цены строительства и все 90 зданий / 11 чудес сохранены.

Локальные сборки и проверки Trade/Builds прошли, включая сбои платежей и диска, повтор после перезапуска, метаданные ItemsAdder и сохранность товара. Проверка на работающем Minecraft-сервере не выполнялась.


# 0.15.0

## 0.16.0 — казна, бюджеты и недельные отчёты

- Настоящая казна Towny разделяется на строительный, армейский, инфраструктурный и социальный бюджеты; свободный остаток остаётся доступен для распределения.
- Мэр включает контроль через `/t treasury`, подтверждает распределение и может переводить средства между статьями или менять доли будущих поступлений. По умолчанию 40% / 20% / 25% / 15%.
- Новые расходы Builds, Upkeep, Trade, Contracts, Ideologies и Espionage проверяют свою статью. Существующие города начинают без ограничений до решения мэра.
- Недельные отчёты: денежные поступления и расходы, возвраты, налоги, фактическое производство и потребление Resources. Неделя начинается в понедельник 00:00 UTC; доступен архив текущей и 12 предыдущих недель, уведомление мэру и экспорт CSV.
- Успешные операции учитываются через AccountObserver Towny. Закрытая экономика не создаёт двойных записей города из-за уведомления о серверном счёте. Неизвестные изменения баланса отражаются отдельными корректировками.
- Перед новым бюджетным списанием сохраняется намерение. Неопределённый результат не повторяется автоматически и требует сверки. Сбой после подтверждённого дебета не выдаётся за отказ банка.
- История фактических ресурсов сохраняется атомарно вместе с запасами. Резерв, возвращённые ресурсы, прогнозы и ручная выдача не считаются производством. Завершённое содержание учитывается в потреблении один раз.


- Policies 0.1.0: шесть политик и 18 режимов; выбор мэром/уполномоченным, подтверждение, отдельное ожидание каждой категории, история и сохранение по UUID города.
- Реальные плюсы и минусы: ставки муниципальных сборов Taxes, транзитные пошлины Trade, лимит армии, субсидии аграрным и промышленным зданиям, ограничения нового импорта.
- Субсидии увеличивают содержание через существующие счета Upkeep; мобилизация снижает стратегическое производство и довольство. Данные армии сохраняются при уменьшении лимита.
- Новое меню /t policies, переходы из Builds, Resources и Governance, API и PlaceholderAPI. Нативные налоги Towny, голосования Governance, санкции и внешние сборы сохраняют прежнюю логику.
- Полный комплект 23 аддонов. 90 зданий, 11 чудес, 461 строительный этап и 1118 строк стоимости строительства сохранены.
- [Обновление](docs/UPDATE_0.15.0_RU.md) · [Баланс](docs/POLICIES_BALANCE_RU.md).

# 0.14.0

- Specialization 0.1.0: семь направлений города, выбор с уровня Towny III и ратуши III, подтверждение и смена раз в семь дней по умолчанию.
- Builds 0.8.4: семь уникальных зданий по пять этапов; 90 зданий и 11 чудес, 461 строительный этап. Старые проекты и цены сохранены.
- Реальные бонусы производства, исследований, торговли, NPC-курьеров, довольства и защиты жителей от враждебных мобов.
- Единая проверка направления при строительстве, работе, обслуживании и питании. При смене старое уникальное здание сохраняется, но не работает.
- Research сохраняет дробный прогресс ускорения; семь новых профилей во всех городских системах. Полный комплект из 22 модулей.
- [Установка и команды](docs/UPDATE_0.14.0_RU.md) · [Баланс](docs/SPECIALIZATIONS_BALANCE_RU.md).

# 0.13.0

- Research 0.1.0: семь технологий по три уровня, требования научных зданий, знания Resources и одна научная работа города.
- Долговечное резервирование, завершение и возврат знаний; пауза инфраструктуры и сохранение прогресса без офлайн-начислений.
- Builds 0.8.3, Resources 0.1.3, Population 0.1.5, Logistics 0.1.3, Trade 0.1.7: реальные производственные, транспортные и демографические эффекты; защита жителей реализована в Research.
- 21 модуль; стоимость и геометрия всех зданий и чудес сохранены.
- [Установка](docs/UPDATE_0.13.0_RU.md) · [Дерево технологий](docs/RESEARCH_TREE_RU.md).

# 0.12.0

- Power 0.1.0: четыре источника энергии, 15 потребителей, приоритеты, остановка и автоматическое восстановление питания.
- Builds 0.8.2: водяное колесо, генератор и электростанция; 83 здания, 11 чудес, 426 строительных уровней; энергетическое меню и русский словарь новых материалов.
- Общее состояние обслуживания и питания для производства, бонусов, населения и логистики; сохранение грузов и независимый запуск генераторов.
- Профили новых источников в Resources, Population, Districts и Upkeep; 20 модулей.
- [Установка и команды](docs/UPDATE_0.12.0_RU.md) · [баланс мощности](docs/POWER_BALANCE_RU.md).

# 0.11.0

- Добавлен NeverLandTownyUpkeep: 91 тариф содержания, размер города, казна Towny и стратегические ресурсы.
- Неактивность отключает производство, инфраструктуру, военные привилегии, районные бонусы и маршруты, сохраняя здания и груз.
- Меню обслуживания, защищённые резервы, долговечные счета, административная сверка неопределённых денежных операций.
- Интеграции Builds, Resources, Population, Districts, Logistics, Trade, Expeditions, Espionage и Events.


# 0.10.0

- Resources 0.1.0: восемь стратегических запасов, 91 профиль зданий/чудес, выпуск и расход по этапам, вместимость и дробный районный бонус.
- Городские резервы и приоритеты; питание населения защищено от потребления зданий; меню баланса и остановок.
- Population 0.1.2: стратегическое снабжение ограничивает фактическое обеспечение при дефиците, недоступное API приостанавливает расчёт.
- Фиксированная точность 0,001, атомарное сохранение до публикации в памяти, сохранение запасов сверх уменьшенного лимита.
- 18 модулей; [установка](docs/UPDATE_0.10.0_RU.md) и [таблица всех проектов](docs/STRATEGIC_BALANCE_RU.md).

# 0.9.0

- Logistics 0.1.0: ходящие NPC, четыре вида базы, пять уровней, маршруты между зданиями, дорожные/рельсовые/портовые точки, фильтры и резерв.
- Builds 0.8.0: публичный API складов, журнал грузов в одной атомарной записи с предметами, защита от повторной выдачи и конфликтующих окон склада.
- Пять производств складывают результат у своего здания; рецепты, вход и выход настраиваются. Вода требует пустые вёдра.
- Ограничены NPC, поиск пути и операции с грузом; незагруженные участки и заполненные склады приостанавливают доставку.
- 17 модулей; руководство [UPDATE_0.9.0_RU.md](docs/UPDATE_0.9.0_RU.md).

# 0.8.0

- Districts 0.1.0: семь типов городских районов, собственная территория Towny, меню и показ границ.
- Правильный район даёт завершённому зданию +15%; кузница, литейная и склад в одном промышленном районе добавляют ещё +20%.
- Builds 0.7.8: публичные границы всех 91 проектов, бонусы муниципальных служб, армии и производства.
- Population 0.1.1: бонус района усиливает вклад конкретного здания в жильё, работу, снабжение и положительное довольство.
- Атомарное сохранение районов, проверка пересечений, связности, полного размещения здания и владения землёй.
- 16 модулей в релизе; руководство [UPDATE_0.8.0_RU.md](docs/UPDATE_0.8.0_RU.md).

# 0.7.9

- Population 0.1.0: городское население, снабжение, жильё, занятость, довольство, меню и API.
- Builds 0.7.7: жилой квартал, 80 зданий; сохранены прежние модели и ресурсный баланс.
- Проверка состава и публикация 15 модулей.

## 0.7.8 — крыши, экспедиции, рейды и армия

Обновлены Builds 0.7.6, Expeditions 0.1.9 и Events 0.2.0. Остальные 11 JAR сохраняют версии из релиза 0.7.7.

- Крыши: добавлены 1937 стеновых блоков в 26 импортированных моделях; балки под крышей появляются вместе с ней. Закрыты стеклянные фронтоны теплицы и Хрустального дворца. Фронтоны обычных домов входят в работу жителей. Архитектура 6 автоматически дополняет существующие площадки при запуске, сохраняя совместимые блоки и содержимое контейнеров.
- Разведены стандартные иконки аграрного комплекса/мельницы, пяти водных служб, крепости/стражи и храма/собора. Старые стандартные иконки обновляются при загрузке, пользовательские переопределения сохраняются.
- Автоматический возврат из экспедиции удалён, включая старую настройку return-on-complete. Кнопка в меню и /expedition return требуют neverlandtownyexpeditions.return. Ручной возврат доступен во время экспедиции и 10 минут после её завершения; время настраивается.
- Щит на территории активной экспедиции требует отдельного neverlandtownyexpeditions.shield. Оба права по умолчанию выключены: назначьте нужной донатной группе.
- Рейд: 10 последовательных волн, 100 врагов по умолчанию; следующая волна после зачистки и паузы. Усиливаются состав, здоровье и урон. За убийство — 1–8 очков по типу врага. Поставки дают очки, победа требует зачистить все 10 волн. Данные волн сохраняются; старые тела после перезапуска удаляются, уже убитые враги не восстанавливаются.
- Новый raids.yml: состав волн, характеристики и очки, модели ModelEngine 4 / ItemsAdder, снаряжение ItemsAdder. Кастомные модели нужно установить отдельно и указать собственные ID. Ошибка ID оставляет врага в очереди с объяснением в журнале.
- Старый префикс MintTownyEvents заменяется на NeverLand • События и в сохранённых сообщениях.
- Отдельный «Штаб армии»: 5 стадий, 10 мест за уровень по умолчанию. /t army открывает состав; мэр или офицер может мобилизовать граждан своего города с 18 лет персонажа. Неизвестный возраст запрещает мобилизацию. Возраст берётся через ResidentAgeProvider, настроенный placeholder паспорта или подтверждение администратором /townyarmy age. Это армия граждан-игроков Towny.

Установка: остановите сервер, замените JAR установленных аддонов без дубликатов, запустите сервер. Конфиги и данные не удаляйте. Новый raids.yml создаётся автоматически. Бюджеты стандартных проектов пересчитываются с учётом новых стен и штаба; resource-balance.csv включает 90 проектов / 406 уровней.

Проверено: сборка трёх изменённых модулей; каталог, геометрия, стадии и миграции; доступ к возврату/щиту, сохранение и срок возвратов; возраст, права и вместимость армии; все 10 волн, рост сложности, перезапуск и повторные смерти. Игровая проверка на вашем мире и отображение ваших моделей ModelEngine/ItemsAdder требуют проверки на сервере.

Подробные команды и настройка: [UPDATE_0.7.8_RU.md](docs/UPDATE_0.7.8_RU.md).

# NeverLand Towny Suite 0.7.7

NeverLandTownyBuilds 0.7.5: препятствия, русские названия и новый расчёт ресурсов всех 78 зданий и 11 чудес.

- Блок предыдущего этапа в подземной зоне больше не считается препятствием. Проверки используют архитектуру сохранённой площадки. Координаты содержат мир; запуск улучшения из другого мира отклоняется с адресом площадки.
- При отказе строительства мешающие блоки из сообщения обводятся красными частицами на 90 секунд. После удаления или замены блока его обводка исчезает. Повторный запуск проверки обновляет список. Обводка персональная, в радиусе 64 блоков.
- Исправлены требования железных цепей в торговом порту, городском рве и портовом форте: используется IRON_CHAIN. Старое имя CHAIN в сохранённых проектах распознаётся при загрузке, поэтому ресурс больше не пропускается.
- Проверены пути отображения материалов в меню всех 14 аддонов. Builds, Camps, Contracts, Events, Expeditions и Trade используют общий встроенный русский словарь для всех 1691 актуальных материалов Paper 26.2. Исправлены саженцы, ресурсы лагерей, заказы, взносы событий, экспорт и городская лавка.
- Старые неполные item-names.yml дополняются при загрузке; автоматические английские подписи не перекрывают русский перевод. Названия, заданные игроком или администратором, сохраняются.
- Совместимость CHAIN → IRON_CHAIN распространяется на требования, материалы настроек шести модулей, цели заказов и сохранённые цены лавки. Старые состояния minecraft:chain в снимках лагерей и экспедиций восстанавливаются как железные цепи с сохранением оси и воды.
- Количество ресурсов определяется объёмом нового этапа: коэффициент 1.15 для зданий, 1.35 для чудес. Редкие ресурсы имеют больший вес, исходные количества задают пропорции. Конечные числа округляются вверх до удобных партий. Для административных custom-projects.yml и ресурсов с NBT цены остаются заданными вручную.
- Старые projects.yml автоматически получают расчёт при загрузке. Его можно отключить через settings.resources.blueprint-budget: false. Излишки фонда возвращаются запускающему строительство игроку; если инвентарь заполнен, предметы выпадают рядом.

Таблица resource-balance.csv в релизе содержит исходные и новые количества для каждого ресурса и уровня стандартного каталога. Пользовательские переопределения могут отличаться.

Обновлены Builds 0.7.5, Camps 0.1.7, Contracts 0.1.3, Events 0.1.9, Expeditions 0.1.8 и Trade 0.1.5. Остановите сервер, замените JAR установленных аддонов без дубликатов и запустите сервер. Не удаляйте конфиги и данные. Автоматические проверки охватывают каталог и модели, бюджеты, весь словарь материалов, миграцию цепей, сохранение излишков и координаты. Точный случай со скриншота без мира сервера не воспроизводился; подсветка показывает блоки, фактически обнаруженные сервером.

Подробности проверки меню и границы проверки: [MATERIAL_LOCALIZATION_AUDIT_RU.md](docs/MATERIAL_LOCALIZATION_AUDIT_RU.md).

# NeverLand Towny Suite 0.7.6

NeverLandTownyEvents 0.1.8 исправляет начисление очков за разбойников и вывод ключа raid-wave-town вместо сообщения.

- Игрок получает подтверждение, а город набега — 10 очков за убитого игроком помеченного моба. Помощники из других городов тоже могут защищать город. Естественная смерть и обычные монстры не учитываются.
- Очки проходят через общий обработчик вкладов: обновляется bossbar, сохраняется прогресс и завершается событие при достижении цели. Метка моба снимается до начисления, исключая повторный учёт.
- Настройка gameplay.raid-kill-points задаёт очки за убийство, по умолчанию 10; значение 0 отключает начисление. Старые конфиги используют значение по умолчанию.
- Отсутствующие в старом messages.yml сообщения берутся из JAR; пользовательские тексты сохраняются.

Обновление: остановите сервер, замените старый JAR Events на 0.1.8 без дубликатов, запустите сервер. Удалять конфиги не нужно. При активном набеге убийство помеченного разбойника должно дать сообщение с очками и изменить счётчик, например 0/460 → 10/460. Прошлые убийства до обновления не восстанавливаются.


## Expeditions 0.1.7

- Для royal_galleon и drowned_temple вместо неопределённого POTION: обычное зелье подводного дыхания (3:00) ×2 и обычное зелье ночного зрения (3:00) ×2. Тип записывается в PotionMeta, поэтому проверка и списание из схрона различают зелья. Название эффекта и длительность видны в меню.
- Старые нетипизированные требования этих двух экспедиций преобразуются при загрузке без удаления конфигов.
- Запрещено распространение скалка от блоков экспедиции, чтобы новые аномалии не оставляли изменения земли вне сохранённого шаблона. Старая территория, испорченная до обновления, не восстанавливается без исходного снимка. Завершение экспедиции по-прежнему требует и победы над врагами, и выполнения целей.

# NeverLand Towny Suite 0.7.5

NeverLandTownyBuilds 0.7.3: вместо ресурса DEEPSLATE_TILES («плитка») требуется только COBBLED_DEEPSLATE_SLAB — плита из колотого глубинного сланца.

- Встроенные требования и старые обычные ресурсы проектов приводятся к выбранной плите при загрузке. Количество не меняется.
- Уже внесённые обычные DEEPSLATE_TILES в фонде засчитываются как плиты 1:1 и суммируются с новыми взносами. Новые взносы принимаются плитами. Пользовательские предметы с метаданными не преобразуются.
- Подсказки ручного строительства требуют ту же плиту. Установка завершает структурный блок чертежа; геометрия готовых крыш и построек сохранена.
- Исправлена старая подпись полного блока на «Глубинносланцевый кафель»; ресурс меню теперь называется «Плита из колотого глубинного сланца».

Обновление: остановите сервер, замените старый JAR Builds на 0.7.3 и запустите сервер. Конфиги и данные сохраняются. Для проверки предмета: `/minecraft:give @s minecraft:cobbled_deepslate_slab 64`.

# NeverLand Towny Suite 0.7.4

NeverLandTownyExpeditions 0.1.6 исправляет вывод `target-coordinates` вместо координат при запуске экспедиции и в `/expedition status` после обновления со старым messages.yml.

Вызов getString с явным запасным значением обходил стандартные сообщения из JAR. Теперь отсутствующие в файле сообщения берутся из встроенных настроек, а пользовательские переводы сохраняются. Мир, X/Y/Z и расстояние подставляются в полученный шаблон.

Регрессионный тест проверяет старый файл без target-coordinates, подстановку всех координат (включая отрицательные) и сохранение пользовательского формата.

Для обновления остановите сервер, замените старый NeverLandTownyExpeditions JAR на 0.1.6 без дубликатов и запустите сервер. Удалять messages.yml не нужно. В активной экспедиции проверьте `/expedition status`.

# NeverLand Towny Suite 0.7.3

- NeverLandTownyEvents 0.1.7: метка нашествия устанавливается до проверки спавна. Через события MobSpawnRemovalEvent и MobRemovalEvent только мобы нашествия освобождаются от запрета и удаления Towny. Другие плагины по-прежнему могут отменить спавн. Отключён обычный деспавн этих мобов по расстоянию; очистка по завершении события сохранена.
- NeverLandTownyStick 0.1.6: серия повторных ПКМ по одному чанку больше не переключает выделение многократно. Для отмены выбора отпустите кнопку на 0,4 секунды и нажмите снова. Другие чанки можно выбирать сразу.
- Неизвестный тип моба больше не подменяется зомби и учитывается в диагностике.

## Установка и проверка

Остановите сервер, замените старые JAR Stick и Events новыми (без дубликатов), затем запустите сервер. Конфиги и данные сохраняются.

Выберите несколько разных чанков специальной палкой и проверьте `/t stick list`. Для нашествия используйте `/townyevents raidwave <город>` во время активного события. В мирной сложности враждебные мобы не смогут жить; другие причины отказа выводятся в консоль.

Проверка CI подтверждает компиляцию и защиту от повторных кликов. Воспроизведение обоих пользовательских сценариев на игровом сервере пока не выполнено.

# История изменений

## NeverLand Towny Suite 0.7.2 — 2026-09-07

- исправлено распознавание обычных ванильных ресурсов со служебными компонентами Paper при сдаче в фонд строительства;
- `DEEPSLATE_TILES` теперь одинаково учитывается при поиске в инвентаре, списании, сохранении фонда и отображении прогресса в GUI;
- пользовательские ItemsAdder/NBT-ресурсы сохраняют строгое сравнение и не смешиваются с ванильными предметами того же базового материала;
- название глубинносланцевой плитки в меню уточняет, что требуется полный блок, а не плита или ступеньки;
- расширены smoke-тесты частичных взносов и идентичности ресурсов.

## NeverLand Towny Suite 0.7.1 — 2026-09-03

- пять первоначальных Чудес Света получили по три профильных требования к городским зданиям;
- все 11 Чудес теперь используют единый механизм инфраструктурных условий до начала строительства;
- старые `projects.yml` безопасно наследуют отсутствующие `requires` из встроенного каталога без удаления конфигов и потери пользовательского баланса;
- релизная проверка фиксирует точные идентификаторы и уровни всех 33 зависимостей Чудес.

## NeverLand Towny Suite 0.7.0 — 2026-09-03

- NeverLandTownyBuilds обновлён до 0.7.0: добавлены Колосс Родосский, Мировое древо, Небесный оррерий, Терракотовая армия, Хрустальный дворец и Великий канал;
- все шесть Чудес имеют уникальные физические чертежи, проходимые входы, внутренние уровни и гибридное строительство;
- запуск новых Чудес требует трёх профильных городских проектов, а состояние условий видно в GUI;
- Archaeology получил 12 новых добываемых артефактов, шесть музейных коллекций и требования для всех 11 Чудес;
- бонусы Чудес подключены к лесничеству, водной сети, лавкам, караванам, экспедициям и пяти городским событиям;
- Chronicles отслеживает новые Чудеса без повторного стартового объявления уже построенных объектов.

## NeverLand Towny Suite 0.6.0 — 2026-09-02

- NeverLandTownyBuilds обновлён до 0.6.0 и расширен с 62 до 78 городских проектов;
- весь каталог зданий разделён на семь тематических категорий с отдельными GUI-разделами;
- добавлены 16 физических пятиэтапных моделей муниципальной очереди;
- реализованы территории Лесничества и Ирригации, линейные маршруты стен, рвов и дамб, отдельные склады и безопасная фоновая автоматизация только в загруженных чанках;
- Гильдия торговцев получила городские лавки на администраторских местах спавна, безопасный список товаров и оплату через счета Towny;
- добавлены водная цепочка, страховой резерв, перепись, бюллетени, переработка и публичный API бонусов без новых зависимостей порядка загрузки;
- существующие уровни, площадки, склады и фонды 0.5.x сохраняются.

## NeverLand Towny Suite 0.5.6 — 2026-09-02

- NeverLandTownyBuilds обновлён до 0.5.3;
- логические уровни выше пятого больше не запускают повторно пятый физический чертёж;
- в меню для таких уровней показывается действие «Улучшить» без требования наводиться на новую площадку;
- опоры под фундаментом, предусмотренные последующей стадией того же активируемого чертежа, больше не считаются отсутствующими;
- суммы в русском интерфейсе выводятся с русским разделителем тысяч;
- добавлен smoke-тест границы физических стадий и запланированных опор.

## NeverLand Towny Suite 0.5.5 — 2026-09-02

- NeverLandTownyBuilds обновлён до 0.5.2;
- подземные этапы Каменоломни и Канализации больше не считают естественный грунт препятствием;
- перед активацией проверяется весь объём выемки, включая пустое пространство внутри котлована;
- после запуска этапа автоматически и без выпадения ресурсов удаляются только природные блоки;
- строительные блоки, контейнеры и другие искусственные объекты остаются защищёнными;
- добавлен smoke-тест объёмов выемки: 197 блоков для Каменоломни и 364 для Канализации.

## NeverLand Towny Suite 0.5.4 — 2026-09-02

- NeverLandTownyChronicles обновлён до 0.1.3;
- первая синхронизация после запуска больше не объявляет уже существующие Чудеса Света как только что построенные;
- начальная загрузка ранее достигнутых городских показателей также выполняется без оповещений;
- реальные новые Чудеса и достижения после запуска продолжают записываться и объявляться;
- для политики стартовых оповещений добавлен отдельный исполняемый smoke-тест.

## NeverLand Towny Suite 0.5.3 — 2026-09-02

- устранён циклический порядок загрузки между Builds, Archaeology, Reputation, Governance, Taxes и связанными аддонами;
- сохранены только реальные обязательные зависимости, а необязательные API-интеграции больше не создают барьеры старта Paper/Purpur;
- добавлена автоматическая проверка `depend`, `softdepend` и `loadbefore` на циклы по правилам загрузчика Paper;
- обновлены Archaeology до 0.1.5, Builds до 0.5.1, Governance до 0.1.3, Reputation до 0.1.3 и Taxes до 0.1.1;
- подтверждено, что Expeditions требует установленный NeverLandTownyCamps 0.1.6.

## NeverLand Towny Suite 0.5.2 — 2026-09-02

- NeverLandTownyBuilds обновлён до 0.5.0 и получил все 37 подготовленных моделей;
- в рабочий JAR перенесены 25 497 блоков, 185 этапов и состояния 46 полноценных дверей;
- каталог `/t builds` расширен с 25 до 62 проектов и разбит на три страницы;
- Дороги и Мостовая служба зарегистрированы как направленные линейные проекты;
- исходный JSON-пакет и компактные серверные ресурсы проверяются на точное совпадение;
- release workflow теперь заново собирает все 14 JAR перед публикацией и запускает smoke-проверки Builds.

## NeverLand Towny Suite 0.5.1 — 2026-09-02

- исправлено разрешение Paper API 26.2 в Gradle при JDK 25 и целевом bytecode Java 17;
- `compileOnly`-зависимости добавлены в classpath автономных smoke-проверок;
- CI собирает JAR каждого модуля и компилирует все smoke-классы без ошибочного запуска JUnit;
- подтверждена успешная сборка всех 14 аддонов в GitHub Actions.

## NeverLand Towny Suite 0.5.0 — 2026-09-02

- впервые опубликован единый GitHub-монорепозиторий всех 14 аддонов;
- добавлены актуальные исходники и готовые JAR-файлы каждого модуля;
- обновлены Archaeology до 0.1.4, Builds до 0.4.0, Camps до 0.1.6 и Expeditions до 0.1.4;
- включён NeverLandTownyTaxes 0.1.0;
- синхронизированы версии в Maven/Gradle, `plugin.yml` и документации;
- добавлен пакет из 37 моделей зданий и пяти стадий строительства для NeverLandTownyBuilds;
- добавлены автоматические проверки и формирование релизных архивов с SHA-256.
- Gradle настроен на разрешение Paper API для JVM 25 при сохранении целевого bytecode Java 17.

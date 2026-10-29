package ru.neverland.townycontrol;

import java.util.*;
import java.util.function.Predicate;
import ru.neverland.core.AuditRecord;

enum AuditCategory implements Predicate<AuditRecord> {
    ALL("Все операции","BOOK",Set.of()),
    BANKS("Денежные переводы","GOLD_INGOT",Set.of("BANK_TRANSFER","BANK_COMMAND")),
    BANK_ENTRIES("Списания и зачисления","GOLD_NUGGET",Set.of("BANK_LEG","TREASURY_ASYNC_DEBIT","TREASURY_ASYNC_REFUND","TREASURY_ASYNC_REVIEW")),
    TRADE("Торговля и поставки","MINECART",Set.of("CARAVAN_DEAL","TRADE_PAYMENT","TRADE_COMPENSATION","TRADE_TARIFF","TRADE_REFUND","SUPPLY_AGREEMENT","SUPPLY_DEAL")),
    MARKETS("Рынок и магазины","EMERALD",Set.of("MARKET_DEAL","SHOP_DEAL","MARKET_DELIVERY","SHOP_DELIVERY")),
    STORAGE("Ресурсы и склады","CHEST",Set.of("WAREHOUSE_TRADE","WAREHOUSE_LEG","STORAGE_PLAYER")),
    UPKEEP("Содержание и экономия","FURNACE",Set.of("UPKEEP_SAVINGS")),
    RESOURCE_PLANS("Резервы и снабжение города","BARREL",Set.of("PURPOSE_RESOURCES")),
    RAIDS("Рейды и добыча","TNT",Set.of("RAID_HOURS","RAID_SUPPLY","RAID_CHUNK","RAID_TNT_DENIED","RAID_DAMAGE","RAID_LOOT_PROTECTION","RAID_CANCELLED","RAID_CAMP","RAID_DECLARED","RAID_CORE","RAID_LOOT_MONEY","RAID_LOOT_RESOURCES","RAID_LOOT_STATE","RAID_LOOT_REVIEW","RAID_LOOT_RECONCILE")),
    BUILDING_REPAIRS("Ремонт зданий","ANVIL",Set.of("BUILDING_REPAIR","BUILDING_REPAIR_REVIEW","BUILDING_REPAIR_RECONCILE")),
    COMPANIES("Компании","IRON_INGOT",Set.of("COMPANY_PAYROLL_SETTINGS","COMPANY_PAYMENT","COMPANY_CONTRACT","COMPANY_CONTRACT_COST")),
    COMPANY_SALARIES("Зарплаты компаний","EMERALD",Set.of("COMPANY_SALARY")),
    CITY_CONTRIBUTIONS("Вклад жителей и благодарности","PLAYER_HEAD",Set.of("CITY_COMMENDATION","CITY_DEFENSE")),
    EXPANSION("Расширение территории","MAP",Set.of("CITY_EXPANSION")),
    CONSTRUCTION("Стройки и возвраты","BRICKS",Set.of("CITY_STYLE","CONSTRUCTION_REFUND","BUILD_CONTRIBUTION")),
    EVENT_PLANS("Решения при событиях","CLOCK",Set.of("CITY_EVENT_PLAN")),
    FIRE_REPAIRS("Ремонт после пожара","ANVIL",Set.of("FIRE_REPAIR")),
    MOB_FARMS("Участки и фермы мобов","SPAWNER",Set.of("MOB_FARM_ZONE")),
    CONTRACTS("Контракты","WRITABLE_BOOK",Set.of("CONTRACT_PAYMENT","CONTRACT_DELIVERY")),
    PUBLIC_ORDERS("Государственные заказы","WRITABLE_BOOK",Set.of("PUBLIC_ORDER_REWARD")),
    SERVER_BUILDS("Сметы серверных строек","BRICKS",Set.of("SERVER_PROJECT")),
    FISHING("Рыбалка заданий","FISHING_ROD",Set.of("FISHING_CATCH")),
    CAMP_ACCESS("Доступ в лагеря","CAMPFIRE",Set.of("CAMP_GUEST")),
    EXPEDITIONS("Экспедиции","COMPASS",Set.of("EXPEDITION_PLAN")),
    ARCHAEOLOGY("Археология и реставрация","BRUSH",Set.of("ARCHAEOLOGY_FRAGMENT","ARCHAEOLOGY_RESTORATION")),
    RESEARCH("Технологические лицензии","ENCHANTED_BOOK",Set.of("RESEARCH_LICENSE","RESEARCH_LICENSE_PAYMENT","RESEARCH_LICENSE_REFUND")),
    SEASONS("Сезонные запасы и ярмарки","PUMPKIN",Set.of("CITY_SEASON_STOCK","CITY_SEASON_FAIR")),
    SALARIES("Зарплаты профессий","GOLDEN_PICKAXE",Set.of("JOB_SALARY")),
    TAXES("Налоги","SUNFLOWER",Set.of("TAX_TRANSFER","TAX_RECONCILE")),
    IDEOLOGIES("Идеологии и реформы","BOOKSHELF",Set.of("CITY_IDEOLOGY")),
    POLITICS("Политики и управление","LECTERN",Set.of("POLICY_SELECTION","COUNCIL_ACCESS","COUNCIL_DELEGATION","DIPLOMACY_AID")),
    CITIZENSHIP("Гражданство","WRITABLE_BOOK",Set.of("CITIZEN_STATUS","CITIZEN_APPLICATION","CITIZEN_RULES")),
    ENVIRONMENT("Экология города","OAK_SAPLING",Set.of("CITY_ENVIRONMENT")),
    ACHIEVEMENTS("Достижения и награды","NETHER_STAR",Set.of("CITY_ACHIEVEMENT","ACHIEVEMENT_REWARD")),
    QUESTS("Городские проекты","KNOWLEDGE_BOOK",Set.of("CITY_PROJECT")),
    TOWN_DELETION("Удаление городов","GRASS_BLOCK",Set.of("ADMIN_TOWN_DELETE")),
    ADMIN("Действия администрации","COMMAND_BLOCK",Set.of("ADMIN_ACTIVITY","ADMIN_MODULE","ADMIN_INCOME")),
    OTHER("Прочие операции","PAPER",Set.of());
    final String title,icon;final Set<String> kinds;
    AuditCategory(String title,String icon,Set<String> kinds){this.title=title;this.icon=icon;this.kinds=kinds;}
    static List<AuditCategory> page(int page){if(page<1||page>100)throw new IllegalArgumentException("Неверная страница категорий");return Arrays.stream(values()).skip((page-1L)*36).limit(36).toList();}
    int page(){return ordinal()/36+1;}
    @Override public boolean test(AuditRecord record){return this==ALL||this==OTHER?this==ALL||Arrays.stream(values()).filter(c->c!=ALL&&c!=OTHER).noneMatch(c->c.kinds.contains(record.kind())):kinds.contains(record.kind());}
    static String kind(String kind){return switch(kind){
        case "TREASURY_ASYNC_DEBIT"->"Подтверждаемое списание казны";case "TREASURY_ASYNC_REFUND"->"Подтверждаемый возврат казны";case "TREASURY_ASYNC_REVIEW"->"Сверка квитанции казны";
        case "EXPEDITION_PLAN"->"План, роли и этапы экспедиции";case "ARCHAEOLOGY_FRAGMENT"->"Фрагменты артефактов";case "ARCHAEOLOGY_RESTORATION"->"Сборка и выдача артефакта";
        case "RESEARCH_LICENSE"->"Технологическая лицензия";case "RESEARCH_LICENSE_PAYMENT"->"Оплата лицензии";case "RESEARCH_LICENSE_REFUND"->"Возврат лицензии";
        case "CITY_SEASON_STOCK"->"Подготовка сезонного запаса";case "CITY_SEASON_FAIR"->"Обмен на сезонной ярмарке";
        case "CITY_IDEOLOGY"->"Переход и оплата реформы";case "CITIZEN_STATUS"->"Назначение гражданства";case "CITIZEN_APPLICATION"->"Заявление и поручительство";case "CITIZEN_RULES"->"Условия гражданства";case "COUNCIL_DELEGATION"->"Временные полномочия жителя";case "COUNCIL_ACCESS"->"Права жителей и должностей";case "POLICY_SELECTION"->"Выбор политик города";case "UPKEEP_SAVINGS"->"Остановка и возобновление здания";case "PURPOSE_RESOURCES"->"Целевой резерв стратегических ресурсов";case "SERVER_PROJECT"->"Смета и резерв серверной стройки";case "FISHING_CATCH"->"Подтверждённый улов задания";case "CAMP_GUEST"->"Гостевые права лагеря";
        case "CITY_EXPANSION"->"План расширения города";case "DIPLOMACY_AID"->"Обязательство взаимной помощи";case "CITY_STYLE"->"Выбор стиля города";case "CITY_COMMENDATION"->"Благодарность мэра";case "CITY_DEFENSE"->"Защита города";case "COMPANY_PAYROLL_SETTINGS"->"Ставки и бюджет зарплат компании";case "COMPANY_SALARY"->"Зарплата сотруднику компании";case "RAID_HOURS"->"Расписание нападений";case "RAID_SUPPLY"->"Доставка и расход военных запасов";
        case "BUILDING_REPAIR"->"Оплата и выполнение ремонта";case "BUILDING_REPAIR_REVIEW"->"Ремонт: нужна сверка";case "BUILDING_REPAIR_RECONCILE"->"Администратор сверил ремонт";
        case "RAID_CHUNK"->"Захват и освобождение участка";case "RAID_TNT_DENIED"->"Заблокированный поджиг TNT";case "RAID_DAMAGE"->"Урон TNT и прочность";case "RAID_LOOT_PROTECTION"->"Защита и расчёт добычи";case "RAID_CANCELLED"->"Остановка рейда";case "RAID_CAMP"->"Осадный лагерь";case "RAID_DECLARED"->"Начало рейда";case "RAID_CORE"->"Захват кафедры ратуши";case "RAID_LOOT_MONEY"->"Захваченные деньги";case "RAID_LOOT_RESOURCES"->"Захваченные ресурсы";case "RAID_LOOT_STATE"->"Выдача добычи";case "RAID_LOOT_REVIEW"->"Добыча: нужна сверка";case "RAID_LOOT_RECONCILE"->"Администратор сверил добычу";
        case "CITY_EVENT_PLAN"->"План действий при городском событии";case "FIRE_REPAIR"->"Подтверждение ремонта участка";
        case "MOB_FARM_ZONE"->"Защита участка от мобов";
        case "BUILD_CONTRIBUTION"->"Материалы для строительства";
        case "CONSTRUCTION_REFUND"->"Оплата и возврат строительства";
        case "ADMIN_TOWN_DELETE"->"Удаление города и откат территории";
        case "PUBLIC_ORDER_REWARD"->"Награда государственного заказа";case "JOB_SALARY"->"Зарплата профессии";case "ADMIN_INCOME"->"Сверка денежной выплаты";case "BANK_TRANSFER"->"Перевод между счетами";case "BANK_COMMAND"->"Банковская команда";case "BANK_LEG"->"Изменение счёта";
        case "CARAVAN_DEAL"->"Сделка каравана";case "TRADE_PAYMENT"->"Оплата торговли";case "TRADE_TARIFF"->"Торговый тариф";case "TRADE_REFUND"->"Возврат торговли";case "TRADE_COMPENSATION"->"Компенсация задержки каравана";case "SUPPLY_AGREEMENT"->"Договор поставки";case "SUPPLY_DEAL"->"Поставка";
        case "MARKET_DEAL"->"Сделка рынка";case "SHOP_DEAL"->"Сделка магазина";case "MARKET_DELIVERY"->"Выдача рынка";case "SHOP_DELIVERY"->"Выдача магазина";
        case "WAREHOUSE_TRADE"->"Обмен со складом";case "WAREHOUSE_LEG"->"Изменение склада";case "STORAGE_PLAYER"->"Игрок и хранилище";
        case "COMPANY_CONTRACT_COST"->"Смета и затраты договора компании";case "COMPANY_PAYMENT"->"Оплата компании";case "COMPANY_CONTRACT"->"Контракт компании";case "CONTRACT_PAYMENT"->"Оплата контракта";case "CONTRACT_DELIVERY"->"Выдача контракта";case "TAX_RECONCILE"->"Сверка налогового перевода";case "TAX_TRANSFER"->"Перечисление налога";
        case "CITY_ENVIRONMENT"->"Изменение уровня загрязнения";case "CITY_ACHIEVEMENT"->"Достижение города";case "ACHIEVEMENT_REWARD"->"Оформление награды";case "CITY_PROJECT"->"Этап городского проекта";case "ADMIN_ACTIVITY"->"Управление задачей";case "ADMIN_MODULE"->"Управление модулем";default->kind;};}
    static String outcome(String value){if(value.startsWith("PROCUREMENT_"))return "Закупка: "+outcome(value.substring(12));return switch(value){case "SWAPPED"->"Обмен выполнен";case "PUBLISHING"->"Заказ сохраняется";case "PUBLISHED"->"Заказ опубликован";case "SETTLED"->"Расчёт завершён";case "CLOSED"->"Завершено";case "EXPIRED"->"Срок истёк";case "QUOTE"->"Смета подготовлена";case "FUNDING"->"Ожидается оплата";case "REFUNDING"->"Возврат выполняется";case "TREASURY","INVOKING"->"Ожидается квитанция казны";case "WARNING"->"Предупреждение";case "DECISION"->"Ожидается решение";case "RECOVERY"->"Восстановление";case "DRAFT"->"Черновик";case "APPROVED"->"Согласовано";case "INTENT"->"Исполнение подготовлено";case "PARTIAL"->"Выполнено частично";case "CONFIRMED"->"Подтверждено";case "NOT_APPLIED"->"Не выполнялось";case "RECONCILED"->"Результат сверен";case "RECONCILE_INTENT"->"Запрошена сверка";case "HELD"->"Удерживается до завершения";case "SUSPENDED"->"Приостановлено городом";case "RESUMED"->"Возобновлено городом";case "SUPPLY_REQUESTED"->"Ожидает снабжения";case "SUPPLY_HELD"->"Ресурсы отложены";case "SUPPLY_RETURNED"->"План отменён, резерв освобождён";case "GRANTED"->"Доступ выдан";case "REVOKED"->"Доступ отменён";case "PLAN"->"Смета сохранена";case "RESERVE"->"Материалы обеспечены";case "CANCEL"->"Смета отменена";case "SCHEDULED"->"Запланировано";case "CONSUMED"->"Израсходовано";case "CHOSEN"->"Выбрано";case "STARTED"->"Удержание начато";case "INTERRUPTED"->"Удержание прервано";case "CAPTURED"->"Участок захвачен";case "RELEASED"->"Контроль снят";case "DENIED"->"Запрещено";case "REQUESTED"->"Отмена запрошена";case "MONEY_RETURNED"->"Деньги возвращены";case "PURCHASED"->"Стройка оплачена";case "COMPLETED","COMPLETE","SUCCESS","DONE"->"Выполнено";case "OBSERVED"->"Зафиксирована команда";case "CREDIT"->"Зачисление";case "DEBIT"->"Списание";case "CANCELLED"->"Отменено";case "REFUNDED","RETURNED"->"Возвращено";case "DELIVERED"->"Доставлено";case "REJECTED"->"Отклонено";case "UNCONFIRMED"->"Не подтверждено";case "PREPARED"->"Подготовлено";case "PAID"->"Оплачено";case "REVIEW","REVIEW_CORE","REVIEW_DEBIT","REVIEW_CREDIT","REVIEW_BANK","REVIEW_WORLD"->"Нужна сверка";case "READY"->"Готово";case "LOST"->"Лагерь потерян";case "ARMED"->"Проверяется кафедра";case "BREACHED"->"Кафедра захвачена";case "DEBITED"->"Деньги списаны";case "FUNDED"->"Деньги переведены, ожидается доставка";case "DEBITING","CREDITING","PAYING"->"Платёж выполняется";case "VOID"->"Захват не состоялся";case "FAILED","FAILURE"->"Ошибка";default->value;};}
}

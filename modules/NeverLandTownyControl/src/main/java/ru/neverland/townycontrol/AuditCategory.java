package ru.neverland.townycontrol;

import java.util.*;
import java.util.function.Predicate;
import ru.neverland.core.AuditRecord;

enum AuditCategory implements Predicate<AuditRecord> {
    ALL("Все операции","BOOK",Set.of()),
    BANKS("Денежные переводы","GOLD_INGOT",Set.of("BANK_TRANSFER","BANK_COMMAND")),
    BANK_ENTRIES("Списания и зачисления","GOLD_NUGGET",Set.of("BANK_LEG")),
    TRADE("Торговля и поставки","MINECART",Set.of("CARAVAN_DEAL","TRADE_PAYMENT","TRADE_TARIFF","TRADE_REFUND","SUPPLY_AGREEMENT","SUPPLY_DEAL")),
    MARKETS("Рынок и магазины","EMERALD",Set.of("MARKET_DEAL","SHOP_DEAL","MARKET_DELIVERY","SHOP_DELIVERY")),
    STORAGE("Ресурсы и склады","CHEST",Set.of("WAREHOUSE_TRADE","WAREHOUSE_LEG","STORAGE_PLAYER")),
    COMPANIES("Компании","IRON_INGOT",Set.of("COMPANY_PAYMENT","COMPANY_CONTRACT")),
    CONSTRUCTION("Стройки и возвраты","BRICKS",Set.of("CONSTRUCTION_REFUND")),
    FIRE_REPAIRS("Ремонт после пожара","ANVIL",Set.of("FIRE_REPAIR")),
    MOB_FARMS("Участки и фермы мобов","SPAWNER",Set.of("MOB_FARM_ZONE")),
    CONTRACTS("Контракты","WRITABLE_BOOK",Set.of("CONTRACT_PAYMENT","CONTRACT_DELIVERY")),
    PUBLIC_ORDERS("Государственные заказы","WRITABLE_BOOK",Set.of("PUBLIC_ORDER_REWARD")),
    SALARIES("Зарплаты профессий","GOLDEN_PICKAXE",Set.of("JOB_SALARY")),
    TAXES("Налоги","SUNFLOWER",Set.of("TAX_TRANSFER")),
    ENVIRONMENT("Экология города","OAK_SAPLING",Set.of("CITY_ENVIRONMENT")),
    ACHIEVEMENTS("Достижения и награды","NETHER_STAR",Set.of("CITY_ACHIEVEMENT","ACHIEVEMENT_REWARD")),
    QUESTS("Городские проекты","KNOWLEDGE_BOOK",Set.of("CITY_PROJECT")),
    TOWN_DELETION("Удаление городов","GRASS_BLOCK",Set.of("ADMIN_TOWN_DELETE")),
    ADMIN("Действия администрации","COMMAND_BLOCK",Set.of("ADMIN_ACTIVITY","ADMIN_MODULE","ADMIN_INCOME")),
    OTHER("Прочие операции","PAPER",Set.of());
    final String title,icon;final Set<String> kinds;
    AuditCategory(String title,String icon,Set<String> kinds){this.title=title;this.icon=icon;this.kinds=kinds;}
    @Override public boolean test(AuditRecord record){return this==ALL||this==OTHER?this==ALL||Arrays.stream(values()).filter(c->c!=ALL&&c!=OTHER).noneMatch(c->c.kinds.contains(record.kind())):kinds.contains(record.kind());}
    static String kind(String kind){return switch(kind){
        case "FIRE_REPAIR"->"Подтверждение ремонта участка";
        case "MOB_FARM_ZONE"->"Защита участка от мобов";
        case "CONSTRUCTION_REFUND"->"Оплата и возврат строительства";
        case "ADMIN_TOWN_DELETE"->"Удаление города и откат территории";
        case "PUBLIC_ORDER_REWARD"->"Награда государственного заказа";case "JOB_SALARY"->"Зарплата профессии";case "ADMIN_INCOME"->"Сверка денежной выплаты";case "BANK_TRANSFER"->"Перевод между счетами";case "BANK_COMMAND"->"Банковская команда";case "BANK_LEG"->"Изменение счёта";
        case "CARAVAN_DEAL"->"Сделка каравана";case "TRADE_PAYMENT"->"Оплата торговли";case "TRADE_TARIFF"->"Торговый тариф";case "TRADE_REFUND"->"Возврат торговли";case "SUPPLY_AGREEMENT"->"Договор поставки";case "SUPPLY_DEAL"->"Поставка";
        case "MARKET_DEAL"->"Сделка рынка";case "SHOP_DEAL"->"Сделка магазина";case "MARKET_DELIVERY"->"Выдача рынка";case "SHOP_DELIVERY"->"Выдача магазина";
        case "WAREHOUSE_TRADE"->"Обмен со складом";case "WAREHOUSE_LEG"->"Изменение склада";case "STORAGE_PLAYER"->"Игрок и хранилище";
        case "COMPANY_PAYMENT"->"Оплата компании";case "COMPANY_CONTRACT"->"Контракт компании";case "CONTRACT_PAYMENT"->"Оплата контракта";case "CONTRACT_DELIVERY"->"Выдача контракта";case "TAX_TRANSFER"->"Перечисление налога";
        case "CITY_ENVIRONMENT"->"Изменение уровня загрязнения";case "CITY_ACHIEVEMENT"->"Достижение города";case "ACHIEVEMENT_REWARD"->"Оформление награды";case "CITY_PROJECT"->"Этап городского проекта";case "ADMIN_ACTIVITY"->"Управление задачей";case "ADMIN_MODULE"->"Управление модулем";default->kind;};}
    static String outcome(String value){return switch(value){case "REQUESTED"->"Отмена запрошена";case "MONEY_RETURNED"->"Деньги возвращены";case "PURCHASED"->"Стройка оплачена";case "COMPLETED","COMPLETE","SUCCESS","DONE"->"Выполнено";case "OBSERVED"->"Зафиксирована команда";case "CREDIT"->"Зачисление";case "DEBIT"->"Списание";case "CANCELLED"->"Отменено";case "REFUNDED","RETURNED"->"Возвращено";case "DELIVERED"->"Доставлено";case "REJECTED"->"Отклонено";case "UNCONFIRMED"->"Не подтверждено";case "PREPARED"->"Подготовлено";case "PAID"->"Оплачено";case "REVIEW"->"Нужна сверка";case "FAILED","FAILURE"->"Ошибка";default->value;};}
}

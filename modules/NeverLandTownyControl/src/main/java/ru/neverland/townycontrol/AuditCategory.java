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
    CONTRACTS("Контракты","WRITABLE_BOOK",Set.of("CONTRACT_PAYMENT","CONTRACT_DELIVERY")),
    TAXES("Налоги","SUNFLOWER",Set.of("TAX_TRANSFER")),
    ADMIN("Действия администрации","COMMAND_BLOCK",Set.of("ADMIN_ACTIVITY","ADMIN_MODULE")),
    OTHER("Прочие операции","PAPER",Set.of());
    final String title,icon;final Set<String> kinds;
    AuditCategory(String title,String icon,Set<String> kinds){this.title=title;this.icon=icon;this.kinds=kinds;}
    @Override public boolean test(AuditRecord record){return this==ALL||this==OTHER?this==ALL||Arrays.stream(values()).filter(c->c!=ALL&&c!=OTHER).noneMatch(c->c.kinds.contains(record.kind())):kinds.contains(record.kind());}
    static String kind(String kind){return switch(kind){
        case "BANK_TRANSFER"->"Перевод между счетами";case "BANK_COMMAND"->"Банковская команда";case "BANK_LEG"->"Изменение счёта";
        case "CARAVAN_DEAL"->"Сделка каравана";case "TRADE_PAYMENT"->"Оплата торговли";case "TRADE_TARIFF"->"Торговый тариф";case "TRADE_REFUND"->"Возврат торговли";case "SUPPLY_AGREEMENT"->"Договор поставки";case "SUPPLY_DEAL"->"Поставка";
        case "MARKET_DEAL"->"Сделка рынка";case "SHOP_DEAL"->"Сделка магазина";case "MARKET_DELIVERY"->"Выдача рынка";case "SHOP_DELIVERY"->"Выдача магазина";
        case "WAREHOUSE_TRADE"->"Обмен со складом";case "WAREHOUSE_LEG"->"Изменение склада";case "STORAGE_PLAYER"->"Игрок и хранилище";
        case "COMPANY_PAYMENT"->"Оплата компании";case "COMPANY_CONTRACT"->"Контракт компании";case "CONTRACT_PAYMENT"->"Оплата контракта";case "CONTRACT_DELIVERY"->"Выдача контракта";case "TAX_TRANSFER"->"Перечисление налога";
        case "ADMIN_ACTIVITY"->"Управление задачей";case "ADMIN_MODULE"->"Управление модулем";default->kind;};}
    static String outcome(String value){return switch(value){case "COMPLETED","COMPLETE","SUCCESS","DONE"->"Выполнено";case "OBSERVED"->"Зафиксирована команда";case "CREDIT"->"Зачисление";case "DEBIT"->"Списание";case "CANCELLED"->"Отменено";case "REFUNDED","RETURNED"->"Возвращено";case "DELIVERED"->"Доставлено";case "REJECTED"->"Отклонено";case "UNCONFIRMED"->"Не подтверждено";case "PREPARED"->"Подготовлено";case "PAID"->"Оплачено";case "FAILED","FAILURE"->"Ошибка";default->value;};}
}

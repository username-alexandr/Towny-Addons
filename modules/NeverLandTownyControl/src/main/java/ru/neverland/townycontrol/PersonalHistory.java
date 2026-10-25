package ru.neverland.townycontrol;

import java.util.*;
import java.util.function.Predicate;
import ru.neverland.core.AuditRecord;

/** An explicit public projection. Never expose admin notes, commands, raw payloads or another account. */
final class PersonalHistory {
    enum Category { ALL("Все операции"), MONEY("Деньги"), RESOURCES("Материалы"), BANK("Движения счёта");
        final String label;Category(String label){this.label=label;}
    }
    private static final Set<String> MONEY=Set.of("PUBLIC_ORDER_REWARD","JOB_SALARY","BANK_TRANSFER","CONTRACT_PAYMENT","COMPANY_PAYMENT","TRADE_PAYMENT","TRADE_REFUND","MARKET_DEAL","SHOP_DEAL","CONSTRUCTION_REFUND");
    private static final Set<String> RESOURCES=Set.of("BUILD_CONTRIBUTION","STORAGE_PLAYER","MARKET_DELIVERY","SHOP_DELIVERY","CONTRACT_DELIVERY","WAREHOUSE_TRADE");
    static Predicate<AuditRecord> filter(UUID player,Category category){return r->{
        if(!r.player(player.toString()))return false;
        // Account observations are a separate view: they may also have a reward/transfer receipt.
        if(category==Category.BANK)return r.kind().equals("BANK_LEG");
        return category!=Category.RESOURCES&&MONEY.contains(r.kind())||category!=Category.MONEY&&RESOURCES.contains(r.kind());
    };}
    static String title(AuditRecord r){return switch(r.kind()){
        case "BUILD_CONTRIBUTION"->"Материалы для строительства";
        case "PUBLIC_ORDER_REWARD"->"Награда за личный заказ";
        case "JOB_SALARY"->"Заработок на профессии";
        case "STORAGE_PLAYER"->"Передача материалов через склад";
        case "CONSTRUCTION_REFUND"->"Строительство и возврат";
        case "BANK_LEG"->"Изменение личного счёта";
        default->AuditCategory.kind(r.kind());
    };}
    static List<String> lines(AuditRecord r,UUID player){
        var lines=new ArrayList<String>();lines.add("Результат: "+(r.kind().equals("STORAGE_PLAYER")?"Изменение склада сохранено":AuditCategory.outcome(r.outcome())));
        if(!r.money().isEmpty())lines.add("Сумма: "+r.money()+" монет");
        if(r.quantity()>0)lines.add("Количество: "+r.quantity());
        if(!r.from().type().equals("UNKNOWN")&&!r.from().type().equals("SYSTEM"))lines.add("Откуда: "+party(r.from(),player));
        if(!r.to().type().equals("UNKNOWN")&&!r.to().type().equals("SYSTEM"))lines.add("Куда: "+party(r.to(),player));
        if(r.kind().equals("BUILD_CONTRIBUTION"))lines.add(clean(r.details()));
        if(r.kind().equals("STORAGE_PLAYER"))lines.add("Итог изменения склада за одно открытие.");
        lines.add("Квитанция: "+r.id());return List.copyOf(lines);
    }
    private static String party(AuditRecord.Party p,UUID player){return p.type().equals("PLAYER")&&p.id().equals(player.toString())?"Вы":clean(p.name());}
    static String clean(String value){String text=value.replaceAll("(?i)§[0-9a-fk-orx]","").replaceAll("[\\p{Cntrl}]"," ");return text.substring(0,Math.min(160,text.length()));}
}

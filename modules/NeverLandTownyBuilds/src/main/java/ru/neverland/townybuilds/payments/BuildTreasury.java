package ru.neverland.townybuilds.payments;

import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.ApiServices;

/** Installed but unavailable Treasury never falls back to a direct withdrawal. */
public final class BuildTreasury {
    private static final String PLUGIN="NeverLandTownyTreasuryPlus",API="ru.neverland.townytreasury.api.TownyTreasuryApi",OWNER="NeverLandTownyBuilds";
    public boolean available(){try{ApiServices.require(PLUGIN,API,"requestDebit","debitStatus","resolveCategory");return true;}catch(RuntimeException error){return false;}}
    public String category(String raw)throws ReflectiveOperationException {Object result=ApiServices.require(PLUGIN,API,"resolveCategory").invoke("resolveCategory",new Class<?>[]{String.class},raw);if(!(result instanceof String value)||value.isBlank())throw new IllegalStateException("Категория казны недоступна");return value;}
    public Map<?,?> status(BuildPayment.Terms t)throws ReflectiveOperationException {
        Object value=ApiServices.require(PLUGIN,API,"debitStatus").invoke("debitStatus",new Class<?>[]{UUID.class},t.id());
        if(value instanceof Map<?,?> row&&"ABSENT".equals(row.get("status"))&&t.id().toString().equals(row.get("operation")))return row;
        return verify(value,t);
    }
    public CompletionStage<Map<?,?>> request(BuildPayment.Terms t)throws ReflectiveOperationException {
        Object value=ApiServices.require(PLUGIN,API,"requestDebit").invoke("requestDebit",new Class<?>[]{UUID.class,UUID.class,String.class,String.class,long.class,String.class,UUID.class,String.class},t.id(),t.town(),t.category(),t.source(),t.cents(),t.reason(),t.actor(),OWNER);
        if(!(value instanceof CompletionStage<?> stage))throw new IllegalStateException("Казна не вернула подтверждение запроса");
        return stage.thenApply(row->verify(row,t));
    }
    static Map<?,?> verify(Object value,BuildPayment.Terms t){
        if(!(value instanceof Map<?,?> row)||!t.id().toString().equals(row.get("operation"))||!t.town().toString().equals(row.get("town"))
                ||!t.category().equals(row.get("category"))||!t.source().equals(row.get("source"))||!(row.get("cents") instanceof Long cents)||cents!=t.cents()
                ||!t.reason().equals(row.get("reason"))||!t.actor().toString().equals(row.get("actor"))||!OWNER.equals(row.get("owner"))
                ||!"DEBIT".equals(row.get("kind"))||!"".equals(row.get("backingDebit"))||!(row.get("status") instanceof String))
            throw new IllegalStateException("Квитанция казны не соответствует сохранённой оплате здания");
        if(!Set.of("PREPARED","INVOKING","PAID","REJECTED","REVIEW").contains(row.get("status")))throw new IllegalStateException("Неизвестный результат оплаты здания");
        if("PAID".equals(row.get("status"))&&!Boolean.TRUE.equals(row.get("backingAvailable")))throw new IllegalStateException("Оплата здания уже возвращена или её обеспечение требует сверки");
        return row;
    }
}

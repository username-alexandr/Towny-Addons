package ru.neverland.townytreasury.debits;

import java.util.*;
import ru.neverland.core.ApiServices;

/** A missing optional Council is distinct from an installed but unavailable policy provider. */
public final class SpendingAccess {
    private SpendingAccess(){}
    public static SpendingAdmission capture(AsyncDebit.Terms terms,Collection<AsyncDebit> receipts,long now){
        if(terms.kind()==AsyncDebit.Kind.REFUND)return exempt("REFUND",now);
        if(terms.actor().equals(SpendingAdmission.SYSTEM))return exempt("SYSTEM",now);
        var connection=ApiServices.connect("NeverLandTownyCouncil","ru.neverland.townycouncil.api.TownyCouncilApi",1,"spendingPolicy");
        if(connection.state()==ApiServices.State.NOT_INSTALLED)return exempt("NO_COUNCIL",now);
        if(!connection.ready())return exempt("UNAVAILABLE",now);
        try{
            Object reply=connection.invoke("spendingPolicy",new Class<?>[]{UUID.class,UUID.class},terms.town(),terms.actor());
            if(!(reply instanceof Map<?,?> row)||!Boolean.TRUE.equals(row.get("available"))||!terms.town().toString().equals(row.get("town"))||!terms.actor().toString().equals(row.get("resident")))return exempt("UNAVAILABLE",now);
            var usage=SpendingAdmission.usage(receipts,terms.town(),terms.actor(),now);
            return new SpendingAdmission("POLICY",integer(row,"revision"),integer(row,"perOperation"),integer(row,"perDay"),usage.spent(),usage.reserved(),now);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){return exempt("UNAVAILABLE",now);}
    }
    private static long integer(Map<?,?> row,String key){Object value=row.get(key);if(!(value instanceof Number))throw new IllegalArgumentException("Неверный ответ ограничений совета");return new java.math.BigDecimal(value.toString()).longValueExact();}
    private static SpendingAdmission exempt(String mode,long now){return new SpendingAdmission(mode,0,0,0,0,0,now);}
}

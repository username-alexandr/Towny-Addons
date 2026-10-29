package ru.neverland.townyupkeep.integration;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.ApiServices;
import ru.neverland.townyupkeep.model.Entry.*;
import ru.neverland.townyupkeep.model.PaymentProcessor.Outcome;

/** Polls one durable invoice ID; old MONEY_PENDING invoices never enter this route. */
public final class UpkeepTreasury {
    private static final String OWNER="NeverLandTownyUpkeep";
    private static final UUID SYSTEM=new UUID(0,0);
    private final Set<UUID> submitting=ConcurrentHashMap.newKeySet();
    private static String reason(Invoice invoice){return "NeverLand Upkeep "+invoice.id();}
    public String category(String project)throws Exception{
        Object value=ApiServices.require("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.api.TownyTreasuryApi","resolveCategory").invoke("resolveCategory",new Class<?>[]{String.class},"project/"+project);
        if(!(value instanceof String category)||!Set.of("construction","army","infrastructure","social","free").contains(category))throw new IllegalArgumentException("Не удалось определить статью содержания");return category;
    }
    public Outcome poll(Key key,Invoice invoice)throws Exception{
        if(invoice.phase()!=Phase.MONEY_REQUESTED)throw new IllegalArgumentException("Счёт ещё не передан казне");
        var api=ApiServices.connect("NeverLandTownyTreasuryPlus","ru.neverland.townytreasury.api.TownyTreasuryApi",1,"requestDebit","debitStatus");
        if(!api.ready())return Outcome.WAITING; // A disabled/removed provider does not authorize a direct bank call.
        Object raw=api.invoke("debitStatus",new Class<?>[]{UUID.class},invoice.id());
        if(!(raw instanceof Map<?,?> proof))throw new IllegalArgumentException("Нет квитанции казны");
        if("ABSENT".equals(proof.get("status"))){
            if(!invoice.id().toString().equals(String.valueOf(proof.get("operation"))))throw new IllegalArgumentException("ID запроса не совпадает со счётом");
            if(submitting.add(invoice.id()))try{
                var pending=(CompletionStage<?>)api.invoke("requestDebit",new Class<?>[]{UUID.class,UUID.class,String.class,String.class,long.class,String.class,UUID.class,String.class},invoice.id(),key.town(),invoice.category(),"upkeep",invoice.cost().money(),reason(invoice),SYSTEM,OWNER);
                pending.whenComplete((v,error)->submitting.remove(invoice.id()));
            }catch(Exception error){submitting.remove(invoice.id());throw error;}
            return Outcome.WAITING;
        }
        return proof(key,invoice,proof);
    }
    public static Outcome proof(Key key,Invoice invoice,Map<?,?> proof){
        long cents;try{cents=new BigDecimal(String.valueOf(proof.get("cents"))).longValueExact();}catch(RuntimeException error){throw new IllegalArgumentException("Неверная сумма квитанции казны");}
        if(invoice.category().isEmpty()||!invoice.id().toString().equals(String.valueOf(proof.get("operation")))||!key.town().toString().equals(String.valueOf(proof.get("town")))||cents!=invoice.cost().money()||!invoice.category().equals(proof.get("category"))||!"upkeep".equals(proof.get("source"))||!OWNER.equals(proof.get("owner"))||!SYSTEM.toString().equals(String.valueOf(proof.get("actor")))||!reason(invoice).equals(proof.get("reason"))||!"DEBIT".equals(proof.get("kind")))throw new IllegalArgumentException("Квитанция казны не совпадает со счётом содержания");
        return switch(String.valueOf(proof.get("status"))){
            case "PAID"->Boolean.TRUE.equals(proof.get("backingAvailable"))?Outcome.PAID:Outcome.REVIEW;
            case "REJECTED"->Outcome.REJECTED;
            case "PREPARED","INVOKING"->Outcome.WAITING;
            case "REVIEW"->Outcome.REVIEW;
            default->throw new IllegalArgumentException("Неизвестный результат оплаты содержания");
        };
    }
}

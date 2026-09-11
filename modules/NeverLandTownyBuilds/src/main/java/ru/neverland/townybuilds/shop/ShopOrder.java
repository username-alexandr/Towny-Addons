package ru.neverland.townybuilds.shop;
import java.util.*;
import ru.neverland.core.PurchaseSaga;
/** Immutable cents, item and participants are fixed before any stock or money mutation. */
public record ShopOrder(UUID id,UUID seller,UUID buyer,String material,int amount,long unit,long created,
                        String paymentStep,long check,String note,boolean finalized) implements PurchaseSaga.Purchase<ShopOrder> {
    public static final Set<String> STEPS=Set.of("PREPARED","DEBIT_PENDING","PAID","DELIVERED","CREDIT_PENDING","RETURNING","COMPLETE","CANCELLED");
    public ShopOrder {
        if(id==null||seller==null||buyer==null||material==null||!material.matches("[A-Z0-9_]+")||amount<1||amount>64||unit<1||unit>100_000_000_000L/amount||created<0||check<0||!STEPS.contains(paymentStep)||note==null||finalized&&!Set.of("COMPLETE","CANCELLED").contains(paymentStep))throw new IllegalArgumentException("Повреждена покупка городской лавки");
    }
    public boolean city(){return false;}
    public long total(){return unit*amount;}
    public ShopOrder paymentStep(String phase,long at,String reason){return new ShopOrder(id,seller,buyer,material,amount,unit,created,phase,at,reason,false);}
    public ShopOrder finish(){return new ShopOrder(id,seller,buyer,material,amount,unit,created,paymentStep,check,note,true);}
    public String title(){return switch(paymentStep){case "PREPARED"->"Подготовка покупки";case "DEBIT_PENDING"->"Списание требует сверки";case "PAID","DELIVERED"->"Покупка обрабатывается";case "CREDIT_PENDING"->"Зачисление требует сверки";case "RETURNING"->"Возвращается резерв товара";case "COMPLETE"->finalized?"Покупка получена":"Можно забрать покупку";default->"Покупка отменена";};}
}

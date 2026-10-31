package cc.crafto.ui;
import java.util.*;
/** Shared pure arithmetic; no client values authorize mutations. */
public final class CivicMath {
    private CivicMath(){}
    public record MaterialNeed(String key,int remaining,int warehouse,int player,int gather){}
    public static MaterialNeed need(String key,int remaining,int warehouse,int player){
        if(remaining<0||warehouse<0||player<0)throw new IllegalArgumentException("Negative material count");
        return new MaterialNeed(key,remaining,warehouse,player,(int)Math.max(0L,(long)remaining-warehouse-player));
    }
    public static MaterialNeed placement(String key,int remaining,int warehouse,int player,boolean returned){
        var ordinary=need(key,remaining,warehouse,player);int required=returned&&remaining>0?1:remaining;
        return new MaterialNeed(key,remaining,warehouse,player,Math.max(0,required-Math.min(required,warehouse)-Math.min(required,player)));
    }
    public record Offer(String id,String title,String type,String rarity,long cents,int amount,int carried){}
    public static List<Offer> filter(List<Offer> offers,String type,String rarity,long minimum,String sort,boolean ready){
        if(minimum<0)throw new IllegalArgumentException("Negative reward");
        Comparator<Offer> order=switch(sort){case "rarity"->Comparator.<Offer>comparingInt(o->OrderRarityStyle.rank(o.rarity())).reversed().thenComparing(Comparator.comparingLong(Offer::cents).reversed());case "reward"->Comparator.comparingLong(Offer::cents).reversed();case "ready"->Comparator.<Offer>comparingInt(o->o.type().equals("DELIVERY")&&o.carried()>=o.amount()?0:1).thenComparing(Comparator.comparingLong(Offer::cents).reversed());default->Comparator.comparing(Offer::title);};
        return offers.stream().filter(o->type.isEmpty()||type.equals("ALL")||o.type().equals(type)).filter(o->rarity.isEmpty()||rarity.equals("ALL")||o.rarity().equals(rarity)).filter(o->o.cents()>=minimum).filter(o->!ready||o.type().equals("DELIVERY")&&o.carried()>=o.amount()).sorted(order.thenComparing(Offer::id)).toList();
    }
    public static int cell(int block,int size){if(size<1)throw new IllegalArgumentException("Invalid plot size");return Math.floorDiv(block,size);}
}

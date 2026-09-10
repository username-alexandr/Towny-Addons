package ru.neverland.townymarket;
import java.util.*;
import org.bukkit.Bukkit;
/** Read-only main-thread snapshots. Purchases require a current player quote and confirmation. */
public final class MarketApi {
    private final MarketService service;public MarketApi(MarketService service){this.service=service;}
    public record Offer(UUID id,UUID town,String item,String scope,boolean automatic,long unitCents,int available,String state){}
    public List<Offer> offers(UUID town)throws Exception{if(!Bukkit.isPrimaryThread())throw new IllegalStateException("API требует основного потока");var result=new ArrayList<Offer>();for(var l:service.listings())if(town==null||l.town().equals(town))result.add(new Offer(l.id(),l.town(),l.label(),l.scope().name(),l.auto(),service.unit(l),service.bridge.stock(l),l.state().name()));return List.copyOf(result);}
}

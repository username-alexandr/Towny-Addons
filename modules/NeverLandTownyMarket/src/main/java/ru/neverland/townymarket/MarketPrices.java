package ru.neverland.townymarket;
import java.util.*;
import java.math.*;
import static ru.neverland.townymarket.MarketData.*;
/** Demand counts paid completed trades, never views, quotes, cancellations or mere reserves. */
public final class MarketPrices {
    private MarketPrices(){}
    public static long demand(List<Demand> rows,String scope,String product,long now,int perBuyerCap){
        Map<UUID,Long> quantities=new HashMap<>();Set<UUID> seen=new HashSet<>();
        for(var d:rows)if(d.at()<=now&&d.at()>now-DAY&&d.scope().equals(scope)&&d.product().equals(product)&&seen.add(d.order()))quantities.merge(d.buyer(),(long)d.amount(),Long::sum);
        return quantities.values().stream().mapToLong(v->Math.min(Math.max(1,perBuyerCap),v)).sum();
    }
    public static long unit(long base,long stock,long demand,int target,int minBasisPoints,int maxBasisPoints){
        if(base<1||base>MAX||stock<0||demand<0||target<1||minBasisPoints<1||maxBasisPoints<minBasisPoints)throw new IllegalArgumentException("Параметры цены");
        BigDecimal factor=BigDecimal.valueOf(target).add(BigDecimal.valueOf(demand)).divide(BigDecimal.valueOf(target).add(BigDecimal.valueOf(stock)),12,RoundingMode.HALF_UP);
        factor=factor.max(BigDecimal.valueOf(minBasisPoints,4)).min(BigDecimal.valueOf(maxBasisPoints,4));
        return Math.max(1,Math.min(MAX,BigDecimal.valueOf(base).multiply(factor).setScale(0,RoundingMode.HALF_UP).longValueExact()));
    }
}

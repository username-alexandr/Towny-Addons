package ru.neverland.integration;
import java.math.*;
import java.util.*;
/** Pure, bounded arithmetic shared by all policy consumers. */
public final class PolicyEffects {
    public static final Set<String> GROUPS=Set.of("taxes","tariffs","mobilization","farmers","imports","industry");
    public static final Set<String> EFFECTS=Set.of("tax","tariff","trade_time","army","production","upkeep","happiness");
    private PolicyEffects(){}
    public static double bound(double value,double min,double max,double fallback){return Double.isFinite(value)?Math.max(min,Math.min(max,value)):fallback;}
    public static double multiply(double first,double second,double min,double max){return bound(BigDecimal.valueOf(first).multiply(BigDecimal.valueOf(second)).doubleValue(),min,max,1);}
    public static long output(long raw,double existing,double policies,long maximum){double combined=multiply(bound(existing,1,3,1),bound(policies,.25,2,1),.25,3);return BigDecimal.valueOf(raw).multiply(BigDecimal.valueOf(combined)).min(BigDecimal.valueOf(maximum)).setScale(0,RoundingMode.DOWN).longValueExact();}
    public static int capacity(int base,double bonus){return BigDecimal.valueOf(base).multiply(BigDecimal.ONE.add(BigDecimal.valueOf(bound(bonus,0,1,0)))).setScale(0,RoundingMode.DOWN).intValueExact();}
    public static boolean rosterActive(Map<UUID,UUID> roster,UUID resident,UUID town,int capacity){if(resident==null||town==null||capacity<=0||!Objects.equals(roster.get(resident),town))return false;return roster.entrySet().stream().filter(e->e.getValue().equals(town)&&e.getKey().compareTo(resident)<0).limit(capacity).count()<capacity;}
    public static boolean imports(String mode,UUID buyer,UUID seller,boolean sameNation){if(buyer==null||seller==null||buyer.equals(seller))return false;return switch(mode){case "open"->true;case "nation"->sameNation;case "closed"->false;default->false;};}
    public static double travel(double seconds,double first,double second){return seconds*multiply(1+bound(first,-.5,.5,0),1+bound(second,-.5,.5,0),.5,1.5);}
    public static double tax(double amount,double multiplier){return amount*bound(multiplier,.5,1.5,1);}
}

package ru.neverland.townydiplomacy;

import java.util.*;
import static ru.neverland.townydiplomacy.Treaty.*;

/** Pure treaty rules, with explicit time. No Bukkit calls or implicit alliance transitivity. */
public final class DiplomacyRules {
    private DiplomacyRules() { }
    public static boolean tradeBlocked(Collection<Treaty> treaties,UUID a,UUID b,long now) {
        return treaties.stream().anyMatch(t->t.active(now)&&t.pair(a,b)&&(t.type()==TreatyType.EMBARGO || t.type()==TreatyType.SANCTIONS&&(t.sanction()==SanctionScope.TRADE||t.sanction()==SanctionScope.ALL)));
    }
    public static boolean negotiationsBlocked(Collection<Treaty> treaties,UUID a,UUID b,long now) {
        return treaties.stream().anyMatch(t->t.active(now)&&t.pair(a,b)&&t.type()==TreatyType.SANCTIONS&&(t.sanction()==SanctionScope.DIPLOMATIC||t.sanction()==SanctionScope.ALL));
    }
    public static boolean hostileBlocked(Collection<Treaty> treaties,UUID a,UUID b,long now) {
        if(a.equals(b))return false;
        if(treaties.stream().anyMatch(t->t.active(now)&&t.pair(a,b)&&t.type().protective()))return true;
        UUID first=root(treaties,a,now),second=root(treaties,b,now);
        return first.equals(second); // A suzerain, its vassals and sub-vassals form one protected bloc.
    }
    public static UUID overlord(Collection<Treaty> treaties,UUID town,long now) {
        return treaties.stream().filter(t->t.active(now)&&t.type()==TreatyType.VASSALAGE&&t.second().equals(town)).map(Treaty::first).findFirst().orElse(null);
    }
    private static UUID root(Collection<Treaty> treaties,UUID town,long now) {
        var seen=new HashSet<UUID>();UUID at=town;
        while(seen.add(at)){UUID next=overlord(treaties,at,now);if(next==null)return at;at=next;}
        throw new IllegalStateException("Цикл вассалитета");
    }
    public static Set<UUID> defenders(Collection<Treaty> treaties,UUID town,long now) {
        var result=new TreeSet<UUID>();
        for(var t:treaties)if(t.active(now)) {
            if(t.type()==TreatyType.ALLIANCE&&t.party(town))result.add(t.other(town));
            if(t.type()==TreatyType.GUARANTEE&&t.second().equals(town))result.add(t.first());
        }
        var seen=new HashSet<UUID>();UUID at=town;
        while(seen.add(at)){UUID parent=overlord(treaties,at,now);if(parent==null)break;result.add(parent);at=parent;}
        UUID bloc=root(treaties,town,now);
        for(var t:treaties)if(t.active(now)&&t.type()==TreatyType.VASSALAGE&&root(treaties,t.first(),now).equals(bloc)){result.add(t.first());result.add(t.second());}
        result.remove(town);return Set.copyOf(result);
    }
    public static double tariffMultiplier(Collection<Treaty> treaties,UUID a,UUID b,UUID tariffTown,long now) {
        if(!tariffTown.equals(a)&&!tariffTown.equals(b)||tradeBlocked(treaties,a,b,now))return 1;
        int discount=treaties.stream().filter(t->t.active(now)&&t.type()==TreatyType.TRADE&&t.pair(a,b)).mapToInt(Treaty::discountBasisPoints).max().orElse(0);
        return 1-discount/10000.0;
    }
    public static void validateNew(Collection<Treaty> treaties,Treaty candidate,long now) {
        var rest=treaties.stream().filter(t->!t.id().equals(candidate.id())).toList();
        for(var t:rest)if(t.open(now)&&t.type()==candidate.type()&&(candidate.type().directional()
                ? t.first().equals(candidate.first())&&t.second().equals(candidate.second()) : t.pair(candidate.first(),candidate.second())))
            throw new IllegalArgumentException("Такой договор или предложение уже существует");
        if(candidate.type().bilateral()&&negotiationsBlocked(rest,candidate.first(),candidate.second(),now))throw new IllegalArgumentException("Дипломатические санкции запрещают новые соглашения");
        if(candidate.type()==TreatyType.TRADE&&tradeBlocked(rest,candidate.first(),candidate.second(),now))throw new IllegalArgumentException("Сначала нужно снять торговые ограничения");
        if(candidate.type()==TreatyType.VASSALAGE) {
            if(overlord(rest,candidate.second(),now)!=null)throw new IllegalArgumentException("У этого города уже есть сюзерен");
            UUID at=candidate.first();var seen=new HashSet<UUID>();
            while(at!=null&&seen.add(at)){if(at.equals(candidate.second()))throw new IllegalArgumentException("Вассалитет не может образовать цикл");at=overlord(rest,at,now);}
            if(rest.stream().anyMatch(t->t.active(now)&&t.type()==TreatyType.GUARANTEE&&t.second().equals(candidate.second())))throw new IllegalArgumentException("Независимость города гарантирована действующим договором");
        }
        if(candidate.type()==TreatyType.GUARANTEE&&overlord(rest,candidate.second(),now)!=null)throw new IllegalArgumentException("Нельзя гарантировать независимость действующего вассала");
    }
    /** Corrupted graphs must not be silently repaired into a different political map. */
    public static void validateSnapshot(Collection<Treaty> treaties,long now) {
        var parents=new HashMap<UUID,UUID>();var keys=new HashSet<String>();
        for(var t:treaties)if(t.open(now)) {
            UUID a=t.first(),b=t.second();if(!t.type().directional()&&a.compareTo(b)>0){UUID swap=a;a=b;b=swap;}
            if(!keys.add(t.type()+"/"+a+"/"+b))throw new IllegalArgumentException("Повтор действующего договора");
        }
        for(var t:treaties)if(t.active(now)&&t.type()==TreatyType.VASSALAGE) {
            if(parents.put(t.second(),t.first())!=null)throw new IllegalArgumentException("Несколько сюзеренов одного города");
        }
        for(var town:parents.keySet())root(treaties,town,now);
        for(var t:treaties)if(t.active(now)&&t.type()==TreatyType.GUARANTEE&&parents.containsKey(t.second()))throw new IllegalArgumentException("Гарантия независимости действующего вассала");
    }
}

package ru.neverland.reputation.model;
import java.util.*;
import static ru.neverland.reputation.model.ReputationLedger.*;

/** Explain retained scores from all durable receipts, including entries outside the short UI history. */
public final class ReputationExplanation {
    public static final long DAY=86_400_000L;
    public record Row(String receipt,long at,long ageDays,boolean future,String rule,String context,int requested,int applied){}
    public record Explanation(int score,long initial,long evidence,long positive,long negative,int limited,long recent7,long days7to30,long older30,long lastAt,int count,boolean consistent,int page,int pages,List<Row> rows){public Explanation{rows=List.copyOf(rows);}}
    private record Key(Subject subject,ReputationAspect aspect){}
    private static final class Series {
        final List<Receipt> rows;final long[] prefix;final long positive,negative;final int limited;
        Series(List<Receipt> input){rows=input.stream().sorted(Comparator.comparingLong((Receipt r)->r.outcome().at()).thenComparing(r->r.outcome().id())).toList();prefix=new long[rows.size()+1];long up=0,down=0;int capped=0;for(int i=0;i<rows.size();i++){var r=rows.get(i);prefix[i+1]=Math.addExact(prefix[i],r.applied());if(r.applied()>0)up=Math.addExact(up,r.applied());if(r.applied()<0)down=Math.addExact(down,r.applied());if(r.applied()!=r.requested())capped++;}positive=up;negative=down;limited=capped;}
        int before(long at){int lo=0,hi=rows.size();while(lo<hi){int mid=(lo+hi)>>>1;if(rows.get(mid).outcome().at()<at)lo=mid+1;else hi=mid;}return lo;}
    }
    private final State state;private final Map<Key,Series> index;
    public ReputationExplanation(State state){this.state=Objects.requireNonNull(state);var grouped=new HashMap<Key,List<Receipt>>();state.receipts().forEach((id,r)->{if(!id.equals(r.outcome().id())||r.aspect()==null||Math.abs((long)r.applied())>2000)throw new IllegalArgumentException("Квитанция оценки повреждена");var subject=new Subject(ReputationScope.valueOf(r.outcome().scope()),r.outcome().subject());grouped.computeIfAbsent(new Key(subject,r.aspect()),key->new ArrayList<>()).add(r);});var built=new HashMap<Key,Series>();grouped.forEach((key,rows)->built.put(key,new Series(rows)));index=Map.copyOf(built);}
    public Explanation explain(Subject subject,ReputationAspect aspect,long now,int requestedPage,int size){
        Objects.requireNonNull(subject);Objects.requireNonNull(aspect);if(now<0||requestedPage<0||size<1||size>28)throw new IllegalArgumentException("Неверная страница объяснения репутации");var series=index.get(new Key(subject,aspect));int count=series==null?0:series.rows.size();int pages=Math.max(1,(int)(((long)count+size-1)/size)),page=Math.min(requestedPage,pages-1);long evidence=series==null?0:series.prefix[count];int score=state.profile(subject).score(aspect);long initial=score-evidence;boolean consistent=initial>=MIN&&initial<=MAX&&(aspect==ReputationAspect.DIPLOMATIC||initial==0);
        var rows=new ArrayList<Row>();if(series!=null)for(int i=count-1-page*size;i>=(int)Math.max(0L,(long)count-((long)page+1)*size);i--){var r=series.rows.get(i);var o=r.outcome();rows.add(new Row(o.id(),o.at(),now>=o.at()?(now-o.at())/DAY:0,o.at()>now,o.rule(),o.context(),r.requested(),r.applied()));}
        int first7=series==null?0:series.before(Math.max(0,now-7*DAY)),first30=series==null?0:series.before(Math.max(0,now-30*DAY));long older=series==null?0:series.prefix[first30],middle=series==null?0:series.prefix[first7]-older,recent=evidence-older-middle;
        return new Explanation(score,initial,evidence,series==null?0:series.positive,series==null?0:series.negative,series==null?0:series.limited,recent,middle,older,count==0?0:series.rows.get(count-1).outcome().at(),count,consistent,page,pages,rows);
    }
}

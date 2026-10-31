package cc.crafto.ui;
import java.util.*;
/** Small expiring queue. A stale screen or old server state can never replay movement later. */
public final class PlacementInput {
    public record Move(String key,long sequence,String action){}
    private static final Set<String> ACTIONS=Set.of("forward","back","left","right","up","down");
    private final ArrayDeque<String> queue=new ArrayDeque<>();
    private String key="";private int slot=-1;private long sequence,expires,next;
    public void update(String key,int slot,long acknowledged,long now){
        if(key==null||!key.matches("[a-fA-F0-9-]{36}")||slot<0||slot>8){clear();return;}
        if(!this.key.equals(key)){queue.clear();sequence=0;next=0;}
        sequence=Math.max(sequence,Math.max(0,acknowledged));if(this.slot!=slot)queue.clear();this.key=key;this.slot=slot;expires=now+3_000_000_000L;
    }
    public void clear(){key="";slot=-1;queue.clear();sequence=0;expires=0;next=0;}
    public void discard(){queue.clear();}
    public boolean active(int selected,long now){return !key.isEmpty()&&selected==slot&&now<expires;}
    public void enqueue(String action){if(!key.isEmpty()&&ACTIONS.contains(action)&&queue.size()<4)queue.addLast(action);}
    public void wheel(double vertical){if(Double.isFinite(vertical)&&vertical!=0)enqueue(vertical>0?"up":"down");}
    public Move next(long now){
        if(now>=expires){clear();return null;}
        if(queue.isEmpty()||now<next)return null;
        next=now+250_000_000L;return new Move(key,++sequence,queue.removeFirst());
    }
}

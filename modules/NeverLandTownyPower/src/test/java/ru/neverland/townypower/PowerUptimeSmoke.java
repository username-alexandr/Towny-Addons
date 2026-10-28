package ru.neverland.townypower;
import java.util.*;
import ru.neverland.townypower.model.*;
public final class PowerUptimeSmoke {
    static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static long powered(Map<String,Object> value){return((Number)value.get("poweredNanos")).longValue();}
    public static void main(String[] args){
        long start=123000000000L;UUID town=UUID.randomUUID();var meter=new PowerUptime(start);var window=PowerWindow.start(start,0,5).acknowledged(start+1_000_000_000L);
        var on=new PowerEngine.Grid(10,10,10,Map.of("forge",new PowerEngine.Allocation("forge",1,0,true,0,10,10,PowerEngine.Status.SUPPLIED)));
        check(powered(meter.read(town,"forge",true,window,on,start+2_000_000_000L))==1_000_000_000L,"unacknowledged first second is not powered time");
        check(powered(meter.read(town,"forge",true,window,on,start+30_000_000_000L))==4_000_000_000L,"live metric clips to prepaid expiry even with stalled scheduler");
        meter.settle(town,window,on,start+30_000_000_000L);check(powered(meter.read(town,"forge",true,null,null,start+31_000_000_000L))==4_000_000_000L,"settling completed period preserves the counter");
        meter.settle(town,window,on,start+40_000_000_000L);check(powered(meter.read(town,"forge",true,window,on,start+40_000_000_000L))==4_000_000_000L,"repeated settlement and stale current window cannot count twice");
        var second=PowerWindow.start(start+40_000_000_000L,0,5);meter.settle(town,second,on,start+42_000_000_000L);check(powered(meter.read(town,"forge",true,second,on,start+42_000_000_000L))==6_000_000_000L,"an unfinished second period is read live and cannot be prematurely settled");
        meter.settle(town,second,on,start+45_000_000_000L);check(powered(meter.read(town,"forge",true,null,null,start+50_000_000_000L))==9_000_000_000L,"downtime between periods never counts as powered");
        check(powered(meter.read(UUID.randomUUID(),"forge",true,null,null,start+50_000_000_000L))==0,"city counters stay isolated");
        check(!meter.read(town,"forge",true,null,null,start).get("epoch").equals(new PowerUptime(start).read(town,"forge",true,null,null,start).get("epoch")),"restart cannot reuse previous cumulative epoch");
        meter.retain(Set.of());check(powered(meter.read(town,"forge",true,null,null,start+50_000_000_000L))==0,"removed city does not retain an unbounded meter");
        System.out.println("PowerUptimeSmoke PASS "+checks+" checks");
    }
}

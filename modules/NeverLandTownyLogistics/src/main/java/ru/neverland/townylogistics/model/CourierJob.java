package ru.neverland.townylogistics.model;
import java.util.*;
public record CourierJob(UUID id,UUID town,String route,String hub,String source,String target,int level,
        List<Position> outbound,List<Position> delivery,List<Position> home,Phase phase,int waypoint,Position position,int handlingTicks) {
    public enum Phase {TO_SOURCE("Идёт за грузом"),LOADING("Погрузка"),TO_TARGET("Доставка"),UNLOADING("Разгрузка"),RETURNING("Возвращается"),DONE("Завершён");public final String title;Phase(String s){title=s;}}
    public CourierJob {Objects.requireNonNull(id);Objects.requireNonNull(town);Network.id(route);Network.id(hub);Network.id(source);Network.id(target);
        outbound=List.copyOf(outbound);delivery=List.copyOf(delivery);home=List.copyOf(home);Objects.requireNonNull(phase);Objects.requireNonNull(position);
        if(level<1||level>5||waypoint<0||handlingTicks<0||outbound.isEmpty()||delivery.isEmpty()||home.isEmpty())throw new IllegalArgumentException("Некорректный курьер");
        for(var path:List.of(outbound,delivery,home))for(var point:path)if(!point.world().equals(position.world()))throw new IllegalArgumentException("Курьер не может менять мир");
    }
    public List<Position> path(){return switch(phase){case TO_SOURCE,LOADING->outbound;case TO_TARGET,UNLOADING->delivery;case RETURNING,DONE->home;};}
    public CourierJob move(Position p,int index){return new CourierJob(id,town,route,hub,source,target,level,outbound,delivery,home,phase,index,p,handlingTicks);}
    public CourierJob phase(Phase value,int wait){return new CourierJob(id,town,route,hub,source,target,level,outbound,delivery,home,value,0,position,wait);}
    public CourierJob returnFromSource(){var back=new ArrayList<>(outbound);Collections.reverse(back);return new CourierJob(id,town,route,hub,source,target,level,outbound,delivery,back,Phase.RETURNING,0,position,0);}
    public CourierJob waitTicks(int ticks){return new CourierJob(id,town,route,hub,source,target,level,outbound,delivery,home,phase,waypoint,position,Math.max(0,handlingTicks-ticks));}
}

package ru.neverland.mintcontracts.service;
import java.util.*;
/** Volatile dwell candidates: teleport, disconnect and restart erase unconfirmed visits. */
public final class ScoutTracker {
    public record Visit(UUID world,int x,int z,long since) {}
    private final Map<UUID,Visit> visits=new HashMap<>();
    public void move(UUID player,UUID world,int x,int z,long now){Visit old=visits.get(player);if(old==null||!old.world().equals(world)||old.x()!=x||old.z()!=z)visits.put(player,new Visit(world,x,z,now));}
    public void reset(UUID player){visits.remove(player);}
    public Set<UUID> players(){return Set.copyOf(visits.keySet());}
    public static boolean elapsed(Visit visit,long created,long now,long dwell){long start=Math.max(visit.since(),created);return now>=start&&now-start>=dwell;}
    public Visit ready(UUID player,UUID world,int x,int z,long now,long dwell){Visit v=visits.get(player);return v!=null&&v.world().equals(world)&&v.x()==x&&v.z()==z&&now>=v.since()&&now-v.since()>=dwell?v:null;}
}

package ru.neverland.townylogistics.model;
import java.util.*;
import java.util.function.Predicate;
public final class NavigationPolicy {
    private NavigationPolicy(){}
    public static boolean allowed(List<Position> points,Position target,Predicate<Position> usable){
        if(points.isEmpty()||points.size()>128||points.get(points.size()-1).distance(target)>1.8)return false;
        for(Position p:points)if(!p.world().equals(target.world())||!usable.test(p))return false;return true;
    }
}

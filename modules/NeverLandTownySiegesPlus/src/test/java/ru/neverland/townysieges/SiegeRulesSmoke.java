package ru.neverland.townysieges;

import java.util.*;
import static ru.neverland.townysieges.SiegeRules.*;

public final class SiegeRulesSmoke {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        UUID world=UUID.randomUUID(),other=UUID.randomUUID();var z=new Zone(world,10,50,10,12,65,20);
        check(z.enters(new Point(world,9,55,15),new Point(world,11,55,15)),"gate entrance");
        check(z.enters(new Point(world,0,55,15),new Point(world,25,55,15)),"fast outside-to-outside crossing cannot skip gate");
        check(!z.enters(new Point(world,11,55,15),new Point(world,20,55,15)),"defender cannot trap attacker already inside");
        check(!z.enters(new Point(world,0,55,25),new Point(world,25,55,25)),"parallel path clear");
        check(!z.enters(new Point(world,0,80,15),new Point(world,25,80,15)),"above gate independent from tower");
        check(!z.contains(new Point(other,11,55,15)),"other world unaffected");
        check(z.enters(new Point(other,11,55,15),new Point(world,11,55,15)),"cross-world entry");
        check(!z.enters(new Point(world,11,55,15),new Point(other,11,55,15)),"cross-world exit");
        check(!z.enters(new Point(world,8,55,15),new Point(world,8,55,15)),"stationary outside");
        check(z.contains(new Point(world,10,50,10)),"inclusive boundary");
        var diagonal=Zone.along(world,0,50,0,100,50,100,4,24);
        check(diagonal.contains(new Point(world,50,55,50)),"selected diagonal line protected");
        check(!diagonal.contains(new Point(world,10,55,90)),"diagonal moat does not slow the whole enclosing rectangle");
        check(reduction(5,5,.2,.05,.03,.4)==.4,"all defenses share a hard cap");
        check(reduction(1,0,Double.NaN,.05,.03,.4)==.05,"non-finite optional bonus cannot poison damage");
        check(reduction(0,0,0,.05,.03,.4)==0,"no inactive phantom defense");
        check(moat(5,.1,.35)==.35&&moat(0,.1,.35)==0,"moat cap and inactive state");
        boolean invalid=false;try{new Point(world,Double.NaN,0,0);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"reject non-finite location");
        System.out.println("SiegeRulesSmoke PASS");
    }
}

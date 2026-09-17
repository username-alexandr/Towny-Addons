package ru.neverland.mintevents;
import java.util.UUID;
import ru.neverland.mintevents.model.ActiveEvent;
public final class EventPauseSmoke {
    public static void main(String[] args) {
        var original=new ActiveEvent(UUID.randomUUID(),"fire",100,10100,42,100,.3,0);
        var paused=original.reschedule(original.endsAt(),2100);
        check(!original.paused()&&paused.paused(),"immutable transition");
        check(paused.secondsLeft(50000)==8,"paused time cannot expire");
        var resumed=paused.reschedule(paused.endsAt()+(502100-paused.pausedAt()),0);
        check(resumed.secondsLeft(502100)==8,"resume preserves remaining time");
        check(resumed.progress()==42&&resumed.startedAt()==100&&resumed.goal()==100,"progress and incident identity survive");
        var restarted=resumed.reschedule(1000100,0);check(restarted.progress()==42&&restarted.startedAt()==100,"restart cannot erase contributed items");
        try {original.pausedAt(-1);throw new AssertionError("invalid pause");}catch(IllegalArgumentException expected){}
        System.out.println("EventPauseSmoke PASS: freeze, resume, restart, progress and identity");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}

package ru.neverland.townycontrol;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.core.*;
public final class ActivityAdminSmoke {
    interface Attempt { void run()throws Exception; }
    private static void rejects(Attempt attempt)throws Exception {try{attempt.run();}catch(IllegalArgumentException|ArithmeticException expected){return;}throw new AssertionError("unsafe input accepted");}
    public static void main(String[] args)throws Exception {
        long start=1_800_000_000_000L;
        var timer=ActivityTimer.running(start,start+60000);
        var paused=timer.edit("pause",0,start+10000);
        assert paused.remaining(start+999999)==50000;
        var yaml=new YamlConfiguration();paused.write(yaml,"task.");yaml.set("task.end",paused.deadline());
        var reloaded=new YamlConfiguration();reloaded.loadFromString(yaml.saveToString());
        var copy=ActivityTimer.read(reloaded,"task.",ActivityTimer.running(start,reloaded.getLong("task.end")));
        assert copy.equals(paused);
        var restarted=copy.edit("restart",0,start+1000000);assert restarted.paused()&&restarted.remaining(start+1000000)==60000;
        var resumed=copy.edit("extend",2,start+1000000).edit("resume",0,start+1000000);
        assert !resumed.paused()&&resumed.remaining(start+1000000)==170000&&resumed.duration()==60000;
        rejects(()->timer.edit("resume",0,start));rejects(()->paused.edit("pause",0,start));
        rejects(()->paused.edit("resume",0,start));rejects(()->timer.edit("pause",0,start+60000));
        rejects(()->timer.edit("extend",0,start));rejects(()->timer.edit("extend",10081,start));
        rejects(()->new ActivityTimer(Long.MAX_VALUE,0,1).edit("extend",1,start));
        reloaded.set("task.admin-paused-at","broken");rejects(()->ActivityTimer.read(reloaded,"task.",timer));
        for(String module:List.of("Contracts","Expeditions","Espionage","Governance","Trade","Elections")) {
            String prefix=switch(module){case "Contracts"->"towns.city.active.task.";case "Expeditions"->"active.task.";case "Espionage"->"operations.task.";case "Governance"->"proposals.task.";case "Trade"->"caravans.task.";default->"towns.city.";};
            String deadline=switch(module){case "Contracts"->"expires-at";case "Expeditions"->"expires";case "Espionage"->"completes-at";case "Governance"->"ends-at";case "Trade"->"arrives-at";default->"voting-end";};
            var y=new YamlConfiguration();y.set(prefix+deadline,paused.deadline());paused.write(y,prefix);y.set(prefix+"progress",37);y.set(prefix+"invoice","stable-id");
            var rules=ModuleTimers.rules(module).values().stream().flatMap(Collection::stream).toList();var shifted=new YamlConfiguration();shifted.loadFromString(ModuleTimers.shifted(y.saveToString(),rules,500000));
            assert shifted.getLong(prefix+deadline)-shifted.getLong(prefix+"admin-paused-at")==50000:module;
            assert shifted.getLong(prefix+"admin-duration")==60000&&shifted.getInt(prefix+"progress")==37&&shifted.getString(prefix+"invoice").equals("stable-id"):module;
        }
        var a=new ActivityAdmin.Target("abc-001","one",Set.of("status"),(x,m)->"one");
        var b=new ActivityAdmin.Target("abc-002","two",Set.of("status"),(x,m)->"two");
        assert ActivityAdmin.find(List.of(a,b),"ABC-001")==a;rejects(()->ActivityAdmin.find(List.of(a,b),"abc"));rejects(()->ActivityAdmin.find(List.of(a,b),""));rejects(()->ActivityAdmin.find(List.of(a,b),"missing"));
        assert NewcomerProtection.registrationRemaining(start,start)==86400000;
        assert NewcomerProtection.registrationRemaining(start/1000,start+1000)==86399000;
        assert NewcomerProtection.registrationRemaining(start,start+86400000)==0&&NewcomerProtection.registrationRemaining(0,start)==0;
        System.out.println("ActivityAdminSmoke PASS: persistent pause, restart, extension, module pause composition, strict IDs, overflow, newcomer registration");
    }
}

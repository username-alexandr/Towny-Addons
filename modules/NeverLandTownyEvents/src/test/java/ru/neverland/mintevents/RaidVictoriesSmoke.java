package ru.neverland.mintevents;

import java.nio.file.*;
import java.util.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.neverland.mintevents.model.*;
import ru.neverland.mintevents.service.EventRepository;

public final class RaidVictoriesSmoke {
    public static class Fixture extends JavaPlugin { @Override public java.util.logging.Logger getLogger() { return java.util.logging.Logger.getLogger("raid-victories"); } }
    static int checks;
    static void check(boolean result,String message) { checks++; if(!result)throw new AssertionError(message); }
    static ActiveEvent raid(UUID town,long started,boolean complete) {
        var event = new ActiveEvent(town,"raid",started,started+10000,0,100,0,0); var raid = new RaidState();
        if(complete)for(int i=0;i<10;i++){raid.begin(List.of("zombie"));UUID mob=UUID.randomUUID();raid.spawned(mob);raid.died(mob);}
        event.raid(raid); return event;
    }
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("raid-victories-");
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var plugin=(Fixture)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(Fixture.class);
        var folder=JavaPlugin.class.getDeclaredField("dataFolder");folder.setAccessible(true);folder.set(plugin,directory.toFile());
        UUID town=UUID.randomUUID();var repo=new EventRepository(plugin);repo.load();repo.initializeRaidVictories(Set.of("raid"));
        for(int i=0;i<12;i++){var event=raid(town,100+i,true);repo.put(event);repo.complete(event,true,1,1000+i);repo.save();}
        check(repo.history(town).size()==1&&repo.raidVictories(town)==12,"bounded history never truncates lifetime wins");
        var reload=new EventRepository(plugin);reload.load();reload.initializeRaidVictories(Set.of("raid"));
        check(reload.raidVictories(town)==12,"wins survive restart without double migration");
        var forced=raid(town,200,false);reload.put(forced);reload.complete(forced,true,1,2000);reload.save();
        check(reload.raidVictories(town)==12,"admin forced success is not a completed raid");
        var loss=raid(town,300,false);reload.put(loss);reload.complete(loss,false,1,3000);reload.save();
        check(reload.raidVictories(town)==12,"defeat grants no win");
        var cancel=raid(town,400,true);reload.put(cancel);reload.replace(cancel,null,4000);
        check(reload.raidVictories(town)==12,"neutral cancellation grants no win");
        check(reload.raidVictories(UUID.randomUUID())==0,"city UUID isolation");
        var yaml=new YamlConfiguration();Path file=directory.resolve("events-data.yml");yaml.load(file.toFile());
        var legacy=Map.of("event","raid","started-at",1L,"ended-at",2L,"progress",100,"goal",100,"success",true);
        yaml.set("towns."+town+".raid-victories",null);yaml.set("towns."+town+".history",List.of(legacy,legacy));yaml.save(file.toFile());
        var migrated=new EventRepository(plugin);migrated.load();migrated.initializeRaidVictories(Set.of("raid"));
        check(migrated.raidVictories(town)==1,"legacy retained history deduplicated once");
        migrated.initializeRaidVictories(Set.of("raid"));check(migrated.raidVictories(town)==1,"migration idempotent");
        yaml.load(file.toFile());yaml.set("towns."+town+".raid-victories",-1);yaml.save(file.toFile());
        try {new EventRepository(plugin).load();throw new AssertionError("negative ledger accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("RaidVictoriesSmoke PASS: "+checks+" assertions");
    }
}

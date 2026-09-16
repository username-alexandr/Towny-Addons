package ru.neverland.townyseasons;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SeasonStorageSmoke {
    interface Action { void run() throws Exception; }
    static void check(boolean ok,String label) { if(!ok)throw new AssertionError(label); }
    static void fails(Action action) throws Exception { try{action.run();}catch(Exception expected){return;}throw new AssertionError("expected rejection"); }
    static YamlConfiguration settings() throws Exception {var y=new YamlConfiguration();try(var in=SeasonStorageSmoke.class.getResourceAsStream("/config.yml")){y.load(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));}return y;}
    public static void main(String[] args) throws Exception {
        var y=settings();var config=SeasonSettings.load(y);check(config.days()==7&&config.effects().get(Season.WINTER).production().get("agrarian_complex")==.6,"configured winter penalty");
        for(Object value:List.of(0,-1,366,1.5,"7")){var bad=settings();bad.set("calendar.days-per-season",value);fails(()->SeasonSettings.load(bad));}
        for(Object value:List.of(Double.NaN,Double.POSITIVE_INFINITY,0,2.1,"0.6")){var bad=settings();bad.set("seasons.winter.production.agrarian_complex",value);fails(()->SeasonSettings.load(bad));}
        var bad=settings();bad.set("seasons.spring.event-weights.typo",2);fails(()->SeasonSettings.load(bad));
        Path file=Files.createTempDirectory("seasons-storage").resolve("calendar.yml");UUID world=UUID.randomUUID();var repo=new SeasonRepository(file);repo.load(1000);repo.set(world,Season.WINTER);
        var restart=new SeasonRepository(file);restart.load(999999);check(restart.epoch()==1000&&restart.overrides().get(world)==Season.WINTER,"restart retains original epoch and override");
        fails(()->restart.overrides().clear());restart.set(world,null);var resumed=new SeasonRepository(file);resumed.load(2000);check(resumed.overrides().isEmpty()&&resumed.epoch()==1000,"resume never resets clock");
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("keep"),"keep");fails(()->resumed.set(world,Season.SUMMER));check(!resumed.healthy(),"write failure freezes calculations");fails(()->resumed.set(world,Season.AUTUMN));check(Files.readString(file.resolve("keep")).equals("keep"),"failed write preserves target");
        Path malformed=file.getParent().resolve("bad.yml");for(String text:List.of("schema: 9\nepoch: 1", "schema: 1\nepoch: 0", "schema: 1\nepoch: 1\noverrides: [bad", "schema: 1\nepoch: 1\noverrides:\n  invalid-uuid: WINTER")){Files.writeString(malformed,text);var broken=new SeasonRepository(malformed);fails(()->broken.load(2000));fails(()->broken.set(world,Season.SPRING));check(Files.readString(malformed).equals(text),"damaged calendar untouched");}
        System.out.println("SeasonStorageSmoke PASS: strict settings, restart, resume, atomic failure, immutable snapshots, corrupted data");
    }
}

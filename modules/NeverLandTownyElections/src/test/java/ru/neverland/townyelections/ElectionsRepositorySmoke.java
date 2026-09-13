package ru.neverland.townyelections;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ElectionsRepositorySmoke {
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("elections-test-"),file=dir.resolve("elections.yml");
        var repo=new ElectionsRepository(file);repo.load();assert repo.healthy();
        var e=ElectionSmoke.campaign();e.nominate(ElectionSmoke.id(1),"mayor",101);e.phase=Election.Phase.VOTING;e.votingEnd=300;
        e.vote(ElectionSmoke.id(1),"mayor",List.of(ElectionSmoke.id(1)),220);repo.put(e);
        e.ballots.clear();assert !repo.get(e.town).ballots.isEmpty();
        var restarted=new ElectionsRepository(file);restarted.load();assert restarted.get(e.town).ballots.get("mayor").size()==1;
        var y=new YamlConfiguration();y.load(file.toFile());y.set("towns."+e.town+".ballots.mayor."+ElectionSmoke.id(1),List.of(ElectionSmoke.id(1).toString(),ElectionSmoke.id(1).toString()));
        Files.writeString(dir.resolve("bad.yml"),y.saveToString());var bad=new ElectionsRepository(dir.resolve("bad.yml"));
        try{bad.load();throw new AssertionError("corruption accepted");}catch(IllegalArgumentException expected){}assert !bad.healthy();
        Path saved=dir.resolve("saved.yml");Files.move(file,saved);Files.createDirectory(file);Files.writeString(file.resolve("block"),"fault");
        var changed=restarted.get(e.town);changed.detail="should not appear";
        try{restarted.put(changed);throw new AssertionError("fault ignored");}catch(java.io.IOException expected){}
        assert !restarted.healthy();assert !restarted.get(e.town).detail.equals(changed.detail);
        try{restarted.put(changed);throw new AssertionError("write retried after fault");}catch(java.io.IOException expected){}
        var config=YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));var settings=ElectionsSettings.load(config);assert settings.interval()==14*86_400_000L;
        config.set("schedule.interval-days",1);ElectionSmoke.reject(()->ElectionsSettings.load(config));
        config.set("schedule.interval-days",14);config.set("quorum",Double.NaN);ElectionSmoke.reject(()->ElectionsSettings.load(config));
        System.out.println("ElectionsRepositorySmoke PASS: restart, corruption, atomic failure, no retry, strict settings");
    }
}

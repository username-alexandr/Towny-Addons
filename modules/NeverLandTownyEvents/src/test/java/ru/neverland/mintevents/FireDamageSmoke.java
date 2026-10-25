package ru.neverland.mintevents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import ru.neverland.mintevents.model.FireDamage;
import ru.neverland.mintevents.service.FireDamageRepository;
import ru.neverland.mintevents.service.FireMaterials;

public final class FireDamageSmoke {
    private static void check(boolean b, String why) { if (!b) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        for (String name : Set.of("OAK_PLANKS", "SPRUCE_STAIRS", "RED_WOOL", "BLUE_CARPET", "STRIPPED_OAK_LOG", "BOOKSHELF", "BAMBOO_MOSAIC")) check(FireMaterials.combustible(name), name);
        for (String name : Set.of("STONE", "CHEST", "BARREL", "CHISELED_BOOKSHELF", "OAK_DOOR", "RED_BED", "NOTE_BLOCK", "CRIMSON_PLANKS", "OAK_LEAVES", "BOGUS_WOOL", "BAMBOO_LOG", "STRIPPED_OAK_STAIRS")) check(!FireMaterials.combustible(name), name + " excluded");
        check(!FireMaterials.structuralMarker("OAK_LOG") && FireMaterials.structuralMarker("OAK_PLANKS"), "natural trunk guard");
        Path root = Files.createTempDirectory("fire-damage-smoke");
        Path file = root.resolve("fire-damage.yml");
        UUID town = UUID.randomUUID(), world = UUID.randomUUID();
        FireDamage damage = new FireDamage(town, world, 17, 70, -8, "minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]", 123);
        FireDamageRepository repo = new FireDamageRepository(file); repo.load();
        check(repo.prepare(damage) && Files.readString(file).contains("facing=west"), "journal durable before caller changes world");
        check(!repo.prepare(damage), "same operation cannot destroy twice");
        try { repo.prepare(new FireDamage(town,world,17,70,-8,"minecraft:oak_planks",124)); throw new AssertionError("different operation reused location"); } catch (IllegalArgumentException expected) { }
        FireDamageRepository restart = new FireDamageRepository(file); restart.load();
        check(restart.pending(world,17,70,-8) && restart.town(town).equals(java.util.List.of(damage)), "restart retains exact block state");
        Path backup = root.resolve("saved.yml"); Files.move(file,backup); Files.createDirectory(file); Files.writeString(file.resolve("blocker"),"keep");
        int worldChanges=0;
        try { restart.prepare(new FireDamage(town,world,18,70,-8,"minecraft:oak_planks",123)); worldChanges++; throw new AssertionError("write fault swallowed"); } catch (java.io.IOException expected) { }
        check(worldChanges==0 && !restart.writable() && restart.town(town).size()==1, "write failure prevents world effects and blocks retry");
        Files.delete(file.resolve("blocker")); Files.delete(file); Files.move(backup,file); restart.load();
        restart.confirm(Set.of(damage.id()));
        FireDamageRepository repaired = new FireDamageRepository(file); repaired.load(); check(repaired.town(town).isEmpty(), "confirmed repairs survive restart");
        // Confirm one plot by trust: no server/world implementation is present in this JVM.
        var a=new FireDamage(town,world,-1,70,-1,"minecraft:oak_planks",200);
        var b=new FireDamage(town,world,0,70,-1,"minecraft:oak_planks",200);
        var foreign=new FireDamage(UUID.randomUUID(),world,-2,70,-1,"minecraft:oak_planks",200);
        repaired.prepare(a);repaired.prepare(b);repaired.prepare(foreign);
        var selection=new ru.neverland.mintevents.model.FireRepairPlot(town,world,-1,-1,32,java.util.List.of(a));
        check(repaired.confirm(selection)==1,"trust confirmation accepts selected plot without any world access");
        check(repaired.get(b.id())!=null&&repaired.get(foreign.id())!=null,"neighbour and foreign town preserved");
        try {repaired.confirm(selection);throw new AssertionError("duplicate confirmation accepted");}catch(IllegalArgumentException expected){}
        var newer=new FireDamage(town,world,-1,70,-1,"minecraft:oak_planks",201);repaired.prepare(newer);
        try {repaired.confirm(selection);throw new AssertionError("stale menu cleared new incident");}catch(IllegalArgumentException expected){}
        var fresh=new ru.neverland.mintevents.model.FireRepairPlot(town,world,-1,-1,32,java.util.List.of(newer));
        var extra=new FireDamage(town,world,-3,71,-1,"minecraft:oak_planks",201);repaired.prepare(extra);
        try {repaired.confirm(fresh);throw new AssertionError("stale subset accepted");}catch(IllegalArgumentException expected){}
        var both=new ru.neverland.mintevents.model.FireRepairPlot(town,world,-1,-1,32,java.util.List.of(newer,extra));
        Files.move(file,backup);Files.createDirectory(file);Files.writeString(file.resolve("blocker"),"keep");
        try {repaired.confirm(both);throw new AssertionError("confirmation write failure swallowed");}catch(java.io.IOException expected){}
        check(repaired.get(newer.id())!=null&&repaired.get(extra.id())!=null,"failed confirmation retains all damage");
        Files.delete(file.resolve("blocker"));Files.delete(file);Files.move(backup,file);repaired.load();repaired.confirm(both);
        repaired.load();check(repaired.get(newer.id())==null&&repaired.get(b.id())!=null,"plot confirmation survives reload without clearing neighbour");
        repaired.confirm(Set.of(b.id(),foreign.id()));
        repaired.prepare(damage); String valid=Files.readString(file);
        Files.writeString(file,valid.replace("minecraft:oak_stairs", "minecraft:bogus_stairs")); String corrupt=Files.readString(file);
        try { repaired.load(); throw new AssertionError("corrupt material accepted"); } catch (java.io.IOException expected) { }
        check(!repaired.writable() && Files.readString(file).equals(corrupt), "corruption preserved and fail closed");
        System.out.println("FireDamageSmoke PASS");
    }
}

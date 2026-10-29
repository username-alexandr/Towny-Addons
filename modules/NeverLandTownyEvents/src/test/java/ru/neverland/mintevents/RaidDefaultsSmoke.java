package ru.neverland.mintevents;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import ru.neverland.mintevents.service.RaidCatalog;

/** Same inherited config setup as RaidCatalog(plugin), with explicit old/custom compositions preserved. */
public final class RaidDefaultsSmoke {
    private static int checks;private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static YamlConfiguration resource(){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(RaidDefaultsSmoke.class.getClassLoader().getResourceAsStream("raids.yml")),StandardCharsets.UTF_8));}
    private static YamlConfiguration removed(String key){var old=resource();old.set(key,null);old.setDefaults(resource());return old;}
    private static void denied(Runnable action,String message){try{action.run();}catch(RuntimeException expected){check(true,message);return;}throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var base=new RaidCatalog(resource());var old=removed("waves.10");var migrated=new RaidCatalog(old);check(migrated.wave(10).equals(base.wave(10)),"missing whole final wave inherits exact composition and multipliers");check(old.get("waves.10",null)==null,"resolved catalog leaves administrator source untouched");
        var noCaptain=new RaidCatalog(removed("mobs.captain"));check(noCaptain.mob("captain").equals(base.mob("captain"))&&noCaptain.wave(10).equals(base.wave(10)),"new missing mob definition inherits exact stats and gear without breaking final wave");
        var noType=new RaidCatalog(removed("mobs.ravager.type"));check(noType.mob("ravager").type()==EntityType.RAVAGER,"missing ravager type inherits RAVAGER instead of generic ZOMBIE fallback");
        var partial=removed("mobs.captain.health");partial.set("mobs.captain.name","Мой главарь");var custom=new RaidCatalog(partial);check(custom.mob("captain").name().equals("Мой главарь")&&custom.mob("captain").health()==60,"partial mob preserves custom title and bundled missing health");
        var subset=resource();subset.set("waves.10.mobs",null);subset.createSection("waves.10.mobs").set("zombie",1);subset.setDefaults(resource());var chosen=new RaidCatalog(subset);check(chosen.wave(10).mobs().equals(List.of("zombie")),"existing explicit custom wave does not regain omitted default mobs");
        var gear=resource();gear.set("mobs.captain.equipment",null);gear.createSection("mobs.captain.equipment").set("hand","WOODEN_AXE");gear.setDefaults(resource());check(new RaidCatalog(gear).mob("captain").equipment().equals(Map.of("hand","WOODEN_AXE")),"explicit custom equipment does not regain default armour");
        var unarmed=resource();unarmed.set("mobs.captain.equipment",null);unarmed.createSection("mobs.captain.equipment");unarmed.setDefaults(resource());check(new RaidCatalog(unarmed).mob("captain").equipment().isEmpty(),"explicit empty gear stays empty");
        var empty=resource();empty.set("waves.10.mobs",null);empty.createSection("waves.10.mobs");empty.setDefaults(resource());denied(()->new RaidCatalog(empty),"explicit empty wave remains invalid rather than refilled by defaults");
        var zero=resource();zero.set("waves.10.mobs.captain",0);zero.setDefaults(resource());check(!new RaidCatalog(zero).wave(10).mobs().contains("captain"),"explicit zero mob count remains zero");
        var invalid=resource();invalid.set("mobs.ravager.type","UNKNOWN_MOB");invalid.setDefaults(resource());denied(()->new RaidCatalog(invalid),"explicit unknown mob type rejected");
        var blank=resource();blank.set("mobs.ravager.type","");blank.setDefaults(resource());denied(()->new RaidCatalog(blank),"explicit empty mob type rejected");
        System.out.println("RaidDefaultsSmoke PASS "+checks+" inherited upgrade checks");
    }
}

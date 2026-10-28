package ru.neverland.mintcamps;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.model.*;
import ru.neverland.mintcamps.data.CampRepository;
import static ru.neverland.mintcamps.model.GuestAccess.Right.*;

public final class GuestAccessSmoke {
    static int checks;static void check(boolean v,String label){checks++;if(!v)throw new AssertionError(label);}
    public static final class Fixture extends JavaPlugin{@Override public Logger getLogger(){return Logger.getLogger("camp-guests-test");}@Override public org.bukkit.configuration.file.YamlConfiguration getConfig(){return new org.bukkit.configuration.file.YamlConfiguration();}}
    static Fixture plugin(Path dir)throws Exception{var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var p=(Fixture)((sun.misc.Unsafe)f.get(null)).allocateInstance(Fixture.class);f=JavaPlugin.class.getDeclaredField("dataFolder");f.setAccessible(true);f.set(p,dir.toFile());return p;}
    public static void main(String[] args)throws Exception{
        long now=100000;UUID owner=UUID.randomUUID(),guest=UUID.randomUUID(),trusted=UUID.randomUUID();
        var camp=new Camp(owner,"Owner",UUID.randomUUID(),"world",new BlockPos(8,80,8),BlockFace.NORTH,1,"plains",now,now+3600000,true,9);
        camp.trusted().put(trusted,"OldTrusted");var mutable=EnumSet.of(SLEEP,WORKBENCH);camp.guests().put(guest,new GuestAccess("Guest",now,now+60000,mutable));mutable.add(STORAGE);
        for(var right:GuestAccess.Right.values()){check(camp.allows(owner,right,now),"owner access remains full");check(camp.allows(trusted,right,now+60001),"legacy trust remains permanent");}
        check(camp.allows(guest,SLEEP,now)&&camp.allows(guest,WORKBENCH,now),"separate guest capabilities apply");
        check(!camp.allows(guest,STORAGE,now),"guest does not inherit storage and mutable input cannot expand rights");
        check(!camp.canAccess(guest),"guest permission does not grant teleport or management access");
        check(!camp.allows(guest,SLEEP,now+60000),"expiry is enforced at exact boundary");
        check(!camp.allows(guest,SLEEP,now-1),"clock rollback before grant fails closed");
        Path dir=Files.createTempDirectory("camp-guests-");var repo=new CampRepository(plugin(dir));repo.load();repo.put(camp);repo.saveOrThrow();
        var restored=new CampRepository(plugin(dir));restored.load();var saved=restored.get(owner).orElseThrow();
        check(saved.allows(guest,SLEEP,now+1)&&!saved.allows(guest,STORAGE,now+1),"exact guest rights survive reload");
        check(!saved.allows(guest,SLEEP,now+60000)&&saved.trusted().containsKey(trusted),"restart neither extends deadline nor removes old trust");
        saved.guests().remove(guest);restored.markDirty();restored.saveOrThrow();var revoked=new CampRepository(plugin(dir));revoked.load();check(!revoked.get(owner).orElseThrow().allows(guest,SLEEP,now+1),"revoked access stays revoked after restart");
        Files.writeString(dir.resolve("camps.yml"),"camps:\n  "+owner+":\n    world-id: broken\n");byte[] corrupt=Files.readAllBytes(dir.resolve("camps.yml"));boolean fail=false;try{revoked.load();}catch(Exception e){fail=true;}check(fail,"malformed saved camp fails closed");
        try{revoked.saveOrThrow();}catch(Exception expected){}check(Arrays.equals(corrupt,Files.readAllBytes(dir.resolve("camps.yml"))),"invalid data is not replaced by empty camp data");
        System.out.println("GuestAccessSmoke PASS "+checks+" checks");
    }
}

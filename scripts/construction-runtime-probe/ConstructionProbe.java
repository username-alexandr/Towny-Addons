package ru.neverland.probe;

import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.SuiteState;
import ru.neverland.townybuilds.construction.*;
import ru.neverland.townybuilds.data.*;
import java.nio.file.*;
import java.util.*;

/** Isolated world + real Folia blocks/entities + persisted ItemStack metadata, across two server boots. */
public final class ConstructionProbe extends JavaPlugin {
    int checks;List<String> results=new ArrayList<>();
    void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);results.add("PASS "+label);}
    @Override public void onEnable() {
        if(!Files.isRegularFile(Path.of("ALLOW_DISPOSABLE_CONSTRUCTION_PROBE"))) { getLogger().severe("Disposable test marker required"); Bukkit.getPluginManager().disablePlugin(this); return; }
        Bukkit.getGlobalRegionScheduler().runDelayed(this,t->{
            var world=Bukkit.getWorlds().get(0);
            world.getChunkAtAsync(0,0).whenComplete((chunk,error)->{
                if(error!=null){finish(error);return;}
                Bukkit.getRegionScheduler().execute(this,world,0,0,()->{
                    try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()){try{run(world);finish(null);}catch(Throwable ex){finish(ex);}}
                });
            });
        },40);
    }
    void run(World world)throws Exception {
        var data=new DataStore(this,54);data.load();
        UUID town=UUID.fromString("00000000-0000-0000-0000-000000000042");
        if(Files.exists(getDataFolder().toPath().resolve("phase1"))) {
            var receipt=data.town(town).constructionReceipts().get("probe");
            check(receipt!=null&&receipt.state().equals("REFUND_PAYING"),"uncertain bank outcome survives real server restart");
            check(receipt.paidCents()==12345&&receipt.remaining().get(0).amount()==100,"exact money and pending resource count preserved");
            check(receipt.paidItems().get(0).sample().getItemMeta().hasDisplayName(),"custom item metadata preserved");
            check(data.town(town).storage()[0].getAmount()==32,"warehouse persisted with same transaction");
            return;
        }
        var service=new ConstructionService(this,null,null,null,null,new BuildingBlueprintGenerator(),(p,t,id,level)->{});
        var site=new ConstructionSite("probe",world.getUID(),0,80,0,org.bukkit.block.BlockFace.NORTH,0,1,1,true);
        var at=new BlockOffset(1,0,1);var unrelated=world.getBlockAt(5,80,5);
        unrelated.setType(Material.DIAMOND_BLOCK,false);
        var target=world.getBlockAt(1,80,1);target.setType(Material.OAK_PLANKS,false);
        var sample=new ItemStack(Material.STONE);var meta=sample.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Refund probe"));sample.setItemMeta(meta);
        var receipt=new ConstructionReceipt(UUID.randomUUID(),1,12345,List.of(new ConstructionReceipt.Item(sample,100)),
                Map.of(at,new ConstructionReceipt.Change("minecraft:stone","minecraft:oak_planks")),"ACTIVE",0,List.of(),0,"probe",false,1).cancel(0,"probe");
        check(service.restoreCancelled(site,receipt),"restore recorded construction cell");
        check(target.getType()==Material.STONE,"original terrain restored");
        check(unrelated.getType()==Material.DIAMOND_BLOCK,"unregistered adjacent block untouched");
        check(service.restoreCancelled(site,receipt)&&target.getType()==Material.STONE,"replaying block restoration is harmless");
        target.setType(Material.GOLD_BLOCK,false);
        try{service.restoreCancelled(site,receipt);throw new AssertionError("foreign change overwritten");}catch(IllegalStateException expected){check(target.getType()==Material.GOLD_BLOCK,"foreign replacement blocks cancellation");}
        target.setType(Material.STONE,false);
        var chest=world.getBlockAt(2,80,1);chest.setType(Material.CHEST,false);((Container)chest.getState()).getInventory().addItem(new ItemStack(Material.DIAMOND));
        var chestReceipt=new ConstructionReceipt(UUID.randomUUID(),1,0,List.of(),Map.of(new BlockOffset(2,0,1),new ConstructionReceipt.Change("minecraft:air","minecraft:chest")),"ACTIVE",0,List.of(),0,"probe",false,0).cancel(0,"probe");
        try{service.restoreCancelled(site,chestReceipt);throw new AssertionError("filled chest erased");}catch(IllegalStateException expected){check(((Container)chest.getState()).getInventory().contains(Material.DIAMOND),"container contents preserved");}
        ((Container)chest.getState()).getInventory().clear();
        check(service.restoreCancelled(site,chestReceipt)&&chest.getType().isAir(),"empty construction chest can be removed");
        Map<BlockOffset,ConstructionReceipt.Change> large=new LinkedHashMap<>();
        for(int i=0;i<130;i++){var offset=new BlockOffset(i%13,10,i/13);site.location(world,offset).getBlock().setType(Material.OAK_PLANKS,false);large.put(offset,new ConstructionReceipt.Change("minecraft:air","minecraft:oak_planks"));}
        var batch=new ConstructionReceipt(UUID.randomUUID(),1,0,List.of(),large,"ACTIVE",0,List.of(),0,"probe",false,0).cancel(0,"probe");
        check(!service.restoreCancelled(site,batch),"large removal yields after batch limit");
        check(service.restoreCancelled(site,batch),"remaining removal continues next pass");
        var display=world.spawn(new Location(world,8,82,8),BlockDisplay.class,d->{d.setPersistent(false);d.setVisibleByDefault(false);d.setBlock(Material.STONE.createBlockData());});
        check(display.isValid()&&!display.isPersistent()&&!display.isVisibleByDefault(),"real Folia BlockDisplay is temporary and private");
        check(display.getBlock().getMaterial()==Material.STONE,"display block data round-trip");
        display.remove();check(!display.isValid(),"display cleaned on owning region");
        var state=data.town(town);state.setConstructionSite(site);state.receipt("probe",receipt.state("REFUND_PAYING"));
        var storage=state.storage();storage[0]=new ItemStack(Material.STONE,32);state.setStorage(storage,54);data.saveOrThrow();
        var reload=new DataStore(this,54);reload.load();
        check(reload.town(town).constructionReceipts().get("probe").paidItems().get(0).sample().isSimilar(sample),"receipt resource metadata survives YAML serialization");
        Files.writeString(getDataFolder().toPath().resolve("phase1"),"ready");
    }
    void finish(Throwable error) {
        try {
            getDataFolder().mkdirs();
            if(error!=null){getLogger().log(java.util.logging.Level.SEVERE,"Native construction probe failed",error);results.add("ERROR "+error);}
            results.add("checks="+checks);results.add("failed="+(error==null?0:1));
            Files.writeString(getDataFolder().toPath().resolve("result.txt"),String.join("\n",results)+"\n");
        }catch(Exception ex){getLogger().severe(ex.toString());}
    }
}

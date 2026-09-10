package ru.neverland.townydistricts.listener;
import com.palmergames.bukkit.towny.TownyAPI;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import ru.neverland.townydistricts.model.*;
import ru.neverland.townydistricts.service.DistrictService;
import java.util.concurrent.ThreadLocalRandom;

public final class ProductionListener implements Listener {
    private final DistrictService service;
    public ProductionListener(DistrictService service){this.service=service;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void smelt(FurnaceSmeltEvent event){
        if(!service.settings().furnaces()||event.getResult().hasItemMeta()||event.getSource().hasItemMeta())return;
        var block=event.getBlock();var d=service.districtAt(block.getWorld().getUID(),block.getX(),block.getZ()).orElse(null);
        if(d==null||d.type()!=DistrictType.INDUSTRIAL)return;
        var town=TownyAPI.getInstance().getTown(block.getLocation());if(town==null||!town.getUUID().equals(d.town()))return;
        if(!(block.getState() instanceof Furnace furnace))return;
        var result=event.getResult().clone();var existing=furnace.getInventory().getResult();
        if(existing!=null&&!existing.getType().isAir()&&!existing.isSimilar(result))return;
        int space=result.getMaxStackSize()-(existing==null||existing.getType().isAir()?0:existing.getAmount());
        int amount=DistrictRules.output(result.getAmount(),service.districtMultiplier(d),ThreadLocalRandom.current().nextDouble(),space);
        if(amount!=result.getAmount()){result.setAmount(amount);event.setResult(result);}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void grow(BlockGrowEvent event){
        if(!service.settings().crops()||!(event.getNewState().getBlockData() instanceof Ageable crop)||crop.getAge()>=crop.getMaximumAge())return;
        var block=event.getBlock();var d=service.districtAt(block.getWorld().getUID(),block.getX(),block.getZ()).orElse(null);
        if(d==null||d.type()!=DistrictType.AGRICULTURAL)return;
        var town=TownyAPI.getInstance().getTown(block.getLocation());if(town==null||!town.getUUID().equals(d.town()))return;
        double chance=Math.min(1,service.districtMultiplier(d)-1);
        if(ThreadLocalRandom.current().nextDouble()<chance){crop.setAge(crop.getAge()+1);event.getNewState().setBlockData(crop);}
    }
}

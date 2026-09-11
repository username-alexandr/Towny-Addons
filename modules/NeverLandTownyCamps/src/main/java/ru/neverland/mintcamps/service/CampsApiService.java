package ru.neverland.mintcamps.service;
import java.util.*;import org.bukkit.*;import org.bukkit.inventory.ItemStack;
import ru.neverland.mintcamps.api.TownyCampsApi;import ru.neverland.mintcamps.data.CampRepository;import ru.neverland.mintcamps.gui.CampInventoryHolder;
public final class CampsApiService implements TownyCampsApi {
    private final CampRepository repository;
    public CampsApiService(CampRepository repository){this.repository=repository;}
    private void thread(){if(!Bukkit.isPrimaryThread()||!repository.writable())throw new IllegalStateException("Хранилище лагерей недоступно");}
    private static ItemStack[] copy(ItemStack[] input){ItemStack[] result=new ItemStack[input.length];for(int i=0;i<input.length;i++)result[i]=input[i]==null?null:input[i].clone();return result;}
    public Map<String,Object> camp(UUID owner){thread();var c=repository.get(owner).orElse(null);if(c==null)return Map.of();Map<String,Object> result=new HashMap<>();result.put("owner",c.ownerId());result.put("world",c.worldId());result.put("worldName",c.worldName());result.put("x",c.anchor().x());result.put("y",c.anchor().y());result.put("z",c.anchor().z());result.put("level",c.level());result.put("burnUntil",c.burnUntil());result.put("trusted",Set.copyOf(c.trusted().keySet()));result.put("stash",copy(c.stash()));return Map.copyOf(result);}
    public boolean stashBusy(UUID owner){thread();for(var player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof CampInventoryHolder holder&&owner.equals(holder.campOwner()))return true;return false;}
    public boolean consume(UUID owner,ItemStack[] costs)throws java.io.IOException{
        thread();if(costs==null||costs.length>128)throw new IllegalArgumentException("Неверная стоимость экспедиции");var camp=repository.get(owner).orElse(null);if(camp==null||stashBusy(owner))return false;var before=copy(camp.stash());var work=copy(before);
        for(var cost:costs){if(cost==null||cost.getType().isAir()||cost.getAmount()<1||cost.getAmount()>1_000_000)throw new IllegalArgumentException("Неверный предмет стоимости");int left=cost.getAmount();for(int i=0;i<work.length&&left>0;i++){var item=work[i];if(item==null||!item.isSimilar(cost))continue;int take=Math.min(left,item.getAmount());left-=take;if(take==item.getAmount())work[i]=null;else item.setAmount(item.getAmount()-take);}if(left>0)return false;}
        camp.stash(work,work.length);repository.markDirty();try{repository.saveOrThrow();}catch(java.io.IOException|RuntimeException ex){camp.stash(before,before.length);throw ex;}return true;
    }
}

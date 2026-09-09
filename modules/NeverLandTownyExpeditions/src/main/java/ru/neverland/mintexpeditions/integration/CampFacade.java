package ru.neverland.mintexpeditions.integration;

import org.bukkit.*; import org.bukkit.entity.Player; import org.bukkit.inventory.*; import org.bukkit.plugin.Plugin; import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintexpeditions.model.ItemAmount;
import java.lang.invoke.*; import java.lang.reflect.Method; import java.util.*;

public final class CampFacade {
    public record CampView(Object camp,Object repository,UUID owner,World world,Location anchor,int level,long burnUntil,Set<UUID> trusted,ItemStack[] stash) {}
    private final JavaPlugin plugin; private final ru.neverland.mintexpeditions.service.RussianItemNames itemNames; private boolean warned;
    public CampFacade(JavaPlugin plugin,ru.neverland.mintexpeditions.service.RussianItemNames itemNames){this.plugin=plugin;this.itemNames=itemNames;}
    public CampView owned(Player player){
        return owned(player.getUniqueId());
    }
    public CampView owned(UUID playerId){
        Plugin source=resolveCampPlugin(); if(source==null)return null;
        try{
            ClassLoader cl=source.getClass().getClassLoader(); Class<?> repoClass=Class.forName("ru.neverland.mintcamps.data.CampRepository",false,cl);
            Object repo=repository(source,repoClass); Optional<?> found=(Optional<?>)repoClass.getMethod("get",UUID.class).invoke(repo,playerId); if(found.isEmpty())return null; Object c=found.get(); Class<?> cc=c.getClass();
            UUID owner=(UUID)call(cc,c,"ownerId"); UUID worldId=(UUID)call(cc,c,"worldId"); String worldName=(String)call(cc,c,"worldName"); World world=Bukkit.getWorld(worldId); if(world==null)world=Bukkit.getWorld(worldName);
            Object pos=call(cc,c,"anchor"); Class<?> pc=pos.getClass(); int x=(int)call(pc,pos,"x"),y=(int)call(pc,pos,"y"),z=(int)call(pc,pos,"z");
            @SuppressWarnings("unchecked") Map<UUID,String> trust=(Map<UUID,String>)call(cc,c,"trusted");
            return new CampView(c,repo,owner,world,new Location(world,x+.5,y+1,z+.5),(int)call(cc,c,"level"),(long)call(cc,c,"burnUntil"),new LinkedHashSet<>(trust.keySet()),(ItemStack[])call(cc,c,"stash"));
        }catch(Throwable e){if(!warned){warned=true;plugin.getLogger().severe("Не удалось подключиться к живому хранилищу NeverLandTownyCamps: "+e);}; return null;}
    }
    private Object repository(Plugin source,Class<?> repoClass)throws Throwable{
        try{
            Method api=source.getClass().getMethod("repository"); Object repository=api.invoke(source);
            if(repoClass.isInstance(repository))return repository;
            throw new IllegalStateException("NeverLandTownyCamps вернул несовместимое хранилище");
        }catch(NoSuchMethodException ignored){
            MethodHandle getter=MethodHandles.privateLookupIn(source.getClass(),MethodHandles.lookup()).findGetter(source.getClass(),"repository",repoClass);
            return getter.invoke(source);
        }
    }
    private Plugin resolveCampPlugin(){for(String name:List.of(plugin.getConfig().getString("camps.plugin","NeverLandTownyCamps"),"NeverLandTownyCamps","MintTownyCamps")){if(name==null||name.isBlank())continue;Plugin found=Bukkit.getPluginManager().getPlugin(name);if(found!=null&&found.isEnabled())return found;}return null;}
    private Object call(Class<?> c,Object o,String name)throws ReflectiveOperationException{Method m=c.getMethod(name);return m.invoke(o);}
    public boolean stashOpen(UUID owner){for(Player p:Bukkit.getOnlinePlayers()){InventoryHolder h=p.getOpenInventory().getTopInventory().getHolder(); if(h!=null&&h.getClass().getName().endsWith("CampInventoryHolder"))try{Object id=h.getClass().getMethod("campOwner").invoke(h);if(owner.equals(id))return true;}catch(ReflectiveOperationException ignored){}} return false;}
    public List<Player> party(CampView c){List<Player> out=new ArrayList<>(); Player leader=Bukkit.getPlayer(c.owner()); if(leader!=null)out.add(leader); int cap=plugin.getConfig().getInt("camps.maximum-party-by-level."+c.level(),c.level()*2); double radius=plugin.getConfig().getDouble("camps.party-radius",20); for(UUID id:c.trusted()){Player p=Bukkit.getPlayer(id);if(p!=null&&p.getWorld().equals(c.world())&&p.getLocation().distanceSquared(c.anchor())<=radius*radius&&out.size()<cap)out.add(p);} return out;}
    public String missing(CampView c,List<ItemAmount> costs){List<String> missing=new ArrayList<>(); for(ItemAmount cost:costs){int have=0;for(ItemStack s:c.stash())if(s!=null&&s.isSimilar(cost.item()))have+=s.getAmount();if(have<cost.amount())missing.add(itemNames.name(cost.item())+" × "+(cost.amount()-have));}return String.join(", ",missing);}
    public boolean consume(CampView c,List<ItemAmount> costs){if(!missing(c,costs).isEmpty())return false; for(ItemAmount cost:costs){int left=cost.amount(); for(int i=0;i<c.stash().length&&left>0;i++){ItemStack s=c.stash()[i];if(s==null||!s.isSimilar(cost.item()))continue;int take=Math.min(left,s.getAmount());s.setAmount(s.getAmount()-take);left-=take;if(s.getAmount()<=0)c.stash()[i]=null;}} try{c.repository().getClass().getMethod("markDirty").invoke(c.repository());c.repository().getClass().getMethod("save").invoke(c.repository());return true;}catch(ReflectiveOperationException e){return false;}}
}

package ru.neverland.mintcontracts.service;
import com.palmergames.bukkit.towny.object.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.integration.TownyHook;
import ru.neverland.mintcontracts.model.*;
import java.util.*;

public final class MunicipalDrafts {
    public record Draft(UUID revision,UUID town,ContractDefinition definition,WorkArea area,long expires) {}
    private record Selection(Location first,Location second,long expires) {}
    private final JavaPlugin plugin;private final TownyHook towny;private final ContractService contracts;private final RussianNames names;
    private final Map<UUID,Draft> drafts=new HashMap<>();private final Map<UUID,Selection> selections=new HashMap<>();
    public MunicipalDrafts(JavaPlugin plugin,TownyHook towny,ContractService contracts,RussianNames names){this.plugin=plugin;this.towny=towny;this.contracts=contracts;this.names=names;}
    public Town manager(Player p){Town t=towny.town(p);if(t==null||!p.hasPermission("mintcontracts.manage")||!towny.isManager(p,t))throw new IllegalArgumentException("Публиковать задания может мэр или уполномоченный заместитель.");return t;}
    public void clear(Player p){drafts.remove(p.getUniqueId());selections.remove(p.getUniqueId());}
    public void select(Player p,boolean first){manager(p);Block b=p.getTargetBlockExact(10);Location l=(b==null?p.getLocation().clone().subtract(0,1,0):b.getLocation());Selection old=selections.get(p.getUniqueId());if(old==null||old.expires()<System.currentTimeMillis())old=new Selection(null,null,0);
        selections.put(p.getUniqueId(),new Selection(first?l:old.first(),first?old.second():l,System.currentTimeMillis()+600_000));
        p.sendMessage("§aТочка "+(first?1:2)+": "+l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ()+". Выделение действует 10 минут.");
    }
    public Draft get(Player p){Draft d=drafts.get(p.getUniqueId());if(d==null||d.expires()<System.currentTimeMillis()||towny.town(p)==null||!d.town().equals(towny.town(p).getUUID())){drafts.remove(p.getUniqueId());throw new IllegalArgumentException("Черновик истёк. Создайте задание заново.");}return d;}
    public Draft template(Player p,ContractDefinition d){if(d==null)throw new IllegalArgumentException("Шаблон не найден.");if(d.type()==ContractType.ROAD||d.type()==ContractType.SCOUT)throw new IllegalArgumentException("Используйте конструктор участка дороги или разведки.");return remember(p,d,null);}
    public Draft create(Player p,ContractType type,String target,int amount,String reward,long hours){
        manager(p);long cents=MunicipalPayment.amount(reward);if(cents>Math.min(100_000_000_000L,Math.max(0,plugin.getConfig().getLong("municipal.max-reward",1_000_000))*100))throw new IllegalArgumentException("Награда превышает лимит.");
        if(hours<1||hours>Math.min(8760,Math.max(1,plugin.getConfig().getLong("municipal.max-hours",720))))throw new IllegalArgumentException("Недопустимый срок задания в часах.");
        if(amount<1||amount>Math.min(1_000_000,Math.max(1,plugin.getConfig().getInt("municipal.max-amount",1000000))))throw new IllegalArgumentException("Недопустимое количество.");
        ItemStack item=null;Material icon;WorkArea area=null;String name;
        switch(type){
            case DELIVERY -> {item=target.equalsIgnoreCase("hand")?p.getInventory().getItemInMainHand().clone():contracts.registry().targetItem(target);if(item==null||item.getType().isAir())throw new IllegalArgumentException("Возьмите нужный предмет в основную руку.");item.setAmount(1);target=item.getType().name();icon=item.getType();name="Поставка: "+names.item(item);}
            case MOB_KILL -> {EntityType entity;try{entity=EntityType.valueOf(target.toUpperCase(Locale.ROOT));}catch(Exception ex){throw new IllegalArgumentException("Неизвестный тип моба.");}if(!entity.isAlive()||entity==EntityType.PLAYER||entity==EntityType.ARMOR_STAND)throw new IllegalArgumentException("Выберите живого моба.");target=entity.name();icon=Material.IRON_SWORD;name="Охота: "+names.value(target);}
            case ROAD -> {Material material=ru.neverland.localization.MaterialNameConfig.matchMaterial(target);if(material==null||!roadMaterials().contains(material))throw new IllegalArgumentException("Этот материал не разрешён для дорог.");target=material.name();icon=material;area=area(p,type,material);amount=area.required().size();name="Строительство дороги";}
            case SCOUT -> {target="AREA";icon=Material.FILLED_MAP;area=area(p,type,null);amount=area.required().size();name="Разведка территории";}
            default -> throw new IllegalArgumentException("Тип недоступен в конструкторе.");
        }
        var d=new ContractDefinition("municipal_"+UUID.randomUUID().toString().replace("-",""),name,type,icon,0,List.of("Муниципальное задание города"),target,item,amount,cents/100.0,hours*3600);
        return remember(p,d,area);
    }
    public Draft edit(Player p,int amountDelta,long rewardDelta,long hoursDelta,String target){
        Draft old=get(p);ContractDefinition d=old.definition();
        if(d.type()!=ContractType.ROAD&&d.type()!=ContractType.SCOUT&&target==null){long cents=MunicipalPayment.amount(java.math.BigDecimal.valueOf(d.reward()).add(java.math.BigDecimal.valueOf(rewardDelta)).toPlainString());int goal=Math.addExact(d.goal(),amountDelta);long hours=d.durationSeconds()/3600+hoursDelta;
            if(goal<1||goal>Math.min(1000000,plugin.getConfig().getInt("municipal.max-amount",1000000))||hours<1||hours>Math.min(8760,plugin.getConfig().getLong("municipal.max-hours",720))||cents>Math.min(100000000000L,Math.max(0,plugin.getConfig().getLong("municipal.max-reward",1000000))*100))throw new IllegalArgumentException("Достигнут предел значения.");
            return remember(p,new ContractDefinition(d.id(),d.name(),d.type(),d.icon(),0,d.description(),d.target(),d.deliveryItem(),goal,cents/100.0,hours*3600),null);
        }
        return create(p,d.type(),target==null?d.target():target,Math.max(1,Math.addExact(d.goal(),amountDelta)),java.math.BigDecimal.valueOf(d.reward()).add(java.math.BigDecimal.valueOf(rewardDelta)).toPlainString(),d.durationSeconds()/3600+hoursDelta);
    }
    public ContractService.ActivateResult confirm(Player p,UUID revision){Town town=manager(p);Draft d=get(p);if(!d.revision().equals(revision))throw new IllegalArgumentException("Условия изменились. Откройте актуальный черновик.");
        if(d.area()!=null){WorkArea current=area(p,d.definition().type(),Material.matchMaterial(d.definition().target()));if(!current.equals(d.area()))throw new IllegalArgumentException("Участок изменился. Пересоздайте черновик.");}
        drafts.remove(p.getUniqueId());return contracts.activate(town,d.definition(),d.area());
    }
    private Draft remember(Player p,ContractDefinition d,WorkArea area){Draft draft=new Draft(UUID.randomUUID(),manager(p).getUUID(),d,area,System.currentTimeMillis()+600_000);drafts.put(p.getUniqueId(),draft);return draft;}
    public List<Material> roadMaterials(){List<String> keys=plugin.getConfig().getStringList("municipal.road.materials");if(keys.isEmpty())keys=List.of("COBBLESTONE","STONE_BRICKS","SMOOTH_STONE","GRAVEL","ANDESITE");return keys.stream().map(Material::matchMaterial).filter(Objects::nonNull).filter(m->m.isBlock()&&m.isSolid()&&m.isItem()).distinct().toList();}
    private WorkArea area(Player p,ContractType type,Material material){
        Selection s=selections.get(p.getUniqueId());if(s==null||s.first()==null||s.second()==null||s.expires()<System.currentTimeMillis())throw new IllegalArgumentException("Выделите две точки: /t contracts pos1 и /t contracts pos2.");
        Location a=s.first(),b=s.second();if(!a.getWorld().equals(b.getWorld())||!a.getWorld().equals(p.getWorld()))throw new IllegalArgumentException("Обе точки должны находиться в вашем текущем мире.");
        int shift=type==ContractType.SCOUT?4:0,x1=a.getBlockX()>>shift,z1=a.getBlockZ()>>shift,x2=b.getBlockX()>>shift,z2=b.getBlockZ()>>shift;
        int limit=type==ContractType.ROAD?Math.min(1024,Math.max(1,plugin.getConfig().getInt("municipal.road.max-blocks",256))):Math.min(256,Math.max(1,plugin.getConfig().getInt("municipal.scout.max-chunks",64)));
        Set<String> cells=new LinkedHashSet<>(WorkArea.rectangle(x1,z1,x2,z2,limit));int y=a.getBlockY();
        if(type==ContractType.ROAD){if(y!=b.getBlockY()||y<=p.getWorld().getMinHeight()||y+2>=p.getWorld().getMaxHeight())throw new IllegalArgumentException("Покрытие дороги должно находиться на одной высоте.");Town town=manager(p);
            for(String key:new ArrayList<>(cells)){int[] xy=WorkArea.point(key);Location l=new Location(p.getWorld(),xy[0],y,xy[1]);if(!p.getWorld().isChunkLoaded(xy[0]>>4,xy[1]>>4))throw new IllegalArgumentException("Загрузите участок, подойдя к нему.");if(!WorldCoord.parseWorldCoord(l).hasTown(town))throw new IllegalArgumentException("Вся дорога должна находиться на территории города.");if(l.getBlock().getType()==material)cells.remove(key);}
            if(cells.isEmpty())throw new IllegalArgumentException("Участок уже покрыт выбранным материалом.");
        }
        return new WorkArea(p.getWorld().getUID(),Math.min(x1,x2),y,Math.min(z1,z2),Math.max(x1,x2),Math.max(z1,z2),cells);
    }
}

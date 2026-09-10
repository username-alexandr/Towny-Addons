package ru.neverland.townyspecialization.gui;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import ru.neverland.townyspecialization.api.SpecializationSnapshot;
import ru.neverland.townyspecialization.model.Specialization;
import ru.neverland.townyspecialization.service.*;
import java.util.*;
import static ru.neverland.townyspecialization.service.Ui.*;
public final class SpecializationMenu implements Listener{
    private final org.bukkit.plugin.Plugin plugin;private final SpecializationService service;
    private static final class View implements InventoryHolder{final UUID town;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;View(UUID town){this.town=town;}public Inventory getInventory(){return inventory;}}
    public SpecializationMenu(org.bukkit.plugin.Plugin plugin,SpecializationService service){this.plugin=plugin;this.service=service;}
    private boolean access(Player p,UUID town){var own=service.town(p);return p.hasPermission("neverlandtownyspecialization.admin")||(p.hasPermission("neverlandtownyspecialization.use")&&own!=null&&own.getUUID().equals(town));}
    public void open(Player p,UUID town,String id){if(!access(p,town)){tell(p,"Нет доступа к этому городу.");return;}var s=service.specialization(town).orElse(null);if(s==null){tell(p,"Ожидается расчёт города.");return;}var settings=service.settings();var v=new View(town);v.inventory=Bukkit.createInventory(v,54,color("&6Специализация города"));
        if(id==null){int slot=10;for(var profile:settings.profiles().values())button(v,slot++,icon(profile.icon()),"&e"+profile.name(),lore(s,profile),()->open(p,town,profile.id()));}
        else{var profile=settings.profiles().get(id);if(profile==null){tell(p,"Направление не найдено.");return;}button(v,13,icon(profile.icon()),"&e"+profile.name(),lore(s,profile),null);button(v,22,Material.BELL,"&eТребования выбора",List.of("Уровень города Towny: "+s.townLevel()+"/"+settings.minimumTownLevel(),"Уровень ратуши: "+s.hallLevel()+"/"+settings.minimumHallLevel(),"Смена доступна через: "+waitTime(s.state().nextChangeAt()),"Выбор и смена бесплатны."),null);
            var own=service.town(p);if(own!=null&&own.getUUID().equals(town)&&!s.state().specialization().equals(id))button(v,31,Material.LIME_DYE,"&aПодтвердить выбор: "+profile.name(),List.of("Предыдущее направление будет заменено.","Его уникальное здание перестанет работать.","Постройка и содержимое склада сохранятся.","Следующая смена через "+settings.cooldownMillis()/3600000+" ч."),()->{try{service.choose(p,town,id,s.state().revision(),settings,false);tell(p,"Выбрано направление: "+profile.name());open(p,town,null);}catch(Exception ex){tell(p,"&c"+ex.getMessage());}});
            button(v,45,Material.ARROW,"&aВсе направления",List.of(),()->open(p,town,null));}
        var chosen=settings.profiles().get(s.state().specialization());button(v,40,Material.CLOCK,"&eСостояние города",List.of("Город: "+s.townName(),"Направление: "+(chosen==null?"Не выбрано":chosen.name()),s.status(),"Рабочий уровень уникального здания: "+s.uniqueLevel(),"Смена через: "+waitTime(s.state().nextChangeAt())),null);button(v,48,Material.BRICKS,"&aГородские постройки",List.of("Уникальные проекты находятся в своих категориях."),()->p.performCommand("town builds"));button(v,49,Material.SPYGLASS,"&aОбновить",List.of(),()->open(p,town,id));p.openInventory(v.inventory);
    }
    private List<String> lore(SpecializationSnapshot s,Specialization profile){var lines=new ArrayList<String>();lines.add(profile.description());lines.add("Уникальное здание: "+profile.buildingName());lines.add("Дополнительные бонусы с уровня "+profile.minimumBuildingLevel());for(String effect:profile.bonuses().keySet()){lines.add(EffectNames.name(effect)+": "+EffectNames.value(effect,profile.bonus(effect,0))+" → "+EffectNames.value(effect,profile.bonus(effect,5)));}if(s.state().specialization().equals(profile.id()))lines.add("&aВыбрано этим городом");if(!profile.enabled())lines.add("&cОтключено в настройках");lines.add("Нажмите: требования и подтверждение.");return lines;}
    private static String waitTime(long until){long seconds=Math.max(0,(until-System.currentTimeMillis()+999)/1000);return seconds==0?"доступна сейчас":seconds/86400+" д. "+seconds%86400/3600+" ч. "+seconds%3600/60+" мин. "+seconds%60+" сек.";}
    private void button(View v,int slot,Material material,String name,List<String> lore,Runnable action){var item=new ItemStack(material);var meta=item.getItemMeta();meta.setDisplayName(color(name));meta.setLore(lore.stream().map(s->color("&7"+s)).toList());item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;if(!access(p,v.town)){p.closeInventory();return;}var action=v.actions.get(e.getRawSlot());if(action!=null)Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==v&&access(p,v.town))action.run();});}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}

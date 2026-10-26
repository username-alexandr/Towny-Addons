package ru.neverland.townycontrol;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import ru.neverland.core.*;

final class PersonalHistoryMenu implements Listener {
    static final String PERMISSION="neverlandtownycontrol.history";
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Europe/Moscow"));
    private final NeverLandTownyControl plugin;
    private final Map<UUID,Long> cooldown=new HashMap<>();
    private final Semaphore reading=new Semaphore(2);
    static final class View implements InventoryHolder {
        final UUID owner;final PersonalHistory.Category category;final int page;final Inventory previous;
        final Map<Integer,Runnable> buttons=new HashMap<>();Inventory inventory;boolean busy;
        View(UUID owner,PersonalHistory.Category category,int page,Inventory previous){this.owner=owner;this.category=category;this.page=page;this.previous=previous;}
        @Override public Inventory getInventory(){return inventory;}
    }
    PersonalHistoryMenu(NeverLandTownyControl plugin){
        this.plugin=plugin;Bukkit.getPluginManager().registerEvents(this,plugin);
        plugin.getCommand("nlthistory").setExecutor((sender,command,label,args)->{
            if(!(sender instanceof Player player)){sender.sendMessage("История открывается игроком в игре.");return true;}
            try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()){
                if(!player.hasPermission(PERMISSION)){player.sendMessage("§eПросмотр личной истории отключён для вашей группы. Обратитесь к администрации.");return true;}
                if(args.length!=0){player.sendMessage("§e/nlthistory — открыть только свою историю операций.");return true;}
                load(player,PersonalHistory.Category.ALL,1,MenuStyle.previousMenu(player));return true;
            }
        });
        plugin.getCommand("nlthistory").setTabCompleter((s,c,l,a)->List.of());
    }
    private View screen(Player player,PersonalHistory.Category category,int page,Inventory previous){
        var view=new View(player.getUniqueId(),category,page,previous);view.inventory=MenuStyle.inventory(plugin,view,54,"Мои операции • "+category.label);
        for(int slot=36;slot<54;slot++)view.inventory.setItem(slot,item(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));
        button(view,45,Material.ARROW,"Назад",List.of("Вернуться в предыдущий раздел"),"back",()->MenuStyle.returnTo(player,previous));
        button(view,53,Material.BARRIER,"Закрыть",List.of("Вернуться в игру"),"close",player::closeInventory);
        return view;
    }
    private void load(Player player,PersonalHistory.Category category,int page,Inventory previous){
        if(!player.hasPermission(PERMISSION)||!plugin.isEnabled())return;
        long now=System.nanoTime();if(now-cooldown.getOrDefault(player.getUniqueId(),0L)<1_000_000_000L){player.sendMessage("§7Подождите немного перед обновлением истории.");return;}
        if(!reading.tryAcquire()){player.sendMessage("§eИстория сейчас загружается у других игроков. Попробуйте чуть позже.");return;}
        cooldown.put(player.getUniqueId(),now);int requested=Math.max(1,Math.min(page,100));
        var view=screen(player,category,requested,previous);view.inventory.setItem(22,item(Material.CLOCK,"Загружаем вашу историю",List.of("Можно закрыть окно: операции продолжат работать.")));player.openInventory(view.inventory);
        var owner=player.getUniqueId();var root=plugin.getDataFolder().toPath().getParent();
        Bukkit.getAsyncScheduler().runNow(plugin,task->{
            AuditReader.Page result=null;String error="";
            try{result=AuditReader.page(root,PersonalHistory.filter(owner,category),requested,36);}
            catch(Exception ex){plugin.getLogger().warning("Личная история не прочитана: "+ex.getMessage());error="История временно недоступна. Деньги и материалы не изменены.";}
            finally{reading.release();}
            var answer=result;String failure=error;
            SuiteScheduler.entity(plugin,player,()->{
                if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=view.inventory||!player.hasPermission(PERMISSION))return;
                for(int i=0;i<36;i++)view.inventory.setItem(i,null);
                if(answer==null)view.inventory.setItem(22,item(Material.PAPER,failure,List.of("Попробуйте позже или сообщите администратору.")));
                else{
                    int slot=0;for(var r:answer.rows()){
                        var lore=new ArrayList<String>();lore.add(TIME.format(Instant.ofEpochMilli(r.at()))+" МСК");lore.addAll(PersonalHistory.lines(r,owner));
                        Material material=Material.PAPER;if(!r.asset().isEmpty()){var parsed=Material.matchMaterial(r.asset().split(":",2)[0]);if(parsed!=null&&parsed.isItem()&&!parsed.isAir())material=parsed;}
                        var icon=item(material,PersonalHistory.title(r),lore);
                        if(!r.asset().isEmpty()&&material!=Material.PAPER){var meta=icon.getItemMeta();var lines=meta.lore();lines=new ArrayList<>(lines==null?List.of():lines);lines.add(net.kyori.adventure.text.Component.text("Предмет: ").append(net.kyori.adventure.text.Component.translatable(material.translationKey())));meta.lore(lines);icon.setItemMeta(meta);}
                        view.inventory.setItem(slot++,icon);
                    }
                    if(answer.rows().isEmpty())view.inventory.setItem(22,item(Material.BOOK,"Пока нет записей",List.of("Здесь появятся ваши награды, переводы и материалы.","История показывает сохранённые операции аддонов.")));
                    if(requested>1)button(view,47,Material.ARROW,"Предыдущая страница",List.of(),"previous",()->load(player,category,requested-1,previous));
                    if((long)requested*36<answer.matched()&&requested<100)button(view,51,Material.ARROW,"Следующая страница",List.of(),"next",()->load(player,category,requested+1,previous));
                    view.inventory.setItem(48,item(Material.PAPER,"Страница "+requested,List.of("Записей: "+answer.matched(),"Движения счёта показаны отдельно от квитанций.")));
                }
                var next=PersonalHistory.Category.values()[(category.ordinal()+1)%PersonalHistory.Category.values().length];
                button(view,49,Material.COMPARATOR,"Фильтр: "+category.label,List.of("Переключить на: "+next.label),"",()->load(player,next,1,previous));
                button(view,50,Material.CLOCK,"Обновить",List.of("Проверить новые операции"),"",()->load(player,category,requested,previous));
                player.openInventory(view.inventory);
            });
        });
    }
    private static ItemStack item(Material icon,String name,List<String> lore){var item=new ItemStack(icon);var meta=item.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(name)));meta.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES,ItemFlag.HIDE_ENCHANTS);item.setItemMeta(meta);return item;}
    private void button(View view,int slot,Material icon,String name,List<String> lore,String role,Runnable action){var item=item(icon,name,lore);if(!role.isEmpty()){var meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("neverland","menu_role"),PersistentDataType.STRING,role);item.setItemMeta(meta);}view.inventory.setItem(slot,item);view.buttons.put(slot,action);}
    @EventHandler public void click(InventoryClickEvent event){try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()){
        if(!(event.getView().getTopInventory().getHolder() instanceof View view))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!view.owner.equals(player.getUniqueId())||!player.hasPermission(PERMISSION)||view.busy)return;
        Runnable action=view.buttons.get(event.getRawSlot());if(action==null)return;view.busy=true;
        SuiteScheduler.entity(plugin,player,()->{try{if(player.getOpenInventory().getTopInventory()==view.inventory&&player.hasPermission(PERMISSION))action.run();}finally{view.busy=false;}});
    }}
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof View)event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()){cooldown.remove(event.getPlayer().getUniqueId());}}
}

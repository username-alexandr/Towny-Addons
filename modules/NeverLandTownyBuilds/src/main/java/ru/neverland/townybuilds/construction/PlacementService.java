package ru.neverland.townybuilds.construction;

import com.palmergames.bukkit.towny.object.Town;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.core.*;
import ru.neverland.townybuilds.data.*;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.model.*;
import ru.neverland.townybuilds.service.*;
import ru.neverland.townybuilds.util.ColorUtil;

/** Selection is free. Only the explicit final button invokes the existing purchase/refund transaction. */
public final class PlacementService implements Listener,CommandExecutor {
    private final JavaPlugin plugin;private final TownyHook towny;private final DataStore data;private final DefinitionRegistry definitions;
    private final ConstructionService construction;private final BuildService builds;private final NamespacedKey token,townKey,projectKey;
    private final Map<UUID,Session> sessions=new HashMap<>();private BukkitTask timer;
    private static final class Session {
        final UUID town;final String project;final PlacementDraft draft;final PlacementChoice choice;final ConstructionPreparation check;
        final PlacementProjection projection;final long expires=System.currentTimeMillis()+300_000;boolean notified;
        Session(UUID town,String project,PlacementDraft draft,PlacementChoice choice,ConstructionPreparation check,PlacementProjection projection){this.town=town;this.project=project;this.draft=draft;this.choice=choice;this.check=check;this.projection=projection;}
    }
    public static final class PlacementMenu implements InventoryHolder {
        final UUID viewer;final Session session;final Map<Integer,Runnable> actions=new HashMap<>();Inventory inventory;boolean used;
        PlacementMenu(Player p,Session s){viewer=p.getUniqueId();session=s;}
        @Override public Inventory getInventory(){return inventory;}
    }
    public PlacementService(JavaPlugin plugin,TownyHook towny,DataStore data,DefinitionRegistry definitions,ConstructionService construction,BuildService builds){
        this.plugin=plugin;this.towny=towny;this.data=data;this.definitions=definitions;this.construction=construction;this.builds=builds;
        token=new NamespacedKey(plugin,"placement_token");townKey=new NamespacedKey(plugin,"placement_town");projectKey=new NamespacedKey(plugin,"placement_project");
    }
    public void start(){stop();timer=SuiteScheduler.get().runTaskTimer(plugin,this::tick,2,2);}
    public void stop(){if(timer!=null)timer.cancel();timer=null;for(UUID id:List.copyOf(sessions.keySet()))close(id);}
    private void close(UUID id){var s=sessions.remove(id);if(s!=null)s.projection.close();}
    private Town resident(Player p){
        if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!data.writable())throw new IllegalArgumentException("Строительство временно недоступно");
        if(!p.hasPermission("neverlandtownybuilds.use"))throw new IllegalArgumentException("Нет доступа к строительству");
        Town t=towny.town(p);if(t==null)throw new IllegalArgumentException("Сначала вступите в город");return t;
    }
    private boolean free(TownData state,ProjectDefinition p,int level){return p.type()==ProjectType.WONDER&&state.level(p.id())==p.maxLevel()&&level==p.maxLevel()&&state.constructionSite(p.id())==null;}
    private boolean resources(TownData state,ProjectDefinition p,int level){
        if(free(state,p,level))return true;var next=p.level(level);if(next==null)return false;
        var fund=state.existingResourceFund(p.id());return next.resources().stream().allMatch(i->fund!=null&&fund.targetLevel()==level&&fund.count(i)>=i.getAmount());
    }
    public void issue(Player player,Town town,ProjectDefinition project,int level){synchronized(SuiteState.lock()){
        if(!resident(player).getUUID().equals(town.getUUID()))throw new IllegalArgumentException("Город изменился");
        var state=data.town(town.getUUID());var site=state.constructionSite(project.id());
        if(site!=null&&site.active())throw new IllegalArgumentException("Это здание уже строится");
        if(level!=state.level(project.id())+1&&!free(state,project,level))throw new IllegalArgumentException("Уровень здания изменился");
        if(!resources(state,project,level))throw new IllegalArgumentException("Сначала сдайте все необходимые ресурсы");
        var r=state.constructionReceipts().get(project.id());if(r!=null&&(!Set.of("DONE","COMPLETE").contains(r.state())||!r.remaining().isEmpty()))throw new IllegalArgumentException("Дождитесь завершения возврата предыдущей стройки");
        var old=state.placements().get(project.id());var draft=old!=null&&old.level()==level?old:PlacementDraft.create(level);
        if(draft!=old){state.placement(project.id(),draft);try{data.saveOrThrow();}catch(Exception e){state.placement(project.id(),old);throw new IllegalStateException("Не удалось сохранить чертёж",e);}}
        for(var item:player.getInventory().getStorageContents())if(matches(item,draft.id())){player.sendMessage("§aЧертёж уже у вас. ПКМ по земле — показать здание; Shift + ПКМ — повернуть; /nltplace — подтвердить место.");return;}
        ItemStack item=new ItemStack(Material.STRUCTURE_VOID);var meta=item.getItemMeta();
        meta.displayName(MenuStyle.nameComponent(MenuStyle.decode("&bЧертёж: "+ColorUtil.plain(project.name())+" · "+level+" ур.")));
        meta.lore(MenuStyle.loreComponents(List.of("ПКМ по земле — показать всё здание","Shift + ПКМ — повернуть проекцию","/nltplace — сдвинуть или подтвердить место","Ресурсы сохранены в фонде города","Деньги спишутся только после подтверждения").stream().map(MenuStyle::decode).toList()));
        var pdc=meta.getPersistentDataContainer();pdc.set(token,PersistentDataType.STRING,draft.id().toString());pdc.set(townKey,PersistentDataType.STRING,town.getUUID().toString());pdc.set(projectKey,PersistentDataType.STRING,project.id());item.setItemMeta(meta);
        if(player.getInventory().firstEmpty()<0){player.sendMessage("§eЧертёж готов. Освободите ячейку и получите его в меню здания. Ресурсы сохранены.");return;}
        player.getInventory().addItem(item);player.sendMessage("§aЧертёж выдан. ПКМ по земле — проекция, Shift + ПКМ — поворот, /nltplace — настройка и подтверждение.");
    }}
    private boolean matches(ItemStack item,UUID id){return item!=null&&item.hasItemMeta()&&id.toString().equals(item.getItemMeta().getPersistentDataContainer().get(token,PersistentDataType.STRING));}
    private void use(Player p,ItemStack item,Location clicked){
        var meta=item.getItemMeta().getPersistentDataContainer();Town town=resident(p);String project=meta.get(projectKey,PersistentDataType.STRING);
        if(!town.getUUID().toString().equals(meta.get(townKey,PersistentDataType.STRING)))throw new IllegalArgumentException("Этот чертёж относится к другому городу");
        var draft=data.town(town.getUUID()).placements().get(project);if(draft==null||!matches(item,draft.id()))throw new IllegalArgumentException("Этот чертёж больше не действует. Получите новый в меню здания.");
        var old=sessions.get(p.getUniqueId());
        if(p.isSneaking()&&old!=null&&old.draft.equals(draft)){select(p,town,project,draft,old.choice.rotate());return;}
        if(clicked==null){if(old!=null)menu(p,old);else throw new IllegalArgumentException("Нажмите чертежом на землю");return;}
        select(p,town,project,draft,new PlacementChoice(clicked.getWorld().getUID(),clicked.getBlockX(),clicked.getBlockY()+1,clicked.getBlockZ(),p.getFacing()));
    }
    private void select(Player p,Town town,String projectId,PlacementDraft draft,PlacementChoice choice){
        var project=definitions.project(projectId);if(project==null)throw new IllegalArgumentException("Здание больше не доступно");
        var state=data.town(town.getUUID());if(!draft.equals(state.placements().get(projectId)))throw new IllegalArgumentException("Чертёж изменился");
        if(!resources(state,project,draft.level()))throw new IllegalArgumentException("В фонде не хватает ресурсов");
        var old=state.constructionSite(projectId);if(old!=null&&old.active())throw new IllegalArgumentException("Здание уже строится");
        if(old!=null)choice=new PlacementChoice(old.worldId(),old.originX(),old.originY(),old.originZ(),old.facing());
        if(!p.getWorld().getUID().equals(choice.world()))throw new IllegalArgumentException("Перейдите в мир площадки");
        var anchor=new Location(p.getWorld(),choice.x(),choice.y(),choice.z());
        if(anchor.distanceSquared(p.getLocation())>64*64||!SuiteScheduler.owns(anchor))throw new IllegalArgumentException("Подойдите ближе к выбранному месту");
        var plan=construction.previewPlan(projectId,draft.level(),old);if(plan==null)throw new IllegalArgumentException("Чертёж здания недоступен");
        var site=new ConstructionSite(projectId,choice.world(),choice.x(),choice.y(),choice.z(),choice.facing(),state.level(projectId),draft.level(),old==null?1:draft.level(),true,old==null?ConstructionSite.CURRENT_ARCHITECTURE_VERSION:old.architectureVersion());
        int current=free(state,project,draft.level())?0:state.level(projectId);
        var checked=construction.prepare(p,town,project,current,draft.level(),choice);
        var projection=new PlacementProjection(plugin,p,site,plan,checked.status()==ConstructionPreparation.Status.READY);
        int perPlayer=Math.max(128,Math.min(30000,plugin.getConfig().getInt("settings.construction.placement.max-displays-per-player",8000)));
        int global=Math.max(perPlayer,Math.min(60000,plugin.getConfig().getInt("settings.construction.placement.max-displays-total",16000)));
        int reserved=sessions.entrySet().stream().filter(e->!e.getKey().equals(p.getUniqueId())).mapToInt(e->e.getValue().projection.required()).sum();
        if(projection.required()>perPlayer)throw new IllegalArgumentException("Это здание превышает разрешённый размер предпросмотра. Сообщите администратору.");
        if(reserved+projection.required()>global)throw new IllegalArgumentException("Сейчас открыто много проекций. Повторите немного позже.");
        close(p.getUniqueId());var session=new Session(town.getUUID(),projectId,draft,choice,checked,projection);sessions.put(p.getUniqueId(),session);
        p.sendMessage("§bПоказываю здание целиком. /nltplace — настройка и подтверждение. Для другого места снова нажмите по земле.");
        if(checked.status()!=ConstructionPreparation.Status.READY)p.sendMessage("§eМесто пока не подходит: "+String.join("; ",checked.details()));
    }
    private void tick(){
        for(var entry:List.copyOf(sessions.entrySet())){
            Player p=Bukkit.getPlayer(entry.getKey());Session s=entry.getValue();if(p==null){close(entry.getKey());continue;}
            SuiteScheduler.entity(plugin,p,()->{
                if(sessions.get(p.getUniqueId())!=s)return;
                try{Town t=resident(p);if(!t.getUUID().equals(s.town)||!s.projection.near()||System.currentTimeMillis()>s.expires||!s.draft.equals(data.town(s.town).placements().get(s.project))){close(p.getUniqueId());return;}
                    int batch=Math.max(1,Math.min(256,plugin.getConfig().getInt("settings.construction.placement.displays-per-tick",96)));s.projection.render(batch);
                    if(s.projection.complete()&&!s.notified){s.notified=true;p.sendMessage("§aПроекция готова. Осмотрите её со всех сторон. /nltplace — подтвердить или сдвинуть.");}
                }catch(Exception e){close(p.getUniqueId());p.sendMessage("§eПредпросмотр закрыт: "+e.getMessage());}
            });
        }
    }
    private void menu(Player p,Session s){
        if(sessions.get(p.getUniqueId())!=s)throw new IllegalArgumentException("Откройте проекцию заново");
        var holder=new PlacementMenu(p,s);holder.inventory=MenuStyle.inventory(plugin,holder,54,"Размещение здания");
        var project=definitions.project(s.project);var state=data.town(s.town);
        button(holder,4,Material.STRUCTURE_VOID,ColorUtil.plain(project.name()),List.of("Уровень: "+s.draft.level(),"Место: "+s.choice.x()+" / "+s.choice.y()+" / "+s.choice.z(),"Из казны при подтверждении: "+(free(state,project,s.draft.level())?"0":AuditTrail.money(project.level(s.draft.level()).money()))),null);
        boolean fixed=state.constructionSite(s.project)!=null;
        if(!fixed){
            button(holder,10,Material.COMPASS,"На север",List.of("Сдвинуть на один блок"),()->move(p,s,0,0,-1));button(holder,19,Material.COMPASS,"На юг",List.of("Сдвинуть на один блок"),()->move(p,s,0,0,1));
            button(holder,12,Material.COMPASS,"На запад",List.of("Сдвинуть на один блок"),()->move(p,s,-1,0,0));button(holder,21,Material.COMPASS,"На восток",List.of("Сдвинуть на один блок"),()->move(p,s,1,0,0));
            button(holder,14,Material.ARROW,"Выше",List.of("Поднять на один блок"),()->move(p,s,0,1,0));button(holder,23,Material.ARROW,"Ниже",List.of("Опустить на один блок"),()->move(p,s,0,-1,0));
            button(holder,16,Material.REPEATER,"Повернуть",List.of("Поворот по часовой стрелке"),()->{select(p,resident(p),s.project,s.draft,s.choice.rotate());menu(p,sessions.get(p.getUniqueId()));});
        }else button(holder,22,Material.ANVIL,"Улучшение на прежнем месте",List.of("Место существующего здания сохраняется"),null);
        boolean ready=s.projection.complete();var lore=new ArrayList<String>();lore.add("Начать стройку именно на этом месте");lore.add("Деньги спишутся из казны; ресурсы — из фонда");if(!ready)lore.add("Дождитесь появления всей проекции");if(!s.check.details().isEmpty())lore.addAll(s.check.details());
        button(holder,40,ready?Material.LIME_CONCRETE:Material.CLOCK,ready?"Подтвердить место и начать стройку":"Проекция ещё загружается",lore,ready?()->confirm(p,s):()->menu(p,s));
        button(holder,45,Material.SPYGLASS,"Осмотреть здание",List.of("Закрыть меню, оставить проекцию"),p::closeInventory);
        button(holder,49,Material.BARRIER,"Отложить размещение",List.of("Проекция исчезнет; ресурсы останутся в фонде","Чертёж можно использовать снова"),()->{close(p.getUniqueId());p.closeInventory();});
        button(holder,53,Material.CLOCK,"Обновить",List.of("Проверить готовность проекции"),()->menu(p,s));p.openInventory(holder.inventory);
    }
    private void move(Player p,Session s,int x,int y,int z){select(p,resident(p),s.project,s.draft,s.choice.move(x,y,z));menu(p,sessions.get(p.getUniqueId()));}
    private void confirm(Player p,Session s){
        Town t=resident(p);if(sessions.get(p.getUniqueId())!=s||!t.getUUID().equals(s.town)||!s.draft.equals(data.town(s.town).placements().get(s.project)))throw new IllegalArgumentException("Выбор изменился. Откройте чертёж заново.");
        if(!s.projection.complete())throw new IllegalArgumentException("Дождитесь полной проекции");
        var result=builds.upgradeAt(p,definitions.project(s.project),s.choice);
        if(result.status()!=UpgradeResult.Status.CONSTRUCTION_STARTED){
            p.sendMessage("§e"+switch(result.status()){case NOT_MAYOR->"Подтвердить место может мэр или уполномоченный строитель.";case NOT_ENOUGH_MONEY,ECONOMY_ERROR->"Не хватает денег в казне или экономика недоступна.";case BUDGET_UNAVAILABLE->"В бюджете строительства не хватает средств.";case NOT_ENOUGH_RESOURCES->"В фонде не хватает ресурсов.";case CONSTRUCTION_IN_PROGRESS->"Проверьте состояние стройки и возврата в меню здания.";default->result.missingResources().isEmpty()?"Место или условия строительства изменились. Выберите место заново.":String.join("; ",result.missingResources());});p.closeInventory();return;
        }
        var state=data.town(s.town);state.placement(s.project,null);try{data.saveOrThrow();}catch(Exception e){state.placement(s.project,s.draft);plugin.getLogger().warning("Стройка запущена, старый чертёж будет заблокирован площадкой: "+e.getMessage());}
        for(int i=0;i<p.getInventory().getSize();i++)if(matches(p.getInventory().getItem(i),s.draft.id()))p.getInventory().setItem(i,null);
        AuditTrail.record(plugin,"placed",s.draft.id().toString(),"CONSTRUCTION_REFUND","PLACED",AuditTrail.player(p.getUniqueId()),AuditTrail.town(s.town),AuditTrail.town(s.town),"",0,"",s.project+" "+s.choice);
        close(p.getUniqueId());p.closeInventory();p.sendMessage("§aМесто подтверждено. Стройка началась; устанавливайте подсвеченные блоки по слоям.");
    }
    private void button(PlacementMenu h,int slot,Material material,String title,List<String> lore,Runnable action){var item=new ItemStack(material);var m=item.getItemMeta();m.displayName(MenuStyle.nameComponent(MenuStyle.decode(title)));m.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);item.setItemMeta(m);h.inventory.setItem(slot,item);if(action!=null)h.actions.put(slot,action);}
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){synchronized(SuiteState.lock()){
        var item=e.getItem();if(item==null||!item.hasItemMeta()||!item.getItemMeta().getPersistentDataContainer().has(token,PersistentDataType.STRING))return;
        e.setCancelled(true);if(e.getHand()!=EquipmentSlot.HAND||!(e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK))return;
        try{use(e.getPlayer(),item,e.getClickedBlock()==null?null:e.getClickedBlock().getLocation());}catch(RuntimeException ex){e.getPlayer().sendMessage("§e"+ex.getMessage());}
    }}
    @EventHandler public void click(InventoryClickEvent e){synchronized(SuiteState.lock()){
        if(!(e.getView().getTopInventory().getHolder() instanceof PlacementMenu h))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(h.viewer)||h.used||!e.isLeftClick()||e.isShiftClick())return;
        var action=h.actions.get(e.getRawSlot());if(action==null)return;h.used=true;
        SuiteScheduler.entity(plugin,p,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=h.inventory||sessions.get(p.getUniqueId())!=h.session)return;try{resident(p);action.run();}catch(RuntimeException ex){p.closeInventory();p.sendMessage("§e"+ex.getMessage());}});
    }}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof PlacementMenu)e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){synchronized(SuiteState.lock()){close(e.getPlayer().getUniqueId());}}
    @EventHandler public void world(PlayerChangedWorldEvent e){synchronized(SuiteState.lock()){close(e.getPlayer().getUniqueId());}}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){synchronized(SuiteState.lock()){
        if(!(sender instanceof Player p)){sender.sendMessage("Команда доступна в игре");return true;}
        try{Town t=resident(p);var s=sessions.get(p.getUniqueId());if(s==null)throw new IllegalArgumentException("Сначала нажмите чертежом по земле. Получить его можно в меню здания после сдачи ресурсов.");
            String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
            if(action.equals("close")){close(p.getUniqueId());p.sendMessage("§aРазмещение отложено. Ресурсы сохранены.");}
            else if(action.equals("rotate"))select(p,t,s.project,s.draft,s.choice.rotate());else menu(p,s);
        }catch(RuntimeException e){p.sendMessage("§e"+e.getMessage());}return true;
    }}
}

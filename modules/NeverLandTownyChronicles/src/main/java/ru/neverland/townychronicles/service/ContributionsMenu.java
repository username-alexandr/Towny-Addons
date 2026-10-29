package ru.neverland.townychronicles.service;

import java.util.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townychronicles.integration.TownyHook;
import ru.neverland.townychronicles.model.*;
import ru.neverland.townychronicles.chapters.CityChapters;

public final class ContributionsMenu implements Listener,AutoCloseable {
    private final JavaPlugin plugin;private final TownyHook towns;private final ChronicleRepository repository;
    private final ThreadPoolExecutor worker;private final ContributionAudit audit;private final AtomicBoolean reading=new AtomicBoolean();
    private final Map<UUID,String> pendingThanks=new HashMap<>();private final Set<String> pendingAudit=ConcurrentHashMap.newKeySet();
    private volatile Map<UUID,List<CityContributions.Member>> snapshot=Map.of();
    private volatile CityChapters.Evidence chapterEvidence=new CityChapters.Evidence(Map.of(),Set.of());
    private volatile boolean closing;private final CompletableFuture<Void> terminated=new CompletableFuture<>();
    private volatile String error="История загружается";private volatile long refreshed;
    public ContributionsMenu(JavaPlugin plugin,TownyHook towns,ChronicleRepository repository){
        this.plugin=plugin;this.towns=towns;this.repository=repository;audit=new ContributionAudit(plugin);
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"NeverLand-Public-Contributions");t.setDaemon(true);return t;}){@Override protected void terminated(){terminated.complete(null);}};
        Bukkit.getPluginManager().registerEvents(this,plugin);SuiteScheduler.get().runTaskTimer(plugin,this::refresh,1,1200);
    }
    private void refresh(){
        if(closing||!reading.compareAndSet(false,true))return;var directories=new ArrayList<Path>();
        for(String name:List.of("NeverLandTownyBuilds","NeverLandTownyContracts","NeverLandTownySiegesPlus","NeverLandTownyControl",plugin.getName())){
            var p=Bukkit.getPluginManager().getPlugin(name);if(p!=null)directories.add(p.getDataFolder().toPath().resolve("audit"));
        }
        try{worker.execute(()->{
            try{
                var next=ContributionHistory.read(directories,plugin.getName(),r->!r.module().equals(plugin.getName())||!pendingAudit.contains(r.id()));
                if(closing)return;snapshot=next.members();chapterEvidence=next.chapters();refreshed=System.currentTimeMillis();error="";
                if(!closing)SuiteScheduler.get().runTask(plugin,()->{
                    if(closing)return;for(var r:next.thanks())restore(r);repository.requestSave();
                });
            }catch(Exception ex){error="Не удалось обновить историю. Последние сведения сохранены";plugin.getLogger().warning("Вклад жителей: "+ex.getMessage());}
            finally{reading.set(false);}
        });}catch(RejectedExecutionException ex){reading.set(false);if(!closing)error="Обновление истории занято. Последние сведения сохранены";}
    }
    private void restore(AuditRecord r){
        final UUID town,id;try{town=UUID.fromString(r.from().id());id=UUID.fromString(r.id());}catch(IllegalArgumentException ignored){return;}
        var entries=repository.entries(town);if(entries.stream().anyMatch(e->e.id().equals(id)))return;
        int maximum=Math.max(10,plugin.getConfig().getInt("storage.maximum-entries-per-town",1000));
        if(!entries.isEmpty()&&entries.size()>=maximum&&r.at()<entries.get(entries.size()-1).timestamp())return;
        repository.state(town);repository.add(new ChronicleEntry(id,town,r.from().name(),ChronicleCategory.CUSTOM,r.at(),"Благодарность: "+r.to().name(),List.of(r.details()),r.actor().name(),"mayor-commendation"));
    }
    public void thank(Player p,String resident,String text){try(var guard=SuiteState.guard()){
        if(closing||SuiteState.frozen(plugin.getName()))throw new IllegalArgumentException("Летопись временно недоступна");
        if(audit.fault())throw new IllegalArgumentException("Запись благодарностей ожидает проверки администратором. История города сохранена");
        var town=towns.town(p);var r=com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(resident);
        if(town==null||!town.isMayor(towns.resident(p))||!p.hasPermission("townychronicles.thank"))throw new IllegalArgumentException("Благодарности публикует мэр города");
        if(r==null||!town.equals(r.getTownOrNull()))throw new IllegalArgumentException("Этот житель не состоит в вашем городе");
        String plain=text.strip();if(plain.length()<5||plain.length()>160||plain.chars().anyMatch(c->Character.isISOControl(c)||c=='§'||c=='&'))throw new IllegalArgumentException("Благодарность: 5–160 символов без цветов");
        long now=System.currentTimeMillis();UUID townId=town.getUUID();
        if(pendingThanks.containsKey(townId))throw new IllegalArgumentException("Предыдущая благодарность ещё сохраняется");
        if(repository.entries(townId).stream().anyMatch(e->e.source().equals("mayor-commendation")&&e.timestamp()>now-60_000))throw new IllegalArgumentException("Подождите минуту перед следующей благодарностью");
        String op=UUID.randomUUID().toString();var record=new AuditRecord(AuditRecord.id(plugin.getName(),op,"thanks"),now,plugin.getName(),"CITY_COMMENDATION","COMPLETED",op,AuditTrail.player(p.getUniqueId()),AuditTrail.town(townId),AuditTrail.player(r.getUUID()),"",0,"",plain);
        pendingThanks.put(townId,op);pendingAudit.add(record.id());p.sendMessage("§7Сохраняем благодарность…");
        audit.submit(record).whenComplete((saved,failure)->{
            try(var callbackGuard=SuiteState.guard()){
                if(failure==null||failure instanceof ContributionAudit.NotAccepted){pendingThanks.remove(townId,op);pendingAudit.remove(record.id());}
                if(closing||!plugin.isEnabled())return;
                if(failure==null){restore(saved);repository.requestSave();refresh();}
                SuiteScheduler.player(plugin,p,viewer->viewer.sendMessage(failure==null?"§aБлагодарность сохранена в летописи города":"§eБлагодарность не опубликована: сохранение не подтверждено. Повторите позже"));
            }catch(Exception ex){plugin.getLogger().warning("Благодарность сохранена в аудите, восстановление летописи отложено: "+ex.getMessage());}
        });
    }}
    public void open(Player p,int requested){try(var guard=SuiteState.guard()){
        var town=towns.town(p);if(closing||SuiteState.frozen(plugin.getName())||town==null||!p.hasPermission("townychronicles.contributions"))throw new IllegalArgumentException("Раздел вклада доступен жителям города");
        List<CityContributions.Member> list=snapshot.getOrDefault(town.getUUID(),List.of());int pages=Math.max(1,(list.size()+44)/45),page=Math.max(0,Math.min(requested,pages-1));View v=new View(p.getUniqueId(),town.getUUID(),page);v.inventory=MenuStyle.inventory(plugin,v,54,"Вклад жителей • "+town.getName());
        for(int i=page*45;i<Math.min(list.size(),(page+1)*45);i++){
            var m=list.get(i);v.inventory.setItem(i%45,item(Material.PLAYER_HEAD,m.name(),List.of("Материалы из личного инвентаря: "+m.materials()+" шт.","Личные взносы в казну: "+m.deposits().toPlainString(),"Организовано ремонтов: "+m.repairs(),"На ремонт потрачено из казны: "+m.repairBudget().toPlainString(),"Победы над нападавшими: "+m.defense(),"Благодарности мэра: "+m.thanks())));
        }
        if(list.isEmpty())v.inventory.setItem(22,item(Material.BOOK,error.isEmpty()?"Записей пока нет":"История временно недоступна",List.of(error.isEmpty()?"Здесь появятся взносы, ремонты и защита города":error)));
        if(page>0)v.inventory.setItem(45,item(Material.ARROW,"Предыдущая страница",List.of()));if(page+1<pages)v.inventory.setItem(53,item(Material.ARROW,"Следующая страница",List.of()));
        v.inventory.setItem(49,item(Material.ARROW,"Назад в летопись",List.of("Обновление раз в минуту",error.isEmpty()?"Последнее обновление: "+java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(java.time.ZoneId.of("Europe/Moscow")).format(java.time.Instant.ofEpochMilli(refreshed))+" МСК":error,"Мэр: /t chronicles thank <житель> <текст>")));p.openInventory(v.inventory);
    }}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(v.owner)||e.getClick()!=ClickType.LEFT||!Set.of(45,49,53).contains(e.getRawSlot()))return;
        try(var guard=SuiteState.guard()){
            if(v.closed||v.busy||closing||p.getOpenInventory().getTopInventory()!=v.inventory)return;v.busy=true;int slot=e.getRawSlot();
            SuiteScheduler.player(plugin,p,x->{try(var callbackGuard=SuiteState.guard()){
                try{
                    if(v.closed||closing||p.getOpenInventory().getTopInventory()!=v.inventory)return;
                    var town=towns.town(p);if(town==null||!town.getUUID().equals(v.town)||!p.hasPermission("townychronicles.contributions")||SuiteState.frozen(plugin.getName())){p.closeInventory();return;}
                    if(slot==49)p.performCommand("chronicles");else if(slot==45)open(p,v.page-1);else open(p,v.page+1);
                }catch(Exception ex){p.sendMessage("§e"+ex.getMessage());}finally{v.busy=false;}
            }});
        }
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    @EventHandler public void closeView(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof View v){try(var guard=SuiteState.guard()){v.closed=true;}}}
    @SuppressWarnings("unchecked") @EventHandler(priority=EventPriority.MONITOR)public void defense(PlayerDeathEvent e){try(var guard=SuiteState.guard()){
        if(closing||audit.fault()||SuiteState.frozen(plugin.getName())||!Bukkit.getPluginManager().isPluginEnabled("NeverLandTownySiegesPlus"))return;Player killer=e.getEntity().getKiller();if(killer==null)return;
        UUID victim=e.getEntity().getUniqueId(),actor=killer.getUniqueId();var own=towns.townOf(actor);var enemy=towns.townOf(victim);var at=com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(e.getEntity().getLocation());if(own==null||enemy==null||at==null||!at.equals(own))return;
        var wars=(Collection<Map<String,Object>>)ApiServices.call("NeverLandTownySiegesPlus","ru.neverland.townysieges.api.TownySiegesApi","raids",new Class<?>[]{});
        for(var war:wars)if(own.getUUID().equals(war.get("defender"))&&enemy.getUUID().equals(war.get("attacker"))&&Boolean.TRUE.equals(war.get("fighting"))){
            String op=war.get("id")+":"+victim+":"+actor;var record=new AuditRecord(AuditRecord.id(plugin.getName(),op,"defense"),System.currentTimeMillis(),plugin.getName(),"CITY_DEFENSE","COMPLETED",op,AuditTrail.player(actor),AuditTrail.town(own.getUUID()),AuditTrail.town(enemy.getUUID()),"",1,"","Отражение нападения в своём городе; одна запись на противника за рейд");
            if(!pendingAudit.add(record.id()))break;audit.submit(record).whenComplete((receipt,failure)->{if(failure==null||failure instanceof ContributionAudit.NotAccepted)pendingAudit.remove(record.id());});break;
        }
    }catch(Exception ignored){/* Missing war evidence never invents a defense achievement. */}}
    private static ItemStack item(Material type,String name,List<String> lore){var item=new ItemStack(type);var meta=item.getItemMeta();meta.displayName(MenuStyle.nameComponent(MenuStyle.decode(name)));meta.lore(MenuStyle.loreComponents(lore.stream().map(MenuStyle::decode).toList()));meta.addItemFlags(ItemFlag.values());item.setItemMeta(meta);return item;}
    private static final class View implements InventoryHolder{final UUID owner,town;final int page;Inventory inventory;boolean busy,closed;View(UUID owner,UUID town,int page){this.owner=owner;this.town=town;this.page=page;}public Inventory getInventory(){return inventory;}}
    public CityChapters.Evidence chapterEvidence(){return chapterEvidence;}
    public String historyIssue(){return error;}
    public long historyRefreshed(){return refreshed;}
    public CompletionStage<Void> prepareClose(){closing=true;worker.shutdown();return CompletableFuture.allOf(terminated,audit.prepareClose().toCompletableFuture());}
    public void close(){var done=prepareClose().toCompletableFuture();if(SuiteState.held()&&!done.isDone())throw new IllegalStateException("Дождитесь записи летописи вне общей блокировки");try{done.get(35,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}catch(ExecutionException|TimeoutException ex){throw new IllegalStateException("Завершение летописи ещё не подтверждено",ex);}}
}

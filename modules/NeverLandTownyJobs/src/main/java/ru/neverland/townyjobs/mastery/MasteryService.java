package ru.neverland.townyjobs.mastery;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.core.*;
import ru.neverland.townyjobs.*;
import static ru.neverland.townyjobs.mastery.MasteryModel.*;

public final class MasteryService implements Listener {
    private final JavaPlugin plugin;private final JobsService jobs;private final MasterySettings settings;private final MasteryStore store;private boolean closing,proofsAvailable=true;
    private static final class View implements InventoryHolder{UUID player,town;int page;Inventory inventory;Map<Integer,Runnable> actions=new HashMap<>();public Inventory getInventory(){return inventory;}}
    public MasteryService(JavaPlugin plugin,JobsService jobs)throws Exception{
        this.plugin=plugin;this.jobs=jobs;var file=plugin.getDataFolder().toPath().resolve("mastery.yml");if(!java.nio.file.Files.exists(file))plugin.saveResource("mastery.yml",false);settings=MasterySettings.load(SafeYaml.load(file),jobs.settings());store=new MasteryStore(file.resolveSibling("mastery.db"));
        store.ready().whenComplete((v,error)->{if(error!=null)plugin.getLogger().log(java.util.logging.Level.SEVERE,"Мастерские заказы приостановлены; профессии и зарплаты продолжают работать",error);});plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    public boolean ready(){return proofsAvailable&&settings.enabled()&&!closing&&store.healthy()&&!SuiteState.frozen(plugin.getName());}
    public MasteryStore store(){return store;}
    public void setProofsAvailable(boolean value){proofsAvailable=value;}
    public List<String> options(UUID player,boolean unlocked){var role=jobs.career(player).profession();var definitions=unlocked?store.get(player).unlocked():settings.definitions();return definitions.values().stream().filter(d->d.profession()==role).map(Definition::id).sorted().toList();}
    private UUID user(Player p){
        if(!ready())throw new IllegalStateException("Мастерские заказы пока недоступны. Опыт и текущий заказ сохраняются.");
        if(!p.hasPermission("neverlandtownyjobs.use")||!p.hasPermission("neverlandtownyjobs.mastery")||!p.hasPermission("neverlandtownyjobs.work")||!p.hasPermission("neverlandtownyjobs.earn"))throw new IllegalArgumentException("Нет доступа к мастерским заказам и работе");
        var town=jobs.town(p);if(town==null)throw new IllegalArgumentException("Для мастерского заказа вступите в город");return town.getUUID();
    }
    public CompletionStage<Worker> start(Player p,String id){UUID town=user(p);var definition=settings.definitions().get(id);var career=jobs.career(p.getUniqueId());if(definition==null||definition.profession()!=career.profession())throw new IllegalArgumentException("Выберите направление своей профессии");if(!jobs.settings().profiles().get(career.profession()).enabled()||jobs.settings().level(career)<definition.minimumLevel())throw new IllegalArgumentException("Для этого заказа нужен уровень профессии "+definition.minimumLevel());return store.start(p.getUniqueId(),UUID.randomUUID(),town,definition,System.currentTimeMillis());}
    public CompletionStage<Worker> choose(Player p,String id){user(p);var profession=jobs.career(p.getUniqueId()).profession();if(profession==null)throw new IllegalArgumentException("Сначала выберите профессию");return store.choose(p.getUniqueId(),profession,id);}
    public CompletionStage<Worker> cancel(Player p,UUID expected){user(p);return store.cancel(p.getUniqueId(),expected);}
    public void record(Player p,Profession profession,Action action,String target,int amount,UUID proof){
        if(!ready()||!p.hasPermission("neverlandtownyjobs.mastery"))return;var career=jobs.career(p.getUniqueId());if(career.profession()!=profession||career.town()==null||!jobs.onDuty(p.getUniqueId())||!jobs.status(p.getUniqueId()).equals("На смене"))return;
        var worker=store.get(p.getUniqueId());if(worker.active()==null)return;
        store.action(new Proof(proof,p.getUniqueId(),career.town(),worker.active().id(),profession,action,target,Math.min(4096,amount),System.currentTimeMillis())).whenComplete((changed,error)->{if(error!=null)plugin.getLogger().warning("Этап мастерского заказа не подтверждён: "+error.getMessage());else if(Boolean.TRUE.equals(changed)&&store.get(p.getUniqueId()).active()==null&&store.get(p.getUniqueId()).unlocked().containsKey(worker.active().definition().id()))SuiteScheduler.entity(plugin,p,()->{if(p.isOnline())JobsMenus.message(p,"&aМастерский заказ выполнен! Выберите открытое направление в /t jobs mastery.");});});
    }
    public double bonus(UUID player,Profession profession,String project){return ready()?store.get(player).bonus(profession,project):0;}
    public Map<String,Object> summary(UUID player){
        if(!ready())return Map.of("available",false,"reason","Мастерские заказы приостановлены или загружаются");var worker=store.get(player);var value=new LinkedHashMap<String,Object>();value.put("available",true);var chosen=new LinkedHashMap<String,String>();worker.chosen().forEach((role,id)->chosen.put(role.id(),worker.unlocked().get(id).title()));value.put("specialties",Map.copyOf(chosen));
        var order=worker.active();if(order!=null){var step=order.definition().steps().get(order.stage());value.put("order",Map.of("id",order.id().toString(),"town",order.town().toString(),"title",order.definition().title(),"profession",order.definition().profession().id(),"stage",order.stage()+1,"stages",order.definition().steps().size(),"progress",order.progress(),"required",step.amount(),"text",step.text()));}else value.put("order",Map.of());return Collections.unmodifiableMap(value);
    }
    public CompletionStage<Void> prepareClose(){closing=true;store.close();return store.closed();}
    public void awaitClosed(){var done=prepareClose().toCompletableFuture();if(SuiteState.held()&&!done.isDone())throw new IllegalStateException("Сначала завершите мастерские заказы вне общей блокировки");try{done.get(10,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}catch(ExecutionException|TimeoutException error){throw new IllegalStateException("Мастерские заказы ещё сохраняются",error);}}
    public void command(Player p,String[] args){
        if(args.length<=1){open(p,0);return;}CompletionStage<Worker> future;
        switch(args[1].toLowerCase(Locale.ROOT)){
            case "start"->{if(args.length!=3)throw new IllegalArgumentException("/t jobs mastery start <направление>");future=start(p,args[2]);}
            case "choose"->{if(args.length!=3)throw new IllegalArgumentException("/t jobs mastery choose <направление>");future=choose(p,args[2]);}
            case "cancel"->{if(args.length!=3||!args[2].equals("confirm"))throw new IllegalArgumentException("/t jobs mastery cancel confirm — отменить без штрафа");var order=store.get(p.getUniqueId()).active();if(order==null)throw new IllegalArgumentException("Нет незавершённого мастерского заказа");future=cancel(p,order.id());}
            default->throw new IllegalArgumentException("/t jobs mastery [start|choose|cancel]");
        }acknowledge(p,future,null);
    }
    private void acknowledge(Player p,CompletionStage<Worker> future,View expected){future.whenComplete((result,error)->SuiteScheduler.entity(plugin,p,()->{if(!p.isOnline())return;if(error!=null){Throwable reason=error;while(reason instanceof CompletionException&&reason.getCause()!=null)reason=reason.getCause();JobsMenus.message(p,"&cИзменение не принято: "+reason.getMessage());return;}JobsMenus.message(p,"&aСохранено. Открытые направления и весь опыт сохраняются.");if(expected==null||p.getOpenInventory().getTopInventory().getHolder()==expected)open(p,expected==null?0:expected.page);}));}
    public void open(Player p,int requested){try(var guard=SuiteState.guard()){
        UUID town=user(p);var career=jobs.career(p.getUniqueId());if(career.profession()==null)throw new IllegalArgumentException("Сначала выберите профессию в /t jobs");var worker=store.get(p.getUniqueId());
        var definitions=new TreeMap<String,Definition>();settings.definitions().forEach((id,d)->{if(d.profession()==career.profession())definitions.put(id,d);});worker.unlocked().forEach((id,d)->{if(d.profession()==career.profession())definitions.put(id,d);});var rows=List.copyOf(definitions.values());int page=Math.max(0,Math.min(Math.max(0,(rows.size()-1)/36),requested));
        var v=new View();v.player=p.getUniqueId();v.town=town;v.page=page;v.inventory=MenuStyle.inventory(plugin,v,54,"Мастерство профессии");
        for(int n=page*36;n<Math.min(rows.size(),(page+1)*36);n++){
            var definition=rows.get(n);boolean unlocked=worker.unlocked().containsKey(definition.id()),selected=definition.id().equals(worker.chosen().get(career.profession()));var lore=new ArrayList<String>();lore.add(selected?"&aВыбрано: это направление усиливает ваши рабочие здания":unlocked?"&aОткрыто: нажмите, чтобы выбрать":"&eНажмите, чтобы принять мастерский заказ");lore.add("&7Требуемый уровень профессии: "+definition.minimumLevel());int index=0;for(var step:definition.steps())lore.add("&f"+(++index)+". "+step.text());lore.add("&7Бонус: +"+JobsMenus.percent(definition.bonus())+"% в профильных зданиях");definition.buildings().stream().sorted().forEach(id->lore.add("&7• "+jobs.settings().building(id)));lore.add("&7Общий предел бонуса зданий сохраняется");lore.add("&7Действует только одно направление каждой профессии");
            button(v,n%36,Material.valueOf(definition.icon()),definition.title(),lore,()->acknowledge(p,unlocked?choose(p,definition.id()):start(p,definition.id()),v));
        }
        var order=worker.active();var lines=new ArrayList<String>();if(order==null)lines.add("&7Выберите направление выше и выполните его этапы");else{
            var step=order.definition().steps().get(order.stage());lines.add("&f"+order.definition().title());lines.add("&7Этап "+(order.stage()+1)+" из "+order.definition().steps().size());lines.add("&f"+step.text());lines.add("&eГотово: "+order.progress()+" / "+step.amount());if(!order.town().equals(town)||order.definition().profession()!=career.profession())lines.add("&eПродолжение — в исходном городе и выбранной профессии");
            button(v,45,Material.BARRIER,"Отменить мастерский заказ",List.of("&7Без штрафа и потери опыта","&7Открытые направления останутся","&eНажмите для отдельного подтверждения"),()->confirmCancel(p,v,order));
        }
        lines.add("&7Засчитывается только результат работы в активную смену");lines.add("&7Выполняйте этапы по порядку. Ожидание на месте не помогает");lines.add("&7У заказа нет срока. Рестарт не сбрасывает выполненное");button(v,40,Material.WRITABLE_BOOK,"Текущий мастерский заказ",lines,null);
        if(page>0)button(v,46,Material.ARROW,"Предыдущая страница",List.of(),()->open(p,page-1));if((page+1)*36<rows.size())button(v,53,Material.ARROW,"Следующая страница",List.of(),()->open(p,page+1));button(v,49,Material.CRAFTING_TABLE,"К профессиям",List.of(),()->p.performCommand("townyjobs"));p.openInventory(v.inventory);
    }}
    private void confirmCancel(Player p,View v,Order order){v.actions.clear();v.inventory.clear();button(v,13,Material.PAPER,order.definition().title(),List.of("&7Прогресс этого заказа будет отменён","&7Опыт и открытые направления сохранятся","&7Деньги и ресурсы не списываются"),null);button(v,30,Material.RED_DYE,"Подтвердить отмену",List.of(),()->acknowledge(p,cancel(p,order.id()),v));button(v,32,Material.LIME_DYE,"Продолжить работу",List.of(),()->open(p,v.page));p.openInventory(v.inventory);}
    private void button(View v,int slot,Material icon,String title,List<String> lore,Runnable action){var item=new ItemStack(icon);var meta=item.getItemMeta();meta.setDisplayName(MenuStyle.nameLegacy(JobsMenus.color("&f"+title)));meta.setLore(MenuStyle.loreStrings(lore.stream().map(JobsMenus::color).toList()));meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);item.setItemMeta(meta);v.inventory.setItem(slot,item);if(action!=null)v.actions.put(slot,action);}
    @EventHandler public void click(InventoryClickEvent e){try(var guard=SuiteState.guard()){if(!(e.getView().getTopInventory().getHolder() instanceof View v))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(v.player)||!ready())return;var action=v.actions.get(e.getRawSlot());if(action==null)return;SuiteScheduler.entity(plugin,p,()->{try{if(p.isOnline()&&p.getOpenInventory().getTopInventory().getHolder()==v&&user(p).equals(v.town))action.run();}catch(Exception error){JobsMenus.message(p,"&c"+error.getMessage());}});}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
}

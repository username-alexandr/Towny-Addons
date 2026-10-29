package ru.neverland.townysieges;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.ServicePriority;
import com.palmergames.bukkit.towny.TownyAPI;
import ru.neverland.core.*;
import ru.neverland.townysieges.api.TownyRaidRepairApi;

/** Shared building integrity, explicit town-funded confirmation, resumable world work. */
public final class RaidRepairs implements TownyRaidRepairApi,Listener {
    public record Quote(RepairPlans.Plan plan,List<RaidRepairsStore.Cell> cells,double maximum,double lost,long cents,String signature){public Quote{cells=List.copyOf(cells);}}
    private static final class View implements InventoryHolder {
        final UUID owner;final Quote quote;final Inventory previous;final long at=System.currentTimeMillis();Inventory inventory;boolean busy;
        View(Player p,Quote q,Inventory previous){owner=p.getUniqueId();quote=q;this.previous=previous;}
        public Inventory getInventory(){return inventory;}
    }
    private final NeverLandTownySiegesPlus plugin;private final RaidController raids;private final RepairPlans plans=new RepairPlans();
    private final BuildingDamageStore damage;private final RaidRepairsStore store;private RepairBank bank=new RepairBank();
    private final Set<UUID> funding=ConcurrentHashMap.newKeySet();private final Set<UUID> working=new HashSet<>();private final Map<UUID,Long> started=new HashMap<>(),busyUntil=new HashMap<>();private long polling,fundingWarning;
    public RaidRepairs(NeverLandTownySiegesPlus plugin,RaidController raids){this.plugin=plugin;this.raids=raids;damage=new BuildingDamageStore(raids.store());store=new RaidRepairsStore(raids.store());Bukkit.getServicesManager().register(TownyRaidRepairApi.class,this,plugin,ServicePriority.Normal);Bukkit.getPluginManager().registerEvents(this,plugin);}
    public RaidRepairsStore store(){return store;}
    @Override public CompletionStage<Boolean> hasRepairDebt(UUID town,String project){if(!active())return CompletableFuture.failedFuture(new IllegalStateException("Учёт повреждений временно недоступен"));return raids.store().hasRepairDebt(town,project);}
    @Override public long repairDebtRevision(){if(!active())throw new IllegalStateException("Учёт повреждений временно недоступен");return raids.store().repairDebtRevision();}
    public boolean busy(UUID town){return System.currentTimeMillis()<busyUntil.getOrDefault(town,0L);}
    private boolean active(){return plugin.isEnabled()&&!Bukkit.isStopping()&&!SuiteState.frozen(plugin.getName())&&raids.store().healthy();}
    private void global(Runnable action){if(active())SuiteScheduler.get().runTask(plugin,action);}
    private void release(UUID id){working.remove(id);started.remove(id);}
    private <T>void after(CompletionStage<T> future,Consumer<T> next,UUID operation){future.whenComplete((v,e)->global(()->{if(e!=null){failed(operation,e);}else try{next.accept(v);}catch(Exception error){failed(operation,error);}}));}
    private void failed(UUID operation,Throwable error){
        plugin.getLogger().warning("Ремонт сохранён, ожидается проверка: "+RaidController.problem(error));if(operation==null)return;release(operation);
        store.get(operation).whenComplete((job,readError)->global(()->{if(readError==null&&job!=null&&job.state().equals("PAID"))review(job,RaidController.problem(error));}));
    }
    private static Block block(World world,RaidStore.Key k){return world.getBlockAt(k.x(),k.y(),k.z());}
    private static boolean match(Block b,String material){return b.getType().name().equals(material);}
    private void checkTerritory(UUID town,Block b){var city=TownyAPI.getInstance().getTown(b.getLocation());if(city==null||!city.getUUID().equals(town))throw new IllegalArgumentException("Место здания больше не принадлежит городу");}
    public CompletionStage<Quote> quote(UUID town,String project)throws Exception {
        if(!active())throw new IllegalArgumentException("Ремонт временно недоступен");var plan=plans.plan(town,project);if(plan.active())throw new IllegalArgumentException("Сначала завершите текущее строительство");
        double maximum=plan.cells().stream().mapToDouble(c->raids.settings().hp(c.hpMaterial())).sum();if(maximum<=0)throw new IllegalArgumentException("В здании нет ремонтируемых блоков");
        var result=new CompletableFuture<Quote>();damage.list(town,project).whenComplete((rows,error)->{if(error!=null){result.completeExceptionally(error);return;}
            global(()->{var expected=new HashMap<String,RepairPlans.Cell>();for(var c:plan.cells())expected.put(c.key().id(),c);
                var groups=new LinkedHashMap<String,List<BuildingDamageStore.Damage>>();for(var d:rows)if(expected.containsKey(d.key().id()))groups.computeIfAbsent((d.key().x()>>4)+":"+(d.key().z()>>4),k->new ArrayList<>()).add(d);
                var output=new ArrayList<RaidRepairsStore.Cell>();var tasks=new ArrayList<CompletableFuture<Void>>();World world=Bukkit.getWorld(plan.world());if(world==null){result.completeExceptionally(new IllegalArgumentException("Мир здания сейчас недоступен"));return;}
                for(var group:groups.values()){
                    var f=new CompletableFuture<Void>();tasks.add(f);var key=group.get(0).key();SuiteScheduler.region(plugin,new Location(world,key.x(),key.y(),key.z()),()->{try{
                        if(!world.isChunkLoaded(key.x()>>4,key.z()>>4))throw new IllegalArgumentException("Подойдите к зданию, чтобы осмотреть повреждения");
                        for(var d:group){var c=expected.get(d.key().id());if(!c.material().equals(d.material()))throw new IllegalArgumentException("Чертёж здания изменился; нужна проверка повреждений");if(!d.pending().isEmpty())throw new IllegalArgumentException("Дождитесь окончания обработки взрыва");
                            Block b=block(world,c.key());checkTerritory(town,b);if(!b.getType().isAir()&&!match(b,c.material()))throw new IllegalArgumentException("На месте ремонта другой блок: "+c.key().x()+", "+c.key().y()+", "+c.key().z());
                            if(!c.top().isEmpty()){var top=b.getRelative(0,1,0);if(!top.getType().isAir()&&!match(top,c.material()))throw new IllegalArgumentException("Освободите верхнюю половину двери");}
                            boolean missing=b.getType().isAir()||!c.top().isEmpty()&&b.getRelative(0,1,0).getType().isAir();
                            if(missing||d.remaining()<d.maximum())output.add(new RaidRepairsStore.Cell(c,d.version(),d.remaining(),d.maximum(),missing));
                        }f.complete(null);
                    }catch(Throwable e){f.completeExceptionally(e);}});
                }
                CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).whenComplete((v,e)->{if(e!=null){result.completeExceptionally(e);return;}global(()->{try{
                    output.sort(Comparator.comparing(c->c.block().key().id()));double lost=output.stream().mapToDouble(c->raids.settings().hp(c.block().hpMaterial())*(c.missing()?1:1-Math.max(0,Math.min(1,c.remaining()/c.maximum())))).sum();
                    long cents=RepairRules.cost(plan.price(),lost,maximum);String signature=UUID.nameUUIDFromBytes((plan.fingerprint()+":"+output).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();result.complete(new Quote(plan,output,maximum,lost,cents,signature));
                }catch(Throwable ex){result.completeExceptionally(ex);}});});
            });
        });return result.orTimeout(20,TimeUnit.SECONDS);
    }
    @Override public void open(Player player,String project){try(var g=SuiteState.guard()){
        var town=SiegeService.town(player.getUniqueId());if(town==null)throw new IllegalArgumentException("Сначала вступите в город");var previous=player.getOpenInventory().getTopInventory();UUID city=town.getUUID();
        try{quote(city,project).whenComplete((q,error)->SuiteScheduler.player(plugin,player,p->{if(p.getOpenInventory().getTopInventory()!=previous)return;if(error!=null){raids.reply(p,RaidController.problem(error));return;}try{show(p,q,previous);}catch(Exception e){raids.reply(p,RaidController.problem(e));}}));}catch(Exception error){throw new IllegalArgumentException(RaidController.problem(error),error);}
    }}
    private void show(Player player,Quote quote,Inventory previous)throws Exception{
        var own=SiegeService.town(player.getUniqueId());if(own==null||!own.getUUID().equals(quote.plan().town()))throw new IllegalArgumentException("Город игрока изменился");
        boolean allowed=plans.canRepair(player,own.getUUID()),economy=quote.cents()==0||bank.ready(own.getUUID(),own.getUUID());long balance=economy&&quote.cents()>0?bank.balance(own.getUUID()):0;
        var h=new View(player,quote,previous);h.inventory=MenuStyle.inventory(plugin,h,27,"Ремонт • "+quote.plan().title());var lore=new ArrayList<String>();
        lore.add("&fУровень здания: "+quote.plan().level());lore.add("&fПрочность: "+String.format(java.util.Locale.ROOT,"%.2f",(quote.maximum()-quote.lost())/quote.maximum()*100)+"%");lore.add("&fПовреждено: "+String.format(java.util.Locale.ROOT,"%.2f",quote.lost()/quote.maximum()*100)+"%");lore.add("&fЦена уровня: "+quote.plan().price()/100.0+" монет");lore.add("&fРемонт из казны: "+quote.cents()/100.0+" монет");lore.add("&7Доля повреждений × цена текущего уровня.");
        h.inventory.setItem(11,SiegeMenu.item(Material.ANVIL,"&eСостояние здания",lore));
        String reason=!allowed?"Ремонт подтверждает мэр или уполномоченный строитель":raids.managed(own.getUUID())?"Дождитесь окончания рейда":quote.lost()==0?"Здание не нуждается в ремонте":!economy?"Экономика города временно недоступна":quote.cents()>balance?"В казне не хватает "+(quote.cents()-balance)/100.0+" монет":quote.cents()>0&&!bank.allowed(own.getUUID(),quote.cents())?"Бюджет города не разрешает этот расход":"";
        h.inventory.setItem(15,SiegeMenu.item(reason.isEmpty()?Material.LIME_CONCRETE:Material.GRAY_CONCRETE,reason.isEmpty()?"&aОплатить ремонт":"&7Ремонт недоступен",reason.isEmpty()?List.of("&fИз казны будет списано "+quote.cents()/100.0+" монет.","&fРесурсы со склада не требуются.","&fНажмите, чтобы подтвердить."):List.of("&e"+reason)));
        h.inventory.setItem(22,SiegeMenu.item(Material.ARROW,"&eНазад",List.of("Вернуться к зданию")));player.openInventory(h.inventory);
    }
    @EventHandler public void click(InventoryClickEvent e){try(var g=SuiteState.guard()){if(!(e.getView().getTopInventory().getHolder() instanceof View h))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(h.owner)||h.busy||!active())return;
        if(e.getRawSlot()==22){MenuStyle.returnTo(p,h.previous);return;}if(e.getRawSlot()!=15)return;
        try{if(System.currentTimeMillis()-h.at>60000)throw new IllegalArgumentException("Расчёт устарел. Откройте ремонт заново");if(!plans.canRepair(p,h.quote.plan().town()))throw new IllegalArgumentException("Нет права ремонта");if(raids.managed(h.quote.plan().town()))throw new IllegalArgumentException("Дождитесь окончания рейда");if(h.quote.lost()==0)throw new IllegalArgumentException("Ремонт не требуется");h.busy=true;
            quote(h.quote.plan().town(),h.quote.plan().project()).whenComplete((fresh,error)->SuiteScheduler.player(plugin,p,actor->{try{
                if(actor.getOpenInventory().getTopInventory()!=h.inventory)return;if(error!=null)throw new IllegalArgumentException(RaidController.problem(error));if(!fresh.signature().equals(h.quote.signature())||fresh.cents()!=h.quote.cents())throw new IllegalArgumentException("Повреждения или цена изменились. Откройте ремонт заново");start(actor,fresh);actor.closeInventory();
            }catch(Exception ex){h.busy=false;raids.reply(actor,RaidController.problem(ex));}}));
        }catch(Exception error){h.busy=false;raids.reply(p,RaidController.problem(error));}
    }}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof View)e.setCancelled(true);}
    public void start(Player player,Quote q)throws Exception{
        if(!active()||!plans.canRepair(player,q.plan().town()))throw new IllegalArgumentException("Нет права ремонта");if(raids.managed(q.plan().town()))throw new IllegalArgumentException("Дождитесь окончания рейда");
        var current=plans.plan(q.plan().town(),q.plan().project());if(!current.fingerprint().equals(q.plan().fingerprint()))throw new IllegalArgumentException("Условия здания изменились");
        if(q.cents()>0&&!bank.allowed(q.plan().town(),q.cents()))throw new IllegalArgumentException("В казне недостаточно денег или расход запрещён бюджетом");
        long before=q.cents()==0?0:bank.balance(q.plan().town());busyUntil.put(q.plan().town(),System.currentTimeMillis()+15000);
        store.create(player.getUniqueId(),q.plan(),q.maximum(),q.lost(),q.cents(),before,q.cells(),bank.asynchronous()).whenComplete((job,error)->{
            if(error!=null){raids.reply(player,RaidController.problem(error));return;}
            if(job.cents()==0){global(()->{log(job);drive(job);});return;}
            if(job.treasury()){global(()->{log(job);fund(job);});raids.reply(player,"Оплата ремонта сохранена. Восстановление начнётся после подтверждения казны.");return;}
            SuiteScheduler.entityLater(plugin,player,()->pay(player,job),()->store.change(job.id(),"PAYING","CANCELLED","Игрок вышел до списания"),1);
        });
    }
    private void pay(Player player,RaidRepairsStore.Job job){try{
        if(!plans.canRepair(player,job.town())||raids.managed(job.town())||!plans.plan(job.town(),job.project()).fingerprint().equals(job.fingerprint())||!bank.allowed(job.town(),job.cents())){after(store.change(job.id(),"PAYING","CANCELLED","Условия оплаты изменились; списание не выполнялось"),this::log,job.id());raids.reply(player,"Ремонт не оплачен: проверьте казну, права и состояние города");return;}
        boolean confirmed=false;String note="Банк не подтвердил списание; нужна сверка";try{confirmed=bank.withdraw(job);}catch(Exception error){note=RaidController.problem(error);}
        after(store.change(job.id(),"PAYING",confirmed?"PAID":"REVIEW_BANK",confirmed?"Списание подтверждено квитанцией Towny":note),saved->{log(saved);if(saved.state().equals("PAID")){raids.reply(player,"Ремонт оплачен из казны: "+saved.cents()/100.0+" монет");drive(saved);}else raids.reply(player,"Результат оплаты сохранён и ожидает проверки администратора");},job.id());
    }catch(Exception error){after(store.change(job.id(),"PAYING","REVIEW_BANK",RaidController.problem(error)),this::log,job.id());}}
    public void pulse(){if(!active()){working.clear();return;}long now=System.currentTimeMillis();working.removeIf(id->now-started.getOrDefault(id,0L)>30000);if(now-polling<3000)return;polling=now;
        after(store.list(true),jobs->{polling=0;for(var job:jobs){if(job.state().equals("TREASURY_PENDING")||job.state().equals("TREASURY_REVIEW"))fund(job);else if(job.state().startsWith("REVIEW"))log(job);else if(job.state().equals("PAYING")&&now-job.created()>30000)after(store.change(job.id(),"PAYING","REVIEW_BANK","Прервано ожидание оплаты; нужна сверка"),this::log,job.id());else if(job.state().equals("PAID"))drive(job);}},null);
    }
    private void fund(RaidRepairsStore.Job job){
        if(!active()||!job.treasury()||!Set.of("TREASURY_PENDING","TREASURY_REVIEW").contains(job.state())||!funding.add(job.id()))return;
        try{
            // Read an existing bank receipt even if the building changed after payment.
            boolean submit=false;
            try{submit=!raids.managed(job.town())&&plans.plan(job.town(),job.project()).fingerprint().equals(job.fingerprint());}catch(Exception unavailable){}
            var outcome=bank.poll(job,submit);
            if(outcome==RepairTreasury.Outcome.WAITING){funding.remove(job.id());return;}
            String next=switch(outcome){case PAID->"PAID";case REJECTED->"CANCELLED";case REVIEW->"TREASURY_REVIEW";default->throw new IllegalStateException();};
            if(job.state().equals(next)){funding.remove(job.id());return;}
            store.funding(job.id(),outcome).whenComplete((saved,error)->{funding.remove(job.id());global(()->{
                // A missing local ACK is retried by receipt polling, never reclassified as world damage.
                if(error!=null){fundingWarning(job.id(),error);return;}log(saved);if(saved.state().equals("PAID"))drive(saved);
            });});
        }catch(Exception error){funding.remove(job.id());fundingWarning(job.id(),error);}
    }
    private void fundingWarning(UUID id,Throwable error){if(System.currentTimeMillis()-fundingWarning>60000){fundingWarning=System.currentTimeMillis();plugin.getLogger().warning("Оплата ремонта "+id+" ожидает подтверждения: "+RaidController.problem(error));}}
    private void drive(RaidRepairsStore.Job job){if(!active()||raids.managed(job.town())||!working.add(job.id()))return;started.put(job.id(),System.currentTimeMillis());
        try{if(!plans.plan(job.town(),job.project()).fingerprint().equals(job.fingerprint())){review(job,"Чертёж или уровень здания изменился");return;}
            after(store.steps(job.id()),steps->{var pending=job.cells().stream().filter(c->!"DONE".equals(steps.get(c.block().key().id()))).toList();if(pending.isEmpty()){after(store.change(job.id(),"PAID","DONE","Здание восстановлено"),done->{log(done);release(done.id());busyUntil.remove(done.town());raids.notifyTown(done.town(),"Отремонтировано: "+done.title()+". Из казны: "+done.cents()/100.0+" монет.");},job.id());return;}
                var first=pending.get(0).block().key();var group=pending.stream().filter(c->c.block().key().world().equals(first.world())&&c.block().key().x()>>4==first.x()>>4&&c.block().key().z()>>4==first.z()>>4).limit(64).toList();var world=Bukkit.getWorld(first.world());if(world==null||!world.isChunkLoaded(first.x()>>4,first.z()>>4)){release(job.id());return;}
                var resumed=group.stream().anyMatch(c->"APPLYING".equals(steps.get(c.block().key().id())));
                if(resumed){apply(job,group,world,true);return;}
                after(store.begin(job.id(),group),v->apply(job,group,world,false),job.id());
            },job.id());
        }catch(Exception error){review(job,RaidController.problem(error));}
    }
    private void apply(RaidRepairsStore.Job job,List<RaidRepairsStore.Cell> cells,World world,boolean resumed){var first=cells.get(0).block().key();SuiteScheduler.region(plugin,new Location(world,first.x(),first.y(),first.z()),()->{
        try{if(!active()||raids.managed(job.town())){global(()->release(job.id()));return;}if(!world.isChunkLoaded(first.x()>>4,first.z()>>4)){global(()->release(job.id()));return;}busyUntil.put(job.town(),System.currentTimeMillis()+15000);
            if(!plans.plan(job.town(),job.project()).fingerprint().equals(job.fingerprint()))throw new IllegalArgumentException("Здание изменилось после оплаты");
            for(var c:cells){var b=block(world,c.block().key());checkTerritory(job.town(),b);if(!match(b,c.block().material())&&(!b.getType().isAir()||resumed))throw new IllegalArgumentException("Нужна проверка места ремонта "+c.block().key().id());if(!c.block().top().isEmpty()){var top=b.getRelative(0,1,0);if(!match(top,c.block().material())&&(!top.getType().isAir()||resumed))throw new IllegalArgumentException("Нужна проверка двери");}}
            for(var c:cells){var b=block(world,c.block().key());if(b.getType().isAir())b.setBlockData(Bukkit.createBlockData(c.block().data()),false);if(!c.block().top().isEmpty()&&b.getRelative(0,1,0).getType().isAir())b.getRelative(0,1,0).setBlockData(Bukkit.createBlockData(c.block().top()),false);}
            plugin.tnt().repaired(cells.stream().map(c->c.block().key()).toList());after(store.applied(job.id(),cells),v->{release(job.id());drive(job);},job.id());
        }catch(Exception error){global(()->review(job,RaidController.problem(error)));}
    });}
    private void review(RaidRepairsStore.Job job,String note){after(store.change(job.id(),"PAID","REVIEW_WORLD",note),saved->{log(saved);release(job.id());busyUntil.remove(job.town());},job.id());}
    private static String state(String value){return switch(value){case "PAYING","TREASURY_PENDING"->"Ожидается подтверждение оплаты";case "TREASURY_REVIEW"->"Нужна сверка операции в казне";case "PAID"->"Оплачено, восстанавливается";case "DONE"->"Отремонтировано";case "CANCELLED"->"Оплата отменена";case "REVIEW_BANK"->"Нужна сверка банка";case "REVIEW_WORLD"->"Нужна проверка здания";default->value;};}
    private void log(RaidRepairsStore.Job job){AuditTrail.record(plugin,"repair:"+job.state(),job.id().toString(),(job.state().startsWith("REVIEW")||job.state().equals("TREASURY_REVIEW"))?"BUILDING_REPAIR_REVIEW":"BUILDING_REPAIR",job.state(),AuditTrail.player(job.actor()),AuditTrail.town(job.town()),AuditRecord.Party.system(),"",0,AuditTrail.cents(job.cents()),job.title()+"; уровень "+job.level()+"; цена уровня="+job.levelPrice()/100.0+"; повреждено="+String.format(Locale.ROOT,"%.2f",job.lost()/job.maximum()*100)+"%; "+state(job.state())+"; "+job.note());}
    public void list(CommandSender sender){after(store.list(false),jobs->{if(jobs.isEmpty())raids.reply(sender,"Квитанций ремонта пока нет");for(var job:jobs)raids.reply(sender,job.id()+" | "+job.title()+" | "+state(job.state())+" | "+job.cents()/100.0+" монет");},null);}
    public void show(CommandSender sender,UUID id){after(store.get(id),job->{if(job==null){raids.reply(sender,"Квитанция не найдена");return;}raids.reply(sender,id+" | "+job.title()+" | "+state(job.state())+" | списание="+job.cents()/100.0+"; баланс до="+job.before()/100.0+"; "+job.note());},null);}
    public void resolve(CommandSender sender,UUID id,String decision,String reason){after(store.resolve(id,decision,sender.getName(),reason),job->{log(job);AuditTrail.record(plugin,"repair-resolve:"+UUID.randomUUID(),id.toString(),"BUILDING_REPAIR_RECONCILE",job.state(),sender instanceof Player p?AuditTrail.player(p.getUniqueId()):AuditRecord.Party.system(),AuditTrail.town(job.town()),AuditRecord.Party.system(),"",0,AuditTrail.cents(job.cents()),decision+"; "+reason);raids.reply(sender,"Сверка ремонта сохранена: "+state(job.state()));},null);}
}

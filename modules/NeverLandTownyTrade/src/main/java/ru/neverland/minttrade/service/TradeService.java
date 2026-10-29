package ru.neverland.minttrade.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.core.*;
import ru.neverland.minttrade.integration.*;
import ru.neverland.minttrade.model.*;
import ru.neverland.minttrade.guarantee.GuaranteeTerms;
import ru.neverland.minttrade.util.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Caravan phases advance only from acknowledged snapshots. No own journal IO on a region thread. */
public final class TradeService {
    public enum ProposeResult { SUCCESS, PROCESSING, BUSY, SAME_TOWN, MARKET_REQUIRED, ROUTE_LIMIT, DUPLICATE, ROUTE_UNAVAILABLE, SANCTIONED, IMPORT_RESTRICTED }
    public enum AcceptResult { SUCCESS, NOT_FOUND, NOT_BUYER, MARKET_REQUIRED, ROUTE_LIMIT, ROUTE_UNAVAILABLE, NO_MONEY, STOCK_LOW, WAREHOUSE_BUSY, WAREHOUSE_UNAVAILABLE, ECONOMY_ERROR, SAVE_ERROR, SANCTIONED, IMPORT_RESTRICTED, PROCESSING, REPUTATION_UNAVAILABLE, TERMS_CHANGED }
    public enum CancelResult { SUCCESS, PROCESSING, NOT_FOUND, NOT_PARTY, WAREHOUSE_BUSY, WAREHOUSE_UNAVAILABLE, PAYMENT_PENDING }
    public record ProposeOutcome(ProposeResult result,TradeOffer offer){}
    public record AcceptOutcome(AcceptResult result,Caravan caravan,double required){}
    private final JavaPlugin plugin;private final TownyHook towny;private final BuildBridge builds;private final WarehouseBridge warehouse;private final ExportRegistry registry;
    private final TradeRepository repository;private final RoutePlanner routes;private final EconomyService economy;private final MessageService messages;private final TaxesBridge taxes;private final TradeAudit audits;
    private BukkitTask task;private Runnable beforeQuote=()->{};private CompletableFuture<?> operation;private volatile boolean stopping;private boolean legacyReviews;private int cursor;private long warned;
    private final ThreadPoolExecutor completions=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"nlt-trade-results");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Set<GlobalRequest<?>> globalRequests=Collections.newSetFromMap(new IdentityHashMap<>());
    @FunctionalInterface private interface CheckedSupplier<T>{T get()throws Exception;}
    private static final class GlobalRequest<T>{final CompletableFuture<T> done=new CompletableFuture<>();boolean running;}
    public void beforeQuote(Runnable action){try(var g=SuiteState.guard()){beforeQuote=Objects.requireNonNull(action);}}
    public double feeMultiplier(UUID buyer){try(var g=SuiteState.guard()){beforeQuote.run();return ReputationAccess.tradeFeeMultiplier(buyer);}}
    public TradeService(JavaPlugin plugin,TownyHook towny,BuildBridge builds,WarehouseBridge warehouse,ExportRegistry registry,TradeRepository repository,RoutePlanner routes,EconomyService economy,MessageService messages,TaxesBridge taxes){this.plugin=plugin;this.towny=towny;this.builds=builds;this.warehouse=warehouse;this.registry=registry;this.repository=repository;this.routes=routes;this.economy=economy;this.messages=messages;this.taxes=taxes;this.audits=new TradeAudit(plugin);}
    public void start(){try(var g=SuiteState.guard()){if(stopping)return;if(task!=null)task.cancel();task=SuiteScheduler.get().runTaskTimer(plugin,this::tick,20,20);}}
    private boolean idle(){return !stopping&&plugin.isEnabled()&&!SuiteState.frozen(plugin.getName())&&repository.writable()&&!repository.busy()&&(operation==null||operation.isDone());}
    public boolean processing(){try(var g=SuiteState.guard()){return !idle();}}
    private void track(CompletionStage<?> work,Caravan caravan){try(var g=SuiteState.guard()){
        var future=work.toCompletableFuture();operation=future;future.whenComplete((v,error)->{try(var committed=SuiteState.guard()){if(operation==future)operation=null;if(error!=null)warn(caravan,error);}});
    }}
    private void warn(Caravan c,Throwable error){long now=System.currentTimeMillis();if(now-warned>60_000){warned=now;plugin.getLogger().log(java.util.logging.Level.WARNING,"Торговля ожидает проверки"+(c==null?"":": "+c.id()+" / "+c.settlement()),error);}}
    private <T>CompletionStage<T> global(CheckedSupplier<T> action){return global(action,true);}
    private <T>CompletionStage<T> global(CheckedSupplier<T> action,boolean guard){
        var request=new GlobalRequest<T>();synchronized(globalRequests){if(stopping)return CompletableFuture.failedFuture(new IllegalStateException("Торговля останавливается"));globalRequests.add(request);}
        schedule(request,action,guard,false);return request.done;
    }
    /** Never wait for the suite lock on Folia's global thread, including future continuations. */
    private <T>void schedule(GlobalRequest<T> request,CheckedSupplier<T> action,boolean guard,boolean delayed){
        try{
            java.util.function.Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> callback=task->{
                synchronized(globalRequests){if(request.done.isDone())return;}
                if(guard){if(!SuiteState.tryRun("Trade acknowledged action",()->execute(request,action,true)))schedule(request,action,true,true);}
                else execute(request,action,false);
            };
            if(delayed)Bukkit.getGlobalRegionScheduler().runDelayed(plugin,callback,1);else Bukkit.getGlobalRegionScheduler().run(plugin,callback);
        }catch(Throwable ex){completeOffThread(request,null,ex);}
    }
    private <T>void execute(GlobalRequest<T> request,CheckedSupplier<T> action,boolean guarded){
        synchronized(globalRequests){if(request.done.isDone())return;request.running=true;}
        T value=null;Throwable failure=null;
        try{if(stopping||!plugin.isEnabled()||SuiteState.frozen(plugin.getName()))throw new IllegalStateException("Модуль приостановлен");value=action.get();}catch(Throwable ex){failure=ex;}
        if(guarded)complete(request,value,failure);else completeOffThread(request,value,failure);
    }
    /** Incoming bank IO has returned; finish its receipt on a worker even during plugin shutdown. */
    private <T>void completeOffThread(GlobalRequest<T> request,T value,Throwable error){
        try{completions.execute(()->{try(var guard=SuiteState.guard()){complete(request,value,error);}});}
        catch(RejectedExecutionException ex){
            if(request.done.isDone())return;
            // Defensive fallback only for completion of an existing request, never bank replay.
            // Its future stays in the shutdown barrier until the known result is acknowledged.
            plugin.getLogger().warning("Основная очередь завершения недоступна; подтверждение результата передано резервному worker");
            CompletableFuture.runAsync(()->{try(var guard=SuiteState.guard()){complete(request,value,error);}});
        }
    }
    private <T>void complete(GlobalRequest<T> request,T value,Throwable error){
        try{if(error==null)request.done.complete(value);else request.done.completeExceptionally(error);}finally{synchronized(globalRequests){globalRequests.remove(request);}}
    }
    private static <T>CompletionStage<T> failed(Throwable ex){return CompletableFuture.failedFuture(ex);}
    private static CompletionStage<Void> done(){return CompletableFuture.completedFuture(null);}
    private <T>CompletionStage<T> guarded(Supplier<CompletionStage<T>> action){try(var guard=SuiteState.guard()){try{return action.get();}catch(Throwable ex){return failed(ex);}}}
    public CompletionStage<Void> shutdown(){try(var g=SuiteState.guard()){
        stopping=true;if(task!=null)task.cancel();task=null;
        synchronized(globalRequests){for(var request:List.copyOf(globalRequests))if(!request.running){request.done.completeExceptionally(new IllegalStateException("Остановка до внешнего действия"));globalRequests.remove(request);}}
        var running=operation==null?CompletableFuture.completedFuture(null):operation.handle((v,e)->null);
        return running.thenCompose(v->repository.prepareClose()).thenCompose(v->audits.closeAsync()).whenComplete((v,e)->completions.shutdown());
    }}
    public static boolean importsAllowed(Town buyer,Town seller){try(var g=SuiteState.guard()){var n=buyer.getNationOrNull();boolean same=n!=null&&seller.getNationOrNull()!=null&&n.getUUID().equals(seller.getNationOrNull().getUUID());return ru.neverland.integration.PoliciesAccess.importsAllowed(buyer.getUUID(),seller.getUUID(),same);}}
    public GuaranteeTerms guaranteeTerms(){try(var g=SuiteState.guard()){
        Object enabled=plugin.getConfig().get("delivery-guarantee.enabled",true);if(!(enabled instanceof Boolean))throw new IllegalArgumentException("delivery-guarantee.enabled должен быть true/false");if(!((Boolean)enabled))return GuaranteeTerms.none();
        return new GuaranteeTerms(integer("reserve-basis-points",2000),integer("delay-basis-points-per-minute",100),Math.multiplyExact(integer("grace-seconds",60),1000L));
    }}
    private int integer(String key,int fallback){Object raw=plugin.getConfig().get("delivery-guarantee."+key,fallback);if(!(raw instanceof Number n)||n.doubleValue()!=n.intValue())throw new IllegalArgumentException("Неверное целое delivery-guarantee."+key);return n.intValue();}
    public String definitionFingerprint(ExportDefinition definition){try(var g=SuiteState.guard()){
        try{var item=definition.item().clone();item.setAmount(1);var digest=java.security.MessageDigest.getInstance("SHA-256");digest.update(item.serializeAsBytes());digest.update(("|"+definition.amount()+"|"+priceCents(cents(definition.price()))).getBytes(java.nio.charset.StandardCharsets.UTF_8));return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException ex){throw new AssertionError(ex);}
    }}
    private static long priceCents(double value){return java.math.BigDecimal.valueOf(value).movePointRight(2).longValueExact();}
    public ProposeOutcome propose(Town seller,Town buyer,ExportDefinition definition){try(var g=SuiteState.guard()){
        if(!idle())return new ProposeOutcome(ProposeResult.BUSY,null);
        if(seller==null||buyer==null||definition==null)return new ProposeOutcome(ProposeResult.ROUTE_UNAVAILABLE,null);
        if(seller.getUUID().equals(buyer.getUUID()))return new ProposeOutcome(ProposeResult.SAME_TOWN,null);
        if(!importsAllowed(buyer,seller))return new ProposeOutcome(ProposeResult.IMPORT_RESTRICTED,null);
        if(taxes.tradeBlocked(seller.getUUID(),buyer.getUUID()))return new ProposeOutcome(ProposeResult.SANCTIONED,null);
        if(marketLevel(seller)<=0||marketLevel(buyer)<=0)return new ProposeOutcome(ProposeResult.MARKET_REQUIRED,null);
        if(!hasRouteSlot(seller)||!hasRouteSlot(buyer))return new ProposeOutcome(ProposeResult.ROUTE_LIMIT,null);
        if(routes.plan(seller,buyer)==null)return new ProposeOutcome(ProposeResult.ROUTE_UNAVAILABLE,null);
        if(repository.offers().stream().anyMatch(o->o.sellerId().equals(seller.getUUID())&&o.buyerId().equals(buyer.getUUID())&&o.exportId().equalsIgnoreCase(definition.id())))return new ProposeOutcome(ProposeResult.DUPLICATE,null);
        long now=System.currentTimeMillis();var terms=guaranteeTerms();var offer=new TradeOffer(UUID.randomUUID(),seller.getUUID(),buyer.getUUID(),definition.id(),now,Math.addExact(now,Math.multiplyExact(Math.max(1,plugin.getConfig().getLong("trade.proposal-expiration-hours",24)),3_600_000L)),terms,priceCents(cents(definition.price())),definitionFingerprint(definition));
        track(repository.putOffer(offer).thenCompose(v->global(()->{if(plugin.getConfig().getBoolean("announcements.proposal",true))announce(buyer,"proposal-received",Map.of("town",seller.getName(),"export",ColorUtil.strip(definition.name())));return null;})),null);
        return new ProposeOutcome(ProposeResult.PROCESSING,offer);
    }}
    public AcceptOutcome accept(Town buyer,TradeOffer offer){return accept(buyer,offer,null);}
    public AcceptOutcome accept(Town buyer,TradeOffer offer,UUID actor){try(var g=SuiteState.guard()){
        if(!idle())return new AcceptOutcome(AcceptResult.SAVE_ERROR,null,0);
        if(offer==null||!offer.equals(repository.findOffer(offer.id().toString()))||offer.expiresAt()<=System.currentTimeMillis())return new AcceptOutcome(AcceptResult.NOT_FOUND,null,0);
        if(buyer==null||!buyer.getUUID().equals(offer.buyerId()))return new AcceptOutcome(AcceptResult.NOT_BUYER,null,0);
        Town seller=towny.town(offer.sellerId());var definition=registry.get(offer.exportId());if(seller==null||definition==null)return new AcceptOutcome(AcceptResult.NOT_FOUND,null,0);
        if(!offer.quotedDefinition().isEmpty()&&!offer.quotedDefinition().equals(definitionFingerprint(definition)))return new AcceptOutcome(AcceptResult.TERMS_CHANGED,null,0);
        if(!importsAllowed(buyer,seller))return new AcceptOutcome(AcceptResult.IMPORT_RESTRICTED,null,0);
        if(taxes.tradeBlocked(seller.getUUID(),buyer.getUUID()))return new AcceptOutcome(AcceptResult.SANCTIONED,null,0);
        if(marketLevel(seller)<=0||marketLevel(buyer)<=0)return new AcceptOutcome(AcceptResult.MARKET_REQUIRED,null,0);
        if(!hasRouteSlot(seller)||!hasRouteSlot(buyer))return new AcceptOutcome(AcceptResult.ROUTE_LIMIT,null,0);
        var plan=routes.plan(seller,buyer);if(plan==null)return new AcceptOutcome(AcceptResult.ROUTE_UNAVAILABLE,null,0);
        Map<UUID,Double> tolls;try{tolls=tolls(definition,plan.transitTowns(),seller,buyer);}catch(IllegalStateException ex){return new AcceptOutcome(AcceptResult.REPUTATION_UNAVAILABLE,null,0);}
        double total=cents(definition.price()+tolls.values().stream().mapToDouble(Double::doubleValue).sum());if(!economy.canWithdraw(buyer,total))return new AcceptOutcome(AcceptResult.NO_MONEY,null,total);
        long now=System.currentTimeMillis();var c=new Caravan(offer.id(),seller.getUUID(),buyer.getUUID(),definition.id(),definition.item(),definition.amount(),definition.amount(),cents(definition.price()),total,tolls,plan.points(),now,Math.addExact(now,plan.durationMillis()),now+Math.max(1000,Math.round(plan.durationMillis()*.55)),ThreadLocalRandom.current().nextDouble()<plan.delayChance(),false,CaravanStatus.ACTIVE);
        if(offer.guarantee().enabled())c.restoreGuarantee(offer.guarantee(),plan.durationMillis(),0,-1,-1);
        track(repository.accept(offer,c).thenCompose(v->audit(c,actor)),c);return new AcceptOutcome(AcceptResult.PROCESSING,c,total);
    }}
    public CancelResult reject(Town buyer,TradeOffer offer){try(var g=SuiteState.guard()){if(offer==null)return CancelResult.NOT_FOUND;if(buyer==null||!buyer.getUUID().equals(offer.buyerId()))return CancelResult.NOT_PARTY;if(!idle())return CancelResult.WAREHOUSE_BUSY;track(repository.removeOffer(offer),null);return CancelResult.PROCESSING;}}
    public CancelResult cancelOffer(Town seller,TradeOffer offer){try(var g=SuiteState.guard()){if(offer==null)return CancelResult.NOT_FOUND;if(seller==null||!seller.getUUID().equals(offer.sellerId()))return CancelResult.NOT_PARTY;if(!idle())return CancelResult.WAREHOUSE_BUSY;track(repository.removeOffer(offer),null);return CancelResult.PROCESSING;}}
    public CancelResult cancelCaravan(Caravan previous){try(var g=SuiteState.guard()){
        if(previous==null)return CancelResult.NOT_FOUND;var c=repository.findCaravan(previous.id().toString());if(c==null||c.terminal())return CancelResult.NOT_FOUND;if(!idle())return CancelResult.WAREHOUSE_BUSY;
        if(!Set.of("PREPARED","ACTIVE","RETURNING").contains(c.settlement())||repository.effects().state(c.operation("debit"))==EffectJournal.State.PENDING)return CancelResult.PAYMENT_PENDING;
        c.settlement("RETURNING");track(repository.update(c).thenCompose(v->audit(c,null)),c);return CancelResult.PROCESSING;
    }}
    public boolean forceComplete(Caravan previous){try(var g=SuiteState.guard()){
        if(previous==null||!idle())return false;var c=repository.findCaravan(previous.id().toString());if(c==null||!c.settlement().equals("ACTIVE"))return false;
        c.settleGuarantee(System.currentTimeMillis());c.settlement("DELIVERING");track(repository.update(c).thenCompose(v->audit(c,null)),c);return true;
    }}
    public double tariff(Town town) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { return tariff(town.getUUID()); } }
    private double tariff(UUID town) { return ru.neverland.integration.PoliciesAccess.tariff(town,repository.tariff(town,plugin.getConfig().getDouble("tariffs.default-percent",0)),maxTariff()); }
    public double maxTariff() { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { return plugin.getConfig().getDouble("tariffs.maximum-percent", 20); } }
    public boolean setTariff(Town town,double percent){try(var guard=ru.neverland.core.SuiteState.guard()){
        if(town==null||!Double.isFinite(percent)||percent<0||percent>maxTariff()||!idle())return false;
        track(repository.setTariff(town.getUUID(),cents(percent)),null);return true;
    }}
    public int marketLevel(Town town) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) { return town == null ? 0 : builds.marketLevel(town.getUUID()); } }
    public int routeLimit(Town town) { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        if (town == null) return 0;
        int limit = plugin.getConfig().getInt("market.routes-by-level." + marketLevel(town), 0);
        limit += builds.projectLevel(town.getUUID(), "rhodes_colossus")
                * plugin.getConfig().getInt("market.colossus-extra-routes", 2);
        limit += builds.projectLevel(town.getUUID(), "crystal_palace")
                * plugin.getConfig().getInt("market.crystal-palace-extra-routes", 2);
        return limit;
    } }
    public int activeCount(Town town) { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) { return town == null ? 0 : repository.caravans(town.getUUID()).size(); } }
    public boolean hasRouteSlot(Town town) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) { return activeCount(town) < routeLimit(town); } }


    private void tick(){
        if(!idle())return;
        if(!legacyReviews){var entries=new LinkedHashMap<UUID,String>();repository.pendingCredits().forEach((town,amount)->entries.put(EffectJournal.id("legacy-trade-credit:"+town),"Старое зачисление городу "+town+": "+amount));track(repository.effects().review(entries).thenRun(()->{try(var g=SuiteState.guard()){legacyReviews=true;}}),null);return;}
        long now=System.currentTimeMillis();if(repository.offers().stream().anyMatch(o->o.expiresAt()<=now)){track(repository.expire(now),null);return;}
        var list=new ArrayList<>(repository.allCaravans());list.sort(Comparator.comparing(c->c.id().toString()));
        for(int i=0;i<list.size();i++){
            var c=list.get(Math.floorMod(cursor++,list.size()));if(c.settlement().equals("LEGACY_REVIEW"))continue;
            if(c.terminal()){track(cleanup(c),c);return;}
            if(c.settlement().equals("ACTIVE")){
                if(c.timer().paused())continue;
                if(towny.town(c.buyerId())==null){cancelCaravan(c);return;}
                if(!c.incidentHandled()&&now>=c.incidentAt()){
                    c.incidentHandled(true);long delay=c.shouldDelay()?Math.max(1,Math.min(86400,plugin.getConfig().getLong("routes.delay.seconds",300)))*1000:0;if(delay>0)c.delay(delay);
                    track(repository.update(c).thenCompose(v->global(()->{if(delay>0&&plugin.getConfig().getBoolean("announcements.delay",true))announceBoth(c,"caravan-delayed",definition(c),Map.of("time",TimeUtil.format(delay)));return null;})),c);return;
                }
                if(now<c.arrivesAt())continue;c.settleGuarantee(now);c.settlement("DELIVERING");track(repository.update(c).thenCompose(v->audit(c,null)),c);return;
            }
            track(advance(c),c);return;
        }
        retryCredit();
    }
    private CompletionStage<Void> advance(Caravan c){
        return switch(c.settlement()){
            case "PREPARED" -> prepare(c);
            case "DELIVERING" -> global(()->warehouse.transfer(c.operation("delivery"),c.buyerId(),c.cargoItem(),c.remainingCargo(),true).status()==WarehouseBridge.Status.SUCCESS).thenCompose(ok->{if(!ok)return done();c.settlement("PAYING");return repository.update(c).thenCompose(v->audit(c,null)).thenApply(v->null);});
            case "PAYING" -> pay(c);
            case "RETURNING" -> returning(c);
            default -> done();
        };
    }
    private CompletionStage<Void> prepare(Caravan c){
        if(taxes.tradeBlocked(c.sellerId(),c.buyerId())&&repository.effects().state(c.operation("debit"))==EffectJournal.State.READY){c.settlement("RETURNING");return repository.update(c);}
        return global(()->warehouse.transfer(c.operation("take"),c.sellerId(),c.cargoItem(),c.totalCargo(),false).status()==WarehouseBridge.Status.SUCCESS).thenCompose(reserved->{
            if(!reserved)return done();return bank(c,c.buyerId(),c.escrow(),"debit",false).thenCompose(paid->{
                c.settlement(paid?"ACTIVE":"RETURNING");if(paid){long duration=c.guarantee().enabled()?c.guaranteedRouteDuration():Math.max(1,c.arrivesAt()-c.departedAt());long now=System.currentTimeMillis();c.travelTimes(now,now+Math.max(1000,Math.round(duration*.55)));c.arrivesAt(Math.addExact(now,duration));}
                return repository.update(c).thenCompose(v->audit(c,null)).thenCompose(v->global(()->{if(paid&&plugin.getConfig().getBoolean("announcements.departure",true))announceBoth(c,"caravan-town-departed",definition(c),Map.of());return null;}));
            });
        });
    }
    private CompletionStage<Void> pay(Caravan c){
        CompletionStage<Boolean> chain=credit(c,c.sellerId(),c.sellerPayment(),"seller");
        if(c.compensationCents()>0)chain=chain.thenCompose(ok->ok?credit(c,c.buyerId(),c.compensationCents()/100.0,"compensation"):CompletableFuture.completedFuture(false));
        for(var e:new TreeMap<>(c.tariffs()).entrySet())chain=chain.thenCompose(ok->ok?credit(c,e.getKey(),e.getValue(),"tariff"):CompletableFuture.completedFuture(false));
        return chain.thenCompose(ok->ok?finish(c,CaravanStatus.COMPLETED):done());
    }
    private CompletionStage<Void> returning(Caravan c){
        return global(()->c.legacyFunded()||warehouse.transferred(c.operation("take"),c.sellerId())).thenCompose(taken->taken?global(()->warehouse.transfer(c.operation("return"),c.sellerId(),c.cargoItem(),c.remainingCargo(),true).status()==WarehouseBridge.Status.SUCCESS):CompletableFuture.completedFuture(true)).thenCompose(returned->{
            if(!returned)return done();boolean funded=c.legacyFunded()||repository.effects().state(c.operation("debit"))==EffectJournal.State.DONE;
            return (funded?credit(c,c.buyerId(),c.escrow(),"refund"):CompletableFuture.completedFuture(true)).thenCompose(refunded->refunded?finish(c,CaravanStatus.CANCELLED):done());
        });
    }
    private CompletionStage<Boolean> bank(Caravan c,UUID recipient,double amount,String kind,boolean incoming){
        if(stopping)return failed(new IllegalStateException("Остановка до нового платежа"));
        if(amount<=0)return CompletableFuture.completedFuture(true);UUID id=c.operation(incoming?kind+":"+recipient:"debit");
        String description=incoming?creditDescription(c,recipient,amount,kind):"Списание каравана "+c.id()+" город "+c.buyerId()+": "+c.escrow();
        return repository.effects().execute(id,description,()->global(()->economy.prepare(towny.town(recipient),amount,definition(c),kind,id,incoming)).thenCompose(prepared->global(prepared::get,!incoming)));
    }
    private String creditDescription(Caravan c,UUID recipient,double amount,String kind){return "Караван "+c.id()+" "+kind+" город "+recipient+": "+amount;}
    private CompletionStage<Boolean> credit(Caravan c,UUID recipient,double amount,String kind){return bank(c,recipient,amount,kind,true).thenCompose(paid->{
        if(!paid||amount<=0)return CompletableFuture.completedFuture(paid);
        return guarded(()->{var entry=new TradeAudit.Entry(kind+":"+recipient,c.id().toString(),kind.equals("tariff")?"TRADE_TARIFF":kind.equals("refund")?"TRADE_REFUND":kind.equals("compensation")?"TRADE_COMPENSATION":"TRADE_PAYMENT","COMPLETED",AuditRecord.Party.system(),new AuditRecord.Party("ESCROW",c.id().toString(),"Резерв каравана",c.buyerId().toString()),AuditTrail.town(recipient),"",0,AuditTrail.money(amount),"effect="+c.operation(kind+":"+recipient));return audits.append(entry);});
    });}
    private CompletionStage<Void> finish(Caravan c,CaravanStatus status){return guarded(()->{
        c.settlement(status==CaravanStatus.COMPLETED?"COMPLETE":"CANCELLED");var history=new TradeHistory(c.id(),c.sellerId(),c.buyerId(),c.exportId(),c.totalCargo(),c.basePrice(),c.tariffs().values().stream().mapToDouble(Double::doubleValue).sum(),System.currentTimeMillis(),status,status==CaravanStatus.COMPLETED?c.compensationCents():0);
        return repository.finish(c,history,plugin.getConfig().getInt("trade.history-limit-per-town",30)).thenCompose(v->audit(c,null)).thenCompose(v->global(()->{if(status==CaravanStatus.COMPLETED&&plugin.getConfig().getBoolean("announcements.arrival",true))announceBoth(c,"caravan-arrived",definition(c),Map.of());return null;}));
    });}
    private CompletionStage<Boolean> audit(Caravan c,UUID actor){return guarded(()->audits.append(new TradeAudit.Entry(c.settlement(),c.id().toString(),"CARAVAN_DEAL",c.settlement(),actor==null?AuditRecord.Party.unknown():AuditTrail.player(actor),AuditTrail.town(c.sellerId()),AuditTrail.town(c.buyerId()),AuditTrail.item(c.cargoItem()),c.totalCargo(),AuditTrail.money(c.basePrice()),"export="+c.exportId()+"; escrow="+c.escrow()+"; tariffs="+new TreeMap<>(c.tariffs())+"; legacy="+c.legacyFunded()+"; compensation-cents="+c.finalCompensation())));}
    private CompletionStage<Void> cleanup(Caravan c){return audit(c,null).thenCompose(ok->{if(!ok)return failed(new IllegalStateException("Нет подтверждения журнала; история каравана сохранена"));return global(()->{warehouse.acknowledge(c.operation("take"),c.sellerId());warehouse.acknowledge(c.operation("return"),c.sellerId());warehouse.acknowledge(c.operation("delivery"),c.buyerId());return null;}).thenCompose(v->repository.removeCaravan(c)).thenCompose(v->pruneEffects());});}
    private CompletionStage<Void> pruneEffects(){return guarded(()->{Set<UUID> needed=new HashSet<>();for(var c:repository.allCaravans()){needed.add(c.operation("debit"));needed.add(c.operation("seller:"+c.sellerId()));needed.add(c.operation("refund:"+c.buyerId()));needed.add(c.operation("compensation:"+c.buyerId()));c.tariffs().keySet().forEach(t->needed.add(c.operation("tariff:"+t)));}repository.pendingCredits().keySet().forEach(t->needed.add(EffectJournal.id("legacy-trade-credit:"+t)));return repository.effects().retain(needed);});}
    public CompletionStage<Void> resolvePayment(UUID id,boolean paid){try(var g=SuiteState.guard()){if(!idle())return failed(new IllegalStateException("Предыдущее действие ещё выполняется"));var result=repository.effects().resolve(id,paid);track(result,null);return result;}}
    public CompletionStage<Void> resolveCaravan(String id,String decision){try(var g=SuiteState.guard()){
        if(!idle())return failed(new IllegalStateException("Предыдущее действие ещё выполняется"));var c=repository.findCaravan(id);if(c==null||!c.settlement().equals("LEGACY_REVIEW"))return failed(new IllegalArgumentException("Нет старого каравана для сверки"));CompletionStage<Void> result;
        if(decision.equals("legacy-active")){if(c.remainingCargo()<1)return failed(new IllegalArgumentException("Груз уже выгружен"));c.settlement("ACTIVE");result=repository.update(c);}
        else if(decision.equals("legacy-complete"))result=finish(c,CaravanStatus.COMPLETED);
        else if(decision.equals("legacy-delivered")){var reviews=new LinkedHashMap<UUID,String>();if(c.basePrice()>0)reviews.put(c.operation("seller:"+c.sellerId()),creditDescription(c,c.sellerId(),c.basePrice(),"seller"));c.tariffs().forEach((town,amount)->{if(amount>0)reviews.put(c.operation("tariff:"+town),creditDescription(c,town,amount,"tariff"));});c.settlement("PAYING");result=repository.effects().review(reviews).thenCompose(v->repository.update(c));}
        else return failed(new IllegalArgumentException("Решение: legacy-active, legacy-delivered или legacy-complete"));track(result,c);return result;
    }}
    private void retryCredit(){
        var fallback=registry.all().stream().findFirst().orElse(null);if(fallback==null)return;
        for(var e:repository.pendingCredits().entrySet()){UUID id=EffectJournal.id("legacy-trade-credit:"+e.getKey());if(repository.effects().state(id)==EffectJournal.State.PENDING)continue;
            var work=repository.effects().execute(id,"Старое зачисление городу "+e.getKey()+": "+e.getValue(),()->global(()->economy.prepare(towny.town(e.getKey()),e.getValue(),fallback,"pending",id,true)).thenCompose(prepared->global(prepared::get,false))).thenCompose(paid->paid?repository.clearPending(e.getKey()):done());track(work,null);return;
        }
    }
    private Map<UUID, Double> tolls(ExportDefinition definition, List<UUID> towns, Town seller, Town buyer) {
        Map<UUID, Double> result = new LinkedHashMap<>();
        double reputation = feeMultiplier(buyer.getUUID());
        double preference = taxes.preferenceMultiplier(seller.getUUID(), buyer.getUUID());
        for (UUID townId : towns) {
            double percent = tariff(townId);
            double agreed = ru.neverland.core.DiplomacyAccess.tariffMultiplier(seller.getUUID(), buyer.getUUID(), townId);
            double amount = cents(TradeMath.tariff(definition.price(), percent) * Math.min(preference, agreed) * reputation); if (amount > 0) result.put(townId, amount);
        }
        return result;
    }
    private ExportDefinition definition(Caravan caravan) {
        ExportDefinition current = registry.get(caravan.exportId());
        return current != null ? current : new ExportDefinition(caravan.exportId(), "&f" + registry.itemName(caravan.cargoItem()),
                caravan.cargoItem().getType() == Material.AIR ? Material.CHEST : caravan.cargoItem().getType(), -1,
                List.of(), caravan.cargoItem().getType().name(), caravan.cargoItem(), caravan.totalCargo(), caravan.basePrice());
    }
    private void announceBoth(Caravan caravan, String key, ExportDefinition definition, Map<String, ?> extra) {
        Town seller = towny.town(caravan.sellerId()), buyer = towny.town(caravan.buyerId());
        Map<String, Object> values = new LinkedHashMap<>(); values.put("from", seller == null ? "?" : seller.getName());
        values.put("to", buyer == null ? "?" : buyer.getName()); values.put("export", ColorUtil.strip(definition.name())); values.putAll(extra);
        if (seller != null) announce(seller, key, values); if (buyer != null) announce(buyer, key, values);
    }
    private void announce(Town town, String key, Map<String, ?> values) {
        String text = messages.text(key, values, true);
        ru.neverland.core.SuiteScheduler.players(plugin, player -> {{ Town playerTown = towny.town(player); if (town.equals(playerTown)) player.sendMessage(text); }});
    }
    private double cents(double value) { return TradeMath.cents(value); }

    public ExportRegistry registry() { try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) { return registry; } }
    public TradeRepository repository() { try (var suiteGuard31 = ru.neverland.core.SuiteState.guard()) { return repository; } }
    public EconomyService economy() { try (var suiteGuard32 = ru.neverland.core.SuiteState.guard()) { return economy; } }
    public TradeOffer offer(String id) { try (var suiteGuard33 = ru.neverland.core.SuiteState.guard()) { return repository.findOffer(id); } }
    public Caravan caravan(String id) { try (var suiteGuard34 = ru.neverland.core.SuiteState.guard()) { return repository.findCaravan(id); } }
    public List<TradeOffer> incoming(Town town) { try (var suiteGuard35 = ru.neverland.core.SuiteState.guard()) { return town == null ? List.of() : repository.incoming(town.getUUID()); } }
    public List<TradeOffer> outgoing(Town town) { try (var suiteGuard36 = ru.neverland.core.SuiteState.guard()) { return town == null ? List.of() : repository.outgoing(town.getUUID()); } }
    public List<Caravan> caravans(Town town) { try (var suiteGuard37 = ru.neverland.core.SuiteState.guard()) { return town == null ? List.of() : repository.caravans(town.getUUID()); } }
    public List<TradeHistory> history(Town town) { try (var suiteGuard38 = ru.neverland.core.SuiteState.guard()) { return town == null ? List.of() : repository.history(town.getUUID()); } }
    public ExportDefinition definition(TradeOffer offer) { try (var suiteGuard39 = ru.neverland.core.SuiteState.guard()) { return offer == null ? null : registry.get(offer.exportId()); } }
    public ExportDefinition definitionOf(Caravan caravan) { try (var suiteGuard40 = ru.neverland.core.SuiteState.guard()) { return caravan == null ? null : definition(caravan); } }


    public List<ActivityAdmin.Target> adminTargets(){try(var g=SuiteState.guard()){
        ApiServices.primaryThread();return repository.caravans().stream().map(c->new ActivityAdmin.Target(c.id().toString(),c.exportId()+" / "+c.sellerId()+" → "+c.buyerId()+" / "+c.settlement(),c.settlement().equals("ACTIVE")?ActivityAdmin.TIMED:Set.of("status"),(action,minutes)->{
            ApiServices.primaryThread();var live=repository.findCaravan(c.id().toString());if(live==null)throw new IllegalStateException("Караван уже завершён");if(action.equals("status"))return live.id()+" | "+live.settlement()+" | "+live.timer().describe(System.currentTimeMillis());if(!idle()||!live.settlement().equals("ACTIVE"))throw new IllegalStateException("Караван сохраняется или рассчитывается; повторите позже");
            if(action.equals("cancel")){var result=cancelCaravan(live);if(result!=CancelResult.PROCESSING)throw new IllegalStateException("Возврат требует проверки: "+result);return "Запрос отмены сохраняется; возврат без провала продолжится после подтверждения записи";}
            live.editTimer(action,minutes,System.currentTimeMillis());track(repository.update(live).thenCompose(v->audit(live,null)),live);return "Изменение времени сохраняется. Обновите состояние каравана";
        })).toList();
    }}
}

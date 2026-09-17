package ru.neverland.minttrade.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.minttrade.integration.BuildBridge;
import ru.neverland.minttrade.integration.TownyHook;
import ru.neverland.minttrade.integration.TaxesBridge;
import ru.neverland.minttrade.integration.WarehouseBridge;
import ru.neverland.minttrade.model.Caravan;
import ru.neverland.minttrade.model.CaravanStatus;
import ru.neverland.minttrade.model.ExportDefinition;
import ru.neverland.minttrade.model.TradeHistory;
import ru.neverland.minttrade.model.TradeOffer;
import ru.neverland.minttrade.util.ColorUtil;
import ru.neverland.minttrade.util.TimeUtil;
import ru.neverland.minttrade.util.TradeMath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class TradeService {
    public enum ProposeResult { SUCCESS, SAME_TOWN, MARKET_REQUIRED, ROUTE_LIMIT, DUPLICATE, ROUTE_UNAVAILABLE, SANCTIONED, IMPORT_RESTRICTED }
    public enum AcceptResult { SUCCESS, NOT_FOUND, NOT_BUYER, MARKET_REQUIRED, ROUTE_LIMIT, ROUTE_UNAVAILABLE,
        NO_MONEY, STOCK_LOW, WAREHOUSE_BUSY, WAREHOUSE_UNAVAILABLE, ECONOMY_ERROR, SAVE_ERROR, SANCTIONED, IMPORT_RESTRICTED, PROCESSING, REPUTATION_UNAVAILABLE }
    public enum CancelResult { SUCCESS, NOT_FOUND, NOT_PARTY, WAREHOUSE_BUSY, WAREHOUSE_UNAVAILABLE, PAYMENT_PENDING }
    public record ProposeOutcome(ProposeResult result, TradeOffer offer) {}
    public record AcceptOutcome(AcceptResult result, Caravan caravan, double required) {}

    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final BuildBridge builds;
    private final WarehouseBridge warehouse;
    private final ExportRegistry registry;
    private final TradeRepository repository;
    private final RoutePlanner routes;
    private final EconomyService economy;
    private final MessageService messages;
    private final TaxesBridge taxes;
    private BukkitTask task;
    private Runnable beforeQuote = () -> { };
    public void beforeQuote(Runnable action) { beforeQuote = java.util.Objects.requireNonNull(action); }
    public double feeMultiplier(UUID buyer) { beforeQuote.run();return ru.neverland.core.ReputationAccess.tradeFeeMultiplier(buyer); }

    public TradeService(JavaPlugin plugin, TownyHook towny, BuildBridge builds, WarehouseBridge warehouse,
                        ExportRegistry registry, TradeRepository repository, RoutePlanner routes,
                        EconomyService economy, MessageService messages, TaxesBridge taxes) {
        this.plugin = plugin; this.towny = towny; this.builds = builds; this.warehouse = warehouse;
        this.registry = registry; this.repository = repository; this.routes = routes; this.economy = economy; this.messages = messages; this.taxes = taxes;
    }
    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }
    public void shutdown() { if (task != null) task.cancel(); task = null; if(repository.writable())repository.save(); }

    public static boolean importsAllowed(Town buyer,Town seller){var nation=buyer.getNationOrNull();boolean sameNation=nation!=null&&seller.getNationOrNull()!=null&&nation.getUUID().equals(seller.getNationOrNull().getUUID());return ru.neverland.integration.PoliciesAccess.importsAllowed(buyer.getUUID(),seller.getUUID(),sameNation);}
    public ProposeOutcome propose(Town seller, Town buyer, ExportDefinition definition) {
        if (seller == null || buyer == null || definition == null) return new ProposeOutcome(ProposeResult.ROUTE_UNAVAILABLE, null);
        if (seller.getUUID().equals(buyer.getUUID())) return new ProposeOutcome(ProposeResult.SAME_TOWN, null);
        if (!importsAllowed(buyer,seller)) return new ProposeOutcome(ProposeResult.IMPORT_RESTRICTED,null);
        if (taxes.tradeBlocked(seller.getUUID(), buyer.getUUID())) return new ProposeOutcome(ProposeResult.SANCTIONED, null);
        if (marketLevel(seller) <= 0 || marketLevel(buyer) <= 0) return new ProposeOutcome(ProposeResult.MARKET_REQUIRED, null);
        if (!hasRouteSlot(seller) || !hasRouteSlot(buyer)) return new ProposeOutcome(ProposeResult.ROUTE_LIMIT, null);
        if (routes.plan(seller, buyer) == null) return new ProposeOutcome(ProposeResult.ROUTE_UNAVAILABLE, null);
        boolean duplicate = repository.offers().stream().anyMatch(value -> value.sellerId().equals(seller.getUUID())
                && value.buyerId().equals(buyer.getUUID()) && value.exportId().equalsIgnoreCase(definition.id()));
        if (duplicate) return new ProposeOutcome(ProposeResult.DUPLICATE, null);
        long now = System.currentTimeMillis();
        TradeOffer offer = new TradeOffer(UUID.randomUUID(), seller.getUUID(), buyer.getUUID(), definition.id(), now,
                now + Math.max(1, plugin.getConfig().getLong("trade.proposal-expiration-hours", 24)) * 3_600_000L);
        repository.add(offer); saveNow();
        if (plugin.getConfig().getBoolean("announcements.proposal", true)) announce(buyer, "proposal-received", Map.of(
                "town", seller.getName(), "export", ColorUtil.strip(definition.name())));
        return new ProposeOutcome(ProposeResult.SUCCESS, offer);
    }

    public AcceptOutcome accept(Town buyer, TradeOffer offer) {
        return accept(buyer,offer,null);
    }
    public AcceptOutcome accept(Town buyer, TradeOffer offer,UUID actor) {
        if (!repository.writable()) return new AcceptOutcome(AcceptResult.SAVE_ERROR,null,0);
        if (offer == null || repository.findOffer(offer.id().toString())==null) return new AcceptOutcome(AcceptResult.NOT_FOUND, null, 0);
        if (buyer == null || !buyer.getUUID().equals(offer.buyerId())) return new AcceptOutcome(AcceptResult.NOT_BUYER, null, 0);
        Town seller = towny.town(offer.sellerId()); ExportDefinition definition = registry.get(offer.exportId());
        if (seller == null || definition == null) { repository.remove(offer); saveNow(); return new AcceptOutcome(AcceptResult.NOT_FOUND, null, 0); }
        if (!importsAllowed(buyer,seller)) return new AcceptOutcome(AcceptResult.IMPORT_RESTRICTED,null,0);
        if (taxes.tradeBlocked(seller.getUUID(), buyer.getUUID())) return new AcceptOutcome(AcceptResult.SANCTIONED, null, 0);
        if (marketLevel(seller) <= 0 || marketLevel(buyer) <= 0) return new AcceptOutcome(AcceptResult.MARKET_REQUIRED, null, 0);
        if (!hasRouteSlot(seller) || !hasRouteSlot(buyer)) return new AcceptOutcome(AcceptResult.ROUTE_LIMIT, null, 0);
        RoutePlanner.RoutePlan plan = routes.plan(seller, buyer);
        if (plan == null) return new AcceptOutcome(AcceptResult.ROUTE_UNAVAILABLE, null, 0);
        Map<UUID, Double> tolls;
        try { tolls = tolls(definition, plan.transitTowns(), seller, buyer); }
        catch (IllegalStateException ex) { return new AcceptOutcome(AcceptResult.REPUTATION_UNAVAILABLE,null,0); }
        double total = cents(definition.price() + tolls.values().stream().mapToDouble(Double::doubleValue).sum());
        if (!economy.canWithdraw(buyer, total)) return new AcceptOutcome(AcceptResult.NO_MONEY, null, total);
        long now = System.currentTimeMillis(), arrives = now + plan.durationMillis();
        Caravan caravan = new Caravan(offer.id(), seller.getUUID(), buyer.getUUID(), definition.id(), definition.item(),
                definition.amount(), definition.amount(), cents(definition.price()), total, tolls, plan.points(), now, arrives,
                now + Math.max(1000, Math.round(plan.durationMillis() * 0.55)),
                ThreadLocalRandom.current().nextDouble() < plan.delayChance(), false, CaravanStatus.ACTIVE);
        repository.remove(offer); repository.add(caravan);
        repository.save();
        audit(caravan,actor);
        try {advance(caravan);}catch(Exception ex){warn(caravan,ex);return new AcceptOutcome(AcceptResult.PROCESSING,caravan,total);}
        if(!caravan.settlement().equals("ACTIVE"))return new AcceptOutcome(AcceptResult.PROCESSING,caravan,total);
        if(plugin.getConfig().getBoolean("announcements.departure",true))announceBoth(caravan,"caravan-town-departed",definition,Map.of());
        return new AcceptOutcome(AcceptResult.SUCCESS,caravan,total);
    }

    public CancelResult reject(Town buyer, TradeOffer offer) {
        if (offer == null) return CancelResult.NOT_FOUND;
        if (buyer == null || !offer.buyerId().equals(buyer.getUUID())) return CancelResult.NOT_PARTY;
        repository.remove(offer); saveNow(); return CancelResult.SUCCESS;
    }
    public CancelResult cancelOffer(Town seller, TradeOffer offer) {
        if (offer == null) return CancelResult.NOT_FOUND;
        if (seller == null || !offer.sellerId().equals(seller.getUUID())) return CancelResult.NOT_PARTY;
        repository.remove(offer); saveNow(); return CancelResult.SUCCESS;
    }
    public CancelResult cancelCaravan(Caravan caravan) {
        if(caravan==null||caravan.terminal())return CancelResult.NOT_FOUND;
        if(!repository.writable())return CancelResult.WAREHOUSE_UNAVAILABLE;
        if(!java.util.Set.of("PREPARED","ACTIVE","RETURNING").contains(caravan.settlement())||repository.effects().state(caravan.operation("debit"))==ru.neverland.core.EffectJournal.State.PENDING)return CancelResult.PAYMENT_PENDING;
        caravan.settlement("RETURNING");repository.save();
        try{advance(caravan);return caravan.terminal()?CancelResult.SUCCESS:CancelResult.WAREHOUSE_BUSY;}catch(Exception ex){warn(caravan,ex);return CancelResult.PAYMENT_PENDING;}
    }
    public boolean forceComplete(Caravan caravan){if(caravan==null||!repository.writable())return false;return deliver(caravan);}

    public double tariff(Town town) { return tariff(town.getUUID()); }
    private double tariff(UUID town) { return ru.neverland.integration.PoliciesAccess.tariff(town,repository.tariff(town,plugin.getConfig().getDouble("tariffs.default-percent",0)),maxTariff()); }
    public double maxTariff() { return plugin.getConfig().getDouble("tariffs.maximum-percent", 20); }
    public boolean setTariff(Town town, double percent) {
        double max = maxTariff();
        if (town == null || percent < 0 || percent > max) return false;
        repository.setTariff(town.getUUID(), cents(percent)); saveNow(); return true;
    }
    public int marketLevel(Town town) { return town == null ? 0 : builds.marketLevel(town.getUUID()); }
    public int routeLimit(Town town) {
        if (town == null) return 0;
        int limit = plugin.getConfig().getInt("market.routes-by-level." + marketLevel(town), 0);
        limit += builds.projectLevel(town.getUUID(), "rhodes_colossus")
                * plugin.getConfig().getInt("market.colossus-extra-routes", 2);
        limit += builds.projectLevel(town.getUUID(), "crystal_palace")
                * plugin.getConfig().getInt("market.crystal-palace-extra-routes", 2);
        return limit;
    }
    public int activeCount(Town town) { return town == null ? 0 : repository.caravans(town.getUUID()).size(); }
    public boolean hasRouteSlot(Town town) { return activeCount(town) < routeLimit(town); }

    private long warned;
    private void warn(Caravan c,Exception ex){if(System.currentTimeMillis()-warned>60000){warned=System.currentTimeMillis();plugin.getLogger().log(java.util.logging.Level.WARNING,"Караван "+c.id()+" ожидает восстановления: "+c.settlement(),ex);}}
    private void tick() {
        if(!repository.writable())return;
        long now=System.currentTimeMillis();
        for(var offer:new ArrayList<>(repository.offers()))if(now>=offer.expiresAt())repository.remove(offer);
        for(var c:new ArrayList<>(repository.allCaravans()))try{
            if(!repository.writable())return;
            if(c.terminal()){cleanup(c);continue;}
            if(c.settlement().equals("LEGACY_REVIEW"))continue;
            if(c.settlement().equals("ACTIVE")){
                if(c.timer().paused())continue;
                if(towny.town(c.buyerId())==null){cancelCaravan(c);continue;}
                if(!c.incidentHandled()&&now>=c.incidentAt()){
                    c.incidentHandled(true);if(c.shouldDelay()){long delay=Math.max(1,plugin.getConfig().getLong("routes.delay.seconds",300))*1000;c.delay(delay);repository.save();if(plugin.getConfig().getBoolean("announcements.delay",true))announceBoth(c,"caravan-delayed",definition(c),Map.of("time",TimeUtil.format(delay)));}repository.changed();
                }
                if(now>=c.arrivesAt()){c.settlement("DELIVERING");repository.save();}
            }
            advance(c);
        }catch(Exception ex){warn(c,ex);}
        if(repository.writable()){retryCredits();repository.saveIfDirty();}
    }
    private boolean deliver(Caravan c){
        if(c.settlement().equals("ACTIVE")){c.settlement("DELIVERING");repository.save();}
        if(!java.util.Set.of("DELIVERING","PAYING").contains(c.settlement()))return false;
        try{advance(c);if(c.settlement().equals("PAYING"))advance(c);return c.terminal();}catch(Exception ex){warn(c,ex);return false;}
    }
    private String creditDescription(Caravan c,UUID recipient,double amount,String kind){return "Караван "+c.id()+" "+kind+" город "+recipient+": "+amount;}
    private void advance(Caravan c)throws Exception {
        if(!repository.writable())throw new IllegalStateException("Хранилище торговли недоступно");
        if(c.settlement().equals("PREPARED") && taxes.tradeBlocked(c.sellerId(),c.buyerId())
                && repository.effects().state(c.operation("debit"))==ru.neverland.core.EffectJournal.State.READY){c.settlement("RETURNING");repository.save();}
        audit(c,null);
        CaravanProcessor.advance(c,new CaravanProcessor.Store(){public void save(){repository.save();audit(c,null);}public void finish(Caravan value,CaravanStatus status){finishHistory(value,status);}},new CaravanProcessor.Gateway(){
            public boolean reserve(Caravan value)throws Exception{return warehouse.transfer(value.operation("take"),value.sellerId(),value.cargoItem(),value.totalCargo(),false).status()==WarehouseBridge.Status.SUCCESS;}
            public boolean debit(Caravan value)throws Exception{return repository.effects().execute(value.operation("debit"),"Списание каравана "+value.id()+" город "+value.buyerId()+": "+value.escrow(),()->economy.transfer(towny.town(value.buyerId()),value.escrow(),definition(value),"debit",value.operation("debit"),false));}
            public boolean deliver(Caravan value)throws Exception{return warehouse.transfer(value.operation("delivery"),value.buyerId(),value.cargoItem(),value.remainingCargo(),true).status()==WarehouseBridge.Status.SUCCESS;}
            public boolean credit(Caravan value,UUID recipient,double amount,String kind)throws Exception{
                if(amount<=0)return true;UUID id=value.operation(kind+":"+recipient);
                boolean paid=repository.effects().execute(id,creditDescription(value,recipient,amount,kind),()->economy.transfer(towny.town(recipient),amount,definition(value),kind,id,true));
                if(paid)ru.neverland.core.AuditTrail.record(plugin,kind+":"+recipient,value.id().toString(),kind.equals("tariff")?"TRADE_TARIFF":kind.equals("refund")?"TRADE_REFUND":"TRADE_PAYMENT","COMPLETED",ru.neverland.core.AuditRecord.Party.system(),kind.equals("refund")?new ru.neverland.core.AuditRecord.Party("ESCROW",value.id().toString(),"Резерв каравана",value.buyerId().toString()):ru.neverland.core.AuditTrail.town(value.buyerId()),ru.neverland.core.AuditTrail.town(recipient),"",0,ru.neverland.core.AuditTrail.money(amount),"effect="+id);
                return paid;
            }
            public boolean sourceTaken(Caravan value)throws Exception{return value.legacyFunded()||warehouse.transferred(value.operation("take"),value.sellerId());}
            public boolean funded(Caravan value){return value.legacyFunded()||repository.effects().state(value.operation("debit"))==ru.neverland.core.EffectJournal.State.DONE;}
            public boolean returnStock(Caravan value)throws Exception{return warehouse.transfer(value.operation("return"),value.sellerId(),value.cargoItem(),value.remainingCargo(),true).status()==WarehouseBridge.Status.SUCCESS;}
        });
    }
    private void finishHistory(Caravan c,CaravanStatus status){
        c.settlement(status==CaravanStatus.COMPLETED?"COMPLETE":"CANCELLED");
        repository.addHistory(new TradeHistory(c.id(),c.sellerId(),c.buyerId(),c.exportId(),c.totalCargo(),c.basePrice(),c.tariffs().values().stream().mapToDouble(Double::doubleValue).sum(),System.currentTimeMillis(),status),plugin.getConfig().getInt("trade.history-limit-per-town",30));
        repository.save();
        audit(c,null);
        if(status==CaravanStatus.COMPLETED&&plugin.getConfig().getBoolean("announcements.arrival",true))announceBoth(c,"caravan-arrived",definition(c),Map.of());
    }
    private boolean audit(Caravan c,UUID actor){return ru.neverland.core.AuditTrail.record(plugin,c.settlement(),c.id().toString(),"CARAVAN_DEAL",c.settlement(),actor==null?ru.neverland.core.AuditRecord.Party.unknown():ru.neverland.core.AuditTrail.player(actor),ru.neverland.core.AuditTrail.town(c.sellerId()),ru.neverland.core.AuditTrail.town(c.buyerId()),ru.neverland.core.AuditTrail.item(c.cargoItem()),c.totalCargo(),ru.neverland.core.AuditTrail.money(c.basePrice()),"export="+c.exportId()+"; escrow="+c.escrow()+"; tariffs="+new java.util.TreeMap<>(c.tariffs())+"; legacy="+c.legacyFunded());}
    private void cleanup(Caravan c)throws Exception {
        ru.neverland.core.AuditTrail.require(audit(c,null));
        warehouse.acknowledge(c.operation("take"),c.sellerId());warehouse.acknowledge(c.operation("return"),c.sellerId());warehouse.acknowledge(c.operation("delivery"),c.buyerId());
        repository.remove(c);repository.save();pruneEffects();
    }
    private void pruneEffects()throws Exception{
        java.util.Set<UUID> needed=new java.util.HashSet<>();for(var c:repository.allCaravans()){needed.add(c.operation("debit"));needed.add(c.operation("seller:"+c.sellerId()));needed.add(c.operation("refund:"+c.buyerId()));c.tariffs().keySet().forEach(t->needed.add(c.operation("tariff:"+t)));}
        repository.pendingCredits().keySet().forEach(t->needed.add(ru.neverland.core.EffectJournal.id("legacy-trade-credit:"+t)));repository.effects().retain(needed);
    }
    public void resolveCaravan(String id,String decision)throws Exception{
        var c=repository.findCaravan(id);if(c==null||!c.settlement().equals("LEGACY_REVIEW"))throw new IllegalArgumentException("Нет старого каравана, ожидающего сверки");
        if(decision.equals("legacy-active")){if(c.remainingCargo()<1)throw new IllegalArgumentException("Груз уже выгружен: используйте legacy-delivered или legacy-complete");c.settlement("ACTIVE");}
        else if(decision.equals("legacy-complete")){finishHistory(c,CaravanStatus.COMPLETED);return;}
        else if(decision.equals("legacy-delivered")){
            c.settlement("PAYING");
            if(c.basePrice()>0)repository.effects().review(c.operation("seller:"+c.sellerId()),creditDescription(c,c.sellerId(),c.basePrice(),"seller"));
            for(var t:c.tariffs().entrySet())if(t.getValue()>0)repository.effects().review(c.operation("tariff:"+t.getKey()),creditDescription(c,t.getKey(),t.getValue(),"tariff"));
        }else throw new IllegalArgumentException("Решение: legacy-active, legacy-delivered или legacy-complete");
        repository.save();
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
    private void retryCredits(){
        var fallback=registry.all().stream().findFirst().orElse(null);if(fallback==null)return;
        for(var entry:repository.pendingCredits().entrySet())try{
            UUID id=ru.neverland.core.EffectJournal.id("legacy-trade-credit:"+entry.getKey());
            if(repository.effects().state(id)==ru.neverland.core.EffectJournal.State.PENDING)continue;
            if(repository.effects().execute(id,"Старое зачисление городу "+entry.getKey()+": "+entry.getValue(),()->economy.transfer(towny.town(entry.getKey()),entry.getValue(),fallback,"pending",id,true))){repository.clearPending(entry.getKey());repository.save();}
        }catch(Exception ex){plugin.getLogger().warning("Старое зачисление ожидает сверки: "+entry.getKey()+" "+ex.getMessage());}
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
        for (Player player : Bukkit.getOnlinePlayers()) { Town playerTown = towny.town(player); if (town.equals(playerTown)) player.sendMessage(text); }
    }
    private void saveNow() { repository.save(); }
    private double cents(double value) { return TradeMath.cents(value); }

    public ExportRegistry registry() { return registry; }
    public TradeRepository repository() { return repository; }
    public EconomyService economy() { return economy; }
    public TradeOffer offer(String id) { return repository.findOffer(id); }
    public Caravan caravan(String id) { return repository.findCaravan(id); }
    public List<TradeOffer> incoming(Town town) { return town == null ? List.of() : repository.incoming(town.getUUID()); }
    public List<TradeOffer> outgoing(Town town) { return town == null ? List.of() : repository.outgoing(town.getUUID()); }
    public List<Caravan> caravans(Town town) { return town == null ? List.of() : repository.caravans(town.getUUID()); }
    public List<TradeHistory> history(Town town) { return town == null ? List.of() : repository.history(town.getUUID()); }
    public ExportDefinition definition(TradeOffer offer) { return offer == null ? null : registry.get(offer.exportId()); }
    public ExportDefinition definitionOf(Caravan caravan) { return caravan == null ? null : definition(caravan); }

    public java.util.List<ru.neverland.core.ActivityAdmin.Target> adminTargets() {
        ru.neverland.core.ApiServices.primaryThread();
        return repository.caravans().stream().map(c -> new ru.neverland.core.ActivityAdmin.Target(c.id().toString(),c.exportId()+" / "+c.sellerId()+" → "+c.buyerId()+" / "+c.settlement(),
                c.settlement().equals("ACTIVE") ? ru.neverland.core.ActivityAdmin.TIMED : java.util.Set.of("status"), (action,minutes) -> {
            ru.neverland.core.ApiServices.primaryThread();
            if(action.equals("status"))return c.id()+" | "+c.exportId()+" / "+c.sellerId()+" → "+c.buyerId()+" / "+c.settlement()+" | "+c.timer().describe(System.currentTimeMillis());
            if(!(c.settlement().equals("ACTIVE")))throw new IllegalStateException("Задача уже завершается или ожидает расчёта; используйте штатную сверку");
            if(action.equals("cancel")){var result=cancelCaravan(c);if(result!=CancelResult.SUCCESS&&result!=CancelResult.WAREHOUSE_BUSY)throw new IllegalStateException("Возврат требует проверки: "+result); return result==CancelResult.WAREHOUSE_BUSY?"Отмена сохранена без провала; возврат груза ожидает доступности склада":"Караван отменён без провала; возврат завершён";}
            var before=c.timer();long oldDeparture=c.departedAt(),oldIncident=c.incidentAt();
            try{c.editTimer(action,minutes,System.currentTimeMillis()); repository.changed();if(!repository.save())throw new IllegalStateException("Караван не сохранён");}
            catch(Exception ex){c.timer(before);c.travelTimes(oldDeparture,oldIncident); throw ex;}
            return c.id()+" | "+c.timer().describe(System.currentTimeMillis());
        })).toList();
    }

}

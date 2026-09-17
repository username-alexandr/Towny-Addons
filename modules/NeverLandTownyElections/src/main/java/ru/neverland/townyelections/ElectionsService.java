package ru.neverland.townyelections;

import java.time.Instant;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.CitizensAccess;
import ru.neverland.townyelections.api.TownyElectionsApi;

public final class ElectionsService implements TownyElectionsApi {
    private final NeverLandTownyElections plugin; private final ElectionsRepository repository;
    private ElectionsSettings settings;
    private final Map<UUID,String> problems = new HashMap<>();
    public ElectionsService(NeverLandTownyElections plugin, ElectionsRepository repository, ElectionsSettings settings) {
        this.plugin=plugin;this.repository=repository;this.settings=settings;
    }
    public ElectionsSettings settings() { return settings; }
    public void settings(ElectionsSettings next) throws Exception { validateCatalog(next); settings=next; }
    public static void validateCatalog(ElectionsSettings settings) throws Exception {
        var catalog=ElectionIntegrations.catalog();
        for(var entry:settings.seats().entrySet()) if(!entry.getKey().equals("mayor") && catalog.getOrDefault(entry.getKey(),0)<entry.getValue()) throw new IllegalArgumentException("Governance: не хватает мест " + entry.getKey());
    }
    private void gate() {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Выборы требуют основного потока сервера.");
        if(!repository.healthy() || !CitizensAccess.available())throw new IllegalStateException("Выборы приостановлены: недоступен журнал или API гражданства.");
    }
    @Override public boolean healthy() { return Bukkit.isPrimaryThread() && repository.healthy() && CitizensAccess.available(); }
    @Override public boolean elective(UUID id) {
        gate();var town=TownyAPI.getInstance().getTown(id);if(town==null)throw new IllegalArgumentException("Unknown town");
        try{return democratic(town);}catch(Exception ex){throw new IllegalStateException(ex);}
    }
    public static Town ownTown(UUID resident) { var r=TownyAPI.getInstance().getResident(resident);return r==null?null:r.getTownOrNull(); }
    public static boolean eligible(Town town, UUID resident, String right) { return town!=null && town.hasResident(resident) && CitizensAccess.allows(town.getUUID(),resident,right); }
    public Election state(Town town) throws Exception {
        gate(); var e=repository.get(town.getUUID());
        if(e==null) { e=new Election(town.getUUID(),System.currentTimeMillis()+settings.initialDelay());repository.put(e); } return e;
    }
    public String form(Town town) throws Exception { return ElectionIntegrations.form(town.getUUID()); }
    private boolean democratic(Town town) throws Exception { return form(town).equals("DEMOCRACY") || !settings.authoritarian(); }
    public void recover() throws Exception {
        for(UUID town:repository.towns()) {
            var e=repository.get(town);if(e.phase==Election.Phase.APPLYING) {
                e.phase=Election.Phase.REVIEW;e.detail="Сервер остановился во время применения. Администратор должен проверить и выполнить resume или abort.";repository.put(e);
            }
        }
    }
    public void tick() {
        try { gate(); ElectionIntegrations.catalog(); }
        catch(Exception ex) { report(new UUID(0,0),ex);return; }
        for(Town town:TownyAPI.getInstance().getTowns()) try { advance(town,System.currentTimeMillis()); problems.remove(town.getUUID()); }
        catch(Exception | LinkageError ex) { report(town.getUUID(),ex); }
    }
    private void report(UUID town,Throwable ex) {
        String detail=ex.getClass().getSimpleName()+": "+ex.getMessage();
        if(!detail.equals(problems.put(town,detail)))plugin.getLogger().warning("Выборы " +town+" приостановлены: "+detail);
    }
    public void advance(Town town,long now) throws Exception {
        gate();var e=state(town);
        if(e.adminPausedAt>0)return;
        if(!democratic(town)) {
            if(e.phase==Election.Phase.NOMINATION || e.phase==Election.Phase.VOTING)finish(e,Election.Phase.CANCELLED,"Кампания отменена: форма правления " +form(town),now);
            return;
        }
        if(!e.active()) { if(now>=e.next)start(town,now);return; }
        if(e.phase==Election.Phase.NOMINATION && now>=e.nominationEnd) {
            // After downtime residents still get a full voting window.
            e.phase=Election.Phase.VOTING;e.votingEnd=Math.addExact(now,e.votingDuration);repository.put(e);
            broadcast(town,"Началось голосование. /t elections");return;
        }
        if(e.phase==Election.Phase.VOTING && now>=e.votingEnd) {
            var voters=new HashSet<UUID>();var candidates=new HashSet<UUID>();
            town.getResidents().forEach(r->{ if(eligible(town,r.getUUID(),"VOTE"))voters.add(r.getUUID());if(eligible(town,r.getUUID(),"HOLD_OFFICE"))candidates.add(r.getUUID()); });
            e.tally(voters,candidates);e.governanceBefore=ElectionIntegrations.receipt(town.getUUID());
            e.phase=Election.Phase.APPLYING;repository.put(e);
            try { apply(town,e,now); }
            catch(Exception ex) { e.phase=Election.Phase.REVIEW;e.detail="Результат сохранён, применение требует проверки: "+ex.getMessage();repository.put(e);throw ex; }
        }
    }
    public void start(Town town,long now) throws Exception {
        gate();if(!democratic(town))throw new IllegalArgumentException("При этой форме правления выборы отключены.");
        var old=state(town);if(old.active())throw new IllegalArgumentException("Кампания уже идёт или требует проверки.");
        validateCatalog(settings);if(town.getMayor()==null)throw new IllegalArgumentException("У города нет мэра.");
        var e=new Election(town.getUUID(),Math.addExact(now,settings.interval()));e.phase=Election.Phase.NOMINATION;e.start=now;
        e.nominationDuration=settings.nomination();e.nominationEnd=Math.addExact(now,settings.nomination());e.votingDuration=settings.voting();e.interval=settings.interval();e.quorum=settings.quorum();
        e.originalMayor=town.getMayor().getUUID();e.seats.putAll(settings.seats());e.history.addAll(old.history);
        town.getResidents().forEach(r->{if(eligible(town,r.getUUID(),"VOTE"))e.electorate.add(r.getUUID());});
        repository.put(e);broadcast(town,"Открыто выдвижение кандидатов. /t elections");
    }
    public void nominate(Player player,String race) throws Exception {
        gate();var town=requireTown(player); if(!player.hasPermission("townyelections.candidate") || !eligible(town,player.getUniqueId(),"HOLD_OFFICE"))throw new IllegalArgumentException("Нет права занимать должность.");
        requireDemocracy(town);var e=state(town);e.nominate(player.getUniqueId(),race,System.currentTimeMillis());repository.put(e);
    }
    public void withdraw(Player player) throws Exception { gate();var town=requireTown(player);requireDemocracy(town);var e=state(town);e.withdraw(player.getUniqueId(),System.currentTimeMillis());repository.put(e); }
    public void vote(Player player,String race,List<UUID> choices) throws Exception {
        gate();var town=requireTown(player);requireDemocracy(town);
        if(!player.hasPermission("townyelections.vote") || !eligible(town,player.getUniqueId(),"VOTE"))throw new IllegalArgumentException("Гражданство не даёт права голоса.");
        for(UUID id:choices)if(!eligible(town,id,"HOLD_OFFICE"))throw new IllegalArgumentException("Один из кандидатов больше не имеет права занять должность.");
        var e=state(town);e.vote(player.getUniqueId(),race,choices,System.currentTimeMillis());repository.put(e);
    }
    public void requireDemocracy(Town town) throws Exception { if(!democratic(town))throw new IllegalArgumentException("При этой форме правления выборы отключены."); }
    public static Town requireTown(Player player) {var town=ownTown(player.getUniqueId());if(town==null)throw new IllegalArgumentException("Вы не состоите в городе.");return town;}
    private void apply(Town town,Election e,long now) throws Exception {
        requireDemocracy(town);var mayor=e.winners.getOrDefault("mayor",List.of());
        for(var ids:e.winners.values())for(UUID id:ids)if(!eligible(town,id,"HOLD_OFFICE"))throw new IllegalStateException("Избранный житель утратил право на должность.");
        UUID current=town.getMayor()==null?null:town.getMayor().getUUID();
        if(!mayor.isEmpty() && !Objects.equals(current,e.originalMayor) && !Objects.equals(current,mayor.get(0)))throw new IllegalStateException("Мэр был изменён вне Elections.");
        var offices=new LinkedHashMap<>(e.winners);offices.remove("mayor");
        String receipt=ElectionIntegrations.receipt(town.getUUID());
        if(!receipt.equals(e.governanceBefore) && !receipt.equals(e.id.toString()))throw new IllegalStateException("Обнаружены более новые итоги Governance.");
        if(!offices.isEmpty()) {
            if(!receipt.equals(e.id.toString()))ElectionIntegrations.apply(town.getUUID(),e.id,Map.copyOf(offices));
            var actual=ElectionIntegrations.holders(town.getUUID());
            for(var entry:offices.entrySet())if(!new HashSet<>(entry.getValue()).equals(new HashSet<>(actual.getOrDefault(entry.getKey(),List.of()))))throw new IllegalStateException("Должности изменены после применения.");
        }
        if(!mayor.isEmpty()) {
            if(!mayor.get(0).equals(current))town.setMayor(TownyAPI.getInstance().getResident(mayor.get(0)));
            if(town.getMayor()==null || !mayor.get(0).equals(town.getMayor().getUUID()))throw new IllegalStateException("Towny не сменил мэра.");
            if(!TownyUniverse.getInstance().getDataSource().saveTown(town))throw new IllegalStateException("Towny не принял сохранение города.");
        }
        finish(e,Election.Phase.COMPLETE,"Результаты применены. При ничьей или отсутствии кворума состав сохранён.",now);
        broadcast(town,"Выборы завершены. Результаты: /t elections results");
    }
    public void resume(Town town,UUID id) throws Exception {
        gate();var e=state(town);if(e.phase!=Election.Phase.REVIEW || !e.id.equals(id))throw new IllegalArgumentException("Нужен точный ID кампании в состоянии REVIEW.");
        e.phase=Election.Phase.APPLYING;repository.put(e);
        try { apply(town,e,System.currentTimeMillis()); }
        catch(Exception ex){e.phase=Election.Phase.REVIEW;e.detail="Повторное применение остановлено: "+ex.getMessage();repository.put(e);throw ex;}
    }
    public void abort(Town town,UUID id,String actor) throws Exception {
        gate();var e=state(town);if(!e.active() || !e.id.equals(id))throw new IllegalArgumentException("Нужен точный ID активной кампании.");
        // Explicit administrative closure keeps already applied offices/mayor, never rolls them back.
        e.winners.clear();finish(e,Election.Phase.CANCELLED,"Закрыто администратором "+actor+". Текущие мэр и должности сохранены.",System.currentTimeMillis());
    }
    private void finish(Election e,Election.Phase phase,String message,long now) throws Exception {
        e.adminPausedAt=0;e.phase=phase;e.detail=message;if(e.next<=now)e.next=Math.addExact(now,e.interval);
        e.history.add(0,Instant.ofEpochMilli(now)+" | "+e.id+" | "+phase+" | "+message+" "+e.results+" "+e.winners);
        while(e.history.size()>20)e.history.remove(e.history.size()-1);repository.put(e);
    }
    public static void broadcast(Town town,String message) { for(var r:town.getResidents()){var p=Bukkit.getPlayer(r.getUUID());if(p!=null)p.sendMessage("§6[Выборы] §f"+message);} }
    @Override public Map<String,String> snapshot(UUID townId) {
        gate();var town=TownyAPI.getInstance().getTown(townId);if(town==null)throw new IllegalArgumentException("Unknown town");
        try {var e=state(town);return Map.of("id",e.id.toString(),"phase",e.phase.name(),"government",form(town),"next",Long.toString(e.next),
                "nomination_end",Long.toString(e.nominationEnd),"voting_end",Long.toString(e.votingEnd),"detail",e.adminPausedAt>0?"Пауза • "+e.timer().describe(System.currentTimeMillis()):e.detail,"results",e.results.toString(),"winners",e.winners.toString());}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    public List<ru.neverland.core.ActivityAdmin.Target> adminTargets(){
        gate();return repository.towns().stream().map(repository::get).filter(Election::active).map(value->new ru.neverland.core.ActivityAdmin.Target(value.id.toString(),"Город "+value.town+" / "+value.phase+" / кандидатов "+value.candidates.size(),
            Set.of(Election.Phase.NOMINATION,Election.Phase.VOTING).contains(value.phase)?ru.neverland.core.ActivityAdmin.TIMED:Set.of("status"),(action,minutes)->{
                gate();var e=repository.get(value.town);if(e==null||!e.id.equals(value.id))throw new IllegalStateException("Кампания изменилась");
                if(action.equals("status"))return e.id+" | "+e.phase+" | "+(Set.of(Election.Phase.NOMINATION,Election.Phase.VOTING).contains(e.phase)?e.timer().describe(System.currentTimeMillis()):e.detail);
                if(!Set.of(Election.Phase.NOMINATION,Election.Phase.VOTING).contains(e.phase))throw new IllegalStateException("Итоги уже применяются; используйте штатный resume/abort с точным UUID");
                if(action.equals("cancel")){e.adminPausedAt=0;finish(e,Election.Phase.CANCELLED,"Административная отмена без смены власти",System.currentTimeMillis());return "Кампания отменена; мэр и должности сохранены";}
                e.timer(e.timer().edit(action,minutes,System.currentTimeMillis()));repository.put(e);return e.id+" | "+e.timer().describe(System.currentTimeMillis());
            })).toList();
    }

}

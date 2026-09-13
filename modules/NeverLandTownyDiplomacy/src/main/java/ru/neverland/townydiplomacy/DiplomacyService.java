package ru.neverland.townydiplomacy;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.io.IOException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.neverland.core.*;
import ru.neverland.townydiplomacy.api.TownyDiplomacyApi;
import static ru.neverland.townydiplomacy.Treaty.*;

public final class DiplomacyService implements TownyDiplomacyApi {
    private final DiplomacyRepository repository;private DiplomacySettings settings;private boolean configured;
    public DiplomacyService(DiplomacyRepository repository,DiplomacySettings settings,boolean configured) { this.repository=repository;this.settings=settings;this.configured=configured; }
    private void primary() { if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Нужен основной поток сервера"); }
    @Override public boolean healthy() { primary();return configured&&repository.writable(); }
    private void ready()throws IOException { if(!healthy())throw new IOException("Реестр дипломатии недоступен; действия остановлены"); }
    public DiplomacyRepository repository() { return repository; }
    public DiplomacySettings settings() { return settings; }
    public void settings(DiplomacySettings value) { primary();settings=value;configured=true; }
    public Town ownTown(UUID player) { var r=TownyAPI.getInstance().getResident(player);return r==null?null:r.getTownOrNull(); }
    private boolean exists(UUID id) { return id!=null&&TownyAPI.getInstance().getTown(id)!=null; }
    public boolean manages(CommandSender sender,Town town,TreatyType type) {
        primary();if(town==null)return false;
        if(sender.hasPermission("neverlandtownydiplomacy.admin"))return true;
        if(!(sender instanceof Player p)||!sender.hasPermission("neverlandtownydiplomacy.manage")||!town.equals(ownTown(p.getUniqueId()))
                ||!CitizensAccess.allows(town.getUUID(),p.getUniqueId(),"HOLD_OFFICE"))return false;
        if(town.getMayor()!=null&&town.getMayor().getUUID().equals(p.getUniqueId()))return true;
        return CouncilAccess.allows(p,town.getUUID(),type.bilateral()?"agreements":"sanctions");
    }
    private static String actor(CommandSender sender) { return sender instanceof Player p?p.getUniqueId().toString():"CONSOLE:"+sender.getName(); }
    private void requireManager(CommandSender sender,Town town,TreatyType type) {
        if(!manages(sender,town,type))throw new IllegalArgumentException("Доступно мэру или министру внешней политики своего города");
    }
    private DiplomacyRepository.Audit audit(Treaty t,String actor,String action,long now) { return new DiplomacyRepository.Audit(t.id(),t.first(),t.second(),now,actor,action); }
    public Treaty offer(CommandSender sender,Town first,Town second,TreatyType type,int days,SanctionScope sanction,String reason)throws IOException {
        primary();requireManager(sender,first,type);ready();maintain(System.currentTimeMillis());
        if(second==null||first.equals(second)||first.getMayor()==null||second.getMayor()==null)throw new IllegalArgumentException("Выберите другой существующий город с мэром");
        if(days<1||days>settings.maximumDays())throw new IllegalArgumentException("Срок: от 1 до "+settings.maximumDays()+" дней");
        long now=System.currentTimeMillis();
        for(var t:List.of(first,second))if(repository.all().values().stream().filter(a->a.party(t.getUUID())&&a.open(now)).count()>=settings.maxOpen())throw new IllegalArgumentException("Достигнут лимит открытых договоров города "+t.getName());
        String clean=org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&',reason)).replace('§',' ').trim();
        long duration=Math.multiplyExact(days,86_400_000L);
        var treaty=new Treaty(UUID.randomUUID(),type,first.getUUID(),second.getUUID(),first.getMayor().getUUID(),second.getMayor().getUUID(),actor(sender),clean,
                type.bilateral()?Phase.PENDING:Phase.ACTIVE,now,Math.addExact(now,settings.proposalMillis()),duration,type.bilateral()?0:now,type.bilateral()?0:Math.addExact(now,duration),0,
                settings.noticeMillis(),type==TreatyType.TRADE?settings.tradeDiscountBasisPoints():0,sanction);
        DiplomacyRules.validateNew(repository.all().values(),treaty,now);
        var next=new LinkedHashMap<>(repository.all());next.put(treaty.id(),treaty);
        repository.commit(next,List.of(audit(treaty,actor(sender),type.bilateral()?"OFFER":"IMPOSE",now)),List.of());return treaty;
    }
    public Treaty change(CommandSender sender,Town town,UUID id,String action)throws IOException {
        primary();ready();maintain(System.currentTimeMillis());
        var t=repository.all().get(id);if(t==null)throw new IllegalArgumentException("Договор не найден; укажите полный ID");
        requireManager(sender,town,t.type());if(!t.party(town.getUUID()))throw new IllegalArgumentException("Город не является стороной договора");
        long now=System.currentTimeMillis();Treaty next;
        switch(action) {
            case "accept" -> {
                if(!t.second().equals(town.getUUID())||!t.type().bilateral())throw new IllegalArgumentException("Принять может только приглашённый город");
                if(!sameMayors(t))throw new IllegalArgumentException("Мэр одной из сторон сменился; нужно новое предложение");
                DiplomacyRules.validateNew(repository.all().values(),t,now);next=t.accept(now);
            }
            case "reject" -> { if(!t.pending(now)||!t.second().equals(town.getUUID()))throw new IllegalArgumentException("Отклонить можно входящее предложение");next=t.end(); }
            case "end" -> {
                if(!t.type().bilateral()&&!t.first().equals(town.getUUID()))throw new IllegalArgumentException("Ограничение снимает только город, который его ввёл");
                if(t.pending(now)){if(!t.first().equals(town.getUUID()))throw new IllegalArgumentException("Используйте reject для входящего предложения");next=t.end();}
                else next=t.terminate(now,t.noticePeriod());
            }
            default -> throw new IllegalArgumentException("Действие: accept, reject или end");
        }
        var values=new LinkedHashMap<>(repository.all());values.put(id,next);repository.commit(values,List.of(audit(t,actor(sender),action.toUpperCase(Locale.ROOT),now)),List.of());return next;
    }
    private boolean sameMayors(Treaty t) {
        var a=TownyAPI.getInstance().getTown(t.first());var b=TownyAPI.getInstance().getTown(t.second());
        return a!=null&&b!=null&&a.getMayor()!=null&&b.getMayor()!=null&&a.getMayor().getUUID().equals(t.firstMayor())&&b.getMayor().getUUID().equals(t.secondMayor());
    }
    public void maintain(long now)throws IOException {
        primary();ready();var values=new LinkedHashMap<>(repository.all());var audit=new ArrayList<DiplomacyRepository.Audit>();
        for(var t:repository.all().values())if(t.phase()!=Phase.ENDED) {
            String reason=!exists(t.first())||!exists(t.second())?"TOWN_DELETED":t.phase()==Phase.PENDING&&!sameMayors(t)?"MAYOR_CHANGED":!t.open(now)?"EXPIRED":null;
            if(reason!=null){values.put(t.id(),t.end());audit.add(audit(t,"SYSTEM",reason,now));}
        }
        if(!audit.isEmpty())repository.commit(values,audit,List.of());
    }
    private List<Treaty> live() {
        long now=System.currentTimeMillis();return repository.all().values().stream().filter(t->t.active(now)&&exists(t.first())&&exists(t.second())).toList();
    }
    @Override public boolean tradeBlocked(UUID a,UUID b) { primary();if(a==null||b==null||a.equals(b))return false;return !healthy()||DiplomacyRules.tradeBlocked(live(),a,b,System.currentTimeMillis()); }
    @Override public boolean hostileBlocked(UUID a,UUID b) { primary();if(a==null||b==null||a.equals(b))return false;return !healthy()||DiplomacyRules.hostileBlocked(live(),a,b,System.currentTimeMillis()); }
    @Override public double tariffMultiplier(UUID a,UUID b,UUID tariffTown) { primary();if(!healthy())throw new IllegalStateException("Дипломатия недоступна");return DiplomacyRules.tariffMultiplier(live(),a,b,tariffTown,System.currentTimeMillis()); }
    @Override public UUID overlord(UUID town) { primary();if(!healthy())throw new IllegalStateException("Дипломатия недоступна");return DiplomacyRules.overlord(live(),town,System.currentTimeMillis()); }
    @Override public Set<UUID> defenders(UUID town) { primary();if(!healthy())throw new IllegalStateException("Дипломатия недоступна");return DiplomacyRules.defenders(live(),town,System.currentTimeMillis()); }
    @Override public Set<String> relations(UUID a,UUID b) {
        primary();if(!healthy())return Set.of("UNAVAILABLE");var result=new TreeSet<String>();
        for(var t:live())if(t.pair(a,b))result.add(t.type().name());return Set.copyOf(result);
    }
    @Override public List<Map<String,String>> treaties(UUID town) {
        primary();return repository.all().values().stream().filter(t->t.party(town)).sorted(Comparator.comparingLong(Treaty::created).reversed().thenComparing(Treaty::id)).map(t->{
            var m=new TreeMap<String,String>();m.put("id",t.id().toString());m.put("type",t.type().id());m.put("first",t.first().toString());m.put("second",t.second().toString());
            m.put("phase",t.phase().name());m.put("effective",String.valueOf(healthy()&&t.active(System.currentTimeMillis())&&exists(t.first())&&exists(t.second())));
            m.put("offerUntil",Long.toString(t.offerUntil()));m.put("duration",Long.toString(t.duration()));m.put("expires",Long.toString(t.expires()));m.put("noticeUntil",Long.toString(t.noticeUntil()));
            m.put("noticePeriod",Long.toString(t.noticePeriod()));m.put("discountBasisPoints",Integer.toString(t.discountBasisPoints()));m.put("sanction",t.sanction().name());m.put("reason",t.reason());return Map.copyOf(m);
        }).toList();
    }
    /** Record an allowed attack once per pair/cooldown; does not create a war or money transfer. */
    public Set<UUID> recordAttack(UUID attacker,UUID victim,String location)throws IOException {
        primary();ready();if(!exists(attacker)||!exists(victim)||attacker.equals(victim)||hostileBlocked(attacker,victim))return Set.of();
        var helpers=new HashSet<>(defenders(victim));helpers.remove(attacker);if(helpers.isEmpty())return Set.of();
        long now=System.currentTimeMillis();
        if(repository.incidents().stream().anyMatch(i->i.attacker().equals(attacker)&&i.victim().equals(victim)&&now-i.at()<settings.incidentCooldown()))return Set.of();
        var incident=new DiplomacyRepository.Incident(UUID.randomUUID(),attacker,victim,helpers,now,location);
        repository.commit(repository.all(),List.of(),List.of(incident));return Set.copyOf(helpers);
    }
}

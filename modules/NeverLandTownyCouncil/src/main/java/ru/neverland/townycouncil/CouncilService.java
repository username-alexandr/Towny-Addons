package ru.neverland.townycouncil;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import ru.neverland.core.CitizensAccess;
import ru.neverland.townycouncil.api.TownyCouncilApi;

public final class CouncilService implements TownyCouncilApi {
    private record Grant(Player player, PermissionAttachment attachment, Set<String> permissions) { }
    private final Plugin plugin;
    private final CouncilRepository repository;
    private CouncilSettings settings;
    private final AccessRepository access;
    private final Map<UUID, Grant> grants = new HashMap<>();
    public CouncilService(Plugin plugin, CouncilRepository repository, CouncilSettings settings, AccessRepository access) {
        this.plugin = plugin; this.repository = repository; this.settings = settings; this.access=access;
    }
    private void primary() { if (!ru.neverland.core.SuiteState.held()) throw new IllegalStateException("Нужен основной поток сервера"); }
    public CouncilRepository repository() { synchronized (ru.neverland.core.SuiteState.lock()) { return repository; } }
    public void settings(CouncilSettings value) { synchronized (ru.neverland.core.SuiteState.lock()) { primary(); settings = value; refreshAll(); } }
    @Override public boolean healthy() { synchronized (ru.neverland.core.SuiteState.lock()) { primary(); return repository.writable() && access.writable() && CitizensAccess.available(); } }
    public Town ownTown(UUID resident) { synchronized (ru.neverland.core.SuiteState.lock()) { var r = TownyAPI.getInstance().getResident(resident); return r == null ? null : r.getTownOrNull(); } }
    private boolean sameTerm(Appointment a) {
        var t = TownyAPI.getInstance().getTown(a.town());
        return t != null && t.getMayor() != null && a.mayor().equals(t.getMayor().getUUID()) && t.equals(ownTown(a.resident()));
    }
    public boolean active(Appointment a) { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); return a != null && healthy() && sameTerm(a) && CitizensAccess.allows(a.town(), a.resident(), "HOLD_OFFICE");
    } }
    public Appointment appointment(UUID town, MinisterRole role) { synchronized (ru.neverland.core.SuiteState.lock()) { return repository.all().get(town + "/" + role.id()); } }
    @Override public Map<String, UUID> holders(UUID town) { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); var result = new TreeMap<String, UUID>();
        repository.all().values().stream().filter(a -> a.town().equals(town) && active(a)).forEach(a -> result.put(a.role().id(), a.resident()));
        return Map.copyOf(result);
    } }
    @Override public String role(UUID town, UUID resident) { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); return repository.all().values().stream().filter(a -> a.town().equals(town) && a.resident().equals(resident) && active(a))
                .map(a -> a.role().id()).findFirst().orElse("");
    } }
    @Override public Set<String> permissions(String role) { synchronized (ru.neverland.core.SuiteState.lock()) { primary(); return settings.permissions().get(MinisterRole.parse(role)); } }
    @Override public boolean allows(UUID town, UUID resident, String permission) { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary();
        if (!healthy() || !Objects.equals(ownTown(resident),TownyAPI.getInstance().getTown(town)) || ownTown(resident)==null) return false;
        try {return decision(town,resident,permission).equals("ALLOW");}catch(IllegalArgumentException ex){
            String role=role(town,resident);return !role.isEmpty()&&permissions(role).contains(permission);
        }
    } }
    /** Editing access is deliberately mayor-only; OP, wildcard and console do not substitute for the mayor. */
    public boolean mayEditAccess(CommandSender actor,Town town) { synchronized(ru.neverland.core.SuiteState.lock()) {
        return actor instanceof Player p && town!=null && town.getMayor()!=null && town.equals(ownTown(p.getUniqueId()))
                && town.getMayor().getUUID().equals(p.getUniqueId());
    } }
    public AccessRules rules(Town town) { synchronized(ru.neverland.core.SuiteState.lock()) {
        if(town==null||town.getMayor()==null)throw new IllegalArgumentException("Город или мэр недоступен");
        var rules=access.cities().get(town.getUUID());return rules!=null&&rules.mayor().equals(town.getMayor().getUUID())?rules:AccessRules.empty(town.getMayor().getUUID());
    } }
    private boolean roleDefault(String role,TownAction action){
        if(role.isEmpty())return false;var nodes=permissions(role);
        return nodes.contains(action.permission())||!action.parent().isEmpty()&&nodes.contains(TownAction.PREFIX+action.parent());
    }
    @Override public String decision(UUID townId,UUID resident,String actionId) { synchronized(ru.neverland.core.SuiteState.lock()) {
        primary();TownAction action=TownAction.parse(actionId);Town town=TownyAPI.getInstance().getTown(townId);
        if(town==null||!town.equals(ownTown(resident)))return "DENY";
        if(town.getMayor()!=null&&town.getMayor().getUUID().equals(resident))return "ALLOW";
        if(!healthy())return "DENY";
        String role=role(townId,resident);Boolean override=rules(town).override(resident,role,action);
        if(override!=null)return override?"ALLOW":"DENY";
        return roleDefault(role,action)?"ALLOW":"DEFAULT";
    } }
    @Override public Map<String,String> actions(){ synchronized(ru.neverland.core.SuiteState.lock()) {
        var result=new LinkedHashMap<String,String>();for(var a:TownAction.values())result.put(a.id(),a.title());return Map.copyOf(result);
    } }
    @Override public Map<String,String> roleActions(UUID town,String role){ synchronized(ru.neverland.core.SuiteState.lock()) {
        MinisterRole.parse(role);var overrides=rules(TownyAPI.getInstance().getTown(town)).roles().getOrDefault(role,Map.of());return modes(overrides);
    } }
    @Override public Map<String,String> residentActions(UUID town,UUID resident){ synchronized(ru.neverland.core.SuiteState.lock()) {
        var t=TownyAPI.getInstance().getTown(town);if(t==null||!t.equals(ownTown(resident)))return Map.of();return modes(rules(t).residents().getOrDefault(resident,Map.of()));
    } }
    private static Map<String,String> modes(Map<String,Boolean> overrides){var result=new TreeMap<String,String>();for(var a:TownAction.values()){
        Boolean value=overrides.get(a.id());result.put(a.id(),value==null?"DEFAULT":value?"ALLOW":"DENY");}return Map.copyOf(result);
    }
    public void changeAccess(Player actor,Town town,UUID mayor,long revision,String role,UUID resident,String action,String mode)throws IOException { synchronized(ru.neverland.core.SuiteState.lock()) {
        primary();if(ru.neverland.core.SuiteState.frozen(plugin.getName())||!mayEditAccess(actor,town))throw new IllegalArgumentException("Менять доступ может только действующий мэр своего города");
        if(!healthy())throw new IOException("Права временно недоступны; изменения не сохранены");
        var current=rules(town);if(!current.mayor().equals(mayor)||current.revision()!=revision)throw new IllegalArgumentException("Настройки изменились. Откройте меню заново");
        if(resident!=null){if(!town.equals(ownTown(resident))||resident.equals(mayor))throw new IllegalArgumentException("Выберите другого жителя своего города");}
        else MinisterRole.parse(role);
        TownAction kind=TownAction.parse(action);Boolean value=switch(mode.toLowerCase(Locale.ROOT)){case "allow"->true;case "deny"->false;case "default"->null;default->throw new IllegalArgumentException("Режим: allow, deny, default");};
        var next=current.change(role,resident,kind,value);var cities=new HashMap<>(access.cities());cities.put(town.getUUID(),next);
        String detail=town.getUUID()+" / "+actor.getUniqueId()+" / "+(resident==null?"role:"+role:"resident:"+resident)+" / "+kind.id()+" / "+mode;
        var operation=UUID.randomUUID().toString();
        ru.neverland.core.AuditTrail.require(ru.neverland.core.AuditTrail.record(plugin,"ACCESS_CHANGE",operation,"COUNCIL_ACCESS","REQUESTED",
            new ru.neverland.core.AuditRecord.Party("MAYOR",actor.getUniqueId().toString(),actor.getName(),""),ru.neverland.core.AuditRecord.Party.unknown(),ru.neverland.core.AuditTrail.town(town.getUUID()),"",0,"0",detail));
        try{access.commit(cities,System.currentTimeMillis()+" "+detail);}catch(IOException e){clearGrants();throw e;}
        ru.neverland.core.AuditTrail.record(plugin,"ACCESS_SAVED",operation,"COUNCIL_ACCESS","DONE",
            new ru.neverland.core.AuditRecord.Party("MAYOR",actor.getUniqueId().toString(),actor.getName(),""),ru.neverland.core.AuditRecord.Party.unknown(),ru.neverland.core.AuditTrail.town(town.getUUID()),"",0,"0",detail);
        refreshAll();
    } }
    public List<String> accessHistory(){return access.history();}
    public void clearAccess(UUID town,UUID resident,String reason)throws IOException { synchronized(ru.neverland.core.SuiteState.lock()) {
        var current=access.cities().get(town);if(current==null)return;
        if(resident!=null&&!current.residents().containsKey(resident))return;
        var next=new HashMap<>(access.cities());if(resident==null)next.remove(town);else next.put(town,current.without(resident));
        access.commit(next,System.currentTimeMillis()+" "+town+" SYSTEM "+reason);refreshAll();
    } }
    private Set<String> effectiveNodes(Town town,UUID resident){
        if(town==null||!healthy())return Set.of();String role=role(town.getUUID(),resident);var nodes=new HashSet<String>();
        if(!role.isEmpty())nodes.addAll(permissions(role));nodes.removeIf(n->n.startsWith(TownAction.PREFIX));
        // Mayors already have their normal authority and must not acquire delegated attachments.
        if(town.getMayor()!=null&&town.getMayor().getUUID().equals(resident))return Set.copyOf(nodes);
        var rules=rules(town);for(var a:TownAction.values()){Boolean override=rules.override(resident,role,a);if(override!=null?override:roleDefault(role,a))nodes.add(a.permission());}
        return Set.copyOf(nodes);
    }
    public boolean manages(CommandSender sender, Town town) { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); if (town == null) return false;
        if (sender.hasPermission("neverlandtownycouncil.admin")) return true;
        return sender instanceof Player p && sender.hasPermission("neverlandtownycouncil.manage") && town.equals(ownTown(p.getUniqueId()))
                && town.getMayor() != null && town.getMayor().getUUID().equals(p.getUniqueId());
    } }
    public void appoint(CommandSender actor, Town town, MinisterRole role, UUID resident) throws IOException { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); if (!manages(actor, town)) throw new IllegalArgumentException("Назначать министров может мэр своего города");
        if (!healthy()) throw new IOException("Совет или гражданство недоступны; назначения остановлены");
        prune();
        if (!town.equals(ownTown(resident)) || town.getMayor() == null || town.getMayor().getUUID().equals(resident))
            throw new IllegalArgumentException("Выберите другого жителя своего города");
        if (!CitizensAccess.allows(town.getUUID(), resident, "HOLD_OFFICE")) throw new IllegalArgumentException("Гражданство не разрешает занимать должность");
        if (appointment(town.getUUID(), role) != null) throw new IllegalArgumentException("Должность занята. Сначала снимите действующего министра");
        if (repository.all().values().stream().anyMatch(a -> a.town().equals(town.getUUID()) && a.resident().equals(resident)))
            throw new IllegalArgumentException("Этот житель уже занимает министерскую должность");
        var a = new Appointment(town.getUUID(), role, resident, town.getMayor().getUUID(), System.currentTimeMillis(), actorId(actor));
        var next = new LinkedHashMap<>(repository.all()); next.put(a.key(), a);
        commit(next, List.of(audit(town.getUUID(), actorId(actor), "APPOINT " + role.id() + " " + resident))); refreshAll();
    } }
    public void dismiss(CommandSender actor, Town town, MinisterRole role) throws IOException { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); if (!manages(actor, town)) throw new IllegalArgumentException("Снимать министров может мэр своего города");
        var a = appointment(town.getUUID(), role);
        if (a == null) throw new IllegalArgumentException("Должность уже свободна");
        remove(x -> x.key().equals(a.key()), actorId(actor), "DISMISS");
    } }
    public void remove(Predicate<Appointment> filter, String actor, String reason) throws IOException { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); var next = new LinkedHashMap<>(repository.all()); var audit = new ArrayList<CouncilRepository.Audit>();
        next.values().removeIf(a -> { if (!filter.test(a)) return false;
            audit.add(audit(a.town(), actor, reason + " " + a.role().id() + " " + a.resident())); return true; });
        if (!audit.isEmpty()) commit(next, audit);
        refreshAll();
    } }
    private void commit(Map<String, Appointment> next, List<CouncilRepository.Audit> audit) throws IOException {
        try { repository.commit(next, audit); }
        catch (IOException ex) { clearGrants(); throw ex; }
    }
    public void prune() throws IOException { synchronized (ru.neverland.core.SuiteState.lock()) { remove(a -> !sameTerm(a), "SYSTEM", "TERM_OR_MEMBERSHIP_ENDED");
        for(var entry:List.copyOf(access.cities().entrySet())){
            var town=TownyAPI.getInstance().getTown(entry.getKey());
            if(town==null||town.getMayor()==null||!town.getMayor().getUUID().equals(entry.getValue().mayor()))clearAccess(entry.getKey(),null,"TERM_ENDED");
            else for(UUID resident:entry.getValue().residents().keySet())if(!town.equals(ownTown(resident)))clearAccess(entry.getKey(),resident,"LEFT_TOWN");
        } } }
    private static CouncilRepository.Audit audit(UUID town, String actor, String detail) { return new CouncilRepository.Audit(town, System.currentTimeMillis(), actor, detail); }
    private static String actorId(CommandSender sender) { return sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE:" + sender.getName(); }
    /** Called on join and every second. Only this plugin's attachment is ever removed. */
    public void refresh(Player player) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if(!ru.neverland.core.SuiteScheduler.owns(player)){ru.neverland.core.SuiteScheduler.player(plugin,player,this::refresh);return;}
        primary(); var town = ownTown(player.getUniqueId());
        var nodes = effectiveNodes(town,player.getUniqueId());
        var old = grants.get(player.getUniqueId());
        if (old != null && old.player() == player && old.permissions().equals(nodes)) return;
        release(player.getUniqueId());
        if (!nodes.isEmpty()) {
            var attachment = player.addAttachment(plugin);
            nodes.forEach(node -> attachment.setPermission(node, true));
            grants.put(player.getUniqueId(), new Grant(player, attachment, nodes));
        }
    } }
    public void refreshAll() { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); var players = new HashMap<UUID, Player>();
        grants.forEach((id, grant) -> players.put(id, grant.player()));
        Bukkit.getOnlinePlayers().forEach(p -> players.put(p.getUniqueId(), p)); players.values().forEach(this::refresh);
    } }
    public void release(UUID player) { synchronized (ru.neverland.core.SuiteState.lock()) {
        primary(); var old = grants.remove(player);
        if (old != null) ru.neverland.core.SuiteScheduler.cleanupPlayer(plugin,old.player(),p->{try{p.removeAttachment(old.attachment());}catch(IllegalArgumentException ignored){}});
    } }
    public void clearGrants() { synchronized (ru.neverland.core.SuiteState.lock()) { primary(); new ArrayList<>(grants.keySet()).forEach(this::release); } }
}

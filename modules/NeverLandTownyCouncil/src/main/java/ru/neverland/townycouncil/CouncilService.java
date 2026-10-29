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
    private DelegationService delegations;
    public void delegations(DelegationService value){delegations=value;}
    public DelegationService delegations(){return delegations;}
    public CouncilService(Plugin plugin, CouncilRepository repository, CouncilSettings settings, AccessRepository access) {
        this.plugin = plugin; this.repository = repository; this.settings = settings; this.access=access;
    }
    private void primary() { if (!ru.neverland.core.SuiteState.held()) throw new IllegalStateException("Нужен основной поток сервера"); }
    public CouncilRepository repository() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return repository; } }
    public void settings(CouncilSettings value) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { primary(); settings = value; refreshAll(); } }
    @Override public boolean healthy() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { primary(); return repository.writable() && access.writable() && CitizensAccess.available(); } }
    public Town ownTown(UUID resident) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { var r = TownyAPI.getInstance().getResident(resident); return r == null ? null : r.getTownOrNull(); } }
    private boolean sameTerm(Appointment a) {
        var t = TownyAPI.getInstance().getTown(a.town());
        return t != null && t.getMayor() != null && a.mayor().equals(t.getMayor().getUUID()) && t.equals(ownTown(a.resident()));
    }
    public boolean active(Appointment a) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        primary(); return a != null && healthy() && sameTerm(a) && CitizensAccess.allows(a.town(), a.resident(), "HOLD_OFFICE");
    } }
    public Appointment appointment(UUID town, MinisterRole role) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return repository.all().get(town + "/" + role.id()); } }
    @Override public Map<String, UUID> holders(UUID town) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        primary(); var result = new TreeMap<String, UUID>();
        repository.all().values().stream().filter(a -> a.town().equals(town) && active(a)).forEach(a -> result.put(a.role().id(), a.resident()));
        return Map.copyOf(result);
    } }
    @Override public String role(UUID town, UUID resident) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        primary(); return repository.all().values().stream().filter(a -> a.town().equals(town) && a.resident().equals(resident) && active(a))
                .map(a -> a.role().id()).findFirst().orElse("");
    } }
    @Override public Set<String> permissions(String role) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { primary(); return settings.permissions().get(MinisterRole.parse(role)); } }
    @Override public boolean allows(UUID town, UUID resident, String permission) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        primary();
        if (!healthy() || !Objects.equals(ownTown(resident),TownyAPI.getInstance().getTown(town)) || ownTown(resident)==null) return false;
        try {return decision(town,resident,permission).equals("ALLOW");}catch(IllegalArgumentException ex){
            String role=role(town,resident);return !role.isEmpty()&&permissions(role).contains(permission);
        }
    } }
    /** Editing access is deliberately mayor-only; OP, wildcard and console do not substitute for the mayor. */
    public boolean mayEditAccess(CommandSender actor,Town town) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {
        return actor instanceof Player p && town!=null && town.getMayor()!=null && town.equals(ownTown(p.getUniqueId()))
                && town.getMayor().getUUID().equals(p.getUniqueId());
    } }
    public AccessRules rules(Town town) { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {
        if(town==null||town.getMayor()==null)throw new IllegalArgumentException("Город или мэр недоступен");
        var rules=access.cities().get(town.getUUID());return rules!=null&&rules.mayor().equals(town.getMayor().getUUID())?rules:AccessRules.empty(town.getMayor().getUUID());
    } }
    private boolean roleDefault(String role,TownAction action){
        if(role.isEmpty())return false;var nodes=permissions(role);
        return nodes.contains(action.permission())||!action.parent().isEmpty()&&nodes.contains(TownAction.PREFIX+action.parent());
    }
    @Override public String decision(UUID townId,UUID resident,String actionId) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        primary();TownAction action=TownAction.parse(actionId);Town town=TownyAPI.getInstance().getTown(townId);
        if(town==null||!town.equals(ownTown(resident)))return "DENY";
        if(town.getMayor()!=null&&town.getMayor().getUUID().equals(resident))return "ALLOW";
        if(!healthy())return "DENY";
        String role=role(townId,resident);Boolean override=rules(town).override(resident,role,action);
        if(override!=null)return override?"ALLOW":"DENY";
        if(delegations!=null&&delegations.active(townId,resident,action))return "ALLOW";
        return roleDefault(role,action)?"ALLOW":"DEFAULT";
    } }
    @Override public Map<String,Long> temporaryRights(UUID town,UUID resident){try(var guard=ru.neverland.core.SuiteState.guard()){var result=new HashMap<String,Long>();if(delegations!=null)for(var grant:delegations.state().grants().values())if(grant.town().equals(town)&&grant.resident().equals(resident)&&delegations.active(town,resident,TownAction.parse(grant.action())))result.put(grant.action(),grant.expiresAt());return Map.copyOf(result);}}
    @Override public Map<String,String> actions(){ try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        var result=new LinkedHashMap<String,String>();for(var a:TownAction.values())result.put(a.id(),a.title());return Map.copyOf(result);
    } }
    @Override public Map<String,String> roleActions(UUID town,String role){ try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
        MinisterRole.parse(role);var overrides=rules(TownyAPI.getInstance().getTown(town)).roles().getOrDefault(role,Map.of());return modes(overrides);
    } }
    @Override public Map<String,String> residentActions(UUID town,UUID resident){ try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
        var t=TownyAPI.getInstance().getTown(town);if(t==null||!t.equals(ownTown(resident)))return Map.of();return modes(rules(t).residents().getOrDefault(resident,Map.of()));
    } }
    private static Map<String,String> modes(Map<String,Boolean> overrides){var result=new TreeMap<String,String>();for(var a:TownAction.values()){
        Boolean value=overrides.get(a.id());result.put(a.id(),value==null?"DEFAULT":value?"ALLOW":"DENY");}return Map.copyOf(result);
    }
    public void changeAccess(Player actor,Town town,UUID mayor,long revision,String role,UUID resident,String action,String mode)throws IOException { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
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
    public void clearAccess(UUID town,UUID resident,String reason)throws IOException { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
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
        var rules=rules(town);for(var a:TownAction.values()){Boolean override=rules.override(resident,role,a);if(override!=null?override:roleDefault(role,a)||delegations!=null&&delegations.active(town.getUUID(),resident,a))nodes.add(a.permission());}
        return Set.copyOf(nodes);
    }
    public boolean manages(CommandSender sender, Town town) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
        primary(); if (town == null) return false;
        if (sender.hasPermission("neverlandtownycouncil.admin")) return true;
        return sender instanceof Player p && sender.hasPermission("neverlandtownycouncil.manage") && town.equals(ownTown(p.getUniqueId()))
                && town.getMayor() != null && town.getMayor().getUUID().equals(p.getUniqueId());
    } }
    /** Read-only UI previews; the mutating service repeats these checks at confirmation time. */
    public String appointmentProblem(CommandSender actor, Town town, MinisterRole role, UUID resident) { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {
        if (!manages(actor,town)) return "Назначать министров может мэр своего города или администратор.";
        if (!healthy()) return "Назначения временно недоступны. Сообщите администрации сервера.";
        if (appointment(town.getUUID(),role)!=null) return "Должность занята. Сначала снимите действующего министра.";
        if (resident==null) return "";
        if (!town.equals(ownTown(resident))) return "Этот игрок больше не живёт в вашем городе.";
        if (town.getMayor()==null || town.getMayor().getUUID().equals(resident)) return "Мэр уже управляет городом. Выберите другого жителя.";
        if (!CitizensAccess.allows(town.getUUID(),resident,"HOLD_OFFICE")) return "Статус жителя не позволяет занимать должность.";
        if (repository.all().values().stream().anyMatch(a->a.town().equals(town.getUUID())&&a.resident().equals(resident)&&sameTerm(a)))
            return "Житель уже назначен министром. Сначала снимите его с прежней должности.";
        return "";
    } }
    public String dismissalProblem(CommandSender actor,Town town,MinisterRole role) { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {
        if (!manages(actor,town)) return "Снимать министров может мэр своего города или администратор.";
        if (!repository.writable()) return "Изменения временно недоступны. Сообщите администрации сервера.";
        return appointment(town.getUUID(),role)==null?"Должность уже свободна.":"";
    } }
    public String roleExplanation(Town town,String role,TownAction action) { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {
        Boolean value=rules(town).roles().getOrDefault(role,Map.of()).get(action.id());
        if(value!=null)return value?"Разрешено мэром для должности":"Запрещено мэром для должности";
        return roleDefault(role,action)?"Разрешено стандартными правами должности":"Должность не выдаёт это право";
    } }
    public String residentExplanation(Town town,UUID resident,TownAction action) { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {
        if(!town.equals(ownTown(resident)))return "Игрок больше не состоит в городе";
        if(town.getMayor()!=null&&town.getMayor().getUUID().equals(resident))return "Разрешено мэру города";
        if(!healthy())return "Настройки доступа временно недоступны";
        Boolean personal=rules(town).residents().getOrDefault(resident,Map.of()).get(action.id());
        if(personal!=null)return personal?"Лично разрешено мэром":"Лично запрещено мэром";
        String role=role(town.getUUID(),resident);
        if(delegations!=null&&delegations.active(town.getUUID(),resident,action))return "Временно поручено мэром; срок указан в разделе полномочий";
        return role.isEmpty()?"Личного правила нет; действуют права жителя и его должностей":roleExplanation(town,role,action);
    } }
    public List<String> appointmentRights(Town town,MinisterRole role,UUID resident) { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) {
        var result=new ArrayList<String>();var rules=rules(town);
        for(var action:TownAction.values()){
            Boolean override=rules.override(resident,role.id(),action);
            boolean allowed=override!=null?override:roleDefault(role.id(),action);
            if(allowed)result.add("&a+ "+action.title());
            else if(override!=null)result.add("&c− "+action.title());
        }
        if(result.isEmpty())result.add("&7Дополнительные действия не разрешены.");
        result.add("&7Личные правила жителя имеют приоритет.");
        result.add("&7Голосование в совете зависит от его состава.");
        return result;
    } }
    public void appoint(CommandSender actor, Town town, MinisterRole role, UUID resident) throws IOException { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) {
        primary(); if (!manages(actor, town)) throw new IllegalArgumentException("Назначать министров может мэр своего города");
        if (!healthy()) throw new IOException("Совет или гражданство недоступны; назначения остановлены");
        prune();
        String problem=appointmentProblem(actor,town,role,resident);
        if(!problem.isEmpty())throw new IllegalArgumentException(problem);
        var a = new Appointment(town.getUUID(), role, resident, town.getMayor().getUUID(), System.currentTimeMillis(), actorId(actor));
        var next = new LinkedHashMap<>(repository.all()); next.put(a.key(), a);
        commit(next, List.of(audit(town.getUUID(), actorId(actor), "APPOINT " + role.id() + " " + resident))); refreshAll();
    } }
    public void dismiss(CommandSender actor, Town town, MinisterRole role) throws IOException { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) {
        primary(); if (!manages(actor, town)) throw new IllegalArgumentException("Снимать министров может мэр своего города");
        var a = appointment(town.getUUID(), role);
        if (a == null) throw new IllegalArgumentException("Должность уже свободна");
        remove(x -> x.key().equals(a.key()), actorId(actor), "DISMISS");
    } }
    public void remove(Predicate<Appointment> filter, String actor, String reason) throws IOException { try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) {
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
    public void prune() throws IOException { try (var suiteGuard28 = ru.neverland.core.SuiteState.guard()) { remove(a -> !sameTerm(a), "SYSTEM", "TERM_OR_MEMBERSHIP_ENDED");
        for(var entry:List.copyOf(access.cities().entrySet())){
            var town=TownyAPI.getInstance().getTown(entry.getKey());
            if(town==null||town.getMayor()==null||!town.getMayor().getUUID().equals(entry.getValue().mayor()))clearAccess(entry.getKey(),null,"TERM_ENDED");
            else for(UUID resident:entry.getValue().residents().keySet())if(!town.equals(ownTown(resident)))clearAccess(entry.getKey(),resident,"LEFT_TOWN");
        } } }
    private static CouncilRepository.Audit audit(UUID town, String actor, String detail) { return new CouncilRepository.Audit(town, System.currentTimeMillis(), actor, detail); }
    private static String actorId(CommandSender sender) { return sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE:" + sender.getName(); }
    /** Called on join and every second. Only this plugin's attachment is ever removed. */
    public void refresh(Player player) { try (var suiteGuard29 = ru.neverland.core.SuiteState.guard()) {
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
    public void refreshAll() { try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) {
        primary(); var players = new HashMap<UUID, Player>();
        grants.forEach((id, grant) -> players.put(id, grant.player()));
        Bukkit.getOnlinePlayers().forEach(p -> players.put(p.getUniqueId(), p)); players.values().forEach(this::refresh);
    } }
    public void release(UUID player) { try (var suiteGuard31 = ru.neverland.core.SuiteState.guard()) {
        primary(); var old = grants.remove(player);
        if (old != null) ru.neverland.core.SuiteScheduler.cleanupPlayer(plugin,old.player(),p->{try{p.removeAttachment(old.attachment());}catch(IllegalArgumentException ignored){}});
    } }
    public void clearGrants() { try (var suiteGuard32 = ru.neverland.core.SuiteState.guard()) { primary(); new ArrayList<>(grants.keySet()).forEach(this::release); } }
}

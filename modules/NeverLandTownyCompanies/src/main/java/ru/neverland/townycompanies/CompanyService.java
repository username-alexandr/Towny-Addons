package ru.neverland.townycompanies;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.io.IOException;
import static ru.neverland.townycompanies.CompanyData.*;
import ru.neverland.townycompanies.api.CompaniesApi;

public final class CompanyService implements CompaniesApi {
    private final JavaPlugin plugin;public final CompanyLedger ledger;public final ContractsBridge contracts=new ContractsBridge();
    public CompanyPayroll payroll;private final CompanyRepository repository;private boolean ready;
    public CompanyService(JavaPlugin plugin,CompanyRepository repository) {this.plugin=plugin;this.repository=repository;ledger=new CompanyLedger(repository,new CompanyBank());repository.audit((a,b)->CompanyAudit.changed(plugin,a,b));CompanyAudit.changed(plugin,CompanyLedger.State.empty(),repository.state());}
    public static Town town(UUID player) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(player);return r==null?null:r.getTownOrNull();} }
    public long interval(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return Math.max(1,Math.min(168,plugin.getConfig().getLong("tax.interval-hours",24)))*3_600_000L;} }
    public long rate(Kind kind){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {long n=plugin.getConfig().getLong("tax.cents."+kind.name().toLowerCase(Locale.ROOT),10000);CompanyData.money(n);return n;} }
    public int limit(String key,int fallback){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return Math.max(1,Math.min(10_000,plugin.getConfig().getInt("limits."+key,fallback)));} }
    public void requireUse(Player p) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(!ru.neverland.core.SuiteState.held()||!ready||!repository.writable())throw new IllegalStateException("Предприятия временно недоступны");if(!p.hasPermission("neverlandtownycompanies.use"))throw new IllegalArgumentException("Нет разрешения на предприятия");} }
    public Company own(Player p){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {requireUse(p);Company c=ledger.membership(p.getUniqueId());if(c==null)throw new IllegalArgumentException("Вы не состоите в компании");return c;} }
    public boolean active(Company c){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {Town t=town(c.owner());return !c.closed()&&t!=null&&t.getUUID().equals(c.town());} }
    public Company manage(Player p,boolean owner,boolean needsCity) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        Company c=own(p);if(owner?!c.owner().equals(p.getUniqueId()):!c.manages(p.getUniqueId()))throw new IllegalArgumentException(owner?"Доступно владельцу компании":"Доступно владельцу и управляющим");
        if(needsCity){Town t=town(p.getUniqueId());if(!active(c)||t==null||!t.getUUID().equals(c.town()))throw new IllegalArgumentException("Вы и владелец должны состоять в городе компании");}
        return c;
    } }
    public void create(Player p,Kind kind,String name)throws IOException { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        requireUse(p);if(!p.hasPermission("neverlandtownycompanies.create"))throw new IllegalArgumentException("Нет разрешения на регистрацию");
        Town t=town(p.getUniqueId());if(t==null)throw new IllegalArgumentException("Сначала вступите в город");
        if(ledger.membership(p.getUniqueId())!=null)throw new IllegalArgumentException("Сначала выйдите из текущей компании");
        long total=ledger.state().companies().values().stream().filter(c->!c.closed()).count();
        long local=ledger.state().companies().values().stream().filter(c->!c.closed()&&c.town().equals(t.getUUID())).count();
        if(total>=limit("companies",2000)||local>=limit("per-town",20))throw new IllegalArgumentException("Достигнут лимит предприятий");
        UUID id=UUID.randomUUID();ledger.put(new Company(id,t.getUUID(),cleanName(name),kind,p.getUniqueId(),Map.of(p.getUniqueId(),Role.OWNER),Map.of(),null,0,0,0,System.currentTimeMillis()+interval(),0,false));
    } }
    public UUID resident(String name){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(name);if(r==null)throw new IllegalArgumentException("Житель не найден");return r.getUUID();} }
    public String residentName(UUID id){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {var r=TownyAPI.getInstance().getResident(id);return r==null?id.toString():r.getName();} }
    public Company find(String raw){ try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) {List<Company> found=ledger.state().companies().values().stream().filter(c->!c.closed()&&(c.id().toString().equalsIgnoreCase(raw)||c.id().toString().substring(0,8).equalsIgnoreCase(raw))).toList();if(found.size()!=1)throw new IllegalArgumentException("Укажите однозначный ID компании");return found.get(0);} }
    public void invite(Player p,UUID target)throws IOException { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) {
        Company c=manage(p,false,true);Town t=town(target);if(t==null||!t.getUUID().equals(c.town()))throw new IllegalArgumentException("Приглашать можно жителей этого города");
        if(ledger.membership(target)!=null)throw new IllegalArgumentException("Игрок уже состоит в компании");
        var invites=new LinkedHashMap<>(c.invitations());long now=System.currentTimeMillis();invites.values().removeIf(exp->exp<=now);
        if(c.members().size()>=limit("members",20)||invites.size()>=limit("members",20))throw new IllegalArgumentException("Достигнут лимит участников или приглашений");
        invites.put(target,now+600_000);ledger.put(c.team(c.owner(),c.members(),invites,c.successor(),c.successorUntil()));
        Player online=Bukkit.getPlayer(target);if(online!=null)message(online,"Приглашение в «"+c.name()+"». /company join "+c.id()+" (10 минут)");
    } }
    public void join(Player p,Company c)throws IOException { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) {
        requireUse(p);c=ledger.company(c.id());Town t=town(p.getUniqueId());
        if(c.closed()||!active(c)||t==null||!t.getUUID().equals(c.town())||c.invitations().getOrDefault(p.getUniqueId(),0L)<=System.currentTimeMillis())throw new IllegalArgumentException("Нет действующего приглашения в компанию вашего города");
        if(ledger.membership(p.getUniqueId())!=null||c.members().size()>=limit("members",20))throw new IllegalArgumentException("Вступление недоступно: проверьте членство и лимит");
        var members=new LinkedHashMap<>(c.members());members.put(p.getUniqueId(),Role.WORKER);var invites=new LinkedHashMap<>(c.invitations());invites.remove(p.getUniqueId());ledger.put(c.team(c.owner(),members,invites,c.successor(),c.successorUntil()));
    } }
    public void role(Player p,UUID target,Role role)throws IOException { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) {
        Company c=manage(p,true,false);if(role==Role.OWNER||target.equals(c.owner())||!c.members().containsKey(target))throw new IllegalArgumentException("Выберите участника и роль manager или worker");
        var members=new LinkedHashMap<>(c.members());members.put(target,role);ledger.put(c.team(c.owner(),members,c.invitations(),c.successor(),c.successorUntil()));
    } }
    public void remove(Player p,UUID target)throws IOException { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) {
        Company c=target.equals(p.getUniqueId())?own(p):manage(p,true,false);if(target.equals(c.owner()))throw new IllegalArgumentException("Сначала передайте компанию или закройте её");
        var members=new LinkedHashMap<>(c.members());members.remove(target);var invites=new LinkedHashMap<>(c.invitations());invites.remove(target);
        ledger.put(c.team(c.owner(),members,invites,target.equals(c.successor())?null:c.successor(),target.equals(c.successor())?0:c.successorUntil()));
    } }
    public void offerOwnership(Player p,UUID target)throws IOException { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) {
        Company c=manage(p,true,false);Town targetTown=town(target);if(targetTown==null||!targetTown.getUUID().equals(c.town()))throw new IllegalArgumentException("Получатель должен состоять в городе компании");if(target.equals(c.owner())||!c.members().containsKey(target))throw new IllegalArgumentException("Получатель должен быть участником компании");
        ledger.put(c.team(c.owner(),c.members(),c.invitations(),target,System.currentTimeMillis()+600_000));
        Player online=Bukkit.getPlayer(target);if(online!=null)message(online,"Вам предлагают владение «"+c.name()+"» вместе со счётом и налоговыми обязательствами: /company acceptowner");
    } }
    public void acceptOwnership(Player p)throws IOException { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
        Company c=own(p);Town t=town(p.getUniqueId());if(!p.getUniqueId().equals(c.successor())||c.successorUntil()<=System.currentTimeMillis()||t==null||!t.getUUID().equals(c.town()))throw new IllegalArgumentException("Нет действующего предложения о передаче");
        var members=new LinkedHashMap<>(c.members());members.put(c.owner(),Role.MANAGER);members.put(p.getUniqueId(),Role.OWNER);ledger.put(c.team(p.getUniqueId(),members,c.invitations(),null,0));
    } }
    public void money(Player p,Purpose purpose,long amount)throws IOException { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) {
        Company c=purpose==Purpose.WITHDRAW?manage(p,true,false):own(p);
        if(purpose==Purpose.WITHDRAW&&payroll!=null&&payroll.obligations(c.id()))throw new IllegalArgumentException("Сначала выплатите заработанное сотрудникам или завершите сверку зарплат");
        if(purpose==Purpose.WITHDRAW&&(c.debt()>0||System.currentTimeMillis()>=c.nextTax()))throw new IllegalArgumentException("Сначала дождитесь расчёта и погасите налог компании");
        Payment pay=ledger.begin(c.id(),p.getUniqueId(),purpose,amount,System.currentTimeMillis());ledger.process(pay.id());
        Payment done=ledger.state().payments().get(pay.id());message(p,done.phase()==Phase.DONE?"Платёж выполнен: "+format(amount):done.phase()==Phase.PENDING?"Платёж ожидает сверки администратором: "+pay.id():"Банк отклонил платёж");
    } }
    public void disband(Player p)throws IOException { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) {
        Company c=manage(p,true,false);if(c.balance()!=0||c.debt()!=0||(payroll!=null&&payroll.obligations(c.id()))||ledger.busy(c.id())||contracts.count(c.id())>0)throw new IllegalArgumentException("Сначала завершите контракты и платежи, погасите налог и выведите остаток");ledger.put(c.close());
    } }
    public void take(Player p,UUID contract){ try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) {Company c=manage(p,false,true);if(!contracts.take(p,c.id(),contract))throw new IllegalArgumentException("Контракт уже занят, начат, просрочен или недоступен компании");} }
    public void release(Player p,UUID contract){ try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) {Company c=manage(p,false,true);if(!contracts.release(p,c.id(),contract))throw new IllegalArgumentException("Освободить можно только свой контракт с нулевым прогрессом");} }
    public void tick() { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {
        ready=true;if(!repository.writable())return;long now=System.currentTimeMillis();
        try {
            for(Payment p:List.copyOf(ledger.state().payments().values()))if(p.phase()==Phase.READY)ledger.process(p.id());
            for(Company original:List.copyOf(ledger.state().companies().values())){
                if(original.closed()||TownyAPI.getInstance().getTown(original.town())==null)continue;
                ledger.assess(original.id(),rate(original.kind()),now,interval());Company c=ledger.company(original.id());
                if(c.debt()>0&&c.balance()>=c.debt()&&!ledger.busy(c.id())) {var p=ledger.begin(c.id(),c.town(),Purpose.TAX,c.debt(),now);ledger.process(p.id());}
            }
        }catch(Exception ex){plugin.getLogger().severe("Расчёт компаний остановлен: "+ex.getMessage());}
    } }
    @Override public boolean canTake(UUID actor,UUID company,UUID town) { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) {
        if(!ru.neverland.core.SuiteState.held()||!ready||!repository.writable())return false;
        Company c=ledger.company(company);Player p=Bukkit.getPlayer(actor);Town t=town(actor);
        return c!=null&&p!=null&&p.hasPermission("neverlandtownycompanies.use")&&p.hasPermission("neverlandtownycompanies.contracts")&&c.manages(actor)&&active(c)&&c.town().equals(town)&&t!=null&&t.getUUID().equals(town)&&c.debt()==0&&System.currentTimeMillis()<c.nextTax()&&!ledger.busy(company)&&contracts.count(company)<limit("contracts",3);
    } }
    @Override public boolean canManage(UUID actor,UUID company,UUID town) { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) {
        if(!ru.neverland.core.SuiteState.held()||!ready||!repository.writable())return false;
        Company c=ledger.company(company);Player p=Bukkit.getPlayer(actor);Town t=town(actor);
        return c!=null&&p!=null&&p.hasPermission("neverlandtownycompanies.use")&&p.hasPermission("neverlandtownycompanies.contracts")&&c.manages(actor)&&t!=null&&t.getUUID().equals(town)&&c.town().equals(town);
    } }
    @Override public boolean canContribute(UUID actor,UUID company,UUID town) { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) {
        if(!ru.neverland.core.SuiteState.held()||!ready||!repository.writable())return false;
        Company c=ledger.company(company);Player p=Bukkit.getPlayer(actor);Town t=town(actor);
        return c!=null&&!c.closed()&&p!=null&&p.hasPermission("neverlandtownycompanies.use")&&p.hasPermission("mintcontracts.contribute")&&c.members().containsKey(actor)&&t!=null&&t.getUUID().equals(town)&&c.town().equals(town);
    } }
    @Override public String companyName(UUID company){ try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) {Company c=ledger.company(company);return c==null?"Неизвестная компания":c.name();} }
    @Override public boolean settleEscrow(UUID contract,UUID company,UUID town,long payout,long refund) { try (var suiteGuard28 = ru.neverland.core.SuiteState.guard()) {
        if(!ru.neverland.core.SuiteState.held()||!ready||!repository.writable())return false;
        try {return ledger.settle(new Receipt(contract,company,town,payout,refund),System.currentTimeMillis());}
        catch(Exception ex){plugin.getLogger().severe("Расчёт контракта "+contract+" отложен: "+ex.getMessage());return false;}
    } }
    public static void message(org.bukkit.command.CommandSender p,String text){ try (var suiteGuard29 = ru.neverland.core.SuiteState.guard()) {p.sendMessage("§6NeverLand §8» §f"+text);} }
}

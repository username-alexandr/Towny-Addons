package ru.neverland.townycouncil;

import java.util.*;
import java.util.concurrent.CompletionStage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.*;
import static ru.neverland.townycouncil.Delegations.*;

public final class DelegationService {
    private final Plugin plugin;private final CouncilService council;private final DelegationRepository repository;
    public DelegationService(Plugin plugin,CouncilService council,DelegationRepository repository){this.plugin=plugin;this.council=council;this.repository=repository;}
    public DelegationRepository repository(){return repository;}
    public State state(){return repository.state();}
    private void mayor(Player actor,Town town){if(!SuiteState.held())throw new IllegalStateException("Нужна согласованная операция совета");if(!plugin.isEnabled()||SuiteState.frozen(plugin.getName())||!actor.hasPermission("neverlandtownycouncil.use")||!council.mayEditAccess(actor,town))throw new IllegalArgumentException("Временные права меняет только действующий мэр своего города");}
    private boolean enabled(){return !(plugin instanceof org.bukkit.plugin.java.JavaPlugin p)||p.getConfig().getBoolean("delegations.accept-new",true);}
    public boolean active(UUID town,UUID resident,TownAction action){
        if(!repository.readable()||!council.healthy())return false;var grant=state().grants().get(town+"/"+resident+"/"+action.id());if(grant==null)return false;var t=TownyAPI.getInstance().getTown(town);if(t!=null&&Boolean.FALSE.equals(council.rules(t).override(resident,council.role(town,resident),action)))return false;return t!=null&&t.getMayor()!=null&&grant.active(t.getMayor().getUUID(),t.equals(council.ownTown(resident)),CitizensAccess.allows(town,resident,"HOLD_OFFICE"),System.currentTimeMillis());
    }
    public Grant proposal(Player actor,Town town,UUID resident,TownAction action,int hours,String reason){mayor(actor,town);if(!enabled())throw new IllegalArgumentException("Новые временные поручения приостановлены; действующие сохраняются");if(hours<1||hours>720)throw new IllegalArgumentException("Срок: от 1 до 720 часов");if(!town.equals(council.ownTown(resident))||resident.equals(actor.getUniqueId()))throw new IllegalArgumentException("Выберите другого жителя своего города");if(!CitizensAccess.allows(town.getUUID(),resident,"HOLD_OFFICE"))throw new IllegalArgumentException("Статус жителя не позволяет временно управлять городом");Boolean rule=council.rules(town).override(resident,council.role(town.getUUID(),resident),action);if(Boolean.FALSE.equals(rule))throw new IllegalArgumentException("Сначала снимите постоянный запрет на это действие в правах жителя или должности");long now=System.currentTimeMillis();var grant=new Grant(UUID.randomUUID(),town.getUUID(),resident,actor.getUniqueId(),action.id(),now,Math.addExact(now,Math.multiplyExact((long)hours,HOUR)),actor.getName(),reason,false,1);Delegations.grant(state(),grant,state().revision());return grant;}
    public CompletionStage<State> grant(Player actor,Grant quoted,long revision){var town=TownyAPI.getInstance().getTown(quoted.town());mayor(actor,town);if(!enabled()||!quoted.mayor().equals(actor.getUniqueId())||!town.equals(council.ownTown(quoted.resident()))||!CitizensAccess.allows(quoted.town(),quoted.resident(),"HOLD_OFFICE"))throw new IllegalArgumentException("Условия поручения изменились");if(System.currentTimeMillis()-quoted.grantedAt()>60_000||System.currentTimeMillis()<quoted.grantedAt())throw new IllegalArgumentException("Подтверждение устарело");var action=TownAction.parse(quoted.action());if(Boolean.FALSE.equals(council.rules(town).override(quoted.resident(),council.role(quoted.town(),quoted.resident()),action)))throw new IllegalArgumentException("Теперь это действие запрещено личными правилами или должностью");var before=state();return save(actor,town,before,Delegations.grant(before,quoted,revision),"GRANT",quoted);}
    public CompletionStage<State> revoke(Player actor,Grant quoted,String reason){var town=TownyAPI.getInstance().getTown(quoted.town());mayor(actor,town);var before=state();return save(actor,town,before,Delegations.revoke(before,quoted.key(),quoted.id(),quoted.revision(),actor.getUniqueId(),actor.getName(),reason,System.currentTimeMillis()),"REVOKE",quoted);}
    private CompletionStage<State> save(Player actor,Town town,State before,State next,String kind,Grant grant){
        if(before==next)return java.util.concurrent.CompletableFuture.completedFuture(before);
        var who=AuditTrail.player(actor.getUniqueId());var city=AuditTrail.town(town.getUUID());String detail=(kind.equals("GRANT")?"Выданы":"Отозваны")+" временные права: "+grant.resident()+" / "+grant.action()+"; до "+DelegationMenus.date(grant.expiresAt())+"; "+next.history().get(next.history().size()-1).reason();
        return repository.commit(before,next).thenApply(saved->{if(saved!=before&&!AuditTrail.record(plugin,"delegation",grant.id()+":"+saved.revision(),"COUNCIL_DELEGATION","COMPLETED",who,city,city,"",0,"",detail))plugin.getLogger().warning("Временные права сохранены; административный журнал требует проверки");if(plugin.isEnabled()&&!SuiteState.frozen(plugin.getName()))try{SuiteScheduler.get().runTask(plugin,council::refreshAll);}catch(RuntimeException error){plugin.getLogger().fine("Права подтверждены; обновление подключений выполнится при следующей проверке");}return saved;});
    }
}

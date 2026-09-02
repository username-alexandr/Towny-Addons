package ru.neverland.townytaxes.command;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.neverland.townytaxes.integration.TownyHook;
import ru.neverland.townytaxes.model.Domain;
import ru.neverland.townytaxes.model.TaxPolicy;
import ru.neverland.townytaxes.service.FiscalService;
import ru.neverland.townytaxes.service.MessageService;
import ru.neverland.townytaxes.util.ColorUtil;

import java.util.Map;

public final class TownTaxesCommand implements CommandExecutor {
    private final TownyHook towny;private final FiscalService fiscal;private final MessageService messages;
    public TownTaxesCommand(TownyHook towny,FiscalService fiscal,MessageService messages){this.towny=towny;this.fiscal=fiscal;this.messages=messages;}
    @Override public boolean onCommand(@NotNull CommandSender sender,@NotNull Command command,@NotNull String label,@NotNull String[] args){if(!(sender instanceof Player player)){messages.send(sender,"players-only");return true;}if(!player.hasPermission("townytaxes.use")){messages.send(player,"no-permission");return true;}Town town=towny.town(player);if(town==null){messages.send(player,"no-town");return true;}if(args.length==0||args[0].equalsIgnoreCase("help")){status(player,town);return true;}switch(args[0].toLowerCase(java.util.Locale.ROOT)){case"policies","list"->list(player,town);case"create"->create(player,town,args);case"remove"->remove(player,town,args);case"debt"->debt(player,town);case"pay"->pay(player);default->messages.list("help-taxes").forEach(player::sendMessage);}return true;}
    private void status(Player p,Town t){p.sendMessage(ColorUtil.color("&#FFD45A&lКазначейство города "+t.getName()));p.sendMessage(ColorUtil.color("&7Баланс города: &f"+fiscal.format(t.getAccount().getHoldingBalance())));Resident r=towny.resident(p);p.sendMessage(ColorUtil.color("&7Ваша задолженность: &f"+fiscal.format(r==null?0:fiscal.taxDebt(r.getUUID()))));p.sendMessage(ColorUtil.color("&7Политик города: &f"+fiscal.repository().policies().stream().filter(v->v.scope()==Domain.Scope.TOWN&&v.targetId().equals(t.getUUID())).count()));messages.list("help-taxes").forEach(p::sendMessage);}
    private void list(Player p,Town t){p.sendMessage(ColorUtil.color("&#FFD45A&lНалоговые политики города:"));boolean any=false;for(TaxPolicy v:fiscal.repository().policies())if(v.scope()==Domain.Scope.TOWN&&v.targetId().equals(t.getUUID())){any=true;p.sendMessage(ColorUtil.color("&8- &f"+v.shortId()+" &7"+taxName(v.type())+": &e"+v.value()+(v.type()==Domain.TaxType.FIXED?" монет":"%")+" &8→ &f"+v.destinationName()));}if(!any)p.sendMessage(ColorUtil.color("&7Политик нет."));}
    private void create(Player p,Town t,String[] a){if(!p.hasPermission("townytaxes.manage")||!towny.isTownManager(p,t)){messages.send(p,"only-town-manager");return;}if(a.length<3){p.sendMessage(ColorUtil.color("&fИспользование: /t taxes create <fixed|income|transaction> <значение> [интервал в минутах]"));return;}Domain.TaxType type=Domain.TaxType.parse(a[1]);if(type==null){messages.send(p,"invalid-tax-type");return;}try{double value=Double.parseDouble(a[2].replace(',','.'));if(!fiscal.validTaxValue(type,value))throw new NumberFormatException();long minutes=a.length>3?Long.parseLong(a[3]):1440;TownyHook.Party party=new TownyHook.Party(Domain.Scope.TOWN,t.getUUID(),t.getName(),t.getAccount());TaxPolicy policy=fiscal.createPolicy(party,type,value,party,fiscal.intervalMillis(minutes),p.getName());messages.send(p,"policy-created",Map.of("id",policy.shortId()));}catch(NumberFormatException e){messages.send(p,"invalid-number");}}
    private void remove(Player p,Town t,String[] a){if(!p.hasPermission("townytaxes.manage")||!towny.isTownManager(p,t)){messages.send(p,"only-town-manager");return;}if(a.length<2){p.sendMessage("/t taxes remove <ID>");return;}TaxPolicy v=fiscal.repository().policy(a[1]);if(v==null||v.scope()!=Domain.Scope.TOWN||!v.targetId().equals(t.getUUID())){messages.send(p,"policy-not-found",Map.of("id",a[1]));return;}fiscal.removePolicy(a[1]);messages.send(p,"policy-removed",Map.of("id",v.shortId()));}
    private void debt(Player p,Town t){Resident r=towny.resident(p);p.sendMessage(ColorUtil.color("&#FFD45AЗадолженность игрока: &f"+fiscal.format(r==null?0:fiscal.taxDebt(r.getUUID()))+"\n&#FFD45AЗадолженность города: &f"+fiscal.format(fiscal.taxDebt(t.getUUID()))));}
    private void pay(Player p){Resident r=towny.resident(p);if(r!=null&&fiscal.payDebt(new TownyHook.Party(Domain.Scope.PLAYER,r.getUUID(),r.getName(),r.getAccount())))messages.send(p,"debt-paid",Map.of("amount","полностью"));else messages.send(p,"tax-debt",Map.of("amount",fiscal.format(r==null?0:fiscal.taxDebt(r.getUUID()))));}
    private String taxName(Domain.TaxType t){return switch(t){case FIXED->"фиксированный";case INCOME->"подоходный";case TRANSACTION->"транзакционный";};}
}

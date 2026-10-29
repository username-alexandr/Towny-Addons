package ru.neverland.townydiplomacy.aid;

import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import ru.neverland.core.*;
import ru.neverland.townydiplomacy.*;

public final class AidCommands {
    private final NeverLandTownyDiplomacy plugin;private final AidService aid;private final AidMenus menus;
    public AidCommands(NeverLandTownyDiplomacy plugin,AidService aid,AidMenus menus){this.plugin=plugin;this.aid=aid;this.menus=menus;}
    public static String when(long at){return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.of("Europe/Moscow")).format(Instant.ofEpochMilli(at))+" МСК";}
    public static String phase(AidPact p){return switch(p.phase()){case OFFERED->"Ожидает подписи второго мэра";case ACCEPTING,FINALIZING->"Подпись сохраняется";case ACTIVE->"Взаимопомощь действует";case ENDED->"Соглашение завершено";};}
    public static String status(AidCall c){return switch(c.status()){case OPEN->"Ожидает помощи";case FULFILLED->"Помощь подтверждена";case UNANSWERED->"За срок помощь не подтверждена полностью";case UNASSESSED->"Не оценено — без последствий";};}
    public void run(CommandSender sender,Town town,String[] args){
        if(args.length==1||args[1].equals("list")){if(sender instanceof Player p)menus.open(p,town,0,null);else aid.visibleState().pacts().values().stream().filter(v->v.party(town.getUUID())).forEach(v->DiplomacyCommand.tell(sender,v.id()+" • "+phase(v)));return;}
        if(args[1].equals("status")){DiplomacyCommand.tell(sender,aid.status());return;}
        if(args[1].equals("offer")){if(args.length<3||args.length>6)throw new IllegalArgumentException("/diplomacy aid offer <город> [дни=30] [ответ-мин=30] [урон=20]");var stage=aid.offer(sender,town,TownyAPI.getInstance().getTown(args[2]),args.length>3?Integer.parseInt(args[3]):aid.settings().days(),args.length>4?Integer.parseInt(args[4]):aid.settings().minutes(),args.length>5?Integer.parseInt(args[5]):aid.settings().damage());reply(stage,sender);return;}
        if(Set.of("accept","reject","end").contains(args[1])){if(args.length!=3)throw new IllegalArgumentException("/diplomacy aid "+args[1]+" <полный-ID>");reply(aid.decide(sender,town,UUID.fromString(args[2]),args[1]),sender);return;}
        if(args[1].equals("calls")){int page=args.length>2?Integer.parseInt(args[2])-1:0;if(page<0)throw new IllegalArgumentException("Страницы начинаются с 1");DiplomacyCommand.tell(sender,"Помощь • "+aid.status());aid.visibleState().calls().values().stream().filter(c->c.beneficiary().equals(town.getUUID())||c.helper().equals(town.getUUID())).sorted(Comparator.comparingLong(AidCall::opened).reversed()).skip(page*6L).limit(6).forEach(c->{DiplomacyCommand.tell(sender,c.id()+" • "+status(c));DiplomacyCommand.tell(sender,DiplomacyMenus.name(c.helper())+" → "+DiplomacyMenus.name(c.beneficiary())+" • "+c.confirmedMilliDamage()/1000.0+" / "+c.requiredMilliDamage()/1000.0+" • до "+when(c.deadline()));});return;}
        if(args[1].equals("history")){int page=args.length>2?Integer.parseInt(args[2])-1:0;if(page<0)throw new IllegalArgumentException("Страницы начинаются с 1");var state=aid.visibleState();state.history().stream().filter(h->state.pacts().get(h.pact()).party(town.getUUID())).sorted(Comparator.comparingLong(AidState.History::at).reversed()).skip(page*6L).limit(6).forEach(h->DiplomacyCommand.tell(sender,when(h.at())+" • "+h.action()+" • "+h.actor()+" • "+h.pact()));return;}
        throw new IllegalArgumentException("/diplomacy aid [list|offer|accept|reject|end|calls|history|status]");
    }
    private void reply(java.util.concurrent.CompletionStage<AidPact> stage,CommandSender sender){DiplomacyCommand.tell(sender,"Решение сохраняется; оно действует после подтверждения записи");stage.whenComplete((p,error)->{if(!plugin.isEnabled())return;Runnable tell=()->DiplomacyCommand.tell(sender,error==null?phase(p)+" • "+p.id():"Решение не подтверждено; проверьте /diplomacy aid status");if(sender instanceof Player player)SuiteScheduler.entity(plugin,player,tell);else SuiteScheduler.get().runTask(plugin,tell);});}
}

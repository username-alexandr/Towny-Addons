package cc.crafto.ui.server;

import cc.crafto.ui.CommandForm;
import com.google.gson.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static cc.crafto.ui.Protocol.*;

/** Called exclusively on the requesting player's EntityScheduler. */
final class CommandWorkbench {
    static final class State {
        String token="";long created,suggestAt,styleConfirmedUntil;CommandCatalog catalog;NativeReview nativeReview;Prepared awaitingStyle;
        final CommandTicket<Prepared> ticket=new CommandTicket<>();
        void clear(){token="";catalog=null;ticket.clear();nativeReview=null;awaitingStyle=null;styleConfirmedUntil=0;}
    }
    record Target(CommandCatalog.Module module,PluginCommand command,CommandForm form) {}
    record Prepared(CommandCatalog catalog,Target target,String arguments) {}
    record NativeReview(Prepared prepared,TownyConfirmBridge.Pending pending) {}
    private final CraftoUIPlugin plugin;
    CommandWorkbench(CraftoUIPlugin plugin){this.plugin=plugin;}
    private boolean permission(Player p,String permission){return permission.isBlank()||p.hasPermission(permission);}
    private static boolean townCreation(Target t){return t.module().plugin().equals("Towny")&&t.command().getName().equals("town")&&t.form().prefix().equals("new");}
    private boolean allowed(Player p,Target t){return p.hasPermission("craftoui.use")&&p.hasPermission("craftoui.commands")&&t.command().getPlugin().isEnabled()&&permission(p,t.module().permission())&&permission(p,t.form().permission())&&t.command().testPermissionSilent(p)&&(!townCreation(t)||!TownMembership.hasTown(plugin,p));}
    private void cancelNative(Player p,State state)throws Exception{
        var review=state.nativeReview;state.nativeReview=null;state.awaitingStyle=null;state.styleConfirmedUntil=0;
        if(review!=null&&plugin.townyConfirmations().get(p)==review.pending())plugin.townyConfirmations().execute(p,review.pending().token(),false);
    }
    private Map<String,Target> targets(Player p,CommandCatalog catalog){
        var result=new TreeMap<String,Target>();
        // Only explicit plugin owners are trusted. Aliases do not create duplicate roots.
        var seen=Collections.newSetFromMap(new IdentityHashMap<PluginCommand,Boolean>());
        for(Command raw:plugin.getServer().getCommandMap().getKnownCommands().values()){
            if(!(raw instanceof PluginCommand command)||!seen.add(command))continue;
            var module=catalog.modules().get(command.getPlugin().getName());if(module==null||!module.roots().isEmpty()&&!module.roots().contains(command.getName()))continue;
            var generic=new CommandForm("raw-"+command.getName(),CommandPresentation.label(module.label(),command.getName(),command.getDescription()),command.getName(),"","",List.of(new CommandForm.Field("args","Параметры действия","arguments",false,List.of())));
            var target=new Target(module,command,generic);if(!allowed(p,target))continue;
            result.put(module.plugin()+"/@"+command.getName(),target);
            for(var form:module.forms())if(form.root().equals(command.getName())){
                var specific=new Target(module,command,form);if(allowed(p,specific))result.put(module.plugin()+"/"+form.id(),specific);
            }
        }
        return result;
    }
    void handle(Player p,State state,CommandCatalog catalog,JsonObject body,Consumer<JsonObject> send,Runnable beforeExecute){
        String op=str(body,"op","");int request=integer(body,"request",0);
        try {
            if(!p.hasPermission("craftoui.use")||!p.hasPermission("craftoui.commands"))throw new IllegalArgumentException("Недостаточно прав на действия аддонов.");
            if(op.equals("open")){cancelNative(p,state);state.clear();state.token=UUID.randomUUID().toString();state.catalog=catalog;state.created=System.nanoTime();if(!str(body,"form","").isBlank()){body=body.deepCopy();body.addProperty("id",str(body,"form",""));op="form";}}
            else if(op.equals("close")){if(state.token.equals(str(body,"token",""))){cancelNative(p,state);state.clear();}return;}
            else if(state.token.isEmpty()||!state.token.equals(str(body,"token",""))||state.catalog!=catalog||System.nanoTime()-state.created>TimeUnit.MINUTES.toNanos(5))throw new IllegalArgumentException("Список устарел. Нажмите «Обновить».");
            if(Set.of("list","form","prepare").contains(op))cancelNative(p,state);
            if(op.equals("open")||op.equals("list")){
                state.ticket.clear();String module=str(body,"module",""),query=str(body,"query","").toLowerCase(Locale.ROOT);if(query.length()>80)throw new IllegalArgumentException("Слишком длинный поиск.");
                var all=targets(p,catalog);var rows=new ArrayList<JsonObject>();
                if(module.isEmpty()){
                    for(var m:catalog.modules().values())if(all.values().stream().anyMatch(t->t.module()==m)&&(m.label()+" "+m.plugin()).toLowerCase(Locale.ROOT).contains(query))rows.add(object("id",m.plugin(),"label",m.label(),"detail","Выбрать действие раздела","type","module"));
                }else for(var e:all.entrySet())if(e.getValue().module().plugin().equals(module)){
                    var t=e.getValue();String detail=CommandPresentation.detail(t.form()),command=CommandPresentation.template(t.form());
                    if((t.form().label()+" "+detail+" "+command).toLowerCase(Locale.ROOT).contains(query))rows.add(object("id",e.getKey(),"label",t.form().label(),"detail",detail,"command",command,"type","form"));
                }
                int size=Math.clamp(integer(body,"size",12),1,24),pages=Math.max(1,(rows.size()+size-1)/size),page=Math.clamp(integer(body,"page",0),0,pages-1);
                send.accept(object("op","list","request",request,"token",state.token,"module",module,"moduleLabel",catalog.modules().containsKey(module)?catalog.modules().get(module).label():"","page",page,"pages",pages,"count",rows.size(),"entries",rows.subList(page*size,Math.min(rows.size(),(page+1)*size))));return;
            }
            if(op.equals("cancel")){cancelNative(p,state);state.ticket.clear();send.accept(object("op","cancelled","request",request));return;}
            if(op.equals("confirm")){
                if(state.nativeReview!=null){
                    var review=state.nativeReview;var pending=review.pending();var t=review.prepared().target();
                    if(!pending.token().equals(str(body,"confirmation","")))throw new IllegalArgumentException("Подтверждение Towny изменилось или истекло.");
                    if(plugin.townyConfirmations().get(p)!=pending){state.nativeReview=null;throw new IllegalArgumentException("Подтверждение Towny изменилось или истекло.");}
                    if(review.prepared().catalog()!=catalog||!allowed(p,t)||targets(p,catalog).values().stream().noneMatch(v->v.command()==t.command()&&v.form().equals(t.form())))throw new IllegalArgumentException("Команда или права изменились.");
                    if(!p.getItemOnCursor().isEmpty())throw new IllegalArgumentException("Сначала положите предмет с курсора в ячейку.");
                    state.nativeReview=null;state.awaitingStyle=null;send.accept(object("op","dispatched","request",request,"message","Подтверждение передано Towny. Результат появится ниже."));
                    plugin.townyConfirmations().execute(p,pending.token(),true);return;
                }
                var prepared=state.ticket.consume(str(body,"confirmation",""),System.nanoTime());
                if(prepared==null||prepared.catalog()!=catalog)throw new IllegalArgumentException("Подтверждение истекло или уже использовано.");
                Target t=prepared.target();
                var current=targets(p,catalog).values().stream().filter(v->v.command()==t.command()&&v.form().equals(t.form())).findFirst();
                if(current.isEmpty()||!allowed(p,t))throw new IllegalArgumentException("Команда или права изменились.");
                if(!p.getItemOnCursor().isEmpty())throw new IllegalArgumentException("Сначала положите предмет с курсора в ячейку.");
                String args=prepared.arguments(),visible="/"+t.command().getName()+(args.isEmpty()?"":" "+args);
                var event=new PlayerCommandPreprocessEvent(p,visible);plugin.getServer().getPluginManager().callEvent(event);
                if(event.isCancelled())throw new IllegalArgumentException("Команда отменена серверным плагином.");
                if(!event.getMessage().equals(visible))throw new IllegalArgumentException("Сервер перенаправил команду. Выполните её через обычный ввод.");
                String pinned=t.module().plugin().toLowerCase(Locale.ROOT)+":"+t.command().getName();
                if(plugin.getServer().getPluginCommand(pinned)!=t.command())throw new IllegalArgumentException("Регистрация команды изменилась.");
                beforeExecute.run();
                // This acknowledges dispatch only. The original handler decides success and sends its own feedback.
                send.accept(object("op","dispatched","request",request,"message","Команда передана аддону. Ответ сервера появится ниже."));
                var bridge=t.module().plugin().equals("Towny")?plugin.townyConfirmations():null;var previous=bridge==null?null:bridge.get(p);
                // Builds may defer /town new until its native style chooser closes.
                state.awaitingStyle=townCreation(t)?prepared:null;
                p.performCommand(pinned+(args.isEmpty()?"":" "+args));
                var pending=bridge==null?null:bridge.get(p);
                if(pending!=null&&pending!=previous){state.awaitingStyle=null;state.nativeReview=new NativeReview(prepared,pending);send.accept(object("op","review","native",true,"request",request,"confirmation",pending.token(),"command",bridge.title(p,pending),"executedCommand",visible,"label",t.form().label()));}
                return;
            }
            String id=str(body,"id","");Target target=targets(p,catalog).get(id);if(target==null)throw new IllegalArgumentException("Действие недоступно.");
            if(op.equals("form")){
                state.ticket.clear();String usage=townCreation(target)?"Город появится на вашем текущем месте. Сервер проверит название, место и стоимость.":CommandPresentation.detail(target.form());
                send.accept(object("op","form","request",request,"token",state.token,"id",id,"module",target.module().plugin(),"moduleLabel",target.module().label(),"form",target.form().json(),"usage",usage,"command",CommandPresentation.template(target.form())));return;
            }
            JsonObject values=body.has("values")?body.getAsJsonObject("values"):new JsonObject();
            if(op.equals("prepare")){
                state.ticket.clear();String args=target.form().arguments(values);String token=state.ticket.issue(new Prepared(catalog,target,args),System.nanoTime());
                send.accept(object("op","review","request",request,"confirmation",token,"command","/"+target.command().getName()+(args.isEmpty()?"":" "+args),"label",target.form().label()));return;
            }
            if(op.equals("suggest")){
                long now=System.nanoTime();if(now-state.suggestAt<TimeUnit.MILLISECONDS.toNanos(180))return;state.suggestAt=now;
                int index=integer(body,"field",0);var fields=target.form().fields();if(index<0||index>=fields.size())return;
                var field=fields.get(index);String value=str(values,field.id(),"");CommandForm.clean(value);
                var args=new ArrayList<String>();if(!target.form().prefix().isEmpty())args.addAll(List.of(target.form().prefix().split(" ")));
                for(int i=0;i<index;i++){String previous=CommandForm.clean(str(values,fields.get(i).id(),""));if(previous.isEmpty())return;args.addAll(List.of(previous.split(" ")));}
                args.addAll(Arrays.asList(value.split(" +",-1)));String prefix=args.getLast().toLowerCase(Locale.ROOT);
                var suggestions=new LinkedHashSet<String>(field.choices());
                TabCompleter completer=target.command().getTabCompleter();if(completer==null&&target.command().getExecutor() instanceof TabCompleter c)completer=c;
                if(completer!=null){var found=completer.onTabComplete(p,target.command(),target.command().getName(),args.toArray(String[]::new));if(found!=null)suggestions.addAll(found);}
                if(field.type().equals("player"))suggestions.addAll(CommandSuggestions.residents(plugin.getServer(),p));
                if(field.type().equals("player"))for(Player other:plugin.getServer().getOnlinePlayers())if(p.canSee(other))suggestions.add(other.getName());
                var list=suggestions.stream().filter(Objects::nonNull).filter(s->s.length()<=128&&!s.isBlank()&&!s.contains(" ")&&s.codePoints().noneMatch(Character::isISOControl)).filter(s->s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().limit(24).toList();
                send.accept(object("op","suggest","request",request,"field",index,"entries",list));return;
            }
            throw new IllegalArgumentException("Неизвестное действие интерфейса.");
        }catch(IllegalArgumentException e){send.accept(object("op","error","request",request,"message",e.getMessage()==null?"Некорректные параметры.":e.getMessage()));}
        catch(Exception e){plugin.getLogger().log(java.util.logging.Level.WARNING,"Addon command UI failed",e);send.accept(object("op","error","request",request,"message","Аддон не смог обработать запрос. Подробности в журнале сервера."));}
    }
    /** Called on the player's EntityScheduler after Builds replays /town new. */
    JsonObject resumedTownCreation(Player p,State state,CommandCatalog catalog,TownyConfirmBridge.Pending pending)throws Exception{
        Prepared prepared=state.awaitingStyle;
        if(prepared==null||state.catalog!=catalog||prepared.catalog()!=catalog||System.nanoTime()>state.styleConfirmedUntil||System.nanoTime()-state.created>TimeUnit.MINUTES.toNanos(5))return null;
        if(!townCreation(prepared.target())||!allowed(p,prepared.target())||plugin.townyConfirmations().get(p)!=pending)return null;
        String title=plugin.townyConfirmations().title(p,pending);
        state.awaitingStyle=null;state.styleConfirmedUntil=0;state.nativeReview=new NativeReview(prepared,pending);
        return object("op","resume-native","token",state.token,"id",prepared.target().module().plugin()+"/"+prepared.target().form().id(),"form",prepared.target().form().json(),"confirmation",pending.token(),"command",title,"executedCommand","/"+prepared.target().command().getName()+" "+prepared.arguments(),"label",prepared.target().form().label());
    }
}

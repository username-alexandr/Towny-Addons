package ru.neverland.townycontrol;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import org.bukkit.command.CommandSender;

/** Versioned public methods on the owning executor; no shared class-loader identity or private state. */
final class ActivityBridge {
    record Task(AdminRouter.Entry entry,String id,String name,Set<String> actions){Task{actions=Set.copyOf(actions);}}
    private final AdminRouter router;
    ActivityBridge(AdminRouter router){this.router=router;}
    private Object owner(CommandSender sender,AdminRouter.Entry entry)throws Exception {
        if(!AdminAccess.admin(sender)||!AdminAccess.has(sender,entry.permission()))throw new IllegalArgumentException("Нет прав на этот аддон");
        Object executor=router.nativeCommand(entry).getExecutor();
        if(!entry.activities()||!Integer.valueOf(1).equals(call(executor,"adminMenuVersion",new Class<?>[0])))throw new IllegalArgumentException("Аддон не поддерживает меню задач этой версии");
        return executor;
    }
    List<Task> targets(CommandSender sender,AdminRouter.Entry entry)throws Exception {
        Object value=call(owner(sender,entry),"adminMenuTargets",new Class<?>[]{CommandSender.class},sender);
        if(!(value instanceof List<?> rows))throw new IllegalArgumentException("Неверный ответ аддона");
        var tasks=new ArrayList<Task>();var ids=new HashSet<String>();
        for(Object row:rows){if(!(row instanceof Map<?,?> m)||!(m.get("id") instanceof String id)||!(m.get("name") instanceof String name)||!(m.get("actions") instanceof List<?> raw)||!ids.add(id))throw new IllegalArgumentException("Неверный список задач аддона");
            var actions=new HashSet<String>();for(Object action:raw){if(!(action instanceof String s))throw new IllegalArgumentException("Неверное действие");actions.add(s);}tasks.add(new Task(entry,id,name,actions));}
        return tasks.stream().sorted(Comparator.comparing(Task::name).thenComparing(Task::id)).toList();
    }
    String execute(CommandSender sender,Task target,String action,long minutes)throws Exception {
        return (String)call(owner(sender,target.entry()),"adminMenuAction",new Class<?>[]{CommandSender.class,String.class,String.class,String.class,long.class},sender,target.id(),target.name(),action,minutes);
    }
    private static Object call(Object owner,String name,Class<?>[] types,Object... args)throws Exception {
        try{return owner.getClass().getMethod(name,types).invoke(owner,args);}
        catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw ex;}
    }
}

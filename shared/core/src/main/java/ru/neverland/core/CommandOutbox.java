package ru.neverland.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/** Durable at-most-once dispatch. An interrupted/declined command needs administrator review. */
public final class CommandOutbox {
    public record Entry(String id,String command,String state,long created){}
    private CommandOutbox(){}
    private static Path file(Plugin plugin){return plugin.getDataFolder().toPath().resolve("command-outbox.yml");}
    public static List<Entry> entries(Plugin plugin) throws IOException { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        Path file=file(plugin);if(!Files.exists(file))return List.of();
        try {
            var y=new YamlConfiguration();y.load(file.toFile());
            if(y.getInt("schema")!=1||!y.isConfigurationSection("commands"))throw new IOException("Unknown command journal schema");
            var values=new ArrayList<Entry>();var section=y.getConfigurationSection("commands");
            for(String id:section.getKeys(false)){
                UUID.fromString(id);String command=SafeYaml.stringValue(section,id+".command");String state=SafeYaml.stringValue(section,id+".state");
                if(!Set.of("QUEUED","REVIEW","DONE").contains(state)||command.isBlank())throw new IOException("Invalid command receipt "+id);
                values.add(new Entry(id,command,state,SafeYaml.longValue(section,id+".created")));
            }
            return List.copyOf(values);
        }catch(Exception ex){throw new IOException("Журнал команд повреждён; выполнение остановлено",ex);}
    }} }
    private static void save(Plugin plugin,List<Entry> entries)throws IOException {
        AtomicFiles.write(file(plugin),()->{var y=new YamlConfiguration();y.set("schema",1);y.createSection("commands");for(var e:entries){String key="commands."+e.id()+".";y.set(key+"command",e.command());y.set(key+"state",e.state());y.set(key+"created",e.created());}return y.saveToString();});
    }
    public static void enqueue(Plugin plugin,String operation,List<String> commands){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        if(commands.isEmpty())return;
        try {
            var entries=new ArrayList<>(entries(plugin));
            for(int i=0;i<commands.size();i++){
                String command=commands.get(i).strip();if(command.startsWith("/"))command=command.substring(1);if(command.isBlank())continue;
                String id=UUID.nameUUIDFromBytes((operation+":"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                final String parsed=command;var existing=entries.stream().filter(e->e.id().equals(id)).findFirst();
                if(existing.isPresent()){if(!existing.get().command().equals(parsed))throw new IOException("Receipt payload changed: "+id);continue;}
                entries.add(new Entry(id,parsed,"QUEUED",System.currentTimeMillis()));
            }
            save(plugin,entries);
            SuiteScheduler.get().runTask(plugin,()->drain(plugin));
        }catch(IOException ex){throw new UncheckedIOException(ex);}
    }} }
    public static void start(Plugin plugin){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {SuiteScheduler.get().runTask(plugin,()->drain(plugin));} }
    public static void drain(Plugin plugin){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        if(SuiteState.frozen(plugin.getName())||!plugin.isEnabled())return;
        try {
            var entries=new ArrayList<>(entries(plugin));
            for(int i=0;i<entries.size();i++){
                Entry e=entries.get(i);if(!e.state().equals("QUEUED"))continue;
                entries.set(i,new Entry(e.id(),e.command(),"REVIEW",e.created()));save(plugin,entries);
                // REVIEW is durable before touching another plugin. Never replay ambiguous effects.
                boolean accepted=false;
                try{accepted=Bukkit.dispatchCommand(Bukkit.getConsoleSender(),e.command());}
                catch(Exception ex){plugin.getLogger().log(java.util.logging.Level.WARNING,"Команда ожидает сверки: "+e.id(),ex);}
                if(accepted){entries.set(i,new Entry(e.id(),e.command(),"DONE",e.created()));save(plugin,entries);}
                else plugin.getLogger().warning("Команда ожидает сверки /nltoutbox: "+e.id());
            }
        }catch(IOException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Очередь команд остановлена",ex);}
    }} }
    public static void resolve(Plugin plugin,String id,String decision)throws IOException { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {
        if(!Set.of("done","retry").contains(decision))throw new IllegalArgumentException("Нужны done или retry после сверки результата");
        var entries=new ArrayList<>(entries(plugin));boolean found=false;
        for(int i=0;i<entries.size();i++){var e=entries.get(i);if(e.id().equals(id)&&e.state().equals("REVIEW")){entries.set(i,new Entry(e.id(),e.command(),decision.equals("done")?"DONE":"QUEUED",e.created()));found=true;}}
        if(!found)throw new IllegalArgumentException("Не найдена команда со статусом REVIEW");save(plugin,entries);
        if(plugin.isEnabled())SuiteScheduler.get().runTask(plugin,()->drain(plugin));
    }} }
}

package ru.neverland.mintespionage.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintespionage.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin; private YamlConfiguration yaml;
    public MessageService(JavaPlugin plugin){this.plugin=plugin;reload();}
    public void reload(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        yaml=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"messages.yml"));
        try(InputStream stream=plugin.getResource("messages.yml")){
            if(stream!=null)yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }catch(Exception exception){plugin.getLogger().warning("Не удалось загрузить messages.yml: "+exception.getMessage());}
    } }
    public String raw(String path){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {String value=yaml.getString(path);return value==null?path:value;} }
    public String text(String path,Map<String,?> replacements,boolean prefix){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        String value=raw(path);for(Map.Entry<String,?> entry:replacements.entrySet())value=value.replace("%"+entry.getKey()+"%",String.valueOf(entry.getValue()));
        return ColorUtil.color((prefix?yaml.getString("prefix",""):"")+value);
    } }
    public void send(CommandSender target,String path){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {send(target,path,Map.of());} }
    public void send(CommandSender target,String path,Map<String,?> replacements){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {target.sendMessage(text(path,replacements,true));} }
    public void help(CommandSender target){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {for(String line:yaml.getStringList("help"))target.sendMessage(ColorUtil.color(line));} }
    public List<String> lines(String path){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return yaml.getStringList(path).stream().map(ColorUtil::color).toList();} }
}

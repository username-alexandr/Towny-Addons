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
    public void reload(){ synchronized (ru.neverland.core.SuiteState.lock()) {
        yaml=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"messages.yml"));
        try(InputStream stream=plugin.getResource("messages.yml")){
            if(stream!=null)yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }catch(Exception exception){plugin.getLogger().warning("Не удалось загрузить messages.yml: "+exception.getMessage());}
    } }
    public String raw(String path){ synchronized (ru.neverland.core.SuiteState.lock()) {String value=yaml.getString(path);return value==null?path:value;} }
    public String text(String path,Map<String,?> replacements,boolean prefix){ synchronized (ru.neverland.core.SuiteState.lock()) {
        String value=raw(path);for(Map.Entry<String,?> entry:replacements.entrySet())value=value.replace("%"+entry.getKey()+"%",String.valueOf(entry.getValue()));
        return ColorUtil.color((prefix?yaml.getString("prefix",""):"")+value);
    } }
    public void send(CommandSender target,String path){ synchronized (ru.neverland.core.SuiteState.lock()) {send(target,path,Map.of());} }
    public void send(CommandSender target,String path,Map<String,?> replacements){ synchronized (ru.neverland.core.SuiteState.lock()) {target.sendMessage(text(path,replacements,true));} }
    public void help(CommandSender target){ synchronized (ru.neverland.core.SuiteState.lock()) {for(String line:yaml.getStringList("help"))target.sendMessage(ColorUtil.color(line));} }
    public List<String> lines(String path){ synchronized (ru.neverland.core.SuiteState.lock()) {return yaml.getStringList(path).stream().map(ColorUtil::color).toList();} }
}

package ru.neverland.townychronicles.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townychronicles.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;private YamlConfiguration yaml;public MessageService(JavaPlugin plugin){this.plugin=plugin;reload();}
    public void reload(){yaml=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"messages.yml"));try(InputStream stream=plugin.getResource("messages.yml")){if(stream!=null)yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));}catch(Exception exception){plugin.getLogger().warning("Не удалось загрузить сообщения: "+exception.getMessage());}}
    public String text(String path,Map<String,?> values,boolean prefix){String result=yaml.getString(path,path);for(Map.Entry<String,?> entry:values.entrySet())result=result.replace("%"+entry.getKey()+"%",String.valueOf(entry.getValue()));return ColorUtil.color((prefix?yaml.getString("prefix",""):"")+result);}
    public void send(CommandSender target,String path){send(target,path,Map.of());}public void send(CommandSender target,String path,Map<String,?> values){target.sendMessage(text(path,values,true));}
    public void help(CommandSender target){yaml.getStringList("help").forEach(line->target.sendMessage(ColorUtil.color(line)));}
}

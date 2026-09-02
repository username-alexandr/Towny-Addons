package ru.neverland.mintexpeditions.service;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintexpeditions.util.ColorUtil;
import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*;
public final class MessageService {
    private final JavaPlugin plugin; private YamlConfiguration yaml;
    public MessageService(JavaPlugin plugin){this.plugin=plugin;reload();}
    public void reload(){yaml=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"messages.yml")); try(InputStream in=plugin.getResource("messages.yml")){if(in!=null)yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));}catch(IOException e){plugin.getLogger().warning(e.getMessage());}}
    public String text(String key,Map<String,?> vars,boolean prefix){String s=yaml.getString(key,key); for(var e:vars.entrySet())s=s.replace("%"+e.getKey()+"%",String.valueOf(e.getValue())); return ColorUtil.color((prefix?yaml.getString("prefix",""):"")+s);}
    public void send(CommandSender to,String key){send(to,key,Map.of());} public void send(CommandSender to,String key,Map<String,?> vars){to.sendMessage(text(key,vars,true));}
    public List<String> list(String key){return yaml.getStringList(key).stream().map(ColorUtil::color).toList();}
}

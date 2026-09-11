package ru.neverland.mintcontracts.service;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcontracts.util.ColorUtil;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private YamlConfiguration yaml;
    public MessageService(JavaPlugin plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        try (InputStream stream = plugin.getResource("messages.yml")) {
            if (stream != null) yaml.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
            try(InputStream oldStream=plugin.getResource("messages-0.2.0.yml")){
                if(oldStream!=null){var old=YamlConfiguration.loadConfiguration(new InputStreamReader(oldStream,StandardCharsets.UTF_8));boolean changed=false;
                    for(String key:old.getKeys(false))if(java.util.Objects.equals(yaml.get(key),old.get(key))&&!java.util.Objects.equals(old.get(key),yaml.getDefaults().get(key))){yaml.set(key,yaml.getDefaults().get(key));changed=true;}
                    if(changed)yaml.save(new File(plugin.getDataFolder(),"messages.yml"));
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Не удалось загрузить встроенные сообщения: " + exception.getMessage());
        }
    }
    public String raw(String key) {
        String value = yaml.getString(key);
        return value == null ? key : value;
    }
    public String text(String key, Map<String, ?> values, boolean prefix) {
        String result = raw(key);
        for (Map.Entry<String, ?> entry : values.entrySet())
            result = result.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        return ColorUtil.color((prefix ? yaml.getString("prefix", "") : "") + result);
    }
    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }
    public void send(CommandSender sender, String key, Map<String, ?> values) { sender.sendMessage(text(key, values, true)); }
    public List<String> list(String key) {
        List<String> out = new ArrayList<>();
        yaml.getStringList(key).forEach(line -> out.add(ColorUtil.color(line)));
        return out;
    }
}

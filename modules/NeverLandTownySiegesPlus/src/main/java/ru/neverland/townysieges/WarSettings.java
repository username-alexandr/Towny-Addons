package ru.neverland.townysieges;
import org.bukkit.configuration.ConfigurationSection;
public record WarSettings(boolean enabled,int holdSeconds) {
    public static WarSettings load(ConfigurationSection config){
        int seconds=config.getInt("war-chunks.empty-hold-seconds",120);
        if(seconds<5||seconds>3600)throw new IllegalArgumentException("war-chunks.empty-hold-seconds: от 5 до 3600 секунд");
        return new WarSettings(config.getBoolean("war-chunks.enabled",true),seconds);
    }
}

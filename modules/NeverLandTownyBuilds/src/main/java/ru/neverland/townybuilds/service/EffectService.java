package ru.neverland.townybuilds.service;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.model.LevelDefinition;
import ru.neverland.townybuilds.model.ProjectDefinition;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class EffectService {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore dataStore;
    private final DefinitionRegistry definitions;
    private BukkitTask task;

    public EffectService(JavaPlugin plugin, TownyHook towny, DataStore dataStore, DefinitionRegistry definitions) {
        this.plugin = plugin;
        this.towny = towny;
        this.dataStore = dataStore;
        this.definitions = definitions;
    }

    public void start() {
        stop();
        long period = Math.max(20L, plugin.getConfig().getLong("settings.effects.refresh-ticks", 200L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void refresh() {
        int duration = Math.max(40, plugin.getConfig().getInt("settings.effects.duration-ticks", 400));
        boolean ambient = plugin.getConfig().getBoolean("settings.effects.ambient", true);
        boolean particles = plugin.getConfig().getBoolean("settings.effects.particles", false);
        boolean icon = plugin.getConfig().getBoolean("settings.effects.icon", true);
        boolean townOnly = plugin.getConfig().getString("settings.effects.scope", "GLOBAL")
                .toUpperCase(Locale.ROOT).equals("TOWN");
        for (Player player : Bukkit.getOnlinePlayers()) {
            Town town = towny.town(player);
            if (town == null) {
                continue;
            }
            if (townOnly) {
                Town currentTown = towny.townAt(player.getLocation());
                if (!town.equals(currentTown)) {
                    continue;
                }
            }
            TownData data = dataStore.town(town.getUUID());
            Map<PotionEffectType, Integer> effects = collectEffects(data);
            for (Map.Entry<PotionEffectType, Integer> effect : effects.entrySet()) {
                int amplifier = Math.max(0, effect.getValue() - 1);
                player.addPotionEffect(new PotionEffect(effect.getKey(), duration, amplifier, ambient, particles, icon));
            }
        }
    }

    private Map<PotionEffectType, Integer> collectEffects(TownData data) {
        Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
        for (ProjectDefinition project : definitions.all()) {
            int levelNumber = data.operationalLevel(project.id());
            if (levelNumber <= 0) {
                continue;
            }
            LevelDefinition level = project.level(levelNumber);
            if (level == null) {
                continue;
            }
            level.effects().forEach((type, amplifier) -> effects.merge(type, amplifier, Math::max));
        }
        return effects;
    }
}

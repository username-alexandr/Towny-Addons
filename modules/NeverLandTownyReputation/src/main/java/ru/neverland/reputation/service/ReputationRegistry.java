package ru.neverland.reputation.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.reputation.model.FeatureDefinition;
import ru.neverland.reputation.model.ReputationScope;
import ru.neverland.reputation.model.ReputationTier;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ReputationRegistry {
    private final JavaPlugin plugin;
    private final List<ReputationTier> tiers = new ArrayList<>();
    private final Map<String, FeatureDefinition> features = new LinkedHashMap<>();
    public ReputationRegistry(JavaPlugin plugin) { this.plugin = plugin; reload(); }
    public void reload() {
        tiers.clear(); features.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "levels.yml"));
        ConfigurationSection levels = yaml.getConfigurationSection("levels");
        if (levels != null) for (String id : levels.getKeys(false)) {
            ConfigurationSection section = levels.getConfigurationSection(id); if (section == null) continue;
            Material material = Material.matchMaterial(section.getString("material", "PAPER"));
            tiers.add(new ReputationTier(id.toLowerCase(Locale.ROOT), section.getString("name", id), section.getInt("minimum-score"),
                    material == null ? Material.PAPER : material, section.getString("itemsadder-icon", ""), List.copyOf(section.getStringList("description")),
                    lowerSet(section.getStringList("privileges")), section.getDouble("trade-discount-percent", 0), section.getDouble("reward-multiplier", 1)));
        }
        tiers.sort(Comparator.comparingInt(ReputationTier::minimumScore));
        ConfigurationSection featureSection = plugin.getConfig().getConfigurationSection("features");
        if (featureSection != null) for (String id : featureSection.getKeys(false)) {
            Set<ReputationScope> scopes = new LinkedHashSet<>();
            for (String value : featureSection.getStringList(id + ".scopes")) { ReputationScope parsed = ReputationScope.parse(value); if (parsed != null) scopes.add(parsed); }
            features.put(id.toLowerCase(Locale.ROOT), new FeatureDefinition(id.toLowerCase(Locale.ROOT), Set.copyOf(scopes), featureSection.getInt(id + ".minimum-score")));
        }
    }
    public ReputationTier tier(int score) {
        ReputationTier selected = tiers.isEmpty() ? new ReputationTier("neutral", "Нейтральная", 0, Material.PAPER, "", List.of(), Set.of(), 0, 1) : tiers.get(0);
        for (ReputationTier tier : tiers) if (score >= tier.minimumScore()) selected = tier; else break;
        return selected;
    }
    public FeatureDefinition feature(String id) { return id == null ? null : features.get(id.toLowerCase(Locale.ROOT)); }
    public List<ReputationTier> tiers() { return List.copyOf(tiers); }
    public Map<String, FeatureDefinition> features() { return Map.copyOf(features); }
    private Set<String> lowerSet(List<String> values) { Set<String> result = new LinkedHashSet<>(); values.forEach(value -> result.add(value.toLowerCase(Locale.ROOT))); return Set.copyOf(result); }
}

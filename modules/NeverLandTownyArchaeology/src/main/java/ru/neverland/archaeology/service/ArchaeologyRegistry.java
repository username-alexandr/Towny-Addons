package ru.neverland.archaeology.service;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.archaeology.model.ArtifactDefinition;
import ru.neverland.archaeology.model.CollectionDefinition;
import ru.neverland.archaeology.model.RarityDefinition;
import ru.neverland.archaeology.model.SiteDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ArchaeologyRegistry {
    private final JavaPlugin plugin; private final Map<String, RarityDefinition> rarities = new LinkedHashMap<>(); private final Map<String, ArtifactDefinition> artifacts = new LinkedHashMap<>(); private final Map<String, CollectionDefinition> collections = new LinkedHashMap<>(); private final Map<String, SiteDefinition> sites = new LinkedHashMap<>(); private final Map<String, Map<String, Integer>> wonderRequirements = new LinkedHashMap<>();
    public ArchaeologyRegistry(JavaPlugin plugin) { this.plugin = plugin; reload(); }
    public void reload() { rarities.clear(); artifacts.clear(); collections.clear(); sites.clear(); wonderRequirements.clear(); loadArtifacts(); loadSites(); }
    private void loadArtifacts() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "artifacts.yml")); ConfigurationSection rarityRoot = yaml.getConfigurationSection("rarities");
        if (rarityRoot != null) for (String id : rarityRoot.getKeys(false)) rarities.put(id, new RarityDefinition(id, rarityRoot.getString(id + ".name", id), rarityRoot.getString(id + ".color", "&f")));
        ConfigurationSection artifactRoot = yaml.getConfigurationSection("artifacts"); if (artifactRoot != null) for (String id : artifactRoot.getKeys(false)) { ConfigurationSection section = artifactRoot.getConfigurationSection(id); if (section == null) continue; Material material = Material.matchMaterial(section.getString("material", "PAPER")); if (material == null || !material.isItem()) material = Material.PAPER; artifacts.put(id, new ArtifactDefinition(id, section.getString("name", id), section.getString("rarity", "common"), material, section.getString("itemsadder-icon", ""), Math.max(0, section.getInt("points", 1)), List.copyOf(section.getStringList("lore")))); }
        ConfigurationSection collectionRoot = yaml.getConfigurationSection("collections"); if (collectionRoot != null) for (String id : collectionRoot.getKeys(false)) { ConfigurationSection section = collectionRoot.getConfigurationSection(id); if (section == null) continue; Material icon = Material.matchMaterial(section.getString("icon", "CHEST")); collections.put(id, new CollectionDefinition(id, section.getString("name", id), icon == null ? Material.CHEST : icon, List.copyOf(section.getStringList("artifacts")))); }
        ConfigurationSection wonderRoot = yaml.getConfigurationSection("wonder-requirements"); if (wonderRoot != null) for (String id : wonderRoot.getKeys(false)) wonderRequirements.put(id, parseWeights(wonderRoot.getStringList(id), "чудо " + id));
    }
    private void loadSites() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "sites.yml")); ConfigurationSection root = yaml.getConfigurationSection("sites"); if (root == null) return;
        for (String id : root.getKeys(false)) { ConfigurationSection section = root.getConfigurationSection(id); if (section == null) continue; Material suspicious = Material.matchMaterial(section.getString("suspicious-block", "SUSPICIOUS_GRAVEL")); if (suspicious != Material.SUSPICIOUS_SAND && suspicious != Material.SUSPICIOUS_GRAVEL) suspicious = Material.SUSPICIOUS_GRAVEL; Set<Material> replaceable = new LinkedHashSet<>(); for (String raw : section.getStringList("replaceable")) { Material material = Material.matchMaterial(raw); if (material != null && material.isBlock()) replaceable.add(material); } Set<String> biomes = new LinkedHashSet<>(); section.getStringList("biomes").forEach(value -> biomes.add(value.toLowerCase(Locale.ROOT))); int min = Math.max(1, section.getInt("minimum-blocks", 2)); int max = Math.max(min, section.getInt("maximum-blocks", min)); sites.put(id, new SiteDefinition(id, section.getString("name", id), Set.copyOf(biomes), suspicious, Set.copyOf(replaceable), min, max, parseWeights(section.getStringList("artifacts"), "раскопки " + id))); }
    }
    private Map<String, Integer> parseWeights(List<String> specifications, String owner) { Map<String, Integer> result = new LinkedHashMap<>(); for (String spec : specifications) try { String[] parts = spec.split(":"); String id = parts[0].toLowerCase(Locale.ROOT); int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1; if (!artifacts.containsKey(id)) { plugin.getLogger().warning("Неизвестный артефакт " + id + " в " + owner); continue; } if (amount > 0) result.put(id, amount); } catch (RuntimeException exception) { plugin.getLogger().warning("Некорректная запись '" + spec + "' в " + owner); } return Map.copyOf(result); }
    public ArtifactDefinition artifact(String id) { return id == null ? null : artifacts.get(id.toLowerCase(Locale.ROOT)); } public Collection<ArtifactDefinition> artifacts() { return List.copyOf(artifacts.values()); }
    public RarityDefinition rarity(String id) { return rarities.getOrDefault(id, new RarityDefinition(id, id, "&f")); } public CollectionDefinition collection(String id) { return collections.get(id); } public Collection<CollectionDefinition> collections() { return List.copyOf(collections.values()); }
    public SiteDefinition site(String id) { return id == null ? null : sites.get(id.toLowerCase(Locale.ROOT)); } public Collection<SiteDefinition> sites() { return List.copyOf(sites.values()); }
    public Map<String, Integer> wonderRequirements(String id) { return Map.copyOf(wonderRequirements.getOrDefault(id, Map.of())); } public Set<String> wonderIds() { return Set.copyOf(wonderRequirements.keySet()); }
    public String randomArtifact(SiteDefinition site) { int total = site.totalWeight(); if (total <= 0) return null; int value = ThreadLocalRandom.current().nextInt(total); for (Map.Entry<String, Integer> entry : site.artifactWeights().entrySet()) { value -= entry.getValue(); if (value < 0) return entry.getKey(); } return null; }
}

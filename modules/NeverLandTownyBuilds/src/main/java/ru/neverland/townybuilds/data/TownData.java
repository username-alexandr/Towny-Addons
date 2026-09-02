package ru.neverland.townybuilds.data;

import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.construction.ConstructionSite;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TownData {
    private final UUID townId;
    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, ConstructionSite> constructionSites = new HashMap<>();
    private final Map<String, ResourceFund> resourceFunds = new HashMap<>();
    private ItemStack[] storage;

    public TownData(UUID townId, int storageSize) {
        this.townId = townId;
        this.storage = new ItemStack[storageSize];
    }

    public UUID townId() {
        return townId;
    }

    public int level(String projectId) {
        return levels.getOrDefault(projectId, 0);
    }

    public void setLevel(String projectId, int level) {
        if (level <= 0) {
            levels.remove(projectId);
        } else {
            levels.put(projectId, level);
        }
    }

    public Map<String, Integer> levels() {
        return Map.copyOf(levels);
    }

    public void loadLevels(Map<String, Integer> loaded) {
        levels.clear();
        levels.putAll(loaded);
    }

    public ConstructionSite constructionSite(String projectId) {
        return constructionSites.get(projectId);
    }

    public void setConstructionSite(ConstructionSite site) {
        constructionSites.put(site.projectId(), site);
    }

    public Map<String, ConstructionSite> constructionSites() {
        return Map.copyOf(constructionSites);
    }

    public void loadConstructionSites(Map<String, ConstructionSite> loaded) {
        constructionSites.clear();
        constructionSites.putAll(loaded);
    }

    public ResourceFund resourceFund(String projectId, int targetLevel) {
        ResourceFund current = resourceFunds.get(projectId);
        if (current == null || current.targetLevel() != targetLevel) {
            current = new ResourceFund(targetLevel);
            resourceFunds.put(projectId, current);
        }
        return current;
    }

    public ResourceFund existingResourceFund(String projectId) {
        return resourceFunds.get(projectId);
    }

    public void clearResourceFund(String projectId) {
        resourceFunds.remove(projectId);
    }

    public Map<String, ResourceFund> resourceFunds() {
        return Map.copyOf(resourceFunds);
    }

    public void loadResourceFunds(Map<String, ResourceFund> loaded) {
        resourceFunds.clear();
        resourceFunds.putAll(loaded);
    }

    public ItemStack[] storage() {
        ItemStack[] copy = new ItemStack[storage.length];
        for (int index = 0; index < storage.length; index++) {
            copy[index] = storage[index] == null ? null : storage[index].clone();
        }
        return copy;
    }

    public void setStorage(ItemStack[] contents, int configuredSize) {
        storage = new ItemStack[configuredSize];
        int length = Math.min(contents.length, configuredSize);
        for (int index = 0; index < length; index++) {
            storage[index] = contents[index] == null ? null : contents[index].clone();
        }
    }
}

package ru.neverland.townybuilds.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProjectDefinition {
    private final String id;
    private final ProjectType type;
    private ProjectCategory category;
    private String name;
    private Material icon;
    private String itemsAdderIcon;
    private int slot;
    private int order;
    private List<String> description;
    private Map<String, Integer> requirements = Map.of();
    private final Map<Integer, LevelDefinition> levels;
    private ItemStack editorIcon;
    private boolean custom;

    public ProjectDefinition(String id, ProjectType type, String name, Material icon, String itemsAdderIcon,
                             int slot, List<String> description, Map<Integer, LevelDefinition> levels) {
        this(id, type, name, icon, itemsAdderIcon, slot, slot, description, levels);
    }

    public ProjectDefinition(String id, ProjectType type, String name, Material icon, String itemsAdderIcon,
                             int slot, int order, List<String> description, Map<Integer, LevelDefinition> levels) {
        this.id = id;
        this.type = type;
        this.category = type == ProjectType.BUILDING ? ProjectCategory.parse(null, id) : ProjectCategory.OTHER;
        this.name = name;
        this.icon = icon;
        this.itemsAdderIcon = itemsAdderIcon;
        this.slot = slot;
        this.order = order;
        this.description = List.copyOf(description);
        this.levels = new LinkedHashMap<>(levels);
    }

    public String id() { synchronized (ru.neverland.core.SuiteState.lock()) { return id; } }
    public ProjectType type() { synchronized (ru.neverland.core.SuiteState.lock()) { return type; } }
    public ProjectCategory category() { synchronized (ru.neverland.core.SuiteState.lock()) { return category; } }
    public String name() { synchronized (ru.neverland.core.SuiteState.lock()) { return name; } }
    public Material icon() { synchronized (ru.neverland.core.SuiteState.lock()) { return icon; } }
    public String itemsAdderIcon() { synchronized (ru.neverland.core.SuiteState.lock()) { return itemsAdderIcon; } }
    public int slot() { synchronized (ru.neverland.core.SuiteState.lock()) { return slot; } }
    public int order() { synchronized (ru.neverland.core.SuiteState.lock()) { return order; } }
    public List<String> description() { synchronized (ru.neverland.core.SuiteState.lock()) { return description; } }
    public Map<String, Integer> requirements() { synchronized (ru.neverland.core.SuiteState.lock()) { return requirements; } }
    public Map<Integer, LevelDefinition> levels() { synchronized (ru.neverland.core.SuiteState.lock()) { return Collections.unmodifiableMap(levels); } }
    public int maxLevel() { synchronized (ru.neverland.core.SuiteState.lock()) { return levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1); } }
    public LevelDefinition level(int level) { synchronized (ru.neverland.core.SuiteState.lock()) { return levels.get(level); } }
    public ItemStack editorIcon() { synchronized (ru.neverland.core.SuiteState.lock()) { return editorIcon == null ? null : editorIcon.clone(); } }
    public boolean custom() { synchronized (ru.neverland.core.SuiteState.lock()) { return custom; } }

    public void setName(String name) { synchronized (ru.neverland.core.SuiteState.lock()) { this.name = name; } }
    public void setCategory(ProjectCategory category) { synchronized (ru.neverland.core.SuiteState.lock()) { this.category = category == null ? ProjectCategory.OTHER : category; } }
    public void setIcon(Material icon) { synchronized (ru.neverland.core.SuiteState.lock()) { this.icon = icon; } }
    public void setItemsAdderIcon(String itemsAdderIcon) { synchronized (ru.neverland.core.SuiteState.lock()) { this.itemsAdderIcon = itemsAdderIcon; } }
    public void setSlot(int slot) { synchronized (ru.neverland.core.SuiteState.lock()) { this.slot = slot; } }
    public void setOrder(int order) { synchronized (ru.neverland.core.SuiteState.lock()) { this.order = order; } }
    public void setDescription(List<String> description) { synchronized (ru.neverland.core.SuiteState.lock()) { this.description = List.copyOf(description); } }
    public void setRequirements(Map<String, Integer> requirements) { synchronized (ru.neverland.core.SuiteState.lock()) {
        this.requirements = Collections.unmodifiableMap(new LinkedHashMap<>(requirements));
    } }
    public void setEditorIcon(ItemStack editorIcon) { synchronized (ru.neverland.core.SuiteState.lock()) { this.editorIcon = editorIcon == null ? null : editorIcon.clone(); } }
    public void setCustom(boolean custom) { synchronized (ru.neverland.core.SuiteState.lock()) { this.custom = custom; } }

    public void setLevel(LevelDefinition definition) { synchronized (ru.neverland.core.SuiteState.lock()) {
        levels.put(definition.level(), definition);
    } }
}

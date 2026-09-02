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

    public String id() { return id; }
    public ProjectType type() { return type; }
    public ProjectCategory category() { return category; }
    public String name() { return name; }
    public Material icon() { return icon; }
    public String itemsAdderIcon() { return itemsAdderIcon; }
    public int slot() { return slot; }
    public int order() { return order; }
    public List<String> description() { return description; }
    public Map<Integer, LevelDefinition> levels() { return Collections.unmodifiableMap(levels); }
    public int maxLevel() { return levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1); }
    public LevelDefinition level(int level) { return levels.get(level); }
    public ItemStack editorIcon() { return editorIcon == null ? null : editorIcon.clone(); }
    public boolean custom() { return custom; }

    public void setName(String name) { this.name = name; }
    public void setCategory(ProjectCategory category) { this.category = category == null ? ProjectCategory.OTHER : category; }
    public void setIcon(Material icon) { this.icon = icon; }
    public void setItemsAdderIcon(String itemsAdderIcon) { this.itemsAdderIcon = itemsAdderIcon; }
    public void setSlot(int slot) { this.slot = slot; }
    public void setOrder(int order) { this.order = order; }
    public void setDescription(List<String> description) { this.description = List.copyOf(description); }
    public void setEditorIcon(ItemStack editorIcon) { this.editorIcon = editorIcon == null ? null : editorIcon.clone(); }
    public void setCustom(boolean custom) { this.custom = custom; }

    public void setLevel(LevelDefinition definition) {
        levels.put(definition.level(), definition);
    }
}

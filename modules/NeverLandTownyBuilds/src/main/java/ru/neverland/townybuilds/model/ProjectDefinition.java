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

    public String id() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return id; } }
    public ProjectType type() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return type; } }
    public ProjectCategory category() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return category; } }
    public String name() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { return name; } }
    public Material icon() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return icon; } }
    public String itemsAdderIcon() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return itemsAdderIcon; } }
    public int slot() { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { return slot; } }
    public int order() { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { return order; } }
    public List<String> description() { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return description; } }
    public Map<String, Integer> requirements() { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) { return requirements; } }
    public Map<Integer, LevelDefinition> levels() { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return Collections.unmodifiableMap(levels); } }
    public int maxLevel() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { return levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1); } }
    public LevelDefinition level(int level) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { return levels.get(level); } }
    public ItemStack editorIcon() { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { return editorIcon == null ? null : editorIcon.clone(); } }
    public boolean custom() { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) { return custom; } }

    public void setName(String name) { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) { this.name = name; } }
    public void setCategory(ProjectCategory category) { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) { this.category = category == null ? ProjectCategory.OTHER : category; } }
    public void setIcon(Material icon) { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) { this.icon = icon; } }
    public void setItemsAdderIcon(String itemsAdderIcon) { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) { this.itemsAdderIcon = itemsAdderIcon; } }
    public void setSlot(int slot) { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) { this.slot = slot; } }
    public void setOrder(int order) { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) { this.order = order; } }
    public void setDescription(List<String> description) { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) { this.description = List.copyOf(description); } }
    public void setRequirements(Map<String, Integer> requirements) { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) {
        this.requirements = Collections.unmodifiableMap(new LinkedHashMap<>(requirements));
    } }
    public void setEditorIcon(ItemStack editorIcon) { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) { this.editorIcon = editorIcon == null ? null : editorIcon.clone(); } }
    public void setCustom(boolean custom) { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) { this.custom = custom; } }

    public void setLevel(LevelDefinition definition) { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) {
        levels.put(definition.level(), definition);
    } }
}

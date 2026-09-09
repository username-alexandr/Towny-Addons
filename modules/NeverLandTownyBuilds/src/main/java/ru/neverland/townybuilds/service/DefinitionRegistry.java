package ru.neverland.townybuilds.service;
import ru.neverland.localization.MaterialNameConfig;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import ru.neverland.townybuilds.integration.ItemsAdderHook;
import ru.neverland.townybuilds.model.LevelDefinition;
import ru.neverland.townybuilds.model.ProjectDefinition;
import ru.neverland.townybuilds.model.ProjectCategory;
import ru.neverland.townybuilds.model.ProjectType;
import ru.neverland.townybuilds.util.ItemCodec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DefinitionRegistry {
    private final JavaPlugin plugin;
    private final ItemsAdderHook itemsAdder;
    private final File defaultsFile;
    private final File customFile;
    private final Map<String, ProjectDefinition> projects = new LinkedHashMap<>();

    public DefinitionRegistry(JavaPlugin plugin, ItemsAdderHook itemsAdder) {
        this.plugin = plugin;
        this.itemsAdder = itemsAdder;
        defaultsFile = new File(plugin.getDataFolder(), "projects.yml");
        customFile = new File(plugin.getDataFolder(), "custom-projects.yml");
        reload();
    }

    public void reload() {
        projects.clear();
        // Сначала читаем встроенный каталог. Так обновление добавляет новые проекты даже
        // при сохранённом старом projects.yml, а значения администратора загружаются поверх.
        try (InputStream stream = plugin.getResource("projects.yml")) {
            if (stream != null) {
                loadFile(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8)), false);
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось прочитать встроенный projects.yml: " + exception.getMessage());
        }
        if (defaultsFile.exists()) {
            loadFile(YamlConfiguration.loadConfiguration(defaultsFile), false);
        }
        if (customFile.exists()) {
            loadFile(YamlConfiguration.loadConfiguration(customFile), true);
        }
        rebalanceResources();
        plugin.getLogger().info("Загружено городских проектов: " + projects.size());
    }

    private void rebalanceResources() {
        if (!plugin.getConfig().getBoolean("settings.resources.blueprint-budget", true)) return;
        var generator = new ru.neverland.townybuilds.construction.BuildingBlueprintGenerator();
        for (ProjectDefinition project : projects.values()) {
            if (project.custom() || !generator.supportedProjects().contains(project.id())) continue;
            for (LevelDefinition level : new ArrayList<>(project.levels().values())) {
                List<ItemStack> items = level.resources();
                // NBT/ItemsAdder prices configured by the editor remain explicit.
                if (items.stream().anyMatch(ItemStack::hasItemMeta)) continue;
                var costs = items.stream().map(i -> new ResourceBudget.Cost(i.getType().name(), i.getAmount())).toList();
                var balanced = ResourceBudget.balance(generator, project.id(), level.level(),
                        project.type() == ProjectType.WONDER, costs);
                List<ItemStack> updated = balanced.stream()
                        .map(c -> new ItemStack(Material.valueOf(c.material()), c.amount())).toList();
                project.setLevel(new LevelDefinition(level.level(), level.money(), level.bonusBlocks(),
                        updated, level.effects(), level.commands()));
            }
        }
    }

    private void loadFile(YamlConfiguration yaml, boolean custom) {
        loadSection(yaml.getConfigurationSection("buildings"), ProjectType.BUILDING, custom);
        loadSection(yaml.getConfigurationSection("wonders"), ProjectType.WONDER, custom);
    }

    private void loadSection(ConfigurationSection section, ProjectType type, boolean custom) {
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection projectSection = section.getConfigurationSection(id);
            if (projectSection == null) {
                continue;
            }
            try {
                String normalizedId = id.toLowerCase(Locale.ROOT);
                ProjectDefinition inherited = projects.get(normalizedId);
                ProjectDefinition definition = parseProject(normalizedId, type, projectSection, custom);
                // Старые projects.yml не содержат появившееся позднее поле requires.
                // Наследуем только отсутствующее поле из встроенного каталога; явно
                // заданные администратором требования по-прежнему имеют приоритет.
                definition.setRequirements(resolveRequirements(
                        projectSection.contains("requires"),
                        definition.requirements(),
                        inherited == null ? Map.of() : inherited.requirements()));
                definition.setCustom(custom);
                projects.put(definition.id(), definition);
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Проект " + id + " пропущен: " + exception.getMessage());
            }
        }
    }

    private ProjectDefinition parseProject(String id, ProjectType type, ConfigurationSection section, boolean custom) {
        Material icon = MaterialNameConfig.matchMaterial(section.getString("icon", "STONE"));
        if (icon == null || !icon.isItem()) {
            icon = Material.STONE;
        }
        icon = ProjectIcons.resolve(id, icon, custom);
        Map<Integer, LevelDefinition> levels = new LinkedHashMap<>();
        ConfigurationSection levelRoot = section.getConfigurationSection("levels");
        if (levelRoot == null) {
            throw new IllegalArgumentException("не определены уровни");
        }
        for (String rawLevel : levelRoot.getKeys(false)) {
            int level = Integer.parseInt(rawLevel);
            ConfigurationSection levelSection = levelRoot.getConfigurationSection(rawLevel);
            if (levelSection == null) {
                continue;
            }
            levels.put(level, parseLevel(level, levelSection));
        }
        ProjectDefinition definition = new ProjectDefinition(id, type,
                section.getString("name", id), icon, section.getString("itemsadder-icon", ""),
                Math.max(0, Math.min(53, section.getInt("slot", 10))),
                Math.max(0, section.getInt("order", section.getInt("slot", 10))),
                section.getStringList("description"), levels);
        definition.setCategory(type == ProjectType.BUILDING
                ? ProjectCategory.parse(section.getString("category"), id) : ProjectCategory.OTHER);
        ConfigurationSection requirementSection = section.getConfigurationSection("requires");
        if (requirementSection != null) {
            Map<String, Integer> requirements = new LinkedHashMap<>();
            for (String projectId : requirementSection.getKeys(false)) {
                int requiredLevel = Math.max(1, requirementSection.getInt(projectId));
                requirements.put(projectId.toLowerCase(Locale.ROOT), requiredLevel);
            }
            definition.setRequirements(requirements);
        }
        String encodedIcon = section.getString("icon-base64");
        if (encodedIcon != null && !encodedIcon.isBlank()) {
            try {
                definition.setEditorIcon(ItemCodec.decodeSingle(encodedIcon));
            } catch (IOException | ClassNotFoundException exception) {
                plugin.getLogger().warning("Не удалось прочитать иконку проекта " + id + ": " + exception.getMessage());
            }
        }
        return definition;
    }

    private LevelDefinition parseLevel(int level, ConfigurationSection section) {
        List<ItemStack> resources = new ArrayList<>();
        for (String encoded : section.getStringList("resources-base64")) {
            try {
                ItemStack item = ItemCodec.decodeSingle(encoded);
                if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                    resources.add(item);
                }
            } catch (IOException | ClassNotFoundException exception) {
                plugin.getLogger().warning("Не удалось прочитать NBT-ресурс уровня " + level + ": " + exception.getMessage());
            }
        }
        for (String specification : section.getStringList("resources")) {
            ItemStack item = parseResource(specification);
            if (item != null) {
                resources.add(item);
            }
        }
        Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
        for (String specification : section.getStringList("effects")) {
            String[] parts = specification.split(":");
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Неизвестный эффект: " + specification);
                continue;
            }
            int amplifier = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1])) : 1;
            effects.put(type, amplifier);
        }
        return new LevelDefinition(level, section.getDouble("money"), section.getInt("bonus-blocks"),
                resources, effects, section.getStringList("commands"));
    }

    private ItemStack parseResource(String specification) {
        try {
            if (specification.toLowerCase(Locale.ROOT).startsWith("itemsadder:")) {
                String[] parts = specification.split(":");
                if (parts.length < 4) {
                    throw new IllegalArgumentException("формат ItemsAdder: itemsadder:namespace:id:amount");
                }
                int amount = Integer.parseInt(parts[parts.length - 1]);
                String id = parts[1] + ":" + parts[2];
                ItemStack custom = itemsAdder.item(id, amount);
                if (custom == null) {
                    plugin.getLogger().warning("Ресурс ItemsAdder недоступен: " + id);
                }
                return custom;
            }
            String[] parts = specification.split(":");
            Material material = resolveResourceMaterial(parts[0]);
            if (material == null || !material.isItem()) {
                throw new IllegalArgumentException("неизвестный материал " + parts[0]);
            }
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            return new ItemStack(material, Math.max(1, amount));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Некорректный ресурс '" + specification + "': " + exception.getMessage());
            return null;
        }
    }

    static Material resolveResourceMaterial(String name) {
        // Saved projects.yml files may still use the name from before iron/copper chains.
        return MaterialNameConfig.matchMaterial(name);
    }

    static Map<String, Integer> resolveRequirements(boolean explicitlyConfigured,
                                                     Map<String, Integer> configured,
                                                     Map<String, Integer> inherited) {
        return explicitlyConfigured || inherited.isEmpty() ? configured : inherited;
    }

    public ProjectDefinition project(String id) {
        return projects.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<ProjectDefinition> all() {
        return projects.values().stream().sorted(Comparator.comparing(ProjectDefinition::type)
                .thenComparingInt(ProjectDefinition::order)
                .thenComparingInt(ProjectDefinition::slot)
                .thenComparing(ProjectDefinition::id)).toList();
    }

    public List<ProjectDefinition> type(ProjectType type) {
        return projects.values().stream().filter(project -> project.type() == type)
                .sorted(Comparator.comparingInt(ProjectDefinition::order)
                        .thenComparingInt(ProjectDefinition::slot)
                .thenComparing(ProjectDefinition::id)).toList();
    }

    public List<ProjectDefinition> category(ProjectCategory category) {
        return projects.values().stream().filter(project -> project.type() == ProjectType.BUILDING)
                .filter(project -> project.category() == category)
                .sorted(Comparator.comparingInt(ProjectDefinition::order)
                        .thenComparingInt(ProjectDefinition::slot)
                        .thenComparing(ProjectDefinition::id)).toList();
    }

    public ProjectDefinition create(String id, ProjectType type) {
        String normalized = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (normalized.isBlank() || projects.containsKey(normalized)) {
            return null;
        }
        Map<Integer, LevelDefinition> levels = new LinkedHashMap<>();
        levels.put(1, new LevelDefinition(1, 0, 0, List.of(), Map.of(), List.of()));
        ProjectDefinition project = new ProjectDefinition(normalized, type, "&fНовый проект", Material.BRICKS,
                "", firstFreeSlot(type), List.of("Описание не задано."), levels);
        project.setCustom(true);
        projects.put(normalized, project);
        saveCustomProjects();
        return project;
    }

    public void ensureCustom(ProjectDefinition project) {
        project.setCustom(true);
    }

    private int firstFreeSlot(ProjectType type) {
        for (int slot = 10; slot < 44; slot++) {
            final int checked = slot;
            if (slot % 9 != 0 && slot % 9 != 8 && projects.values().stream()
                    .noneMatch(project -> project.type() == type && project.slot() == checked)) {
                return slot;
            }
        }
        return 10;
    }

    public void saveCustomProjects() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (ProjectDefinition project : projects.values()) {
            if (!project.custom()) {
                continue;
            }
            String root = (project.type() == ProjectType.BUILDING ? "buildings." : "wonders.") + project.id();
            yaml.set(root + ".name", project.name());
            yaml.set(root + ".category", project.category().name());
            yaml.set(root + ".icon", project.icon().name());
            yaml.set(root + ".itemsadder-icon", project.itemsAdderIcon());
            yaml.set(root + ".slot", project.slot());
            yaml.set(root + ".order", project.order());
            yaml.set(root + ".description", project.description());
            yaml.set(root + ".requires", project.requirements().isEmpty() ? null : project.requirements());
            if (project.editorIcon() != null) {
                try {
                    yaml.set(root + ".icon-base64", ItemCodec.encodeSingle(project.editorIcon()));
                } catch (IOException exception) {
                    plugin.getLogger().warning("Не удалось сохранить иконку " + project.id());
                }
            }
            for (LevelDefinition level : project.levels().values()) {
                String levelRoot = root + ".levels." + level.level();
                yaml.set(levelRoot + ".money", level.money());
                yaml.set(levelRoot + ".bonus-blocks", level.bonusBlocks());
                List<String> encoded = new ArrayList<>();
                for (ItemStack item : level.resources()) {
                    try {
                        encoded.add(ItemCodec.encodeSingle(item));
                    } catch (IOException exception) {
                        plugin.getLogger().warning("Не удалось сохранить ресурс " + project.id());
                    }
                }
                yaml.set(levelRoot + ".resources-base64", encoded);
                yaml.set(levelRoot + ".effects", level.effects().entrySet().stream()
                        .map(entry -> entry.getKey().getKey().getKey().toUpperCase(Locale.ROOT) + ":" + entry.getValue()).toList());
                yaml.set(levelRoot + ".commands", level.commands());
            }
        }
        try {
            yaml.save(customFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить custom-projects.yml: " + exception.getMessage());
        }
    }
}

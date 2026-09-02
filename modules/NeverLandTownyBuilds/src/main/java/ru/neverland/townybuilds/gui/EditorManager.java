package ru.neverland.townybuilds.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import ru.neverland.townybuilds.model.LevelDefinition;
import ru.neverland.townybuilds.model.ProjectDefinition;
import ru.neverland.townybuilds.model.ProjectType;
import ru.neverland.townybuilds.service.DefinitionRegistry;
import ru.neverland.townybuilds.service.MessageService;
import ru.neverland.townybuilds.util.ColorUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class EditorManager implements Listener {
    private final JavaPlugin plugin;
    private final DefinitionRegistry definitions;
    private final MessageService messages;
    private final NamespacedKey actionKey;
    private final NamespacedKey projectKey;
    private final NamespacedKey effectKey;
    private final Map<UUID, InputRequest> requests = new HashMap<>();
    private final Map<UUID, Boolean> resourceSave = new HashMap<>();

    public EditorManager(JavaPlugin plugin, DefinitionRegistry definitions, MessageService messages) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.messages = messages;
        actionKey = new NamespacedKey(plugin, "editor_action");
        projectKey = new NamespacedKey(plugin, "editor_project");
        effectKey = new NamespacedKey(plugin, "editor_effect");
    }

    public void openIndex(Player player) {
        Inventory inventory = Bukkit.createInventory(new EditorIndexHolder(), 54,
                ColorUtil.component("&#B65CFFРедактор городских проектов"));
        fill(inventory);
        List<Integer> slots = contentSlots();
        int index = 0;
        for (ProjectDefinition project : definitions.all()) {
            if (index >= slots.size() - 2) break;
            ItemStack item = icon(project);
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(projectKey, PersistentDataType.STRING, project.id());
            item.setItemMeta(meta);
            inventory.setItem(slots.get(index++), item);
        }
        inventory.setItem(47, action(Material.BRICKS, "create_building", "&#63E6BEСоздать здание",
                List.of("&7Введите ID в чат и настройте проект.")));
        inventory.setItem(51, action(Material.END_CRYSTAL, "create_wonder", "&#FFD45AСоздать чудо",
                List.of("&7Введите ID в чат и настройте проект.")));
        player.openInventory(inventory);
    }

    public void openProject(Player player, ProjectDefinition project, int levelNumber) {
        int level = Math.max(1, Math.min(project.maxLevel(), levelNumber));
        Inventory inventory = Bukkit.createInventory(new ProjectEditorHolder(project.id(), level), 54,
                ColorUtil.component("&#B65CFFРедактор: " + project.name()));
        fill(inventory);
        inventory.setItem(4, icon(project));
        inventory.setItem(10, action(Material.NAME_TAG, "name", "&fНазвание",
                List.of("&7Сейчас: " + project.name(), "&7Поддерживается &#RRGGBB", "&eНажмите и введите в чат")));
        inventory.setItem(11, action(Material.ITEM_FRAME, "icon", "&fИконка из руки",
                List.of("&7Возьмите предмет в основную руку", "&7и нажмите эту кнопку.")));
        inventory.setItem(12, action(Material.HOPPER, "slot", "&fСлот в меню",
                List.of("&7Сейчас: &e" + project.slot(), "&7Допустимо: 0–53")));
        inventory.setItem(13, action(Material.WRITABLE_BOOK, "description", "&fОписание",
                descriptionLore(project)));
        inventory.setItem(19, action(Material.GOLD_INGOT, "money", "&fЦена уровня " + level,
                List.of("&7Сейчас: &e" + project.level(level).money(), "&eНажмите и введите число")));
        inventory.setItem(20, action(Material.FILLED_MAP, "bonus", "&fБонусные чанки",
                List.of("&7Сейчас: &a+" + project.level(level).bonusBlocks(), "&eНажмите и введите число")));
        inventory.setItem(28, action(Material.CHEST, "resources", "&#63E6BEРесурсы уровня " + level,
                List.of("&7Настройка предметами с полным NBT.", "&7Позиций: &f" + project.level(level).resources().size())));
        inventory.setItem(30, action(Material.POTION, "effects", "&#B65CFFЭффекты уровня " + level,
                List.of("&7Выберите эффекты и их силу.")));
        inventory.setItem(32, action(Material.COMMAND_BLOCK, "commands", "&#FF8A5BКонсольные команды",
                commandLore(project.level(level))));
        inventory.setItem(45, action(Material.ARROW, "previous_level", "&fПредыдущий уровень",
                List.of("&7Текущий: &f" + level + "&8/&f" + project.maxLevel())));
        inventory.setItem(49, action(Material.LIME_DYE, "save", "&a&lСОХРАНИТЬ",
                List.of("&7Записать изменения в custom-projects.yml")));
        inventory.setItem(53, action(level < project.maxLevel() ? Material.ARROW : Material.NETHER_STAR,
                level < project.maxLevel() ? "next_level" : "add_level",
                level < project.maxLevel() ? "&fСледующий уровень" : "&#63E6BEДобавить уровень",
                List.of("&7Поддерживается до 5 уровней.")));
        player.openInventory(inventory);
    }

    private void openResources(Player player, ProjectDefinition project, int level) {
        Inventory inventory = Bukkit.createInventory(new ResourceEditorHolder(project.id(), level), 54,
                ColorUtil.component("&#63E6BEРесурсы: уровень " + level));
        int slot = 0;
        for (ItemStack resource : project.level(level).resources()) {
            if (slot >= 45) break;
            inventory.setItem(slot++, resource.clone());
        }
        for (int index = 45; index < 54; index++) {
            inventory.setItem(index, simple(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(45, action(Material.ARROW, "resource_back", "&cОтмена", List.of("&7Не сохранять изменения.")));
        inventory.setItem(49, action(Material.LIME_DYE, "resource_save", "&a&lСОХРАНИТЬ",
                List.of("&7Сохранить предметы из верхних 45 слотов.")));
        resourceSave.put(player.getUniqueId(), false);
        player.openInventory(inventory);
    }

    private void openEffects(Player player, ProjectDefinition project, int level) {
        Inventory inventory = Bukkit.createInventory(new EffectEditorHolder(project.id(), level), 54,
                ColorUtil.component("&#B65CFFЭффекты: уровень " + level));
        fill(inventory);
        int slotIndex = 0;
        List<Integer> slots = contentSlots();
        Map<PotionEffectType, Integer> selected = project.level(level).effects();
        for (PotionEffectType type : Registry.MOB_EFFECT.stream()
                .sorted(Comparator.comparing(effect -> effect.getKey().getKey())).toList()) {
            if (slotIndex >= slots.size()) break;
            int strength = selected.getOrDefault(type, 0);
            ItemStack item = simple(strength > 0 ? Material.POTION : Material.GLASS_BOTTLE,
                    (strength > 0 ? "&#63E6BE" : "&7") + readable(type.getKey().getKey()),
                    List.of("&7Сила: " + (strength == 0 ? "&cвыключено" : "&a" + strength),
                            "&eЛКМ: переключить 0 → 1 → 2 → 3"));
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, type.getKey().getKey());
            item.setItemMeta(meta);
            inventory.setItem(slots.get(slotIndex++), item);
        }
        inventory.setItem(49, action(Material.ARROW, "effect_back", "&fНазад", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof EditorIndexHolder) {
            event.setCancelled(true);
            String id = project(event.getCurrentItem());
            if (id != null && definitions.project(id) != null) {
                openProject(player, definitions.project(id), 1);
                return;
            }
            String action = action(event.getCurrentItem());
            if ("create_building".equals(action) || "create_wonder".equals(action)) {
                ProjectType type = action.endsWith("wonder") ? ProjectType.WONDER : ProjectType.BUILDING;
                request(player, new InputRequest(InputType.CREATE, null, 1, type),
                        "&fВведите технический ID латиницей, например &eneverland_bank&f. Напишите &cотмена&f для выхода.");
            }
            return;
        }
        if (holder instanceof ProjectEditorHolder editor) {
            event.setCancelled(true);
            ProjectDefinition project = definitions.project(editor.projectId());
            if (project == null) return;
            String action = action(event.getCurrentItem());
            if (action == null) return;
            switch (action) {
                case "name" -> request(player, new InputRequest(InputType.NAME, project.id(), editor.level(), project.type()), null);
                case "slot" -> request(player, new InputRequest(InputType.SLOT, project.id(), editor.level(), project.type()), null);
                case "money" -> request(player, new InputRequest(InputType.MONEY, project.id(), editor.level(), project.type()),
                        "&fВведите стоимость уровня числом. Напишите &cотмена&f для выхода.");
                case "bonus" -> request(player, new InputRequest(InputType.BONUS, project.id(), editor.level(), project.type()),
                        "&fВведите количество бонусных чанков. Напишите &cотмена&f для выхода.");
                case "description" -> {
                    if (event.isRightClick()) {
                        mutate(project, () -> project.setDescription(List.of()));
                        openProject(player, project, editor.level());
                    } else {
                        request(player, new InputRequest(InputType.DESCRIPTION, project.id(), editor.level(), project.type()),
                                "&fВведите новую строку описания. ПКМ по кнопке очищает описание.");
                    }
                }
                case "commands" -> {
                    if (event.isRightClick()) {
                        LevelDefinition old = project.level(editor.level());
                        setLevel(project, old, old.money(), old.bonusBlocks(), old.resources(), old.effects(), List.of());
                        persist(project);
                        openProject(player, project, editor.level());
                    } else {
                        request(player, new InputRequest(InputType.COMMAND, project.id(), editor.level(), project.type()), null);
                    }
                }
                case "icon" -> {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) {
                        player.sendMessage(ColorUtil.component("&cВозьмите нужный предмет в основную руку."));
                    } else {
                        mutate(project, () -> project.setEditorIcon(hand.clone()));
                        openProject(player, project, editor.level());
                    }
                }
                case "resources" -> openResources(player, project, editor.level());
                case "effects" -> openEffects(player, project, editor.level());
                case "previous_level" -> openProject(player, project, Math.max(1, editor.level() - 1));
                case "next_level" -> openProject(player, project, Math.min(project.maxLevel(), editor.level() + 1));
                case "add_level" -> {
                    if (project.maxLevel() < 5) {
                        LevelDefinition old = project.level(project.maxLevel());
                        project.setLevel(new LevelDefinition(project.maxLevel() + 1, old.money(), old.bonusBlocks(),
                                old.resources(), old.effects(), old.commands()));
                        persist(project);
                        openProject(player, project, project.maxLevel());
                    }
                }
                case "save" -> {
                    persist(project);
                    messages.send(player, "editor-saved");
                    openIndex(player);
                }
            }
            return;
        }
        if (holder instanceof ResourceEditorHolder resources) {
            event.setCancelled(true);
            String action = action(event.getCurrentItem());
            ProjectDefinition project = definitions.project(resources.projectId());
            if (project == null) return;
            if ("resource_back".equals(action)) {
                resourceSave.put(player.getUniqueId(), false);
                openProject(player, project, resources.level());
                return;
            }
            if ("resource_save".equals(action)) {
                saveResources(player, top, project, resources.level());
                resourceSave.put(player.getUniqueId(), true);
                openProject(player, project, resources.level());
                return;
            }
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(top) && event.getRawSlot() < 45) {
                ItemStack cursor = event.getCursor();
                top.setItem(event.getRawSlot(), cursor.getType().isAir() ? null : cursor.clone());
                return;
            }
            if (event.getClickedInventory() != null && !event.getClickedInventory().equals(top) && event.isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && !clicked.getType().isAir()) {
                    int empty = firstEmpty(top, 45);
                    if (empty >= 0) top.setItem(empty, clicked.clone());
                }
            }
            return;
        }
        if (holder instanceof EffectEditorHolder effects) {
            event.setCancelled(true);
            ProjectDefinition project = definitions.project(effects.projectId());
            if (project == null) return;
            if ("effect_back".equals(action(event.getCurrentItem()))) {
                openProject(player, project, effects.level());
                return;
            }
            String effectName = effect(event.getCurrentItem());
            if (effectName == null) return;
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(effectName));
            if (type == null) return;
            LevelDefinition old = project.level(effects.level());
            Map<PotionEffectType, Integer> updated = new LinkedHashMap<>(old.effects());
            int next = (updated.getOrDefault(type, 0) + 1) % 4;
            if (next == 0) updated.remove(type); else updated.put(type, next);
            setLevel(project, old, old.money(), old.bonusBlocks(), old.resources(), updated, old.commands());
            persist(project);
            openEffects(player, project, effects.level());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof ResourceEditorHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof ResourceEditorHolder) {
            resourceSave.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        InputRequest request = requests.remove(event.getPlayer().getUniqueId());
        if (request == null) return;
        event.setCancelled(true);
        String input = ColorUtil.plain(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(event.getPlayer(), request, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
        resourceSave.remove(event.getPlayer().getUniqueId());
    }

    private void applyInput(Player player, InputRequest request, String input) {
        if (input.equalsIgnoreCase("отмена") || input.equalsIgnoreCase("cancel")) {
            if (request.projectId() == null) openIndex(player);
            else openProject(player, definitions.project(request.projectId()), request.level());
            return;
        }
        if (request.type() == InputType.CREATE) {
            ProjectDefinition created = definitions.create(input, request.projectType());
            if (created == null) {
                player.sendMessage(ColorUtil.component("&cТакой ID уже существует или введён неверно."));
                openIndex(player);
            } else {
                openProject(player, created, 1);
            }
            return;
        }
        ProjectDefinition project = definitions.project(request.projectId());
        if (project == null) {
            openIndex(player);
            return;
        }
        try {
            switch (request.type()) {
                case NAME -> mutate(project, () -> project.setName(input));
                case SLOT -> {
                    int slot = Integer.parseInt(input);
                    if (slot < 0 || slot > 53) throw new NumberFormatException();
                    mutate(project, () -> project.setSlot(slot));
                }
                case MONEY -> {
                    double money = Double.parseDouble(input.replace(',', '.'));
                    if (money < 0) throw new NumberFormatException();
                    LevelDefinition old = project.level(request.level());
                    setLevel(project, old, money, old.bonusBlocks(), old.resources(), old.effects(), old.commands());
                    persist(project);
                }
                case BONUS -> {
                    int bonus = Integer.parseInt(input);
                    if (bonus < 0) throw new NumberFormatException();
                    LevelDefinition old = project.level(request.level());
                    setLevel(project, old, old.money(), bonus, old.resources(), old.effects(), old.commands());
                    persist(project);
                }
                case DESCRIPTION -> {
                    List<String> description = new ArrayList<>(project.description());
                    description.add(input);
                    mutate(project, () -> project.setDescription(description));
                }
                case COMMAND -> {
                    LevelDefinition old = project.level(request.level());
                    List<String> commands = new ArrayList<>(old.commands());
                    commands.add(input.startsWith("/") ? input.substring(1) : input);
                    setLevel(project, old, old.money(), old.bonusBlocks(), old.resources(), old.effects(), commands);
                    persist(project);
                }
                default -> { }
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(ColorUtil.component("&cВведено некорректное число."));
        }
        openProject(player, project, request.level());
    }

    private void saveResources(Player player, Inventory inventory, ProjectDefinition project, int level) {
        List<ItemStack> resources = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            merge(resources, item.clone());
        }
        LevelDefinition old = project.level(level);
        setLevel(project, old, old.money(), old.bonusBlocks(), resources, old.effects(), old.commands());
        persist(project);
        player.sendMessage(ColorUtil.component("&aРесурсы уровня сохранены."));
    }

    private void merge(List<ItemStack> target, ItemStack added) {
        for (ItemStack existing : target) {
            if (existing.isSimilar(added)) {
                existing.setAmount(existing.getAmount() + added.getAmount());
                return;
            }
        }
        target.add(added);
    }

    private void setLevel(ProjectDefinition project, LevelDefinition old, double money, int bonus,
                          List<ItemStack> resources, Map<PotionEffectType, Integer> effects, List<String> commands) {
        project.setLevel(new LevelDefinition(old.level(), money, bonus, resources, effects, commands));
    }

    private void mutate(ProjectDefinition project, Runnable mutation) {
        mutation.run();
        persist(project);
    }

    private void persist(ProjectDefinition project) {
        definitions.ensureCustom(project);
        definitions.saveCustomProjects();
    }

    private void request(Player player, InputRequest request, String customPrompt) {
        requests.put(player.getUniqueId(), request);
        player.closeInventory();
        scheduleRequestTimeout(player, request);
        if (customPrompt != null) {
            player.sendMessage(ColorUtil.component(customPrompt));
            return;
        }
        String key = switch (request.type()) {
            case NAME -> "editor-prompt-name";
            case SLOT -> "editor-prompt-slot";
            case COMMAND -> "editor-prompt-command";
            default -> "editor-prompt-name";
        };
        messages.send(player, key);
    }

    private void scheduleRequestTimeout(Player player, InputRequest request) {
        long timeout = Math.max(10L, plugin.getConfig().getLong("settings.editor.chat-input-timeout-seconds", 60L)) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (requests.remove(player.getUniqueId(), request) && player.isOnline()) {
                player.sendMessage(ColorUtil.component("&cВремя ввода истекло. Откройте редактор повторно."));
            }
        }, timeout);
    }

    private ItemStack icon(ProjectDefinition project) {
        ItemStack item = project.editorIcon();
        if (item == null) item = new ItemStack(project.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.component(project.name()));
        List<Component> lore = new ArrayList<>();
        lore.add(ColorUtil.component("&7ID: &f" + project.id()));
        lore.add(ColorUtil.component("&7Тип: &f" + (project.type() == ProjectType.BUILDING ? "здание" : "чудо")));
        lore.add(ColorUtil.component("&7Уровней: &f" + project.maxLevel()));
        lore.add(Component.empty());
        lore.add(ColorUtil.component("&#63E6BEНажмите для редактирования"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        item.setAmount(1);
        return item;
    }

    private ItemStack action(Material material, String action, String name, List<String> lore) {
        ItemStack item = simple(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack simple(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.component(name));
        meta.lore(lore.stream().map(ColorUtil::component).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = simple(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int column = slot % 9;
            if (slot < 9 || slot >= 45 || column == 0 || column == 8) inventory.setItem(slot, filler);
        }
    }

    private List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) slots.add(row * 9 + column);
        }
        return slots;
    }

    private List<String> descriptionLore(ProjectDefinition project) {
        List<String> lore = new ArrayList<>();
        project.description().forEach(line -> lore.add("&8• &7" + line));
        lore.add("");
        lore.add("&eЛКМ: добавить строку");
        lore.add("&cПКМ: очистить");
        return lore;
    }

    private List<String> commandLore(LevelDefinition level) {
        List<String> lore = new ArrayList<>();
        level.commands().forEach(command -> lore.add("&8• &7/" + command));
        if (level.commands().isEmpty()) lore.add("&7Команд пока нет.");
        lore.add("");
        lore.add("&eЛКМ: добавить команду");
        lore.add("&cПКМ: очистить все");
        return lore;
    }

    private int firstEmpty(Inventory inventory, int limit) {
        for (int slot = 0; slot < limit; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) return slot;
        }
        return -1;
    }

    private String action(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private String project(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(projectKey, PersistentDataType.STRING);
    }

    private String effect(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(effectKey, PersistentDataType.STRING);
    }

    private String readable(String key) {
        String[] parts = key.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private interface EditorHolder extends InventoryHolder {
        @Override
        default Inventory getInventory() { throw new UnsupportedOperationException("marker"); }
    }
    private record EditorIndexHolder() implements EditorHolder { }
    private record ProjectEditorHolder(String projectId, int level) implements EditorHolder { }
    private record ResourceEditorHolder(String projectId, int level) implements EditorHolder { }
    private record EffectEditorHolder(String projectId, int level) implements EditorHolder { }

    private enum InputType { CREATE, NAME, SLOT, MONEY, BONUS, DESCRIPTION, COMMAND }
    private record InputRequest(InputType type, String projectId, int level, ProjectType projectType) { }
}

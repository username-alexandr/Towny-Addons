package ru.neverland.townybuilds.gui;

import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.integration.ItemsAdderHook;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.model.LevelDefinition;
import ru.neverland.townybuilds.model.ProjectDefinition;
import ru.neverland.townybuilds.model.ProjectCategory;
import ru.neverland.townybuilds.model.ProjectType;
import ru.neverland.townybuilds.service.BuildService;
import ru.neverland.townybuilds.service.ContributionResult;
import ru.neverland.townybuilds.service.DefinitionRegistry;
import ru.neverland.townybuilds.service.MessageService;
import ru.neverland.townybuilds.service.RussianItemNames;
import ru.neverland.townybuilds.service.UpgradeResult;
import ru.neverland.townybuilds.construction.ConstructionProgress;
import ru.neverland.townybuilds.util.ColorUtil;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

public final class MenuManager implements Listener {
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU")));
    private static final int PROJECTS_PER_PAGE = 28;
    private final JavaPlugin plugin;
    private final DefinitionRegistry definitions;
    private final DataStore dataStore;
    private final TownyHook towny;
    private final ItemsAdderHook itemsAdder;
    private final BuildService builds;
    private final MessageService messages;
    private final RussianItemNames itemNames;
    private final NamespacedKey projectKey;
    private final NamespacedKey actionKey;

    public MenuManager(JavaPlugin plugin, DefinitionRegistry definitions, DataStore dataStore, TownyHook towny,
                       ItemsAdderHook itemsAdder, BuildService builds, MessageService messages,
                       RussianItemNames itemNames) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.dataStore = dataStore;
        this.towny = towny;
        this.itemsAdder = itemsAdder;
        this.builds = builds;
        this.messages = messages;
        this.itemNames = itemNames;
        projectKey = new NamespacedKey(plugin, "project");
        actionKey = new NamespacedKey(plugin, "action");
    }

    public void openProjects(Player player, ProjectType type) {
        if (type == ProjectType.BUILDING) openCategories(player);
        else openProjects(player, type, null, 0);
    }

    private void openCategories(Player player) {
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "no-town");
            return;
        }
        Inventory inventory = Bukkit.createInventory(new CategoryHolder(), 27,
                ColorUtil.component("&#B65CFFКатегории городских построек"));
        decorate(inventory);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        int index = 0;
        for (ProjectCategory category : ProjectCategory.values()) {
            int count = definitions.category(category).size();
            if (count == 0) continue;
            ItemStack icon = actionItem("category:" + category.name(), new ItemStack(category.icon()),
                    category.color() + "&l" + category.displayName(),
                    List.of("&7Объектов: &f" + count, "", "&#63E6BEНажмите, чтобы открыть"));
            inventory.setItem(slots[Math.min(index++, slots.length - 1)], icon);
        }
        player.openInventory(inventory);
    }

    private void openProjects(Player player, ProjectType type, ProjectCategory category, int requestedPage) {
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "no-town");
            return;
        }
        List<ProjectDefinition> projects = type == ProjectType.BUILDING && category != null
                ? definitions.category(category) : definitions.type(type);
        int pages = Math.max(1, (projects.size() + PROJECTS_PER_PAGE - 1) / PROJECTS_PER_PAGE);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        String title = type == ProjectType.BUILDING && category != null
                ? category.color() + category.displayName() : "&#FFD45AВеликие чудеса света";
        if (pages > 1) title += " &8[&f" + (page + 1) + "&8/&f" + pages + "&8]";
        Inventory inventory = Bukkit.createInventory(new ProjectListHolder(type, category, page), 54,
                ColorUtil.component(title));
        decorate(inventory);
        TownData data = dataStore.town(town.getUUID());
        List<Integer> slots = projectSlots();
        int start = page * PROJECTS_PER_PAGE;
        int end = Math.min(projects.size(), start + PROJECTS_PER_PAGE);
        for (int index = start; index < end; index++) {
            ProjectDefinition project = projects.get(index);
            inventory.setItem(slots.get(index - start), projectIcon(project, data.level(project.id())));
        }
        if (page > 0) inventory.setItem(47, actionItem("projects-previous", backItem(),
                "&#B65CFFПредыдущая страница", List.of("&7Страница " + page + " из " + pages)));
        if (page + 1 < pages) inventory.setItem(51, actionItem("projects-next", new ItemStack(Material.ARROW),
                "&#B65CFFСледующая страница", List.of("&7Страница " + (page + 2) + " из " + pages)));
        if (type == ProjectType.BUILDING) inventory.setItem(45, actionItem("categories-back", backItem(),
                "&fВсе категории", List.of("&7Вернуться к разделам каталога.")));
        ItemStack storage = menuItem(Material.CHEST, "&#63E6BEГородской склад",
                List.of("&7Общее хранилище ресурсов.", "&7Открыть: &f/t inv"));
        inventory.setItem(49, storage);
        player.openInventory(inventory);
    }

    public void openDetails(Player player, ProjectDefinition project) {
        openDetails(player, project, pageFor(project),
                project.type() == ProjectType.BUILDING ? project.category() : null);
    }

    private void openDetails(Player player, ProjectDefinition project, int page, ProjectCategory category) {
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "no-town");
            return;
        }
        TownData data = dataStore.town(town.getUUID());
        int current = data.level(project.id());
        ConstructionProgress construction = builds.constructionProgress(town, project);
        Inventory inventory = Bukkit.createInventory(new DetailsHolder(project.id(), page, category), 27,
                ColorUtil.component(project.name()));
        decorate(inventory);
        inventory.setItem(11, projectIcon(project, current));
        if (construction.active()) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Этап: &f" + construction.stageName());
            lore.add("&7Построено: &f" + construction.placed() + "&8/&f" + construction.total());
            lore.add(progressBar(construction.percent()));
            lore.add("");
            lore.add("&#63E6BEЗелёные частицы показывают");
            lore.add("&#63E6BEнедостающие блоки чертежа.");
            inventory.setItem(15, actionItem("upgrade", upgradeItem(), "&#FFD166&lСТРОИТЕЛЬСТВО ИДЁТ", lore));
        } else if (builds.needsPhysicalPlacement(town, project)) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Чудо уже открыто городом, но его");
            lore.add("&7физическая постройка ещё не размещена.");
            lore.add("");
            lore.add("&#63E6BEНаведитесь на ровную землю и нажмите.");
            lore.add("&aПовторная оплата и артефакты не требуются.");
            inventory.setItem(15, actionItem("upgrade", upgradeItem(),
                    "&#63E6BE&lРАЗМЕСТИТЬ ЧУДО", lore));
        } else if (current >= project.maxLevel()) {
            inventory.setItem(15, actionItem("upgrade", lockedItem(), "&a&lМАКСИМАЛЬНЫЙ УРОВЕНЬ",
                    List.of("&7Эта постройка полностью развита.")));
        } else {
            LevelDefinition next = project.level(current + 1);
            List<String> lore = new ArrayList<>();
            lore.add("&7Следующий уровень: &f" + (current + 1));
            lore.add("&7Из казны: &e" + MONEY.format(next.money()));
            lore.add("&7Бонусных чанков: &a+" + next.bonusBlocks());
            lore.add("");
            lore.add("&#C9A7FFНеобходимые ресурсы:");
            if (next.resources().isEmpty()) {
                lore.add("&8• &7не требуются");
            } else {
                next.resources().forEach(item -> {
                    int contributed = builds.contributed(town, project, current + 1, item);
                    String color = contributed >= item.getAmount() ? "&#63E6BE" : "&f";
                    lore.add("&8• " + color + itemName(item) + " &7" + contributed + "/" + item.getAmount());
                });
            }
            List<String> artifactRequirements = builds.artifactRequirements(town, project);
            if (!artifactRequirements.isEmpty()) {
                lore.add("");
                lore.add("&#E6C363Музейные артефакты:");
                artifactRequirements.forEach(requirement -> lore.add("&8• &f" + requirement));
                lore.add("");
                lore.add("&7Учитываются артефакты, переданные");
                lore.add("&7в фонд городского музея.");
                lore.add("&#E6C363Сдать находки: &f/t archaeology donate all");
            }
            lore.add("");
            boolean procedural = builds.usesConstruction(project, current + 1);
            boolean upgrading = current > 0 && !procedural;
            lore.add(procedural
                    ? "&#63E6BEНаведитесь на землю и начните стройку"
                    : upgrading ? "&#63E6BEНажмите, чтобы улучшить" : "&#63E6BEНажмите, чтобы построить");
            inventory.setItem(15, actionItem("upgrade", upgradeItem(),
                    procedural ? "&#63E6BE&lНАЧАТЬ СТРОИТЕЛЬСТВО"
                            : upgrading ? "&#63E6BE&lУЛУЧШИТЬ" : "&#63E6BE&lПОСТРОИТЬ", lore));
            if (!next.resources().isEmpty()) {
                List<String> contributionLore = new ArrayList<>();
                contributionLore.add("&7Ресурсы сохраняются в фонде проекта");
                contributionLore.add("&7и могут передаваться несколькими частями.");
                contributionLore.add("");
                contributionLore.add("&#63E6BEЛКМ &8— &fсдать из личного инвентаря");
                contributionLore.add("&#FFD166Shift + ЛКМ &8— &fсдать со склада /t inv");
                contributionLore.add("&8Со склада может сдавать только мэр.");
                inventory.setItem(13, actionItem("contribute", new ItemStack(Material.HOPPER),
                        "&#E6C363&lСДАТЬ РЕСУРСЫ", contributionLore));
            }
        }
        inventory.setItem(22, actionItem("back", backItem(), "&fНазад", List.of("&7Вернуться к списку.")));
        player.openInventory(inventory);
    }

    public void openStorage(Player player) {
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "no-town");
            return;
        }
        boolean mayor = towny.isMayor(player, town);
        int configured = plugin.getConfig().getInt("settings.storage.size", 54);
        int size = Math.max(9, Math.min(54, ((configured + 8) / 9) * 9));
        Inventory inventory = Bukkit.createInventory(new StorageHolder(town.getUUID(), mayor), size,
                ColorUtil.component(mayor ? "&#63E6BEСклад города — полный доступ" : "&#63E6BEСклад города — только сдача"));
        inventory.setContents(dataStore.town(town.getUUID()).storage());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder instanceof CategoryHolder) {
            event.setCancelled(true);
            String action = actionFrom(event.getCurrentItem());
            if (action != null && action.startsWith("category:")) {
                try {
                    ProjectCategory category = ProjectCategory.valueOf(action.substring("category:".length()));
                    openProjects(player, ProjectType.BUILDING, category, 0);
                } catch (IllegalArgumentException ignored) {
                    openCategories(player);
                }
            }
            return;
        }
        if (holder instanceof ProjectListHolder list) {
            event.setCancelled(true);
            String action = actionFrom(event.getCurrentItem());
            if ("projects-previous".equals(action)) {
                openProjects(player, list.type(), list.category(), list.page() - 1);
                return;
            }
            if ("projects-next".equals(action)) {
                openProjects(player, list.type(), list.category(), list.page() + 1);
                return;
            }
            if ("categories-back".equals(action)) {
                openCategories(player);
                return;
            }
            ProjectDefinition project = projectFrom(event.getCurrentItem());
            if (project != null && project.type() == list.type()) {
                openDetails(player, project, list.page(), list.category());
            } else if (event.getSlot() == 49) {
                openStorage(player);
            }
            return;
        }
        if (holder instanceof DetailsHolder details) {
            event.setCancelled(true);
            ProjectDefinition project = definitions.project(details.projectId());
            if (project == null) {
                player.closeInventory();
                return;
            }
            String action = actionFrom(event.getCurrentItem());
            if ("back".equals(action)) {
                if (project.type() == ProjectType.BUILDING && details.category() == null) openCategories(player);
                else openProjects(player, project.type(), details.category(), details.page());
            } else if ("upgrade".equals(action)) {
                handleUpgrade(player, project, details.page(), details.category());
            } else if ("contribute".equals(action)) {
                // Инвентарь меняется после завершения отменённого GUI-клика. Иначе Paper/Purpur
                // может восстановить снимок события: прогресс фонда изменится, а предметы вернутся игроку.
                boolean cityStorage = event.isShiftClick();
                player.closeInventory();
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> handleContribution(player, project, cityStorage, details.page()));
            }
            return;
        }
        if (holder instanceof StorageHolder storage && !storage.mayor()) {
            if (event.getClickedInventory() == null) {
                return;
            }
            boolean top = event.getClickedInventory().equals(event.getView().getTopInventory());
            boolean attemptedWithdrawal = top && (event.getAction() == InventoryAction.PICKUP_ALL
                    || event.getAction() == InventoryAction.PICKUP_HALF
                    || event.getAction() == InventoryAction.PICKUP_ONE
                    || event.getAction() == InventoryAction.PICKUP_SOME
                    || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || event.getAction() == InventoryAction.HOTBAR_SWAP
                    || event.getAction() == InventoryAction.SWAP_WITH_CURSOR
                    || event.getAction() == InventoryAction.CLONE_STACK
                    || event.getAction() == InventoryAction.DROP_ALL_SLOT
                    || event.getAction() == InventoryAction.DROP_ONE_SLOT);
            if (attemptedWithdrawal || event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                messages.send(player, "storage-withdraw-denied");
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof StorageHolder storage) || storage.mayor()) {
            return;
        }
        // Drag в верхнюю часть только добавляет предметы. Drag, затрагивающий лишь нижний инвентарь, тоже безопасен.
        // Забрать предмет посредством InventoryDragEvent невозможно, поэтому событие не блокируется.
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof StorageHolder storage) {
            TownData data = dataStore.town(storage.townId());
            data.setStorage(event.getInventory().getContents(), plugin.getConfig().getInt("settings.storage.size", 54));
            dataStore.markDirty();
        }
    }

    private void handleUpgrade(Player player, ProjectDefinition project, int page, ProjectCategory category) {
        UpgradeResult result = builds.upgrade(player, project);
        switch (result.status()) {
            case SUCCESS -> {
                messages.send(player, "upgrade-success", Map.of("project", project.name(), "level", result.newLevel()));
                openDetails(player, project, page, category);
            }
            case CONSTRUCTION_STARTED -> {
                messages.send(player, "construction-started", Map.of("project", project.name(), "level", result.newLevel()));
                openDetails(player, project, page, category);
            }
            case CONSTRUCTION_IN_PROGRESS -> messages.send(player, "construction-already-active");
            case CONSTRUCTION_NO_TARGET -> messages.send(player, "construction-no-target");
            case CONSTRUCTION_OUTSIDE_TOWN -> messages.send(player, "construction-outside-town",
                    Map.of("details", String.join("; ", result.missingResources())));
            case CONSTRUCTION_UNEVEN_GROUND -> messages.send(player, "construction-uneven-ground",
                    Map.of("details", String.join("; ", result.missingResources())));
            case CONSTRUCTION_OBSTRUCTED -> messages.send(player, "construction-obstructed",
                    Map.of("details", String.join("; ", result.missingResources())));
            case CONSTRUCTION_UNAVAILABLE -> messages.send(player, "construction-unavailable");
            case NO_TOWN -> messages.send(player, "no-town");
            case NOT_MAYOR -> messages.send(player, "only-mayor-upgrade");
            case MAX_LEVEL -> messages.send(player, "max-level");
            case NOT_ENOUGH_MONEY, ECONOMY_ERROR -> messages.send(player, "not-enough-money",
                    Map.of("amount", MONEY.format(result.requiredMoney())));
            case NOT_ENOUGH_RESOURCES -> messages.send(player, "not-enough-resources",
                    Map.of("resources", String.join(", ", result.missingResources())));
            case NOT_ENOUGH_ARTIFACTS -> messages.send(player, "not-enough-artifacts",
                    Map.of("artifacts", String.join(", ", result.missingResources())));
        }
    }

    private void handleContribution(Player player, ProjectDefinition project, boolean cityStorage, int page) {
        ContributionResult result = cityStorage
                ? builds.contributeFromStorage(player, project)
                : builds.contributeFromPlayer(player, project);
        switch (result.status()) {
            case SUCCESS -> messages.send(player, cityStorage ? "resources-contributed-storage" : "resources-contributed",
                    Map.of("count", result.totalItems(), "resources", String.join(", ", result.details())));
            case NO_TOWN -> messages.send(player, "no-town");
            case NOT_MAYOR -> messages.send(player, "only-mayor-storage-contribute");
            case MAX_LEVEL -> messages.send(player, "max-level");
            case NOTHING_NEEDED -> messages.send(player, "contribution-not-needed");
            case NOTHING_MATCHED -> messages.send(player, "contribution-nothing-matched");
            case INVENTORY_SYNC_FAILED -> messages.send(player, "contribution-sync-failed");
        }
        openDetails(player, project, page, project.type() == ProjectType.BUILDING ? project.category() : null);
    }

    private ItemStack projectIcon(ProjectDefinition project, int level) {
        ItemStack icon = project.editorIcon();
        if (icon == null) {
            icon = itemsAdder.item(project.itemsAdderIcon(), 1);
        }
        if (icon == null) {
            icon = new ItemStack(project.icon());
        }
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(ColorUtil.component(project.name()));
        List<Component> lore = new ArrayList<>();
        if (project.type() == ProjectType.BUILDING) {
            lore.add(ColorUtil.component(project.category().color() + project.category().displayName()));
            lore.add(Component.empty());
        }
        project.description().forEach(line -> lore.add(ColorUtil.component("&7" + line)));
        lore.add(Component.empty());
        lore.add(ColorUtil.component("&7Уровень: &f" + level + "&8/&f" + project.maxLevel()));
        lore.add(ColorUtil.component(progress(level, project.maxLevel())));
        lore.add(Component.empty());
        lore.add(ColorUtil.component(level >= project.maxLevel() ? "&aПолностью развито" : "&#63E6BEНажмите для подробностей"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(projectKey, PersistentDataType.STRING, project.id());
        icon.setItemMeta(meta);
        icon.setAmount(1);
        return icon;
    }

    private ItemStack actionItem(String action, ItemStack base, String name, List<String> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.component(name));
        meta.lore(lore.stream().map(ColorUtil::component).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.component(name));
        meta.lore(lore.stream().map(ColorUtil::component).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack upgradeItem() {
        ItemStack item = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.upgrade-item"), 1);
        return item == null ? new ItemStack(Material.NETHER_STAR) : item;
    }

    private ItemStack backItem() {
        ItemStack item = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.back-item"), 1);
        return item == null ? new ItemStack(Material.ARROW) : item;
    }

    private ItemStack lockedItem() {
        ItemStack item = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.locked-item"), 1);
        return item == null ? new ItemStack(Material.LIME_STAINED_GLASS_PANE) : item;
    }

    private void decorate(Inventory inventory) {
        ItemStack filler = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.filler-item"), 1);
        if (filler == null) {
            filler = menuItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int column = slot % 9;
            if (slot < 9 || slot >= inventory.getSize() - 9 || column == 0 || column == 8) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ProjectDefinition projectFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(projectKey, PersistentDataType.STRING);
        return id == null ? null : definitions.project(id);
    }

    private String actionFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private String progress(int level, int maximum) {
        StringBuilder value = new StringBuilder("&8[");
        for (int index = 1; index <= maximum; index++) {
            value.append(index <= level ? "&#63E6BE■" : "&7□");
        }
        return value.append("&8]").toString();
    }

    private String progressBar(int percent) {
        int filled = Math.max(0, Math.min(10, (int) Math.ceil(percent / 10.0)));
        return "&8[" + "&#63E6BE■".repeat(filled) + "&7□".repeat(10 - filled) + "&8] &f" + percent + "%";
    }

    private String itemName(ItemStack item) {
        return itemNames.name(item);
    }

    private int pageFor(ProjectDefinition project) {
        int index = (project.type() == ProjectType.BUILDING
                ? definitions.category(project.category()) : definitions.type(project.type())).indexOf(project);
        return index < 0 ? 0 : index / PROJECTS_PER_PAGE;
    }

    private List<Integer> projectSlots() {
        List<Integer> slots = new ArrayList<>(PROJECTS_PER_PAGE);
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) slots.add(row * 9 + column);
        }
        return slots;
    }

    private interface MenuHolder extends InventoryHolder {
        @Override
        default Inventory getInventory() {
            throw new UnsupportedOperationException("Holder-marker does not own inventory");
        }
    }

    private record CategoryHolder() implements MenuHolder { }
    private record ProjectListHolder(ProjectType type, ProjectCategory category, int page) implements MenuHolder { }
    private record DetailsHolder(String projectId, int page, ProjectCategory category) implements MenuHolder { }
    private record StorageHolder(UUID townId, boolean mayor) implements MenuHolder { }
}

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

    public void openProjects(Player player, ProjectType type) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        if (type == ProjectType.BUILDING) openCategories(player);
        else openProjects(player, type, null, 0);
    } }

    private void openCategories(Player player) {
        Town town = towny.town(player);
        if (town == null) {
            messages.send(player, "no-town");
            return;
        }
        Inventory inventory = ru.neverland.core.MenuStyle.inventory(plugin, new CategoryHolder(), 27,
                ColorUtil.component("&#B65CFFКатегории городских построек"));
        decorate(inventory);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 21, 23};
        int index = 0;
        for (ProjectCategory category : ProjectCategory.values()) {
            int count = definitions.category(category).size();
            if (count == 0) continue;
            ItemStack icon = actionItem("category:" + category.name(), new ItemStack(category.icon()),
                    category.color() + "&l" + category.displayName(),
                    List.of("&7Объектов: &f" + count, "", "&#63E6BEНажмите, чтобы открыть"));
            inventory.setItem(slots[Math.min(index++, slots.length - 1)], icon);
        }
        if(Bukkit.getPluginManager().getPlugin("NeverLandTownyMarket")!=null)inventory.setItem(18,actionItem("market",new ItemStack(Material.EMERALD),"&aРынки города",List.of("&7Продажа продукции и закупки товаров.")));
        if(Bukkit.getPluginManager().getPlugin("NeverLandTownyTreasuryPlus")!=null) inventory.setItem(22,actionItem("treasury",new ItemStack(Material.GOLD_INGOT),"&eКазна и бюджет",List.of("&7Статьи расходов и недельные отчёты.")));
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
        Inventory inventory = ru.neverland.core.MenuStyle.inventory(plugin, new ProjectListHolder(type, category, page), 54,
                ColorUtil.component(title));
        decorate(inventory);
        TownData data = dataStore.town(town.getUUID());
        List<Integer> slots = projectSlots();
        int start = page * PROJECTS_PER_PAGE;
        int end = Math.min(projects.size(), start + PROJECTS_PER_PAGE);
        for (int index = start; index < end; index++) {
            ProjectDefinition project = projects.get(index);
            inventory.setItem(slots.get(index - start), projectIcon(project, data.level(project.id()), town.getUUID()));
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
        if(Bukkit.getPluginManager().getPlugin("NeverLandTownyUpkeep")!=null)inventory.setItem(53,actionItem("upkeep",new ItemStack(Material.CLOCK),"&eОбслуживание города",List.of("&7Расходы и состояние зданий.")));
        if(Bukkit.getPluginManager().getPlugin("NeverLandTownyPolicies")!=null)inventory.setItem(48,actionItem("policies",new ItemStack(Material.WRITABLE_BOOK),"&eГородские указы",List.of("&7Политики, преимущества и издержки.")));
        if(Bukkit.getPluginManager().getPlugin("NeverLandTownySpecialization")!=null)inventory.setItem(46,actionItem("specialization",new ItemStack(Material.NETHER_STAR),"&eСпециализация города",List.of("&7Направление, уникальные здания и бонусы.")));
        if(Bukkit.getPluginManager().getPlugin("NeverLandTownyResearch")!=null)inventory.setItem(50,actionItem("research",new ItemStack(Material.ENCHANTED_BOOK),"&eИсследования города",List.of("&7Технологии, знания и научные здания.")));
        if(Bukkit.getPluginManager().getPlugin("NeverLandTownyPower")!=null)inventory.setItem(52,actionItem("power",new ItemStack(Material.REDSTONE),"&eЭнергия города",List.of("&7Выработка, потребление и приоритеты.")));
        player.openInventory(inventory);
    }

    public void openDetails(Player player, ProjectDefinition project) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        openDetails(player, project, pageFor(project),
                project.type() == ProjectType.BUILDING ? project.category() : null);
    } }

    private void openDetails(Player player, ProjectDefinition project, int page, ProjectCategory category) {
        if(towny.town(player)==null){messages.send(player,"no-town");return;}
        DetailsHolder holder = player.getOpenInventory().getTopInventory().getHolder(false) instanceof DetailsHolder existing
                && existing.owner.equals(player.getUniqueId()) && existing.projectId().equals(project.id()) ? existing
                : new DetailsHolder(player.getUniqueId(),project.id(),page,category);
        boolean opening=holder.inventory==null;
        if(opening)holder.inventory=ru.neverland.core.MenuStyle.inventory(plugin,holder,27,ColorUtil.component(project.name()));
        renderDetails(player,project,holder);
        if(opening)player.openInventory(holder.inventory);
    }

    private void renderDetails(Player player,ProjectDefinition project,DetailsHolder holder) {
        Town town=towny.town(player);
        if(town==null){messages.send(player,"no-town");player.closeInventory();return;}
        TownData data = dataStore.town(town.getUUID());
        int current = data.level(project.id());
        ConstructionProgress construction = builds.constructionProgress(town, project);
        Inventory inventory=holder.inventory;inventory.clear();
        decorate(inventory);
        ItemStack summary=projectIcon(project,current,town.getUUID());
        var summaryMeta=summary.getItemMeta();var summaryLore=new ArrayList<>(summaryMeta.lore()==null?List.<net.kyori.adventure.text.Component>of():summaryMeta.lore());
        summaryLore.addAll(ru.neverland.core.MenuStyle.loreComponents(builds.environmentForecast(town,project).stream().map(ru.neverland.core.MenuStyle::decode).toList()));
        summaryLore.add(ru.neverland.core.MenuStyle.decode("&7Стиль новых зданий: &f"+ru.neverland.townybuilds.construction.BuildingStyle.parse(data.buildingStyle()).title()));summaryMeta.lore(summaryLore);summary.setItemMeta(summaryMeta);
        role(summary,"building-preview");
        inventory.setItem(11,summary);
        if(current<project.maxLevel()&&!construction.active()&&town.isMayor(com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player))&&org.bukkit.Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyContracts"))
            inventory.setItem(17,actionItem("build-orders",new ItemStack(Material.WRITABLE_BOOK),"&eЗаказать материалы горожанам",List.of("&7Заказ на то, чего не хватает для здания","&7Награду оплачивает городская казна","&eНажмите, чтобы проверить условия")));
        if (project.id().equals("army")) inventory.setItem(16, actionItem("army", new ItemStack(Material.NETHERITE_HELMET),
                "&#B65CFFСостав армии", List.of("&7Реестр и мобилизация граждан с 18 лет")));
        if (construction.active()) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Этап: &f" + construction.stageName());
            lore.add("&7Построено: &f" + construction.placed() + "&8/&f" + construction.total());
            lore.add(progressBar(construction.percent()));
            lore.add("");
            lore.add("&#63E6BEПодсказки показывают текущий слой.");
            lore.add("&#63E6BEЗавершите его, чтобы увидеть следующий.");
            inventory.setItem(15, actionItem("upgrade", upgradeItem(), "&#FFD166&lСТРОИТЕЛЬСТВО ИДЁТ", lore));
            inventory.setItem(13, actionItem("construction-cancel", new ItemStack(Material.RED_CONCRETE),
                    "&cОтменить строительство", List.of("&7Посмотреть возврат денег и ресурсов.","&7Затем подтвердить или продолжить стройку.")));
        } else if (builds.needsPhysicalPlacement(town, project)) {
            List<String> lore = new ArrayList<>();
            lore.add("&7Чудо уже открыто городом, но его");
            lore.add("&7физическая постройка ещё не размещена.");
            lore.add("");
            lore.add("&#63E6BEПолучите чертёж, затем нажмите им по земле.");
            lore.add("&aПовторная оплата и артефакты не требуются.");
            inventory.setItem(15, actionItem("upgrade", upgradeItem(),
                    "&#63E6BE&lПОЛУЧИТЬ ЧЕРТЁЖ ЧУДА", lore));
        } else if (current >= project.maxLevel()) {
            inventory.setItem(15, actionItem("upgrade", lockedItem(), "&a&lМАКСИМАЛЬНЫЙ УРОВЕНЬ",
                    List.of("&7Эта постройка полностью развита.")));
        } else {
            LevelDefinition next = project.level(current + 1);
            List<String> lore = new ArrayList<>();
            lore.add("&7Следующий уровень: &f" + (current + 1));
            lore.add("&7Из казны: &e" + MONEY.format(next.money()));
            lore.add("&7Бонусных чанков: &a+" + next.bonusBlocks());
            List<String> prerequisites = builds.prerequisiteRequirements(town, project);
            if (!prerequisites.isEmpty()) {
                lore.add("");
                lore.add("&#74D7FFГородская инфраструктура:");
                prerequisites.forEach(requirement -> lore.add("&8• " + requirement));
            }
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
                    ? "&#63E6BEПолучите чертёж и выберите место здания"
                    : upgrading ? "&#63E6BEНажмите, чтобы улучшить" : "&#63E6BEНажмите, чтобы построить");
            inventory.setItem(15, actionItem("upgrade", upgradeItem(),
                    procedural ? "&#63E6BE&lПОЛУЧИТЬ ЧЕРТЁЖ"
                            : upgrading ? "&#63E6BE&lУЛУЧШИТЬ" : "&#63E6BE&lПОСТРОИТЬ", lore));

        }
        var unavailable=builds.unavailableReasons(player,project);
        if(!construction.active()&&!unavailable.isEmpty()){
            var item=inventory.getItem(15);if(item!=null)markUnavailable(item,unavailable);
        }
        if(!construction.active()&&current<project.maxLevel())renderContribution(player,project,holder);
        if(current>0&&!construction.active()&&Bukkit.getPluginManager().isPluginEnabled("NeverLandTownySiegesPlus"))inventory.setItem(14,actionItem("raid-repair",new ItemStack(Material.ANVIL),"&eПрочность и ремонт",List.of("&7Посмотреть общую прочность здания.","&7Ремонт оплачивается из городской казны.","&7Цена зависит от доли повреждений.")));
        inventory.setItem(24,actionItem("refresh",new ItemStack(Material.CLOCK),"Обновить условия",List.of("&7Проверить материалы и разрешения.")));
        var back=actionItem("back",backItem(),"&fНазад",List.of("&7Вернуться к списку зданий."));role(back,"back");inventory.setItem(22,back);
    }

    public void openStorage(Player player) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        ((ru.neverland.townybuilds.NeverLandTownyBuilds)plugin).storage().openStorage(player,"warehouse");
    } }

    @EventHandler
    public void onClick(InventoryClickEvent event) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyBuilds")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if(holder instanceof MenuHolder&&!(holder instanceof StorageHolder)){
            event.setCancelled(true);
            if(event.getRawSlot()<0||event.getRawSlot()>=event.getView().getTopInventory().getSize())return;
            if(!player.hasPermission("neverlandtownybuilds.use")){player.closeInventory();return;}
        }
        if (holder instanceof CategoryHolder) {
            event.setCancelled(true);
            String action = actionFrom(event.getCurrentItem());
            if ("market".equals(action)) {player.performCommand("townymarket");return;}
            if ("treasury".equals(action)) { player.performCommand("townytreasury"); return; }
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
            } else if ("policies".equals(actionFrom(event.getCurrentItem()))) {
                player.performCommand("townypolicies");
            } else if ("specialization".equals(actionFrom(event.getCurrentItem()))) {
                player.performCommand("townyspecialization");
            } else if ("research".equals(actionFrom(event.getCurrentItem()))) {
                player.performCommand("townyresearch");
            } else if ("power".equals(actionFrom(event.getCurrentItem()))) {
                player.performCommand("townypower");
            } else if ("upkeep".equals(actionFrom(event.getCurrentItem()))) {
                player.performCommand("townyupkeep");
            } else if (event.getSlot() == 49) {
                openStorage(player);
            }
            return;
        }
        if (holder instanceof DetailsHolder details) {
            event.setCancelled(true);
            if(!details.owner.equals(player.getUniqueId())||details.busy)return;
            ProjectDefinition project = definitions.project(details.projectId());
            if (project == null) {
                player.closeInventory();
                return;
            }
            String action = actionFrom(event.getCurrentItem());
            if ("back".equals(action)) {
                if (project.type() == ProjectType.BUILDING && details.category() == null) openCategories(player);
                else openProjects(player, project.type(), details.category(), details.page());
            } else if ("refresh".equals(action)) {
                openDetails(player,project,details.page(),details.category());
            } else if ("army".equals(action) && project.id().equals("army")) {
                ((ru.neverland.townybuilds.NeverLandTownyBuilds) plugin).army().open(player, 0);
            } else if ("build-orders".equals(action)) {
                player.performCommand("town buildorders "+project.id());
            } else if ("upgrade".equals(action)) {
                var reasons=builds.unavailableReasons(player,project);
                if(!reasons.isEmpty()){player.sendMessage(ColorUtil.component("&e"+String.join("\n",reasons)));openDetails(player,project,details.page(),details.category());return;}
                handleUpgrade(player, project, details.page(), details.category());
            } else if ("raid-repair".equals(action)) {
                try{ru.neverland.core.ApiServices.call("NeverLandTownySiegesPlus","ru.neverland.townysieges.api.TownyRaidRepairApi","open",new Class<?>[]{Player.class,String.class},player,project.id());}catch(Exception ex){player.sendMessage(ColorUtil.component("&eРемонт сейчас недоступен: "+ex.getMessage()));}
            } else if ("construction-cancel".equals(action)) {
                ((ru.neverland.townybuilds.NeverLandTownyBuilds)plugin).constructionCancelMenu().open(player,project.id());
            } else if ("contribute".equals(action)) {
                confirmMaterials(player,project,details,event.isShiftClick());
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
    } }

    @EventHandler
    public void onDrag(InventoryDragEvent event) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyBuilds")){ru.neverland.core.ModuleGate.stopEvent(event);return;}
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if(holder instanceof MenuHolder&&!(holder instanceof StorageHolder)){event.setCancelled(true);return;}
        if (!(holder instanceof StorageHolder storage) || storage.mayor()) {
            return;
        }
        // Drag в верхнюю часть только добавляет предметы. Drag, затрагивающий лишь нижний инвентарь, тоже безопасен.
        // Забрать предмет посредством InventoryDragEvent невозможно, поэтому событие не блокируется.
    } }

    @EventHandler
    public void onClose(InventoryCloseEvent event) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof StorageHolder storage) {
            TownData data = dataStore.town(storage.townId());
            data.setStorage(event.getInventory().getContents(), plugin.getConfig().getInt("settings.storage.size", 54));
            dataStore.markDirty();
        }
    } }

    private void handleUpgrade(Player player, ProjectDefinition project, int page, ProjectCategory category) {
        UpgradeResult result = builds.upgrade(player, project);
        switch (result.status()) {
            case PAYMENT_PENDING -> {player.sendMessage("§eОплата и выбранное место сохраняются. Дождитесь подтверждения; повторно нажимать не нужно.");player.closeInventory();}
            case PAYMENT_UNAVAILABLE -> player.sendMessage("§eОплата временно недоступна. Требуется работающая городская казна; обратитесь к администратору.");
            case SUCCESS -> {
                messages.send(player, "upgrade-success", Map.of("project", project.name(), "level", result.newLevel()));
                openDetails(player, project, page, category);
            }
            case PLACEMENT_READY -> { player.closeInventory(); }
            case CONSTRUCTION_STARTED -> {
                messages.send(player, "construction-layer-started", Map.of("project", project.name(), "level", result.newLevel()));
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
            case BUDGET_UNAVAILABLE -> player.sendMessage(ColorUtil.component("&cВ бюджете строительства недостаточно средств или казна недоступна. Откройте /t treasury."));
            case NOT_ENOUGH_MONEY, ECONOMY_ERROR -> messages.send(player, "not-enough-money",
                    Map.of("amount", MONEY.format(result.requiredMoney())));
            case NOT_ENOUGH_RESOURCES -> messages.send(player, "not-enough-resources",
                    Map.of("resources", String.join(", ", result.missingResources())));
            case NOT_ENOUGH_ARTIFACTS -> messages.send(player, "not-enough-artifacts",
                    Map.of("artifacts", String.join(", ", result.missingResources())));
            case PREREQUISITES_NOT_MET -> messages.send(player, "prerequisites-not-met",
                    Map.of("projects", String.join(", ", result.missingResources())));
        }
    }

    private void showContributionResult(Player player,ProjectDefinition project,boolean cityStorage,ContributionResult result){
        switch (result.status()) {
            case PREVIEW_CHANGED -> player.sendMessage("§eУсловия изменились. Откройте список материалов заново.");
            case SUCCESS -> messages.send(player, cityStorage ? "resources-contributed-storage" : "resources-contributed",
                    Map.of("count", result.totalItems(), "resources", String.join(", ", result.details())));
            case NO_TOWN -> messages.send(player, "no-town");
            case NOT_MAYOR -> messages.send(player, "only-mayor-storage-contribute");
            case MAX_LEVEL -> messages.send(player, "max-level");
            case NOTHING_NEEDED -> messages.send(player, "contribution-not-needed");
            case NOTHING_MATCHED -> messages.send(player, "contribution-nothing-matched");
            case STORAGE_BUSY -> player.sendMessage("Склад города открыт другим игроком. Закройте склад перед взносом.");
            case CONSTRUCTION_ACTIVE -> player.sendMessage("§eСначала завершите текущую стройку или дождитесь возврата за её отмену. Ресурсы остались у вас.");
            case SPECIALIZATION_LOCKED -> messages.send(player,"specialization-required",Map.of("specialization",ru.neverland.integration.SpecializationAccess.requirement(project.id())));
            case INVENTORY_SYNC_FAILED -> messages.send(player, "contribution-sync-failed");
        }
    }

    private void markUnavailable(ItemStack item,List<String> reasons){
        var meta=item.getItemMeta();var lore=meta.lore()==null?new ArrayList<Component>():new ArrayList<>(meta.lore());
        lore.add(Component.empty());lore.add(ColorUtil.component("&eПока недоступно:"));
        for(String reason:reasons)lore.add(ColorUtil.component("&7"+reason));
        meta.lore(ru.neverland.core.MenuStyle.loreComponents(lore));
        meta.getPersistentDataContainer().set(new NamespacedKey("neverland","disabled_reason"),PersistentDataType.STRING,String.join("\n",reasons));item.setItemMeta(meta);
    }
    private void role(ItemStack item,String role){var meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("neverland","menu_role"),PersistentDataType.STRING,role);item.setItemMeta(meta);}
    private void renderContribution(Player player,ProjectDefinition project,DetailsHolder holder){
        var town=towny.town(player);if(town==null)return;
        int target=dataStore.town(town.getUUID()).level(project.id())+1;var level=project.level(target);if(level==null)return;
        holder.quote=builds.previewContribution(player,project,false);
        holder.storageQuote=builds.previewContribution(player,project,true);holder.created=System.nanoTime();
        List<String> lore=new ArrayList<>();
        lore.add("&7Для уровня: &f"+target);
        lore.add("&7Стоимость из казны: &e"+MONEY.format(level.money())+" монет");
        lore.add("&7Деньги списываются при начале строительства.");
        lore.add("");lore.add("&#C9A7FFНеобходимые ресурсы:");
        if(level.resources().isEmpty())lore.add("&7Ресурсы не требуются.");
        else for(var item:level.resources()){
            int have=builds.contributed(town,project,target,item);
            lore.add("&8• &f"+itemName(item)+" &7— "+have+"/"+item.getAmount()+" сдано");
        }
        var prerequisites=builds.prerequisiteRequirements(town,project);
        if(!prerequisites.isEmpty()){
            lore.add("");lore.add("&#74D7FFНеобходимые здания и условия:");
            prerequisites.forEach(line->lore.add("&8• "+line));
        }
        var artifacts=builds.artifactRequirements(town,project);
        if(!artifacts.isEmpty()){
            lore.add("");lore.add("&#E6C363Необходимые артефакты:");
            artifacts.forEach(line->lore.add("&8• &f"+line));
            lore.add("&7Учитываются находки в фонде городского музея.");
        }
        lore.add("");lore.add("&7Можно сдавать по частям: берётся только недостающее.");
        lore.add("&#63E6BEЛКМ — сдать из своего инвентаря.");
        lore.add("&#63E6BEShift + ЛКМ — сдать со склада города.");
        // One button supports either source. A missing personal stack must not disable warehouse clicks.
        holder.inventory.setItem(13,actionItem("contribute",new ItemStack(Material.HOPPER),
                "&#E6C363&lСдать ресурсы",lore));
    }
    private void confirmMaterials(Player player,ProjectDefinition project,DetailsHolder holder,boolean warehouse){
        var quote=warehouse?holder.storageQuote:holder.quote;if(quote==null)return;
        if(System.nanoTime()-holder.created>120_000_000_000L){
            player.sendMessage("§eСписок обновлён. Проверьте материалы и подтвердите снова.");renderDetails(player,project,holder);return;
        }
        if(!quote.ready()){player.sendMessage("§e"+quote.reason());renderDetails(player,project,holder);return;}
        holder.busy=true;
        ru.neverland.core.SuiteScheduler.entity(plugin,player,()->{
            try{
                if(!player.isOnline()||player.getOpenInventory().getTopInventory()!=holder.inventory
                        ||!player.hasPermission("neverlandtownybuilds.use")||ru.neverland.core.SuiteState.frozen(plugin.getName()))return;
                var result=builds.confirmContribution(player,project,quote);
                if(result.status()==ContributionResult.Status.PREVIEW_CHANGED)
                    player.sendMessage("§eМатериалы или условия изменились. Ничего не передано: проверьте обновлённый список.");
                else showContributionResult(player,project,quote.warehouse(),result);
                renderDetails(player,project,holder);
            }finally{holder.busy=false;}
        });
    }

    private ItemStack projectIcon(ProjectDefinition project, int level, UUID town) {
        ItemStack icon = project.editorIcon();
        if (icon == null) {
            icon = itemsAdder.item(project.itemsAdderIcon(), 1);
        }
        if (icon == null) {
            icon = new ItemStack(project.icon());
        }
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(ru.neverland.core.MenuStyle.nameComponent(ColorUtil.component(project.name())));
        List<Component> lore = new ArrayList<>();
        if (project.type() == ProjectType.BUILDING) {
            lore.add(ColorUtil.component(project.category().color() + project.category().displayName()));
            lore.add(Component.empty());
        }
        project.description().forEach(line -> lore.add(ColorUtil.component("&7" + line)));
        lore.add(Component.empty());
        lore.add(ColorUtil.component("&7Уровень: &f" + level + "&8/&f" + project.maxLevel()));
        lore.add(ColorUtil.component(progress(level, project.maxLevel())));
        if(ru.neverland.integration.JobsAccess.supports(project.id())){
            lore.add(ColorUtil.component("&7Специалистов на смене: &f"+ru.neverland.integration.JobsAccess.workers(town,project.id())));
            lore.add(ColorUtil.component("&7Бонус профессий: &a+"+String.format(java.util.Locale.ROOT,"%.1f",100*ru.neverland.integration.JobsAccess.bonus(town,project.id()))+"%"));
            lore.add(ColorUtil.component("&7Профессия и рабочее место: /t jobs"));
        }
        if(!ru.neverland.integration.SpecializationRules.required(project.id()).isEmpty())lore.add(ColorUtil.component((ru.neverland.integration.SpecializationAccess.allowed(town,project.id())?"&a":"&c")+"Специализация: "+ru.neverland.integration.SpecializationAccess.requirement(project.id())));
        if(level>0&&!ru.neverland.integration.BuildingOperations.active(town,project.id())){
            lore.add(ColorUtil.component("&cНЕАКТИВНО — "+ru.neverland.integration.BuildingOperations.inactiveReason(town,project.id())));
            lore.add(ColorUtil.component("&7Обслуживание: /t upkeep; энергия: /t power"));
        }
        lore.add(Component.empty());
        lore.add(ColorUtil.component(level >= project.maxLevel() ? "&aПолностью развито" : "&#63E6BEНажмите для подробностей"));
        meta.lore(ru.neverland.core.MenuStyle.loreComponents(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(projectKey, PersistentDataType.STRING, project.id());
        icon.setItemMeta(meta);
        icon.setAmount(1);
        return icon;
    }

    private ItemStack actionItem(String action, ItemStack base, String name, List<String> lore) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ru.neverland.core.MenuStyle.nameComponent(ColorUtil.component(name)));
        meta.lore(ru.neverland.core.MenuStyle.loreComponents(lore.stream().map(ColorUtil::component).toList()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ru.neverland.core.MenuStyle.nameComponent(ColorUtil.component(name)));
        meta.lore(ru.neverland.core.MenuStyle.loreComponents(lore.stream().map(ColorUtil::component).toList()));
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

    public ru.neverland.townybuilds.construction.BlueprintPlan preview(Player player,Inventory inventory){try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()){
        if(player.getOpenInventory().getTopInventory()!=inventory||!(inventory.getHolder() instanceof DetailsHolder holder)||!player.hasPermission("neverlandtownybuilds.use"))return null;
        var town=towny.town(player);var project=definitions.project(holder.projectId());if(town==null||project==null)return null;
        var state=dataStore.town(town.getUUID());int level=Math.min(project.maxLevel(),state.level(project.id())+1);
        var draft=state.placements().get(project.id());return builds.previewPlan(project.id(),level,state.constructionSite(project.id()),state.buildingStyle(),draft!=null&&draft.level()==level?draft.blueprintSet():null);
    }}
    private record CategoryHolder() implements MenuHolder { }
    private record ProjectListHolder(ProjectType type, ProjectCategory category, int page) implements MenuHolder { }
    private static final class DetailsHolder implements MenuHolder {
        final UUID owner;final String projectId;final int page;final ProjectCategory category;
        Inventory inventory;boolean busy;long created;
        ru.neverland.townybuilds.service.ContributionPreview quote,storageQuote;
        DetailsHolder(UUID owner,String projectId,int page,ProjectCategory category){this.owner=owner;this.projectId=projectId;this.page=page;this.category=category;}
        String projectId(){return projectId;}int page(){return page;}ProjectCategory category(){return category;}
    }
    private record StorageHolder(UUID townId, boolean mayor) implements MenuHolder { }
}

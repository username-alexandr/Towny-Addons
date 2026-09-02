package ru.neverland.mintcamps.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.data.CostStore;
import ru.neverland.mintcamps.integration.ItemsAdderHook;
import ru.neverland.mintcamps.model.BlockPos;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.service.CampService;
import ru.neverland.mintcamps.service.FuelService;
import ru.neverland.mintcamps.service.MessageService;
import ru.neverland.mintcamps.service.ResourceService;
import ru.neverland.mintcamps.service.StructureGenerator;
import ru.neverland.mintcamps.service.RussianItemNames;
import ru.neverland.mintcamps.service.TeleportService;
import ru.neverland.mintcamps.util.ColorUtil;
import ru.neverland.mintcamps.util.DirectionUtil;
import ru.neverland.mintcamps.util.TimeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MenuManager implements Listener {
    private final JavaPlugin plugin;
    private final CampRepository repository;
    private final CostStore costs;
    private final CampService camps;
    private final FuelService fuel;
    private final TeleportService teleports;
    private final MessageService messages;
    private final RussianItemNames itemNames;
    private final ResourceService resources;
    private final ItemsAdderHook itemsAdder;
    private final Map<UUID, Long> emptySaveConfirmation = new HashMap<>();

    public MenuManager(JavaPlugin plugin, CampRepository repository, CostStore costs, CampService camps,
                       FuelService fuel, TeleportService teleports, MessageService messages,
                       RussianItemNames itemNames, ResourceService resources, ItemsAdderHook itemsAdder) {
        this.plugin = plugin;
        this.repository = repository;
        this.costs = costs;
        this.camps = camps;
        this.fuel = fuel;
        this.teleports = teleports;
        this.messages = messages;
        this.itemNames = itemNames;
        this.resources = resources;
        this.itemsAdder = itemsAdder;
    }

    public void openMain(Player player) {
        Camp camp = repository.get(player.getUniqueId()).orElse(null);
        MenuHolder holder = new MenuHolder(MenuType.MAIN, player.getUniqueId(), 0);
        Inventory inventory = create(holder, 27, plugin.getConfig().getString("settings.gui.main-title", "&8Походный лагерь"));
        fill(inventory);
        if (camp == null) {
            List<Component> lore = lore("gui.no-camp-lore", Map.of("level", camps.levelName(1),
                    "time", TimeUtil.formatMillis(plugin.getConfig().getLong("settings.lifetime.initial-burn-seconds", 1800) * 1000L)));
            lore.addAll(costLore(player, 1));
            lore.add(ColorUtil.component(""));
            lore.add(ColorUtil.component(messages.raw("gui.click-create")));
            inventory.setItem(13, icon("create-item", Material.CAMPFIRE, messages.raw("gui.no-camp-title"), lore));
        } else {
            long now = System.currentTimeMillis();
            inventory.setItem(10, icon(null, Material.WRITABLE_BOOK,
                    messages.replace(messages.raw("gui.info-title"), Map.of("level", camps.levelName(camp.level()))), lore("gui.info-lore", Map.of(
                    "level", camps.levelName(camp.level()), "owner", camp.ownerName(), "style", camps.styleName(camp),
                    "time", TimeUtil.formatMillis(camp.remainingBurnMillis(now)),
                    "life", TimeUtil.formatMillis(camp.remainingLifeMillis(now, fuel.maximumLifeMillis())),
                    "trusted", camp.trusted().size(), "max_trusted", camps.maxTrusted(camp.level())))));
            if (camp.level() < 3) {
                int next = camp.level() + 1;
                List<Component> lore = lore("gui.upgrade-lore", Map.of("level", camps.levelName(next),
                        "slots", camps.stashSize(next), "trusted", camps.maxTrusted(next)));
                lore.addAll(costLore(player, next));
                inventory.setItem(11, icon("upgrade-item", Material.SMITHING_TABLE, messages.raw("gui.upgrade-title"), lore));
            } else inventory.setItem(11, icon("upgrade-item", Material.NETHER_STAR, messages.raw("gui.upgrade-max"), List.of()));
            inventory.setItem(12, icon("fuel-item", Material.COAL, messages.raw("gui.fuel-title"), lore("gui.fuel-lore", Map.of(
                    "time", TimeUtil.formatMillis(camp.remainingBurnMillis(now)), "cap", TimeUtil.formatMillis(fuel.burnCapMillis(camp.level()))))));
            inventory.setItem(13, icon("storage-item", Material.BARREL, messages.raw("gui.storage-title"), lore("gui.storage-lore", Map.of("slots", camp.stash().length))));
            inventory.setItem(14, icon("teleport-item", Material.ENDER_PEARL, messages.raw("gui.teleport-title"), List.of()));
            inventory.setItem(15, icon("trust-item", Material.PLAYER_HEAD, messages.raw("gui.trust-title"), lore("gui.trust-lore", Map.of(
                    "trusted", camp.trusted().size(), "max_trusted", camps.maxTrusted(camp.level())))));
            String barrier = messages.raw(camp.mobsDisabled() ? "gui.barrier-on" : "gui.barrier-off");
            inventory.setItem(16, icon("protection-item", Material.SHIELD, messages.raw("gui.barrier-title"), List.of(ColorUtil.component(barrier))));
            inventory.setItem(22, icon("pack-item", Material.BUNDLE, messages.raw("gui.pack-title"), lore("gui.pack-lore", Map.of())));
        }
        player.openInventory(inventory);
    }

    public void openStorage(Player player, Camp camp) {
        if (!camp.canAccess(player.getUniqueId()) && !player.hasPermission("mintcamps.admin")) {
            messages.send(player, "protection.interact");
            return;
        }
        MenuHolder holder = new MenuHolder(MenuType.STORAGE, camp.ownerId(), camp.level());
        Inventory inventory = create(holder, camp.stash().length, messages.raw("gui.storage-title"));
        inventory.setContents(Arrays.stream(camp.stash()).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new));
        player.openInventory(inventory);
    }

    public void openEditor(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.EDITOR_LEVELS, player.getUniqueId(), 0);
        Inventory inventory = create(holder, 27, plugin.getConfig().getString("settings.gui.editor-title", "&8Редактор стоимости лагерей"));
        fill(inventory);
        int[] slots = {11, 13, 15};
        Material[] materials = {Material.CAMPFIRE, Material.SMOKER, Material.BLAST_FURNACE};
        for (int level = 1; level <= 3; level++) {
            List<Component> lore = lore("gui.editor-level-lore", Map.of());
            lore.addAll(costLore(null, level));
            inventory.setItem(slots[level - 1], icon(null, materials[level - 1], camps.levelName(level), lore));
        }
        player.openInventory(inventory);
    }

    public void openCostEditor(Player player, int level) {
        MenuHolder holder = new MenuHolder(MenuType.EDITOR_COST, player.getUniqueId(), level);
        String title = messages.replace(plugin.getConfig().getString("settings.gui.cost-editor-title", "&8Стоимость: {level}"),
                Map.of("level", ColorUtil.plain(camps.levelName(level))));
        Inventory inventory = create(holder, 54, title);
        List<ItemStack> current = costs.get(level);
        for (int slot = 0; slot < Math.min(45, current.size()); slot++) inventory.setItem(slot, current.get(slot));
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, filler());
        inventory.setItem(47, icon(null, Material.YELLOW_DYE, messages.raw("gui.editor-clear"), List.of()));
        inventory.setItem(49, icon("cancel-item", Material.BARRIER, messages.raw("gui.editor-cancel"), List.of()));
        inventory.setItem(51, icon("save-item", Material.LIME_DYE, messages.raw("gui.editor-save"), lore("gui.editor-save-lore", Map.of())));
        player.openInventory(inventory);
        messages.send(player, "editor.opened");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        Camp camp = repository.findAt(block.getLocation()).orElse(null);
        if (camp == null) return;
        BlockPos position = BlockPos.of(block.getLocation());
        if (position.equals(camp.stashBlock())) {
            event.setCancelled(true);
            openStorage(event.getPlayer(), camp);
            return;
        }
        BlockPos fire = DirectionUtil.rotate(camp.anchor(), camp.facing(), StructureGenerator.CAMPFIRE_RELATIVE);
        if (position.equals(fire)) {
            event.setCancelled(true);
            if (!camp.canAccess(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("mintcamps.admin")) {
                messages.send(event.getPlayer(), "protection.interact");
                return;
            }
            addFuel(event.getPlayer(), camp);
            return;
        }
        if (!camp.canAccess(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("mintcamps.admin")) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), "protection.interact");
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || !(event.getWhoClicked() instanceof Player player)) return;
        if (holder.type() == MenuType.STORAGE) return;
        if (holder.type() == MenuType.EDITOR_COST) {
            handleCostClick(event, player, holder);
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return;
        switch (holder.type()) {
            case MAIN -> handleMain(player, event.getRawSlot());
            case CONFIRM_PACK -> {
                if (event.getRawSlot() == 11) { player.closeInventory(); camps.pack(player); }
                else if (event.getRawSlot() == 15) openMain(player);
            }
            case EDITOR_LEVELS -> {
                if (event.getRawSlot() == 11) openCostEditor(player, 1);
                if (event.getRawSlot() == 13) openCostEditor(player, 2);
                if (event.getRawSlot() == 15) openCostEditor(player, 3);
            }
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (holder.type() != MenuType.EDITOR_COST) {
            if (holder.type() != MenuType.STORAGE) event.setCancelled(true);
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot < 54 && slot >= 45)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || holder.type() != MenuType.STORAGE) return;
        repository.get(holder.owner()).ifPresent(camp -> {
            camp.stash(event.getInventory().getContents(), camp.stash().length);
            repository.markDirty();
            repository.saveIfDirty();
        });
    }

    private void handleMain(Player player, int slot) {
        Camp camp = repository.get(player.getUniqueId()).orElse(null);
        if (camp == null) {
            if (slot == 13) { player.closeInventory(); camps.create(player); }
            return;
        }
        switch (slot) {
            case 11 -> { player.closeInventory(); camps.upgrade(player); }
            case 12 -> addFuel(player, camp);
            case 13 -> openStorage(player, camp);
            case 14 -> { player.closeInventory(); teleports.teleport(player, null); }
            case 16 -> { camps.toggleBarrier(player); openMain(player); }
            case 22 -> openPackConfirmation(player);
            default -> { }
        }
    }

    private void openPackConfirmation(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.CONFIRM_PACK, player.getUniqueId(), 0);
        Inventory inventory = create(holder, 27, messages.raw("gui.confirm-pack-title"));
        fill(inventory);
        inventory.setItem(11, icon(null, Material.LIME_WOOL, messages.raw("gui.confirm"), List.of()));
        inventory.setItem(15, icon(null, Material.RED_WOOL, messages.raw("gui.cancel"), List.of()));
        player.openInventory(inventory);
    }

    private void handleCostClick(InventoryClickEvent event, Player player, MenuHolder holder) {
        int raw = event.getRawSlot();
        if (raw >= 45 && raw < 54) {
            event.setCancelled(true);
            if (raw == 47) for (int slot = 0; slot < 45; slot++) event.getInventory().setItem(slot, null);
            if (raw == 49) openEditor(player);
            if (raw == 51) saveCost(player, holder.level(), event.getInventory());
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK) event.setCancelled(true);
        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) return;
            ItemStack moving = current.clone();
            for (int slot = 0; slot < 45 && moving.getAmount() > 0; slot++) {
                ItemStack target = event.getInventory().getItem(slot);
                if (target == null) {
                    event.getInventory().setItem(slot, moving.clone());
                    current.setAmount(0);
                    break;
                }
                if (target.isSimilar(moving) && target.getAmount() < target.getMaxStackSize()) {
                    int amount = Math.min(moving.getAmount(), target.getMaxStackSize() - target.getAmount());
                    target.setAmount(target.getAmount() + amount);
                    moving.setAmount(moving.getAmount() - amount);
                    current.setAmount(current.getAmount() - amount);
                }
            }
        }
    }

    private void saveCost(Player player, int level, Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) items.add(item.clone());
        }
        if (items.isEmpty() && emptySaveConfirmation.getOrDefault(player.getUniqueId(), 0L) < System.currentTimeMillis()) {
            emptySaveConfirmation.put(player.getUniqueId(), System.currentTimeMillis() + 10000L);
            messages.send(player, "editor.empty-warning");
            return;
        }
        costs.set(level, items);
        emptySaveConfirmation.remove(player.getUniqueId());
        messages.send(player, "editor.saved", Map.of("level", level, "items", items.size()));
        openEditor(player);
    }

    private void addFuel(Player player, Camp camp) {
        FuelService.Result result = fuel.addFromMainHand(player, camp);
        switch (result.status()) {
            case EMPTY_HAND -> messages.send(player, "fuel.hold-item");
            case INVALID -> messages.send(player, "fuel.invalid", Map.of("item", itemNames.name(player.getInventory().getItemInMainHand())));
            case FULL -> messages.send(player, "fuel.full");
            case LIFETIME_LIMIT -> messages.send(player, "fuel.lifetime-limit");
            case ADDED -> {
                repository.markDirty();
                repository.saveIfDirty();
                camps.updateCampfire(camp);
                messages.send(player, "fuel.added", Map.of("items", result.items(),
                        "time", TimeUtil.formatMillis(result.addedMillis()), "remaining", TimeUtil.formatMillis(result.remainingMillis())));
                player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1F, 1F);
            }
        }
    }

    private Inventory create(MenuHolder holder, int size, String title) {
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.component(title));
        holder.inventory(inventory);
        return inventory;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = filler();
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private ItemStack filler() {
        ItemStack item = itemsAdder.item(plugin.getConfig().getString("settings.itemsadder.filler-item"), 1);
        if (item == null) {
            Material material = Material.matchMaterial(plugin.getConfig().getString("settings.gui.filler-material", "BLACK_STAINED_GLASS_PANE"));
            item = new ItemStack(material == null ? Material.BLACK_STAINED_GLASS_PANE : material);
        }
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack icon(String configKey, Material fallback, String name, List<Component> lore) {
        ItemStack item = configKey == null ? null : itemsAdder.item(plugin.getConfig().getString("settings.itemsadder." + configKey), 1);
        if (item == null) item = new ItemStack(fallback);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.component(name));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> lore(String path, Map<String, ?> replacements) {
        return messages.list(path).stream().map(line -> ColorUtil.component(messages.replace(line, replacements))).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private List<Component> costLore(Player player, int level) {
        List<Component> lore = new ArrayList<>();
        for (ItemStack item : costs.get(level)) {
            int has = player == null ? item.getAmount() : resources.count(player, item);
            String path = player != null && has < item.getAmount() ? "gui.missing-resource-line" : "gui.resource-line";
            String template = messages.replace(messages.raw(path), Map.of("amount", item.getAmount(), "has", has));
            int marker = template.indexOf("{item}");
            if (marker < 0) lore.add(ColorUtil.component(template));
            else lore.add(ColorUtil.component(template.substring(0, marker)).append(itemNames.component(item))
                    .append(ColorUtil.component(template.substring(marker + "{item}".length()))));
        }
        if (lore.isEmpty()) lore.add(ColorUtil.component("&8• &aБесплатно"));
        return lore;
    }

    private enum MenuType { MAIN, CONFIRM_PACK, STORAGE, EDITOR_LEVELS, EDITOR_COST }

    private static final class MenuHolder implements InventoryHolder, CampInventoryHolder {
        private final MenuType type;
        private final UUID owner;
        private final int level;
        private Inventory inventory;

        private MenuHolder(MenuType type, UUID owner, int level) {
            this.type = type;
            this.owner = owner;
            this.level = level;
        }

        MenuType type() { return type; }
        UUID owner() { return owner; }
        @Override public UUID campOwner() { return owner; }
        int level() { return level; }
        void inventory(Inventory value) { inventory = value; }
        @Override public Inventory getInventory() { return inventory; }
    }
}

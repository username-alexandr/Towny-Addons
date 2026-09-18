package ru.neverland.melons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NeverLandMelonsPlugin extends JavaPlugin {

    NamespacedKey goldenMelonItemKey;
    MelonRegistry registry;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        goldenMelonItemKey = new NamespacedKey(this, "golden_melon_block");
        registry = new MelonRegistry(this);

        getServer().getPluginManager().registerEvents(new MelonListener(this, registry), this);

        MelonCommand command = new MelonCommand(this, registry);
        Objects.requireNonNull(getCommand("nlmelon")).setExecutor(command);
        Objects.requireNonNull(getCommand("nlmelon")).setTabCompleter(command);

        registerRecipes();

        getLogger().info("NeverLandMelons 0.1.0-test enabled.");
    }

    @Override
    public void onDisable() {
        registry.save();
    }

    void reloadPlugin() {
        reloadConfig();
        registry.reload();
        registerRecipes();
    }

    ItemStack createGoldenMelonItem() {
        ItemStack item = new ItemStack(Material.MELON);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6Золотой арбуз");
        meta.setLore(List.of(
                "§7Установите блок и ешьте его порциями.",
                "§eКаждый укус даёт краткую регенерацию."
        ));
        meta.getPersistentDataContainer().set(
                goldenMelonItemKey,
                PersistentDataType.BYTE,
                (byte) 1
        );
        meta.setEnchantmentGlintOverride(true);

        item.setItemMeta(meta);
        return item;
    }

    boolean isGoldenMelonItem(ItemStack item) {
        if (item == null || item.getType() != Material.MELON || !item.hasItemMeta()) return false;

        Byte value = item.getItemMeta().getPersistentDataContainer().get(
                goldenMelonItemKey,
                PersistentDataType.BYTE
        );
        return value != null && value == (byte) 1;
    }

    private void registerRecipes() {
        NamespacedKey key = new NamespacedKey(this, "golden_melon_block");
        Bukkit.removeRecipe(key);

        if (!getConfig().getBoolean("golden-melon.recipe-enabled", true)) return;

        ShapedRecipe recipe = new ShapedRecipe(key, createGoldenMelonItem());
        recipe.shape("GGG", "GGG", "GGG");
        recipe.setIngredient('G', Material.GLISTERING_MELON_SLICE);
        Bukkit.addRecipe(recipe);
    }
}

final class MelonListener implements Listener {

    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    };

    private final NeverLandMelonsPlugin plugin;
    private final MelonRegistry registry;

    MelonListener(NeverLandMelonsPlugin plugin, MelonRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelonGrow(BlockGrowEvent event) {
        if (!plugin.getConfig().getBoolean("stem-fix.enabled", true)) return;
        if (event.getNewState().getType() != Material.MELON) return;

        repairStemForFruit(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.MELON) return;
        if (!plugin.isGoldenMelonItem(event.getItemInHand())) return;

        registry.put(
                event.getBlockPlaced(),
                new MelonRegistry.MelonData(true, 0)
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (eatGoldenSlice(event)) return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.MELON) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("neverlandmelons.use")) return;

        MelonRegistry.MelonData state = registry.get(block);
        boolean golden = state != null && state.golden();

        if (!golden && player.getFoodLevel() >= 20) {
            return;
        }

        event.setCancelled(true);

        int maxBites = Math.max(
                1,
                plugin.getConfig().getInt(
                        golden ? "golden-melon.bites" : "melon.bites",
                        7
                )
        );

        int currentBites = state == null ? 0 : state.bites();
        int nextBites = currentBites + 1;

        applyBite(player, golden);

        player.getWorld().playSound(
                block.getLocation(),
                Sound.ENTITY_GENERIC_EAT,
                0.85f,
                golden ? 1.15f : 1.0f
        );

        player.getWorld().spawnParticle(
                Particle.ITEM,
                block.getLocation().add(0.5, 0.8, 0.5),
                6,
                0.25,
                0.18,
                0.25,
                0.02,
                new ItemStack(golden ? Material.GLISTERING_MELON_SLICE : Material.MELON_SLICE)
        );

        if (nextBites >= maxBites) {
            registry.remove(block);
            block.setType(Material.AIR, false);

            player.sendActionBar(Component.text(
                    golden ? "Золотой арбуз съеден." : "Арбуз съеден.",
                    golden ? NamedTextColor.GOLD : NamedTextColor.GREEN
            ));
            return;
        }

        registry.put(block, new MelonRegistry.MelonData(golden, nextBites));

        player.sendActionBar(Component.text(
                (golden ? "Золотой арбуз" : "Арбуз")
                        + ": осталось " + (maxBites - nextBites) + " порц.",
                golden ? NamedTextColor.GOLD : NamedTextColor.GREEN
        ));
    }

    private boolean eatGoldenSlice(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.getType() != Material.GLISTERING_MELON_SLICE) return false;
        if (!player.hasPermission("neverlandmelons.use")) return false;

        int cooldown = Math.max(0, plugin.getConfig().getInt("golden-slice.cooldown-ticks", 8));
        if (player.getCooldown(Material.GLISTERING_MELON_SLICE) > 0) {
            event.setCancelled(true);
            return true;
        }

        event.setCancelled(true);

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.subtract(1);
        }

        double heal = Math.max(
                0.0,
                plugin.getConfig().getDouble("golden-slice.heal-health-points", 2.0)
        );

        double maxHealth = 20.0;
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        }

        player.setHealth(Math.min(maxHealth, player.getHealth() + heal));
        player.setCooldown(Material.GLISTERING_MELON_SLICE, cooldown);

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_GENERIC_EAT,
                0.9f,
                1.25f
        );

        player.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1.0, 0),
                4,
                0.25,
                0.25,
                0.25,
                0.0
        );

        player.sendActionBar(Component.text(
                "+" + String.format(Locale.ROOT, "%.1f", heal / 2.0) + " ❤",
                NamedTextColor.GOLD
        ));

        return true;
    }

    private void applyBite(Player player, boolean golden) {
        String root = golden ? "golden-melon" : "melon";

        int food = Math.max(0, plugin.getConfig().getInt(root + ".food-per-bite", 2));
        float saturation = (float) Math.max(
                0.0,
                plugin.getConfig().getDouble(root + ".saturation-per-bite", golden ? 0.6 : 0.4)
        );

        player.setFoodLevel(Math.min(20, player.getFoodLevel() + food));
        player.setSaturation(Math.min(20.0f, player.getSaturation() + saturation));

        if (golden) {
            int ticks = Math.max(
                    1,
                    plugin.getConfig().getInt("golden-melon.regeneration-ticks", 80)
            );
            int amplifier = Math.max(
                    0,
                    plugin.getConfig().getInt("golden-melon.regeneration-amplifier", 0)
            );

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    ticks,
                    amplifier,
                    true,
                    true,
                    true
            ));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.MELON) return;

        MelonRegistry.MelonData state = registry.remove(block);
        if (state == null) return;

        if (state.golden()) {
            event.setDropItems(false);

            if (state.bites() == 0 && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                block.getWorld().dropItemNaturally(
                        block.getLocation().add(0.5, 0.5, 0.5),
                        plugin.createGoldenMelonItem()
                );
            }
        } else if (state.bites() > 0) {
            event.setDropItems(false);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.MELON) registry.remove(block);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.MELON) registry.remove(block);
        }
    }

    int fixStemsAround(Player player, int radius) {
        radius = Math.max(1, Math.min(32, radius));

        Location center = player.getLocation();
        World world = center.getWorld();
        int fixed = 0;

        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int y = Math.max(world.getMinHeight(), center.getBlockY() - radius);
                 y <= Math.min(world.getMaxHeight() - 1, center.getBlockY() + radius);
                 y++) {
                for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.MELON && repairStemForFruit(block)) {
                        fixed++;
                    }
                }
            }
        }

        return fixed;
    }

    private boolean repairStemForFruit(Block fruit) {
        boolean fixed = false;

        for (BlockFace face : HORIZONTAL) {
            Block stem = fruit.getRelative(face);
            if (stem.getType() != Material.MELON_STEM
                    && stem.getType() != Material.ATTACHED_MELON_STEM) {
                continue;
            }

            BlockFace towardFruit = face.getOppositeFace();

            BlockData data = Bukkit.createBlockData(Material.ATTACHED_MELON_STEM);
            if (data instanceof Directional directional) {
                directional.setFacing(towardFruit);
            }

            if (stem.getType() != Material.ATTACHED_MELON_STEM
                    || !(stem.getBlockData() instanceof Directional current)
                    || current.getFacing() != towardFruit) {
                stem.setBlockData(data, false);
                fixed = true;
            }
        }

        return fixed;
    }
}

final class MelonRegistry {

    private final NeverLandMelonsPlugin plugin;
    private final File file;
    private final Map<String, MelonData> melons = new ConcurrentHashMap<>();
    private YamlConfiguration yaml;

    MelonRegistry(NeverLandMelonsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "melons.yml");
        reload();
    }

    synchronized void reload() {
        melons.clear();
        yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = yaml.getConfigurationSection("melons");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            boolean golden = section.getBoolean(key + ".golden", false);
            int bites = Math.max(0, section.getInt(key + ".bites", 0));
            melons.put(key, new MelonData(golden, bites));
        }
    }

    synchronized void save() {
        if (yaml == null) yaml = new YamlConfiguration();
        yaml.set("melons", null);

        for (Map.Entry<String, MelonData> entry : melons.entrySet()) {
            String path = "melons." + entry.getKey();
            yaml.set(path + ".golden", entry.getValue().golden());
            yaml.set(path + ".bites", entry.getValue().bites());
        }

        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Cannot save melons.yml: " + ex.getMessage());
        }
    }

    MelonData get(Block block) {
        return melons.get(keyOf(block));
    }

    void put(Block block, MelonData data) {
        melons.put(keyOf(block), data);
        save();
    }

    MelonData remove(Block block) {
        MelonData removed = melons.remove(keyOf(block));
        if (removed != null) save();
        return removed;
    }

    int size() {
        return melons.size();
    }

    private String keyOf(Block block) {
        return block.getWorld().getUID()
                + "_" + block.getX()
                + "_" + block.getY()
                + "_" + block.getZ();
    }

    record MelonData(boolean golden, int bites) {}
}

final class MelonCommand implements CommandExecutor, TabCompleter {

    private final NeverLandMelonsPlugin plugin;
    private final MelonRegistry registry;
    private final MelonListener listener;

    MelonCommand(NeverLandMelonsPlugin plugin, MelonRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.listener = new MelonListener(plugin, registry);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text(
                    "NeverLandMelons 0.1.0-test • сохранённых съедобных блоков: " + registry.size(),
                    NamedTextColor.GOLD
            ));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("neverlandmelons.admin")) {
                sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }

            plugin.reloadPlugin();
            sender.sendMessage(Component.text("NeverLandMelons перезагружен.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("neverlandmelons.admin")) {
                sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }

            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
                    return true;
                }
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(Component.text("Укажите игрока.", NamedTextColor.RED));
                return true;
            }

            giveOrDrop(target, plugin.createGoldenMelonItem());
            giveOrDrop(target, new ItemStack(Material.GLISTERING_MELON_SLICE, 8));

            sender.sendMessage(Component.text(
                    "Выданы золотой арбуз и 8 золотых ломтиков.",
                    NamedTextColor.GREEN
            ));
            return true;
        }

        if (args[0].equalsIgnoreCase("fixstems")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only player.");
                return true;
            }

            if (!sender.hasPermission("neverlandmelons.admin")) {
                sender.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }

            int radius = 8;
            if (args.length >= 2) {
                try {
                    radius = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    radius = 8;
                }
            }

            int fixed = listener.fixStemsAround(player, radius);
            player.sendMessage(Component.text(
                    "Исправлено соединений стебля: " + fixed,
                    NamedTextColor.GREEN
            ));
            return true;
        }

        return false;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack left : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("status", "give", "fixstems", "reload"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[1]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("fixstems")) {
            return filter(List.of("4", "8", "16", "24", "32"), args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        }

        return result;
    }
}

package ru.neverland.townybuilds.army;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townybuilds.api.*;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.util.ColorUtil;
import java.io.*;
import java.util.*;

/** Towny player mobilisation. UUIDs and verified character ages survive name changes/restarts. */
public final class ArmyService implements CommandExecutor, TabCompleter, Listener, TownArmyApi {
    private final JavaPlugin plugin;
    private final TownyHook towny;
    private final DataStore data;
    private final File file;
    private final Map<UUID, Integer> ages = new HashMap<>();
    private final Map<UUID, UUID> roster = new LinkedHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private BukkitTask task;
    private long ageWarningAt;
    public ArmyService(JavaPlugin plugin, TownyHook towny, DataStore data) {
        this.plugin = plugin; this.towny = towny; this.data = data;
        file = new File(plugin.getDataFolder(), "army-data.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var ageRoot = yaml.getConfigurationSection("ages");
        if (ageRoot != null) for (String id : ageRoot.getKeys(false)) try {
            var value = MobilizationPolicy.parseAge(ageRoot.getString(id));
            if (value.isPresent()) ages.put(UUID.fromString(id), value.getAsInt());
        } catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Повреждённый возраст в army-data.yml: " + id); }
        var soldiers = yaml.getConfigurationSection("soldiers");
        if (soldiers != null) for (String id : soldiers.getKeys(false)) try {
            roster.put(UUID.fromString(id), UUID.fromString(soldiers.getString(id)));
        } catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Повреждённый солдат в army-data.yml: " + id); }
    }
    public void start() {
        Bukkit.getServicesManager().register(TownArmyApi.class, this, plugin, ServicePriority.Normal);
        // Wait for optional passport providers to register during plugin startup.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcile, 100, 200);
    }
    public void stop() {
        if (task != null) task.cancel();
        for (var entry : attachments.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.removeAttachment(entry.getValue());
        }
        attachments.clear(); save();
        Bukkit.getServicesManager().unregister(TownArmyApi.class, this);
    }
    private OptionalInt age(UUID id) {
        var registration = Bukkit.getServicesManager().getRegistration(ResidentAgeProvider.class);
        try {
            if (registration != null) return registration.getProvider().age(id);
            String placeholder = plugin.getConfig().getString("army.age-placeholder", "");
            if (!placeholder.isBlank()) {
                var papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
                if (papi == null || !papi.isEnabled()) return OptionalInt.empty();
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, papi.getClass().getClassLoader());
                Object result = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class)
                        .invoke(null, Bukkit.getOfflinePlayer(id), placeholder);
                return MobilizationPolicy.parseAge(String.valueOf(result));
            }
            Integer value = ages.get(id);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (System.currentTimeMillis() - ageWarningAt > 60000) {
                ageWarningAt = System.currentTimeMillis(); plugin.getLogger().warning("Не удалось получить возраст персонажа: " + ex.getMessage());
            }
            return OptionalInt.empty();
        }
    }
    private int level(Town town) { return town == null ? 0 : data.town(town.getUUID()).operationalLevel("army"); }
    private int capacity(Town town) { return town == null ? 0 : (int)Math.floor(level(town) * Math.max(1, Math.min(1000, plugin.getConfig().getInt("army.soldiers-per-level", 10)))
            * ru.neverland.integration.DistrictBonuses.multiplier(town.getUUID(), "army")); }
    private boolean manager(Player player, Town town) { return towny.isMayor(player, town) || player.hasPermission("neverlandtownybuilds.army.mobilize"); }
    private boolean sameTown(Resident resident, Town town) { return resident != null && resident.getTownOrNull() != null && resident.getTownOrNull().getUUID().equals(town.getUUID()); }
    @Override public boolean isMobilized(UUID id) {
        UUID townId = roster.get(id);
        if (townId == null) return false;
        Town town = towny.town(townId);
        Resident resident = TownyAPI.getInstance().getResident(id);
        return town != null && level(town) > 0 && sameTown(resident, town) && age(id).orElse(-1) >= 18;
    }
    @Override public Set<UUID> soldiers(UUID townId) {
        Set<UUID> result = new LinkedHashSet<>();
        for (var entry : roster.entrySet()) if (entry.getValue().equals(townId) && isMobilized(entry.getKey())) result.add(entry.getKey());
        return Set.copyOf(result);
    }
    private void reconcile() {
        // Temporary provider outages suspend privileges; they do not erase the saved roster.
        boolean changed = roster.entrySet().removeIf(entry -> {
            Town town = towny.town(entry.getValue());
            return town == null || data.town(town.getUUID()).level("army") == 0 || !sameTown(TownyAPI.getInstance().getResident(entry.getKey()), town);
        });
        attachments.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) return true;
            if (!isMobilized(entry.getKey())) { player.removeAttachment(entry.getValue()); return true; }
            return false;
        });
        for (Player player : Bukkit.getOnlinePlayers()) if (isMobilized(player.getUniqueId()) && !attachments.containsKey(player.getUniqueId())) {
            var attachment = player.addAttachment(plugin);
            attachment.setPermission("neverlandtownybuilds.army.soldier", true);
            attachments.put(player.getUniqueId(), attachment);
        }
        if (changed) save();
    }
    private void tell(CommandSender sender, String text) { sender.sendMessage(ColorUtil.component("&#B65CFF[NeverLand • Армия] &f" + text)); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("age")) { setAge(sender, args); return true; }
        if (!(sender instanceof Player player)) { tell(sender, "/townyarmy age <житель> <0..150|clear>"); return true; }
        if (!player.hasPermission("neverlandtownybuilds.army.use")) { tell(player, "&cНет доступа к армии."); return true; }
        Town town = towny.town(player);
        if (town == null) { tell(player, "&cВы не состоите в городе."); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { open(player, 0); return true; }
        if (args.length == 2 && Set.of("mobilize", "release").contains(args[0].toLowerCase(Locale.ROOT))) {
            Resident resident = TownyAPI.getInstance().getResident(args[1]);
            change(player, town, resident, args[0].equalsIgnoreCase("mobilize"));
        } else tell(player, "/t army — состав; /t army mobilize <житель>; /t army release <житель>");
        return true;
    }
    private void setAge(CommandSender sender, String[] args) {
        if (!sender.hasPermission("neverlandtownybuilds.army.age")) { tell(sender, "&cВозраст персонажа подтверждает администратор."); return; }
        if (args.length != 3) { tell(sender, "/townyarmy age <житель> <0..150|clear>"); return; }
        Resident resident = TownyAPI.getInstance().getResident(args[1]);
        if (resident == null) { tell(sender, "&cЖитель Towny не найден."); return; }
        if (args[2].equalsIgnoreCase("clear")) ages.remove(resident.getUUID());
        else {
            OptionalInt value = MobilizationPolicy.parseAge(args[2]);
            if (value.isEmpty()) { tell(sender, "&cУкажите целый возраст персонажа от 0 до 150."); return; }
            ages.put(resident.getUUID(), value.getAsInt());
        }
        save(); reconcile();
        tell(sender, "Возраст персонажа сохранён. При подключённом паспорте используется возраст из него.");
    }
    private void change(Player player, Town town, Resident resident, boolean mobilize) {
        if (!player.hasPermission("neverlandtownybuilds.army.use") || !manager(player, town)) { tell(player, "&cМобилизацией управляет мэр или уполномоченный офицер."); return; }
        if (!sameTown(resident, town)) { tell(player, "&cМожно выбирать только жителей своего города."); return; }
        if (!mobilize) {
            if (town.getUUID().equals(roster.get(resident.getUUID()))) { roster.remove(resident.getUUID()); save(); reconcile(); tell(player, resident.getName() + " демобилизован."); }
            else tell(player, "Этот житель не мобилизован.");
            return;
        }
        var result = MobilizationPolicy.check(true, level(town), true, age(resident.getUUID()),
                roster.containsKey(resident.getUUID()), (int) roster.values().stream().filter(town.getUUID()::equals).count(), capacity(town));
        String error = switch (result) {
            case ALLOWED -> null;
            case NO_BUILDING -> "Нужен действующий «Штаб армии». Постройте его или оплатите содержание: /t upkeep.";
            case UNKNOWN_AGE -> "Возраст персонажа не подтверждён.";
            case UNDER_AGE -> "Мобилизация доступна с 18 лет персонажа.";
            case ALREADY_MOBILIZED -> "Житель уже мобилизован.";
            case FULL -> "Штат заполнен. Улучшите штаб армии.";
            default -> "Мобилизация недоступна.";
        };
        if (error != null) { tell(player, "&c" + error); return; }
        roster.put(resident.getUUID(), town.getUUID()); save(); reconcile();
        tell(player, resident.getName() + " мобилизован в армию города.");
        Player target = Bukkit.getPlayer(resident.getUUID());
        if (target != null && !target.equals(player)) tell(target, "Вы мобилизованы в армию города " + town.getName() + ". Состав: /t army");
    }
    public void open(Player player, int page) {
        Town town = towny.town(player);
        if (town == null || !player.hasPermission("neverlandtownybuilds.army.use")) { tell(player, "&cНет доступа к армии города."); return; }
        reconcile();
        List<Resident> residents = town.getResidents().stream().sorted(Comparator.comparing(Resident::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int current = Math.max(0, Math.min(page, Math.max(0, (residents.size() - 1) / 45)));
        ArmyHolder holder = new ArmyHolder(town.getUUID(), current);
        holder.inventory = Bukkit.createInventory(holder, 54, ColorUtil.component("&#B65CFFАрмия города · " + (current + 1)));
        for (int n = current * 45; n < Math.min(residents.size(), current * 45 + 45); n++) {
            Resident resident = residents.get(n); UUID id = resident.getUUID(); int slot = n % 45;
            boolean mobilized = town.getUUID().equals(roster.get(id)); OptionalInt age = age(id);
            holder.residents.put(slot, id);
            holder.inventory.setItem(slot, item(mobilized ? Material.IRON_SWORD : Material.PLAYER_HEAD, resident.getName(), List.of(
                    "&7Возраст персонажа: &f" + (age.isPresent() ? age.getAsInt() : "не подтверждён"),
                    mobilized ? (isMobilized(id) ? "&aМобилизован" : "&eСлужба приостановлена: проверьте возраст") : "&7Гражданский",
                    manager(player, town) ? (mobilized ? "&eНажмите для демобилизации" : "&aНажмите для мобилизации (18+)") : "&7Управление доступно мэру/офицеру")));
        }
        holder.inventory.setItem(49, item(Material.NETHERITE_HELMET, "Штаб армии · уровень " + level(town), List.of(
                "&7Действующих солдат: &f" + soldiers(town.getUUID()).size() + "/" + capacity(town), "&7Мобилизация с 18 лет персонажа")));
        if (current > 0) holder.inventory.setItem(45, item(Material.ARROW, "Предыдущая страница", List.of()));
        if ((current + 1) * 45 < residents.size()) holder.inventory.setItem(53, item(Material.ARROW, "Следующая страница", List.of()));
        player.openInventory(holder.inventory);
    }
    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> { meta.displayName(ColorUtil.component("&f" + name)); meta.lore(lore.stream().map(ColorUtil::component).toList()); });
        return item;
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ArmyHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Town town = towny.town(player);
        if (town == null || !town.getUUID().equals(holder.town)) { player.closeInventory(); return; }
        int slot = event.getRawSlot(); UUID id = holder.residents.get(slot);
        if (id != null) { change(player, town, TownyAPI.getInstance().getResident(id), !town.getUUID().equals(roster.get(id))); open(player, holder.page); }
        else if ((slot == 45 || slot == 53) && event.getCurrentItem() != null) open(player, holder.page + (slot == 45 ? -1 : 1));
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ArmyHolder) event.setCancelled(true);
    }
    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ages.forEach((id, age) -> yaml.set("ages." + id, age));
        roster.forEach((id, town) -> yaml.set("soldiers." + id, town.toString()));
        try { yaml.save(file); } catch (IOException ex) { plugin.getLogger().severe("Не удалось сохранить army-data.yml: " + ex.getMessage()); }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = new ArrayList<>();
        if (args.length == 1) { choices.addAll(List.of("list", "mobilize", "release")); if (sender.hasPermission("neverlandtownybuilds.army.age")) choices.add("age"); }
        else if (args.length == 2 && sender instanceof Player player) {
            Town town = towny.town(player); if (town != null) town.getResidents().forEach(resident -> choices.add(resident.getName()));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    private static final class ArmyHolder implements InventoryHolder {
        private final UUID town; private final int page; private final Map<Integer, UUID> residents = new HashMap<>(); private Inventory inventory;
        private ArmyHolder(UUID town, int page) { this.town = town; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}

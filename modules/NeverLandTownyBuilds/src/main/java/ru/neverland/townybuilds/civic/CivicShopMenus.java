package ru.neverland.townybuilds.civic;
import ru.neverland.localization.MaterialNameConfig;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.Player;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import ru.neverland.townybuilds.api.CivicBenefit;
import ru.neverland.townybuilds.api.TownShopTradeEvent;
import ru.neverland.townybuilds.api.TownyBuildsApi;
import ru.neverland.townybuilds.construction.CivicBlueprintGenerator;
import ru.neverland.townybuilds.data.DataStore;
import ru.neverland.townybuilds.data.TownData;
import ru.neverland.townybuilds.integration.TownyHook;
import ru.neverland.townybuilds.service.MessageService;
import ru.neverland.townybuilds.service.RussianItemNames;
import ru.neverland.townybuilds.util.ColorUtil;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Trade menus and stall settings; ShopService owns every stock/money mutation of a purchase. */
public final class CivicShopMenus implements Listener {
    private record Offer(Material material,long cents){}
    private record ShopHolder(UUID viewer,UUID townId,Map<Integer,Offer> displayed) implements InventoryHolder {public Inventory getInventory(){return null;}}
    private record PurchasesHolder(UUID viewer,Map<Integer,UUID> orders,int page) implements InventoryHolder {public Inventory getInventory(){return null;}}
    private static final DecimalFormat MONEY=new DecimalFormat("#,##0.##",DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ru-RU")));
    private final JavaPlugin plugin;private final TownyHook towny;private final DataStore dataStore;private final MessageService messages;private final RussianItemNames itemNames;private final CivicService civic;
    private final ru.neverland.townybuilds.shop.ShopService service;
    public CivicShopMenus(JavaPlugin plugin,TownyHook towny,DataStore data,MessageService messages,RussianItemNames names,CivicService civic){this.plugin=plugin;this.towny=towny;dataStore=data;this.messages=messages;itemNames=names;this.civic=civic;service=new ru.neverland.townybuilds.shop.ShopService(plugin,data);Bukkit.getPluginManager().registerEvents(this,plugin);}
    public ru.neverland.townybuilds.shop.ShopService service(){return service;}
    public void start(){service.start();}public void stop(){service.stop();}
    private Town requireTown(Player p){return civic.requireTown(p);}
    private boolean requireMayor(Player p,Town t){return civic.requireMayor(p,t);}
    private boolean requireProject(Player p,TownData d,String id){return civic.requireProject(p,d,id);}
    private void openCivicStorage(Player p,Town town,TownData d,String id){((ru.neverland.townybuilds.NeverLandTownyBuilds)plugin).storage().openStorage(p,id);}
    public void setStall(Player player, String rawId) {
        String id = normalizeId(rawId);
        if (id.isBlank()) {
            messages.send(player, "civic-invalid-stall");
            return;
        }
        Location location = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        String root = "settings.civic.spawn-shops.stalls." + id;
        plugin.getConfig().set(root + ".world", location.getWorld().getUID().toString());
        plugin.getConfig().set(root + ".x", location.getX());
        plugin.getConfig().set(root + ".y", location.getY());
        plugin.getConfig().set(root + ".z", location.getZ());
        plugin.getConfig().set(root + ".yaw", player.getLocation().getYaw());
        plugin.saveConfig();
        messages.send(player, "civic-stall-set", Map.of("stall", id));
    }

    public void removeStall(org.bukkit.command.CommandSender sender, String rawId) {
        String id = normalizeId(rawId);
        if (stallLocation(id) == null) {
            messages.send(sender, "civic-stall-missing", Map.of("stall", id));
            return;
        }
        plugin.getConfig().set("settings.civic.spawn-shops.stalls." + id, null);
        plugin.saveConfig();
        for (TownData data : dataStore.towns().values()) {
            if (data.shopStall().equals(id)) data.setShopStall("");
        }
        dataStore.markDirty();
        dataStore.save();
        messages.send(sender, "civic-stall-removed", Map.of("stall", id));
    }

    public List<String> stallIds() {
        var section = plugin.getConfig().getConfigurationSection("settings.civic.spawn-shops.stalls");
        return section == null ? List.of() : section.getKeys(false).stream().sorted().toList();
    }

    public void shop(Player player, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("browse")) {
            openNearestShop(player);
            return;
        }
        if(args[1].equalsIgnoreCase("purchases")){purchases(player,0);return;}
        Town town = requireTown(player);
        if (town == null) return;
        TownData data = dataStore.town(town.getUUID());
        if (!requireProject(player, data, "merchant_guild")) return;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "stock" -> openCivicStorage(player, town, data, "merchant_guild");
            case "claim" -> claimShop(player, town, data, args);
            case "release" -> releaseShop(player, town, data);
            case "price" -> setShopPrice(player, town, data, args);
            case "list" -> listShop(player, town, data);
            default -> messages.send(player, "civic-shop-help");
        }
    }

    private void claimShop(Player player, Town town, TownData data, String[] args) {
        if (!requireMayor(player, town)) return;
        if (data.operationalLevel("merchant_guild") < 3) {
            messages.send(player, "civic-shop-level");
            return;
        }
        if (args.length < 3) {
            messages.send(player, "civic-shop-help");
            return;
        }
        String id = normalizeId(args[2]);
        Location stall = stallLocation(id);
        if (stall == null) {
            messages.send(player, "civic-stall-missing", Map.of("stall", id));
            return;
        }
        double radius = Math.max(2, plugin.getConfig().getDouble("settings.civic.spawn-shops.claim-radius", 8));
        if (!stall.getWorld().equals(player.getWorld()) || stall.distanceSquared(player.getLocation()) > radius * radius) {
            messages.send(player, "civic-shop-not-at-stall", Map.of("stall", id));
            return;
        }
        for (TownData other : dataStore.towns().values()) {
            if (!other.townId().equals(town.getUUID()) && other.shopStall().equals(id)) {
                messages.send(player, "civic-shop-occupied", Map.of("stall", id));
                return;
            }
        }
        data.setShopStall(id);
        dataStore.markDirty();
        dataStore.save();
        messages.send(player, "civic-shop-claimed", Map.of("stall", id));
    }

    private void releaseShop(Player player, Town town, TownData data) {
        if (!requireMayor(player, town)) return;
        data.setShopStall("");
        dataStore.markDirty();
        dataStore.save();
        messages.send(player, "civic-shop-released");
    }

    private void setShopPrice(Player player, Town town, TownData data, String[] args) {
        if (!requireMayor(player, town)) return;
        if (args.length < 4) {
            messages.send(player, "civic-shop-help");
            return;
        }
        Material material = MaterialNameConfig.matchMaterial(args[2]);
        if (material == null || !material.isItem() || !allowedMaterials().contains(material)) {
            messages.send(player, "civic-shop-material");
            return;
        }
        try {
            double price = Double.parseDouble(args[3].replace(',', '.'));
            double maximum = Math.max(1, plugin.getConfig().getDouble("settings.civic.spawn-shops.maximum-unit-price", 1_000_000));
            if (!Double.isFinite(price) || price < 0 || price > maximum) throw new NumberFormatException();
            int limit = 4 + data.operationalLevel("merchant_guild") * 4 + data.operationalLevel("crystal_palace") * 16;
            if (price > 0 && data.shopPrice(material.name()) <= 0 && data.shopPrices().size() >= limit) {
                messages.send(player, "civic-shop-listing-limit", Map.of("limit", limit));
                return;
            }
            if(price>0)ru.neverland.townybuilds.shop.ShopService.cents(price);
            data.setShopPrice(material.name(), price);
            dataStore.markDirty();
            dataStore.save();
            messages.send(player, price <= 0 ? "civic-shop-price-removed" : "civic-shop-price-set", Map.of(
                    "material", itemNames.name(material), "price", MONEY.format(price)));
        } catch (IllegalArgumentException exception) {
            messages.send(player, "civic-invalid-number");
        }
    }

    private void listShop(Player player, Town town, TownData data) {
        player.sendMessage(ColorUtil.component("&#FFD45B&lЛавка города " + town.getName()
                + " &8• &f" + (data.shopStall().isBlank() ? "место не занято" : data.shopStall())));
        if (data.shopPrices().isEmpty()) {
            player.sendMessage(ColorUtil.component("&7Товары ещё не выставлены."));
            return;
        }
        data.shopPrices().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                player.sendMessage(ColorUtil.component("&8• &f" + itemNames.name(MaterialNameConfig.matchMaterial(entry.getKey())) + " &7— &e"
                        + MONEY.format(entry.getValue()) + " &7за шт.")));
    }

    private void openNearestShop(Player player) {
        TownData nearest = null;
        double best = Double.MAX_VALUE;
        double radius = Math.max(2, plugin.getConfig().getDouble("settings.civic.spawn-shops.browse-radius", 8));
        for (TownData data : dataStore.towns().values()) {
            if (data.shopStall().isBlank() || data.operationalLevel("merchant_guild")==0) continue;
            Location location = stallLocation(data.shopStall());
            if (location == null || !location.getWorld().equals(player.getWorld())) continue;
            double distance = location.distanceSquared(player.getLocation());
            if (distance <= radius * radius && distance < best) {
                best = distance;
                nearest = data;
            }
        }
        if (nearest == null) {
            messages.send(player, "civic-shop-none-nearby");
            return;
        }
        openShop(player, nearest);
    }

    private void openShop(Player player, TownData sellerData) {
        if(sellerData.operationalLevel("merchant_guild")==0){player.sendMessage(ColorUtil.component("&cЛавка временно не работает: проверьте содержание и питание гильдии."));return;}
        Town seller = towny.town(sellerData.townId());
        if (seller == null) {
            messages.send(player, "civic-shop-unavailable");
            return;
        }
        Map<Integer, Offer> displayed = new LinkedHashMap<>();
        Inventory inventory = Bukkit.createInventory(new ShopHolder(player.getUniqueId(),sellerData.townId(), displayed), 54,
                ColorUtil.component("&#FFD45BЛавка &8• &f" + seller.getName()));
        ItemStack[] stock = sellerData.civicInventory("merchant_guild", 54);
        int slot = 10;
        for (Map.Entry<String, Double> listing : sellerData.shopPrices().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            Material material = MaterialNameConfig.matchMaterial(listing.getKey());
            if (material == null || listing.getValue() <= 0) continue;
            try{ru.neverland.townybuilds.shop.ShopService.cents(listing.getValue());}catch(IllegalArgumentException invalidPrice){continue;}
            int amount = countPlain(stock, material);
            if (amount <= 0) continue;
            while (slot % 9 == 0 || slot % 9 == 8) slot++;
            if (slot >= 44) break;
            ItemStack icon = new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(ColorUtil.component(itemNames.name(material)));
            meta.lore(List.of(
                    ColorUtil.component("&7Цена за единицу: &e" + MONEY.format(listing.getValue())),
                    ColorUtil.component("&7В наличии: &f" + amount),
                    ColorUtil.component("&#63E6BEЛКМ: 1 &8• &#63E6BEShift+ЛКМ: стак")
            ));
            icon.setItemMeta(meta);
            inventory.setItem(slot, icon);
            displayed.put(slot, new Offer(material,ru.neverland.townybuilds.shop.ShopService.cents(listing.getValue())));
            slot++;
        }
        var button=new ItemStack(Material.CHEST);var meta=button.getItemMeta();meta.displayName(ColorUtil.component("&aМои покупки"));button.setItemMeta(meta);inventory.setItem(49,button);
        player.openInventory(inventory);
    }

    private Location stallLocation(String id) {
        String root = "settings.civic.spawn-shops.stalls." + id;
        String rawWorld = plugin.getConfig().getString(root + ".world", "");
        if (rawWorld.isBlank()) return null;
        try {
            World world = Bukkit.getWorld(UUID.fromString(rawWorld));
            if (world == null) return null;
            return new Location(world, plugin.getConfig().getDouble(root + ".x"),
                    plugin.getConfig().getDouble(root + ".y"), plugin.getConfig().getDouble(root + ".z"),
                    (float) plugin.getConfig().getDouble(root + ".yaw"), 0);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Set<Material> allowedMaterials() {
        Set<Material> configured = new java.util.LinkedHashSet<>();
        for (String value : plugin.getConfig().getStringList("settings.civic.spawn-shops.allowed-materials")) {
            Material material = MaterialNameConfig.matchMaterial(value);
            if (material != null && material.isItem()) configured.add(material);
        }
        return Set.copyOf(configured);
    }

    private int countPlain(ItemStack[] contents, Material material) {
        int result = 0;
        for (ItemStack item : contents) if (plain(item, material)) result += item.getAmount();
        return result;
    }

    private boolean plain(ItemStack item, Material material) {
        if (item == null || item.getType() != material || !allowedMaterials().contains(material)) return false;
        if (!item.hasItemMeta()) return true;
        ItemMeta meta = item.getItemMeta();
        return !meta.hasDisplayName() && !meta.hasLore() && meta.getEnchants().isEmpty()
                && meta.getPersistentDataContainer().isEmpty();
    }

    private String normalizeId(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    public void purchases(Player player,int page){
        var list=service.journal().all().stream().filter(o->o.buyer().equals(player.getUniqueId())&&!o.finalized()).sorted(java.util.Comparator.comparingLong(ru.neverland.townybuilds.shop.ShopOrder::created)).toList();
        Map<Integer,UUID> orders=new HashMap<>();var inventory=Bukkit.createInventory(new PurchasesHolder(player.getUniqueId(),orders,page),54,ColorUtil.component("&dПокупки в городских лавках"));int slot=0;
        for(var order:list.stream().skip(page*45L).limit(45).toList()){
            var item=new ItemStack(Material.valueOf(order.material()),Math.min(order.amount(),64));var meta=item.getItemMeta();meta.displayName(ColorUtil.component("&f"+itemNames.name(item.getType())));
            String receipt;try{receipt=service.receipt(order);}catch(Exception ex){receipt="UNAVAILABLE";}
            String status=receipt.equals("CLAIM_PENDING")?"Выдача требует сверки администратора":order.title();meta.lore(List.of(ColorUtil.component("&7Количество: &f"+order.amount()),ColorUtil.component("&7Стоимость: &e"+MONEY.format(order.total()/100.0)),ColorUtil.component("&7"+status),ColorUtil.component("&8ID: "+order.id()),ColorUtil.component("&aНажмите, чтобы забрать готовую покупку")));item.setItemMeta(meta);inventory.setItem(slot,item);orders.put(slot++,order.id());
        }
        if(page>0)inventory.setItem(45,new ItemStack(Material.ARROW));if((page+1)*45<list.size())inventory.setItem(53,new ItemStack(Material.ARROW));player.openInventory(inventory);
    }
    @EventHandler public void click(InventoryClickEvent event){
        Inventory inventory=event.getView().getTopInventory();var holder=inventory.getHolder();if(!(holder instanceof ShopHolder)&&!(holder instanceof PurchasesHolder))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||event.getRawSlot()<0||event.getRawSlot()>=inventory.getSize())return;
        UUID viewer=holder instanceof ShopHolder shop?shop.viewer():((PurchasesHolder)holder).viewer();int slot=event.getRawSlot();boolean stack=event.isShiftClick();
        Bukkit.getScheduler().runTask(plugin,()->{if(!player.isOnline()||!player.getUniqueId().equals(viewer)||player.getOpenInventory().getTopInventory()!=inventory)return;
            try{if(holder instanceof ShopHolder shop){if(slot==49){purchases(player,0);return;}var offer=shop.displayed().get(slot);if(offer==null)return;
                var order=service.purchase(player,shop.townId(),offer.material(),offer.cents(),stack);
                if(order.finalized()&&order.paymentStep().equals("COMPLETE"))messages.send(player,"civic-shop-purchased",Map.of("amount",order.amount(),"material",itemNames.name(offer.material()),"price",MONEY.format(order.total()/100.0),"town",towny.town(shop.townId()).getName()));
                else player.sendMessage(ColorUtil.component("&e"+order.title()+". Состояние доступно в «Мои покупки»."));openShop(player,dataStore.town(shop.townId()));
            }else {var list=(PurchasesHolder)holder;if(slot==45&&list.page()>0){purchases(player,list.page()-1);return;}if(slot==53){purchases(player,list.page()+1);return;}UUID order=list.orders().get(slot);if(order==null)return;String result=service.claim(player,order);player.sendMessage(ColorUtil.component(result.equals("CLAIMED")?"&aПокупка получена":result.equals("FULL")?"&eОсвободите место в инвентаре":result.equals("CLAIM_PENDING")?"&eВыдача ожидает сверки администратора":"&e"+result));purchases(player,list.page());}
            }catch(Exception ex){player.sendMessage(ColorUtil.component("&c"+ex.getMessage()+". Проверьте «Мои покупки»: /t shop purchases"));player.closeInventory();}
        });
    }
    @EventHandler public void drag(InventoryDragEvent event){var holder=event.getView().getTopInventory().getHolder();if(holder instanceof ShopHolder||holder instanceof PurchasesHolder)event.setCancelled(true);}
    public void admin(org.bukkit.command.CommandSender sender,String[] args){try{
        if(args.length==2&&args[1].equalsIgnoreCase("payments")){var pending=service.journal().all().stream().filter(o->!o.finalized()).toList();sender.sendMessage("Незавершённые покупки: "+pending.size());pending.stream().limit(50).forEach(o->sender.sendMessage(o.id()+" — "+o.title()+" — "+MONEY.format(o.total()/100.0)));return;}
        if(args.length==5&&args[1].equalsIgnoreCase("resolve")&&args[4].equalsIgnoreCase("confirm")){UUID id=UUID.fromString(args[2]);service.resolve(id,args[3]);plugin.getLogger().warning(sender.getName()+" сверил лавку "+id+": "+args[3]);sender.sendMessage("Решение сохранено; операция продолжится автоматически.");return;}
        sender.sendMessage("/townybuilds shop payments | /townybuilds shop resolve <UUID> <debit-paid|debit-unpaid|credit-paid|credit-unpaid|claim-received|claim-not-received> confirm");
    }catch(Exception ex){sender.sendMessage("Операция не выполнена: "+ex.getMessage());}}
}

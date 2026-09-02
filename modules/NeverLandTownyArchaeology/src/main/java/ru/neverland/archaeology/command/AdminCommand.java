package ru.neverland.archaeology.command;

import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.archaeology.TownyArchaeology;
import ru.neverland.archaeology.api.MuseumSnapshot;
import ru.neverland.archaeology.integration.TownyHook;
import ru.neverland.archaeology.model.ArtifactDefinition;
import ru.neverland.archaeology.model.SiteDefinition;
import ru.neverland.archaeology.service.ArchaeologyRegistry;
import ru.neverland.archaeology.service.ArtifactService;
import ru.neverland.archaeology.service.MessageService;
import ru.neverland.archaeology.service.MuseumService;
import ru.neverland.archaeology.service.SiteService;
import ru.neverland.archaeology.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AdminCommand implements TabExecutor {
    private static final String ADMIN_PERMISSION = "townyarchaeology.admin";
    private static final String GIVE_PERMISSION = "townyarchaeology.admin.give";

    private final TownyArchaeology plugin; private final TownyHook towny; private final ArchaeologyRegistry registry; private final ArtifactService artifacts; private final MuseumService museums; private final SiteService sites; private final MessageService messages;
    public AdminCommand(TownyArchaeology plugin, TownyHook towny, ArchaeologyRegistry registry, ArtifactService artifacts, MuseumService museums, SiteService sites, MessageService messages) { this.plugin = plugin; this.towny = towny; this.registry = registry; this.artifacts = artifacts; this.museums = museums; this.sites = sites; this.messages = messages; }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        boolean artifactCommand = command.getName().equalsIgnoreCase("artifact");
        if (artifactCommand) {
            if (!sender.hasPermission(GIVE_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) {
                messages.send(sender, "no-permission");
                return true;
            }
            if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
                messages.send(sender, "usage-artifact");
                return true;
            }
            give(sender, args, "usage-artifact");
            return true;
        }

        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage-admin");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPlugin();
                messages.send(sender, "reloaded");
            }
            case "give" -> give(sender, args, "usage-admin");
            case "site" -> site(sender, args);
            case "museum" -> museum(sender, args);
            case "info" -> info(sender, args);
            default -> messages.send(sender, "usage-admin");
        }
        return true;
    }

    private void give(CommandSender sender, String[] args, String usageMessage) {
        if (args.length < 3) {
            messages.send(sender, usageMessage);
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            messages.send(sender, "unknown-player", Map.of("player", args[1]));
            return;
        }
        ArtifactDefinition definition = registry.artifact(args[2]);
        if (definition == null) {
            messages.send(sender, "unknown-artifact", Map.of("artifact", args[2]));
            return;
        }
        int amount = number(args, 3, 1);
        if (amount < 1 || amount > 256) amount = 1;
        for (ItemStack item : artifacts.createBatch(definition.id(), amount)) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
        }
        messages.send(sender, "given", Map.of("artifact", definition.name(), "player", player.getName(), "amount", amount));
    }
    private void site(CommandSender sender, String[] args) { if (!(sender instanceof Player player)) { messages.send(sender, "players-only"); return; } if (args.length < 3 || !args[1].equalsIgnoreCase("create")) { messages.send(sender, "usage-admin"); return; } SiteDefinition definition = registry.site(args[2]); if (definition == null) { messages.send(sender, "unknown-site", Map.of("site", args[2])); return; } SiteService.Creation result = sites.create(player.getLocation(), definition, true); if (result.site() == null) messages.send(player, "site-failed"); else messages.send(player, "site-created", Map.of("site", definition.name(), "blocks", result.blocks())); }
    private void museum(CommandSender sender, String[] args) { if (args.length < 5 || !args[1].equalsIgnoreCase("add")) { messages.send(sender, "usage-admin"); return; } Town town = towny.town(args[2]); if (town == null) { messages.send(sender, "unknown-town", Map.of("town", args[2])); return; } ArtifactDefinition definition = registry.artifact(args[3]); if (definition == null) { messages.send(sender, "unknown-artifact", Map.of("artifact", args[3])); return; } int amount = number(args, 4, 1); museums.add(town.getUUID(), town.getName(), definition.id(), amount); messages.send(sender, "museum-added", Map.of("town", town.getName(), "artifact", definition.name(), "amount", amount)); }
    private void info(CommandSender sender, String[] args) { if (args.length < 2) { messages.send(sender, "usage-admin"); return; } Town town = towny.town(args[1]); if (town == null) { messages.send(sender, "unknown-town", Map.of("town", args[1])); return; } MuseumSnapshot snapshot = museums.snapshot(town.getUUID()); sender.sendMessage(ColorUtil.color("&#E6C363Музей города " + town.getName() + "\n&7Очки: &f" + snapshot.points() + "\n&7Уникальных артефактов: &f" + snapshot.donated().size() + "\n&7Доступно экземпляров: &f" + snapshot.available().values().stream().mapToInt(Integer::intValue).sum() + "\n&7Коллекций: &f" + snapshot.completedCollections().size())); }
    private int number(String[] args, int index, int fallback) { if (args.length <= index) return fallback; try { return Math.max(1, Integer.parseInt(args[index])); } catch (NumberFormatException ignored) { return fallback; } }
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        boolean artifactCommand = command.getName().equalsIgnoreCase("artifact");
        if (artifactCommand) {
            if (!sender.hasPermission(GIVE_PERMISSION) && !sender.hasPermission(ADMIN_PERMISSION)) return List.of();
            if (args.length == 1) return filter(List.of("give"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                return filter(registry.artifacts().stream().map(ArtifactDefinition::id).toList(), args[2]);
            }
            if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
                return filter(List.of("1", "8", "16", "32", "64"), args[3]);
            }
            return List.of();
        }

        if (!sender.hasPermission(ADMIN_PERMISSION)) return List.of();
        if (args.length == 1) return filter(List.of("reload", "give", "site", "museum", "info"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return filter(registry.artifacts().stream().map(ArtifactDefinition::id).toList(), args[2]);
        if (args.length == 2 && args[0].equalsIgnoreCase("site")) return filter(List.of("create"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("site")) return filter(registry.sites().stream().map(SiteDefinition::id).toList(), args[2]);
        if (args.length == 2 && args[0].equalsIgnoreCase("museum")) return filter(List.of("add"), args[1]);
        if ((args.length == 2 && args[0].equalsIgnoreCase("info")) || (args.length == 3 && args[0].equalsIgnoreCase("museum"))) return filter(towny.towns().stream().map(Town::getName).toList(), args[args.length - 1]);
        if (args.length == 4 && args[0].equalsIgnoreCase("museum")) return filter(registry.artifacts().stream().map(ArtifactDefinition::id).toList(), args[3]);
        return List.of();
    }
    private List<String> filter(List<String> values, String prefix) { String lower = prefix.toLowerCase(Locale.ROOT); List<String> result = new ArrayList<>(); for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value); return result; }
}

package ru.neverland.archaeology.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.archaeology.gui.ArchaeologyMenuManager;
import ru.neverland.archaeology.model.ArtifactDefinition;
import ru.neverland.archaeology.model.DigSite;
import ru.neverland.archaeology.model.SiteDefinition;
import ru.neverland.archaeology.service.ArchaeologyRegistry;
import ru.neverland.archaeology.service.ArtifactService;
import ru.neverland.archaeology.service.MessageService;
import ru.neverland.archaeology.service.MuseumService;
import ru.neverland.archaeology.service.SiteService;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ArchaeologyCommand implements TabExecutor {
    private final ArchaeologyRegistry registry; private final ArtifactService artifacts; private final MuseumService museums; private final SiteService sites; private final ArchaeologyMenuManager menus; private final MessageService messages;
    public ArchaeologyCommand(ArchaeologyRegistry registry, ArtifactService artifacts, MuseumService museums, SiteService sites, ArchaeologyMenuManager menus, MessageService messages) { this.registry = registry; this.artifacts = artifacts; this.museums = museums; this.sites = sites; this.menus = menus; this.messages = messages; }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) { if (!(sender instanceof Player player)) { messages.send(sender, "players-only"); return true; } if (!player.hasPermission("townyarchaeology.use")) { messages.send(player, "no-permission"); return true; } if (args.length == 0) { menus.openMain(player); return true; } switch (args[0].toLowerCase(Locale.ROOT)) { case "journal", "журнал" -> menus.openJournal(player); case "museum", "музей" -> menus.openMuseum(player); case "collections", "коллекции" -> menus.openCollections(player); case "wonders", "чудеса" -> menus.openWonders(player); case "donate", "сдать" -> donate(player, args); case "nearest", "ближайшие" -> nearest(player); default -> messages.send(player, "usage-main"); } return true; }
    private void donate(Player player, String[] args) { if (!player.hasPermission("townyarchaeology.donate")) { messages.send(player, "no-permission"); return; } boolean all = args.length > 1 && args[1].equalsIgnoreCase("all"); MuseumService.DonateResult result = all ? museums.donateAll(player) : museums.donateHand(player); switch (result.status()) { case SUCCESS -> { if (all) messages.send(player, "donated-many", Map.of("count", result.count(), "points", result.points())); else { ArtifactDefinition definition = registry.artifact(result.artifactId()); String message = result.count() > 1 ? "donated-stack" : "donated"; messages.send(player, message, Map.of("artifact", definition.name(), "town", result.town().getName(), "count", result.count(), "points", result.points())); } } case NO_TOWN -> messages.send(player, "no-town"); case NOT_ARTIFACT -> messages.send(player, "not-artifact"); case FORGED -> messages.send(player, "forged-artifact"); case DUPLICATE -> messages.send(player, "duplicate-artifact"); default -> messages.send(player, "nothing-to-donate"); } }
    private void nearest(Player player) { DigSite site = sites.nearest(player.getLocation(), 5000); if (site == null) { messages.send(player, "no-nearby-site"); return; } SiteDefinition definition = registry.site(site.type()); int distance = (int) Math.round(Math.sqrt(player.getLocation().distanceSquared(new org.bukkit.Location(player.getWorld(), site.x(), site.y(), site.z())))); messages.send(player, "nearest-site", Map.of("site", definition == null ? site.type() : definition.name(), "distance", distance, "x", site.x(), "y", site.y(), "z", site.z())); }
    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) { if (args.length == 1) return filter(List.of("journal", "museum", "collections", "wonders", "donate", "nearest"), args[0]); if (args.length == 2 && args[0].equalsIgnoreCase("donate")) return filter(List.of("all"), args[1]); return List.of(); }
    private List<String> filter(List<String> values, String prefix) { String lower = prefix.toLowerCase(Locale.ROOT); return values.stream().filter(value -> value.startsWith(lower)).toList(); }
}

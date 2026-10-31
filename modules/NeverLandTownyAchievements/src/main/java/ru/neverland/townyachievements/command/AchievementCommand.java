package ru.neverland.townyachievements.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import ru.neverland.townyachievements.NeverLandTownyAchievements;
import ru.neverland.townyachievements.gui.AchievementMenu;
import ru.neverland.townyachievements.service.*;

public final class AchievementCommand implements TabExecutor {
    private final NeverLandTownyAchievements plugin; private final AchievementService service; private final AchievementMenu menu;
    public AchievementCommand(NeverLandTownyAchievements plugin, AchievementService service, AchievementMenu menu) { this.plugin = plugin; this.service = service; this.menu = menu; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {if(ru.neverland.core.SuiteState.frozen("NeverLandTownyAchievements")){return true;}
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.isOp() && !sender.hasPermission("neverlandtownyachievements.admin")) throw new IllegalArgumentException("Нет прав администратора");
                plugin.reloadAchievements(); sender.sendMessage(ru.neverland.core.EditableMessages.text(AchievementCommand.class, "§aКаталог обновлён; начатые достижения и награды сохранены.")); return true;
            }
            if (!(sender instanceof Player player)) throw new IllegalArgumentException("Из консоли: /townyachievements admin list или reload");
            service.requireTown(player);
            if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("menu")) menu.home(player);
            else if (args.length == 1 && args[0].equalsIgnoreCase("rewards")) menu.rewards(player);
            else if (args.length == 2) switch (args[0].toLowerCase(Locale.ROOT)) {
                case "info" -> menu.achievement(player, args[1]);
                case "track" -> menu.track(player, ru.neverland.townyachievements.model.Achievement.Track.valueOf(args[1].toUpperCase(Locale.ROOT)));
                case "title" -> { service.selectTitle(player, args[1]); player.sendMessage(ru.neverland.core.EditableMessages.text(AchievementCommand.class, "§aТитул города обновлён.")); }
                case "cosmetic" -> { service.selectCosmetic(player, args[1]); player.sendMessage(ru.neverland.core.EditableMessages.text(AchievementCommand.class, "§aЭффект частиц обновлён.")); }
                case "banner" -> BannerRewards.apply(plugin, service, player, args[1]);
                default -> help(sender);
            } else help(sender);
        } catch (Exception ex) { sender.sendMessage("§c" + ex.getMessage()); }
        return true;
    } }
    private void help(CommandSender sender) { sender.sendMessage(ru.neverland.core.EditableMessages.text(AchievementCommand.class, "§e/t achievements [menu|rewards|track <common|trade|industry|military|culture>|info <ID>|title <ID|none>|cosmetic <ID|none>|banner <ID>]")); }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (args.length == 0 || !sender.isOp() && !sender.hasPermission("neverlandtownyachievements.use")) return List.of();
        var values = new ArrayList<String>();
        if (args.length == 1) {
            values.addAll(List.of("menu", "rewards", "track", "info", "title", "cosmetic", "banner"));
            if (sender.isOp() || sender.hasPermission("neverlandtownyachievements.admin")) values.add("reload");
        }
        if (args.length == 2 && sender instanceof Player p && service.town(p) != null && Set.of("info", "title", "cosmetic", "banner").contains(args[0])) {
            service.catalogue(service.town(p).getUUID()).forEach((id, progress) -> { if (args[0].equals("info") || progress.earned()) values.add(id); });
            if (args[0].equals("title") || args[0].equals("cosmetic")) values.add("none");
        }
        if (args.length == 2 && args[0].equals("track")) values.addAll(List.of("common", "trade", "industry", "military", "culture"));
        return values.stream().filter(s -> s.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    } }
}

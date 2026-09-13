package ru.neverland.townycitizens;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Isolated so the plugin works without PlaceholderAPI or a specific passport plugin. */
final class CitizensPlaceholders extends PlaceholderExpansion {
    private final NeverLandTownyCitizens plugin; private final CitizensService service;
    private CitizensPlaceholders(NeverLandTownyCitizens plugin, CitizensService service) { this.plugin = plugin; this.service = service; }
    static Runnable register(NeverLandTownyCitizens plugin, CitizensService service) {
        var expansion = new CitizensPlaceholders(plugin, service);
        if (!expansion.register()) throw new IllegalStateException("Не удалось зарегистрировать citizens placeholders");
        return expansion::unregister;
    }
    @Override public String getIdentifier() { return "townycitizens"; }
    @Override public String getAuthor() { return "NeverLand"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        // Do not access Towny from async renderers; they may retry on the main thread.
        if (!Bukkit.isPrimaryThread()) return "Недоступно";
        try {
            var town = service.ownTown(player.getUniqueId());
            if (params.equals("town")) return town == null ? "Нет города" : town.getName();
            var fields = service.passportFields(town == null ? null : town.getUUID(), player.getUniqueId());
            return switch (params) { case "status" -> fields.get("citizenship"); case "status_id" -> fields.get("citizenship_id");
                case "expires" -> fields.get("citizenship_expires"); case "tax_multiplier" -> fields.get("citizenship_tax_multiplier");
                case "vote" -> fields.get("citizenship_vote"); default -> null; };
        } catch (RuntimeException ex) { return "Недоступно"; }
    }
}

package ru.neverland.townyarmy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import com.palmergames.bukkit.towny.TownyAPI;
public final class ArmyExpansion extends PlaceholderExpansion {
    private final ArmyService service; private final String version;
    public ArmyExpansion(ArmyService service, String version) { this.service = service; this.version = version; }
    @Override public String getIdentifier() { return "townyarmy"; }
    @Override public String getAuthor() { return "Alexander Sokolov"; }
    @Override public String getVersion() { return version; }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, String key) {
        if (player == null || !Bukkit.isPrimaryThread() || !service.healthy()) return "—";
        try {
            var s = service.repository().state().soldiers().get(player.getUniqueId());
            if (key.equals("rank")) return s == null || !s.serving() ? "Нет" : service.settings().ranks().get(s.rank()).title();
            if (key.equals("unit")) return s == null || !s.serving() ? "Нет" : service.settings().units().get(s.unit()).title();
            if (key.equals("duty")) return service.onDuty(player.getUniqueId()) ? "Да" : "Нет";
            if (key.equals("training")) return s == null ? "0" : Integer.toString(s.training());
            var resident = TownyAPI.getInstance().getResident(player.getUniqueId()); var town = resident == null ? null : resident.getTownOrNull();
            if (town == null) return "0"; var g = service.garrison(town.getUUID()).orElseThrow();
            return switch (key) { case "active", "capacity", "reserve" -> g.get(key).toString(); case "readiness", "score" -> String.format(java.util.Locale.ROOT, "%.1f", g.get(key)); default -> null; };
        } catch (Exception | LinkageError e) { return "—"; }
    }
}

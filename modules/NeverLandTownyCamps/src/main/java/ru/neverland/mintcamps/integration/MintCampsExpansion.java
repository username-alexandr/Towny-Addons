package ru.neverland.mintcamps.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.mintcamps.data.CampRepository;
import ru.neverland.mintcamps.model.Camp;
import ru.neverland.mintcamps.service.CampService;
import ru.neverland.mintcamps.service.FuelService;
import ru.neverland.mintcamps.util.TimeUtil;

public final class MintCampsExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final CampRepository repository;
    private final CampService camps;
    private final FuelService fuel;

    public MintCampsExpansion(JavaPlugin plugin, CampRepository repository, CampService camps, FuelService fuel) {
        this.plugin = plugin;
        this.repository = repository;
        this.camps = camps;
        this.fuel = fuel;
    }

    @Override public @NotNull String getIdentifier() { return "mintcamps"; }
    @Override public @NotNull String getAuthor() { return "Alexander Sokolov"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (offline == null) return "";
        Camp camp = repository.get(offline.getUniqueId()).orElse(null);
        return switch (params.toLowerCase()) {
            case "has_camp" -> String.valueOf(camp != null);
            case "has_camp_formatted" -> camp == null ? "§cНет" : "§aДа";
            case "time_left" -> camp == null ? "0 сек." : TimeUtil.formatMillis(camp.remainingBurnMillis(System.currentTimeMillis()));
            case "level" -> camp == null ? "0" : String.valueOf(camp.level());
            case "level_name" -> camp == null ? "§7Нет лагеря" : camps.levelName(camp.level());
            case "style" -> camp == null ? "§7—" : camps.styleName(camp);
            case "trusted_count" -> camp == null ? "0" : String.valueOf(camp.trusted().size());
            case "max_trusted" -> camp == null ? "0" : String.valueOf(camps.maxTrusted(camp.level()));
            case "is_in_camp" -> String.valueOf(offline instanceof Player player && repository.findAt(player.getLocation()).isPresent());
            case "current_camp_owner" -> offline instanceof Player player
                    ? repository.findAt(player.getLocation()).map(Camp::ownerName).orElse("") : "";
            default -> null;
        };
    }
}

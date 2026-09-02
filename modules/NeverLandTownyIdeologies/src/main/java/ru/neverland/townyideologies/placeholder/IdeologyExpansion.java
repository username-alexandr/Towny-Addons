package ru.neverland.townyideologies.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.townyideologies.NeverLandTownyIdeologies;
import ru.neverland.townyideologies.model.IdeologyDefinition;
import ru.neverland.townyideologies.model.TownIdeology;
import ru.neverland.townyideologies.service.IdeologyRegistry;
import ru.neverland.townyideologies.service.IdeologyService;
import ru.neverland.townyideologies.util.ColorUtil;

public final class IdeologyExpansion extends PlaceholderExpansion {
    private final NeverLandTownyIdeologies plugin;
    private final IdeologyService ideologies;
    private final IdeologyRegistry registry;

    public IdeologyExpansion(NeverLandTownyIdeologies plugin, IdeologyService ideologies, IdeologyRegistry registry) {
        this.plugin = plugin;
        this.ideologies = ideologies;
        this.registry = registry;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nlti";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Alexander Sokolov";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        TownIdeology current = ideologies.get(player).orElse(null);
        if (current == null) {
            return switch (params.toLowerCase()) {
                case "ideology", "ideology_plain" -> "Не выбрана";
                case "level" -> "0";
                case "progress" -> "□□□□□";
                default -> "";
            };
        }
        IdeologyDefinition definition = registry.find(current.ideologyId()).orElse(null);
        return switch (params.toLowerCase()) {
            case "id" -> current.ideologyId();
            case "ideology" -> definition == null ? current.ideologyId() : ColorUtil.color(definition.name());
            case "ideology_plain" -> definition == null ? current.ideologyId() : ColorUtil.strip(definition.name());
            case "level" -> Integer.toString(current.level());
            case "progress" -> "■".repeat(current.level()) + "□".repeat(5 - current.level());
            default -> null;
        };
    }
}

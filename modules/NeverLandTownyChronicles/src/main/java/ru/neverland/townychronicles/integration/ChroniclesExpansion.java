package ru.neverland.townychronicles.integration;

import com.palmergames.bukkit.towny.object.Town;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.neverland.townychronicles.TownyChronicles;
import ru.neverland.townychronicles.model.ChronicleEntry;
import ru.neverland.townychronicles.model.TownChronicleState;
import ru.neverland.townychronicles.service.ChronicleRepository;
import ru.neverland.townychronicles.util.TimeUtil;

public final class ChroniclesExpansion extends PlaceholderExpansion {
    private final TownyChronicles plugin;private final TownyHook towny;private final ChronicleRepository repository;public ChroniclesExpansion(TownyChronicles plugin,TownyHook towny,ChronicleRepository repository){this.plugin=plugin;this.towny=towny;this.repository=repository;}
    @Override public @NotNull String getIdentifier(){return "townychronicles";}@Override public @NotNull String getAuthor(){return "Alexander Sokolov";}@Override public @NotNull String getVersion(){return plugin.getPluginMeta().getVersion();}@Override public boolean persist(){return true;}
    @Override public @Nullable String onRequest(OfflinePlayer player,@NotNull String params){if(player==null)return "";Town town=towny.townOf(player.getUniqueId());if(town==null)return "0";TownChronicleState state=repository.state(town.getUUID());ChronicleEntry latest=repository.latest(town.getUUID());return switch(params.toLowerCase()){case "entries"->String.valueOf(repository.entries(town.getUUID()).size());case "achievements"->String.valueOf(state.achievements().size());case "wonders"->String.valueOf(state.wonders().values().stream().filter(v->v>0).count());case "age_days"->String.valueOf(TimeUtil.days(state.foundedAt()));case "founding_date"->TimeUtil.date(state.foundedAt());case "latest_title"->latest==null?"Нет записей":latest.title();case "latest_date"->latest==null?"—":TimeUtil.date(latest.timestamp());default->null;};}
}

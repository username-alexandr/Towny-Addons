package ru.neverland.townyideologies.service;
import java.util.UUID;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.townyideologies.api.TownyIdeologiesApi;
import ru.neverland.townyideologies.data.DataStore;

public final class IdeologiesApiService implements TownyIdeologiesApi {
    private final JavaPlugin plugin; private final DataStore data;
    public IdeologiesApiService(JavaPlugin plugin, DataStore data) { this.plugin = plugin; this.data = data; }
    public String ideology(UUID town) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Ideologies API: main thread required");
        if (com.palmergames.bukkit.towny.TownyAPI.getInstance().getTown(town) == null) throw new IllegalArgumentException("Unknown town");
        return data.get(town).map(value -> value.ideologyId()).orElse("");
    }
    public String governmentForm(UUID town) {
        String id = ideology(town);
        String fallback = id.equals("monarchy") ? "MONARCHY" : id.equals("autocracy") ? "AUTOCRACY" : "DEMOCRACY";
        String form = plugin.getConfig().getString("government.ideology-forms." + (id.isEmpty() ? "none" : id), fallback).toUpperCase(Locale.ROOT);
        if (!Set.of("DEMOCRACY", "MONARCHY", "AUTOCRACY").contains(form)) throw new IllegalStateException("Unknown government form: " + form);
        return form;
    }
}

package ru.neverland.morstownstick.service;

import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.morstownstick.model.CellKey;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SelectionService {
    public enum ToggleResult { SELECTED, UNSELECTED, LIMIT }

    private final JavaPlugin plugin;
    private final Map<UUID, LinkedHashSet<CellKey>> selections = new LinkedHashMap<>();

    public SelectionService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ToggleResult toggle(UUID playerId, CellKey cell) {
        LinkedHashSet<CellKey> selected = selections.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        if (selected.remove(cell)) {
            removeEmpty(playerId, selected);
            return ToggleResult.UNSELECTED;
        }
        int maximum = plugin.getConfig().getInt("selection.max-chunks", -1);
        if (maximum >= 0 && selected.size() >= maximum) return ToggleResult.LIMIT;
        selected.add(cell);
        return ToggleResult.SELECTED;
    }

    public Set<CellKey> snapshot(UUID playerId) {
        Set<CellKey> selected = selections.get(playerId);
        return selected == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(selected));
    }

    public int count(UUID playerId) {
        Set<CellKey> selected = selections.get(playerId);
        return selected == null ? 0 : selected.size();
    }

    public void remove(UUID playerId, CellKey cell) {
        LinkedHashSet<CellKey> selected = selections.get(playerId);
        if (selected == null) return;
        selected.remove(cell);
        removeEmpty(playerId, selected);
    }

    public void removeAll(UUID playerId, Collection<CellKey> cells) {
        LinkedHashSet<CellKey> selected = selections.get(playerId);
        if (selected == null) return;
        selected.removeAll(cells);
        removeEmpty(playerId, selected);
    }

    public void clear(UUID playerId) {
        selections.remove(playerId);
    }

    public void clearAll() {
        selections.clear();
    }

    private void removeEmpty(UUID playerId, Set<CellKey> selected) {
        if (selected.isEmpty()) selections.remove(playerId);
    }
}

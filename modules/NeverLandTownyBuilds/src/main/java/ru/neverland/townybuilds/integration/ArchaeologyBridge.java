package ru.neverland.townybuilds.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArchaeologyBridge {
    private static final String API_CLASS = "ru.neverland.archaeology.api.TownyArchaeologyApi"; private final JavaPlugin plugin; private boolean warned;
    public ArchaeologyBridge(JavaPlugin plugin) { this.plugin = plugin; }
    public Map<String, Integer> requirements(String wonderId) { return map(call("wonderRequirements", new Class<?>[]{String.class}, wonderId)); }
    public Map<String, Integer> missing(UUID townId, String wonderId) { return map(call("missingForWonder", new Class<?>[]{UUID.class, String.class}, townId, wonderId)); }
    public int available(UUID townId, String artifactId) { Object value = call("available", new Class<?>[]{UUID.class, String.class}, townId, artifactId); return value instanceof Number number ? number.intValue() : 0; }
    public boolean consume(UUID townId, String wonderId, UUID actorId) { if (provider() == null) return true; Object value = call("consumeForWonder", new Class<?>[]{UUID.class, String.class, UUID.class}, townId, wonderId, actorId); return value instanceof Boolean bool && bool; }
    public String name(String artifactId) { Object value = call("artifactName", new Class<?>[]{String.class}, artifactId); return value == null ? artifactId : String.valueOf(value); }
    public List<String> missingLines(UUID townId, String wonderId) { List<String> result = new ArrayList<>(); missing(townId, wonderId).forEach((id, amount) -> result.add(name(id) + " x" + amount)); return result; }
    public List<String> requirementLines(UUID townId, String wonderId) { List<String> result = new ArrayList<>(); requirements(wonderId).forEach((id, amount) -> result.add(name(id) + " &7" + available(townId, id) + "/" + amount)); return result; }
    private Object call(String name, Class<?>[] parameters, Object... arguments) { Object provider = provider(); if (provider == null) return null; try { Method method = provider.getClass().getMethod(name, parameters); return method.invoke(provider, arguments); } catch (ReflectiveOperationException exception) { if (!warned) { warned = true; plugin.getLogger().warning("Интеграция NeverLandTownyArchaeology недоступна: " + exception.getMessage()); } return null; } }
    @SuppressWarnings({"rawtypes", "unchecked"}) private Object provider() { if (!Bukkit.getPluginManager().isPluginEnabled("NeverLandTownyArchaeology") && !Bukkit.getPluginManager().isPluginEnabled("TownyArchaeology")) return null; try { Class type = Class.forName(API_CLASS); return Bukkit.getServicesManager().load(type); } catch (ClassNotFoundException exception) { return null; } }
    private Map<String, Integer> map(Object value) { if (!(value instanceof Map<?, ?> raw)) return Map.of(); Map<String, Integer> result = new LinkedHashMap<>(); raw.forEach((key, amount) -> { if (key != null && amount instanceof Number number) result.put(String.valueOf(key), number.intValue()); }); return Map.copyOf(result); }
}

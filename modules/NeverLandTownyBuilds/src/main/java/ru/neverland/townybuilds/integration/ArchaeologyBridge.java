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
    public Map<String, Integer> requirements(String wonderId) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return map(call("wonderRequirements", new Class<?>[]{String.class}, wonderId)); } }
    public Map<String, Integer> missing(UUID townId, String wonderId) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return map(call("missingForWonder", new Class<?>[]{UUID.class, String.class}, townId, wonderId)); } }
    public int available(UUID townId, String artifactId) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { Object value = call("available", new Class<?>[]{UUID.class, String.class}, townId, artifactId); return value instanceof Number number ? number.intValue() : 0; } }
    public boolean consume(UUID townId, String wonderId, UUID actorId) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { if (provider().state()==ru.neverland.core.ApiServices.State.NOT_INSTALLED) return true; Object value = call("consumeForWonder", new Class<?>[]{UUID.class, String.class, UUID.class}, townId, wonderId, actorId); return value instanceof Boolean bool && bool; } }
    public boolean consumeRecorded(UUID townId,String wonderId,UUID actorId)throws ReflectiveOperationException {
        var api=refundableProvider();if(api.state()==ru.neverland.core.ApiServices.State.NOT_INSTALLED)return true;
        Object result=api.invoke("consumeForWonderDurable",new Class<?>[]{UUID.class,String.class,UUID.class},townId,wonderId,actorId);
        if(!(result instanceof Boolean))throw new IllegalStateException("Музей не подтвердил результат списания");
        return Boolean.TRUE.equals(result);
    }
    public String name(String artifactId) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { Object value = call("artifactName", new Class<?>[]{String.class}, artifactId); return value == null ? artifactId : String.valueOf(value); } }
    public List<ru.neverland.townybuilds.construction.ConstructionReceipt.Item> refundableArtifacts(String wonder) {
        List<ru.neverland.townybuilds.construction.ConstructionReceipt.Item> result=new ArrayList<>();
        var api=refundableProvider();if(api.state()==ru.neverland.core.ApiServices.State.NOT_INSTALLED)return result;
        try { for(var entry:map(api.invoke("wonderRequirements",new Class<?>[]{String.class},wonder)).entrySet()) {
            Object item=api.invoke("createArtifact",new Class<?>[]{String.class},entry.getKey());
            if(!(item instanceof org.bukkit.inventory.ItemStack stack))throw new IllegalStateException("Не удалось подготовить квитанцию музейных артефактов");
            result.add(new ru.neverland.townybuilds.construction.ConstructionReceipt.Item(stack,entry.getValue()));
        } } catch(ReflectiveOperationException ex) { throw new IllegalStateException("Не удалось проверить музейные артефакты",ex); }
        return result;
    }
    private ru.neverland.core.ApiServices.Connection refundableProvider() {
        String name=Bukkit.getPluginManager().getPlugin("NeverLandTownyArchaeology")!=null?"NeverLandTownyArchaeology":"TownyArchaeology";
        var api=ru.neverland.core.ApiServices.connect(name,API_CLASS,1,"wonderRequirements","createArtifact","consumeForWonderDurable");
        if(api.state()!=ru.neverland.core.ApiServices.State.NOT_INSTALLED&&!api.ready())
            throw new IllegalStateException("Для возврата артефактов нужен включённый NeverLandTownyArchaeology 0.1.21 или новее");
        return api;
    }
    public List<String> missingLines(UUID townId, String wonderId) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { List<String> result = new ArrayList<>(); missing(townId, wonderId).forEach((id, amount) -> result.add(name(id) + " x" + amount)); return result; } }
    public List<String> requirementLines(UUID townId, String wonderId) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { List<String> result = new ArrayList<>(); requirements(wonderId).forEach((id, amount) -> result.add(name(id) + " &7" + available(townId, id) + "/" + amount)); return result; } }
    private Object call(String name,Class<?>[] parameters,Object... arguments){var api=provider();if(!api.ready())return null;try{return api.invoke(name,parameters,arguments);}catch(ReflectiveOperationException|RuntimeException ex){return null;}}
    private ru.neverland.core.ApiServices.Connection provider(){String name=Bukkit.getPluginManager().getPlugin("NeverLandTownyArchaeology")!=null?"NeverLandTownyArchaeology":"TownyArchaeology";return ru.neverland.core.ApiServices.connect(name,API_CLASS,1,"wonderRequirements","missingForWonder","available","consumeForWonder","artifactName");}
    private Map<String, Integer> map(Object value) { if (!(value instanceof Map<?, ?> raw)) return Map.of(); Map<String, Integer> result = new LinkedHashMap<>(); raw.forEach((key, amount) -> { if (key != null && amount instanceof Number number) result.put(String.valueOf(key), number.intValue()); }); return Map.copyOf(result); }
}

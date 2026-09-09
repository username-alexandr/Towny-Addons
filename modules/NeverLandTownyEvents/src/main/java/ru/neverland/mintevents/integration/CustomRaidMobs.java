package ru.neverland.mintevents.integration;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.neverland.mintevents.service.RaidCatalog;
import java.lang.reflect.Method;
import java.util.*;

/** Optional public APIs; no dependency on server implementation or proprietary plugin binaries. */
public final class CustomRaidMobs {
    private final JavaPlugin plugin;
    public CustomRaidMobs(JavaPlugin plugin) { this.plugin = plugin; }
    private Class<?> api(String pluginName, String className) throws ReflectiveOperationException {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (dependency == null || !dependency.isEnabled()) throw new IllegalStateException("Не включён " + pluginName);
        return Class.forName(className, true, dependency.getClass().getClassLoader());
    }
    public void apply(LivingEntity entity, RaidCatalog.MobSpec spec, RaidCatalog.Wave wave, double protection) throws ReflectiveOperationException {
        // Resolve all equipment before attaching a model, so an invalid item cannot leave model parts behind.
        Map<String, ItemStack> equipment = new LinkedHashMap<>();
        for (var item : spec.equipment().entrySet()) equipment.put(item.getKey(), item(item.getValue()));
        if (spec.provider().equals("itemsadder")) {
            Class<?> type = api("ItemsAdder", "dev.lone.itemsadder.api.CustomEntity");
            Object custom = type.getMethod("convert", String.class, LivingEntity.class).invoke(null, spec.model(), entity);
            if (custom == null) throw new IllegalArgumentException("Нет существа ItemsAdder: " + spec.model());
        } else if (spec.provider().equals("modelengine")) {
            Class<?> type = api("ModelEngine", "com.ticxo.modelengine.api.ModelEngineAPI");
            Object model = type.getMethod("createActiveModel", String.class).invoke(null, spec.model());
            if (model == null) throw new IllegalArgumentException("Нет модели ModelEngine: " + spec.model());
            Method create = Arrays.stream(type.getMethods()).filter(m -> m.getName().equals("createModeledEntity")
                    && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(entity)).findFirst().orElseThrow();
            Object modeled = create.invoke(null, entity);
            Method add = Arrays.stream(modeled.getClass().getMethods()).filter(m -> m.getName().equals("addModel")
                    && m.getParameterCount() == 2 && m.getParameterTypes()[0].isInstance(model)
                    && m.getParameterTypes()[1] == boolean.class).findFirst().orElseThrow();
            add.invoke(modeled, model, true);
            modeled.getClass().getMethod("setBaseEntityVisible", boolean.class).invoke(modeled, false);
        }
        double mitigation = 1 - Math.max(0, Math.min(.8, protection)) * .4;
        attribute(entity, Attribute.MAX_HEALTH, spec.health() * wave.healthMultiplier() * mitigation);
        var health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) entity.setHealth(health.getValue());
        attribute(entity, Attribute.ATTACK_DAMAGE, spec.damage() * wave.damageMultiplier() * mitigation);
        var gear = entity.getEquipment();
        if (gear != null) {
            for (var item : equipment.entrySet()) switch (item.getKey()) {
                case "hand" -> gear.setItemInMainHand(item.getValue());
                case "offhand" -> gear.setItemInOffHand(item.getValue());
                case "helmet" -> gear.setHelmet(item.getValue());
                case "chestplate" -> gear.setChestplate(item.getValue());
                case "leggings" -> gear.setLeggings(item.getValue());
                case "boots" -> gear.setBoots(item.getValue());
                default -> throw new IllegalArgumentException("Неверный слот снаряжения");
            }
            gear.setItemInMainHandDropChance(0); gear.setItemInOffHandDropChance(0);
            gear.setHelmetDropChance(0); gear.setChestplateDropChance(0); gear.setLeggingsDropChance(0); gear.setBootsDropChance(0);
        }
        entity.setCanPickupItems(false);
    }
    private void attribute(LivingEntity entity, Attribute attribute, double value) {
        var instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(Math.max(1, Math.min(1024, value)));
    }
    private ItemStack item(String id) throws ReflectiveOperationException {
        if (id.startsWith("itemsadder:")) {
            Class<?> type = api("ItemsAdder", "dev.lone.itemsadder.api.CustomStack");
            Object stack = type.getMethod("getInstance", String.class).invoke(null, id.substring(11));
            if (stack == null) throw new IllegalArgumentException("Нет предмета ItemsAdder: " + id);
            return ((ItemStack) type.getMethod("getItemStack").invoke(stack)).clone();
        }
        Material material = ru.neverland.localization.MaterialNameConfig.matchMaterial(id);
        if (material == null || !material.isItem() || material.isAir()) throw new IllegalArgumentException("Нет предмета: " + id);
        return new ItemStack(material);
    }
}

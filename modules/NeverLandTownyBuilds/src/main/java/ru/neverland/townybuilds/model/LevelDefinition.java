package ru.neverland.townybuilds.model;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LevelDefinition {
    private final int level;
    private final double money;
    private final int bonusBlocks;
    private final List<ItemStack> resources;
    private final Map<PotionEffectType, Integer> effects;
    private final List<String> commands;

    public LevelDefinition(int level, double money, int bonusBlocks, List<ItemStack> resources,
                           Map<PotionEffectType, Integer> effects, List<String> commands) {
        this.level = level;
        this.money = money;
        this.bonusBlocks = bonusBlocks;
        this.resources = resources.stream().map(ru.neverland.townybuilds.service.ConstructionSupply::migrate).toList();
        this.effects = Collections.unmodifiableMap(new LinkedHashMap<>(effects));
        this.commands = List.copyOf(commands);
    }

    public int level() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return level;
    } }

    public double money() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return money;
    } }

    public int bonusBlocks() { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return bonusBlocks;
    } }

    public List<ItemStack> resources() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        return resources.stream().map(ItemStack::clone).toList();
    } }

    public Map<PotionEffectType, Integer> effects() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        return effects;
    } }

    public List<String> commands() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
        return commands;
    } }
}

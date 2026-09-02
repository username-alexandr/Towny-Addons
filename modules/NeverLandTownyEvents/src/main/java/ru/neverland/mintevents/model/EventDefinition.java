package ru.neverland.mintevents.model;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;

import java.util.List;
import java.util.Map;

public record EventDefinition(
        String id,
        String name,
        Material icon,
        String description,
        long durationSeconds,
        int baseGoal,
        int goalPerResident,
        double mitigationCap,
        EventMode mode,
        BarColor bossBarColor,
        Map<String, Double> buildingModifiers,
        Map<String, Double> ideologyModifiers,
        List<ContributionRule> contributions,
        List<String> successCommands,
        List<String> failureCommands
) {}

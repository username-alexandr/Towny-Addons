package ru.neverland.mintevents.model;

import org.bukkit.Material;

public record ContributionRule(String key, Material material, int points, String name) {}

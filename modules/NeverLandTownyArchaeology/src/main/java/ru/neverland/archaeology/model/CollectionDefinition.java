package ru.neverland.archaeology.model;

import org.bukkit.Material;

import java.util.List;

public record CollectionDefinition(String id, String name, Material icon, List<String> artifacts) { }

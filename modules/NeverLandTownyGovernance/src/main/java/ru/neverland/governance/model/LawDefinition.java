package ru.neverland.governance.model;

import org.bukkit.Material;
import java.util.List;

public record LawDefinition(
        String id,
        String name,
        LawCategory category,
        Material material,
        String itemsAdderIcon,
        String exclusiveGroup,
        List<String> description,
        LawEffects effects
) { }

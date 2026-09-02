package ru.neverland.governance.model;

import org.bukkit.Material;
import java.util.List;
import java.util.Set;

public record OfficeDefinition(
        String id,
        String name,
        Material material,
        String itemsAdderIcon,
        int maxHolders,
        boolean councilMember,
        Set<LawCategory> proposalCategories,
        boolean allCategories,
        List<String> description
) {
    public boolean mayPropose(LawCategory category) { return allCategories || proposalCategories.contains(category); }
}

package ru.neverland.townybuilds.model;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Set;

/** Крупные разделы каталога построек. */
public enum ProjectCategory {
    ADMINISTRATION("Управление", Material.BELL, "&#C8B7E8"),
    PRODUCTION("Производство", Material.ANVIL, "&#E89A4A"),
    TRADE("Торговля и финансы", Material.EMERALD, "&#FFD45B"),
    INFRASTRUCTURE("Инфраструктура", Material.RAIL, "&#58CFE8"),
    DEFENSE("Оборона", Material.SHIELD, "&#C0C7D1"),
    SCIENCE("Наука и образование", Material.ENCHANTING_TABLE, "&#8C8CFF"),
    CULTURE("Культура и общество", Material.PAINTING, "&#DFA7FF"),
    OTHER("Прочее", Material.CHEST, "&#AAB4C4");

    private static final Set<String> ADMINISTRATION_IDS = Set.of(
            "town_hall", "embassy", "courthouse", "prison", "archive", "census_bureau"
    );
    private static final Set<String> PRODUCTION_IDS = Set.of(
            "forge", "miners_guild", "agrarian_complex", "workshop", "hunting_lodge", "fishing_harbor",
            "sawmill", "quarry", "foundry", "mill", "bakery", "apiary", "alchemy", "forestry", "recycling_yard"
    );
    private static final Set<String> TRADE_IDS = Set.of(
            "market", "city_bank", "post_station", "warehouse", "caravanserai", "auction", "merchant_guild",
            "cargo_terminal", "customs", "trade_port", "mint", "insurance_chamber"
    );
    private static final Set<String> INFRASTRUCTURE_IDS = Set.of(
            "residential_quarter", "shipyard", "aqueduct", "water_tower", "sewer", "baths", "roads", "bridge_service", "reservoir",
            "pumping_station", "irrigation_station", "dam"
    );
    private static final Set<String> DEFENSE_IDS = Set.of(
            "army", "barracks", "fire_station", "watch_fortress", "guard", "arsenal", "armory", "watchtower",
            "fortress_gate", "counterintel", "stables", "fortress_wall", "city_moat", "archery_range", "port_fort"
    );
    private static final Set<String> SCIENCE_IDS = Set.of(
            "great_library", "museum", "university", "observatory", "cartography", "research", "botanical", "printing_house"
    );
    private static final Set<String> CULTURE_IDS = Set.of(
            "temple", "infirmary", "tavern_inn", "theater", "square", "arena", "gallery", "park",
            "guild_house", "memorial", "shelter", "cathedral"
    );

    private final String displayName;
    private final Material icon;
    private final String color;

    ProjectCategory(String displayName, Material icon, String color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public String color() { return color; }

    public static ProjectCategory parse(String raw, String projectId) {
        if (raw != null && !raw.isBlank()) {
            try { return valueOf(raw.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { }
        }
        if (ADMINISTRATION_IDS.contains(projectId)) return ADMINISTRATION;
        if (PRODUCTION_IDS.contains(projectId)) return PRODUCTION;
        if (TRADE_IDS.contains(projectId)) return TRADE;
        if (INFRASTRUCTURE_IDS.contains(projectId)) return INFRASTRUCTURE;
        if (DEFENSE_IDS.contains(projectId)) return DEFENSE;
        if (SCIENCE_IDS.contains(projectId)) return SCIENCE;
        if (CULTURE_IDS.contains(projectId)) return CULTURE;
        return OTHER;
    }
}

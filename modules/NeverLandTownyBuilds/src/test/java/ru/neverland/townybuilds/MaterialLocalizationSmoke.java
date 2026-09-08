package ru.neverland.townybuilds;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.ItemMeta;
import ru.neverland.localization.MaterialLabels;
import ru.neverland.localization.MaterialNameConfig;
import ru.neverland.townybuilds.data.TownData;

public final class MaterialLocalizationSmoke {
    public static void main(String[] args) throws Exception {
        MaterialLabels labels = new MaterialLabels();
        int materials = 0;
        for (Material material : Material.values()) {
            if (material.isLegacy()) continue;
            check(MaterialLabels.hasBuiltin(material.name()), "Missing material: " + material);
            check(labels.name(material.name()).matches(".*[А-Яа-яЁё].*"), "English label: " + material);
            check(MaterialNameConfig.matchMaterial(material.name()) == material, "Changed material: " + material);
            materials++;
        }
        check(labels.name("OAK_SAPLING").equals("Саженец дуба"), "Reported sapling label");
        check(labels.name("BLACK_WALL_BANNER").equals(labels.name("BLACK_BANNER")), "Wall banner alias");
        check(labels.name("WATER").equals("Вода"), "Non-item obstruction label");

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.loadFromString("OAK_SAPLING: Oak sapling\nIRON_CHAIN: Iron chain\nCHAIN: Цепь\nSTONE: Камень общины\n");
        MaterialNameConfig.apply(labels, legacy);
        check(labels.name("OAK_SAPLING").equals("Саженец дуба"), "Legacy English label hides Russian");
        check(labels.name("CHAIN").equals("Железная цепь"), "Legacy chain label");
        check(labels.name("STONE").equals("Камень общины"), "Custom Russian override lost");
        labels.override("OAK_SAPLING", "&7OAK_SAPLING");
        check(labels.name("OAK_SAPLING").equals("Саженец дуба"), "Colored English label hides Russian");
        labels.reset();
        check(labels.name("STONE").equals("Камень"), "Removed config override survives reload");
        check(labels.configuredName("OAK_LOG", "Oak log").equals("Дубовое бревно"), "Automatic event/export label");
        check(labels.configuredName("OAK_LOG", "Древесина города").equals("Древесина города"), "Custom export label lost");

        for (String key : new String[]{"CHAIN", "chain", "minecraft:chain", "IRON_CHAIN", "minecraft:iron_chain"}) {
            check(MaterialNameConfig.matchMaterial(key) == Material.IRON_CHAIN, "Chain lookup: " + key);
            check(labels.name(key).equals("Железная цепь"), "Chain translation: " + key);
        }
        check(MaterialNameConfig.matchMaterial("NOT_A_MATERIAL") == null, "Invalid material substituted");
        check(MaterialLabels.canonicalBlockData("minecraft:chain[axis=x,waterlogged=true]")
                .equals("minecraft:iron_chain[axis=x,waterlogged=true]"), "Chain state lost");
        check(MaterialLabels.canonicalBlockData("custom:chain[axis=x]").equals("custom:chain[axis=x]"), "Custom namespace changed");
        check(MaterialLabels.canonicalBlockData("minecraft:copper_chain[axis=z]")
                .equals("minecraft:copper_chain[axis=z]"), "Copper chain changed");

        TownData town = new TownData(UUID.randomUUID(), 9);
        town.loadShopPrices(Map.of("CHAIN", 3.0));
        check(town.shopPrice("IRON_CHAIN") == 3.0, "Old chain price unavailable on purchase");
        town.loadShopPrices(Map.of("CHAIN", 3.0, "IRON_CHAIN", 7.0));
        check(town.shopPrice("CHAIN") == 7.0 && town.shopPrices().size() == 1, "Duplicate chain price migration");

        ItemMeta custom = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class<?>[]{ItemMeta.class},
                (proxy, method, values) -> switch (method.getName()) {
                    case "hasCustomName" -> true;
                    case "customName" -> Component.text("Captain's Chain");
                    default -> throw new AssertionError("Unexpected metadata access: " + method.getName());
                });
        check(MaterialNameConfig.customName(custom).equals("Captain's Chain"), "User-assigned item name changed");
        System.out.println("MaterialLocalizationSmoke OK: " + materials + " materials; legacy configs, names and chain migration");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

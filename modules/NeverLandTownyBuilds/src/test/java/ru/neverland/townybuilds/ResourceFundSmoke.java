package ru.neverland.townybuilds;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.data.ResourceFund;

public final class ResourceFundSmoke {
    public static void main(String[] args) {
        ResourceFund fund = new ResourceFund(1);
        ItemStack sandstone = new TestItemStack("sandstone", Material.SANDSTONE, false);
        fund.add(sandstone, 64);
        fund.add(new TestItemStack("paper-normalized", Material.SANDSTONE, false), 128);
        if (fund.count(sandstone) != 192) {
            throw new AssertionError("Частичные взносы не суммированы: " + fund.count(sandstone));
        }
        if (fund.entries().size() != 1 || fund.entries().get(0).amount() != 192) {
            throw new AssertionError("Одинаковые предметы должны храниться одной записью");
        }
        if (fund.targetLevel() != 1) {
            throw new AssertionError("Потерян целевой уровень фонда");
        }
        ItemStack custom = new TestItemStack("itemsadder:tile", Material.SANDSTONE, true);
        fund.add(custom, 7);
        if (fund.count(custom) != 7 || fund.entries().size() != 2) {
            throw new AssertionError("Пользовательский ресурс смешан с ванильным фондом");
        }
        ItemStack legacy = new TestItemStack("legacy-tiles", Material.DEEPSLATE_TILES, false);
        ItemStack slab = new TestItemStack("slab", Material.COBBLED_DEEPSLATE_SLAB, false);
        fund.add(legacy, 32);
        fund.add(slab, 100);
        if (fund.count(slab) != 132) throw new AssertionError("Legacy contributions must migrate and merge");
        if (ru.neverland.townybuilds.service.ResourceMatcher.matches(legacy, slab))
            throw new AssertionError("New inventory contributions must require the slab");
        ItemStack special = new TestItemStack("custom-tiles", Material.DEEPSLATE_TILES, true);
        if (ru.neverland.townybuilds.service.ConstructionSupply.migrate(special).getType() != Material.DEEPSLATE_TILES)
            throw new AssertionError("Custom resources must remain unchanged");
        if (ru.neverland.townybuilds.service.ConstructionSupply.material(Material.DEEPSLATE_TILES) != Material.COBBLED_DEEPSLATE_SLAB)
            throw new AssertionError("Construction must request the same slab as the menu");
        System.out.println("ResourceFundSmoke OK");
    }

    private static final class TestItemStack extends ItemStack {
        private final String id;
        private Material material;
        private final boolean custom;

        private TestItemStack(String id, Material material, boolean custom) {
            super();
            this.id = id;
            this.material = material;
            this.custom = custom;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof TestItemStack test && id.equals(test.id);
        }

        @Override
        public Material getType() {
            return material;
        }

        @Override
        public void setType(Material material) { this.material = material; }

        @Override
        public boolean hasItemMeta() {
            return custom;
        }

        @Override
        public TestItemStack clone() {
            return new TestItemStack(id, material, custom);
        }

        @Override
        public void setAmount(int amount) {
            // Количество фонда хранится отдельно от ItemStack.
        }
    }
}

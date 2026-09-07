package ru.neverland.townybuilds;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.service.ResourceTransfer;

public final class ResourceTransferSmoke {
    public static void main(String[] args) {
        ItemStack required = new TestItemStack("sandstone", Material.SANDSTONE, 1, false);
        ItemStack[] contents = {
                new TestItemStack("sandstone", Material.SANDSTONE, 64, false),
                new TestItemStack("unrelated", Material.DIRT, 32, false),
                // Имитирует тот же ванильный материал со служебными компонентами Paper:
                // isSimilar=false, но тип ресурса должен быть распознан.
                new TestItemStack("paper-normalized", Material.SANDSTONE, 40, false)
        };
        int notRemoved = ResourceTransfer.remove(contents, required, 80);
        if (notRemoved != 0 || ResourceTransfer.count(contents, required) != 24) {
            throw new AssertionError("Фактическое списание 80 предметов выполнено неверно");
        }
        if (contents[0] != null || contents[1] == null || contents[2].getAmount() != 24) {
            throw new AssertionError("Изменено неверное содержимое инвентаря");
        }

        ItemStack customRequired = new TestItemStack("itemsadder:tile", Material.SANDSTONE, 1, true);
        ItemStack vanilla = new TestItemStack("sandstone", Material.SANDSTONE, 16, false);
        if (ResourceTransfer.count(new ItemStack[]{vanilla}, customRequired) != 0) {
            throw new AssertionError("Пользовательский ресурс ошибочно сопоставлен только по материалу");
        }
        System.out.println("ResourceTransferSmoke OK");
    }

    private static final class TestItemStack extends ItemStack {
        private final String id;
        private final Material material;
        private int amount;
        private final boolean custom;

        private TestItemStack(String id, Material material, int amount, boolean custom) {
            super();
            this.id = id;
            this.material = material;
            this.amount = amount;
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
        public boolean hasItemMeta() {
            return custom;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }

        @Override
        public TestItemStack clone() {
            return new TestItemStack(id, material, amount, custom);
        }
    }
}

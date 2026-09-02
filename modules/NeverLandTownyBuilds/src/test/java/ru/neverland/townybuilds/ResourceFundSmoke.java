package ru.neverland.townybuilds;

import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.data.ResourceFund;

public final class ResourceFundSmoke {
    public static void main(String[] args) {
        ResourceFund fund = new ResourceFund(1);
        ItemStack sandstone = new TestItemStack("sandstone");
        fund.add(sandstone, 64);
        fund.add(sandstone, 128);
        if (fund.count(sandstone) != 192) {
            throw new AssertionError("Частичные взносы не суммированы: " + fund.count(sandstone));
        }
        if (fund.entries().size() != 1 || fund.entries().get(0).amount() != 192) {
            throw new AssertionError("Одинаковые предметы должны храниться одной записью");
        }
        if (fund.targetLevel() != 1) {
            throw new AssertionError("Потерян целевой уровень фонда");
        }
        System.out.println("ResourceFundSmoke OK");
    }

    private static final class TestItemStack extends ItemStack {
        private final String id;

        private TestItemStack(String id) {
            super();
            this.id = id;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof TestItemStack test && id.equals(test.id);
        }

        @Override
        public TestItemStack clone() {
            return new TestItemStack(id);
        }

        @Override
        public void setAmount(int amount) {
            // Количество фонда хранится отдельно от ItemStack.
        }
    }
}

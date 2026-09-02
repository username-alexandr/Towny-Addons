package ru.neverland.townybuilds;

import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.service.ResourceTransfer;

public final class ResourceTransferSmoke {
    public static void main(String[] args) {
        ItemStack required = new TestItemStack("sandstone", 1);
        ItemStack[] contents = {
                new TestItemStack("sandstone", 64),
                new TestItemStack("unrelated", 32),
                new TestItemStack("sandstone", 40)
        };
        int notRemoved = ResourceTransfer.remove(contents, required, 80);
        if (notRemoved != 0 || ResourceTransfer.count(contents, required) != 24) {
            throw new AssertionError("Фактическое списание 80 предметов выполнено неверно");
        }
        if (contents[0] != null || contents[1] == null || contents[2].getAmount() != 24) {
            throw new AssertionError("Изменено неверное содержимое инвентаря");
        }
        System.out.println("ResourceTransferSmoke OK");
    }

    private static final class TestItemStack extends ItemStack {
        private final String id;
        private int amount;

        private TestItemStack(String id, int amount) {
            super();
            this.id = id;
            this.amount = amount;
        }

        @Override
        public boolean isSimilar(ItemStack other) {
            return other instanceof TestItemStack test && id.equals(test.id);
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
            return new TestItemStack(id, amount);
        }
    }
}

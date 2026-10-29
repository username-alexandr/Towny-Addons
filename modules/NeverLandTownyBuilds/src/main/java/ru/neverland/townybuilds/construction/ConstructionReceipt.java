package ru.neverland.townybuilds.construction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.util.ItemCodec;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Cost at purchase, not today's price. Refund intent and warehouse changes share town-data.yml. */
public record ConstructionReceipt(UUID id, int level, long paidCents, List<Item> paidItems,
                                  Map<BlockOffset, Change> changes, String state, long refundCents,
                                  List<Item> remaining, int feePercent, String actor, boolean legacy, int baseItems) {
    public static final Set<String> STATES = Set.of("TREASURY_PENDING", "DEBIT_PENDING", "DEBIT_CONFIRMED", "ARTIFACT_PENDING", "ACTIVE", "COMPLETE", "REMOVING", "REFUND_READY", "REFUND_PAYING", "REFUND_PAID", "DONE");
    public record Item(ItemStack sample, int amount) {
        public Item {
            if (sample == null || Set.of(org.bukkit.Material.AIR,org.bukkit.Material.CAVE_AIR,org.bukkit.Material.VOID_AIR).contains(sample.getType()) || amount < 1) throw new IllegalArgumentException("Некорректный ресурс квитанции");
            sample = sample.clone(); sample.setAmount(1);
        }
        @Override public ItemStack sample() { return sample.clone(); }
    }
    public record Change(String before, String expected) {
        public Change {
            if (before == null || before.isBlank() || expected == null || expected.isBlank()) throw new IllegalArgumentException("Пустой снимок блока");
        }
    }
    public ConstructionReceipt {
        if (id == null || level < 1 || level > 5 || paidCents < 0 || refundCents < 0 || refundCents > paidCents
                || !STATES.contains(state) || feePercent < 0 || feePercent > 100 || actor == null || baseItems < 0 || baseItems > paidItems.size())
            throw new IllegalArgumentException("Повреждена квитанция строительства");
        paidItems = List.copyOf(paidItems); changes = Map.copyOf(changes); remaining = List.copyOf(remaining);
    }
    public boolean cancelling() { return Set.of("REMOVING","REFUND_READY","REFUND_PAYING","REFUND_PAID").contains(state); }
    public boolean blocksConstruction() { return !state.equals("ACTIVE"); }
    public ConstructionReceipt cancel(int fee, String who) {
        if (!state.equals("ACTIVE")) throw new IllegalStateException("Отмена уже оформлена");
        List<Item> returned = new ArrayList<>();
        for (Item item : paidItems) {
            int amount = Math.toIntExact(refund(item.amount(), fee));
            if (amount > 0) returned.add(new Item(item.sample(), amount));
        }
        returned.addAll(remaining); // Unused surplus belongs to the town in full, without commission.
        return new ConstructionReceipt(id, level, paidCents, paidItems, changes, "REMOVING",
                refund(paidCents, fee), returned, fee, who, legacy, baseItems);
    }
    public ConstructionReceipt state(String value) {
        return new ConstructionReceipt(id, level, paidCents, paidItems, changes, value, refundCents, remaining, feePercent, actor, legacy, baseItems);
    }
    public ConstructionReceipt remaining(List<Item> value) {
        return new ConstructionReceipt(id, level, paidCents, paidItems, changes, state, refundCents, value, feePercent, actor, legacy, baseItems);
    }
    public ConstructionReceipt paid(long cents, List<Item> items) {
        return new ConstructionReceipt(id, level, cents, items, changes, state, 0, remaining, feePercent, actor, legacy, Math.min(baseItems,items.size()));
    }
    public static long cents(double money) {
        if (!Double.isFinite(money) || money < 0) throw new IllegalArgumentException("Некорректная стоимость");
        return BigDecimal.valueOf(money).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
    /** Round the refund down: fractional blocks/cents cannot create extra resources. */
    public static long refund(long amount, int fee) {
        if (amount < 0 || fee < 0 || fee > 100) throw new IllegalArgumentException("Некорректная комиссия");
        return BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100 - fee)).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).longValueExact();
    }
    public void save(ConfigurationSection section) throws IOException {
        section.set("id", id.toString()); section.set("level", level); section.set("paid-cents", paidCents);
        section.set("state", state); section.set("refund-cents", refundCents); section.set("fee-percent", feePercent);
        section.set("actor", actor); section.set("legacy", legacy); section.set("base-items", baseItems);
        saveItems(section.createSection("paid-items"), paidItems);
        saveItems(section.createSection("remaining"), remaining);
        var blocks = section.createSection("changes");
        int index = 0;
        for (var entry : changes.entrySet()) {
            var block = blocks.createSection(Integer.toString(index++));
            block.set("x", entry.getKey().x()); block.set("y", entry.getKey().y()); block.set("z", entry.getKey().z());
            block.set("before", entry.getValue().before()); block.set("expected", entry.getValue().expected());
        }
    }
    private static void saveItems(ConfigurationSection section, List<Item> items) throws IOException {
        for (int i = 0; i < items.size(); i++) {
            section.set(i + ".item", ItemCodec.encodeSingle(items.get(i).sample()));
            section.set(i + ".amount", items.get(i).amount());
        }
    }
    public static ConstructionReceipt load(ConfigurationSection section) throws IOException, ClassNotFoundException {
        var blocks = required(section, "changes"); Map<BlockOffset, Change> changes = new HashMap<>();
        for (String key : blocks.getKeys(false)) {
            var block = required(blocks, key);
            var offset = new BlockOffset(integer(block,"x"), integer(block,"y"), integer(block,"z"));
            if (changes.put(offset, new Change(block.getString("before"), block.getString("expected"))) != null)
                throw new IOException("Повтор блока в квитанции");
        }
        return new ConstructionReceipt(UUID.fromString(text(section,"id")), integer(section,"level"), number(section,"paid-cents"),
                loadItems(required(section,"paid-items")), changes, text(section,"state"), number(section,"refund-cents"),
                loadItems(required(section,"remaining")), integer(section,"fee-percent"), text(section,"actor"), section.getBoolean("legacy"), integer(section,"base-items"));
    }
    private static List<Item> loadItems(ConfigurationSection section) throws IOException, ClassNotFoundException {
        List<Item> result = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            var item = required(section,key);
            result.add(new Item(ItemCodec.decodeSingle(text(item,"item")), integer(item,"amount")));
        }
        return result;
    }
    private static ConfigurationSection required(ConfigurationSection section, String key) throws IOException {
        var value = section.getConfigurationSection(key); if (value == null) throw new IOException("Нет раздела квитанции: " + key); return value;
    }
    private static String text(ConfigurationSection section, String key) throws IOException {
        if (!section.isString(key)) throw new IOException("Нет строки квитанции: " + key); return section.getString(key);
    }
    private static long number(ConfigurationSection section, String key) throws IOException {
        Object value = section.get(key); if (!(value instanceof Integer || value instanceof Long)) throw new IOException("Нет числа квитанции: " + key); return ((Number)value).longValue();
    }
    private static int integer(ConfigurationSection section, String key) throws IOException { return Math.toIntExact(number(section,key)); }
}

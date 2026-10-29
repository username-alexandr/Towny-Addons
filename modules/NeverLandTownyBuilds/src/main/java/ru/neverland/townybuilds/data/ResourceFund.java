package ru.neverland.townybuilds.data;

import org.bukkit.inventory.ItemStack;
import ru.neverland.townybuilds.service.ResourceMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Накопительный фонд одного следующего уровня городского проекта.
 * Предмет хранится отдельно от количества, чтобы взносы не зависели от
 * максимального размера ванильного стака.
 */
public final class ResourceFund {
    private Runnable mutation=()->{};
    public void mutationGuard(Runnable value){mutation=java.util.Objects.requireNonNull(value);}

    private final int targetLevel;
    private final List<Entry> entries = new ArrayList<>();

    public ResourceFund(int targetLevel) {
        this.targetLevel = targetLevel;
    }

    public int targetLevel() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return targetLevel;
    } }

    public int count(ItemStack template) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        if (template == null) return 0;
        template = ru.neverland.townybuilds.service.ConstructionSupply.migrate(template);
        for (Entry entry : entries) {
            if (ResourceMatcher.matches(entry.template, template)) {
                return entry.amount;
            }
        }
        return 0;
    } }

    public void add(ItemStack template, int amount) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {mutation.run();
        if (template == null || amount <= 0) return;
        template = ru.neverland.townybuilds.service.ConstructionSupply.migrate(template);
        for (Entry entry : entries) {
            if (ResourceMatcher.matches(entry.template, template)) {
                entry.amount = Math.addExact(entry.amount, amount);
                return;
            }
        }
        ItemStack stored = template.clone();
        stored.setAmount(1);
        entries.add(new Entry(stored, amount));
    } }

    public List<Snapshot> entries() { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
        List<Snapshot> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            result.add(new Snapshot(entry.template.clone(), entry.amount));
        }
        return List.copyOf(result);
    } }

    public List<Snapshot> surplus(List<ItemStack> requirements) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {
        List<Snapshot> result = new ArrayList<>();
        for (Entry entry : entries) {
            int needed = requirements.stream().filter(r -> ResourceMatcher.matches(entry.template, r))
                    .mapToInt(ItemStack::getAmount).sum();
            if (entry.amount > needed) result.add(new Snapshot(entry.template, entry.amount - needed));
        }
        return List.copyOf(result);
    } }

    private static final class Entry {
        private final ItemStack template;
        private int amount;

        private Entry(ItemStack template, int amount) {
            this.template = template;
            this.amount = amount;
        }
    }

    public record Snapshot(ItemStack template, int amount) {
        public Snapshot {
            template = template.clone();
        }

        @Override
        public ItemStack template() { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {
            return template.clone();
        } }
    }
}

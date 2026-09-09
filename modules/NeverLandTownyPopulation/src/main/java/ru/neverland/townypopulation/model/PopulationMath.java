package ru.neverland.townypopulation.model;

import java.util.ArrayList;
import java.util.List;

/** Deterministic simulation independent of Bukkit, the clock and persistence. */
public final class PopulationMath {
    private PopulationMath() {}
    public record Rules(int maximum, double growth, double decline, double workforce,
                        double foodDemand, double waterDemand, double criticalCoverage,
                        double growthHappiness, double declineHappiness,
                        double baseHappiness, double unemploymentPenalty,
                        double housingPenalty, double foodPenalty, double waterPenalty) {}
    public record Metrics(int workforce, int employed, int unemployed, double unemployment,
                          double foodCoverage, double waterCoverage, double happiness,
                          double change, List<String> reasons) {
        public Metrics { reasons = List.copyOf(reasons); }
    }
    public static Metrics evaluate(int population, Capacity c, Rules r) {
        int workers = (int)Math.ceil(population * r.workforce());
        int employed = Math.min(workers, c.jobs());
        double unemployment = workers == 0 ? 0 : (workers - employed) / (double)workers;
        double food = coverage(c.food(), population * r.foodDemand());
        double water = coverage(c.water(), population * r.waterDemand());
        double housing = coverage(c.housing(), population);
        double happiness = Math.max(0, Math.min(100, r.baseHappiness() + c.happiness()
                - unemployment * r.unemploymentPenalty() - (1-housing)*r.housingPenalty()
                - (1-food)*r.foodPenalty() - (1-water)*r.waterPenalty()));
        List<String> reasons = new ArrayList<>();
        if (food < 1) reasons.add("Недостаточно еды");
        else if (c.food() < (population+1)*r.foodDemand()) reasons.add("Нет запаса еды для новых жителей");
        if (water < 1) reasons.add("Недостаточно воды");
        else if (c.water() < (population+1)*r.waterDemand()) reasons.add("Нет запаса воды для новых жителей");
        if (housing < 1) reasons.add("Не хватает жилья");
        else if (population >= c.housing()) reasons.add("Все места жилья заняты");
        if (unemployment > 0) reasons.add("Есть безработные жители");
        if (happiness < r.growthHappiness()) reasons.add("Низкое довольство");
        double severity = Math.max(1-housing,
                Math.max(food < r.criticalCoverage() ? 1-food : 0,
                         water < r.criticalCoverage() ? 1-water : 0));
        if (happiness < r.declineHappiness())
            severity = Math.max(severity, (r.declineHappiness()-happiness)/Math.max(1,r.declineHappiness()));
        double change = 0;
        if (population > 0 && severity > 0 && r.decline() > 0) {
            change = -Math.min(population, Math.max(1, population*r.decline()*severity));
        } else if (severity == 0 && r.growth() > 0 && food >= 1 && water >= 1 && happiness >= r.growthHappiness()) {
            double sustainable = Math.min(c.housing(), Math.min(c.food()/r.foodDemand(), c.water()/r.waterDemand()));
            int headroom = (int)Math.max(0, Math.floor(Math.min(r.maximum(), sustainable)) - population);
            change = Math.min(headroom, Math.max(1, population*r.growth()) * happiness/100);
        }
        if (population >= r.maximum()) reasons.add("Достигнут предел населения в настройках");
        if (reasons.isEmpty()) reasons.add(change > 0 ? "Условия для роста выполнены" : "Население стабильно");
        return new Metrics(workers, employed, workers-employed, unemployment, food, water, happiness, change, reasons);
    }
    private static double coverage(double supply, double demand) { return demand <= 0 ? 1 : Math.min(1, supply/demand); }
    public static PopulationState advance(PopulationState state, Capacity capacity, Rules rules, long now) {
        double change = evaluate(state.population(), capacity, rules).change();
        double remainder = state.remainder();
        if (change == 0 || Math.signum(change) != Math.signum(remainder)) remainder = 0;
        double amount = change + remainder;
        int delta = (int)amount; // towards zero: preserve fractional growth/decline across cycles
        int next = Math.max(0, state.population() + delta);
        return new PopulationState(next, next == 0 && change <= 0 ? 0 : amount-delta, next-state.population(), now);
    }
}

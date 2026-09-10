package ru.neverland.townypopulation.model;

/** Food/water are supported demand units per cycle, not inventory items. */
public record Capacity(int housing, int jobs, double food, double water, double happiness) {
    public Capacity {
        if (housing < 0 || jobs < 0 || !Double.isFinite(food) || food < 0
                || !Double.isFinite(water) || water < 0 || !Double.isFinite(happiness))
            throw new IllegalArgumentException("Некорректная мощность здания");
    }
    public Capacity plus(Capacity other) {
        return new Capacity((int)Math.min(10000000L, (long)housing + other.housing),
                (int)Math.min(10000000L, (long)jobs + other.jobs),
                Math.min(1e9, food + other.food), Math.min(1e9, water + other.water),
                Math.max(-1e9, Math.min(1e9, happiness + other.happiness)));
    }
    public Capacity scaled(double raw) {
        double multiplier=Double.isFinite(raw)?Math.max(1,Math.min(3,raw)):1;
        return new Capacity((int)Math.min(10000000, Math.floor(housing*multiplier)),
                (int)Math.min(10000000,Math.floor(jobs*multiplier)), Math.min(1e9,food*multiplier),
                Math.min(1e9,water*multiplier), happiness>0?happiness*multiplier:happiness);
    }
    public Capacity times(int count) {
        return new Capacity((int)Math.min(10000000L, (long)housing * count),
                (int)Math.min(10000000L, (long)jobs * count),
                Math.min(1e9, food * count), Math.min(1e9, water * count), happiness * count);
    }
}

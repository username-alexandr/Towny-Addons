package ru.neverland.townypopulation.model;
/** Resource coverage constrains actual supply on shortage; full coverage preserves building capacity. */
public record StrategicSupply(double food,double water,boolean paused) {
    public StrategicSupply {
        if(!Double.isFinite(food)||!Double.isFinite(water)||food<0||food>1||water<0||water>1)throw new IllegalArgumentException("Некорректное стратегическое снабжение");
    }
    public Capacity limit(Capacity capacity,int population,PopulationMath.Rules rules) {
        return new Capacity(capacity.housing(),capacity.jobs(),
            food>=1?capacity.food():Math.min(capacity.food(),population*rules.foodDemand()*food),
            water>=1?capacity.water():Math.min(capacity.water(),population*rules.waterDemand()*water),capacity.happiness());
    }
}

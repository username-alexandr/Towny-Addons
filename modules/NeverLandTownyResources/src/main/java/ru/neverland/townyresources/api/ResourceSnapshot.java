package ru.neverland.townyresources.api;
import ru.neverland.townyresources.model.*;
import java.util.*;
/** All resource amounts are thousandths of one strategic unit; immutable and safe for asynchronous readers. */
public record ResourceSnapshot(UUID townId,String townName,int population,boolean populationLinked,boolean paused,String status,long nextCycle,
                               TownState state,Map<Resource,Long> capacity,Map<Resource,Long> forecastIncome,Map<Resource,Long> forecastExpense,
                               Map<Resource,Long> populationDemand,Map<String,ResourceEngine.Activity> buildings,double foodCoverage,double waterCoverage) {
    public ResourceSnapshot { capacity=Amounts.copy(capacity);forecastIncome=Amounts.flows(forecastIncome);forecastExpense=Amounts.flows(forecastExpense);populationDemand=Amounts.copy(populationDemand);buildings=Map.copyOf(buildings); }
}

package ru.neverland.integration;
/** Pure bounds shared by the integrations and their regression checks. */
public final class ResearchEffects {
    private ResearchEffects(){}
    public static double bounded(double bonus){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return Double.isFinite(bonus)?Math.max(0,Math.min(0.5,bonus)):0;} }
    public static String productionTechnology(String project){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return switch(project){case "agrarian_complex","irrigation_station"->"irrigation";case "foundry","forge"->"metallurgy";case "alchemy"->"alchemy";default->"";};} }
    public static double production(double district,double researchBonus){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        double base=Double.isFinite(district)?Math.max(1,Math.min(3,district)):1;
        return java.math.BigDecimal.valueOf(base).multiply(java.math.BigDecimal.ONE.add(java.math.BigDecimal.valueOf(bounded(researchBonus)))).min(java.math.BigDecimal.valueOf(3)).doubleValue();
    } }
    public static double speed(double base,double bonus){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return base*(1+bounded(bonus));} }
    public static double minutes(double base,double minimum,double maximum,double bonus){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {return Math.max(minimum,Math.min(maximum,base*(1-bounded(bonus))));} }
    public static double delay(double chance,double minimum,double bonus){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return Math.max(minimum,Math.min(1,chance-bounded(bonus)));} }
    public static double hostileDamage(double damage,double bonus,boolean residentAtHome,boolean activeWall,boolean hostile){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {return damage*(1-(residentAtHome&&activeWall&&hostile?bounded(bonus):0));} }
}

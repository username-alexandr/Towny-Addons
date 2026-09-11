package ru.neverland.integration;
/** Shared finite bounds, independent of optional plugin classes. */
public final class JobsEffects {
    private JobsEffects(){}
    public static double bonus(double value){return Double.isFinite(value)?Math.max(0,Math.min(0.5,value)):0;}
    public static double multiplier(double current,double bonus){double base=Double.isFinite(current)?Math.max(1,Math.min(3,current)):1;return Math.min(3,base*(1+bonus(bonus)));}
    public static double research(double specialization,double jobs){return Math.min(0.5,bonus(specialization)+bonus(jobs));}
}

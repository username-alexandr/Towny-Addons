package ru.neverland.mintevents.service;

import ru.neverland.mintevents.model.EventMode;

/** Pure weights and loss math; all inputs validated before side effects. */
public final class WeatherEconomy {
    private WeatherEconomy() { }
    public static int choose(double[] weights,double sample) {
        if(!Double.isFinite(sample)||sample<0||sample>=1)throw new IllegalArgumentException("Неверная случайная доля");
        double total=0;int last=-1;
        for(int i=0;i<weights.length;i++) {double w=weights[i];if(!Double.isFinite(w)||w<0||w>10)throw new IllegalArgumentException("Неверный вес события");total+=w;if(w>0)last=i;}
        if(total==0)return -1;double target=sample*total;
        for(int i=0;i<weights.length;i++){target-=weights[i];if(target<0)return i;}
        return last;
    }
    public static double production(EventMode mode,double protection,double droughtLoss,double floodLoss) {
        for(double loss:new double[]{droughtLoss,floodLoss})if(!Double.isFinite(loss)||loss<0||loss>.9)throw new IllegalArgumentException("Потеря выпуска: 0..0.9");
        if(!Double.isFinite(protection)||protection<0||protection>1)throw new IllegalArgumentException("Неверная защита");
        double loss=switch(mode){case DROUGHT->droughtLoss;case FLOOD->floodLoss;default->0;};
        return Math.max(.1,1-loss*(1-protection));
    }
}

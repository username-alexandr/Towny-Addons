package ru.neverland.mintevents;

import ru.neverland.mintevents.model.EventMode;
import ru.neverland.mintevents.service.WeatherEconomy;

public final class WeatherEconomySmoke {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        check(WeatherEconomy.choose(new double[]{0,0},.5)==-1,"all zero suppresses selection");
        check(WeatherEconomy.choose(new double[]{0,3,1},0)==1,"zero weight never chosen");
        check(WeatherEconomy.choose(new double[]{0,3,1},.75)==2,"weighted boundary");
        check(WeatherEconomy.choose(new double[]{1,0},.9999999999)==0,"zero tail never chosen");
        int[] count=new int[3];for(int i=0;i<40000;i++)count[WeatherEconomy.choose(new double[]{3,.5,.5},i/40000d)]++;
        check(count[0]==30000&&count[1]==5000&&count[2]==5000,"deterministic seasonal distribution");
        check(WeatherEconomy.production(EventMode.FLOOD,0,.5,.4)==.6,"unprotected flood loss");
        check(Math.abs(WeatherEconomy.production(EventMode.FLOOD,.8,.5,.4)-.92)<1e-12,"canal/irrigation protection reduces loss");
        check(WeatherEconomy.production(EventMode.DROUGHT,0,.5,.4)==.5,"drought loss");
        check(WeatherEconomy.production(EventMode.FIRE,0,.5,.4)==1,"other modes untouched");
        try{WeatherEconomy.choose(new double[]{Double.NaN},.5);throw new AssertionError("NaN");}catch(IllegalArgumentException expected){}
        try{WeatherEconomy.production(EventMode.FLOOD,0,.5,1);throw new AssertionError("invalid loss");}catch(IllegalArgumentException expected){}
        System.out.println("WeatherEconomySmoke PASS: relative weights, exclusions, distribution, damage and mitigation");
    }
}

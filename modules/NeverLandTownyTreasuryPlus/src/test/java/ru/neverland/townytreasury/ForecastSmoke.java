package ru.neverland.townytreasury;
import java.util.*;
import ru.neverland.townytreasury.model.*;
public final class ForecastSmoke {
    static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    interface Work{void run();}static void reject(Work work,String label){try{work.run();}catch(Exception expected){checks++;return;}throw new AssertionError(label);}
    public static void main(String[] args){
        var shares=Map.of(Budget.CONSTRUCTION,40,Budget.ARMY,20,Budget.INFRASTRUCTURE,25,Budget.SOCIAL,15);var city=CityLedger.initial(100000,shares,0);var active=LedgerEngine.mode(city,true,"mayor");
        var rows=List.of(new TreasuryForecast.Item("build/farm","Farm",Budget.CONSTRUCTION,50000,true),new TreasuryForecast.Item("upkeep/farm","Farm",Budget.INFRASTRUCTURE,10000,true));
        var result=TreasuryForecast.calculate(active,86400,rows,List.of());check(result.get("plannedCents").equals(60000L)&&result.get("afterCents").equals(40000L),"same building purchase and maintenance are different obligations");check(((Map<?,?>)result.get("categoryShortages")).get("construction").equals(10000L),"wealthy treasury still exposes shortage in restricted construction category");check(result.get("shortageCents").equals(0L)&&Boolean.TRUE.equals(result.get("complete")),"fully known total remains affordable before category transfer");
        var free=TreasuryForecast.calculate(city,86400,rows,List.of());check(((Map<?,?>)free.get("categoryShortages")).get("construction").equals(0L),"disabled budget does not invent category restriction");
        var unknown=List.of(new TreasuryForecast.Item("build/farm","Farm",Budget.CONSTRUCTION,0,false));check(Boolean.FALSE.equals(TreasuryForecast.calculate(city,86400,unknown,List.of()).get("complete")),"unknown external payment makes forecast explicitly incomplete");check(Boolean.FALSE.equals(TreasuryForecast.calculate(city,86400,List.of(),List.of("Upkeep unavailable")).get("complete")),"missing provider is not reported as zero known costs");
        var pending=LedgerEngine.prepare(active,new CityLedger.Pending(UUID.randomUUID(),1,1000,Budget.CONSTRUCTION,"construction","pending"));check(Boolean.FALSE.equals(TreasuryForecast.calculate(pending,86400,List.of(),List.of()).get("complete")),"pending treasury debit blocks reliable remainder");
        var poor=CityLedger.initial(100,shares,0);var deficit=TreasuryForecast.calculate(poor,86400,rows,List.of());check(deficit.get("shortageCents").equals(59900L)&&deficit.get("afterCents").equals(-59900L),"forecast exposes exact total deficit");
        reject(()->TreasuryForecast.calculate(city,86400,List.of(rows.get(0),rows.get(0)),List.of()),"duplicate obligation cannot be counted twice");reject(()->TreasuryForecast.calculate(city,604801,rows,List.of()),"unbounded horizon rejected");
        check(city.balance()==100000&&city.weeks().isEmpty()&&city.pending()==null,"forecast does not debit, reserve or manufacture income");
        System.out.println("ForecastSmoke PASS "+checks+" checks");
    }
}

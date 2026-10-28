package ru.neverland.townytaxes;
import ru.neverland.townytaxes.model.TaxCalculation;
public final class TaxCalculationSmoke {
    static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}static void reject(Runnable task,String label){try{task.run();}catch(IllegalArgumentException expected){checks++;return;}throw new AssertionError(label);}
    public static void main(String[] args){
        check(TaxCalculation.calculate(1.005,1,1,1,0).cents()==101,"round half cent once at final amount");check(TaxCalculation.calculate(100,1.2,1.5,2,.25).cents()==27000,"all visible modifiers produce actual final amount");check(TaxCalculation.calculate(100,1,20,1,0).municipal()==1.5,"existing municipal cap preserved");check(TaxCalculation.calculate(100,1,Double.NaN,1,0).cents()==10000,"missing optional municipal multiplier remains neutral");check(TaxCalculation.calculate(100,0,1,1,0).cents()==0,"full citizen exemption preserved");check(TaxCalculation.calculate(100,1,1,1,1).cents()==0,"full agreement exemption preserved");
        reject(()->TaxCalculation.calculate(100,Double.NaN,1,1,0),"unavailable required citizenship cannot charge");reject(()->TaxCalculation.calculate(100,1,1,Double.POSITIVE_INFINITY,0),"nonfinite sanction rejected");reject(()->TaxCalculation.calculate(-1,1,1,1,0),"negative base rejected");reject(()->new TaxCalculation(100,1,1,1,0,9999),"persisted amount must match formula");reject(()->TaxCalculation.calculate(1e20,1,1,1,0),"overflow cannot become a charge");
        var quote=TaxCalculation.calculate(12.345,1,1,1,0);check(quote.money()==12.35&&quote.describe().contains("12.35")&&quote.values().get("cents").equals(1235L),"preview, receipt and bank use identical rounded amount");System.out.println("TaxCalculationSmoke PASS "+checks+" checks");
    }
}

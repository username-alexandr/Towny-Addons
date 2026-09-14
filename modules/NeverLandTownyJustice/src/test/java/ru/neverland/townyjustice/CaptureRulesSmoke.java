package ru.neverland.townyjustice;
import java.util.*;
public final class CaptureRulesSmoke {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){UUID a=UUID.randomUUID(),b=UUID.randomUUID(),town=UUID.randomUUID();check(CaptureRules.allowed(a,b,town,town,town,true,true,false,true,true,4,4));
        check(!CaptureRules.allowed(a,a,town,town,town,true,true,false,true,true,0,4));check(!CaptureRules.allowed(a,b,town,b,town,true,true,false,true,true,2,4));check(!CaptureRules.allowed(a,b,town,town,b,true,true,false,true,true,2,4));
        for(int mask=0;mask<32;mask++)check(CaptureRules.allowed(a,b,town,town,town,(mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,(mask&16)!=0,2,4)==(mask==27));
        for(double n:new double[]{-1,4.01,Double.NaN,Double.POSITIVE_INFINITY})check(!CaptureRules.allowed(a,b,town,town,town,true,true,false,true,true,n,4));
        for(int mask=0;mask<16;mask++)check(CaptureRules.confirmed(a,(mask&1)!=0?a:b,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0)==(mask==11));
        check(JusticeSettings.cents("12,34")==1234);for(String n:List.of("NaN","-1","1.001","1000000001")){boolean fail=false;try{JusticeSettings.cents(n);}catch(IllegalArgumentException e){fail=true;}check(fail);}
        System.out.println("Capture rules: custody/queue/physical arrival, self/foreign/offline gates and exact money PASS");
    }
}

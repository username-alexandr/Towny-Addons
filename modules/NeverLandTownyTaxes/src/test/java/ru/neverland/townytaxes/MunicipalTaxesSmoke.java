package ru.neverland.townytaxes;
import ru.neverland.townytaxes.model.*;
import java.util.*;
public final class MunicipalTaxesSmoke {
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){UUID city=UUID.randomUUID(),other=UUID.randomUUID();for(var type:Domain.TaxType.values())for(var scope:Domain.Scope.values())for(var dest:Domain.Scope.values()){var policy=new TaxPolicy(UUID.randomUUID(),scope,city,"Город",type,5,dest,city,"Казна",60000,1,true,"admin");check(MunicipalTaxes.matches(policy,city)==(scope==Domain.Scope.TOWN&&dest==Domain.Scope.TOWN),"municipal scope only");check(!MunicipalTaxes.matches(policy,other),"foreign city not affected");}var foreign=new TaxPolicy(UUID.randomUUID(),Domain.Scope.TOWN,city,"Город",Domain.TaxType.FIXED,10,Domain.Scope.TOWN,other,"Чужая казна",60000,1,true,"admin");check(!MunicipalTaxes.matches(foreign,city),"payments to another treasury unchanged");System.out.println("MunicipalTaxesSmoke OK: three tax types / sixteen scope pairs, own treasury and foreign/global/national exclusions");}
}

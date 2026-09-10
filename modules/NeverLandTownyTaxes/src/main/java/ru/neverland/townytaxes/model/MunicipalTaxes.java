package ru.neverland.townytaxes.model;
import java.util.UUID;
public final class MunicipalTaxes {
    private MunicipalTaxes(){}
    public static boolean matches(TaxPolicy p,UUID town){return town!=null&&p.enabled()&&Double.isFinite(p.value())&&p.value()>0&&p.scope()==Domain.Scope.TOWN&&town.equals(p.targetId())&&p.destinationScope()==Domain.Scope.TOWN&&town.equals(p.destinationId());}
}

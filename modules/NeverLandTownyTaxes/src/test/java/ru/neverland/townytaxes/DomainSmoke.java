package ru.neverland.townytaxes;

import ru.neverland.townytaxes.model.Agreement;
import ru.neverland.townytaxes.model.Domain;
import java.util.UUID;

public final class DomainSmoke {
    public static void main(String[] args){UUID a=UUID.randomUUID(),b=UUID.randomUUID();Agreement v=new Agreement(UUID.randomUUID(),Domain.Scope.TOWN,a,"A",Domain.Scope.TOWN,b,"B",Domain.AgreementType.TRADE_PREFERENCE,15,System.currentTimeMillis(),0,Domain.AgreementStatus.ACTIVE,"test");if(!v.pair(Domain.Scope.TOWN,b,Domain.Scope.TOWN,a)||!v.active(System.currentTimeMillis()))throw new AssertionError();if(Domain.TaxType.parse("income")!=Domain.TaxType.INCOME||Domain.SanctionEffect.parse("trade_block")!=Domain.SanctionEffect.TRADE_BLOCK)throw new AssertionError();System.out.println("Domain smoke: OK");}
}

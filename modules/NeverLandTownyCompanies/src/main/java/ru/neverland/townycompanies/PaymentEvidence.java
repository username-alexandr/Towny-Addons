package ru.neverland.townycompanies;
public final class PaymentEvidence {
    private final ru.neverland.core.PaymentEvidence delegate;
    public PaymentEvidence(Object account,boolean incoming,String token,long cents){delegate=new ru.neverland.core.PaymentEvidence(account,incoming,token,cents);}
    public void observe(Object source,boolean deposit,double amount,String reason){delegate.observe(source,deposit,amount,reason);}
    public boolean observed(){return delegate.observed();}
    public boolean result(boolean reported){return delegate.result(reported);}
}

package ru.neverland.mintcontracts.integration;
public final class PaymentEvidence {
    private final ru.neverland.core.PaymentEvidence delegate;
    public PaymentEvidence(Object account,boolean incoming,String token,long cents){delegate=new ru.neverland.core.PaymentEvidence(account,incoming,token,cents);}
    public void observe(Object source,boolean deposit,double amount,String reason){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {delegate.observe(source,deposit,amount,reason);} }
    public boolean observed(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return delegate.observed();} }
    public boolean result(boolean reported){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return delegate.result(reported);} }
}

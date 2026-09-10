package ru.neverland.townymarket;
/** Identity, direction and operation token prevent counting the server account or another transfer. */
public final class PaymentEvidence {
    private final Object account;private final boolean incoming;private final String token;private final double amount;private boolean observed;
    public PaymentEvidence(Object account,boolean incoming,String token,long cents){this.account=account;this.incoming=incoming;this.token=token;this.amount=cents/100.0;}
    public void observe(Object source,boolean deposit,double value,String reason){if(source==account&&incoming==deposit&&Double.isFinite(value)&&Math.abs(value-amount)<0.000001&&reason!=null&&reason.contains(token))observed=true;}
    public boolean observed(){return observed;}
    public boolean result(boolean reported){if(observed)return true;if(reported)throw new IllegalStateException("Банк сообщил успех без квитанции города/игрока");return false;}
}

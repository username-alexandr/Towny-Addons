package ru.neverland.townyjustice;
import java.util.*;
public record JusticePayment(UUID id,UUID caseId,UUID town,UUID resident,Kind kind,long amount,Step step,long created,long check){
    public enum Kind{FINE,BOUNTY_FUND,BOUNTY_PAY,BOUNTY_REFUND}
    public enum Step{READY,DEBIT_PENDING,DEBITED,CREDIT_PENDING,COMPLETE,CANCELLED}
    public JusticePayment{Objects.requireNonNull(id);Objects.requireNonNull(caseId);Objects.requireNonNull(town);Objects.requireNonNull(resident);Objects.requireNonNull(kind);Objects.requireNonNull(step);if(amount<1||amount>100000000000L||created<0||check<0||(kind==Kind.BOUNTY_PAY||kind==Kind.BOUNTY_REFUND)&&(step==Step.DEBIT_PENDING||step==Step.CANCELLED)||kind==Kind.BOUNTY_FUND&&step==Step.CREDIT_PENDING)throw new IllegalArgumentException("Повреждён платёж суда");}
    public boolean debitRequired(){return kind==Kind.FINE||kind==Kind.BOUNTY_FUND;}
    public boolean creditRequired(){return kind!=Kind.BOUNTY_FUND;}
    public JusticePayment step(Step s,long now){return new JusticePayment(id,caseId,town,resident,kind,amount,s,created,now);}
}

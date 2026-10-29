package ru.neverland.townyjustice;
import java.util.*;
public record JusticePayment(UUID id,UUID caseId,UUID town,UUID resident,Kind kind,long amount,Step step,long created,long check,Route route,int treasuryAttempt){
    public enum Route{LEGACY,TREASURY}
    public JusticePayment(UUID id,UUID caseId,UUID town,UUID resident,Kind kind,long amount,Step step,long created,long check){this(id,caseId,town,resident,kind,amount,step,created,check,Route.LEGACY,0);}
    public JusticePayment(UUID id,UUID caseId,UUID town,UUID resident,Kind kind,long amount,Step step,long created,long check,Route route){this(id,caseId,town,resident,kind,amount,step,created,check,route,0);}
    public enum Kind{FINE,BOUNTY_FUND,BOUNTY_PAY,BOUNTY_REFUND}
    public enum Step{READY,DEBIT_PENDING,DEBITED,CREDIT_PENDING,COMPLETE,CANCELLED}
    public JusticePayment{Objects.requireNonNull(id);Objects.requireNonNull(caseId);Objects.requireNonNull(town);Objects.requireNonNull(resident);Objects.requireNonNull(kind);Objects.requireNonNull(step);Objects.requireNonNull(route);if(amount<1||amount>100000000000L||created<0||check<0||treasuryAttempt<0||treasuryAttempt>1000||treasuryAttempt>0&&(route!=Route.TREASURY||kind!=Kind.BOUNTY_REFUND)||route==Route.TREASURY&&kind!=Kind.BOUNTY_FUND&&kind!=Kind.BOUNTY_REFUND||(kind==Kind.BOUNTY_PAY||kind==Kind.BOUNTY_REFUND)&&(step==Step.DEBIT_PENDING||step==Step.CANCELLED)||kind==Kind.BOUNTY_FUND&&step==Step.CREDIT_PENDING)throw new IllegalArgumentException("Повреждён платёж суда");}
    public boolean debitRequired(){ try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {return kind==Kind.FINE||kind==Kind.BOUNTY_FUND;} }
    public boolean creditRequired(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return kind!=Kind.BOUNTY_FUND;} }
    public JusticePayment step(Step s,long now){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {return new JusticePayment(id,caseId,town,resident,kind,amount,s,created,now,route,treasuryAttempt);} }
    public JusticePayment treasury(){if(kind!=Kind.BOUNTY_FUND||step!=Step.READY)throw new IllegalArgumentException("Старое неопределённое списание нельзя переносить автоматически");return new JusticePayment(id,caseId,town,resident,kind,amount,step,created,check,Route.TREASURY);}
    public UUID treasuryId(){return treasuryAttempt==0?id:UUID.nameUUIDFromBytes(("justice-refund:"+id+":"+treasuryAttempt).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public JusticePayment rejectedRefund(long now){if(route!=Route.TREASURY||kind!=Kind.BOUNTY_REFUND||step!=Step.CREDIT_PENDING)throw new IllegalArgumentException("Нет доказанного отклонения возврата");return new JusticePayment(id,caseId,town,resident,kind,amount,Step.DEBITED,created,now,route,Math.incrementExact(treasuryAttempt));}
}

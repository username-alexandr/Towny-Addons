package ru.neverland.townytaxes.model;

import java.util.*;

/** Parties, price and modifiers are fixed before the first bank call. */
public record TaxPayment(UUID id,UUID policy,Kind kind,Domain.Scope sourceScope,UUID sourceId,String sourceName,
                         Domain.Scope destinationScope,UUID destinationId,String destinationName,
                         TaxCalculation calculation,Phase phase,long created,boolean debtOnReject,String note) {
    public enum Kind { TAX, DEBT }
    public enum Phase { PREPARED,DEBITING,DEBITED,CREDITING,REFUND_READY,REFUNDING,DONE,REFUNDED,REJECTED,VOID }
    public TaxPayment {
        Objects.requireNonNull(id);Objects.requireNonNull(kind);Objects.requireNonNull(sourceScope);Objects.requireNonNull(sourceId);Objects.requireNonNull(destinationScope);Objects.requireNonNull(destinationId);Objects.requireNonNull(calculation);Objects.requireNonNull(phase);
        if(sourceScope==Domain.Scope.GLOBAL||kind==Kind.TAX&&policy==null||kind==Kind.DEBT&&destinationScope!=Domain.Scope.GLOBAL||created<0||calculation.cents()<=0)throw new IllegalArgumentException("Повреждена налоговая квитанция");
        for(String text:new String[]{sourceName,destinationName,note})if(text==null||text.length()>1024||text.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Повреждено описание налоговой квитанции");
    }
    public boolean terminal(){return Set.of(Phase.DONE,Phase.REFUNDED,Phase.REJECTED,Phase.VOID).contains(phase);}
    public boolean review(){return Set.of(Phase.DEBITING,Phase.CREDITING,Phase.REFUNDING).contains(phase);}
    public TaxPayment phase(Phase next){
        if(next==phase)return this;boolean valid=switch(phase){
            case PREPARED->next==Phase.DEBITING||next==Phase.VOID;
            case DEBITING->next==Phase.DEBITED||next==Phase.REJECTED||next==Phase.PREPARED;
            case DEBITED->next==Phase.CREDITING||next==Phase.DONE||next==Phase.REFUND_READY;
            case CREDITING->next==Phase.DONE||next==Phase.DEBITED;
            case REFUND_READY->next==Phase.REFUNDING;
            case REFUNDING->next==Phase.REFUNDED||next==Phase.REFUND_READY;
            default->false;
        };if(!valid)throw new IllegalStateException("Недопустимый переход налогового перевода");
        return new TaxPayment(id,policy,kind,sourceScope,sourceId,sourceName,destinationScope,destinationId,destinationName,calculation,next,created,debtOnReject,note);
    }
}

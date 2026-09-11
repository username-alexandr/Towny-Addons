package ru.neverland.mintcontracts.model;
import java.util.*;
/** PLAYER_PENDING is deliberately not replayed: inventory persistence must be reconciled first. */
public record DeliveryIntent(UUID id,UUID contract,UUID town,UUID actor,String sample,int amount,Phase phase,long created,boolean acknowledged) {
    public enum Phase { PLAYER_PENDING, TAKEN, COMPLETE, REJECTED }
    public DeliveryIntent {
        Objects.requireNonNull(id);Objects.requireNonNull(contract);Objects.requireNonNull(town);Objects.requireNonNull(actor);Objects.requireNonNull(phase);
        if(sample==null||sample.isEmpty()||sample.length()>1_000_000||amount<1||amount>1_000_000||created<0||acknowledged&&phase!=Phase.COMPLETE)throw new IllegalArgumentException("Некорректная поставка");
    }
    public DeliveryIntent phase(Phase value){return new DeliveryIntent(id,contract,town,actor,sample,amount,value,created,acknowledged);}
    public DeliveryIntent acknowledge(){return new DeliveryIntent(id,contract,town,actor,sample,amount,phase,created,true);}
}

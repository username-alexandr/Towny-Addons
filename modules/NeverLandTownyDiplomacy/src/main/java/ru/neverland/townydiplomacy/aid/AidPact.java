package ru.neverland.townydiplomacy.aid;

import java.util.*;

/** Political undertaking only: no money, items, reputation changes or authority to start a war. */
public record AidPact(UUID id,UUID alliance,UUID first,UUID second,UUID firstMayor,UUID secondMayor,
        String proposer,String accepter,long created,long offerUntil,long endsAt,long responseMillis,
        long requiredMilliDamage,Phase phase,String note) {
    public enum Phase { OFFERED, ACCEPTING, FINALIZING, ACTIVE, ENDED }
    public AidPact {
        for(var idValue:List.of(id,alliance,first,second,firstMayor,secondMayor))Objects.requireNonNull(idValue);
        Objects.requireNonNull(phase);
        if(first.equals(second)||proposer==null||proposer.isBlank()||proposer.length()>100||accepter==null||accepter.length()>100
                ||created<0||offerUntil<=created||endsAt<=offerUntil||endsAt-created>365L*86_400_000
                ||responseMillis<60_000||responseMillis>7_200_000||requiredMilliDamage<1000||requiredMilliDamage>100_000
                ||note==null||note.length()>200||phase==Phase.OFFERED&&!accepter.isEmpty()
                ||(phase==Phase.ACCEPTING||phase==Phase.FINALIZING||phase==Phase.ACTIVE)&&accepter.isBlank())throw new IllegalArgumentException("Повреждены условия взаимопомощи");
    }
    public boolean party(UUID town){return first.equals(town)||second.equals(town);}
    public UUID other(UUID town){if(!party(town))throw new IllegalArgumentException("Город не является стороной");return first.equals(town)?second:first;}
    public boolean live(long at){return phase==Phase.ACTIVE&&at<endsAt;}
    public AidPact phase(Phase next,String signer,String why){return new AidPact(id,alliance,first,second,firstMayor,secondMayor,proposer,signer,created,offerUntil,endsAt,responseMillis,requiredMilliDamage,next,why);}
}

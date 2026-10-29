package ru.neverland.townydiplomacy.aid;

import java.nio.charset.StandardCharsets;
import java.util.*;

public record AidCall(UUID id,UUID pact,UUID beneficiary,UUID helper,UUID enemy,long opened,long deadline,
        long requiredMilliDamage,Status status,String note,AidFact attack,List<AidFact> proof) {
    public enum Status { OPEN, FULFILLED, UNANSWERED, UNASSESSED }
    public AidCall {
        for(var value:List.of(id,pact,beneficiary,helper,enemy))Objects.requireNonNull(value);
        Objects.requireNonNull(status);Objects.requireNonNull(attack);proof=List.copyOf(proof);
        if(beneficiary.equals(helper)||beneficiary.equals(enemy)||helper.equals(enemy)||opened<0||deadline<=opened
                ||deadline-opened>7_200_000||requiredMilliDamage<1000||requiredMilliDamage>100_000||note==null||note.length()>200||proof.size()>128)throw new IllegalArgumentException("Повреждён вызов помощи");
        if(!attack.actorTown().equals(enemy)||!attack.victimTown().equals(beneficiary)||!attack.territory().equals(beneficiary)||attack.at()!=opened)throw new IllegalArgumentException("Квитанция нападения не совпадает");
        var ids=new HashSet<UUID>();long damage=0;
        for(var fact:proof){if(!ids.add(fact.id())||!valid(fact,helper,enemy,beneficiary,opened,deadline))throw new IllegalArgumentException("Квитанция другого вызова");damage=Math.addExact(damage,fact.milliDamage());}
        if(status==Status.FULFILLED&&damage<requiredMilliDamage||status==Status.OPEN&&damage>=requiredMilliDamage)throw new IllegalArgumentException("Состояние не соответствует фактам помощи");
    }
    private static boolean valid(AidFact f,UUID helper,UUID enemy,UUID territory,long opened,long deadline){return f.actorTown().equals(helper)&&f.victimTown().equals(enemy)&&f.territory().equals(territory)&&f.at()>=opened&&f.at()<deadline;}
    public long confirmedMilliDamage(){return proof.stream().mapToLong(AidFact::milliDamage).sum();}
    public AidCall close(Status next,String why){return new AidCall(id,pact,beneficiary,helper,enemy,opened,deadline,requiredMilliDamage,next,why,attack,proof);}
    public AidCall observe(AidFact fact){
        if(status!=Status.OPEN||!valid(fact,helper,enemy,beneficiary,opened,deadline)||proof.stream().anyMatch(p->p.id().equals(fact.id())||p.actor().equals(fact.actor())&&p.victim().equals(fact.victim())&&Math.abs(fact.at()-p.at())<5000))return this;
        if(proof.size()>=128)return close(Status.UNASSESSED,"Достигнут предел квитанций; требуется просмотр истории");
        var next=new ArrayList<>(proof);next.add(fact);boolean fulfilled=confirmedMilliDamage()+fact.milliDamage()>=requiredMilliDamage;
        return new AidCall(id,pact,beneficiary,helper,enemy,opened,deadline,requiredMilliDamage,fulfilled?Status.FULFILLED:Status.OPEN,fulfilled?"Помощь подтверждена серверными событиями":"Помощь подтверждена частично",attack,next);
    }
    public static AidCall open(AidPact pact,AidFact attack){
        long deadline=Math.min(pact.endsAt(),Math.addExact(attack.at(),pact.responseMillis()));
        UUID id=UUID.nameUUIDFromBytes(("aid:"+pact.id()+":"+attack.id()).getBytes(StandardCharsets.UTF_8));
        return new AidCall(id,pact.id(),attack.victimTown(),pact.other(attack.victimTown()),attack.actorTown(),attack.at(),deadline,pact.requiredMilliDamage(),Status.OPEN,"Зафиксировано нападение на земле союзного города",attack,List.of());
    }
}

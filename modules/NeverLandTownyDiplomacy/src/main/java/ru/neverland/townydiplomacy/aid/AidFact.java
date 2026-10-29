package ru.neverland.townydiplomacy.aid;

import java.util.*;

/** Captured on the victim's owning region after an uncancelled positive-damage event. */
public record AidFact(UUID id,long at,UUID actor,UUID actorTown,UUID victim,UUID victimTown,UUID territory,
        long milliDamage,UUID world,int x,int y,int z) {
    public AidFact {
        for(var value:List.of(id,actor,actorTown,victim,victimTown,territory,world))Objects.requireNonNull(value);
        if(actor.equals(victim)||actorTown.equals(victimTown)||at<0||milliDamage<1000||milliDamage>20_000)throw new IllegalArgumentException("Неверная квитанция помощи");
    }
}

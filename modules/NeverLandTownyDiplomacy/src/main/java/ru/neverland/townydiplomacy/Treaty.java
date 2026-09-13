package ru.neverland.townydiplomacy;

import java.util.*;

public record Treaty(UUID id, TreatyType type, UUID first, UUID second, UUID firstMayor, UUID secondMayor,
                     String actor, String reason, Phase phase, long created, long offerUntil, long duration,
                     long activated, long expires, long noticeUntil, long noticePeriod, int discountBasisPoints, SanctionScope sanction) {
    public enum Phase { PENDING, ACTIVE, TERMINATING, ENDED }
    public enum SanctionScope { NONE, TRADE, DIPLOMATIC, ALL }
    public Treaty {
        Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(first); Objects.requireNonNull(second);
        Objects.requireNonNull(firstMayor); Objects.requireNonNull(secondMayor); Objects.requireNonNull(phase); Objects.requireNonNull(sanction);
        if (first.equals(second) || actor==null || actor.isBlank() || actor.length()>200 || reason==null || reason.isBlank() || reason.length()>200
                || created<0 || offerUntil<=created || duration<=0 || duration>3650L*86_400_000 || activated<0 || expires<0 || noticeUntil<0
                || noticePeriod<0 || noticePeriod>30L*86_400_000 || discountBasisPoints<0 || discountBasisPoints>10000 || type!=TreatyType.TRADE && discountBasisPoints!=0
                || type==TreatyType.SANCTIONS && sanction==SanctionScope.NONE || type!=TreatyType.SANCTIONS && sanction!=SanctionScope.NONE)
            throw new IllegalArgumentException("Повреждены условия договора");
        if (phase==Phase.PENDING && (!type.bilateral() || activated!=0 || expires!=0 || noticeUntil!=0)
                || (phase==Phase.ACTIVE || phase==Phase.TERMINATING) && (activated<created || expires<=activated || expires-activated!=duration)
                || phase==Phase.ACTIVE && noticeUntil!=0 || phase==Phase.TERMINATING && (noticeUntil<activated || noticeUntil>expires))
            throw new IllegalArgumentException("Повреждены сроки договора");
    }
    public boolean party(UUID town) { return first.equals(town)||second.equals(town); }
    public boolean pair(UUID a,UUID b) { return first.equals(a)&&second.equals(b)||first.equals(b)&&second.equals(a); }
    public UUID other(UUID town) { if(!party(town))throw new IllegalArgumentException("Город не является стороной");return first.equals(town)?second:first; }
    public boolean active(long now) { return (phase==Phase.ACTIVE || phase==Phase.TERMINATING) && now<expires && (phase!=Phase.TERMINATING || now<noticeUntil); }
    public boolean pending(long now) { return phase==Phase.PENDING && now<offerUntil; }
    public boolean open(long now) { return active(now)||pending(now); }
    public Treaty accept(long now) {
        if(!pending(now))throw new IllegalArgumentException("Предложение уже обработано или истекло");
        return copy(Phase.ACTIVE,now,Math.addExact(now,duration),0);
    }
    public Treaty terminate(long now,long notice) {
        if(!active(now) || phase==Phase.TERMINATING)throw new IllegalArgumentException("Договор уже завершён или расторгается");
        return type.bilateral() && notice>0 ? copy(Phase.TERMINATING,activated,expires,Math.min(expires,Math.addExact(now,notice))) : end();
    }
    public Treaty end() { return copy(Phase.ENDED,activated,expires,noticeUntil); }
    private Treaty copy(Phase next,long start,long until,long end) { return new Treaty(id,type,first,second,firstMayor,secondMayor,actor,reason,next,created,offerUntil,duration,start,until,end,noticePeriod,discountBasisPoints,sanction); }
}

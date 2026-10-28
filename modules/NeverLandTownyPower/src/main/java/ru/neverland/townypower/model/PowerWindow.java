package ru.neverland.townypower.model;
/** A process-local monotonic lease. Nothing can restore or replay it after restart. */
public record PowerWindow(long opened,long expires,long surplus,int seconds) {
    public PowerWindow{if(seconds<1||seconds>60||surplus<0||surplus>1_000_000)throw new IllegalArgumentException("Неверное окно сети");}
    public static PowerWindow start(long now,long surplus,int seconds){return new PowerWindow(now,now+java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds),surplus,seconds);}
    public PowerWindow acknowledged(long now){return new PowerWindow(now,expires,surplus,seconds);}
    public boolean live(long now){return now-opened>=0&&now-expires<0;}
    public long completedEnergy(long now){long duration=expires-opened;return now-expires>=0&&duration>0?Math.multiplyExact(surplus,Math.min(duration,java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds)))/1_000_000_000L:0;}
}

package ru.neverland.townydiplomacy;

import org.bukkit.configuration.ConfigurationSection;

public record DiplomacySettings(int defaultDays,int maximumDays,long proposalMillis,long noticeMillis,int tradeDiscountBasisPoints,int maxOpen,long incidentCooldown) {
    public DiplomacySettings {
        if(defaultDays<1||maximumDays<defaultDays||maximumDays>3650||proposalMillis<60_000||proposalMillis>30L*86_400_000
                ||noticeMillis<0||noticeMillis>30L*86_400_000||tradeDiscountBasisPoints<0||tradeDiscountBasisPoints>10000||maxOpen<1||maxOpen>1000
                ||incidentCooldown<10_000||incidentCooldown>86_400_000)throw new IllegalArgumentException("Некорректные сроки или ограничения дипломатии");
    }
    public static DiplomacySettings defaults() { return new DiplomacySettings(30,365,72*3_600_000L,24*3_600_000L,2500,64,300_000); }
    public static DiplomacySettings load(ConfigurationSection y) {
        return new DiplomacySettings(integer(y,"default-days"),integer(y,"maximum-days"),Math.multiplyExact(integer(y,"proposal-hours"),3_600_000L),
                Math.multiplyExact(integer(y,"termination-notice-hours"),3_600_000L),Math.multiplyExact(integer(y,"trade-discount-percent"),100),
                integer(y,"maximum-open-per-town"),Math.multiplyExact(integer(y,"incident-cooldown-seconds"),1000L));
    }
    private static int integer(ConfigurationSection y,String key) {
        if(!(y.get(key) instanceof Integer n))throw new IllegalArgumentException("Нужно целое число: "+key); return n;
    }
}

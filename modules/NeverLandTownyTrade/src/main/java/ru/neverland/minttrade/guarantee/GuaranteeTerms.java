package ru.neverland.minttrade.guarantee;

import java.math.BigInteger;

/** Seller's delivery-time warranty is carved only out of the already funded base-price escrow. */
public record GuaranteeTerms(int reserveBasisPoints,int delayBasisPointsPerMinute,long graceMillis) {
    public static final long MAX_CENTS=100_000_000_000L,MAX_DURATION=30L*86_400_000;
    public GuaranteeTerms {
        if(reserveBasisPoints<0||reserveBasisPoints>10_000||delayBasisPointsPerMinute<0||delayBasisPointsPerMinute>10_000||graceMillis<0||graceMillis>MAX_DURATION||reserveBasisPoints==0&&delayBasisPointsPerMinute!=0||reserveBasisPoints>0&&delayBasisPointsPerMinute==0)throw new IllegalArgumentException("Неверные условия гарантии сроков");
    }
    public static GuaranteeTerms none(){return new GuaranteeTerms(0,0,0);}
    public boolean enabled(){return reserveBasisPoints>0;}
    public long reserved(long baseCents){amount(baseCents);return baseCents*reserveBasisPoints/10_000;}
    public long compensation(long baseCents,long experiencedDelayMillis){
        amount(baseCents);if(experiencedDelayMillis<0||experiencedDelayMillis>MAX_DURATION)throw new IllegalArgumentException("Неверная длительность задержки");
        long paidDelay=Math.max(0,experiencedDelayMillis-graceMillis);if(!enabled()||paidDelay==0)return 0;
        var claim=BigInteger.valueOf(baseCents).multiply(BigInteger.valueOf(delayBasisPointsPerMinute)).multiply(BigInteger.valueOf(paidDelay)).divide(BigInteger.valueOf(10_000L*60_000));
        return claim.min(BigInteger.valueOf(reserved(baseCents))).longValueExact();
    }
    public Allocation allocate(long baseCents,long experiencedDelayMillis){long compensation=compensation(baseCents,experiencedDelayMillis);return new Allocation(baseCents-compensation,compensation,reserved(baseCents));}
    public record Allocation(long sellerCents,long buyerCompensationCents,long reservedCents){public Allocation{if(sellerCents<0||buyerCompensationCents<0||reservedCents<buyerCompensationCents)throw new IllegalArgumentException("Необеспеченная компенсация");}}
    private static void amount(long cents){if(cents<0||cents>MAX_CENTS)throw new IllegalArgumentException("Цена гарантии вне допустимого диапазона");}
}

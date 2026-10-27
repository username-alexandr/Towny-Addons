package ru.neverland.townysieges;
import java.math.*;
public final class RepairRules {
    private RepairRules(){}
    /** One shared HP pool; charge current-level price, rounded up only once to the nearest cent. */
    public static long cost(long levelCents,double lost,double maximum){
        if(levelCents<0||!Double.isFinite(lost)||!Double.isFinite(maximum)||maximum<=0||lost<0||lost>maximum+.000001)throw new IllegalArgumentException("Неверная прочность здания");
        return BigDecimal.valueOf(levelCents).multiply(BigDecimal.valueOf(Math.min(lost,maximum))).divide(BigDecimal.valueOf(maximum),0,RoundingMode.CEILING).longValueExact();
    }
}

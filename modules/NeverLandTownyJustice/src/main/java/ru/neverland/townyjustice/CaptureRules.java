package ru.neverland.townyjustice;
import java.util.UUID;
/** No combat or protection bypass: capture is a close-range action in the issuing town. */
public final class CaptureRules {
    private CaptureRules(){}
    public static boolean allowed(UUID hunter,UUID target,UUID issuingTown,UUID hunterTown,UUID targetTown,boolean hunterReady,boolean targetReady,boolean alreadyJailed,boolean funded,boolean wanted,double distance,double limit){return hunter!=null&&target!=null&&!hunter.equals(target)&&issuingTown!=null&&issuingTown.equals(hunterTown)&&issuingTown.equals(targetTown)&&hunterReady&&targetReady&&!alreadyJailed&&funded&&wanted&&Double.isFinite(distance)&&distance>=0&&distance<=limit;}
    public static boolean confirmed(UUID expected,UUID actual,boolean online,boolean queued,boolean inCell){return expected!=null&&expected.equals(actual)&&online&&!queued&&inCell;}
}

package ru.neverland.townycrime;
/** Pure, bounded calculations. No Bukkit objects, randomness, clock or persistence. */
public final class CrimeEngine {
    private CrimeEngine(){}
    public record Inputs(double happiness,int guardLevel,int workers){public Inputs{if(!CrimeSettings.finite(happiness,0,100)||guardLevel<0||guardLevel>100||workers<0||workers>1000)throw new IllegalArgumentException("Неверные данные города");}}
    public static double guard(Inputs i,CrimeSettings s){return Math.min(100,i.guardLevel()==0?0:i.guardLevel()*s.levelStrength()+i.workers()*s.workerStrength());}
    public static double target(Inputs i,CrimeSettings s){return Math.max(0,Math.min(100,(100-i.happiness())*s.unhappiness()+s.unguarded()-guard(i,s)*s.guardReduction()));}
    public static double advance(double old,Inputs i,CrimeSettings s){if(!CrimeSettings.finite(old,0,100))throw new IllegalArgumentException("Неверная преступность");return old+Math.max(-s.change(),Math.min(s.change(),target(i,s)-old));}
    public static int income(double crime,boolean extortion,CrimeSettings s){if(!CrimeSettings.finite(crime,0,100))throw new IllegalArgumentException("Неверная преступность");return 10000-(int)Math.round(crime*s.shopLoss()/100)-(extortion?s.extortionLoss():0);}
    public static long theft(long available,CrimeSettings s){if(available<0)throw new IllegalArgumentException("Неверный запас");return Math.min(s.theftCap(),(available/10000)*s.theftShare()+(available%10000)*s.theftShare()/10000);}
}

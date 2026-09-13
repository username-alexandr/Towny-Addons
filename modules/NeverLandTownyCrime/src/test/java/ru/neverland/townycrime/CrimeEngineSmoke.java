package ru.neverland.townycrime;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
public final class CrimeEngineSmoke {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static CrimeSettings settings()throws Exception{var y=new YamlConfiguration();try(var in=CrimeEngineSmoke.class.getResourceAsStream("/config.yml")){y.load(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));}return CrimeSettings.load(y);}
    public static void main(String[] args)throws Exception{
        var s=settings();var happy=new CrimeEngine.Inputs(100,0,0);var unhappy=new CrimeEngine.Inputs(0,0,0);var guards=new CrimeEngine.Inputs(0,5,10);
        check(CrimeEngine.target(unhappy,s)>CrimeEngine.target(happy,s),"unhappiness raises crime");check(CrimeEngine.target(guards,s)<CrimeEngine.target(unhappy,s),"operational guards reduce crime");
        check(CrimeEngine.guard(new CrimeEngine.Inputs(0,0,10),s)==0,"disabled building cannot supply guards");check(CrimeEngine.guard(new CrimeEngine.Inputs(0,1,3),s)>CrimeEngine.guard(new CrimeEngine.Inputs(0,1,0),s),"staff improves guard effectiveness");
        double n=0;for(int i=0;i<1000;i++){double next=CrimeEngine.advance(n,unhappy,s);check(next>=0&&next<=100&&Math.abs(next-n)<=s.change(),"bounded gradual cycle");n=next;}
        check(n==100&&CrimeEngine.advance(n,guards,s)<100,"crime can recover");
        check(CrimeEngine.income(100,false,s)==7500&&CrimeEngine.income(100,true,s)==6500,"income and temporary extortion combine");
        check(CrimeEngine.theft(Long.MAX_VALUE,s)==s.theftCap()&&CrimeEngine.theft(49,s)==0&&CrimeEngine.theft(10000,s)==200,"theft cap, overflow and thousandths");
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,101}){boolean denied=false;try{new CrimeEngine.Inputs(invalid,1,1);}catch(IllegalArgumentException e){denied=true;}check(denied,"invalid happiness rejected");}
        var bad=new YamlConfiguration();bad.set("cycle-seconds","600");boolean denied=false;try{CrimeSettings.load(bad);}catch(IllegalArgumentException e){denied=true;}check(denied,"malformed config rejected");
        System.out.println("CrimeEngineSmoke OK");
    }
}

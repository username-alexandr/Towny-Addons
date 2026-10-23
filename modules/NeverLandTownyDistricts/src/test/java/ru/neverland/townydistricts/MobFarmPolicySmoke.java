package ru.neverland.townydistricts;
import java.util.UUID;
import ru.neverland.townydistricts.api.MobFarmPlot;
import ru.neverland.townydistricts.service.MobFarmPolicy;
public final class MobFarmPolicySmoke {
    private static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static void fail(Runnable task,String message){try{task.run();throw new AssertionError(message);}catch(IllegalStateException expected){checks++;}}
    public static void main(String[] args){
        check(MobFarmPolicy.status(true,true,false,false).equals("PROTECTED"),"native protected flag");
        check(MobFarmPolicy.status(true,true,false,true).equals("ALLOWED"),"native farm flag");
        check(MobFarmPolicy.status(true,false,false,true).equals("WORLD_RESTRICTED"),"world ban not shown as working farm");
        check(MobFarmPolicy.status(true,true,true,false).equals("FORCED_ALLOWED"),"forced mobs override false local flag");
        check(MobFarmPolicy.status(false,false,false,false).equals("TOWNY_DISABLED"),"disabled Towny never shown as protected");
        MobFarmPolicy.validate(true,true,false,false,true);MobFarmPolicy.validate(true,true,false,false,false);
        fail(()->MobFarmPolicy.validate(true,false,false,false,true),"global restrictions respected");
        fail(()->MobFarmPolicy.validate(true,true,true,false,false),"forced setting cannot be locally disabled");
        fail(()->MobFarmPolicy.validate(false,true,false,false,true),"disabled world cannot be changed");
        fail(()->MobFarmPolicy.validate(true,true,false,true,true),"group cannot change neighboring plots");
        fail(()->MobFarmPolicy.validate(true,true,false,true,false),"group cannot change neighboring plots on protection");
        var p=new MobFarmPlot(UUID.randomUUID(),UUID.randomUUID(),"world",-1,-2,16,true,"ALLOWED","");
        check(p.minBlockX()==-16&&p.maxBlockX()==-1&&p.minBlockZ()==-32&&p.maxBlockZ()==-17,"negative block bounds exact");
        check(p.protectionDisabled(),"map classifies farm");
        var large=new MobFarmPlot(p.townId(),p.worldId(),"world",-1,1,32,true,"WORLD_RESTRICTED","");
        check(large.minBlockX()==-32&&large.maxBlockZ()==63,"custom Towny grid represented truthfully");
        check(!large.protectionDisabled(),"world restriction distinct from allowed flag");
        System.out.println("MobFarmPolicySmoke PASS: "+checks+" checks");
    }
}

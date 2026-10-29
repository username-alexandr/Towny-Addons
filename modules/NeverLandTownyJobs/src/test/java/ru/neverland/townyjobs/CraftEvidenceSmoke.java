package ru.neverland.townyjobs;
import java.util.*;
public final class CraftEvidenceSmoke {
    static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var recipe=new CraftEvidence(Map.of("iron",2L,"stick",1L),Map.of("iron",64L,"stick",32L),0,1);
        check(recipe.confirmed(Map.of("iron",62L,"stick",31L),1)==1,"one real sword consumes every ingredient");
        check(recipe.confirmed(Map.of("iron",48L,"stick",24L),8)==8,"shift craft quantity is backed by all eight recipes");
        check(recipe.confirmed(Map.of("iron",64L,"stick",32L),1)==0,"moving output without consuming inputs cannot pay");
        check(recipe.confirmed(Map.of("iron",62L,"stick",32L),1)==0,"one consumed ingredient cannot fake a whole recipe");
        check(recipe.confirmed(Map.of("iron",62L,"stick",31L),8)==0,"a single recipe cannot be inflated to a stack");
        check(recipe.confirmed(Map.of("iron",64L,"stick",32L),0)==0,"matrix and inventory transfers conserve combined counts");
        var repair=new CraftEvidence(Map.of("sword",2L),Map.of("sword",2L),0,1);check(repair.confirmed(Map.of("sword",1L),1)==0,"repairing existing tools cannot masquerade as manufacturing new ones");
        var packet=new CraftEvidence(Map.of("dust",1L),Map.of("dust",10L),0,4);check(packet.confirmed(Map.of("dust",9L),3)==0&&packet.confirmed(Map.of("dust",9L),4)==4,"multi-output recipe must produce a complete recipe quantity");
        check(recipe.confirmed(Map.of(),4097)==0,"oversized artificial crafting action is refused");
        var random=new Random(99331);for(int n=0;n<10000;n++){int output=1+random.nextInt(64),iron=random.nextInt(65),stick=random.nextInt(33);int accepted=recipe.confirmed(Map.of("iron",(long)iron,"stick",(long)stick),output);if(accepted>0&&(64-iron<2L*accepted||32-stick<accepted))throw new AssertionError("unfunded craft at "+n);}
        check(true,"10000 adversarial inventory deltas cannot produce an unfunded recipe");
        System.out.println("CraftEvidenceSmoke PASS "+checks+" checks including 10000 adversarial inventory deltas");
    }
}

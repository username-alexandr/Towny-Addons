package ru.neverland.townyjobs;
import java.util.*;
/** Every recipe ingredient must leave the combined player+matrix inventory for every produced recipe. */
public record CraftEvidence(Map<String,Long> ingredients,Map<String,Long> before,int resultBefore,int perCraft) {
    public CraftEvidence{ingredients=Map.copyOf(ingredients);before=Map.copyOf(before);if(ingredients.isEmpty()||perCraft<1||perCraft>99||resultBefore<0||ingredients.values().stream().anyMatch(n->n<1))throw new IllegalArgumentException("Неверная квитанция изготовления");}
    public int confirmed(Map<String,Long> after,int resultAfter){
        int gained=resultAfter-resultBefore;if(gained<1||gained>4096||gained%perCraft!=0)return 0;int crafts=gained/perCraft;
        for(var ingredient:ingredients.entrySet())if(before.getOrDefault(ingredient.getKey(),0L)-after.getOrDefault(ingredient.getKey(),0L)<Math.multiplyExact(ingredient.getValue(),crafts))return 0;
        return gained;
    }
}

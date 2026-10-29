package ru.neverland.townysieges;
import java.util.*;
import static ru.neverland.townysieges.RepairTreasury.Outcome.*;
public final class RepairTreasurySmoke {
    static int checks;static void ok(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    static void rejected(Runnable action){try{action.run();throw new AssertionError("foreign proof accepted");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        var job=new RaidRepairsStore.Job(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"town_hall","Ратуша","fixed",2,10000,800,400,5000,30000,12,"TREASURY_PENDING","",List.of(),"TREASURY_V1");
        Map<String,Object> proof=new HashMap<>();proof.put("operation",job.id().toString());proof.put("town",job.town().toString());proof.put("actor",job.actor().toString());proof.put("cents",5000L);proof.put("category","construction");proof.put("source","raid-repair");proof.put("owner","NeverLandTownySiegesPlus");proof.put("reason",RepairTreasury.reason(job));proof.put("kind","DEBIT");proof.put("status","PAID");proof.put("backingAvailable",true);
        ok(RepairTreasury.proof(job,proof)==PAID,"exact paid receipt starts restoration");
        for(String state:List.of("PREPARED","INVOKING","REJECTED","REVIEW")){proof.put("status",state);ok(RepairTreasury.proof(job,proof)==switch(state){case "REJECTED"->REJECTED;case "REVIEW"->REVIEW;default->WAITING;},"durable pending and unknown outcomes never authorize blocks");}
        proof.put("status","PAID");proof.put("backingAvailable",false);ok(RepairTreasury.proof(job,proof)==REVIEW,"refunded debit cannot purchase another repair");proof.put("backingAvailable",true);
        for(String key:List.of("operation","town","actor","cents","category","source","owner","reason","kind","status")){var bad=new HashMap<>(proof);bad.put(key,"wrong");rejected(()->RepairTreasury.proof(job,bad));}
        for(Object amount:List.of("5000.01","NaN","9223372036854775808")){var bad=new HashMap<>(proof);bad.put("cents",amount);rejected(()->RepairTreasury.proof(job,bad));}
        var legacy=new RaidRepairsStore.Job(job.id(),job.town(),job.actor(),job.project(),job.title(),job.fingerprint(),job.level(),job.levelPrice(),job.maximum(),job.lost(),job.cents(),job.before(),job.created(),"REVIEW_BANK","",job.cells());
        rejected(()->RepairTreasury.proof(legacy,proof));ok(!legacy.treasury(),"old unknown calls never become new Treasury intents");
        System.out.println("RepairTreasurySmoke PASS: "+checks+" assertions; exact proof, frozen terms, no false payment or legacy replay");
    }
}

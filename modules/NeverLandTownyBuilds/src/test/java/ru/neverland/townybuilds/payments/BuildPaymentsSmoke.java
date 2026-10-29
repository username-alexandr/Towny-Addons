package ru.neverland.townybuilds.payments;

import java.util.*;
import static ru.neverland.townybuilds.payments.BuildPayment.*;

/** Frozen identity and conservative restoration of payment, effect and audit receipts. */
public final class BuildPaymentsSmoke {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    static void refuses(Runnable action){try{action.run();}catch(RuntimeException expected){checks++;return;}throw new AssertionError("Expected refusal");}
    static Map<String,Object> proof(Terms terms){return new HashMap<>(Map.ofEntries(
        Map.entry("operation",terms.id().toString()),Map.entry("town",terms.town().toString()),Map.entry("actor",terms.actor().toString()),
        Map.entry("category",terms.category()),Map.entry("source",terms.source()),Map.entry("cents",terms.cents()),Map.entry("reason",terms.reason()),
        Map.entry("owner","NeverLandTownyBuilds"),Map.entry("kind","DEBIT"),Map.entry("backingDebit",""),Map.entry("status","PAID"),Map.entry("backingAvailable",true)));
    }
    public static void main(String[] args){
        var commands=new ArrayList<>(List.of("effect town paid"));
        var terms=new Terms(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Mayor",Kind.VIRTUAL,"forge",1,2,550000,"construction","construction","Улучшение кузницы",123456,3,commands,false,List.of());
        commands.clear();check(terms.commands().size()==1,"accepted commands detached from mutable config");
        var prepared=new BuildPayment(terms,Phase.PREPARED);check(prepared.pending()&&prepared.needsAudit(),"new receipt must be durable and audited before funding");
        var paid=prepared.phase(Phase.PAID).auditConfirmed();check(!paid.needsAudit()&&paid.pending(),"audit acknowledgement does not finish purchase");
        var ready=paid.phase(Phase.READY);check(ready.needsAudit(),"each new payment state requires its own audit acknowledgement");
        var applied=ready.phase(Phase.APPLIED);var uncertain=applied.phase(Phase.EFFECTS_PENDING);
        var saved=JSON.fromJson(JSON.toJson(uncertain),BuildPayment.class);check(saved.equals(uncertain)&&saved.phase()==Phase.EFFECTS_PENDING,"unknown command outcome survives restart and is not retried automatically");
        var effect=saved.effectConfirmed();check(effect.effectIndex()==1&&effect.phase()==Phase.APPLIED&&effect.terms().equals(terms),"effect acknowledgement advances one command without changing bank terms");
        var retry=saved.phase(Phase.APPLIED).phase(Phase.EFFECTS_PENDING);
        check(!retry.auditKey().equals(saved.auditKey())&&retry.revision()==saved.revision()+2,"manual unpaid retry retains a new audit occurrence for the same effect");
        check(retry.phaseAt()>saved.phaseAt()&&saved.phaseAt()>applied.phaseAt(),"persisted state timestamps order retries even within one clock tick");
        check(retry.auditConfirmed().phaseAt()==retry.phaseAt()&&retry.auditConfirmed().revision()==retry.revision()&&retry.auditConfirmed().terms().equals(terms),"audit ACK does not create another transition or change bank terms");
        check(retry.phase(retry.phase()).equals(retry),"same-phase observation is idempotent");
        var future=new BuildPayment(terms,Phase.EFFECTS_PENDING,0,"",System.currentTimeMillis()+60000,10).phase(Phase.APPLIED);
        check(future.revision()==11&&future.phaseAt()>System.currentTimeMillis()+59000,"clock moving backwards cannot reorder persisted transitions");
        check(retry.auditOutcome().equals("REVIEW")&&applied.auditOutcome().equals("PENDING"),"Control understands pending/manual reconciliation outcomes");
        var legacyJson=JSON.toJsonTree(saved).getAsJsonObject();legacyJson.remove("phaseAt");legacyJson.remove("revision");
        var legacy=JSON.fromJson(legacyJson,BuildPayment.class);check(legacy.phaseAt()==terms.created()&&legacy.revision()==0&&legacy.phase()==Phase.EFFECTS_PENDING,"older local receipts retain unknown effect and acquire an audit sequence");
        refuses(()->new BuildPayment(terms,Phase.PREPARED,0,"",terms.created()-1,0));
        refuses(applied::skipEmptyBonus);
        var complete=effect.phase(Phase.COMPLETE);check(!complete.pending()&&complete.needsAudit(),"terminal receipt still needs durable audit");check(!complete.auditConfirmed().needsAudit(),"terminal audit acknowledgement survives separately from bank result");
        refuses(()->complete.phase(Phase.PREPARED));refuses(()->prepared.phase(Phase.READY));refuses(()->applied.phase(Phase.PAID));refuses(()->applied.effectConfirmed());
        check(JSON.fromJson(JSON.toJson(complete.auditConfirmed()),BuildPayment.class).equals(complete.auditConfirmed()),"all frozen fields and audit state round-trip");
        check(BuildTreasury.verify(proof(terms),terms).get("status").equals("PAID"),"exact backed bank receipt accepted");
        for(String key:List.of("operation","town","actor","category","source","cents","reason","owner","kind","backingDebit")){
            var wrong=proof(terms);wrong.put(key,key.equals("cents")?terms.cents()+1:"different");refuses(()->BuildTreasury.verify(wrong,terms));
        }
        var refunded=proof(terms);refunded.put("backingAvailable",false);refuses(()->BuildTreasury.verify(refunded,terms));
        var missing=proof(terms);missing.remove("backingAvailable");refuses(()->BuildTreasury.verify(missing,terms));
        var unknown=proof(terms);unknown.put("status","SUCCESS");refuses(()->BuildTreasury.verify(unknown,terms));
        for(String status:List.of("PREPARED","INVOKING","REVIEW","REJECTED")){var receipt=proof(terms);receipt.put("status",status);receipt.put("backingAvailable",false);check(BuildTreasury.verify(receipt,terms).get("status").equals(status),"non-paid status remains non-paid: "+status);}
        var returning=paid.phase(Phase.REFUNDING);check(returning.pending()&&!returning.phase(Phase.REFUNDED).pending(),"refund receipt remains pending until exact core completion");
        refuses(()->returning.phase(Phase.PAID));refuses(()->prepared.phase(Phase.REJECTED).phase(Phase.PREPARED));
        System.out.println("BuildPaymentsSmoke PASS "+checks+" checks; immutable terms, unknown effects, backed exact receipts and audit restart");
    }
}

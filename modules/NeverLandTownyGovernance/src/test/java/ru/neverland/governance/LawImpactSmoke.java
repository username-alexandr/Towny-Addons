package ru.neverland.governance;
import java.util.*;
import ru.neverland.governance.model.*;
import ru.neverland.governance.service.LawReview;

public final class LawImpactSmoke {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static LawDefinition law(String id,String group,Double tax,Boolean percent,double construction,double cost,double xp,int claims){return new LawDefinition(id,id,LawCategory.CIVIC,null,"",group,List.of(),new LawEffects(tax,percent,construction,cost,xp,claims));}
    static void reject(Runnable run,String message){try{run.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        var low=law("low","tax",5d,false,1,1,1,0);var high=law("high","tax",10d,true,1,1,1,0);var subsidy=law("subsidy","build",null,null,.85,1,1,5);var expand=law("expand","build",null,null,1.1,1,1,15);var civic=law("civic","tax",null,null,1,.85,1.1,0);
        var defs=new LinkedHashMap<String,LawDefinition>();for(var l:List.of(low,high,subsidy,expand,civic))defs.put(l.id(),l);var plain=new LawImpact.City(Set.of(),23,false,7,null,null);
        var p=LawImpact.project(plain,defs,subsidy,ProposalAction.ENACT,.1,5);check(p.after().bonusBlocks()==12&&p.proposed().construction()==.85&&plain.laws().isEmpty(),"preview pure and combines current city with proposed subsidy");
        var replace=LawImpact.project(p.after(),defs,expand,ProposalAction.ENACT,.1,5);check(replace.after().bonusBlocks()==22&&replace.removed().equals(List.of("subsidy"))&&replace.proposed().construction()==1.1,"exclusive replacement removes old claims and old multiplier before adding new ones");
        var repeal=LawImpact.project(replace.after(),defs,expand,ProposalAction.REPEAL,.1,5);check(repeal.after().bonusBlocks()==7&&repeal.proposed().construction()==1,"repeal recovers original bonus count and neutral modifier");
        var tax=LawImpact.project(plain,defs,low,ProposalAction.ENACT,.1,5);check(tax.after().tax()==5&&tax.after().baselineTax()==23&&!tax.after().percentage(),"tax proposal freezes original city rate without changing it");
        var percent=LawImpact.project(tax.after(),defs,high,ProposalAction.ENACT,.1,5);check(percent.after().tax()==10&&percent.after().percentage()&&percent.after().baselineTax()==23,"switch fixed to percentage keeps original baseline");
        var untax=LawImpact.project(percent.after(),defs,high,ProposalAction.REPEAL,.1,5);check(untax.after().tax()==23&&!untax.after().percentage()&&untax.after().baselineTax()==null,"repeal restores exact original rate and mode");
        var cross=LawImpact.project(tax.after(),defs,civic,ProposalAction.ENACT,.1,5);check(cross.after().tax()==23&&cross.after().baselineTax()==null&&cross.after().laws().equals(Set.of("civic")),"non-tax exclusive replacement cannot orphan previous law tax");
        var legacy=new LawImpact.City(Set.of("low"),5,false,0,null,null);var unknown=LawImpact.project(legacy,defs,low,ProposalAction.REPEAL,.1,5);check(unknown.after().tax()==5&&!unknown.warnings().isEmpty(),"missing legacy baseline remains explicit and never invents zero rate");
        var capped=LawImpact.project(plain,defs,high,ProposalAction.ENACT,.1,5,new LawImpact.TaxRules(100,7,false,0));check(capped.after().tax()==7&&capped.after().percentage()&&!capped.warnings().isEmpty(),"Towny percentage cap included in exact estimate");
        var negative=LawImpact.project(new LawImpact.City(Set.of("low"),5,false,0,-2d,false),defs,low,ProposalAction.REPEAL,.1,5,new LawImpact.TaxRules(100,100,true,0));check(negative.after().tax()==-2,"legitimate negative Towny baseline is preserved when enabled");
        var disabledNegative=LawImpact.project(new LawImpact.City(Set.of("low"),5,false,0,-2d,false),defs,low,ProposalAction.REPEAL,.1,5,new LawImpact.TaxRules(100,100,false,3));check(disabledNegative.after().tax()==3,"disabled negative Towny rate uses actual default rule");
        var max=law("max","",null,null,100,100,100,0);var all=new HashMap<>(defs);all.put(max.id(),max);check(LawImpact.project(plain,all,max,ProposalAction.ENACT,.1,5).proposed().construction()==5,"combined modifier respects actual cap");
        check(LawReview.lines(replace,id->id).stream().anyMatch(s->s.contains("85% → 110%")),"human comparison includes current-to-proposed complete multiplier");
        check(LawReview.lines(capped,id->id).stream().anyMatch(s->s.contains("23 монет с жителя → 7%")),"tax mode change is explicit in player review");
        check(p.fingerprint().equals(LawImpact.project(plain,new HashMap<>(defs),subsidy,ProposalAction.ENACT,.1,5).fingerprint()),"fingerprint independent of definition map insertion order");
        check(!p.fingerprint().equals(LawImpact.project(new LawImpact.City(Set.of(),24,false,7,null,null),defs,subsidy,ProposalAction.ENACT,.1,5).fingerprint()),"changed current city rate invalidates quoted preview");
        check(!capped.fingerprint().equals(LawImpact.project(plain,defs,high,ProposalAction.ENACT,.1,5,new LawImpact.TaxRules(100,8,false,0)).fingerprint()),"changed Towny limits invalidate preview");
        reject(()->LawImpact.project(tax.after(),defs,low,ProposalAction.ENACT,.1,5),"already enacted cannot be enacted twice");reject(()->LawImpact.project(plain,defs,low,ProposalAction.REPEAL,.1,5),"already repealed cannot be removed twice");
        reject(()->LawImpact.validate(new LawEffects(Double.NaN,false,1,1,1,0)),"NaN tax rejected");reject(()->LawImpact.validate(new LawEffects(null,null,Double.POSITIVE_INFINITY,1,1,0)),"infinite multiplier rejected");reject(()->LawImpact.validate(new LawEffects(1d,null,1,1,1,0)),"half tax definition rejected");reject(()->LawImpact.multipliers(Set.of(),defs,5,.1),"inverted caps rejected");
        var deleted=new LawImpact.City(Set.of("removed"),23,false,7,null,null);check(!LawImpact.project(deleted,defs,subsidy,ProposalAction.ENACT,.1,5).warnings().isEmpty(),"unknown saved law is retained and flagged rather than silently deleted");
        check(LawImpact.project(new LawImpact.City(Set.of(),23,false,-5,null,null),defs,civic,ProposalAction.ENACT,.1,5).after().bonusBlocks()==-5,"non-construction law preserves existing negative Towny claim adjustment");
        for(int i=0;i<10000;i++){int own=i%200;var base=new LawImpact.City(Set.of(),23,false,own,null,null);var added=LawImpact.project(base,defs,subsidy,ProposalAction.ENACT,.1,5);var undone=LawImpact.project(added.after(),defs,subsidy,ProposalAction.REPEAL,.1,5);if(!undone.after().equals(base))throw new AssertionError("law cycle did not conserve baseline");}
        System.out.println("LawImpactSmoke PASS "+checks+" checks and 10000 conserved city-law cycles");
    }
}

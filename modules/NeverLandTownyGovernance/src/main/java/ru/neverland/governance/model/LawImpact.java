package ru.neverland.governance.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** One pure projection for the review cards, live modifiers and actual enact/repeal effects. */
public final class LawImpact {
    private LawImpact(){}
    public record City(Set<String> laws,double tax,boolean percentage,int bonusBlocks,Double baselineTax,Boolean baselinePercentage){
        public City{laws=Collections.unmodifiableSet(new LinkedHashSet<>(laws));if(!Double.isFinite(tax)||baselineTax!=null&&!Double.isFinite(baselineTax)||(baselineTax==null)!=(baselinePercentage==null))throw new IllegalArgumentException("Некорректное состояние городских законов");}
    }
    public record TaxRules(double maximumFixed,double maximumPercentage,boolean negativeAllowed,double defaultTax){public TaxRules{if(!Double.isFinite(maximumFixed)||maximumFixed<0||!Double.isFinite(maximumPercentage)||maximumPercentage<0||!Double.isFinite(defaultTax))throw new IllegalArgumentException("Некорректные ограничения налога Towny");}}
    public record Multipliers(double construction,double ideologyCost,double ideologyExperience){}
    public record Projection(City before,City after,Multipliers current,Multipliers proposed,List<String> removed,List<String> warnings,String fingerprint){
        public Projection{removed=List.copyOf(removed);warnings=List.copyOf(warnings);}
    }
    public static void validate(LawEffects e){
        if(e==null||(e.taxAmount()==null)!=(e.taxPercentage()==null)||e.taxAmount()!=null&&(!Double.isFinite(e.taxAmount())||e.taxAmount()<0||e.taxAmount()>9_000_000_000_000d)||e.bonusClaimBlocks()<-1_000_000||e.bonusClaimBlocks()>1_000_000)throw new IllegalArgumentException("Некорректные эффекты закона");
        for(double value:new double[]{e.constructionCostMultiplier(),e.ideologyCostMultiplier(),e.ideologyExperienceMultiplier()})if(!Double.isFinite(value)||value<=0||value>100)throw new IllegalArgumentException("Множитель закона должен быть положительным и не выше 100");
    }
    public static Multipliers multipliers(Set<String> active,Map<String,LawDefinition> definitions,double minimum,double maximum){
        if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<=0||maximum<minimum||maximum>100)throw new IllegalArgumentException("Некорректные пределы влияния законов");
        BigDecimal construction=BigDecimal.ONE,cost=BigDecimal.ONE,experience=BigDecimal.ONE;
        for(String id:new TreeSet<>(active)){var law=definitions.get(id);if(law==null)continue;var e=law.effects();validate(e);construction=construction.multiply(BigDecimal.valueOf(e.constructionCostMultiplier()));cost=cost.multiply(BigDecimal.valueOf(e.ideologyCostMultiplier()));experience=experience.multiply(BigDecimal.valueOf(e.ideologyExperienceMultiplier()));}
        return new Multipliers(bound(construction,minimum,maximum),bound(cost,minimum,maximum),bound(experience,minimum,maximum));
    }
    private static double bound(BigDecimal value,double min,double max){return value.max(BigDecimal.valueOf(min)).min(BigDecimal.valueOf(max)).doubleValue();}
    public static Projection project(City city,Map<String,LawDefinition> definitions,LawDefinition law,ProposalAction action,double min,double max){
        return project(city,definitions,law,action,min,max,new TaxRules(9_000_000_000_000d,100,true,0));
    }
    public static Projection project(City city,Map<String,LawDefinition> definitions,LawDefinition law,ProposalAction action,double min,double max,TaxRules taxRules){
        Objects.requireNonNull(action);if(law==null||!law.equals(definitions.get(law.id())))throw new IllegalArgumentException("Закон больше не доступен");validate(law.effects());
        if(city.laws().contains(law.id())==(action==ProposalAction.ENACT))throw new IllegalArgumentException(action==ProposalAction.ENACT?"Закон уже действует":"Закон уже отменён");
        var next=new LinkedHashSet<>(city.laws());var removed=new ArrayList<String>();var warnings=new ArrayList<String>();
        if(action==ProposalAction.REPEAL)removed.add(law.id());
        else if(!law.exclusiveGroup().isBlank())for(String id:new TreeSet<>(city.laws())){var active=definitions.get(id);if(active!=null&&law.exclusiveGroup().equalsIgnoreCase(active.exclusiveGroup()))removed.add(id);}
        long blocks=city.bonusBlocks();for(String id:removed){var old=definitions.get(id);validate(old.effects());if(old.effects().bonusClaimBlocks()!=0)blocks=Math.max(0,blocks-old.effects().bonusClaimBlocks());next.remove(id);}
        double tax=city.tax();boolean percentage=city.percentage();Double baseline=city.baselineTax();Boolean baselinePercentage=city.baselinePercentage();
        if(action==ProposalAction.ENACT){next.add(law.id());if(law.effects().bonusClaimBlocks()!=0)blocks=Math.max(0,blocks+law.effects().bonusClaimBlocks());if(law.effects().changesTax()){if(baseline==null){baseline=city.tax();baselinePercentage=city.percentage();}tax=law.effects().taxAmount();percentage=law.effects().taxPercentage();}}
        boolean hasTax=next.stream().map(definitions::get).filter(Objects::nonNull).anyMatch(l->l.effects().changesTax());
        if(!hasTax&&baseline!=null){tax=baseline;percentage=baselinePercentage;baseline=null;baselinePercentage=null;}
        double quotedTax=tax;tax=Math.min(tax,percentage?taxRules.maximumPercentage():taxRules.maximumFixed());if(tax<0&&!taxRules.negativeAllowed())tax=taxRules.defaultTax();if(tax!=quotedTax)warnings.add("Ставка ограничена действующими настройками Towny.");
        if(blocks>Integer.MAX_VALUE)throw new IllegalArgumentException("Превышено допустимое число дополнительных участков");
        for(String id:next)if(!definitions.containsKey(id))warnings.add("Среди действующих законов есть удалённый из настроек. Его влияние в оценку не включено.");
        if(city.baselineTax()==null&&action==ProposalAction.REPEAL&&law.effects().changesTax())warnings.add("Прежняя ставка не сохранена; текущий городской налог останется без изменения.");
        var after=new City(next,tax,percentage,(int)blocks,baseline,baselinePercentage);var beforeEffects=multipliers(city.laws(),definitions,min,max);var afterEffects=multipliers(next,definitions,min,max);
        String fingerprint=fingerprint(city,definitions,law.id(),action,min,max,taxRules);
        return new Projection(city,after,beforeEffects,afterEffects,removed,warnings.stream().distinct().toList(),fingerprint);
    }
    private static String fingerprint(City city,Map<String,LawDefinition> definitions,String law,ProposalAction action,double min,double max,TaxRules taxRules){
        var text=new StringBuilder().append(new TreeSet<>(city.laws())).append('|').append(city.tax()).append('|').append(city.percentage()).append('|').append(city.bonusBlocks()).append('|').append(city.baselineTax()).append('|').append(city.baselinePercentage()).append('|').append(law).append('|').append(action).append('|').append(min).append('|').append(max).append('|').append(taxRules);
        new TreeMap<>(definitions).forEach((id,d)->text.append('|').append(id).append(':').append(d.name()).append(':').append(d.exclusiveGroup()).append(':').append(d.effects()));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}

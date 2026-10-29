package ru.neverland.reputation.service;

import java.io.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import ru.neverland.core.ReputationOutcome;
import ru.neverland.reputation.model.*;
import static ru.neverland.reputation.model.ReputationLedger.*;

public final class ReputationProfiles {
    public enum Rule {
        PLAYER_ENDORSE(ReputationAspect.DIPLOMATIC,5,"Положительный отзыв игрока"),
        PLAYER_DENOUNCE(ReputationAspect.DIPLOMATIC,-3,"Отрицательный отзыв игрока"),
        SUPPLY_COMPLETED(ReputationAspect.TRADE,3,"Поставка выполнена"),
        SUPPLY_CANCELLED(ReputationAspect.TRADE,-20,"Действующий договор расторгнут"),
        SUPPLY_MISSED(ReputationAspect.TRADE,-10,"Просрочена поставка: нет товара"),
        MUNICIPAL_SUCCESS(ReputationAspect.TRADE,3,"Контракт компании выполнен"),
        MUNICIPAL_FAILED(ReputationAspect.TRADE,-15,"Контракт компании не выполнен"),
        TREATY_HONOURED(ReputationAspect.DIPLOMATIC,5,"Договор соблюдён весь срок"),
        RAID_VICTORY(ReputationAspect.MILITARY,10,"Набег отражён"),
        RAID_DEFEAT(ReputationAspect.MILITARY,-10,"Оборона от набега провалена");
        public final ReputationAspect aspect; public final int points; public final String title;
        Rule(ReputationAspect aspect,int points,String title) { this.aspect=aspect;this.points=points;this.title=title; }
    }
    private final ProfileRepository repository;
    private Map<Rule,Integer> points;
    private int dailyCap;
    private double maximum, discount;
    private ReputationLedger.State explanationState;private ReputationExplanation explanationIndex;
    public ReputationProfiles(ProfileRepository repository, ConfigurationSection config) { this.repository=repository;reload(config); }
    public void reload(ConfigurationSection config) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        var next = new EnumMap<Rule,Integer>(Rule.class);
        for (var rule : Rule.values()) { int value=config.getInt("profiles.rules."+rule.name().toLowerCase(Locale.ROOT),rule.points); if (Math.abs((long)value)>1000 || (value!=0 && Integer.signum(value)!=Integer.signum(rule.points))) throw new IllegalArgumentException("Некорректные очки "+rule);next.put(rule,value); }
        int cap=config.getInt("profiles.positive-daily-cap",30); if(cap<0||cap>1000)throw new IllegalArgumentException("Неверный дневной лимит репутации");
        double max=config.getDouble("profiles.trade-fees.maximum-multiplier",2), off=config.getDouble("profiles.trade-fees.maximum-discount-percent",15)/100;
        ReputationLedger.feeMultiplier(0,max,off); points=Map.copyOf(next);dailyCap=cap;maximum=max;discount=off;
    } }
    public boolean healthy() { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return repository.healthy(); } }
    private void gate() { if(!healthy())throw new IllegalStateException("Хранилище репутации недоступно"); }
    public Profile get(ReputationScope scope,UUID subject) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { gate();return repository.state().profile(new Subject(scope,subject)); } }
    public Map<String,Object> snapshot(String scope,UUID subject) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) { var s=ReputationScope.valueOf(scope.toUpperCase(Locale.ROOT));var p=get(s,subject);return Map.of("scope",s.name(),"subject",subject.toString(),"diplomatic",p.diplomatic(),"trade",p.trade(),"military",p.military(),"tradeFeeMultiplier",fee(p.trade())); } }
    public ReputationExplanation.Explanation explain(ReputationScope scope,UUID subject,ReputationAspect aspect,long now,int page,int size){try(var guard=ru.neverland.core.SuiteState.guard()){gate();var state=repository.state();if(explanationIndex==null||explanationState!=state){explanationIndex=new ReputationExplanation(state);explanationState=state;}return explanationIndex.explain(new Subject(scope,subject),aspect,now,page,size);}}
    public Map<String,Object> explanation(String scope,UUID subject,String aspect,int page){try(var guard=ru.neverland.core.SuiteState.guard()){var result=explain(ReputationScope.valueOf(scope.toUpperCase(Locale.ROOT)),subject,ReputationAspect.parse(aspect),System.currentTimeMillis(),page,28);var map=new LinkedHashMap<String,Object>();map.put("available",true);map.put("score",result.score());map.put("initial",result.initial());map.put("evidence",result.evidence());map.put("positive",result.positive());map.put("negative",result.negative());map.put("limited",result.limited());map.put("recent7",result.recent7());map.put("days7to30",result.days7to30());map.put("older30",result.older30());map.put("lastAt",result.lastAt());map.put("count",result.count());map.put("consistent",result.consistent());map.put("page",result.page());map.put("pages",result.pages());map.put("rows",result.rows().stream().map(row->Map.<String,Object>of("receipt",row.receipt(),"at",row.at(),"ageDays",row.ageDays(),"future",row.future(),"rule",row.rule(),"context",row.context(),"requested",row.requested(),"applied",row.applied())).toList());return Map.copyOf(map);}}
    public double fee(int score) { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { gate();return ReputationLedger.feeMultiplier(score,maximum,discount); } }
    public double tradeFeeMultiplier(UUID town) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return fee(get(ReputationScope.TOWN,town).trade()); } }
    public String record(String scope,UUID subject,String rule,String receipt,long at,String context) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {
        gate(); Rule policy=Rule.valueOf(rule);var outcome=new ReputationOutcome(receipt,scope.toUpperCase(Locale.ROOT),subject,rule,at,context);
        // Outboxes may be old, but future receipts would bypass daily limits.
        if(at>System.currentTimeMillis()+300_000L)throw new IllegalArgumentException("Событие репутации датировано будущим");
        return commit(ReputationLedger.apply(repository.state(),outcome,policy.aspect,points.get(policy),dailyCap));
    } }
    public String administer(ReputationScope scope,UUID subject,ReputationAspect aspect,int value,boolean set,String actor) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {
        gate();int current=get(scope,subject).score(aspect);
        int bounded=(int)Math.max(MIN,Math.min(MAX,set?(long)value:(long)current+value));
        var outcome=new ReputationOutcome("admin:"+UUID.randomUUID(),scope.name(),subject,"ADMIN",System.currentTimeMillis(),"Администратор: "+actor);
        return commit(ReputationLedger.apply(repository.state(),outcome,aspect,bounded-current,Integer.MAX_VALUE));
    } }
    private String commit(Result result) { if(result.status().equals("APPLIED"))try{repository.commit(result.state());}catch(IOException ex){throw new UncheckedIOException(ex);}return result.status(); }
    public static String reason(String rule) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { try{return Rule.valueOf(rule).title;}catch(IllegalArgumentException ex){return "ADMIN".equals(rule)?"Изменение администратором":rule;} } }
}

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
    public ReputationProfiles(ProfileRepository repository, ConfigurationSection config) { this.repository=repository;reload(config); }
    public void reload(ConfigurationSection config) {
        var next = new EnumMap<Rule,Integer>(Rule.class);
        for (var rule : Rule.values()) { int value=config.getInt("profiles.rules."+rule.name().toLowerCase(Locale.ROOT),rule.points); if (Math.abs((long)value)>1000 || (value!=0 && Integer.signum(value)!=Integer.signum(rule.points))) throw new IllegalArgumentException("Некорректные очки "+rule);next.put(rule,value); }
        int cap=config.getInt("profiles.positive-daily-cap",30); if(cap<0||cap>1000)throw new IllegalArgumentException("Неверный дневной лимит репутации");
        double max=config.getDouble("profiles.trade-fees.maximum-multiplier",2), off=config.getDouble("profiles.trade-fees.maximum-discount-percent",15)/100;
        ReputationLedger.feeMultiplier(0,max,off); points=Map.copyOf(next);dailyCap=cap;maximum=max;discount=off;
    }
    public boolean healthy() { return repository.healthy(); }
    private void gate() { if(!healthy())throw new IllegalStateException("Хранилище репутации недоступно"); }
    public Profile get(ReputationScope scope,UUID subject) { gate();return repository.state().profile(new Subject(scope,subject)); }
    public Map<String,Object> snapshot(String scope,UUID subject) { var s=ReputationScope.valueOf(scope.toUpperCase(Locale.ROOT));var p=get(s,subject);return Map.of("scope",s.name(),"subject",subject.toString(),"diplomatic",p.diplomatic(),"trade",p.trade(),"military",p.military(),"tradeFeeMultiplier",fee(p.trade())); }
    public double fee(int score) { gate();return ReputationLedger.feeMultiplier(score,maximum,discount); }
    public double tradeFeeMultiplier(UUID town) { return fee(get(ReputationScope.TOWN,town).trade()); }
    public String record(String scope,UUID subject,String rule,String receipt,long at,String context) {
        gate(); Rule policy=Rule.valueOf(rule);var outcome=new ReputationOutcome(receipt,scope.toUpperCase(Locale.ROOT),subject,rule,at,context);
        // Outboxes may be old, but future receipts would bypass daily limits.
        if(at>System.currentTimeMillis()+300_000L)throw new IllegalArgumentException("Событие репутации датировано будущим");
        return commit(ReputationLedger.apply(repository.state(),outcome,policy.aspect,points.get(policy),dailyCap));
    }
    public String administer(ReputationScope scope,UUID subject,ReputationAspect aspect,int value,boolean set,String actor) {
        gate();int current=get(scope,subject).score(aspect);
        int bounded=(int)Math.max(MIN,Math.min(MAX,set?(long)value:(long)current+value));
        var outcome=new ReputationOutcome("admin:"+UUID.randomUUID(),scope.name(),subject,"ADMIN",System.currentTimeMillis(),"Администратор: "+actor);
        return commit(ReputationLedger.apply(repository.state(),outcome,aspect,bounded-current,Integer.MAX_VALUE));
    }
    private String commit(Result result) { if(result.status().equals("APPLIED"))try{repository.commit(result.state());}catch(IOException ex){throw new UncheckedIOException(ex);}return result.status(); }
    public static String reason(String rule) { try{return Rule.valueOf(rule).title;}catch(IllegalArgumentException ex){return "ADMIN".equals(rule)?"Изменение администратором":rule;} }
}

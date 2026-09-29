package ru.neverland.mintcontracts.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ActiveContract {
    private final UUID id;
    private final UUID townId;
    private final String templateId;
    private final long createdAt;
    private ru.neverland.core.ActivityTimer timer;
    private final int goal;
    private final double escrow;
    private final Map<UUID, Integer> contributions = new LinkedHashMap<>();
    private int progress;
    private UUID companyId;
    private ContractStatus settlementStatus;
    private long settlementPayout, settlementRefund;
    private ContractDefinition snapshot;
    private WorkArea area;
    private final Map<String,WorkProof> proofs=new LinkedHashMap<>();
    private boolean funded=true;

    public ActiveContract(UUID id, UUID townId, String templateId, long createdAt, long expiresAt,
                          int progress, int goal, double escrow, Map<UUID, Integer> contributions) {
        this.id = id; this.townId = townId; this.templateId = templateId; this.createdAt = createdAt;
        this.timer=ru.neverland.core.ActivityTimer.running(createdAt,expiresAt); this.progress = Math.max(0, progress); this.goal = Math.max(1, goal);
        this.escrow = Math.max(0, escrow); this.contributions.putAll(contributions);
    }
    public UUID id() { synchronized (ru.neverland.core.SuiteState.lock()) { return id; } }
    public ContractDefinition snapshot(){ synchronized (ru.neverland.core.SuiteState.lock()) {return snapshot;} }
    public void snapshot(ContractDefinition value){ synchronized (ru.neverland.core.SuiteState.lock()) {if(snapshot!=null)throw new IllegalStateException("Условия уже сохранены");if(value.goal()!=goal||Math.round(value.reward()*100)!=Math.round(escrow*100)||!value.id().equals(templateId))throw new IllegalArgumentException("Условия не совпадают с резервом");snapshot=value;} }
    public boolean funded(){ synchronized (ru.neverland.core.SuiteState.lock()) {return funded;} }
    public void funded(boolean value){ synchronized (ru.neverland.core.SuiteState.lock()) {funded=value;} }
    public WorkArea area(){ synchronized (ru.neverland.core.SuiteState.lock()) {return area;} }
    public void area(WorkArea value){ synchronized (ru.neverland.core.SuiteState.lock()) {if(area!=null)throw new IllegalStateException("Участок уже задан");if(value.required().size()!=goal)throw new IllegalArgumentException("Площадь не совпадает с целью");area=value;} }
    public Map<String,WorkProof> proofs(){ synchronized (ru.neverland.core.SuiteState.lock()) {return Map.copyOf(proofs);} }
    public void proof(String key,WorkProof proof){ synchronized (ru.neverland.core.SuiteState.lock()) {if(area==null||!area.required().contains(key)||settlementStatus!=null)throw new IllegalArgumentException("Недопустимая точка выполнения");proofs.put(key,proof);} }
    public void removeProof(String key){ synchronized (ru.neverland.core.SuiteState.lock()) {if(settlementStatus==null)proofs.remove(key);} }
    public void replaceWorkProgress(Map<UUID,Integer> values){ synchronized (ru.neverland.core.SuiteState.lock()) {if(settlementStatus!=null)throw new IllegalStateException("Расчёт уже начат");long sum=values.values().stream().mapToLong(Integer::longValue).sum();if(values.values().stream().anyMatch(n->n<=0)||sum>goal)throw new IllegalArgumentException("Некорректный прогресс");contributions.clear();contributions.putAll(values);progress=(int)sum;} }
    public UUID companyId() { synchronized (ru.neverland.core.SuiteState.lock()) { return companyId; } }
    public void companyId(UUID id) { synchronized (ru.neverland.core.SuiteState.lock()) { if(progress!=0||!proofs.isEmpty()||settlementStatus!=null)throw new IllegalStateException("Начатый контракт нельзя передать");companyId=id; } }
    public void restoreCompany(UUID id) { synchronized (ru.neverland.core.SuiteState.lock()) { companyId=id; } }
    public ContractStatus settlementStatus() { synchronized (ru.neverland.core.SuiteState.lock()) { return settlementStatus; } }
    public long settlementPayout() { synchronized (ru.neverland.core.SuiteState.lock()) { return settlementPayout; } }
    public long settlementRefund() { synchronized (ru.neverland.core.SuiteState.lock()) { return settlementRefund; } }
    public void settlement(ContractStatus status,long payout,long refund) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if(!funded||status==null||payout<0||refund<0||Math.addExact(payout,refund)!=Math.round(escrow*100))throw new IllegalArgumentException("Некорректный расчёт контракта");
        if(settlementStatus!=null&&(settlementStatus!=status||settlementPayout!=payout||settlementRefund!=refund))throw new IllegalArgumentException("Условия расчёта уже зафиксированы");
        settlementStatus=status;settlementPayout=payout;settlementRefund=refund;
    } }
    public UUID townId() { synchronized (ru.neverland.core.SuiteState.lock()) { return townId; } }
    public String templateId() { synchronized (ru.neverland.core.SuiteState.lock()) { return templateId; } }
    public long createdAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return createdAt; } }
    public long expiresAt() { synchronized (ru.neverland.core.SuiteState.lock()) { return timer.deadline(); } }
    public int progress() { synchronized (ru.neverland.core.SuiteState.lock()) { return progress; } }
    public int goal() { synchronized (ru.neverland.core.SuiteState.lock()) { return goal; } }
    public double escrow() { synchronized (ru.neverland.core.SuiteState.lock()) { return escrow; } }
    public Map<UUID, Integer> contributions() { synchronized (ru.neverland.core.SuiteState.lock()) { return Map.copyOf(contributions); } }
    public int add(UUID playerId, int amount) { synchronized (ru.neverland.core.SuiteState.lock()) {
        if(settlementStatus!=null)return 0;
        int accepted = Math.min(Math.max(0, amount), goal - progress);
        if (accepted <= 0) return 0;
        progress += accepted;
        contributions.merge(playerId, accepted, Integer::sum);
        return accepted;
    } }
    public boolean completed() { synchronized (ru.neverland.core.SuiteState.lock()) { return progress >= goal; } }
    public double ratio() { synchronized (ru.neverland.core.SuiteState.lock()) { return Math.min(1, (double) progress / goal); } }
    public String shortId() { synchronized (ru.neverland.core.SuiteState.lock()) { return id.toString().substring(0, 8); } }
    public ru.neverland.core.ActivityTimer timer(){ synchronized (ru.neverland.core.SuiteState.lock()) {return timer;} }
    public void timer(ru.neverland.core.ActivityTimer value){ synchronized (ru.neverland.core.SuiteState.lock()) {timer=java.util.Objects.requireNonNull(value);} }
    private boolean administrativeCancellation;
    public boolean administrativeCancellation(){ synchronized (ru.neverland.core.SuiteState.lock()) {return administrativeCancellation;} }
    public void administrativeCancellation(boolean value){ synchronized (ru.neverland.core.SuiteState.lock()) {administrativeCancellation=value;} }
}

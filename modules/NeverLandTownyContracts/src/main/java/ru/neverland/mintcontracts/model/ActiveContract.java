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
    public UUID id() { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) { return id; } }
    public ContractDefinition snapshot(){ try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {return snapshot;} }
    public void snapshot(ContractDefinition value){ try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {if(snapshot!=null)throw new IllegalStateException("Условия уже сохранены");if(value.goal()!=goal||Math.round(value.reward()*100)!=Math.round(escrow*100)||!value.id().equals(templateId))throw new IllegalArgumentException("Условия не совпадают с резервом");snapshot=value;} }
    public boolean funded(){ try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {return funded;} }
    public void funded(boolean value){ try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) {funded=value;} }
    public WorkArea area(){ try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) {return area;} }
    public void area(WorkArea value){ try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) {if(area!=null)throw new IllegalStateException("Участок уже задан");if(value.required().size()!=goal)throw new IllegalArgumentException("Площадь не совпадает с целью");area=value;} }
    public Map<String,WorkProof> proofs(){ try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) {return Map.copyOf(proofs);} }
    public void proof(String key,WorkProof proof){ try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) {if(area==null||!area.required().contains(key)||settlementStatus!=null)throw new IllegalArgumentException("Недопустимая точка выполнения");proofs.put(key,proof);} }
    public void removeProof(String key){ try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {if(settlementStatus==null)proofs.remove(key);} }
    public void replaceWorkProgress(Map<UUID,Integer> values){ try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) {if(settlementStatus!=null)throw new IllegalStateException("Расчёт уже начат");long sum=values.values().stream().mapToLong(Integer::longValue).sum();if(values.values().stream().anyMatch(n->n<=0)||sum>goal)throw new IllegalArgumentException("Некорректный прогресс");contributions.clear();contributions.putAll(values);progress=(int)sum;} }
    public UUID companyId() { try (var suiteGuard12 = ru.neverland.core.SuiteState.guard()) { return companyId; } }
    public void companyId(UUID id) { try (var suiteGuard13 = ru.neverland.core.SuiteState.guard()) { if(progress!=0||!proofs.isEmpty()||settlementStatus!=null)throw new IllegalStateException("Начатый контракт нельзя передать");companyId=id; } }
    public void restoreCompany(UUID id) { try (var suiteGuard14 = ru.neverland.core.SuiteState.guard()) { companyId=id; } }
    public ContractStatus settlementStatus() { try (var suiteGuard15 = ru.neverland.core.SuiteState.guard()) { return settlementStatus; } }
    public long settlementPayout() { try (var suiteGuard16 = ru.neverland.core.SuiteState.guard()) { return settlementPayout; } }
    public long settlementRefund() { try (var suiteGuard17 = ru.neverland.core.SuiteState.guard()) { return settlementRefund; } }
    public void settlement(ContractStatus status,long payout,long refund) { try (var suiteGuard18 = ru.neverland.core.SuiteState.guard()) {
        if(!funded||status==null||payout<0||refund<0||Math.addExact(payout,refund)!=Math.round(escrow*100))throw new IllegalArgumentException("Некорректный расчёт контракта");
        if(settlementStatus!=null&&(settlementStatus!=status||settlementPayout!=payout||settlementRefund!=refund))throw new IllegalArgumentException("Условия расчёта уже зафиксированы");
        settlementStatus=status;settlementPayout=payout;settlementRefund=refund;
    } }
    public UUID townId() { try (var suiteGuard19 = ru.neverland.core.SuiteState.guard()) { return townId; } }
    public String templateId() { try (var suiteGuard20 = ru.neverland.core.SuiteState.guard()) { return templateId; } }
    public long createdAt() { try (var suiteGuard21 = ru.neverland.core.SuiteState.guard()) { return createdAt; } }
    public long expiresAt() { try (var suiteGuard22 = ru.neverland.core.SuiteState.guard()) { return timer.deadline(); } }
    public int progress() { try (var suiteGuard23 = ru.neverland.core.SuiteState.guard()) { return progress; } }
    public int goal() { try (var suiteGuard24 = ru.neverland.core.SuiteState.guard()) { return goal; } }
    public double escrow() { try (var suiteGuard25 = ru.neverland.core.SuiteState.guard()) { return escrow; } }
    public Map<UUID, Integer> contributions() { try (var suiteGuard26 = ru.neverland.core.SuiteState.guard()) { return Map.copyOf(contributions); } }
    public int add(UUID playerId, int amount) { try (var suiteGuard27 = ru.neverland.core.SuiteState.guard()) {
        if(settlementStatus!=null)return 0;
        int accepted = Math.min(Math.max(0, amount), goal - progress);
        if (accepted <= 0) return 0;
        progress += accepted;
        contributions.merge(playerId, accepted, Integer::sum);
        return accepted;
    } }
    public boolean completed() { try (var suiteGuard28 = ru.neverland.core.SuiteState.guard()) { return progress >= goal; } }
    public double ratio() { try (var suiteGuard29 = ru.neverland.core.SuiteState.guard()) { return Math.min(1, (double) progress / goal); } }
    public String shortId() { try (var suiteGuard30 = ru.neverland.core.SuiteState.guard()) { return id.toString().substring(0, 8); } }
    public ru.neverland.core.ActivityTimer timer(){ try (var suiteGuard31 = ru.neverland.core.SuiteState.guard()) {return timer;} }
    public void timer(ru.neverland.core.ActivityTimer value){ try (var suiteGuard32 = ru.neverland.core.SuiteState.guard()) {timer=java.util.Objects.requireNonNull(value);} }
    private boolean administrativeCancellation;
    public boolean administrativeCancellation(){ try (var suiteGuard33 = ru.neverland.core.SuiteState.guard()) {return administrativeCancellation;} }
    public void administrativeCancellation(boolean value){ try (var suiteGuard34 = ru.neverland.core.SuiteState.guard()) {administrativeCancellation=value;} }
}

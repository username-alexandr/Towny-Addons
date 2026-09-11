package ru.neverland.mintcontracts.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ActiveContract {
    private final UUID id;
    private final UUID townId;
    private final String templateId;
    private final long createdAt;
    private final long expiresAt;
    private final int goal;
    private final double escrow;
    private final Map<UUID, Integer> contributions = new LinkedHashMap<>();
    private int progress;
    private UUID companyId;
    private ContractStatus settlementStatus;
    private long settlementPayout, settlementRefund;

    public ActiveContract(UUID id, UUID townId, String templateId, long createdAt, long expiresAt,
                          int progress, int goal, double escrow, Map<UUID, Integer> contributions) {
        this.id = id; this.townId = townId; this.templateId = templateId; this.createdAt = createdAt;
        this.expiresAt = expiresAt; this.progress = Math.max(0, progress); this.goal = Math.max(1, goal);
        this.escrow = Math.max(0, escrow); this.contributions.putAll(contributions);
    }
    public UUID id() { return id; }
    public UUID companyId() { return companyId; }
    public void companyId(UUID id) { if(progress!=0||settlementStatus!=null)throw new IllegalStateException("Начатый контракт нельзя передать");companyId=id; }
    public void restoreCompany(UUID id) { companyId=id; }
    public ContractStatus settlementStatus() { return settlementStatus; }
    public long settlementPayout() { return settlementPayout; }
    public long settlementRefund() { return settlementRefund; }
    public void settlement(ContractStatus status,long payout,long refund) {
        if(companyId==null||status==null||payout<0||refund<0||Math.addExact(payout,refund)!=Math.round(escrow*100))throw new IllegalArgumentException("Некорректный расчёт компании");
        if(settlementStatus!=null&&(settlementStatus!=status||settlementPayout!=payout||settlementRefund!=refund))throw new IllegalArgumentException("Условия расчёта уже зафиксированы");
        settlementStatus=status;settlementPayout=payout;settlementRefund=refund;
    }
    public UUID townId() { return townId; }
    public String templateId() { return templateId; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public int progress() { return progress; }
    public int goal() { return goal; }
    public double escrow() { return escrow; }
    public Map<UUID, Integer> contributions() { return Map.copyOf(contributions); }
    public int add(UUID playerId, int amount) {
        if(settlementStatus!=null)return 0;
        int accepted = Math.min(Math.max(0, amount), goal - progress);
        if (accepted <= 0) return 0;
        progress += accepted;
        contributions.merge(playerId, accepted, Integer::sum);
        return accepted;
    }
    public boolean completed() { return progress >= goal; }
    public double ratio() { return Math.min(1, (double) progress / goal); }
    public String shortId() { return id.toString().substring(0, 8); }
}

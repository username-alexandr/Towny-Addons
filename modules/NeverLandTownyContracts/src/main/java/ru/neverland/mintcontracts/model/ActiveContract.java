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
    private ContractDefinition snapshot;
    private WorkArea area;
    private final Map<String,WorkProof> proofs=new LinkedHashMap<>();
    private boolean funded=true;

    public ActiveContract(UUID id, UUID townId, String templateId, long createdAt, long expiresAt,
                          int progress, int goal, double escrow, Map<UUID, Integer> contributions) {
        this.id = id; this.townId = townId; this.templateId = templateId; this.createdAt = createdAt;
        this.expiresAt = expiresAt; this.progress = Math.max(0, progress); this.goal = Math.max(1, goal);
        this.escrow = Math.max(0, escrow); this.contributions.putAll(contributions);
    }
    public UUID id() { return id; }
    public ContractDefinition snapshot(){return snapshot;}
    public void snapshot(ContractDefinition value){if(snapshot!=null)throw new IllegalStateException("Условия уже сохранены");if(value.goal()!=goal||Math.round(value.reward()*100)!=Math.round(escrow*100)||!value.id().equals(templateId))throw new IllegalArgumentException("Условия не совпадают с резервом");snapshot=value;}
    public boolean funded(){return funded;}
    public void funded(boolean value){funded=value;}
    public WorkArea area(){return area;}
    public void area(WorkArea value){if(area!=null)throw new IllegalStateException("Участок уже задан");if(value.required().size()!=goal)throw new IllegalArgumentException("Площадь не совпадает с целью");area=value;}
    public Map<String,WorkProof> proofs(){return Map.copyOf(proofs);}
    public void proof(String key,WorkProof proof){if(area==null||!area.required().contains(key)||settlementStatus!=null)throw new IllegalArgumentException("Недопустимая точка выполнения");proofs.put(key,proof);}
    public void removeProof(String key){if(settlementStatus==null)proofs.remove(key);}
    public void replaceWorkProgress(Map<UUID,Integer> values){if(settlementStatus!=null)throw new IllegalStateException("Расчёт уже начат");long sum=values.values().stream().mapToLong(Integer::longValue).sum();if(values.values().stream().anyMatch(n->n<=0)||sum>goal)throw new IllegalArgumentException("Некорректный прогресс");contributions.clear();contributions.putAll(values);progress=(int)sum;}
    public UUID companyId() { return companyId; }
    public void companyId(UUID id) { if(progress!=0||!proofs.isEmpty()||settlementStatus!=null)throw new IllegalStateException("Начатый контракт нельзя передать");companyId=id; }
    public void restoreCompany(UUID id) { companyId=id; }
    public ContractStatus settlementStatus() { return settlementStatus; }
    public long settlementPayout() { return settlementPayout; }
    public long settlementRefund() { return settlementRefund; }
    public void settlement(ContractStatus status,long payout,long refund) {
        if(!funded||status==null||payout<0||refund<0||Math.addExact(payout,refund)!=Math.round(escrow*100))throw new IllegalArgumentException("Некорректный расчёт контракта");
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

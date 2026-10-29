package ru.neverland.townysieges;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import ru.neverland.core.ApiServices;

/** One durable repair ID and frozen terms. A queued debit is never treated as payment. */
public final class RepairTreasury {
    public enum Outcome { WAITING, PAID, REJECTED, REVIEW }
    private static final String OWNER = "NeverLandTownySiegesPlus";
    private final Set<UUID> submitting = ConcurrentHashMap.newKeySet();
    static String reason(RaidRepairsStore.Job job) { return "Ремонт здания: " + job.id(); }
    public Outcome poll(RaidRepairsStore.Job job, boolean allowSubmit) throws Exception {
        if (!job.treasury()) throw new IllegalArgumentException("У ремонта прежний способ оплаты");
        var api = ApiServices.connect("NeverLandTownyTreasuryPlus", "ru.neverland.townytreasury.api.TownyTreasuryApi", 1, "requestDebit", "debitStatus");
        if (!api.ready()) return Outcome.WAITING;
        Object raw = api.invoke("debitStatus", new Class<?>[]{UUID.class}, job.id());
        if (!(raw instanceof Map<?, ?> receipt)) throw new IllegalArgumentException("Нет квитанции казны");
        if ("ABSENT".equals(receipt.get("status"))) {
            if (!job.id().toString().equals(String.valueOf(receipt.get("operation")))) throw new IllegalArgumentException("Квитанция другого ремонта");
            if (allowSubmit && submitting.add(job.id())) try {
                var pending = (CompletionStage<?>) api.invoke("requestDebit", new Class<?>[]{UUID.class, UUID.class, String.class, String.class, long.class, String.class, UUID.class, String.class},
                        job.id(), job.town(), "construction", "raid-repair", job.cents(), reason(job), job.actor(), OWNER);
                pending.whenComplete((value, error) -> submitting.remove(job.id()));
            } catch (Exception error) { submitting.remove(job.id()); throw error; }
            return Outcome.WAITING;
        }
        return proof(job, receipt);
    }
    public static Outcome proof(RaidRepairsStore.Job job, Map<?, ?> receipt) {
        long cents;
        try { cents = new BigDecimal(String.valueOf(receipt.get("cents"))).longValueExact(); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Неверная сумма квитанции ремонта"); }
        if (!job.treasury() || !job.id().toString().equals(String.valueOf(receipt.get("operation")))
                || !job.town().toString().equals(String.valueOf(receipt.get("town"))) || cents != job.cents()
                || !"construction".equals(receipt.get("category")) || !"raid-repair".equals(receipt.get("source"))
                || !OWNER.equals(receipt.get("owner")) || !job.actor().toString().equals(String.valueOf(receipt.get("actor")))
                || !reason(job).equals(receipt.get("reason")) || !"DEBIT".equals(receipt.get("kind")))
            throw new IllegalArgumentException("Квитанция казны не совпадает с ремонтом");
        return switch (String.valueOf(receipt.get("status"))) {
            case "PAID" -> Boolean.TRUE.equals(receipt.get("backingAvailable")) ? Outcome.PAID : Outcome.REVIEW;
            case "REJECTED" -> Outcome.REJECTED;
            case "PREPARED", "INVOKING" -> Outcome.WAITING;
            case "REVIEW" -> Outcome.REVIEW;
            default -> throw new IllegalArgumentException("Неизвестный результат оплаты ремонта");
        };
    }
}

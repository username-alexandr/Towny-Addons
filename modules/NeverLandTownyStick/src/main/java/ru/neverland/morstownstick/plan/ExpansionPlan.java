package ru.neverland.morstownstick.plan;

import ru.neverland.morstownstick.model.CellKey;
import java.util.*;

/** A reviewed estimate, never a receipt proving that Towny withdrew money. */
public record ExpansionPlan(UUID id, UUID town, UUID author, UUID mayor, int revision, long created,
        long updated, List<CellKey> cells, double estimate, boolean economy, int townSize,
        Status status, UUID approvedBy, List<CellKey> observedOwned, String note) {
    public enum Status { DRAFT, APPROVED, INTENT, OBSERVED, REVIEW, CANCELLED, RECONCILED }
    public ExpansionPlan {
        Objects.requireNonNull(id);Objects.requireNonNull(town);Objects.requireNonNull(author);Objects.requireNonNull(mayor);Objects.requireNonNull(status);
        cells=List.copyOf(cells);observedOwned=List.copyOf(observedOwned);note=Objects.requireNonNull(note);
        if(revision<1||created<1||updated<created||cells.isEmpty()||cells.size()>64||new HashSet<>(cells).size()!=cells.size()
                ||!Double.isFinite(estimate)||estimate<0||townSize<0||note.length()>1000||new HashSet<>(observedOwned).size()!=observedOwned.size()
                ||!cells.containsAll(observedOwned))throw new IllegalArgumentException("Повреждён план расширения");
        for(var cell:cells)if(cell.world()==null||cell.world().isBlank()||cell.world().length()>128||Math.abs((long)cell.x())>30_000_000||Math.abs((long)cell.z())>30_000_000)throw new IllegalArgumentException("Некорректный участок");
        if((status==Status.APPROVED||status==Status.INTENT||status==Status.OBSERVED||status==Status.REVIEW)&&approvedBy==null)throw new IllegalArgumentException("Нет согласования мэра");
        if(approvedBy!=null&&!approvedBy.equals(mayor))throw new IllegalArgumentException("Согласование другого мэра");
        if(status==Status.OBSERVED&&observedOwned.size()!=cells.size())throw new IllegalArgumentException("Не все участки присоединены");
    }
    public boolean open(){return status==Status.DRAFT||status==Status.APPROVED||status==Status.INTENT||status==Status.REVIEW;}
    public ExpansionPlan approve(UUID actor,int expected,long now){
        requireRevision(expected);if(status!=Status.DRAFT||!mayor.equals(actor))throw new IllegalStateException("Этот план может согласовать только указанный мэр");
        return change(Status.APPROVED,actor,List.of(),"Мэр согласовал смету; присоединение ещё не запускалось",now);
    }
    public ExpansionPlan intent(UUID actor,int expected,long now){
        requireRevision(expected);if(status!=Status.APPROVED||!mayor.equals(actor)||!actor.equals(approvedBy))throw new IllegalStateException("Сначала согласуйте текущую версию плана");
        return change(Status.INTENT,actor,List.of(),"Начало записано. После сбоя нужна сверка; автоматически не повторять",now);
    }
    public ExpansionPlan result(List<CellKey> owned,String reason,long now){
        if(status!=Status.INTENT)throw new IllegalStateException("Запуск не подтверждён");
        return change(owned.size()==cells.size()?Status.OBSERVED:Status.REVIEW,approvedBy,owned,reason,now);
    }
    public ExpansionPlan cancel(long now){if(status!=Status.DRAFT&&status!=Status.APPROVED)throw new IllegalStateException("Запущенный план отменять нельзя: нужна сверка администратора");return change(Status.CANCELLED,approvedBy,List.of(),"План отменён до запуска. Списание не запрашивалось",now);}
    public ExpansionPlan reconcile(List<CellKey> owned,String reason,long now){if(status!=Status.INTENT&&status!=Status.REVIEW)throw new IllegalStateException("План не ожидает сверки");return change(Status.RECONCILED,approvedBy,owned,reason,now);}
    private ExpansionPlan change(Status next,UUID approval,List<CellKey> owned,String reason,long now){return new ExpansionPlan(id,town,author,mayor,revision,created,Math.max(updated,now),cells,estimate,economy,townSize,next,approval,owned,reason);}
    public void requireRevision(int expected){if(expected!=revision)throw new IllegalStateException("Версия плана изменилась: откройте его заново");}
}

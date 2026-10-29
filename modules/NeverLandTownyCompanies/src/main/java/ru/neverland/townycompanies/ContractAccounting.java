package ru.neverland.townycompanies;

import java.nio.charset.StandardCharsets;
import java.util.*;
import static ru.neverland.townycompanies.CompanyData.*;

/** Cost allocation changes reports only. Bank receipts remain the authoritative source of paid money. */
public final class ContractAccounting {
    private ContractAccounting(){}
    public enum Cost { WAGES, TAXES }
    private static void amount(Long value){if(value!=null&&(value<0||value>MAX))throw new IllegalArgumentException("Неверная сумма затрат");}
    public record Budget(Long materials,Long wages,Long taxes){public Budget{amount(materials);amount(wages);amount(taxes);}public static Budget unknown(){return new Budget(null,null,null);}public Long total(){return materials==null||wages==null||taxes==null?null:Math.addExact(materials,Math.addExact(wages,taxes));}}
    public record Report(UUID id,UUID company,UUID town,UUID contract,String title,String target,int goal,long reward,Budget budget,Long declaredMaterials,String materialNote,UUID actor,long updated,long revision){
        public Report{Objects.requireNonNull(company);Objects.requireNonNull(town);Objects.requireNonNull(contract);Objects.requireNonNull(budget);Objects.requireNonNull(actor);if(!reportId(company,contract).equals(id)||title==null||title.isBlank()||title.length()>160||target==null||target.length()>256||goal<1||goal>1_000_000||reward<0||reward>MAX||updated<0||revision<1||materialNote==null||materialNote.length()>200||declaredMaterials!=null&&materialNote.isBlank())throw new IllegalArgumentException("Некорректная смета договора");amount(declaredMaterials);}
        public Report edit(Budget budget,Long materials,String note,UUID actor,long at){return new Report(id,company,town,contract,title,target,goal,reward,budget,materials,note,actor,at,Math.incrementExact(revision));}
    }
    public record Allocation(UUID id,UUID report,UUID payment,Cost cost,long cents,UUID actor,long created,boolean reversed){
        public Allocation{Objects.requireNonNull(id);Objects.requireNonNull(report);Objects.requireNonNull(payment);Objects.requireNonNull(cost);Objects.requireNonNull(actor);if(cents<1||cents>MAX||created<0)throw new IllegalArgumentException("Некорректное распределение затрат");}
    }
    public record State(Map<UUID,Report> reports,Map<UUID,Allocation> allocations,long revision){
        public State{reports=Map.copyOf(reports);allocations=Map.copyOf(allocations);if(revision<0||reports.size()>10000||allocations.size()>50000)throw new IllegalArgumentException("Превышен предел учёта договоров");long bound=allocations.size()*1024L;for(var r:reports.values())bound+=2000L+10L*(r.title().length()+r.target().length()+r.materialNote().length());if(bound>16*1024*1024)throw new IllegalArgumentException("Достигнут предел журнала затрат; обратитесь к администрации для архивирования");for(var e:reports.entrySet())if(!e.getKey().equals(e.getValue().id()))throw new IllegalArgumentException("ID сметы не совпадает");for(var e:allocations.entrySet())if(!e.getKey().equals(e.getValue().id())||!reports.containsKey(e.getValue().report()))throw new IllegalArgumentException("Затрата без сметы");}
        public static State empty(){return new State(Map.of(),Map.of(),0);}
    }
    public record Summary(Long received,long refund,Long plannedMargin,long paidWages,long paidTaxes,Long declaredMaterials,Long accountedMargin,long unallocatedWages,long unallocatedTaxes,int unverified){ }
    public static UUID reportId(UUID company,UUID contract){return UUID.nameUUIDFromBytes(("NeverLand:contract-report:"+company+":"+contract).getBytes(StandardCharsets.UTF_8));}
    private static Report current(State state,UUID report,long revision){var r=state.reports().get(report);if(r==null||r.revision()!=revision)throw new IllegalArgumentException("Смета изменилась. Откройте её заново");return r;}
    public static State put(State state,Report next,long expected){
        var old=state.reports().get(next.id());if(old==null?expected!=0||next.revision()!=1:old.revision()!=expected||next.revision()!=expected+1)throw new IllegalArgumentException("Смета изменилась. Повторите выбор");
        if(old!=null&&(!old.company().equals(next.company())||!old.town().equals(next.town())||!old.contract().equals(next.contract())||!old.title().equals(next.title())||!old.target().equals(next.target())||old.goal()!=next.goal()||old.reward()!=next.reward()))throw new IllegalArgumentException("Условия исходного договора закреплены в смете");
        var reports=new HashMap<>(state.reports());reports.put(next.id(),next);return new State(reports,state.allocations(),Math.incrementExact(state.revision()));
    }
    public static Cost cost(Payment payment){if(payment.purpose()==Purpose.SALARY)return Cost.WAGES;if(payment.purpose()==Purpose.TAX)return Cost.TAXES;throw new IllegalArgumentException("Распределять можно оплаченные зарплаты и налоги компании");}
    private static boolean verified(Allocation a,Report r,Payment p){return p!=null&&p.phase()==Phase.DONE&&p.company().equals(r.company())&&(a.cost()==Cost.WAGES?p.purpose()==Purpose.SALARY:p.purpose()==Purpose.TAX);}
    public static long allocated(State state,UUID payment){long total=0;for(var a:state.allocations().values())if(!a.reversed()&&a.payment().equals(payment))total=Math.addExact(total,a.cents());return total;}
    public static State allocate(State state,UUID report,long expected,Payment payment,UUID id,long cents,UUID actor,long now){
        Objects.requireNonNull(payment);Cost category=cost(payment);var next=new Allocation(id,report,payment.id(),category,cents,actor,now,false);var existing=state.allocations().get(id);
        if(existing!=null){if(existing.equals(next))return state;throw new IllegalArgumentException("ID затраты уже занят другими условиями");}
        var r=current(state,report,expected);if(!verified(next,r,payment))throw new IllegalArgumentException("Нет подтверждённого расхода этой компании");long used=allocated(state,payment.id());if(cents>payment.amount()-used)throw new IllegalArgumentException("Сумма превышает нераспределённую часть платежа");
        var allocations=new HashMap<>(state.allocations());allocations.put(id,next);var reports=new HashMap<>(state.reports());reports.put(report,r.edit(r.budget(),r.declaredMaterials(),r.materialNote(),actor,now));return new State(reports,allocations,Math.incrementExact(state.revision()));
    }
    public static State reverse(State state,UUID id,long expected,UUID actor,long now){var a=state.allocations().get(id);if(a==null)throw new IllegalArgumentException("Распределение не найдено");var r=current(state,a.report(),expected);if(a.reversed())return state;var allocations=new HashMap<>(state.allocations());allocations.put(id,new Allocation(a.id(),a.report(),a.payment(),a.cost(),a.cents(),a.actor(),a.created(),true));var reports=new HashMap<>(state.reports());reports.put(r.id(),r.edit(r.budget(),r.declaredMaterials(),r.materialNote(),actor,now));return new State(reports,allocations,Math.incrementExact(state.revision()));}
    public static Map<UUID,Long> totals(State state){var totals=new HashMap<UUID,Long>();for(var a:state.allocations().values())if(!a.reversed())totals.merge(a.payment(),a.cents(),Math::addExact);return Map.copyOf(totals);}
    public static Map<UUID,Summary> summaries(State state,CompanyLedger.State bank){
        var totals=totals(state);var costs=new HashMap<UUID,long[]>();var unallocated=new HashMap<UUID,long[]>();
        for(var a:state.allocations().values())if(!a.reversed()){var r=state.reports().get(a.report());var payment=bank.payments().get(a.payment());var values=costs.computeIfAbsent(r.id(),id->new long[3]);if(!verified(a,r,payment)||totals.getOrDefault(a.payment(),0L)>payment.amount()){values[2]++;continue;}int index=a.cost()==Cost.WAGES?0:1;values[index]=Math.addExact(values[index],a.cents());}
        for(var payment:bank.payments().values())if(payment.phase()==Phase.DONE&&(payment.purpose()==Purpose.SALARY||payment.purpose()==Purpose.TAX)){long available=Math.max(0,payment.amount()-totals.getOrDefault(payment.id(),0L));var values=unallocated.computeIfAbsent(payment.company(),id->new long[2]);int index=payment.purpose()==Purpose.SALARY?0:1;values[index]=Math.addExact(values[index],available);}
        var result=new HashMap<UUID,Summary>();for(var r:state.reports().values()){var values=costs.getOrDefault(r.id(),new long[3]);var free=unallocated.getOrDefault(r.company(),new long[2]);var receipt=bank.receipts().get(r.contract());Long received=receipt!=null&&receipt.company().equals(r.company())&&receipt.town().equals(r.town())?receipt.payout():null;long refund=received==null?0:receipt.refund();Long planned=r.budget().total()==null?null:r.reward()-r.budget().total();Long accounted=received==null||r.declaredMaterials()==null||values[2]>0?null:received-values[0]-values[1]-r.declaredMaterials();result.put(r.id(),new Summary(received,refund,planned,values[0],values[1],r.declaredMaterials(),accounted,free[0],free[1],Math.toIntExact(values[2])));}
        return Map.copyOf(result);
    }
    public static Summary summary(State state,Report report,CompanyLedger.State bank){var value=summaries(state,bank).get(report.id());if(value==null)throw new IllegalArgumentException("Смета не найдена в снимке");return value;}
}

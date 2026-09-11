package ru.neverland.townycompanies;

import java.util.*;
import java.io.IOException;
import static ru.neverland.townycompanies.CompanyData.*;

/** Each mutation is committed before publishing in-memory state or calling a bank. */
public final class CompanyLedger {
    public record State(Map<UUID,Company> companies, Map<UUID,Payment> payments, Map<UUID,Receipt> receipts) {
        public State { companies=Map.copyOf(companies);payments=Map.copyOf(payments);receipts=Map.copyOf(receipts);validate(companies,payments,receipts); }
        public static State empty() { return new State(Map.of(),Map.of(),Map.of()); }
    }
    public interface Store { State state(); void save(State state)throws IOException; boolean writable(); }
    public interface Bank { boolean transfer(Payment payment)throws Exception; }
    private final Store store; private final Bank bank;
    public CompanyLedger(Store store,Bank bank) { this.store=store;this.bank=bank; }
    public State state() { return store.state(); }
    public Company company(UUID id) { return state().companies().get(id); }
    public Company membership(UUID player) { return state().companies().values().stream().filter(c->!c.closed()&&c.members().containsKey(player)).findFirst().orElse(null); }
    public void put(Company c)throws IOException { var cs=new LinkedHashMap<>(state().companies());cs.put(c.id(),c);store.save(new State(cs,state().payments(),state().receipts())); }
    public boolean busy(UUID company) { return state().payments().values().stream().anyMatch(p->p.company().equals(company)&&(p.phase()==Phase.READY||p.phase()==Phase.PENDING)); }
    public Payment begin(UUID company, UUID account, Purpose purpose, long amount,long now)throws IOException {
        Company c=Objects.requireNonNull(company(company));if(c.closed()||busy(company))throw new IllegalStateException("Предыдущий платёж компании ещё не завершён");
        if(purpose==Purpose.REFUND)throw new IllegalArgumentException("Возврат создаётся только расчётом контракта");
        if(purpose==Purpose.DEPOSIT)money(Math.addExact(c.balance(),amount));
        if(purpose!=Purpose.DEPOSIT){if(amount>c.balance())throw new IllegalArgumentException("Недостаточно средств компании");c=c.funds(c.balance()-amount,c.debt(),c.nextTax());}
        if(purpose==Purpose.TAX&&(amount>c.debt()||!account.equals(c.town())))throw new IllegalArgumentException("Некорректный налог");
        Payment p=new Payment(UUID.randomUUID(),company,account,purpose,amount,Phase.READY,now);
        var cs=new LinkedHashMap<>(state().companies());cs.put(c.id(),c);var ps=new LinkedHashMap<>(state().payments());ps.put(p.id(),p);
        store.save(new State(cs,ps,state().receipts()));return p;
    }
    public void process(UUID id)throws IOException {
        Payment p=state().payments().get(id);if(p==null||p.phase()!=Phase.READY)return;
        // Missing final bank evidence never triggers an automatic retry.
        set(p.phase(Phase.PENDING)); boolean success;
        try { success=bank.transfer(p); } catch(Exception ex) { return; }
        finish(id,success);
    }
    public void finish(UUID id,boolean success)throws IOException {
        Payment p=state().payments().get(id);if(p==null||p.phase()!=Phase.PENDING)throw new IllegalArgumentException("Платёж не ожидает сверки");
        Company c=company(p.company());long balance=c.balance(),debt=c.debt();
        if(success&&p.purpose()==Purpose.DEPOSIT)balance=Math.addExact(balance,p.amount());
        if(success&&p.purpose()==Purpose.TAX)debt-=p.amount();
        if(!success&&(p.purpose()==Purpose.WITHDRAW||p.purpose()==Purpose.TAX))balance=Math.addExact(balance,p.amount());
        var cs=new LinkedHashMap<>(state().companies());cs.put(c.id(),c.funds(balance,debt,c.nextTax()));
        var ps=new LinkedHashMap<>(state().payments());
        // A definitively rejected city refund remains held and can be retried safely.
        ps.put(id,p.phase(!success&&p.purpose()==Purpose.REFUND?Phase.READY:success?Phase.DONE:Phase.REJECTED));
        store.save(new State(cs,ps,state().receipts()));
    }
    private void set(Payment p)throws IOException {var ps=new LinkedHashMap<>(state().payments());ps.put(p.id(),p);store.save(new State(state().companies(),ps,state().receipts()));}
    public void assess(UUID id,long rate,long now,long interval)throws IOException {
        Company c=company(id);if(c.closed()||now<c.nextTax()||busy(id))return;
        // One assessment after downtime; existing debt blocks another assessment.
        put(c.funds(c.balance(),c.debt()==0?rate:c.debt(),Math.addExact(now,interval)));
    }
    public boolean settle(Receipt receipt,long now)throws IOException {
        Receipt old=state().receipts().get(receipt.contract());if(old!=null){if(!old.equals(receipt))throw new IllegalArgumentException("Условия повторного расчёта отличаются");return true;}
        Company c=company(receipt.company());if(c==null||c.closed()||!c.town().equals(receipt.town())||busy(c.id()))return false;
        var cs=new LinkedHashMap<>(state().companies());cs.put(c.id(),c.funds(Math.addExact(c.balance(),receipt.payout()),c.debt(),c.nextTax()));
        var rs=new LinkedHashMap<>(state().receipts());rs.put(receipt.contract(),receipt);var ps=new LinkedHashMap<>(state().payments());
        if(receipt.refund()>0){var p=new Payment(receipt.contract(),c.id(),c.town(),Purpose.REFUND,receipt.refund(),Phase.READY,now);if(ps.putIfAbsent(p.id(),p)!=null)throw new IllegalArgumentException("ID платежа занят");}
        store.save(new State(cs,ps,rs));return true;
    }
    private static void validate(Map<UUID,Company> cs,Map<UUID,Payment> ps,Map<UUID,Receipt> rs) {
        Set<UUID> members=new HashSet<>();Set<String> names=new HashSet<>();
        cs.forEach((id,c)->{if(!id.equals(c.id()))throw new IllegalArgumentException("ID компании не совпадает");if(!c.closed()){
            for(UUID player:c.members().keySet())if(!members.add(player))throw new IllegalArgumentException("Игрок состоит в двух компаниях");
            if(!names.add(c.town()+":"+nameKey(c.name())))throw new IllegalArgumentException("Название компании повторяется");}});
        Map<UUID,Long> held=new HashMap<>();
        ps.forEach((id,p)->{Company c=cs.get(p.company());if(!id.equals(p.id())||c==null)throw new IllegalArgumentException("Платёж без компании");
            if((p.purpose()==Purpose.TAX||p.purpose()==Purpose.REFUND)&&!p.account().equals(c.town()))throw new IllegalArgumentException("Платёж другому городу");
            if(p.phase()==Phase.READY||p.phase()==Phase.PENDING){if(c.closed())throw new IllegalArgumentException("Закрытая компания с платежом");
                if(p.purpose()==Purpose.DEPOSIT||p.purpose()==Purpose.WITHDRAW||p.purpose()==Purpose.TAX)held.merge(c.id(),p.amount(),Math::addExact);
                if(p.purpose()==Purpose.TAX&&p.amount()>c.debt())throw new IllegalArgumentException("Налог превышает долг");}});
        held.forEach((id,n)->money(Math.addExact(cs.get(id).balance(),n)));
        rs.forEach((id,r)->{Company c=cs.get(r.company());if(!id.equals(r.contract())||c==null||!c.town().equals(r.town()))throw new IllegalArgumentException("Квитанция без компании");
            Payment p=ps.get(id);if(r.refund()>0&&(p==null||p.purpose()!=Purpose.REFUND||p.amount()!=r.refund()||!p.company().equals(r.company())))throw new IllegalArgumentException("Потерян возврат городу");});
    }
}

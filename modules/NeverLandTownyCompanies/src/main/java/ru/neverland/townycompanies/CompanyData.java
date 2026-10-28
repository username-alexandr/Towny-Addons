package ru.neverland.townycompanies;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Immutable ledger values. Amounts are exact cents; closed companies retain their receipts. */
public final class CompanyData {
    private CompanyData() {}
    public static final long MAX = 100_000_000_000L;
    public enum Kind {
        SHOP("Магазин", "CHEST"), MINE("Шахта", "IRON_PICKAXE"),
        FARM("Ферма", "WHEAT"), TRANSPORT("Транспортная компания", "CHEST_MINECART");
        public final String label, icon;
        Kind(String label, String icon) { this.label=label; this.icon=icon; }
        public static Kind parse(String text) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
            return switch(text.toLowerCase(Locale.ROOT)) {
                case "магазин" -> SHOP; case "шахта" -> MINE; case "ферма" -> FARM;
                case "транспорт" -> TRANSPORT; default -> valueOf(text.toUpperCase(Locale.ROOT));
            };
        } }
    }
    public enum Role { OWNER("Владелец"), MANAGER("Управляющий"), WORKER("Участник");
        public final String label; Role(String label) { this.label=label; }
    }
    public enum Purpose { DEPOSIT, WITHDRAW, TAX, REFUND, SALARY }
    public enum Phase { READY, PENDING, DONE, REJECTED }
    public record Company(UUID id, UUID town, String name, Kind kind, UUID owner,
                          Map<UUID,Role> members, Map<UUID,Long> invitations, UUID successor,
                          long successorUntil, long balance, long debt, long nextTax, long revision, boolean closed) {
        public Company {
            Objects.requireNonNull(id); Objects.requireNonNull(town); Objects.requireNonNull(kind); Objects.requireNonNull(owner);
            if (!name.equals(cleanName(name))) throw new IllegalArgumentException("Некорректное имя компании");
            members=Map.copyOf(members); invitations=Map.copyOf(invitations);
            if(members.get(owner)!=Role.OWNER || members.values().stream().filter(r->r==Role.OWNER).count()!=1)
                throw new IllegalArgumentException("У компании должен быть один владелец");
            money(balance); money(debt);
            if(nextTax<0 || revision<0 || successorUntil<0 || invitations.values().stream().anyMatch(t->t<0)
                    || (closed && (balance!=0 || debt!=0))) throw new IllegalArgumentException("Некорректное состояние компании");
            if(successor!=null && (!members.containsKey(successor) || successor.equals(owner))) throw new IllegalArgumentException("Получатель компании не является участником");
        }
        public boolean manages(UUID player) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) { return !closed && (members.get(player)==Role.OWNER || members.get(player)==Role.MANAGER); } }
        public Company funds(long balance,long debt,long nextTax) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) { return new Company(id,town,name,kind,owner,members,invitations,successor,successorUntil,balance,debt,nextTax,revision+1,closed); } }
        public Company team(UUID owner, Map<UUID,Role> members, Map<UUID,Long> invitations, UUID successor,long until) { try (var suiteGuard4 = ru.neverland.core.SuiteState.guard()) {
            return new Company(id,town,name,kind,owner,members,invitations,successor,until,balance,debt,nextTax,revision+1,closed);
        } }
        public Company close() { try (var suiteGuard5 = ru.neverland.core.SuiteState.guard()) { return new Company(id,town,name,kind,owner,members,Map.of(),null,0,balance,debt,nextTax,revision+1,true); } }
    }
    public record Payment(UUID id, UUID company, UUID account, Purpose purpose, long amount, Phase phase, long created) {
        public Payment { Objects.requireNonNull(id);Objects.requireNonNull(company);Objects.requireNonNull(account);Objects.requireNonNull(purpose);Objects.requireNonNull(phase);money(amount);if(amount==0||created<0)throw new IllegalArgumentException("Некорректный платёж"); }
        public Payment phase(Phase phase) { try (var suiteGuard6 = ru.neverland.core.SuiteState.guard()) { return new Payment(id,company,account,purpose,amount,phase,created); } }
    }
    public record Receipt(UUID contract, UUID company, UUID town, long payout, long refund) {
        public Receipt { Objects.requireNonNull(contract);Objects.requireNonNull(company);Objects.requireNonNull(town);money(payout);money(refund);money(Math.addExact(payout,refund)); }
    }
    public static void money(long n) { try (var suiteGuard7 = ru.neverland.core.SuiteState.guard()) { if(n<0||n>MAX)throw new IllegalArgumentException("Сумма должна быть от 0 до 1 000 000 000 монет"); } }
    public static long cents(String text) { try (var suiteGuard8 = ru.neverland.core.SuiteState.guard()) { long n=new BigDecimal(text.replace(',','.')).movePointRight(2).longValueExact();money(n);if(n==0)throw new IllegalArgumentException("Укажите положительную сумму, не более двух знаков после запятой");return n; } }
    public static String format(long cents) { try (var suiteGuard9 = ru.neverland.core.SuiteState.guard()) { return BigDecimal.valueOf(cents,2).toPlainString(); } }
    public static String cleanName(String value) { try (var suiteGuard10 = ru.neverland.core.SuiteState.guard()) {
        String s=Normalizer.normalize(Objects.requireNonNull(value),Normalizer.Form.NFKC).strip().replaceAll(" +"," ");
        if(!s.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} _-]{2,31}"))throw new IllegalArgumentException("Название: 3–32 буквы, цифры, пробелы, _ или -");
        return s;
    } }
    public static String nameKey(String name) { try (var suiteGuard11 = ru.neverland.core.SuiteState.guard()) { return cleanName(name).toLowerCase(Locale.ROOT); } }
}

package ru.neverland.townycompanies.api;
import java.util.UUID;
/** Main-thread API for trusted plugins. Escrow is already debited by Contracts. */
public interface CompaniesApi extends ru.neverland.core.ApiContract {
    @Override default java.util.Set<String> capabilities() { return java.util.Set.of("canContribute", "canManage", "canTake", "companyName", "settleEscrow"); }
    boolean canTake(UUID actor,UUID company,UUID town);
    boolean canManage(UUID actor,UUID company,UUID town);
    boolean canContribute(UUID actor,UUID company,UUID town);
    String companyName(UUID company);
    boolean settleEscrow(UUID contract,UUID company,UUID town,long payoutCents,long refundCents);
}

package ru.neverland.townycompanies.api;
import java.util.UUID;
/** Main-thread API for trusted plugins. Escrow is already debited by Contracts. */
public interface CompaniesApi {
    boolean canTake(UUID actor,UUID company,UUID town);
    boolean canManage(UUID actor,UUID company,UUID town);
    boolean canContribute(UUID actor,UUID company,UUID town);
    String companyName(UUID company);
    boolean settleEscrow(UUID contract,UUID company,UUID town,long payoutCents,long refundCents);
}

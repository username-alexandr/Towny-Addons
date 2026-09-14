package ru.neverland.townyjustice.api;
import java.util.*;
/** Main-thread queries. Immutable JDK snapshots. Monetary commands remain authenticated player actions.
 * caseFile: id/town/subject/issuer UUID, kind/phase/reason String, amount/created/due/capturedAt Long,
 * hours Integer, hunter/prison/payment/payout String UUID or empty. Amounts are cents.
 * wanted includes only funded, unexpired WANTED cases. Custody and payments pending are separate states.
 */
public interface TownyJusticeApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities(){return Set.of("healthy","caseFile","cases","wanted","fines","prisons");}
    boolean healthy();Optional<Map<String,Object>> caseFile(UUID id);Collection<Map<String,Object>> cases(UUID town);Collection<Map<String,Object>> wanted(UUID subject);Collection<Map<String,Object>> fines(UUID subject);Collection<Map<String,Object>> prisons(UUID town);
}

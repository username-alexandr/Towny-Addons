package ru.neverland.townyideologies.api;
import java.util.UUID;
import java.util.Set;
/** Public political policy lookup. Main server thread; throws on unavailable storage. */
public interface TownyIdeologiesApi extends ru.neverland.core.ApiContract {
    @Override default Set<String> capabilities() { return Set.of("ideology", "governmentForm"); }
    String ideology(UUID town);
    /** DEMOCRACY, MONARCHY or AUTOCRACY. Unknown configured values fail closed. */
    String governmentForm(UUID town);
}

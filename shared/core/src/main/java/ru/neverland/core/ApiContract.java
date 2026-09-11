package ru.neverland.core;
import java.util.Set;
/** Stable cross-plugin metadata. A major version changes only for incompatible semantics. */
public interface ApiContract {
    default int apiVersion() { return 1; }
    default Set<String> capabilities() { return Set.of(); }
    /** JDK-only snapshots remain readable across independent plugin classloaders. */
    default java.util.List<java.util.Map<String,Object>> storageMetrics() {
        return AtomicFiles.metrics().stream().map(m -> java.util.Map.<String,Object>of("file",m.file(),"writes",m.writes(),"failures",m.failures(),"bytes",m.bytes(),"totalNanos",m.totalNanos(),"maximumNanos",m.maximumNanos())).toList();
    }
    default java.util.List<java.util.Map<String,Object>> integrationDiagnostics() {
        return ApiServices.statuses().stream().map(s -> java.util.Map.<String,Object>of("plugin",s.plugin(),"contract",s.contract(),"state",s.state().name(),"detail",s.detail(),"at",s.at())).toList();
    }
}

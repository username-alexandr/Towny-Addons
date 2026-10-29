# Builds 0.11.1 payment validation

Final JAR SHA256: `8fdadfe37be3ff070aa61de11f0e22d9d5f4e71b7f006264369082f91aa4f704`.

48 cold programs, including BuildPaymentsSmoke43. Native tmpfs fixture5: 22+8 assertions, both JVM exit0. Both shutdown hooks report `terminated=true` for the real bounded core writer. Embedded source hashes match the current module and shared sources.

Native covers real Folia/Towny/Builds/Treasury, delayed native AtomicFiles writer, no bank before core ACK, mutation guards on retained objects, surplus, virtual/physical purchases, insurance, player/admin cancellation, rejected funding, unavailable installed Treasury, restart and final snapshot after closing an exclusive warehouse session. Explicit substitutes: offline Player, pre-authorized project terms, deterministic economy adapter. No live Vault, network client or whole-pack result is claimed.

Pilot fixture2 failed a test-only contention observation: its independent thread called tryRun only once while the probe itself held SuiteState. The final probe retries non-blockingly within a bounded interval. No production condition was weakened to obtain the result. The earlier fixture1 passed the smaller 20+5 set; the final evidence is fixture5.

Offline validation of the actual persisted TSV confirms unique chronological state timestamps and the correct terminal entry for all 5 first-boot and 6 restart completed/rejected purchases. Repeated manual effect decisions retain distinct transition IDs without changing the bank operation UUID.

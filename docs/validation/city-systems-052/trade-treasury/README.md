# Trade0.3.1 budget migration

Exact Trade JAR SHA256: `e25cd20ce0a1b4e598b54009adcc2b95472a815372f5881cd7d1a35f347cad26`.

Cold: 10 programs PASS, including TreasuryFundingSmoke27 and SupplyJournalSmoke20. Native Folia26.2 build7: Treasury fixture4 17+5, legacy guarantees fixture2 22+10, four JVM exit0 with the same exact candidate. The final three completed supply histories also have strictly ordered terminal SUPPLY_DEAL evidence, including durable pending audit outbox rows.

Actual Towny accounts, Treasury, Trade journals and Folia schedulers are exercised. Treasury fixture uses both real Builds warehouse APIs; accepted terms/reservations, offline Player/EntityScheduler and deterministic economy adapter are explicit substitutes. Legacy fixture additionally uses a deterministic warehouse and route/clock seeds. No live Vault/client or full-pack result is claimed.

Pilot1 failed in the fixture bank's name() call because ConcurrentHashMap does not accept a null lookup key; the fixture was corrected. Pilot2 exposed real actor-authorization starvation under frequent progress ticks; product tick now waits for the bounded entity callback. Pilot3 exposed a real stale error cooldown after a confirmed administrator resolution; product clears that delay after ACK. Both regressions are exercised without loosening the final probe timeout. Earlier failed disposable fixtures remain locally available.

Supply journal schema1 loads without rewriting old files; unknown old payments retain manual review. Schema2 adds frozen budget intent, accepting actor, audit/reputation outboxes and optional per-attempt audit chronology. Do not downgrade while unresolved new payments exist. Older warehouse I/O and the rest of the full suite remain under the common migration gate.

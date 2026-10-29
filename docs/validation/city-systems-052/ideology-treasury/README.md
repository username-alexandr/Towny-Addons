# Ideologies 0.2.1 / Treasury async migration validation

Fresh loopback fixture `folia-ideology-treasury-3`, port 25680. **First 19 + restart 11 = 30 PASS**, two sequential foreground Folia JVMs, both exit 0. The second started only after the first completely stopped. The final JAR has lazy Treasury lookup with no added load-order edge; the full-pack reverse path through Policies/Quests/Resources/Events would make that edge cyclic.

Cold: three programs PASS, including existing ReformsSmoke 36 assertions and new ReformTreasurySmoke 25 checks. Every embedded build input hash matches the final source.

- JAR SHA-256: `f886b922fb79f0e2e0461aa2e1f7903b5676442cdb1e24397c949ee9e8cf68f1`.
- Source SHA-256: `d0c8534fe0e39f86a9addff257b2a304da9f42179e01b4c40ba31b7236078e10`.
- Pinned provider: TreasuryPlus 0.3.1; other fixture dependencies Citizens 0.2.0, Control 0.6.1, Towny 0.103.2.0. Exact dependency hashes are in native-results.json. A newer full pack remains a separate gate.

Native exercises both actual consumer/provider state machines, physical Treasury and consumer file delays, own intent ACK before submission, exact stable route/UUID/payload, frozen prices, PREPARED without benefits, consumer pause before debit, native AccountObserver evidence, PAID plus local ACK before level change, duplicate paid request, allocation refusal without direct fallback, unknown bank reply, full JVM restart with no replay, Treasury reconciliation, legacy route preservation, missing installed API, busy provider with ABSENT receipt, and explicit retry of the same exact UUID. Real bank invocation is checked outside SuiteState ownership.

The fixture uses a controlled economy adapter with a real Towny account/observer, offline Player/InventoryView/EntityScheduler, accelerated stages and one explicit legacy pending row. It does not exercise a live Vault economy implementation, network client, or render menus. All checks and exact artifacts are recorded. No addon lifecycle/event error or Folia watchdog appears; unavailable Yggdrasil public key and offline/root fixture warnings are expected.

Prior disposable fixtures are not used as final evidence. Fixture1 restart stopped at a probe setup error attempting to transfer from an empty FREE allocation; the probe now transfers an actually funded INFRASTRUCTURE allocation. Fixture2 restart raced asynchronous provider maintenance and received the legitimate busy refusal; the probe now waits for the provider idle condition before manual reconciliation. These were probe sequencing/data fixes, not payment bypasses or production relaxations. Final fixture3 reran both phases from a fresh legacy-data seed.

`validated-0.2.1.json` under scripts/ideology-treasury-runtime-probe binds native results to cold build metadata. Unknown outcomes are never automatically retried. Direct Towny fallback exists only when TreasuryPlus is entirely absent and remains a synchronous backend boundary; installed unavailable Treasury fails closed. This evidence closes the Ideologies consumer migration only, not the other callers in ASYNC-TREASURY-MIGRATION-GATE.md.

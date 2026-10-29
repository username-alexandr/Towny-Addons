# Justice and Espionage Treasury caller fixture

Use only an explicitly marked disposable loopback server. Reserve the shared native JVM slot before `run.py`. `prepare.py` refuses an existing directory; `build.py` compiles the fixture against the exact selected JARs. `run.py` executes first, restart and isolated-corruption boots, rejects stale proof markers and lifecycle failures, and records JAR SHA-256 values.

The probe uses actual Folia 26.2, Towny Account/Observer, Justice0.2.1, Espionage0.2.1 and Treasury0.3.2. The economy backend is a deterministic fixture adapter; initiating players are explicit offline proxies. It does not claim a real Vault provider integration or live-client verification.

Scenarios include delayed caller/core file ACK, repeated clicks, stable terms, rejection, owner pause, synchronous busy before a Treasury intent exists, restart after unknown bank response, old unresolved legacy payments, exact manual reconciliation, backed refunds, known unpaid refund attempts, an actual target-town deletion after banking PAID but before mission application, and optional purchase-file corruption while old missions remain available.

The fixture disables newcomer protection only in its in-memory configuration to reach payment scenarios. It does not override production defaults or claim to retest all hostile-action permission rules. One synthetic legacy unresolved case and one independent one-cent bank deposit before Treasury observation reconciliation are explicit test seeds. Production journals must never be used as fixtures.

The script writes `corrupt-mode` and deliberately replaces only the disposable Espionage `purchases.yml` during its third phase. Keep failed fixtures for diagnosis; start a fresh directory after a failed boot instead of deleting its failure marker.

The restart phase deliberately disables Treasury to verify the absence of direct-bank fallback. It then invokes the real console command `nltmodules enable NeverLandTownyTreasuryPlus` to authorize its next-boot recovery before the corruption phase. It does not hot-enable the plugin, reset any financial journal, or change the production dependency graph. Fixture3 passed 33 first-boot and 10 restart assertions; its third boot exposed this missing test-fixture recovery step because Control correctly retained the deliberate pause. Failed fixture3 remains evidence, not a passing corruption run.

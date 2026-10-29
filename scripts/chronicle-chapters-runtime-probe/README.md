# Native chapter probe

Requires validated production JARs and an explicitly marked stopped disposable Folia template. `prepare.py --template PATH --server NEW_PATH --towny JAR`, then `build.py --server-dir NEW_PATH --java-home JDK25`, then `run.py --server NEW_PATH --java-home JDK25`.

The fixture binds only loopback port 25674. Run two real server processes sequentially; do not share the port. Uses real Towny/Chronicles journals, asynchronous reading and menu events. Historical entries and a completed repair audit receipt are explicit fixtures, without an actual repair or payment. Player/InventoryView/EntityScheduler are offline substitutes routed to a real region. No network client or rendering claim.

The expanded probe also blocks the actual Chronicles AuditJournal monitor on a separate fixture thread, then submits a mayor thank and verifies a global SuiteState heartbeat, no premature publication/success, one pending thank per city, exact captured timestamp/ID after fsync, cooldown, stale inventory rejection and revoked permission on a deferred back click. Blocking is explicit test instrumentation; the production writer is unchanged.

For short foreground invocations run `run.py ... --phase first` and then `run.py ... --phase restart`; each phase exits its JVM before returning and the second verifies identical JAR hashes.

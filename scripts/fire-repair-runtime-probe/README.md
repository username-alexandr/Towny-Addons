# Disposable Folia repair probe

Use only a local throwaway Folia 26.2 server on 127.0.0.1. Never install the probe on a player server.
Install Towny 0.103.2.0, Events 0.4.9 and Control 0.4.11. Disable economy in this isolated fixture and disable telemetry/update checks. Create ALLOW_DISPOSABLE_FIRE_REPAIR_PROBE, compile with build.py --server-dir PATH --java-home JDK25, then start with -Dneverland.fireRepairProbe=true. The probe creates its own towns/residents/damage ledger and shuts down the server. Start again to test persistence.

Expected proof: plugins/FireRepairRuntimeProbe/first-passed.txt and restart-passed.txt. Real Folia, Towny, services and inventories; simulated Player, InventoryView and EntityScheduler delivery on the global scheduler for metadata-only operations. No live player or cross-region world mutation is simulated as successful. File-failure cases run separately in FireDamageSmoke.

# Governance council button probe

This test-only plugin runs against an isolated Folia 26.2 server with real Towny 0.103.2.0 and Governance 0.1.23. It creates disposable town/resident data. Never install it on a player server.

All three guards are mandatory: loopback bind `127.0.0.1`, `-Dneverland.governanceProbe=true`, and a file named `ALLOW_DISPOSABLE_GOVERNANCE_PROBE` in the server directory. The probe shuts the server down when finished.

Build using `python3 build.py --server-dir <disposable-server> --java-home <JDK-25>` after the real server has unpacked its `versions/` and `libraries/` and the two required plugins are installed. Run the marked server twice: first checks navigation/operations, second checks persisted appointment and audit history. Inspect `plugins/GovernanceMenuRuntimeProbe/first-passed.txt`, `restart-passed.txt`, `checks.txt` and any `failed.txt`.

Player, InventoryView and EntityScheduler objects are explicitly simulated. Scheduled callbacks are forwarded to Folia's global scheduler for this data-only fixture. InventoryClickEvent, inventory/item implementation, permissions logic, Towny records, Governance services and file persistence are real. This is not a visual Fabric client test or a test of actual regional ownership of a connected player.

The fixture disables Towny economy and does not require optional Citizens/Elections. Those integration-specific restrictions are preserved in the common Governance service; they are not integration-tested by this probe. Expected offline test-environment Mojang public-key lookup failures do not establish a production authentication test.

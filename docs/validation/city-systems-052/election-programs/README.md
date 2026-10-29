# Elections 0.2.0 — native programs validation

PASS: 31 assertions on first boot, 6 after complete restart, 6 with deliberately corrupt optional program sidecar. Every real Folia process exited 0; no addon lifecycle/event error or watchdog message. Fixture: `folia-election-programs-2`, loopback 127.0.0.1:25675, Java 25, actual Folia 26.2 runtime, pinned Towny 0.103.2.0 plus validated addon JARs. `native-results.json` records SHA-256 of every installed plugin and phase outcomes.

Evidence is the exact assertion list in the three `*-checks.txt` files, not merely an API availability probe. The scenario invokes original nomination/voting/tally/application paths, actual new program repository, actual Population state changes, real native InventoryClickEvent and commands. It physically blocks AtomicFiles replacement on the sidecar path: one draft accepted before the deadline remains unpublished until acknowledgement and then freezes at exact revision 2; an independently delayed freeze passes the 5-second fallback while the regular scheduler interval is one hour. Late unbound data stays excluded from accepted archives after a new campaign and restart.

Provider pause yields unknown evidence, permission failure and menu viewing leave both file bytes unchanged, and corrupt optional sidecar leaves the current votes and subsequent voting intact without overwriting the malformed file.

Player, InventoryView and EntityScheduler are explicit offline substitutes. No Minecraft client interaction, bank/payment operation, or indefinite live-server endurance is claimed. No production server was accessed. `programs.yml` corruption was performed only in the disposable marked fixture after successful normal restart.

Reproduction scripts: `scripts/election-programs-runtime-probe`. The earlier 23+6 fixture was superseded by this broader fresh run and is not mixed into the evidence.

# Council spending limits — exact native evidence

Final candidates and full source manifests are in `artifacts.json` and `scripts/council-spending-runtime-probe/validated-versions.json`.

- Council0.3.0: `6c0c82e5d570db4769e827a1d6372191ecc56551df7a0953c00027c32a5ce40a`
- TreasuryPlus0.3.3: `7707edb20af499b05deadd0886f0ba983039067eabf5af389eff4f6c8e07d91f`
- Governance0.2.2: `e5cb81b00cc561b0115e3d855985b6da364348939f090d269faa9189832d4ce7`
- Control0.6.3: `f436f32473e2cd9999199d5b320f62b49149605757efe89c310fbf2ba9101bb1`

Cold builds: Council5, Treasury7, Governance5, Control14 executable programs. The final Council rebuild additionally includes nonblocking drain when onDisable is invoked from an already-held Suite scope. `cold-all-initial.txt` records the initial complete build, and `cold-council-final.txt` the final Council candidate; the initial Council hash is not labelled as the tested artifact.

The final native limits run `/dev/shm/neverland-council-spending-3` passed **35 + 10 + 11 + 12** assertions in four sequential Folia26.2 build7 JVMs, exit0 each. Actual Council commands, inventories, current mayor and membership checks, physical file writer delay, ACK visibility, native Towny Account/Observer debit/refund, pending daily limits, lost bank response, admin reconciliation, lower future policy, exact restart, optional sidecar corruption, repair, administrative audit and shutdown drain are covered.

The exact Treasury0.3.3 and Control0.6.3 then passed the existing native Treasury regression **24 + 9** in `/dev/shm/neverland-treasury-limits-1`, two more sequential JVMs, exit0. Council is absent in that fixture: it covers the distinct optional-absence path, category conservation, uncertain refunds, repeated IDs, deferred refunds, captured bank flows, recovery and shutdown. No native server was run concurrently with another.

Explicit substitutes: offline Player, InventoryView and entity scheduler delegated to the actual owning region; deterministic EconomyAdapter backend beneath genuine Towny Account/Observer. A recovery seed in the baseline Treasury regression is an explicit fixture. This does not assert compatibility with a particular real Vault provider, network GUI client or the complete 40-addon pack.

Only the added spending sidecar and policy API are fault-isolated here. Complete Council plugin shutdown retains the existing Control dependency graph. The audited Quests isolation exception and its earlier evidence are unchanged; no broad optional-dependency exclusion was added.

Two failed pilot fixtures are retained locally and are not counted as passing runs. Pilot1 asserted that the next inventory slot was null; the final assertion checks the actual requirement that every displayed resident entry belongs to the viewer. Pilot2 changed the mayor to test stale authority, correctly triggering the existing removal of permanent ministers, then incorrectly expected that appointment after restart. The final fixture explicitly reappoints the minister after restoring the mayor. No production authorization, bank or persistence assertion was relaxed to make these pilots pass.

Expected environment-only messages about unreachable Mojang authentication and unavailable OSHI are retained in raw logs. The corrupt phase intentionally logs an invalid optional policy schema. No other addon error, lifecycle failure or watchdog assertion is permitted by the runner.

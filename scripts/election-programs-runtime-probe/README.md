# Native Elections programs probe

Opt-in, three-boot test using an explicitly marked, stopped loopback Folia template. Never run against a real server. The final boot deliberately corrupts only the disposable optional `NeverLandTownyElections/programs.yml` to verify isolation and preservation of ballots.

Prepare a fresh directory with `prepare.py --template <marked-template> --server <fresh-directory> --towny <Towny-0.103.2.0.jar> --port 25675`, compile using `build.py --server-dir <fresh-directory> --java-home <Java25>`, then run `run.py --server <fresh-directory> --java-home <Java25>`. Preparation reads `versions.yml` and requires already validated module JARs. It rejects existing targets or templates without `ALLOW_DISPOSABLE_RAID_PROBE` and loopback binding. The runner checks the loopback port is free before every boot and refuses stale assertion markers.

Actual Towny, Citizens, Governance, Builds, Population, Environment and Elections are installed. `AdmissionsProbe.java` supplies explicitly offline Player / InventoryView / EntityScheduler substitutes; all election state, file writes, provider calls, events, commands, tally and mayor application use the real plugins. Every phase shuts down the real Folia server. This is not a network-client or money-flow test.

Assertions cover exact immutable program versions, permissions and read-only menus, deadline races with a physically delayed draft, freeze timeout without waiting for the ordinary hourly tick, late unbound archive exclusion, unknown paused-provider evidence, unchanged ballots, restart, and optional-file corruption. Evidence is copied to `docs/validation/city-systems-052/election-programs` only after every phase passes.

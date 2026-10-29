# Diplomacy 0.2.0 — native proof

Fresh Folia 26.2 fixture: first **28**, restart **12**, corrupt **4** assertions passed. All three processes exited 0. Per-phase checks are extracted from that phase’s logged CHECK entries (the inherited helper appends restart and corrupt checks to one raw file). Exact JARs and source input hashes are recorded in the manifests. Production JAR SHA-256: `d3da7f26fd07575c083f7a5118d40dbe6693ebee75be274bc274b428cc07472b`.

Cold build completed all four smoke programs (19 + 34 + 20 + 40 checks). The published JAR input hashes match current files; its metadata is written only after successful cold tests.

This uses actual Towny territories, existing alliance service, region-owned native CraftPlayer handles and registered Towny/Diplomacy event handlers. Players are offline constructions; damage events are supplied by the probe. It is not a network-client combat load test. One OPEN receipt is explicitly restored before restart to model a durable pre-crash snapshot. Optional-file corruption intentionally emits one Diplomacy error while Towny, Citizens and old relations remain usable.

Reproduce with `scripts/diplomacy-aid-runtime-probe/README.md`. No money/item movement, automatic sanctions or broad migration of existing Diplomacy IO is claimed.

Common DIPLOMACY_AID audit is verified before shutdown and after restart: exact signer, two original combat proof receipts, one completion, neutral stop with both towns, durable outbox drained. Cold checks additionally deny an actual audit-directory failure, preserve/replay original receipt time, deduplicate an already appended receipt, and drain delayed audit during shutdown.

Final activation review: persisted FINALIZING and ACTIVE with an outdated signer are explicit restart fixtures. Both close neutrally before observation. A live mayor replacement after restart ends only the aid promise, preserving fulfilled proof and the original alliance.

# Atomic war closure evidence

SiegesPlus 0.6.5 SHA-256: `8dcad015964849a8c2b87ea3ce578d862bb22406f4ada5b4b52f4229fce3b13e`. Complete input manifest is in `artifact.json`; all inputs and module source files were checked against the root repository before copying the artifact.

- Cold build: **15** executable programs. New `WarConclusionSmoke`: **17** actual SQLite checks, including exact-ID completion, actor attribution, unchanged cooldown, wrong-outcome rejection, expiry, rollback of both tables on an injected SQL failure, restart repair of old-version orphan captures and predecessor cleanup before row deletion.
- Final native fixture `/dev/shm/neverland-war-close-3`: **53 + 18** assertions, two sequential Folia 26.2 JVMs, both exit 0. Real Towny 0.103.2.0, Builds 0.11.2, Control 0.6.4 and SiegesPlus 0.6.5. Checks include native Towny/PvP/TNT decisions, captured-chunk permissions, separate audit actions, restart, cancellation, registered two-phase shutdown, external Suite lock and draining an already accepted SQLite write before connection closure.
- Paired native fixture `/dev/shm/neverland-army-async-closure-1`: **36 + 15**, Army 0.3.0 plus the exact same SiegesPlus artifact. Consumed resources, camp supply receipts, no double/missed-minute charge, duty and shutdown regressions remain green.

War actors are native offline CraftPlayers placed in the owning region identity list. Army/camp permission policies are explicit fixtures in the war-world test, with the missing real Army provider required to fail closed. The paired supply test uses actual Army/Resources services with offline player/scheduler and accepted building/war fixtures. No authenticated client, live battle, real bank-provider payment, or complete 40-addon rollout is certified.

Pilot 1 passed 53+16 before an additional predecessor-cleanup correction. Pilot 2 passed 53+16 on the final artifact. Pilot 3 adds two explicit assertions for the registered shutdown hook and closed database connection; the final artifact is unchanged. Cold tests were adapted from an ancient artificial timestamp to actual current time because expired raids now close on startup. The cancellation test now asserts release at cancellation ACK, rather than requiring a later separate release write.

All native full logs, JAR hashes and assertion files are retained. Environment-only Mojang/OSHI warnings are not hidden. Goals, surrender and funded ransom remain separate unfinished work; the new database completion boundary alone does not implement their player UI.

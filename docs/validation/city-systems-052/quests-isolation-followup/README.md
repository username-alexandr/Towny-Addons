# Quests isolation follow-up on the current addon candidates

Actual Folia26.2 build7, three fresh JVM boots in tmpfs: 24+13+12 checks, exit0. Includes Builds0.11.1, Control0.6.2 and Quests0.7.0. Exact artifact hashes are in native-results.json. The inherited probe combines the broken and restart assertion lists in restart-checks.txt; separate phase results and server logs preserve the sequence.

An unexpected Quests disable and startup failure from an invalid explicit stage type leave Citizens, Control, Builds, Districts, Population, Resources, Upkeep and Policies enabled and unfrozen. Only Quests is quarantined. Confirmed policy effects/costs and completed quest data persist across failure/recovery; new unlock-dependent actions wait until Quests recovers.

This is the targeted nine-addon regression, not the unfinished whole 40-addon pack gate. No bank transfer/network client is claimed. Completed quest and offline mayor are explicit fixtures.

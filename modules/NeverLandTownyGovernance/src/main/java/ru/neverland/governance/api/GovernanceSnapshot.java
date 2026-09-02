package ru.neverland.governance.api;

import java.util.Set;
import java.util.UUID;

public record GovernanceSnapshot(
        UUID townId,
        Set<String> activeLaws,
        int councilSize,
        int openProposals,
        double constructionCostMultiplier,
        double ideologyCostMultiplier,
        double ideologyExperienceMultiplier
) { }

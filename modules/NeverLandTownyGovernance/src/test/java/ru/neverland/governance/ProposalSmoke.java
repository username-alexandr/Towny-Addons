package ru.neverland.governance;

import ru.neverland.governance.model.Proposal;
import ru.neverland.governance.model.ProposalAction;
import ru.neverland.governance.model.ProposalStatus;
import ru.neverland.governance.model.VoteChoice;

import java.util.Map;
import java.util.UUID;

public final class ProposalSmoke {
    public static void main(String[] args) {
        UUID voter = UUID.randomUUID();
        Proposal proposal = new Proposal(UUID.randomUUID(), UUID.randomUUID(), "NeverLand", "low_taxes",
                ProposalAction.ENACT, voter, "sasha34", 100, 200, ProposalStatus.OPEN);
        proposal.vote(voter, VoteChoice.YES);
        proposal.vote(voter, VoteChoice.NO);
        proposal.vote(UUID.randomUUID(), VoteChoice.ABSTAIN);
        Map<VoteChoice, Integer> counts = proposal.counts();
        require(counts.get(VoteChoice.YES) == 0, "старый голос не заменён");
        require(counts.get(VoteChoice.NO) == 1, "новый голос не сохранён");
        require(counts.get(VoteChoice.ABSTAIN) == 1, "воздержавшийся не учтён");
        require(proposal.shortId().length() == 8, "неверная короткая форма ID");
        System.out.println("Proposal smoke test: OK");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

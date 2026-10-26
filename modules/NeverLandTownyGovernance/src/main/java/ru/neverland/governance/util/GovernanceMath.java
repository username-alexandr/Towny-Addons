package ru.neverland.governance.util;

public final class GovernanceMath {
    private GovernanceMath() { }
    public static int quorum(int electorate, double ratio) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return electorate <= 0 ? 1 : Math.max(1, (int) Math.ceil(electorate * clamp(ratio, 0, 1)));
    } }
    public static boolean passed(int yes, int no, int abstain, int electorate, double quorumRatio, double approvalRatio) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        int participation = yes + no + abstain;
        if (participation < quorum(electorate, quorumRatio) || yes + no == 0) return false;
        return yes / (double) (yes + no) >= clamp(approvalRatio, 0, 1);
    } }
    public static double clamp(double value, double minimum, double maximum) { try (var suiteGuard3 = ru.neverland.core.SuiteState.guard()) {
        return Math.max(minimum, Math.min(maximum, value));
    } }
}

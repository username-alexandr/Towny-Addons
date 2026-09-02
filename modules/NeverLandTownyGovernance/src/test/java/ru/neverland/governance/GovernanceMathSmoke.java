package ru.neverland.governance;

import ru.neverland.governance.util.GovernanceMath;

public final class GovernanceMathSmoke {
    public static void main(String[] args) {
        require(GovernanceMath.quorum(5, 0.5) == 3, "неверный кворум для пяти участников");
        require(GovernanceMath.passed(2, 1, 0, 5, 0.5, 0.5), "решение должно быть принято");
        require(!GovernanceMath.passed(2, 0, 0, 5, 0.5, 0.5), "решение не достигло кворума");
        require(!GovernanceMath.passed(1, 2, 2, 5, 0.5, 0.5), "решение не набрало большинства");
        require(GovernanceMath.passed(2, 2, 1, 5, 0.5, 0.5), "ровно 50% должно пройти при пороге 50%");
        System.out.println("GovernanceMath smoke test: OK");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

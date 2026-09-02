package ru.neverland.reputation;

import ru.neverland.reputation.util.ReputationMath;

public final class ReputationMathSmoke {
    public static void main(String[] args) {
        if (ReputationMath.clamp(1200, -1000, 1000) != 1000) throw new AssertionError();
        if (ReputationMath.clamp(-1200, -1000, 1000) != -1000) throw new AssertionError();
        if (ReputationMath.decay(500, 1, 1) != 495) throw new AssertionError();
        if (ReputationMath.decay(-500, 1, 1) != -495) throw new AssertionError();
        if (ReputationMath.decay(1, 1, 1) != 0) throw new AssertionError();
    }
}

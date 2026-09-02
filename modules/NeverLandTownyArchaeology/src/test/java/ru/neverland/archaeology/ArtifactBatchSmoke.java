package ru.neverland.archaeology;

import ru.neverland.archaeology.util.ArtifactBatchMath;

public final class ArtifactBatchSmoke {
    public static void main(String[] args) {
        if (ArtifactBatchMath.acceptedNow(64, 0, 16) != 16) throw new AssertionError("first split rejected");
        if (ArtifactBatchMath.acceptedNow(64, 16, 48) != 48) throw new AssertionError("remaining split rejected");
        if (ArtifactBatchMath.acceptedNow(64, 64, 64) != 0) throw new AssertionError("duplicate batch accepted");
        if (ArtifactBatchMath.acceptedNow(64, 60, 16) != 4) throw new AssertionError("batch capacity exceeded");
        if (ArtifactBatchMath.remaining(1, Integer.MAX_VALUE) != 0) throw new AssertionError("legacy serial accepted twice");
    }
}

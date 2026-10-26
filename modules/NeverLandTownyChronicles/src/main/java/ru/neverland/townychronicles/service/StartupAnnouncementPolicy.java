package ru.neverland.townychronicles.service;

/** Keeps the first state synchronization silent while preserving later announcements. */
public final class StartupAnnouncementPolicy {
    private StartupAnnouncementPolicy() { }

    public static boolean shouldAnnounce(boolean freshTown, boolean initialSync) { try (var suiteGuard1 = ru.neverland.core.SuiteState.guard()) {
        return !freshTown && !initialSync;
    } }

    public static boolean shouldRecordWonder(boolean discovered, boolean initialSync,
                                             boolean recordExistingWonders) { try (var suiteGuard2 = ru.neverland.core.SuiteState.guard()) {
        return discovered && (!initialSync || recordExistingWonders);
    } }
}

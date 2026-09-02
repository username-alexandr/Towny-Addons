package ru.neverland.townychronicles.service;

/** Keeps the first state synchronization silent while preserving later announcements. */
public final class StartupAnnouncementPolicy {
    private StartupAnnouncementPolicy() { }

    public static boolean shouldAnnounce(boolean freshTown, boolean initialSync) {
        return !freshTown && !initialSync;
    }

    public static boolean shouldRecordWonder(boolean discovered, boolean initialSync,
                                             boolean recordExistingWonders) {
        return discovered && (!initialSync || recordExistingWonders);
    }
}

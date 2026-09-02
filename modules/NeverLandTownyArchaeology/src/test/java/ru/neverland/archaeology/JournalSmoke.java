package ru.neverland.archaeology;

import ru.neverland.archaeology.model.PlayerJournal;

import java.util.UUID;

public final class JournalSmoke {
    public static void main(String[] args) { PlayerJournal journal = new PlayerJournal(UUID.randomUUID(), "Explorer"); if (!journal.discover("clay_seal", "one")) throw new AssertionError(); if (journal.discover("clay_seal", "one")) throw new AssertionError("same serial counted twice"); if (journal.discover("clay_seal", "two")) throw new AssertionError("second copy is not a first type discovery"); if (journal.totalFound() != 2 || journal.discoveries().get("clay_seal") != 2) throw new AssertionError(); }
}

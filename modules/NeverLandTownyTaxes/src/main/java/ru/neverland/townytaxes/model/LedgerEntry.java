package ru.neverland.townytaxes.model;

import java.util.UUID;

public record LedgerEntry(long timestamp, String action, Domain.Scope subjectScope, UUID subjectId,
                          String subjectName, double amount, String details, boolean success) {}

package ru.neverland.governance.model;

import java.util.UUID;

public record OfficeHolder(UUID residentId, String residentName, long appointedAt, UUID appointedBy) { }

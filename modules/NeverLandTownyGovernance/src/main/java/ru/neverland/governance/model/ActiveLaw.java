package ru.neverland.governance.model;

import java.util.UUID;

public record ActiveLaw(String lawId, long enactedAt, UUID enactedBy, String enactedByName) { }

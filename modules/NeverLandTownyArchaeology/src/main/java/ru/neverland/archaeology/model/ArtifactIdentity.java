package ru.neverland.archaeology.model;

import java.util.UUID;

public record ArtifactIdentity(String artifactId, UUID serial, int batchSize, boolean valid) { }

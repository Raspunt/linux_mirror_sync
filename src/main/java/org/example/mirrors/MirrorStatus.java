package org.example.mirrors;

import java.time.Instant;

public record MirrorStatus(
    String name,
    long sizeBytes,
    Instant lastSync,
    String upToDate,
    String repos
) {}

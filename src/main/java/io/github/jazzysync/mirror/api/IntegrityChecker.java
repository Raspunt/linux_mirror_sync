package io.github.jazzysync.mirror.api;

import java.nio.file.Path;
import java.util.List;

public interface IntegrityChecker {
    /**
     * Verify integrity of given files.
     * Returns list of corrupt/broken files.
     */
    List<Path> verify(List<Path> files);

    /**
     * Check if this checker is available on the current system.
     */
    boolean isAvailable();
}

package io.github.jazzysync.mirror.api;

import java.nio.file.Path;
import java.util.Map;

public interface MetadataParser {
    /**
     * Parse metadata and return map of file paths to their expected hashes.
     * Key: relative or absolute path to package file
     * Value: expected hash (hex string)
     */
    Map<Path, String> parseHashes(Path repoRoot);

    /**
     * Algorithm used for hashes (e.g. "SHA256", "SHA512", "BLAKE2B")
     */
    String getHashAlgorithm();
}

package io.github.jazzysync.mirror.api;

import java.nio.file.Path;
import java.util.List;

public interface SyncStrategy {
    List<String> buildSyncCommand(String sourceUrl, Path targetDir);
    List<String> buildCheckCommand(String sourceUrl, Path targetDir);
}

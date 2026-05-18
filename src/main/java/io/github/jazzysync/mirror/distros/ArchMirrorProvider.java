package io.github.jazzysync.mirror.distros;

import io.github.jazzysync.mirror.api.*;
import io.github.jazzysync.mirror.core.*;

import io.github.jazzysync.config.ConfigManager;
import io.github.jazzysync.logging.LogManager;

public class ArchMirrorProvider implements MirrorProvider {
    @Override
    public String getFamily() {
        return "arch";
    }

    @Override
    public IMirror create(String name, ConfigManager config, LogManager logger) {
        String sourceUrl = config.getDistroSourceUrl(name);
        java.nio.file.Path targetDir = config.getDistroTarget(name);
        java.nio.file.Path logDir = logger.getLogDir();
        var excludes = config.getDistroExcludes(name);
        var repos = config.getDistroRepos(name);
        return new ArchMirror(sourceUrl, targetDir, logDir, logger, excludes, repos);
    }
}

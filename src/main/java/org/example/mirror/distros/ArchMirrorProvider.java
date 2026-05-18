package org.example.mirror.distros;

import org.example.mirror.api.*;
import org.example.mirror.core.*;

import org.example.config.ConfigManager;
import org.example.logging.LogManager;

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

package io.github.jazzysync.mirror.distros;

import io.github.jazzysync.mirror.api.*;
import io.github.jazzysync.mirror.core.*;

import io.github.jazzysync.config.ConfigManager;
import io.github.jazzysync.logging.LogManager;

public class FedoraMirrorProvider implements MirrorProvider {
    @Override
    public String getFamily() {
        return "fedora";
    }

    @Override
    public IMirror create(String name, ConfigManager config, LogManager logger) {
        var sourceUrls = config.getDistroSourceUrls(name);
        java.nio.file.Path targetDir = config.getDistroTarget(name);
        var repos = config.getDistroRepos(name);
        return new FedoraMirror(sourceUrls, targetDir, logger, repos);
    }
}

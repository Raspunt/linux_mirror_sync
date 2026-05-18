package org.example.mirror.distros;

import org.example.mirror.api.*;
import org.example.mirror.core.*;

import org.example.config.ConfigManager;
import org.example.logging.LogManager;

public class FedoraMirrorProvider implements MirrorProvider {
    @Override
    public String getFamily() {
        return "fedora";
    }

    @Override
    public IMirror create(String name, ConfigManager config, LogManager logger) {
        String sourceUrl = config.getDistroSourceUrl(name);
        java.nio.file.Path targetDir = config.getDistroTarget(name);
        var repos = config.getDistroRepos(name);
        return new FedoraMirror(sourceUrl, targetDir, logger, repos);
    }
}

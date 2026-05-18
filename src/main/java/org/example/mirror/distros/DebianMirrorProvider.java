package org.example.mirror.distros;

import org.example.mirror.api.*;
import org.example.mirror.core.*;

import org.example.config.ConfigManager;
import org.example.logging.LogManager;

public class DebianMirrorProvider implements MirrorProvider {
    @Override
    public String getFamily() {
        return "debian";
    }

    @Override
    public IMirror create(String name, ConfigManager config, LogManager logger) {
        var props = config.getDistroProperties(name);
        String host = config.getDistroProperty(name, "host", "mirror.yandex.ru");
        String root = config.getDistroProperty(name, "root", "/debian");
        String dist = config.getDistroProperty(name, "dist", "trixie");
        String arch = config.getDistroProperty(name, "arch", "amd64");
        String section = config.getDistroProperty(name, "section", "main,contrib,non-free,non-free-firmware");

        java.nio.file.Path targetDir = config.getDistroTarget(name);
        var repos = config.getDistroRepos(name);

        return new DebianMirror(host, root, dist, arch, section, targetDir, logger, repos);
    }
}

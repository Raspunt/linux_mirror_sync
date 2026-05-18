package org.example.mirrors;

import org.example.ConfigManager;
import org.example.LogManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MirrorFactory {
    private final ConfigManager config;
    private final LogManager logger;

    public MirrorFactory(ConfigManager config, LogManager logger) {
        this.config = config;
        this.logger = logger;
    }

    public IMirror create(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!config.isDistroEnabled(key)) {
            throw new IllegalArgumentException("Distribution '" + name + "' is disabled in config");
        }
        Path targetDir = config.getDistroTarget(key);

        return switch (key) {
            case "arch", "archlinux" -> {
                String sourceUrl = config.getDistroSourceUrl(key);
                List<String> excludes = config.getDistroExcludes(key);
                List<String> repos = config.getDistroRepos(key);
                yield new ArchMirror(sourceUrl, targetDir, logger.getLogDir(), logger, excludes, repos);
            }
            case "debian" -> {
                String host = config.getDebianHost();
                String root = config.getDebianRoot();
                String dist = config.getDebianDist();
                String arch = config.getDebianArch();
                String section = config.getDebianSection();
                List<String> repos = config.getDistroRepos(key);
                yield new DebianMirror(host, root, dist, arch, section, targetDir, logger, repos);
            }
            default -> throw new IllegalArgumentException("Unknown distribution: " + name);
        };
    }

    public List<IMirror> createAll() {
        List<IMirror> mirrors = new ArrayList<>();
        for (String distro : availableDistros()) {
            if (config.isDistroEnabled(distro)) {
                try {
                    mirrors.add(create(distro));
                } catch (IllegalArgumentException e) {
                    logger.warn("Skipping " + distro + ": " + e.getMessage());
                }
            }
        }
        return mirrors;
    }

    public static List<String> availableDistros() {
        return List.of("arch", "debian");
    }
}

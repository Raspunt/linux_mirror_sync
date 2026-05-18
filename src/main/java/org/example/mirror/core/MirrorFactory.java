package org.example.mirror.core;

import org.example.mirror.api.*;
import org.example.mirror.distros.ArchMirrorProvider;
import org.example.mirror.distros.DebianMirrorProvider;
import org.example.mirror.distros.FedoraMirrorProvider;

import org.example.config.ConfigManager;
import org.example.logging.LogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MirrorFactory {
    private final ConfigManager config;
    private final LogManager logger;
    private final List<MirrorProvider> providers = new ArrayList<>();

    public MirrorFactory(ConfigManager config, LogManager logger) {
        this.config = config;
        this.logger = logger;
        registerDefaults();
    }

    private void registerDefaults() {
        providers.add(new ArchMirrorProvider());
        providers.add(new DebianMirrorProvider());
        providers.add(new FedoraMirrorProvider());
    }

    public void register(MirrorProvider provider) {
        providers.add(provider);
    }

    public IMirror create(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!config.isDistroEnabled(key)) {
            throw new IllegalArgumentException("Distribution '" + name + "' is disabled in config");
        }

        String family = config.getDistroFamily(key);
        if (family == null || family.isBlank()) {
            throw new IllegalArgumentException("Distribution '" + name + "' has no family configured");
        }

        for (MirrorProvider provider : providers) {
            if (provider.getFamily().equalsIgnoreCase(family)) {
                return provider.create(key, config, logger);
            }
        }

        throw new IllegalArgumentException(
            "No provider registered for family '" + family + "'. " +
            "Available families: " + providers.stream().map(MirrorProvider::getFamily).toList()
        );
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
        return List.of("arch", "debian", "fedora");
    }
}

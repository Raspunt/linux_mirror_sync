package io.github.jazzysync.mirror.api;

import io.github.jazzysync.config.ConfigManager;
import io.github.jazzysync.logging.LogManager;

public interface MirrorProvider {
    String getFamily();
    IMirror create(String name, ConfigManager config, LogManager logger);
}

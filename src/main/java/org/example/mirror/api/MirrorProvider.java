package org.example.mirror.api;

import org.example.config.ConfigManager;
import org.example.logging.LogManager;

public interface MirrorProvider {
    String getFamily();
    IMirror create(String name, ConfigManager config, LogManager logger);
}

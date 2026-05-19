package io.github.jazzysync.config;

import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final String CONFIG_DIR = "~/.config/jazzy";
    private static final String CONFIG_FILE = "config.toml";

    private final AppConfig config;
    private final Path targetDir;
    private final Path logDir;

    public ConfigManager() {
        this.config = loadOrCreateConfig();
        this.targetDir = expandHome(config.getTargetDir());
        this.logDir = expandHome(config.getLogDir());
    }

    public ConfigManager(String targetDirOverride) {
        this.config = loadOrCreateConfig();
        this.targetDir = expandHome(targetDirOverride);
        this.logDir = expandHome(config.getLogDir());
    }

    public ConfigManager(AppConfig config) {
        this.config = config;
        this.targetDir = expandHome(config.getTargetDir());
        this.logDir = expandHome(config.getLogDir());
    }

    private static AppConfig loadOrCreateConfig() {
        Path configDir = expandHome(Paths.get(CONFIG_DIR));
        Path configFile = configDir.resolve(CONFIG_FILE);

        if (Files.exists(configFile)) {
            try {
                Toml toml = new Toml().read(configFile.toFile());
                return toml.to(AppConfig.class);
            } catch (Exception e) {
                System.err.println("Warning: failed to read config, using defaults. " + e.getMessage());
                return new AppConfig();
            }
        } else {
            AppConfig defaults = new AppConfig();
            try {
                Files.createDirectories(configDir);
                new TomlWriter().write(defaults, configFile.toFile());
                System.out.println("Created default config: " + configFile);
            } catch (IOException e) {
                System.err.println("Warning: failed to create default config. " + e.getMessage());
            }
            return defaults;
        }
    }

    private static Path expandHome(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            String home = System.getenv("HOME");
            if (home == null) {
                home = System.getProperty("user.home");
            }
            path = home + path.substring(1);
        }
        return Paths.get(path);
    }

    private static Path expandHome(Path path) {
        return expandHome(path.toString());
    }

    public String getBaseUrl() {
        String url = config.getBaseUrl();
        return url.endsWith("/") ? url : url + "/";
    }

    public Path getTargetDir() {
        return targetDir;
    }

    public Path getLogDir() {
        return logDir;
    }

    public Path getDistroTarget(String distro) {
        String subdir = switch (distro) {
            case "arch", "archlinux" -> "archlinux";
            case "debian" -> "debian";
            default -> distro;
        };
        return targetDir.resolve(subdir);
    }

    public String getDistroBaseUrl(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        if (dc == null) {
            throw new IllegalArgumentException("Unknown distro in config: " + distro);
        }
        String url = dc.getBaseUrl();
        if (url != null && !url.isBlank()) {
            return url.endsWith("/") ? url : url + "/";
        }
        return getBaseUrl();
    }

    public String getDistroSourceUrl(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        if (dc == null) {
            throw new IllegalArgumentException("Unknown distro in config: " + distro);
        }
        String path = dc.getSourcePath();
        return getDistroBaseUrl(distro) + (path.endsWith("/") ? path : path + "/");
    }

    public List<String> getDistroSourceUrls(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        if (dc == null) {
            throw new IllegalArgumentException("Unknown distro in config: " + distro);
        }
        String base = getDistroBaseUrl(distro);
        List<String> paths = dc.getSourcePaths();
        if (paths != null && !paths.isEmpty()) {
            return paths.stream()
                .map(p -> base + (p.endsWith("/") ? p : p + "/"))
                .toList();
        }
        String path = dc.getSourcePath();
        return List.of(base + (path.endsWith("/") ? path : path + "/"));
    }

    public boolean isDistroEnabled(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        return dc != null && dc.isEnabled();
    }

    public AppConfig.DistroConfig getDistroConfig(String distro) {
        AppConfig.DistroConfig dc = config.getDistros().get(distro);
        if (dc == null) {
            throw new IllegalArgumentException("Unknown distro in config: " + distro);
        }
        return dc;
    }

    public String getDistroFamily(String distro) {
        AppConfig.DistroConfig dc = getDistroConfig(distro);
        String family = dc.getFamily();
        if (family != null && !family.isBlank()) {
            return family;
        }
        // Fallback for legacy configs without family field
        return switch (distro) {
            case "arch", "archlinux", "manjaro" -> "arch";
            case "debian", "ubuntu", "mint" -> "debian";
            default -> distro;
        };
    }

    public List<String> getDistroRepos(String distro) {
        AppConfig.DistroConfig dc = getDistroConfig(distro);
        if (dc.getRepos() == null) {
            return List.of();
        }
        return dc.getRepos();
    }

    public List<String> getDistroExcludes(String distro) {
        AppConfig.DistroConfig dc = getDistroConfig(distro);
        if (dc.getExcludes() == null) {
            return List.of();
        }
        return dc.getExcludes();
    }

    public Map<String, String> getDistroProperties(String distro) {
        AppConfig.DistroConfig dc = getDistroConfig(distro);
        if (dc.getProperties() == null) {
            return Map.of();
        }
        return dc.getProperties();
    }

    public String getDistroProperty(String distro, String key, String defaultValue) {
        return getDistroProperties(distro).getOrDefault(key, defaultValue);
    }

    public AppConfig getRawConfig() {
        return config;
    }
}

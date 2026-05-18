package org.example;

import java.util.List;
import java.util.Map;

public class AppConfig {
    private String baseUrl = "rsync://mirror.yandex.ru/";
    private String targetDir = "~/mirrors";
    private String logDir = "~/.cache/linux_mirror_sync";
    private Map<String, DistroConfig> distros = Map.of(
            "arch", new DistroConfig("archlinux/", true),
            "debian", new DistroConfig("debian/", true)
    );

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getTargetDir() { return targetDir; }
    public void setTargetDir(String targetDir) { this.targetDir = targetDir; }

    public String getLogDir() { return logDir; }
    public void setLogDir(String logDir) { this.logDir = logDir; }

    public Map<String, DistroConfig> getDistros() { return distros; }
    public void setDistros(Map<String, DistroConfig> distros) { this.distros = distros; }

    public static class DistroConfig {
        private String sourcePath;
        private boolean enabled = true;

        // Arch specific
        private String rsyncUrl;

        // Debian specific
        private String sourceHost;
        private String sourceRoot;
        private String dist;
        private String arch;
        private String section;

        // Exclude patterns (e.g. "iso", "core-testing")
        private List<String> excludes;

        // White list of repos/branches to sync (e.g. "core", "extra", "multilib")
        private List<String> repos;

        public DistroConfig() {}

        public DistroConfig(String sourcePath, boolean enabled) {
            this.sourcePath = sourcePath;
            this.enabled = enabled;
        }

        public String getSourcePath() { return sourcePath; }
        public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getRsyncUrl() { return rsyncUrl; }
        public void setRsyncUrl(String rsyncUrl) { this.rsyncUrl = rsyncUrl; }

        public String getSourceHost() { return sourceHost; }
        public void setSourceHost(String sourceHost) { this.sourceHost = sourceHost; }

        public String getSourceRoot() { return sourceRoot; }
        public void setSourceRoot(String sourceRoot) { this.sourceRoot = sourceRoot; }

        public String getDist() { return dist; }
        public void setDist(String dist) { this.dist = dist; }

        public String getArch() { return arch; }
        public void setArch(String arch) { this.arch = arch; }

        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }

        public List<String> getExcludes() { return excludes; }
        public void setExcludes(List<String> excludes) { this.excludes = excludes; }

        public List<String> getRepos() { return repos; }
        public void setRepos(List<String> repos) { this.repos = repos; }
    }
}

package org.example.mirrors;

import org.example.LogManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DebianMirror extends AbstractMirror {
    private final String sourceHost;
    private final String sourceRoot;
    private final String dist;
    private final String arch;
    private final String section;
    private final List<String> repos;

    public DebianMirror(String sourceHost, String sourceRoot, String dist, String arch, String section,
                        Path targetDir, LogManager logger, List<String> repos) {
        super("debian", targetDir, logger);
        this.sourceHost = sourceHost;
        this.sourceRoot = sourceRoot.startsWith("/") ? sourceRoot : "/" + sourceRoot;
        this.dist = dist;
        this.arch = arch;
        this.section = section;
        this.repos = repos != null ? repos : List.of();
    }

    @Override
    public void checkDependencies() {
        try {
            ProcessResult r = runProcess(List.of("sh", "-c", "command -v debmirror && command -v dpkg-deb"), 1);
            if (r.exitCode() != 0) {
                logger.error("[" + name + "] error: debmirror and/or dpkg-deb are not installed.");
                logger.error("[" + name + "] Install: yay -S debmirror; sudo pacman -S dpkg");
                throw new RuntimeException("Missing dependencies: debmirror and/or dpkg-deb");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to check dependencies", e);
        }
    }

    @Override
    public void syncRepo() {
        logger.info("[" + name + "] syncing Debian " + dist + "...");
        checkDependencies();
        try {
            ensureTargetDir();
            List<String> cmd = buildDebmirrorCommand(false);
            ProcessResult result = runProcess(cmd, 180);
            if (result.exitCode() == 0) {
                logger.info("[" + name + "] done");
            } else {
                logger.error("[" + name + "] Synchronization failed with exit code: " + result.exitCode());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("[" + name + "] Synchronization error: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void checkRepo() {
        logger.info("[" + name + "] checking Debian " + dist + " for updates...");
        checkDependencies();
        try {
            List<String> cmd = buildDebmirrorCommand(true);
            ProcessResult result = runProcess(cmd, 60);
            if (result.exitCode() == 0) {
                logger.info("[" + name + "] Check completed");
            } else {
                logger.error("[" + name + "] Check failed with exit code: " + result.exitCode());
            }
        } catch (IOException | InterruptedException e) {
            logger.error("[" + name + "] Check error: " + e.getMessage());
        }
    }

    @Override
    public void verifyRepo() {
        Path pool = targetDir.resolve("pool");
        if (!Files.isDirectory(pool)) {
            logger.error("[" + name + "] error: " + pool + " not found");
            return;
        }
        logger.info("[" + name + "] verifying all .deb files in " + pool + " ...");
        try {
            List<Path> packages = Files.walk(pool)
                .filter(p -> p.toString().endsWith(".deb"))
                .collect(Collectors.toList());

            if (packages.isEmpty()) {
                logger.info("[" + name + "] no .deb files found");
                return;
            }

            PackageVerifier verifier = new PackageVerifier(logger, name);
            List<Path> bad = verifier.verify(packages, List.of("dpkg-deb", "-I"), 60);

            if (bad.isEmpty()) {
                logger.info("[" + name + "] all .deb files are healthy");
            } else {
                logger.warn("[" + name + "] found " + bad.size() + " corrupt package(s):");
                for (Path p : bad) {
                    logger.warn("  CORRUPT: " + p);
                }
                logger.warn("[" + name + "] run 'lms fix -d debian' to remove and re-download them");
                logPackageWord(bad.size());
            }
        } catch (IOException e) {
            logger.error("[" + name + "] Verification error: " + e.getMessage());
        }
    }

    @Override
    public void fixRepo() {
        Path pool = targetDir.resolve("pool");
        if (!Files.isDirectory(pool)) {
            logger.error("[" + name + "] error: " + pool + " not found");
            return;
        }
        logger.info("[" + name + "] scanning for corrupt packages in " + pool + " ...");
        try {
            List<Path> packages = Files.walk(pool)
                .filter(p -> p.toString().endsWith(".deb"))
                .collect(Collectors.toList());

            PackageVerifier verifier = new PackageVerifier(logger, name);
            List<Path> bad = verifier.verify(packages, List.of("dpkg-deb", "-I"), 60);

            if (bad.isEmpty()) {
                logger.info("[" + name + "] all .deb files are healthy, nothing to fix");
                return;
            }

            logger.info("[" + name + "] found corrupt packages, removing...");
            for (Path p : bad) {
                logger.info("[" + name + "] REMOVING: " + p);
                Files.deleteIfExists(p);
            }

            logger.info("[" + name + "] re-downloading missing packages...");
            syncRepo();

            logger.info("[" + name + "] fix done");
        } catch (IOException e) {
            logger.error("[" + name + "] Fix error: " + e.getMessage());
        }
    }

    @Override
    protected Path getLastSyncFile() {
        return targetDir.resolve("dists").resolve(dist).resolve("Release");
    }

    @Override
    protected String checkUpToDateStatus() {
        Instant lastSync = getLastSyncTime(getLastSyncFile());
        if (lastSync == null) return "unknown";
        long days = Duration.between(lastSync, Instant.now()).toDays();
        if (days < 2) return "yes";
        if (days < 7) return "likely";
        return "stale (" + days + "d)";
    }

    @Override
    protected String getReposDisplay() {
        return repos.isEmpty() ? section : String.join(", ", repos);
    }

    private List<String> buildDebmirrorCommand(boolean dryRun) {
        String effectiveSection = repos.isEmpty() ? section : String.join(",", repos);
        List<String> cmd = new ArrayList<>(List.of(
            "debmirror",
            "--host=" + sourceHost,
            "--root=" + sourceRoot,
            "--method=rsync",
            "--dist=" + dist,
            "--arch=" + arch,
            "--section=" + effectiveSection,
            "--getcontents",
            "--nosource",
            "--progress",
            "--ignore-release-gpg",
            targetDir.toString()
        ));
        if (dryRun) {
            cmd.add("--dry-run");
        }
        return cmd;
    }

    private void logPackageWord(int count) {
        String word;
        if (count % 10 == 1 && count % 100 != 11) {
            word = "пакет";
        } else if (count % 10 >= 2 && count % 10 <= 4 && (count % 100 < 10 || count % 100 >= 20)) {
            word = "пакета";
        } else {
            word = "пакетов";
        }
        logger.warn("[" + name + "] " + count + " " + word + " сломано");
    }
}

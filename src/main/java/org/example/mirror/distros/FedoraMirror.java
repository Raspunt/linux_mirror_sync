package org.example.mirror.distros;

import org.example.mirror.api.*;
import org.example.mirror.core.*;
import org.example.mirror.strategies.*;
import org.example.mirror.checkers.*;

import org.example.logging.LogManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FedoraMirror extends AbstractMirror {
    private final String sourceUrl;
    private final List<String> repos;

    public FedoraMirror(String sourceUrl, Path targetDir, LogManager logger, List<String> repos) {
        super("fedora", targetDir, logger, new RsyncStrategy(), buildCheckers(logger));
        this.sourceUrl = sourceUrl.endsWith("/") ? sourceUrl : sourceUrl + "/";
        this.repos = repos != null ? repos : List.of();
    }

    private static List<IntegrityChecker> buildCheckers(LogManager logger) {
        List<IntegrityChecker> checkers = new ArrayList<>();
        var rpmChecker = new ExternalToolChecker(List.of("rpm", "-K", "--nosignature"), 60, logger, "fedora");
        if (rpmChecker.isAvailable()) {
            checkers.add(rpmChecker);
        }
        return checkers;
    }

    @Override
    public void checkDependencies() {
        try {
            ProcessResult r = runProcess(List.of("sh", "-c", "command -v rsync"), 1);
            if (r.exitCode() != 0) {
                throw new RuntimeException("Missing dependency: rsync is not installed");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to check dependencies", e);
        }
    }

    @Override
    public void syncRepo() {
        logger.info("[" + name + "] syncing Fedora from " + sourceUrl + " to " + targetDir);
        try {
            ensureTargetDir();
            List<String> cmd = syncStrategy.buildSyncCommand(sourceUrl, targetDir);
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
        logger.info("[" + name + "] checking Fedora for updates...");
        try {
            List<String> cmd = syncStrategy.buildCheckCommand(sourceUrl, targetDir);
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
        logger.info("[" + name + "] verifying all .rpm files in " + targetDir + " ...");
        try {
            List<Path> packages = Files.walk(targetDir)
                .filter(p -> p.toString().endsWith(".rpm"))
                .collect(Collectors.toList());

            if (packages.isEmpty()) {
                logger.info("[" + name + "] no .rpm files found");
                return;
            }

            if (integrityCheckers.isEmpty()) {
                logger.info("[" + name + "] rpm not available, skipping integrity check for " + packages.size() + " package(s)");
                return;
            }

            List<Path> bad = verifyWithCheckers(packages);

            if (bad.isEmpty()) {
                logger.info("[" + name + "] all .rpm files are healthy");
            } else {
                logger.warn("[" + name + "] found " + bad.size() + " corrupt package(s):");
                for (Path p : bad) {
                    logger.warn("  CORRUPT: " + p);
                }
                logger.warn("[" + name + "] run 'lms fix fedora' to remove and re-download them");
            }
        } catch (IOException e) {
            logger.error("[" + name + "] Verification error: " + e.getMessage());
        }
    }

    @Override
    public void fixRepo() {
        logger.info("[" + name + "] scanning for corrupt packages in " + targetDir + " ...");
        try {
            List<Path> packages = Files.walk(targetDir)
                .filter(p -> p.toString().endsWith(".rpm"))
                .collect(Collectors.toList());

            if (integrityCheckers.isEmpty()) {
                logger.info("[" + name + "] rpm not available, cannot verify packages; running sync to ensure consistency");
                syncRepo();
                return;
            }

            List<Path> bad = verifyWithCheckers(packages);

            if (bad.isEmpty()) {
                logger.info("[" + name + "] all .rpm files are healthy, nothing to fix");
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
        try {
            return Files.walk(targetDir)
                .filter(p -> p.getFileName().toString().equals("repomd.xml"))
                .findFirst()
                .orElse(targetDir);
        } catch (IOException e) {
            return targetDir;
        }
    }

    @Override
    protected String checkUpToDateStatus() {
        try {
            Path localRepomd = getLastSyncFile();
            if (!Files.exists(localRepomd) || localRepomd.equals(targetDir)) {
                return "unknown";
            }

            Path tmpDir = Files.createTempDirectory("fedora-check-");
            try {
                Path tmpRepomd = tmpDir.resolve("repomd.xml");
                String remotePath = sourceUrl + "repodata/repomd.xml";
                // If repomd.xml is nested (e.g. Everything/x86_64/os/repodata/),
                // sourceUrl already includes that path, so remotePath is correct.
                ProcessResult r = runProcessQuiet(List.of(
                    "rsync", "--contimeout=60", "--timeout=120",
                    remotePath, tmpRepomd.toString()
                ), 5);
                if (r.exitCode() != 0) {
                    return "unknown";
                }
                long localSize = Files.size(localRepomd);
                long remoteSize = Files.size(tmpRepomd);
                return localSize == remoteSize ? "yes" : "no";
            } finally {
                deleteDir(tmpDir);
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
    }

    @Override
    protected String getReposDisplay() {
        return repos.isEmpty() ? "all" : String.join(", ", repos);
    }
}

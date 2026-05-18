package org.example.mirror.core;

import org.example.mirror.api.*;

import org.example.logging.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class AbstractMirror implements IMirror {
    protected final String name;
    protected final Path targetDir;
    protected final LogManager logger;
    protected final SyncStrategy syncStrategy;
    protected final List<IntegrityChecker> integrityCheckers;

    protected AbstractMirror(String name, Path targetDir, LogManager logger,
                             SyncStrategy syncStrategy, List<IntegrityChecker> integrityCheckers) {
        this.name = name;
        this.targetDir = targetDir;
        this.logger = logger;
        this.syncStrategy = syncStrategy;
        this.integrityCheckers = integrityCheckers != null ? integrityCheckers : List.of();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void checkDependencies() {
        // override where needed
    }

    protected void ensureTargetDir() throws IOException {
        Files.createDirectories(targetDir);
    }

    protected ProcessResult runProcess(List<String> command, long timeoutMinutes) throws IOException, InterruptedException {
        logger.info("[" + name + "] Executing: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                logger.info("[" + name + "] " + line);
            }
        }

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            logger.error("[" + name + "] Process timed out after " + timeoutMinutes + " minutes");
            return new ProcessResult(-1, output);
        }
        return new ProcessResult(process.exitValue(), output);
    }

    protected ProcessResult runProcess(List<String> command) throws IOException, InterruptedException {
        return runProcess(command, 30);
    }

    protected ProcessResult runProcessQuiet(List<String> command, long timeoutMinutes) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
            }
        }

        boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(-1, output);
        }
        return new ProcessResult(process.exitValue(), output);
    }

    protected long getDirSize(Path dir) {
        try {
            ProcessResult r = runProcessQuiet(List.of("du", "-sb", dir.toString()), 1);
            if (r.exitCode() == 0 && !r.output().isEmpty()) {
                String line = r.output().get(0).trim();
                String[] parts = line.split("\\s+");
                return Long.parseLong(parts[0]);
            }
        } catch (Exception e) {
            logger.warn("[" + name + "] Failed to get directory size: " + e.getMessage());
        }
        return -1;
    }

    protected Instant getLastSyncTime(Path file) {
        try {
            if (Files.exists(file)) {
                return Files.getLastModifiedTime(file).toInstant();
            }
        } catch (IOException e) {
            logger.warn("[" + name + "] Failed to get last sync time: " + e.getMessage());
        }
        return null;
    }

    public final MirrorStatus getStatus() {
        long size = getDirSize(targetDir);
        Instant lastSync = getLastSyncTime(getLastSyncFile());
        String upToDate = checkUpToDateStatus();
        String repos = getReposDisplay();
        return new MirrorStatus(name, size, lastSync, upToDate, repos);
    }

    protected abstract Path getLastSyncFile();
    protected abstract String checkUpToDateStatus();
    protected abstract String getReposDisplay();

    protected List<Path> verifyWithCheckers(List<Path> files) {
        for (IntegrityChecker checker : integrityCheckers) {
            if (checker.isAvailable()) {
                return checker.verify(files);
            }
        }
        logger.warn("[" + name + "] No integrity checker available");
        return List.of();
    }

    protected record ProcessResult(int exitCode, List<String> output) {}
}

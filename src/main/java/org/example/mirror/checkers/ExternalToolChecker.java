package org.example.mirror.checkers;

import org.example.mirror.api.*;

import org.example.logging.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ExternalToolChecker implements IntegrityChecker {
    private final List<String> checkCommand;
    private final int timeoutSeconds;
    private final LogManager logger;
    private final String mirrorName;

    public ExternalToolChecker(List<String> checkCommand, int timeoutSeconds, LogManager logger, String mirrorName) {
        this.checkCommand = checkCommand;
        this.timeoutSeconds = timeoutSeconds;
        this.logger = logger;
        this.mirrorName = mirrorName;
    }

    @Override
    public boolean isAvailable() {
        if (checkCommand.isEmpty()) return false;
        String tool = checkCommand.get(0);
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", "command -v " + tool);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<Path> verify(List<Path> files) {
        if (files.isEmpty()) return List.of();

        int total = files.size();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<Path>> futures = new ArrayList<>();
        AtomicInteger done = new AtomicInteger(0);

        for (Path file : files) {
            futures.add(executor.submit(() -> {
                try {
                    List<String> cmd = new ArrayList<>(checkCommand);
                    cmd.add(file.toString());
                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        return file;
                    }
                    int exitCode = process.exitValue();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        while (reader.readLine() != null) {}
                    }

                    if (exitCode != 0) {
                        return file;
                    }
                    return null;
                } catch (IOException | InterruptedException e) {
                    return file;
                } finally {
                    done.incrementAndGet();
                }
            }));
        }

        ScheduledExecutorService progressExecutor = Executors.newSingleThreadScheduledExecutor();
        progressExecutor.scheduleAtFixedRate(() -> {
            int d = done.get();
            int pct = total > 0 ? d * 100 / total : 0;
            System.out.printf("\r[%s]   checked %d / %d (%d%%)", mirrorName, d, total, pct);
            System.out.flush();
        }, 1, 1, TimeUnit.SECONDS);

        List<Path> bad = new ArrayList<>();
        for (Future<Path> f : futures) {
            try {
                Path result = f.get();
                if (result != null) {
                    bad.add(result);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                // ignore
            }
        }

        progressExecutor.shutdownNow();
        executor.shutdown();
        System.out.println();

        return bad;
    }
}

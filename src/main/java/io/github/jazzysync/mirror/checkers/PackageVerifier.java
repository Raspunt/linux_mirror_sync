package io.github.jazzysync.mirror.checkers;

import io.github.jazzysync.mirror.api.*;

import io.github.jazzysync.logging.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PackageVerifier {
    private final LogManager logger;
    private final String mirrorName;

    public PackageVerifier(LogManager logger, String mirrorName) {
        this.logger = logger;
        this.mirrorName = mirrorName;
    }

    public List<Path> verify(List<Path> files, List<String> checkCommand, int timeoutSeconds) {
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

                    // consume output to prevent deadlock
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

        // progress reporter
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
                // ignore, file already counted as bad
            }
        }

        progressExecutor.shutdownNow();
        executor.shutdown();
        System.out.println(); // newline after progress

        return bad;
    }
}

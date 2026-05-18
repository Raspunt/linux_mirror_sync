package org.example.mirror.checkers;

import org.example.mirror.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ArTarChecker implements IntegrityChecker {
    private final int timeoutSeconds;

    public ArTarChecker(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public boolean isAvailable() {
        try {
            return runCheck(List.of("sh", "-c", "command -v ar && command -v tar"), 5) == 0;
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
                    Path tmpDir = Files.createTempDirectory("deb-check-");
                    try {
                        // Extract control.tar.* from .deb
                        int arExit = runCheck(List.of("ar", "x", file.toString()), tmpDir, timeoutSeconds);
                        if (arExit != 0) return file;

                        // Find control.tar.* and verify it
                        var controls = Files.list(tmpDir)
                            .filter(p -> p.getFileName().toString().startsWith("control.tar"))
                            .toList();
                        if (controls.isEmpty()) return file;

                        int tarExit = runCheck(List.of("tar", "tf", controls.get(0).toString()), tmpDir, timeoutSeconds);
                        if (tarExit != 0) return file;

                        return null;
                    } finally {
                        deleteDir(tmpDir);
                        done.incrementAndGet();
                    }
                } catch (IOException | InterruptedException e) {
                    return file;
                }
            }));
        }

        ScheduledExecutorService progress = Executors.newSingleThreadScheduledExecutor();
        progress.scheduleAtFixedRate(() -> {
            int d = done.get();
            int pct = total > 0 ? d * 100 / total : 0;
            System.out.printf("\r[debian-ar]   checked %d / %d (%d%%)", d, total, pct);
            System.out.flush();
        }, 1, 1, TimeUnit.SECONDS);

        List<Path> bad = new ArrayList<>();
        for (Future<Path> f : futures) {
            try {
                Path r = f.get();
                if (r != null) bad.add(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                // ignore
            }
        }

        progress.shutdownNow();
        executor.shutdown();
        System.out.println();
        return bad;
    }

    private int runCheck(List<String> cmd, long timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            while (reader.readLine() != null) {}
        }
        boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            return -1;
        }
        return p.exitValue();
    }

    private int runCheck(List<String> cmd, Path workingDir, long timeout) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            while (reader.readLine() != null) {}
        }
        boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            return -1;
        }
        return p.exitValue();
    }

    private void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
    }
}

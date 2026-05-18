package org.example.mirrors;

import org.example.LogManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ArchMirror extends AbstractMirror {
    private final String sourceUrl;
    private final Path cacheDir;
    private final Path logDir;
    private final List<String> excludes;
    private final List<String> repos;

    public ArchMirror(String sourceUrl, Path targetDir, Path logDir, LogManager logger,
                      List<String> excludes, List<String> repos) {
        super("archlinux", targetDir, logger);
        this.sourceUrl = sourceUrl.endsWith("/") ? sourceUrl : sourceUrl + "/";
        this.cacheDir = targetDir.resolve(".cache");
        this.logDir = logDir;
        this.excludes = excludes != null ? excludes : List.of();
        this.repos = repos != null ? repos : List.of();
    }

    @Override
    public void checkDependencies() {
        try {
            ProcessResult r = runProcess(List.of("sh", "-c", "command -v rsync && command -v zstd"), 1);
            if (r.exitCode() != 0) {
                throw new RuntimeException("Missing dependencies: rsync and/or zstd are not installed");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to check dependencies", e);
        }
    }

    @Override
    public void checkRepo() {
        logger.info("[" + name + "] checking for updates...");
        try {
            String remote = fetchLastUpdate();
            if (remote == null || remote.isBlank()) {
                logger.error("[" + name + "] cannot fetch lastupdate");
                return;
            }
            logger.info("[" + name + "] remote lastupdate: " + remote);

            Path lastUpdateFile = cacheDir.resolve("lastupdate");
            String local = Files.exists(lastUpdateFile) ? Files.readString(lastUpdateFile).trim() : "";

            if (remote.equals(local)) {
                logger.info("[" + name + "] up to date");
                return;
            }

            int changed = countChangedPackages();
            if (changed > 0) {
                logger.info("[" + name + "] updates available, " + changed + " packages need to update");
            } else {
                logger.info("[" + name + "] updates available (metadata only, no packages)");
            }
        } catch (IOException | InterruptedException e) {
            logger.error("[" + name + "] Check error: " + e.getMessage());
        }
    }

    @Override
    public void syncRepo() {
        logger.info("[" + name + "] Starting synchronization from " + sourceUrl + " to " + targetDir);
        try {
            ensureTargetDir();
            Files.createDirectories(cacheDir);
            Files.createDirectories(logDir);

            if (isUpToDate()) {
                logger.info("[" + name + "] up to date, nothing to do");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path logFile = logDir.resolve("sync-" + timestamp + ".log");

            List<String> cmd = new ArrayList<>(List.of(
                "rsync", "--contimeout=60", "--timeout=300",
                "-rtlvHv", "--safe-links", "--delete-after",
                "--delay-updates", "--no-motd", "--partial",
                "--info=progress2",
                "--log-file=" + logFile.toString(),
                "--log-file-format=%f"
            ));
            cmd.addAll(buildFilterArgs());
            cmd.add(sourceUrl);
            cmd.add(targetDir.toString());

            ProcessResult result = runProcess(cmd, 120);
            if (result.exitCode() != 0) {
                logger.error("[" + name + "] Synchronization failed with exit code: " + result.exitCode());
                return;
            }

            // update lastupdate
            runProcess(List.of("rsync", "--contimeout=60", "--timeout=120",
                sourceUrl + "lastupdate", cacheDir.resolve("lastupdate").toString()), 5);

            verifyChanged(logFile);
            logger.info("[" + name + "] done");
        } catch (IOException | InterruptedException e) {
            logger.error("[" + name + "] Synchronization error: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void verifyRepo() {
        Path pool = targetDir.resolve("pool");
        if (!Files.isDirectory(pool)) {
            logger.error("[" + name + "] error: " + pool + " not found");
            return;
        }
        logger.info("[" + name + "] verifying all packages in " + pool + " ...");
        try {
            List<Path> packages = Files.walk(pool)
                .filter(p -> p.toString().endsWith(".pkg.tar.zst"))
                .collect(Collectors.toList());

            if (packages.isEmpty()) {
                logger.info("[" + name + "] no packages found");
                return;
            }

            PackageVerifier verifier = new PackageVerifier(logger, name);
            List<Path> bad = verifier.verify(packages, List.of("zstd", "-t"), 60);

            if (bad.isEmpty()) {
                logger.info("[" + name + "] all packages are healthy");
            } else {
                logger.warn("[" + name + "] found " + bad.size() + " corrupt package(s):");
                for (Path p : bad) {
                    logger.warn("  CORRUPT: " + p);
                }
                logger.warn("[" + name + "] run 'lms fix -d arch' to remove and re-download them");
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
                .filter(p -> p.toString().endsWith(".pkg.tar.zst"))
                .collect(Collectors.toList());

            PackageVerifier verifier = new PackageVerifier(logger, name);
            List<Path> bad = verifier.verify(packages, List.of("zstd", "-t"), 60);

            if (bad.isEmpty()) {
                logger.info("[" + name + "] all packages are healthy, nothing to fix");
                return;
            }

            logger.info("[" + name + "] found corrupt packages, removing...");
            for (Path p : bad) {
                logger.info("[" + name + "] REMOVING: " + p);
                Files.deleteIfExists(p);
                Files.deleteIfExists(Path.of(p.toString() + ".sig"));
            }

            logger.info("[" + name + "] re-downloading missing packages...");
            List<String> cmd = new ArrayList<>(List.of(
                "rsync", "--contimeout=60", "--timeout=300",
                "-rtlvHv", "--safe-links", "--delete-after",
                "--delay-updates", "--no-motd", "--partial",
                "--info=progress2"
            ));
            cmd.addAll(buildFilterArgs());
            cmd.add(sourceUrl);
            cmd.add(targetDir.toString());
            ProcessResult result = runProcess(cmd, 120);
            if (result.exitCode() == 0) {
                logger.info("[" + name + "] fix done");
            } else {
                logger.error("[" + name + "] fix re-download failed");
            }
        } catch (IOException | InterruptedException e) {
            logger.error("[" + name + "] Fix error: " + e.getMessage());
        }
    }

    @Override
    protected Path getLastSyncFile() {
        Path f = cacheDir.resolve("lastupdate");
        if (Files.exists(f)) return f;
        return targetDir.resolve("lastupdate");
    }

    @Override
    protected String checkUpToDateStatus() {
        try {
            String remote = fetchLastUpdateQuiet();
            Path lastUpdateFile = getLastSyncFile();
            String local = Files.exists(lastUpdateFile) ? Files.readString(lastUpdateFile).trim() : "";
            if (remote == null || remote.isBlank()) return "unknown";
            return remote.equals(local) ? "yes" : "no";
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    protected String getReposDisplay() {
        return repos.isEmpty() ? "all (no whitelist)" : String.join(", ", repos);
    }

    private String fetchLastUpdateQuiet() throws IOException, InterruptedException {
        Files.createDirectories(cacheDir);
        Path tmp = cacheDir.resolve("lastupdate.tmp");
        ProcessResult r = runProcessQuiet(List.of(
            "rsync", "--contimeout=60", "--timeout=120",
            sourceUrl + "lastupdate", tmp.toString()
        ), 5);
        if (r.exitCode() != 0) {
            Files.deleteIfExists(tmp);
            return null;
        }
        String content = Files.readString(tmp).trim();
        Files.deleteIfExists(tmp);
        return content;
    }

    private String fetchLastUpdate() throws IOException, InterruptedException {
        Files.createDirectories(cacheDir);
        Path tmp = cacheDir.resolve("lastupdate.tmp");
        ProcessResult r = runProcess(List.of(
            "rsync", "--contimeout=60", "--timeout=120",
            sourceUrl + "lastupdate", tmp.toString()
        ), 5);
        if (r.exitCode() != 0) {
            Files.deleteIfExists(tmp);
            return null;
        }
        String content = Files.readString(tmp).trim();
        Files.deleteIfExists(tmp);
        return content;
    }

    private boolean isUpToDate() throws IOException, InterruptedException {
        String remote = fetchLastUpdate();
        if (remote == null || remote.isBlank()) return false;
        Path lastUpdateFile = cacheDir.resolve("lastupdate");
        if (!Files.exists(lastUpdateFile)) {
            lastUpdateFile = targetDir.resolve("lastupdate");
        }
        String local = Files.exists(lastUpdateFile) ? Files.readString(lastUpdateFile).trim() : "";
        return remote.equals(local);
    }

    private int countChangedPackages() throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(
            "rsync", "--contimeout=60", "--timeout=120",
            "-rtlvHv", "--safe-links", "--delete-after",
            "--delay-updates", "--no-motd"
        ));
        cmd.addAll(buildFilterArgs());
        cmd.addAll(List.of(
            "--include=*/", "--include=*.pkg.tar.zst", "--exclude=*",
            "-n", "--stats",
            sourceUrl, targetDir.toString()
        ));
        ProcessResult r = runProcess(cmd, 30);
        for (String line : r.output()) {
            if (line.contains("Number of regular files transferred:")) {
                String[] parts = line.trim().split("\\s+");
                String num = parts[parts.length - 1].replaceAll("[^0-9]", "");
                return num.isEmpty() ? 0 : Integer.parseInt(num);
            }
        }
        return 0;
    }

    private void verifyChanged(Path logFile) throws IOException, InterruptedException {
        if (!Files.exists(logFile)) return;
        List<String> changed = Files.readAllLines(logFile).stream()
            .filter(line -> line.endsWith(".pkg.tar.zst"))
            .toList();
        if (changed.isEmpty()) return;

        logger.info("[" + name + "] verifying changed packages...");
        List<Path> paths = changed.stream()
            .map(f -> targetDir.resolve(f))
            .filter(Files::exists)
            .toList();

        PackageVerifier verifier = new PackageVerifier(logger, name);
        List<Path> bad = verifier.verify(paths, List.of("zstd", "-t"), 60);
        for (Path p : bad) {
            logger.warn("[" + name + "] CORRUPT: " + p);
            Files.deleteIfExists(p);
            Files.deleteIfExists(Path.of(p.toString() + ".sig"));
        }
    }

    private List<String> buildFilterArgs() {
        List<String> args = new ArrayList<>();
        if (!repos.isEmpty()) {
            // whitelist mode: sync only specified repos + pool + metadata
            args.add("--include=/pool/");
            args.add("--include=/pool/**");
            for (String repo : repos) {
                String r = repo.endsWith("/") ? repo : repo + "/";
                args.add("--include=/" + r);
                args.add("--include=/" + r + "**");
            }
            args.add("--include=/lastupdate");
            args.add("--include=/lastsync");
            args.add("--exclude=*");
        } else {
            // blacklist mode (default)
            args.add("--exclude=iso/");
            for (String ex : excludes) {
                String e = ex.endsWith("/") ? ex : ex + "/";
                args.add("--exclude=" + e);
            }
        }
        return args;
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

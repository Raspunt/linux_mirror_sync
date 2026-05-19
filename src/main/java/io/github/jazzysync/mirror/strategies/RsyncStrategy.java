package io.github.jazzysync.mirror.strategies;

import io.github.jazzysync.mirror.api.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RsyncStrategy implements SyncStrategy {
    private final List<String> excludes;
    private final List<String> includes;

    public RsyncStrategy() {
        this(List.of(), List.of());
    }

    public RsyncStrategy(List<String> excludes, List<String> includes) {
        this.excludes = excludes != null ? excludes : List.of();
        this.includes = includes != null ? includes : List.of();
    }

    @Override
    public List<String> buildSyncCommand(String sourceUrl, Path targetDir) {
        List<String> cmd = new ArrayList<>(List.of(
            "rsync", "--contimeout=60", "--timeout=300",
            "-rtlvHv", "--safe-links", "--delete-after",
            "--delay-updates", "--no-motd", "--partial",
            "--info=progress2"
        ));
        addFilters(cmd);
        cmd.add(sourceUrl);
        cmd.add(targetDir.toString());
        return cmd;
    }

    @Override
    public List<String> buildCheckCommand(String sourceUrl, Path targetDir) {
        List<String> cmd = new ArrayList<>(List.of(
            "rsync", "--contimeout=60", "--timeout=120",
            "-rtlvHv", "--safe-links", "--delete-after",
            "--delay-updates", "--no-motd",
            "-n", "--stats"
        ));
        addFilters(cmd);
        cmd.add(sourceUrl);
        cmd.add(targetDir.toString());
        return cmd;
    }

    private void addFilters(List<String> cmd) {
        // Build all intermediate directory includes so rsync can descend into nested paths
        java.util.LinkedHashSet<String> dirIncludes = new java.util.LinkedHashSet<>();
        for (String in : includes) {
            String clean = in.endsWith("/") ? in.substring(0, in.length() - 1) : in;
            if (clean.isEmpty()) continue;
            StringBuilder path = new StringBuilder();
            for (String part : clean.split("/")) {
                if (path.length() > 0) path.append("/");
                path.append(part);
                dirIncludes.add(path.toString() + "/");
            }
        }
        // First: include directories themselves (intermediate + target)
        for (String dir : dirIncludes) {
            cmd.add("--include=/" + dir);
        }
        // Second: apply exclusions before recursive includes
        for (String ex : excludes) {
            String e = ex.endsWith("/") ? ex : ex + "/";
            cmd.add("--exclude=/" + e);
        }
        // Third: include contents recursively
        for (String in : includes) {
            String i = in.endsWith("/") ? in : in + "/";
            cmd.add("--include=/" + i + "**");
        }
        if (!includes.isEmpty()) {
            cmd.add("--exclude=*");
        }
    }
}

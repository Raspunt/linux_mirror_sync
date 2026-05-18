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
        for (String in : includes) {
            String i = in.endsWith("/") ? in : in + "/";
            cmd.add("--include=/" + i);
            cmd.add("--include=/" + i + "**");
        }
        for (String ex : excludes) {
            String e = ex.endsWith("/") ? ex : ex + "/";
            cmd.add("--exclude=/" + e);
        }
        if (!includes.isEmpty()) {
            cmd.add("--exclude=*");
        }
    }
}

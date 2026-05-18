package io.github.jazzysync.mirror.strategies;

import io.github.jazzysync.mirror.api.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DebmirrorStrategy implements SyncStrategy {
    private final String host;
    private final String root;
    private final String dist;
    private final String arch;
    private final String section;

    public DebmirrorStrategy(String host, String root, String dist, String arch, String section) {
        this.host = host;
        this.root = root;
        this.dist = dist;
        this.arch = arch;
        this.section = section;
    }

    @Override
    public List<String> buildSyncCommand(String sourceUrl, Path targetDir) {
        return buildCommand(targetDir, false);
    }

    @Override
    public List<String> buildCheckCommand(String sourceUrl, Path targetDir) {
        return buildCommand(targetDir, true);
    }

    private List<String> buildCommand(Path targetDir, boolean dryRun) {
        List<String> cmd = new ArrayList<>(List.of(
            "debmirror",
            "--host=" + host,
            "--root=" + root,
            "--method=rsync",
            "--dist=" + dist,
            "--arch=" + arch,
            "--section=" + section,
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
}

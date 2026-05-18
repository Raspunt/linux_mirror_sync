package io.github.jazzysync;

import io.github.jazzysync.cli.MirrorSyncCli;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new MirrorSyncCli()).execute(args);
        System.exit(exitCode);
    }
}

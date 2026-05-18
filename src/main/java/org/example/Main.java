package org.example;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new MirrorSyncCli()).execute(args);
        System.exit(exitCode);
    }
}

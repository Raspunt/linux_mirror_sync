package org.example.cli;

import org.example.config.ConfigManager;
import org.example.logging.LogManager;
import org.example.mirror.api.IMirror;
import org.example.mirror.core.MirrorFactory;
import org.example.mirror.api.MirrorStatus;
import picocli.CommandLine;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "lms",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "CLI tool for synchronizing Linux distribution mirrors"
)
public class MirrorSyncCli implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Parameters(
            index = "0",
            paramLabel = "COMMAND",
            description = "Command to execute: sync, verify, check, fix, list, status",
            arity = "0..1"
    )
    private String command;

    @CommandLine.Parameters(
            index = "1",
            paramLabel = "TARGET",
            description = "Distribution to process: arch, debian, or all (default: all)",
            arity = "0..1"
    )
    private String target;

    @CommandLine.Option(
            names = {"-d", "--distro"},
            description = "Distribution to process (deprecated, use positional TARGET)",
            hidden = true
    )
    private String distroLegacy;

    @CommandLine.Option(
            names = {"-t", "--target-dir"},
            description = "Target directory for mirrors (default: ~/mirrors)"
    )
    private String targetDir;

    @Override
    public Integer call() {
        if (command == null || command.isBlank()) {
            spec.commandLine().usage(System.out);
            return 0;
        }

        ConfigManager config;
        if (targetDir != null && !targetDir.isBlank()) {
            config = new ConfigManager(targetDir);
        } else {
            config = new ConfigManager();
        }

        if ("list".equalsIgnoreCase(command)) {
            System.out.println("Available distributions:");
            for (String d : MirrorFactory.availableDistros()) {
                String status = config.isDistroEnabled(d) ? "enabled" : "disabled";
                System.out.println("  - " + d + " (" + status + ")");
            }
            System.out.println("Config file: " + System.getenv("HOME") + "/.config/lms/config.json");
            return 0;
        }

        String effectiveTarget = resolveTarget();

        LogManager logger = new LogManager(config.getLogDir());
        MirrorFactory factory = new MirrorFactory(config, logger);

        List<IMirror> mirrors;
        if ("all".equalsIgnoreCase(effectiveTarget)) {
            mirrors = factory.createAll();
        } else {
            try {
                mirrors = List.of(factory.create(effectiveTarget));
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
                System.err.println("Run 'lms list' to see available distributions.");
                return 1;
            }
        }

        if (mirrors.isEmpty()) {
            System.err.println("No enabled distributions found.");
            return 1;
        }

        if ("status".equalsIgnoreCase(command)) {
            return printStatus(mirrors);
        }

        boolean failed = false;
        int total = mirrors.size();
        int current = 0;

        for (IMirror mirror : mirrors) {
            current++;
            if (total > 1) {
                logger.info("[" + mirror.getName() + "] === [" + current + "/" + total + "] ===");
            }
            try {
                mirror.checkDependencies();
                switch (command.toLowerCase()) {
                    case "sync" -> mirror.syncRepo();
                    case "verify" -> mirror.verifyRepo();
                    case "check" -> mirror.checkRepo();
                    case "fix" -> mirror.fixRepo();
                    default -> {
                        System.err.println("Unknown command: " + command);
                        System.err.println("Available commands: sync, verify, check, fix, list, status");
                        return 1;
                    }
                }
            } catch (Exception e) {
                logger.error("Unexpected error processing " + mirror.getName() + ": " + e.getMessage());
                failed = true;
            }
        }

        if (total > 1) {
            logger.info("=== All done ===");
        }

        return failed ? 1 : 0;
    }

    private int printStatus(List<IMirror> mirrors) {
        List<MirrorStatus> statuses = new ArrayList<>();
        for (IMirror mirror : mirrors) {
            try {
                statuses.add(mirror.getStatus());
            } catch (Exception e) {
                System.err.println("Failed to get status for " + mirror.getName() + ": " + e.getMessage());
            }
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());

        System.out.printf("%-12s %-10s %-18s %-12s %s%n",
                "Mirror", "Size", "Last Sync", "Up-to-date", "Repos");
        System.out.println("-".repeat(90));
        for (MirrorStatus s : statuses) {
            String sizeStr = formatSize(s.sizeBytes());
            String lastSyncStr = s.lastSync() != null ? fmt.format(s.lastSync()) : "never";
            System.out.printf("%-12s %-10s %-18s %-12s %s%n",
                    s.name(), sizeStr, lastSyncStr, s.upToDate(), s.repos());
        }
        return 0;
    }

    private String formatSize(long bytes) {
        if (bytes < 0) return "unknown";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f K", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f M", bytes / (1024.0 * 1024));
        return String.format("%.1f G", bytes / (1024.0 * 1024 * 1024));
    }

    private String resolveTarget() {
        if (target != null && !target.isBlank()) {
            return target;
        }
        if (distroLegacy != null && !distroLegacy.isBlank()) {
            return distroLegacy;
        }
        return "all";
    }
}

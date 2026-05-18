package org.example.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogManager {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path logFile;
    private final Path logDir;

    public LogManager(Path logDir) {
        try {
            Files.createDirectories(logDir);
            this.logDir = logDir;
            this.logFile = logDir.resolve("mirror-sync.log");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize log directory: " + e.getMessage(), e);
        }
    }

    public Path getLogDir() {
        return logDir;
    }

    public void info(String msg) {
        log("INFO", msg);
    }

    public void warn(String msg) {
        log("WARN", msg);
    }

    public void error(String msg) {
        log("ERROR", msg);
    }

    private void log(String level, String msg) {
        String line = String.format("[%s] [%s] %s%n", LocalDateTime.now().format(FMT), level, msg);
        System.out.print(line);
        try {
            Files.writeString(logFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }
}

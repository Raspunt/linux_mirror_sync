package io.github.jazzysync.mirror.strategies;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RsyncStrategyTest {

    @Test
    void buildSyncCommand_hasBaseFlags() {
        RsyncStrategy strategy = new RsyncStrategy();
        List<String> cmd = strategy.buildSyncCommand("rsync://example.com/repo/", Paths.get("/target"));

        assertTrue(cmd.contains("rsync"));
        assertTrue(cmd.contains("-rtlvHv"));
        assertTrue(cmd.contains("--safe-links"));
        assertTrue(cmd.contains("--delete-after"));
        assertEquals("rsync://example.com/repo/", cmd.get(cmd.size() - 2));
        assertEquals("/target", cmd.get(cmd.size() - 1));
    }

    @Test
    void buildCheckCommand_hasDryRunFlag() {
        RsyncStrategy strategy = new RsyncStrategy();
        List<String> cmd = strategy.buildCheckCommand("rsync://example.com/repo/", Paths.get("/target"));

        assertTrue(cmd.contains("-n"));
        assertTrue(cmd.contains("--stats"));
    }

    @Test
    void addFilters_includesOnly() {
        RsyncStrategy strategy = new RsyncStrategy(List.of(), List.of("core", "extra"));
        List<String> cmd = strategy.buildSyncCommand("rsync://example.com/", Paths.get("/target"));

        int includeDirCore = cmd.indexOf("--include=/core/");
        int includeRecursiveCore = cmd.indexOf("--include=/core/**");
        int includeDirExtra = cmd.indexOf("--include=/extra/");
        int excludeAll = cmd.indexOf("--exclude=*");

        assertTrue(includeDirCore > 0, "should include /core/ directory");
        assertTrue(includeRecursiveCore > includeDirCore, "recursive include should come after dir include");
        assertTrue(includeDirExtra > 0, "should include /extra/ directory");
        assertTrue(excludeAll > includeRecursiveCore, "final exclude should come after recursive includes");
    }

    @Test
    void addFilters_excludesBetweenDirAndRecursive() {
        RsyncStrategy strategy = new RsyncStrategy(
            List.of("Everything/aarch64", "Everything/x86_64/iso"),
            List.of("Everything")
        );
        List<String> cmd = strategy.buildSyncCommand("rsync://example.com/", Paths.get("/target"));

        int includeDir = cmd.indexOf("--include=/Everything/");
        int excludeAarch64 = cmd.indexOf("--exclude=/Everything/aarch64/");
        int excludeIso = cmd.indexOf("--exclude=/Everything/x86_64/iso/");
        int includeRecursive = cmd.indexOf("--include=/Everything/**");
        int excludeAll = cmd.indexOf("--exclude=*");

        assertTrue(includeDir > 0);
        assertTrue(excludeAarch64 > includeDir, "exclude should come after dir include");
        assertTrue(excludeIso > excludeAarch64, "excludes should be ordered");
        assertTrue(includeRecursive > excludeIso, "recursive include should come after excludes");
        assertTrue(excludeAll > includeRecursive, "final exclude should come last");
    }

    @Test
    void addFilters_noFiltersWhenEmpty() {
        RsyncStrategy strategy = new RsyncStrategy();
        List<String> cmd = strategy.buildSyncCommand("rsync://example.com/", Paths.get("/target"));

        assertFalse(cmd.contains("--exclude=*"));
        for (String arg : cmd) {
            assertFalse(arg.startsWith("--include="));
            assertFalse(arg.startsWith("--exclude=/"));
        }
    }
}

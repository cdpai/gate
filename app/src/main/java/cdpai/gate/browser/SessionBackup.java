package cdpai.gate.browser;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/// Copies each profile's `Sessions` folder aside before cdpgate closes a running browser, so a
/// restart that does not restore everything is recoverable by hand. Small files; the last ten
/// backups are kept under `~/cdpai/gate/session-backups/`.
public final class SessionBackup {

    static final int KEEP = 10;

    public static Path root() { return Path.of(System.getProperty("user.home"), "cdpai", "gate", "session-backups"); }

    public static Path backup(Path userDataDir, List<String> profileDirs) {
        var dest = root().resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        for (var dir : profileDirs) {
            var src = userDataDir.resolve(dir).resolve("Sessions");
            if (!Files.isDirectory(src)) continue;
            try (var files = Files.list(src)) {
                var target = Files.createDirectories(dest.resolve(dir));
                for (var f : files.toList()) if (Files.isRegularFile(f)) Files.copy(f, target.resolve(f.getFileName()));
            } catch (Exception e) { System.err.println("session backup of " + dir + " incomplete: " + e.getMessage()); }
        }
        prune();
        return dest;
    }

    static void prune() {
        try (var all = Files.list(root())) {
            var old = all.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).skip(KEEP).toList();
            for (var d : old) try (var walk = Files.walk(d)) { walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
        } catch (Exception ignored) {}
    }

    private SessionBackup() {}
}

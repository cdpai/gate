package cdpai.gate.access;

import java.nio.file.*;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/// Bridges the gaps MSYS and Cygwin leave in the Windows process tree. They emulate fork and exec
/// with fresh Windows processes and let the old ones exit, so a program started under Git Bash --
/// `timeout`, `sh -c`, any exec'd last command -- often has a Windows parent that no longer exists,
/// and the chain stops short of the shell and the agent session above it. The installation's own
/// process table still records the Unix parent of every live MSYS process, with its Windows pid;
/// this reads it with that installation's `ps.exe` (beside the broken program) and returns the
/// Windows pid of the nearest live MSYS parent. Measured on Git Bash, 2026-09-23: without it the
/// chain ended at `timeout.exe`; with it, it continues to the bash under claude.exe.
public final class MsysProcessTable {

    record Row(long pid, long ppid, long winpid) {}

    /// The Windows pid of the MSYS parent of `winpid`, if `image` belongs to an MSYS installation.
    /// When an MSYS shell execs a native program, Cygwin moves the shell's row to the new program's
    /// Windows pid and leaves the shell's own Windows process as a stub with no row -- measured -- so
    /// the row of `childWinpid`, the process just below the break, is tried as well.
    public static Optional<Long> parentOf(long winpid, String image, long childWinpid) {
        try {
            var ps = Path.of(image).getParent().resolve("ps.exe");
            if (!Files.isRegularFile(ps)) return Optional.empty();
            var rows = read(ps);
            var me = rows.values().stream().filter(r -> r.winpid() == winpid).findFirst()
                .or(() -> rows.values().stream().filter(r -> r.winpid() == childWinpid).findFirst());
            return me.map(r -> rows.get(r.ppid())).map(Row::winpid).filter(w -> w != winpid);
        } catch (Exception e) { return Optional.empty(); }
    }

    static HashMap<Long, Row> read(Path ps) throws Exception {
        var p = new ProcessBuilder(ps.toString(), "-l").redirectErrorStream(true).start();
        var out = new String(p.getInputStream().readAllBytes());
        p.waitFor(3, TimeUnit.SECONDS);
        var rows = new HashMap<Long, Row>();
        for (var line : out.split("\\R")) {
            var f = line.trim().replaceFirst("^[SIO]\\s+", "").split("\\s+");
            if (f.length < 4 || !f[0].matches("\\d+")) continue;
            rows.put(Long.parseLong(f[0]), new Row(Long.parseLong(f[0]), Long.parseLong(f[1]), Long.parseLong(f[3])));
        }
        return rows;
    }

    private MsysProcessTable() {}
}

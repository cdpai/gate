package cdpai.gate.posix;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cdpai.gate.browser.BrowserLauncher;

import static cdpai.gate.posix.Libc.*;

/// Starts the browser with --remote-debugging-pipe on macOS: two pipes, dup2'd onto the child's
/// fd 3 (it reads commands) and fd 4 (it writes replies) -- exactly what Puppeteer's `pipe: true`
/// does on Mac. posix_spawn rather than ProcessBuilder, because a Java ProcessBuilder child gets
/// fds 0-2 and nothing else. POSIX_SPAWN_CLOEXEC_DEFAULT (an Apple extension) closes every other
/// descriptor in the child, so not one of this JVM's sockets or files leaks into the browser;
/// fds 0-2 go to /dev/null. Signal mask and dispositions are reset, because the child would
/// otherwise inherit the JVM thread's blocked signals. The architecture is pinned too (see below).
public final class PosixLauncher {

    static final String ARCH = "/usr/bin/arch";

    public static PosixBrowserProcess launch(String exePath, String userDataDir, List<String> extraArgs) {
        var argv = new ArrayList<String>();
        // On Apple Silicon the browser goes through `/usr/bin/arch -arm64`, which execs it in place
        // (same pid, fds 3/4 still open). The binpref pin below covers the browser process only;
        // measured 2026-10-03, its Helper processes (GPU, renderers) still came up under Rosetta
        // when an ancestor was translated, and the GPU process crash-looped so no tab ever
        // answered. arch's preference is inherited by the whole subtree (checked to grandchildren).
        var viaArch = "aarch64".equals(System.getProperty("os.arch")) && java.nio.file.Files.isExecutable(java.nio.file.Path.of(ARCH));
        if (viaArch) { argv.add(ARCH); argv.add("-arm64"); }
        argv.add(exePath);
        var program = viaArch ? ARCH : exePath;
        argv.addAll(BrowserLauncher.pipeFlags(userDataDir, extraArgs));
        try (var a = Arena.ofConfined()) {
            var cap = newCapture(a);
            var toChild = a.allocate(ValueLayout.JAVA_INT, 2);     // [0] child reads as fd3, [1] we write
            var fromChild = a.allocate(ValueLayout.JAVA_INT, 2);   // [0] we read, [1] child writes as fd4
            if ((int) pipe.invoke(cap, toChild) != 0) throw fail("pipe", cap);
            if ((int) pipe.invoke(cap, fromChild) != 0) throw fail("pipe", cap);
            int childIn = toChild.getAtIndex(ValueLayout.JAVA_INT, 0), ourWrite = toChild.getAtIndex(ValueLayout.JAVA_INT, 1);
            int ourRead = fromChild.getAtIndex(ValueLayout.JAVA_INT, 0), childOut = fromChild.getAtIndex(ValueLayout.JAVA_INT, 1);

            var fa = a.allocate(SPAWN_OPAQUE_SIZE);
            var attr = a.allocate(SPAWN_OPAQUE_SIZE);
            check("posix_spawn_file_actions_init", (int) fa_init.invoke(fa));
            check("posix_spawnattr_init", (int) attr_init.invoke(attr));
            try {
                var devnull = a.allocateFrom("/dev/null");
                check("addopen 0", (int) fa_addopen.invoke(fa, 0, devnull, O_RDONLY, (short) 0));
                check("addopen 1", (int) fa_addopen.invoke(fa, 1, devnull, O_WRONLY, (short) 0));
                check("addopen 2", (int) fa_addopen.invoke(fa, 2, devnull, O_WRONLY, (short) 0));
                check("adddup2 3", (int) fa_adddup2.invoke(fa, childIn, 3));
                check("adddup2 4", (int) fa_adddup2.invoke(fa, childOut, 4));
                var empty = a.allocate(8);
                var all = a.allocate(8);
                all.set(ValueLayout.JAVA_INT, 0, -1);
                check("setsigmask", (int) attr_setsigmask.invoke(attr, empty));
                check("setsigdefault", (int) attr_setsigdefault.invoke(attr, all));
                // Pin the browser to this JVM's own architecture. Without it a universal binary
                // follows an INHERITED preference: measured 2026-10-03 on an M3, cdpgate started
                // from a shell running under Rosetta launched Vivaldi as x86_64 even though the
                // JVM itself was native arm64, and it sat in dyld translating its 500 MB framework.
                var cpu = a.allocate(ValueLayout.JAVA_INT);
                cpu.set(ValueLayout.JAVA_INT, 0, "aarch64".equals(System.getProperty("os.arch")) ? CPU_TYPE_ARM64 : CPU_TYPE_X86_64);
                check("setbinpref_np", (int) attr_setbinpref.invoke(attr, 1L, cpu, a.allocate(ValueLayout.JAVA_LONG)));
                check("setflags", (int) attr_setflags.invoke(attr,
                    (short) (POSIX_SPAWN_CLOEXEC_DEFAULT | POSIX_SPAWN_SETSIGMASK | POSIX_SPAWN_SETSIGDEF)));

                var pidBox = a.allocate(ValueLayout.JAVA_INT);
                var rc = (int) posix_spawn.invoke(pidBox, a.allocateFrom(program), fa, attr, strings(a, argv), strings(a, env()));
                // Our copies of the child's ends: the child holds its own now. Closing them is what
                // lets a read on ourRead see end-of-stream when the browser exits.
                close.invoke(cap, childIn);
                close.invoke(cap, childOut);
                if (rc != 0) {
                    close.invoke(cap, ourRead);
                    close.invoke(cap, ourWrite);
                    throw failCode("posix_spawn(" + exePath + ")", rc);
                }
                return new PosixBrowserProcess(pidBox.get(ValueLayout.JAVA_INT, 0), FdIo.pipes(ourRead, ourWrite));
            } finally {
                fa_destroy.invoke(fa);
                attr_destroy.invoke(attr);
            }
        } catch (RuntimeException e) { throw e; }
        catch (Throwable t) { throw unchecked(t); }
    }

    static void check(String what, int rc) { if (rc != 0) throw failCode(what, rc); }

    static List<String> env() {
        var out = new ArrayList<String>();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) out.add(e.getKey() + "=" + e.getValue());
        return out;
    }

    /// A NULL-terminated char*[] whose strings live in the same arena.
    static MemorySegment strings(Arena a, List<String> values) {
        var arr = a.allocate(ValueLayout.ADDRESS, values.size() + 1);
        for (var i = 0; i < values.size(); i++) arr.setAtIndex(ValueLayout.ADDRESS, i, a.allocateFrom(values.get(i)));
        arr.setAtIndex(ValueLayout.ADDRESS, values.size(), MemorySegment.NULL);
        return arr;
    }

    private PosixLauncher() {}
}

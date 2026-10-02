package cdpai.gate.posix;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;

import cdpai.gate.browser.LaunchedBrowser;
import cdpai.gate.client.FrameIo;

import static cdpai.gate.posix.Libc.*;

/// A browser posix_spawn'd by this JVM. The JVM does not reap children it did not start through
/// ProcessBuilder, so a thread here waits on it: without that the exited browser would stay a
/// zombie, and ProcessHandle would go on reporting it alive.
public final class PosixBrowserProcess implements LaunchedBrowser {

    final long pid;
    final FdIo cdp;
    volatile boolean exited;

    PosixBrowserProcess(long pid, FdIo cdp) {
        this.pid = pid;
        this.cdp = cdp;
        Thread.ofPlatform().name("cdpgate-reaper-" + pid).daemon().start(this::reap);
    }

    void reap() {
        try (var a = Arena.ofConfined()) {
            var cap = newCapture(a);
            var status = a.allocate(ValueLayout.JAVA_INT);
            while (true) {
                var r = (int) waitpid.invoke(cap, (int) pid, status, 0);
                if (r == (int) pid) break;
                if (r < 0 && errno(cap) == EINTR) continue;
                break;
            }
        } catch (Throwable ignored) {}
        exited = true;
    }

    @Override public long pid() { return pid; }

    @Override public FrameIo cdp() { return cdp; }

    @Override public boolean isAlive() { return !exited && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); }

    @Override public void kill() {
        try (var a = Arena.ofConfined()) { Libc.kill.invoke(newCapture(a), (int) pid, SIGKILL); } catch (Throwable ignored) {}
    }

    @Override public void close() { cdp.close(); }
}

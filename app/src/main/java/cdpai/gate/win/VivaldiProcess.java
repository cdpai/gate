package cdpai.gate.win;

import java.lang.foreign.MemorySegment;

import cdpai.gate.client.win.PipeIo;

import static cdpai.gate.win.ProcessLaunchApi.*;

/// A launched Vivaldi holding its own CDP pipe link and native process handle. This is the sole
/// link to the browser -- cdpgate owns it, nothing else can open another.
public final class VivaldiProcess implements AutoCloseable {

    public final long pid;
    public final PipeIo cdp;
    final MemorySegment processHandle;

    VivaldiProcess(long pid, MemorySegment processHandle, PipeIo cdp) {
        this.pid = pid;
        this.processHandle = processHandle;
        this.cdp = cdp;
    }

    public boolean isAlive() { return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); }

    /// Graceful shutdown via the CDP method itself, falling back to a hard kill if it doesn't
    /// exit in time. Prefer this over close() when the browser should quit cleanly.
    public void shutdownGracefully(long timeoutMs) {
        try { cdp.writeFrame("{\"id\":999999,\"method\":\"Browser.close\"}"); } catch (Exception ignored) {}
        try {
            var waited = (int) WaitForSingleObject.invoke(processHandle, (int) timeoutMs);
            if (waited != 0) kill();
        } catch (Throwable t) { kill(); }
    }

    public void kill() {
        try { TerminateProcess.invoke(processHandle, 1); } catch (Throwable ignored) {}
    }

    @Override public void close() {
        cdp.close();
    }
}

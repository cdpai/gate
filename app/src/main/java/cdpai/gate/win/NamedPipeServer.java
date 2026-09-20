package cdpai.gate.win;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

import cdpai.gate.client.win.PipeIo;

import static cdpai.gate.client.win.Kernel32.*;
import static cdpai.gate.client.win.Win32.lastErrorFrom;
import static cdpai.gate.client.win.Win32.newCaptureSegment;
import static cdpai.gate.win.NamedPipeApi.*;

/// The untested piece from the spike findings, now real: a named pipe whose DACL admits only this
/// Windows user, and whose accepted connections are identified by the kernel rather than trusted
/// on their word. One instance is created per accepted client (the standard Win32 multi-instance
/// pattern) so a slow consumer never blocks a new one from connecting.
public final class NamedPipeServer implements AutoCloseable {

    final String pipePath;

    public NamedPipeServer(String pipeName) {
        this.pipePath = "\\\\.\\pipe\\" + pipeName;
    }

    /// Blocks until a client connects, then returns its identity and a live channel to it.
    /// Call this in a loop from a dedicated accept thread -- each call creates a fresh pipe
    /// instance, so the previous connection's handling never delays the next accept. All
    /// allocations here are scratch, freed the moment the connection is handed off.
    public Accepted accept() {
        MemorySegment handle;
        try (var scratch = Arena.ofConfined()) {
            var sa = UserOnlySecurity.buildSecurityAttributes(scratch);
            var name = scratch.allocateFrom(pipePath, StandardCharsets.UTF_16LE);
            var capture = newCaptureSegment(scratch);
            try {
                handle = (MemorySegment) CreateNamedPipeW.invoke(capture, name,
                    PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED,
                    PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT | PIPE_REJECT_REMOTE_CLIENTS,
                    PIPE_UNLIMITED_INSTANCES, DEFAULT_BUF_SIZE, DEFAULT_BUF_SIZE, DEFAULT_TIMEOUT_MS, sa);
            } catch (Throwable t) { throw new RuntimeException(t); }
            UserOnlySecurity.freeSecurityDescriptor(sa);
            if (isInvalid(handle)) throw lastError("CreateNamedPipeW(" + pipePath + ")", capture);

            awaitConnect(scratch, handle);

            var pidBox = scratch.allocate(ValueLayout.JAVA_INT);
            try {
                if ((int) GetNamedPipeClientProcessId.invoke(capture, handle, pidBox) == 0)
                    throw lastError("GetNamedPipeClientProcessId", capture);
            } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }

            var pid = Integer.toUnsignedLong(pidBox.get(ValueLayout.JAVA_INT, 0));
            return new Accepted(PeerIdentity.resolve(pid), new PipeIo(handle));
        }
    }

    /// A pipe opened with FILE_FLAG_OVERLAPPED must be connected with a real OVERLAPPED
    /// structure too -- passing NULL here is documented as unreliable for such a handle. Two
    /// outcomes are normal, not errors: ERROR_PIPE_CONNECTED (a client raced in before this call)
    /// and ERROR_IO_PENDING (the ordinary case; GetOverlappedResult blocks for the real connect).
    static void awaitConnect(Arena scratch, MemorySegment handle) {
        var ov = scratch.allocate(32);
        var event = createEvent(scratch);
        ov.set(ValueLayout.ADDRESS, 24, event);
        var capture = newCaptureSegment(scratch);
        try {
            var ok = (int) ConnectNamedPipe.invoke(capture, handle, ov);
            if (ok != 0) return;
            var err = lastErrorFrom(capture);
            if (err == ERROR_PIPE_CONNECTED) return;
            if (err != ERROR_IO_PENDING) throw lastError("ConnectNamedPipe", capture);
            var count = scratch.allocate(ValueLayout.JAVA_INT);
            if ((int) GetOverlappedResult.invoke(capture, handle, ov, count, 1) == 0)
                throw lastError("ConnectNamedPipe/GetOverlappedResult", capture);
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }
        finally { try { CloseHandle.invoke(event); } catch (Throwable ignored) {} }
    }

    @Override public void close() {}

    public record Accepted(PeerIdentity peer, PipeIo io) {}
}

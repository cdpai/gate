package cdpai.gate.client.win;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

import cdpai.gate.client.GateDeniedException;

import static cdpai.gate.client.win.Kernel32.*;
import static cdpai.gate.client.win.Win32.*;

/// Opens the consumer end of cdpgate's named pipe. Kept apart from GateClient so that nothing on
/// another platform ever initialises Kernel32 (whose static initialiser looks up kernel32.dll).
public final class PipeClient {

    /// cdpgate creates the next pipe instance only after handing the last one to a connection, so a
    /// client arriving in between sees "not found" (2) or "busy" (231) for a moment; both are retried
    /// briefly before concluding that cdpgate is not running.
    public static PipeIo open(String pipeName) {
        var path = "\\\\.\\pipe\\" + pipeName;
        var deadline = System.currentTimeMillis() + 3000;
        try (var scratch = Arena.ofConfined()) {
            var capture = newCaptureSegment(scratch);
            var name = scratch.allocateFrom(path, StandardCharsets.UTF_16LE);
            MemorySegment handle;
            while (true) {
                try {
                    handle = (MemorySegment) CreateFileW.invoke(capture, name, GENERIC_READ | GENERIC_WRITE, 0,
                        MemorySegment.NULL, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, MemorySegment.NULL);
                } catch (Throwable t) { throw new RuntimeException(t); }
                var code = lastErrorFrom(capture);
                if (!isInvalid(handle) || (code != 2 && code != 231) || System.currentTimeMillis() > deadline) break;
                try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            if (isInvalid(handle)) {
                var err = lastError("CreateFileW(" + path + ")", capture);
                throw new GateDeniedException("cdpgate is not running (no pipe " + path + ")",
                    "start cdpgate.exe; it lives in the tray and owns the browser. " + err.getMessage());
            }
            return new PipeIo(handle);
        }
    }

    private PipeClient() {}
}

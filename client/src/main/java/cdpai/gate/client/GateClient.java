package cdpai.gate.client;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

import cdpai.gate.client.win.PipeIo;

import static cdpai.gate.client.win.Kernel32.*;
import static cdpai.gate.client.win.Win32.*;

/// Connects to cdpgate's named pipe as a consumer. This is the whole client-side handshake:
/// open the pipe by its well-known path, then speak NUL-delimited CDP JSON frames over it.
/// No token, no secret -- identity is attested by the kernel on cdpgate's side of the connection.
public final class GateClient implements AutoCloseable {

    public static final String PIPE_NAME = "cdpai-gate";

    final PipeIo io;

    public GateClient() { this(PIPE_NAME); }

    public GateClient(String pipeName) {
        var path = "\\\\.\\pipe\\" + pipeName;
        try (var scratch = Arena.ofConfined()) {
            var capture = newCaptureSegment(scratch);
            var name = scratch.allocateFrom(path, StandardCharsets.UTF_16LE);
            MemorySegment handle;
            try {
                handle = (MemorySegment) CreateFileW.invoke(capture, name, GENERIC_READ | GENERIC_WRITE, 0,
                    MemorySegment.NULL, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, MemorySegment.NULL);
            } catch (Throwable t) { throw new RuntimeException(t); }
            if (isInvalid(handle)) throw lastError("CreateFileW(" + path + ")", capture);
            io = new PipeIo(handle);
        }
    }

    public void send(String json) { io.writeFrame(json); }

    public String receive() { return io.readFrame(); }

    @Override public void close() {
        io.close();
    }
}

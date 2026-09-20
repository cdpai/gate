package cdpai.gate.client.win;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

import static cdpai.gate.client.win.Kernel32.*;

/// NUL-delimited UTF-8 frame read/write over a raw HANDLE (CDP's own wire framing).
/// Blocking, synchronous -- no OVERLAPPED -- same shape PipeSpike.java proved against Vivaldi.
/// Owns a small private arena and reuses its scratch buffers across calls: a long-running server
/// handling many frames must not allocate fresh off-heap memory per frame and never free it.
public final class PipeIo implements AutoCloseable {

    static final int INITIAL_BUF = 65536;

    final Arena arena = Arena.ofShared();
    final MemorySegment handle;
    final MemorySegment readBuf = arena.allocate(INITIAL_BUF);
    final MemorySegment countBox = arena.allocate(ValueLayout.JAVA_INT);
    MemorySegment writeBuf = arena.allocate(INITIAL_BUF);

    public PipeIo(MemorySegment handle) {
        this.handle = handle;
    }

    public void writeFrame(String json) {
        var bytes = (json + "\0").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > writeBuf.byteSize()) writeBuf = arena.allocate(bytes.length);
        MemorySegment.copy(bytes, 0, writeBuf, ValueLayout.JAVA_BYTE, 0, bytes.length);
        try {
            if ((int) WriteFile.invoke(handle, writeBuf, bytes.length, countBox, MemorySegment.NULL) == 0)
                throw lastError("WriteFile");
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }
    }

    public String readFrame() {
        var acc = new ByteArrayOutputStream();
        try {
            while (true) {
                if ((int) ReadFile.invoke(handle, readBuf, INITIAL_BUF, countBox, MemorySegment.NULL) == 0)
                    throw lastError("ReadFile");
                var got = countBox.get(ValueLayout.JAVA_INT, 0);
                if (got <= 0) return null;
                for (var i = 0; i < got; i++) {
                    var b = readBuf.get(ValueLayout.JAVA_BYTE, i);
                    if (b == 0) return acc.toString(StandardCharsets.UTF_8);
                    acc.write(b);
                }
            }
        } catch (Throwable t) { throw t instanceof RuntimeException r ? r : new RuntimeException(t); }
    }

    @Override public void close() {
        try { CloseHandle.invoke(handle); } catch (Throwable ignored) {}
        arena.close();
    }
}

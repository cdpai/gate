package cdpai.gate.posix;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import cdpai.gate.client.FrameIo;

import static cdpai.gate.posix.Libc.*;

/// NUL-delimited CDP frames over raw file descriptors: one duplex socket (a consumer) or a
/// read/write pair of pipes (the browser's fd4/fd3). The POSIX counterpart of PipeIo, and simpler:
/// a read blocked on one thread never holds up a write on another, so plain blocking read/write is
/// enough. Writes loop over short writes; both directions retry on EINTR.
public final class FdIo implements FrameIo {

    static final int BUF = 65536;

    // GC-managed: a thread blocked in read() holds readBuf, which keeps the memory alive until that
    // call returns, so close() can never free it out from under native code.
    final Arena arena = Arena.ofAuto();
    final int readFd, writeFd;
    final boolean socket;
    final MemorySegment readBuf = arena.allocate(BUF);
    final MemorySegment readCapture = newCapture(arena);
    final MemorySegment writeCapture = newCapture(arena);
    MemorySegment writeBuf = arena.allocate(BUF);
    byte[] carry = new byte[0];
    int carryPos;
    final AtomicBoolean closed = new AtomicBoolean();

    /// A connected socket: one fd, both directions.
    public static FdIo socket(int fd) { return new FdIo(fd, fd, true); }

    /// The browser's pipe pair: read what it writes on its fd4, write what it reads on its fd3.
    public static FdIo pipes(int readFd, int writeFd) { return new FdIo(readFd, writeFd, false); }

    FdIo(int readFd, int writeFd, boolean socket) {
        this.readFd = readFd;
        this.writeFd = writeFd;
        this.socket = socket;
    }

    @Override public synchronized void writeFrame(String json) {
        var bytes = (json + "\0").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > writeBuf.byteSize()) writeBuf = arena.allocate(bytes.length);
        MemorySegment.copy(bytes, 0, writeBuf, ValueLayout.JAVA_BYTE, 0, bytes.length);
        long off = 0;
        try {
            while (off < bytes.length) {
                var n = (long) Libc.write.invoke(writeCapture, writeFd, writeBuf.asSlice(off), (long) bytes.length - off);
                if (n < 0) {
                    if (errno(writeCapture) == EINTR) continue;
                    throw fail("write(fd " + writeFd + ")", writeCapture);
                }
                off += n;
            }
        } catch (Throwable t) { throw unchecked(t); }
    }

    /// One read can return several frames, or part of one; whatever follows a frame's NUL is kept
    /// for the next call. Null at end of stream -- which is also what a cancelled read returns.
    @Override public String readFrame() {
        var acc = new ByteArrayOutputStream();
        while (carryPos < carry.length) {
            var b = carry[carryPos++];
            if (b == 0) return acc.toString(StandardCharsets.UTF_8);
            acc.write(b);
        }
        try {
            while (true) {
                if (closed.get()) return null;
                var got = (long) Libc.read.invoke(readCapture, readFd, readBuf, (long) BUF);
                if (got < 0) {
                    if (errno(readCapture) == EINTR) continue;
                    if (closed.get()) return null;
                    throw fail("read(fd " + readFd + ")", readCapture);
                }
                if (got == 0) return null;
                var chunk = readBuf.asSlice(0, got).toArray(ValueLayout.JAVA_BYTE);
                for (var i = 0; i < got; i++) {
                    if (chunk[i] == 0) { carry = chunk; carryPos = i + 1; return acc.toString(StandardCharsets.UTF_8); }
                    acc.write(chunk[i]);
                }
            }
        } catch (Throwable t) { throw unchecked(t); }
    }

    /// shutdown() wakes a read blocked on this socket in another thread (it returns end of stream)
    /// without closing the descriptor, so that thread's own cleanup does the close -- the same
    /// contract as CancelIoEx on Windows. A pipe has no such call; nothing cancels the browser link.
    @Override public void cancelPendingIo() {
        if (closed.get() || !socket) return;
        try { Libc.shutdown.invoke(newCapture(arena), readFd, SHUT_RDWR); } catch (Throwable ignored) {}
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try (var a = Arena.ofConfined()) {
            var cap = newCapture(a);
            if (socket) Libc.shutdown.invoke(cap, readFd, SHUT_RDWR);
            Libc.close.invoke(cap, readFd);
            if (writeFd != readFd) Libc.close.invoke(cap, writeFd);
        } catch (Throwable ignored) {}    }
}

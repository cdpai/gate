package cdpai.gate.client.posix;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import cdpai.gate.client.FrameIo;

/// The consumer's end of cdpgate's Unix domain socket, on plain java.nio -- a client needs no
/// native code at all; only the server side, which must ask the kernel who connected, does.
/// A socket is full duplex, so a read pending on one thread never holds up a write on another
/// (the problem the Windows side needs overlapped I/O for does not exist here).
public final class SocketChannelIo implements FrameIo {

    final SocketChannel ch;
    final ByteBuffer in = ByteBuffer.allocate(65536).flip();
    final AtomicBoolean closed = new AtomicBoolean();

    public SocketChannelIo(SocketChannel ch) { this.ch = ch; }

    @Override public void writeFrame(String json) {
        var buf = ByteBuffer.wrap((json + "\0").getBytes(StandardCharsets.UTF_8));
        synchronized (this) {
            try { while (buf.hasRemaining()) ch.write(buf); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }

    @Override public String readFrame() {
        var acc = new ByteArrayOutputStream();
        try {
            while (true) {
                while (in.hasRemaining()) {
                    var b = in.get();
                    if (b == 0) return acc.toString(StandardCharsets.UTF_8);
                    acc.write(b);
                }
                in.clear();
                var n = ch.read(in);
                in.flip();
                if (n < 0) return null;
            }
        } catch (IOException e) {
            if (closed.get()) return null;
            throw new UncheckedIOException(e);
        }
    }

    /// shutdownInput makes a blocked read return end-of-stream without closing the channel.
    @Override public void cancelPendingIo() {
        if (closed.get()) return;
        try { ch.shutdownInput(); } catch (IOException ignored) {}
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { ch.close(); } catch (IOException ignored) {}
    }
}

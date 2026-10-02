package cdpai.gate.client;

/// One CDP-framed link: NUL-delimited UTF-8 JSON in both directions. The Windows named pipe and
/// anonymous pipes (`win.PipeIo`) and the POSIX socket and fd pair (`posix.*`) all speak it, so
/// everything above the transport -- the hub, the handshake, the consumers -- is the same code on
/// every platform.
public interface FrameIo extends AutoCloseable {

    void writeFrame(String json);

    /// The next frame, or null at end of stream.
    String readFrame();

    /// Wakes a readFrame() blocked on another thread, without closing: that thread unwinds through
    /// its own cleanup, which does the close.
    void cancelPendingIo();

    @Override void close();
}

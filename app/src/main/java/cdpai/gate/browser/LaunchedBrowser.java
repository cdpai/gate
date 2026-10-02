package cdpai.gate.browser;

import cdpai.gate.client.FrameIo;

/// A browser cdpgate started with --remote-debugging-pipe, and the pipe that is its only CDP link.
/// `win.VivaldiProcess` on Windows, `posix.PosixBrowserProcess` elsewhere.
public interface LaunchedBrowser extends AutoCloseable {

    long pid();

    FrameIo cdp();

    boolean isAlive();

    void kill();

    @Override void close();
}

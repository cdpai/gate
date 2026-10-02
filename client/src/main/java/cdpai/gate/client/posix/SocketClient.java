package cdpai.gate.client.posix;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;

import cdpai.gate.client.GateDeniedException;
import cdpai.gate.client.GatePlatform;

/// Opens the consumer end of cdpgate's Unix domain socket. cdpgate accepts on one thread and
/// hands each connection off at once, so unlike the Windows pipe there is no gap between
/// instances to retry across: a refused connect means nobody is listening.
public final class SocketClient {

    public static SocketChannelIo open(String name) {
        var path = GatePlatform.socketPath(name);
        if (!Files.exists(path))
            throw new GateDeniedException("cdpgate is not running (no socket " + path + ")",
                "start cdpgate; it lives in the menu bar and owns the browser");
        try {
            return new SocketChannelIo(SocketChannel.open(UnixDomainSocketAddress.of(path)));
        } catch (IOException e) {
            throw new GateDeniedException("cdpgate is not running (socket " + path + " refused: " + e.getMessage() + ")",
                "start cdpgate; it lives in the menu bar and owns the browser. A socket file left by a crash is replaced when it starts");
        }
    }

    private SocketClient() {}
}

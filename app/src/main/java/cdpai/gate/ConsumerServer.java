package cdpai.gate;

import cdpai.gate.client.FrameIo;
import cdpai.gate.win.PeerIdentity;

/// Where consumers connect: the named pipe on Windows (`win.NamedPipeServer`), a Unix domain
/// socket elsewhere (`posix.UnixSocketServer`). Either way the peer is named by the kernel, never
/// by the client's own word -- that is the property the rest of the gate is built on.
public interface ConsumerServer {

    /// Blocks until a client connects. Called in a loop from one accept thread.
    Accepted accept();

    record Accepted(PeerIdentity peer, FrameIo io) {}
}

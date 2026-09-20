package cdpai.gate.hub;

import cdpai.gate.client.win.PipeIo;
import cdpai.gate.win.PeerIdentity;

/// One approved consumer's live connection: who the kernel says they are, and the pipe to talk
/// to them over. Reference identity is what matters for the hub's session/consumer bookkeeping,
/// so this stays a plain carrier rather than a value type.
public final class ConsumerLink {

    public final PeerIdentity peer;
    public final PipeIo io;

    public ConsumerLink(PeerIdentity peer, PipeIo io) {
        this.peer = peer;
        this.io = io;
    }
}

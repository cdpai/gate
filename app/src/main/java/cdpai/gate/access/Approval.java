package cdpai.gate.access;

import java.util.List;
import java.util.Optional;

import cdpai.gate.win.PeerIdentity;

/// The human-in-the-loop decision the design doc insists on: "the anchor is a deliberate choice
/// made by a human, per approval, from a displayed tree. It is never assumed and never
/// auto-applied." This interface is the seam -- ConsoleApproval is today's stand-in so the rest
/// of the server can be built and tested now; the JavaFX approval window replaces it later
/// without anything else in the server changing.
public interface Approval {

    record Decision(AncestryNode anchor, int durationMinutes) {}

    Optional<Decision> decide(PeerIdentity client, List<AncestryNode> chain, ProcessTree tree);
}

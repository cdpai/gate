package cdpai.gate.access;

import java.util.List;
import java.util.Optional;

import cdpai.gate.browser.ProfileMap;
import cdpai.gate.win.PeerIdentity;

/// The human decision. One window serves both kinds of request: an attested one, where the human
/// picks how far up the process tree the approval reaches, and a keyed one, where the approval is
/// held by the app's key and the tree is shown for information only. `requested.profiles` are
/// already resolved to profile folders. The decision's scope is exactly what the human saw.
public interface Approval {

    enum Kind { ATTESTED, KEYED }

    record Request(Kind kind, PeerIdentity client, List<AncestryNode> chain, ProcessTree tree, Scope requested,
                   String app, String keyFingerprint, List<ProfileMap.Entry> profiles, String reason) {}

    /// `anchor` is null for a keyed decision.
    record Decision(AncestryNode anchor, int durationMinutes, int capMinutes, Scope scope) {}

    Optional<Decision> decide(Request request);
}

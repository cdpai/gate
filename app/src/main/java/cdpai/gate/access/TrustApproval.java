package cdpai.gate.access;

import java.util.List;
import java.util.Optional;

import cdpai.gate.browser.ProfileMap;
import cdpai.gate.win.PeerIdentity;

/// `cdpg trust`: a process asks that one of its own ancestors -- by default the nearest agent session,
/// such as claude.exe -- be trusted, together with every program it starts, for hours rather than
/// minutes. No heuristic caps apply here; the human decides with the passphrase, and the window's
/// colour says how much is being handed over. `requested.profiles` are already resolved to folders.
public interface TrustApproval {

    int MAX_MINUTES = 48 * 60;

    record Request(PeerIdentity client, AncestryNode target, Scope requested, String app, List<ProfileMap.Entry> profiles) {}

    record Decision(int minutes, Scope scope) {}

    Optional<Decision> decide(Request request);
}

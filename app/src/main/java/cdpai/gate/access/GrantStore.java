package cdpai.gate.access;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/// Live grants, in memory only -- restarting cdpgate means re-approving, which is the honest
/// behaviour matching "revocable at any time" from the design doc. A grant ends when its duration
/// expires, when the anchor process exits, or when revoked by hand; all three are checked here
/// rather than left to a background sweep, since the check is cheap and the alternative is a
/// stale row that lies about who currently has access.
public final class GrantStore {

    final CopyOnWriteArrayList<Grant> grants = new CopyOnWriteArrayList<>();

    public Grant create(String clientImagePath, AncestryNode anchor, int durationMinutes) {
        var expires = Instant.now().plusSeconds(durationMinutes * 60L);
        var grant = new Grant(clientImagePath, anchor.pid(),
            anchor.startInstant().orElseThrow(() -> new IllegalStateException(
                "cannot anchor to a process whose start time is unavailable")),
            anchor.imagePath(), durationMinutes, expires);
        grants.add(grant);
        return grant;
    }

    /// Re-attests a returning client: same executable, and the pinned anchor is still present
    /// somewhere in its CURRENT ancestry. Intermediate processes may differ freely -- a fresh
    /// per-command shell is expected and passes, per the design.
    public Optional<Grant> findValid(String clientImagePath, List<AncestryNode> currentChain) {
        purgeStale();
        return grants.stream()
            .filter(g -> g.coversClient(clientImagePath))
            .filter(g -> g.anchorStillPresent(currentChain))
            .findFirst();
    }

    public void revoke(Grant grant) { grants.remove(grant); }

    public List<Grant> active() { purgeStale(); return List.copyOf(grants); }

    void purgeStale() {
        var now = Instant.now();
        grants.removeIf(g -> g.isExpired(now) || !g.anchorStillAlive());
    }
}

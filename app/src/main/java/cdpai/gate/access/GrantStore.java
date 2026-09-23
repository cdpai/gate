package cdpai.gate.access;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/// Live attested grants, in memory only: restarting cdpgate means approving again. Expired grants
/// and grants whose anchor has exited are dropped whenever the list is read, so it never shows
/// access that no longer exists.
public final class GrantStore {

    final CopyOnWriteArrayList<Grant> grants = new CopyOnWriteArrayList<>();

    public Grant create(String clientImagePath, AncestryNode anchor, int minutes, int capMinutes, Scope scope) {
        var start = anchor.startInstant().orElseThrow(() -> new IllegalStateException("anchor has no start time"));
        var g = new Grant(UUID.randomUUID().toString(), clientImagePath, anchor.pid(), start, anchor.imagePath(),
            minutes, capMinutes, Instant.now().plusSeconds(minutes * 60L), scope);
        grants.add(g);
        return g;
    }

    /// Same executable, the pinned anchor still somewhere in its current ancestry, and a scope that
    /// covers the request. The shells in between may differ -- a fresh per-command shell is expected.
    /// Scope is part of the match, not checked after it: one client may hold grants for several
    /// profiles, and taking the first and then finding it too narrow re-asked the human every time.
    public Optional<Grant> findValid(String clientImagePath, List<AncestryNode> chain, Scope requested) {
        purgeStale();
        return grants.stream().filter(g -> g.coversClient(clientImagePath)).filter(g -> g.anchorStillPresent(chain))
            .filter(g -> g.scope().covers(requested)).findFirst();
    }

    public Optional<Grant> byId(String id) {
        purgeStale();
        return grants.stream().filter(g -> g.id().equals(id)).findFirst();
    }

    /// Extends by the grant's own duration from now, never beyond its cap.
    public Optional<Grant> extend(String id) {
        return byId(id).map(g -> {
            var ext = g.extendedTo(Instant.now().plusSeconds(Math.min(g.durationMinutes(), g.capMinutes()) * 60L));
            grants.replaceAll(x -> x.id().equals(id) ? ext : x);
            return ext;
        });
    }

    public void revoke(String id) { grants.removeIf(g -> g.id().equals(id)); }

    public void clear() { grants.clear(); }

    public List<Grant> active() { purgeStale(); return List.copyOf(grants); }

    void purgeStale() {
        var now = Instant.now();
        grants.removeIf(g -> g.isExpired(now) || !g.anchorStillAlive());
    }
}

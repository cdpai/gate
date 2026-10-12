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

    public Grant create(String clientImagePath, AncestryNode anchor, int minutes, int capMinutes, Scope scope, boolean blanket) {
        var start = anchor.startInstant().orElseThrow(() -> new IllegalStateException("anchor has no start time"));
        var g = new Grant(UUID.randomUUID().toString(), clientImagePath, anchor.pid(), start, anchor.imagePath(),
            minutes, capMinutes, Instant.now().plusSeconds(minutes * 60L), scope, blanket);
        grants.add(g);
        return g;
    }

    /// Same executable (or a trust grant, which takes any), the pinned anchor still somewhere in its
    /// current ancestry, and a scope that covers the request. The shells in between may differ -- a
    /// fresh per-command shell is expected. Scope is part of the match, not checked after it: one
    /// client may hold grants for several profiles, and taking the first and then finding it too
    /// narrow re-asked the human every time.
    public Optional<Grant> findValid(String clientImagePath, List<AncestryNode> chain, Scope requested) {
        purgeStale();
        return grants.stream().filter(g -> g.coversClient(clientImagePath)).filter(g -> g.anchorStillPresent(chain))
            .filter(g -> g.scope().covers(requested)).findFirst();
    }

    public Optional<Grant> byId(String id) {
        purgeStale();
        return grants.stream().filter(g -> g.id().equals(id)).findFirst();
    }

    /// Empty when the grant has already ended -- the caller must say so rather than look as if it worked.
    public Optional<Grant> extend(String id) {
        return byId(id).map(g -> replace(g.withExpiry(g.extendedExpiry(Instant.now()))));
    }

    /// Blanket mode was extended: its grants run until its new end too.
    public void extendBlanketGrants(Instant newEnd) {
        purgeStale();
        grants.stream().filter(g -> g.blanket() && g.expiresAt().isBefore(newEnd)).forEach(g -> replace(g.withExpiry(newEnd)));
    }

    Grant replace(Grant g) {
        grants.replaceAll(x -> x.id().equals(g.id()) ? g : x);
        return g;
    }

    public void revoke(String id) { grants.removeIf(g -> g.id().equals(id)); }

    public void clear() { grants.clear(); }

    public List<Grant> active() { purgeStale(); return List.copyOf(grants); }

    void purgeStale() {
        var now = Instant.now();
        grants.removeIf(g -> g.isExpired(now) || !g.anchorStillAlive());
    }
}

package cdpai.gate.access;

import java.time.Instant;

/// An attested approval: the client executable, the ancestor it is anchored to -- pinned by
/// (pid, start time, image path), never by the client's own short-lived PID -- the scope shown to
/// the human, the duration chosen and the cap that applied, and when it ends. It also ends the
/// moment the anchor exits. `capMinutes` bounds any later extension: an extension never leaves more
/// than the cap remaining. A null `clientImagePath` is a trust grant (`cdpg trust`): every program
/// under the anchor passes, whatever its executable. `blanket` marks a grant issued by blanket mode,
/// so extending blanket mode can carry these grants along with it.
public record Grant(String id, String clientImagePath, long anchorPid, Instant anchorStartInstant,
                    String anchorImagePath, int durationMinutes, int capMinutes, Instant expiresAt, Scope scope,
                    boolean blanket) {

    public static final int MIN_EXTEND_MINUTES = 30;

    public boolean anyClient() { return clientImagePath == null; }

    public boolean coversClient(String candidateImagePath) { return anyClient() || clientImagePath.equalsIgnoreCase(candidateImagePath); }

    public boolean anchorStillPresent(java.util.List<AncestryNode> chain) {
        return chain.stream().anyMatch(n -> n.pid() == anchorPid && n.startInstant().map(anchorStartInstant::equals).orElse(false));
    }

    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }

    public boolean anchorStillAlive() {
        return ProcessHandle.of(anchorPid).filter(ProcessHandle::isAlive)
            .flatMap(p -> p.info().startInstant()).map(anchorStartInstant::equals).orElse(false);
    }

    /// Adds the grant's own duration (at least `MIN_EXTEND_MINUTES`) on top of what is left, never
    /// leaving more than the cap. Repeated extends add up until the cap.
    Instant extendedExpiry(Instant now) {
        var from = expiresAt.isAfter(now) ? expiresAt : now;
        var added = from.plusSeconds(Math.max(durationMinutes, MIN_EXTEND_MINUTES) * 60L);
        var ceiling = now.plusSeconds(capMinutes * 60L);
        return added.isAfter(ceiling) ? ceiling : added;
    }

    Grant withExpiry(Instant newExpiry) {
        return new Grant(id, clientImagePath, anchorPid, anchorStartInstant, anchorImagePath, durationMinutes, capMinutes, newExpiry, scope, blanket);
    }
}

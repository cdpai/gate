package cdpai.gate.access;

import java.time.Instant;

/// An attested approval: the client executable, the ancestor it is anchored to -- pinned by
/// (pid, start time, image path), never by the client's own short-lived PID -- the scope shown to
/// the human, the duration chosen and the cap that applied, and when it ends. It also ends the
/// moment the anchor exits. `capMinutes` bounds any later extension, so a grant cannot be renewed
/// wider or longer than the anchor originally earned.
public record Grant(String id, String clientImagePath, long anchorPid, Instant anchorStartInstant,
                    String anchorImagePath, int durationMinutes, int capMinutes, Instant expiresAt, Scope scope) {

    public boolean coversClient(String candidateImagePath) { return clientImagePath.equalsIgnoreCase(candidateImagePath); }

    public boolean anchorStillPresent(java.util.List<AncestryNode> chain) {
        return chain.stream().anyMatch(n -> n.pid() == anchorPid && n.startInstant().map(anchorStartInstant::equals).orElse(false));
    }

    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }

    public boolean anchorStillAlive() {
        return ProcessHandle.of(anchorPid).filter(ProcessHandle::isAlive)
            .flatMap(p -> p.info().startInstant()).map(anchorStartInstant::equals).orElse(false);
    }

    Grant extendedTo(Instant newExpiry) {
        return new Grant(id, clientImagePath, anchorPid, anchorStartInstant, anchorImagePath, durationMinutes, capMinutes, newExpiry, scope);
    }
}

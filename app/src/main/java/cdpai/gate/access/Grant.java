package cdpai.gate.access;

import java.time.Instant;

/// The four things a grant records, per the design doc: the client executable's image path, an
/// anchor ancestor pinned by (pid, creationTime, imagePath) rather than by the client's own PID
/// (consumers are short-lived and respawned constantly -- keying on client PID would re-prompt on
/// every invocation), the approved duration, and the expiry.
public record Grant(String clientImagePath, long anchorPid, Instant anchorStartInstant,
                     String anchorImagePath, int durationMinutes, Instant expiresAt) {

    public boolean coversClient(String candidateImagePath) {
        return clientImagePath.equalsIgnoreCase(candidateImagePath);
    }

    public boolean anchorStillPresent(java.util.List<AncestryNode> currentChain) {
        return currentChain.stream().anyMatch(n -> n.pid() == anchorPid
            && n.startInstant().map(anchorStartInstant::equals).orElse(false));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean anchorStillAlive() {
        return ProcessHandle.of(anchorPid).map(ProcessHandle::isAlive).orElse(false);
    }
}

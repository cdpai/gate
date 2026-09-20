package cdpai.gate.win;

import java.time.Instant;
import java.util.Optional;

/// The kernel's answer to "who connected" -- a PID plus what ProcessHandle can say about it at
/// the moment of connection. Immutable value object, resolved once per connection.
public record PeerIdentity(long pid, String imagePath, Optional<Instant> startInstant) {

    public static PeerIdentity resolve(long pid) {
        var handle = ProcessHandle.of(pid).orElseThrow(() ->
            new IllegalStateException("pid " + pid + " vanished before it could be identified"));
        var info = handle.info();
        return new PeerIdentity(pid, info.command().orElse("(unknown)"), info.startInstant());
    }
}

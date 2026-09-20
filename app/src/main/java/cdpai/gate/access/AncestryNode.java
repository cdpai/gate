package cdpai.gate.access;

import java.time.Instant;
import java.util.Optional;

/// One level of an ancestry chain, decorated with what the approval decision needs: how long
/// this process has lived, and how many live descendants it currently has -- the number that
/// turns "how broad is this anchor" from an abstraction into something a human can read at a
/// glance, per the design doc.
public record AncestryNode(long pid, String imagePath, Optional<Instant> startInstant, int descendantCount) {

    public String imageName() {
        var i = imagePath.lastIndexOf('\\');
        return (i < 0 ? imagePath : imagePath.substring(i + 1)).toLowerCase();
    }
}

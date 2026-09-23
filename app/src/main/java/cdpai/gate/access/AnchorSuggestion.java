package cdpai.gate.access;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/// The suggestion principle from the design doc: "prefer the shallowest ancestor that is
/// materially longer-lived than the client, and never suggest at or above the terminal host."
/// Shared by ConsoleApproval and the JavaFX approval window so the two never drift apart.
public final class AnchorSuggestion {

    /// Returns the suggested hop index, or -1 if nothing above the client looks materially
    /// longer-lived (the human then has to pick by hand).
    public static int suggest(List<AncestryNode> chain, ProcessTree tree) {
        for (var hop = 1; hop < chain.size(); hop++) {
            var node = chain.get(hop);
            var category = CategoryClassifier.classify(node, hop, chain.size(), tree);
            var cap = DurationCaps.compute(category, hop, node.descendantCount());
            if (cap.minutes() > 0 && materiallyLongerLived(chain.get(0), node)) return hop;
        }
        return -1;
    }

    static boolean materiallyLongerLived(AncestryNode client, AncestryNode candidate) {
        return ageSeconds(candidate) > ageSeconds(client) + 5;
    }

    static long ageSeconds(AncestryNode node) {
        return node.startInstant().map(s -> Duration.between(s, Instant.now()).toSeconds()).orElse(0L);
    }

    private AnchorSuggestion() {}
}

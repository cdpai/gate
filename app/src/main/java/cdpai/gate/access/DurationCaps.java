package cdpai.gate.access;

/// The three independent caps the design doc combines by minimum, and which one bound the
/// result -- so the approval window can name the reason rather than leave it mysterious.
/// These bands are heuristic and tunable on purpose: the architecture does not depend on getting
/// them exactly right, only on the shape (broader anchor earns less time).
public final class DurationCaps {

    public record Result(int minutes, String boundBy) {}

    public static Result compute(AnchorCategory category, int hopsFromRequester, int descendantCount) {
        var byCategory = category.capMinutes;
        var byHops = byHopDistance(hopsFromRequester);
        var byDescendants = byDescendantCount(descendantCount);

        var minutes = Math.min(byCategory, Math.min(byHops, byDescendants));
        var reason = minutes == byCategory ? "anchor category (" + category + ")"
            : minutes == byHops ? "distance from requesting process (" + hopsFromRequester + " hops)"
            : "live descendant count (" + descendantCount + ")";
        return new Result(minutes, reason);
    }

    static int byHopDistance(int hops) {
        if (hops <= 2) return 1440;
        if (hops == 3) return 480;
        if (hops == 4) return 120;
        if (hops == 5) return 60;
        return 30;
    }

    static int byDescendantCount(int count) {
        if (count < 2) return 1440;
        if (count < 5) return 120;
        if (count < 30) return 30;
        return 0;
    }

    private DurationCaps() {}
}

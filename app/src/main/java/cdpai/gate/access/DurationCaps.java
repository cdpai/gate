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

    /// The fourth cap: how much the request reaches. Every profile and every website earns an hour
    /// at most, one of the two narrowed four hours, both narrowed nothing extra -- so the cheapest way
    /// to a long approval is to ask for less.
    public static Result compute(AnchorCategory category, int hopsFromRequester, int descendantCount, Scope scope) {
        var base = compute(category, hopsFromRequester, descendantCount);
        var byScope = BY_SCOPE_DIMENSIONS[scope.narrowedDimensions()];
        return byScope < base.minutes()
            ? new Result(byScope, scope.unscoped() ? "an unscoped request (every profile, every website)" : "a request narrowed only one way")
            : base;
    }

    static final int[] BY_SCOPE_DIMENSIONS = {60, 240, 1440};

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

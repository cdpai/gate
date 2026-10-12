package cdpai.gate.access;

/// How much a trust hands over, as the colour the window is painted in -- judged before any word is
/// read: green / amber / red, like a traffic light. Breadth sets the base: one profile and named
/// websites is green, anything narrowed short of that amber, every profile and every website red.
/// More than a day raises it one step.
public enum TrustRisk {

    GREEN("LOWER RISK", "#1b5e20", "#eaf5ec"),
    AMBER("WIDER ACCESS", "#bf4b00", "#fff4e5"),
    RED("EVERYTHING", "#b71c1c", "#fdeaea");

    public final String badge, accent, tint;

    TrustRisk(String badge, String accent, String tint) { this.badge = badge; this.accent = accent; this.tint = tint; }

    public static TrustRisk of(Scope scope, int minutes) {
        var base = DurationCaps.singleProfileNamedSites(scope) ? 0 : scope.unscoped() ? 2 : 1;
        return values()[Math.min(2, base + (minutes > 1440 ? 1 : 0))];
    }
}

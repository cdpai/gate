package cdpai.gate.ui;

/// The duration ladders shown as chips. Attested approvals run up to a day, capped by how broad the
/// chosen anchor is; keyed approvals up to thirty days, capped by how narrowly they are scoped.
/// Chips above the cap stay visible but struck through, so the cost of breadth is seen.
public final class DurationChips {

    public record Chip(int minutes, String label) {}

    public static final Chip[]
        ATTESTED = {
            new Chip(15, "15m"), new Chip(30, "30m"), new Chip(60, "1h"), new Chip(120, "2h"),
            new Chip(240, "4h"), new Chip(480, "8h"), new Chip(1440, "1 day")},
        KEYED = {
            new Chip(60, "1h"), new Chip(240, "4h"), new Chip(1440, "1 day"), new Chip(7 * 1440, "7 days"),
            new Chip(30 * 1440, "30 days")};

    /// The chip to preselect: the largest one within both the cap and what the client asked for,
    /// or simply within the cap when the client did not say.
    public static int preselect(Chip[] chips, int capMinutes, Integer requested) {
        var limit = requested == null ? capMinutes : Math.min(capMinutes, requested);
        var best = 0;
        for (var c : chips) if (c.minutes() <= limit) best = c.minutes();
        if (best == 0) for (var c : chips) if (c.minutes() <= capMinutes) { best = c.minutes(); break; }
        return best;
    }

    public static int largestFitting(int capMinutes) { return preselect(ATTESTED, capMinutes, null); }

    public static String label(int minutes) {
        if (minutes % 1440 == 0) return minutes / 1440 + (minutes == 1440 ? " day" : " days");
        if (minutes % 60 == 0) return minutes / 60 + "h";
        return minutes + "m";
    }

    private DurationChips() {}
}

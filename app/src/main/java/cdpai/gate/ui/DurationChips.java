package cdpai.gate.ui;

/// The fixed duration ladder shown as chips in the approval window: 15m, 30m, 1h, 2h, 4h, 8h,
/// 1 day. A chip is selectable only when its minutes fit within the anchor's computed cap --
/// "choosing a broader anchor visibly removes duration options" (design doc).
public final class DurationChips {

    public record Chip(int minutes, String label) {}

    public static final Chip[] ALL = {
        new Chip(15, "15m"), new Chip(30, "30m"), new Chip(60, "1h"), new Chip(120, "2h"),
        new Chip(240, "4h"), new Chip(480, "8h"), new Chip(1440, "1 day"),
    };

    /// The largest chip that still fits within the cap -- the default selection, so approving
    /// grants the full duration the chosen anchor allows without the human having to reach for it.
    public static int largestFitting(int capMinutes) {
        var best = 0;
        for (var chip : ALL) if (chip.minutes() <= capMinutes) best = chip.minutes();
        return best;
    }

    private DurationChips() {}
}

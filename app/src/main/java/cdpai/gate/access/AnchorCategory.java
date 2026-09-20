package cdpai.gate.access;

/// What kind of process an anchor is, and the duration cap that category earns on its own --
/// one of the three independent caps the design doc combines by minimum. Session root and
/// terminal host are zero: not merely short, genuinely unselectable, because a level that cannot
/// be granted at all is the one property that keeps a tired human from clicking the broadest
/// option to stop being asked.
public enum AnchorCategory {

    SESSION_ROOT(0),
    TERMINAL_HOST(0),
    SHELL_OR_TAB(30),
    AGENT_SESSION(120),
    ORDINARY_PROCESS(1440);

    public final int capMinutes;

    AnchorCategory(int capMinutes) { this.capMinutes = capMinutes; }
}

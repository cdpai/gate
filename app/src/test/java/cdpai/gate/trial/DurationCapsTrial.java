package cdpai.gate.trial;

import java.util.List;

import cdpai.gate.access.DurationCaps;
import cdpai.gate.access.Scope;

import static cdpai.gate.access.AnchorCategory.*;
import static cdpai.gate.trial.Checks.*;

/// Pure logic proof for the scope caps, including the four-hour floor for one profile and named
/// websites (PRP 12). mvn exec:java -Dexec.mainClass=cdpai.gate.trial.DurationCapsTrial -Dexec.classpathScope=test
public class DurationCapsTrial {
    public static void main(String[] args) {
        var fb = new Scope(List.of("facebook.com"), List.of("Default"), 240);
        var twoProfiles = new Scope(List.of("facebook.com"), List.of("Default", "Profile 1"), 240);
        var profileOnly = new Scope(null, List.of("Default"), 240);

        check("agent session, many descendants, one profile + one site: 4h",
            DurationCaps.compute(AGENT_SESSION, 2, 12, fb).minutes() == 240);
        check("shell anchor, one profile + one site: 4h", DurationCaps.compute(SHELL_OR_TAB, 1, 1, fb).minutes() == 240);
        check("six hops, one profile + one site: 4h", DurationCaps.compute(ORDINARY_PROCESS, 6, 1, fb).minutes() == 240);
        check("ordinary process, few hops, one profile + one site: still a day",
            DurationCaps.compute(ORDINARY_PROCESS, 1, 1, fb).minutes() == 1440);
        check("terminal host stays unselectable (0)", DurationCaps.compute(TERMINAL_HOST, 1, 1, fb).minutes() == 0);
        check("30+ descendants stays unselectable (0)", DurationCaps.compute(ORDINARY_PROCESS, 1, 40, fb).minutes() == 0);
        check("two profiles get no floor: agent with many descendants 30m",
            DurationCaps.compute(AGENT_SESSION, 2, 12, twoProfiles).minutes() == 30);
        check("profile only, no sites: capped at 4h, no floor",
            DurationCaps.compute(AGENT_SESSION, 2, 12, profileOnly).minutes() == 30);
        check("unscoped still capped at 1h", DurationCaps.compute(ORDINARY_PROCESS, 1, 1, Scope.UNSCOPED).minutes() == 60);
        System.exit(summary());
    }
}

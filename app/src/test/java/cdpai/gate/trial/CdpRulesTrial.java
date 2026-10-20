package cdpai.gate.trial;

import java.util.List;

import cdpai.gate.access.CdpRules;
import cdpai.gate.access.CdpScope;
import cdpai.gate.browser.ProfileMap;
import leasegate.core.access.DurationCaps;
import leasegate.core.access.TrustRisk;

import static leasegate.core.access.AnchorCategory.*;
import static cdpai.gate.trial.Checks.*;

/// Pure logic proof for cdpgate's own rules on top of leasegate's: the scope caps, including the
/// four-hour floor for one profile and named websites (PRP 12), the keyed maximum, and the trust
/// colours. mvn exec:java -Dexec.mainClass=cdpai.gate.trial.CdpRulesTrial -Dexec.classpathScope=test
public class CdpRulesTrial {
    public static void main(String[] args) {
        var rules = new CdpRules(new ProfileMap());
        var fb = new CdpScope(List.of("facebook.com"), List.of("Default"), 240);
        var twoProfiles = new CdpScope(List.of("facebook.com"), List.of("Default", "Profile 1"), 240);
        var profileOnly = new CdpScope(null, List.of("Default"), 240);

        check("agent session, many descendants, one profile + one site: 4h",
            DurationCaps.compute(AGENT_SESSION, 2, 12, fb, rules).minutes() == 240);
        check("shell anchor, one profile + one site: 4h", DurationCaps.compute(SHELL_OR_TAB, 1, 1, fb, rules).minutes() == 240);
        check("six hops, one profile + one site: 4h", DurationCaps.compute(ORDINARY_PROCESS, 6, 1, fb, rules).minutes() == 240);
        check("ordinary process, few hops, one profile + one site: still a day",
            DurationCaps.compute(ORDINARY_PROCESS, 1, 1, fb, rules).minutes() == 1440);
        check("terminal host stays unselectable (0)", DurationCaps.compute(TERMINAL_HOST, 1, 1, fb, rules).minutes() == 0);
        check("30+ descendants stays unselectable (0)", DurationCaps.compute(ORDINARY_PROCESS, 1, 40, fb, rules).minutes() == 0);
        check("two profiles get no floor: agent with many descendants 30m",
            DurationCaps.compute(AGENT_SESSION, 2, 12, twoProfiles, rules).minutes() == 30);
        check("profile only, no sites: no floor",
            DurationCaps.compute(AGENT_SESSION, 2, 12, profileOnly, rules).minutes() == 30);
        check("unscoped still capped at 1h", DurationCaps.compute(ORDINARY_PROCESS, 1, 1, CdpScope.UNSCOPED, rules).minutes() == 60);

        check("keyed: unscoped 4h, one way a week, both ways 30 days",
            rules.keyedMaxMinutes(CdpScope.UNSCOPED) == 240 && rules.keyedMaxMinutes(profileOnly) == 7 * 1440 && rules.keyedMaxMinutes(fb) == 30 * 1440);

        var trustScope = new CdpScope(List.of("facebook.com"), List.of("Default"), null);
        check("risk: one profile + one site, 8h is green", TrustRisk.of(rules, trustScope, 480) == TrustRisk.GREEN);
        check("risk: same over a day is amber", TrustRisk.of(rules, trustScope, 2880) == TrustRisk.AMBER);
        check("risk: profile only is amber", TrustRisk.of(rules, new CdpScope(null, List.of("Default"), null), 480) == TrustRisk.AMBER);
        check("risk: everything is red", TrustRisk.of(rules, CdpScope.UNSCOPED, 240) == TrustRisk.RED);
        check("passphrase needed only for everything", rules.needsPassphrase(CdpScope.UNSCOPED) && !rules.needsPassphrase(profileOnly));
        System.exit(summary());
    }
}

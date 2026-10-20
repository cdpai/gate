package cdpai.gate.access;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.browser.ProfileMap;
import leasegate.core.access.*;

import static leasegate.core.access.ScopeLine.*;

/// cdpgate's own rules on top of leasegate's: what a browser request reaches is profiles and
/// websites, and the fewer of each it names, the longer it may be approved for.
public final class CdpRules implements LeaseRules<CdpScope> {

    /// Every profile and every website earns an hour at most, one of the two narrowed four hours,
    /// both narrowed nothing extra -- so the cheapest way to a long approval is to ask for less. A
    /// request for ONE profile and named websites is never held below four hours by the other caps,
    /// except where they make an anchor unselectable (0).
    static final int[] BY_SCOPE_DIMENSIONS = {60, 240, 1440};
    static final int SINGLE_PROFILE_FLOOR = 240;
    static final int[] KEYED_MAX_MINUTES = {240, 7 * 1440, 30 * 1440};

    final ProfileMap profiles;

    public CdpRules(ProfileMap profiles) { this.profiles = profiles; }

    @Override public String product() { return "cdpgate"; }

    @Override public DurationCaps.Result capByScope(DurationCaps.Result base, CdpScope scope) {
        if (singleProfileNamedSites(scope) && base.minutes() > 0 && base.minutes() < SINGLE_PROFILE_FLOOR)
            return new DurationCaps.Result(SINGLE_PROFILE_FLOOR, "one profile and named websites (raised from " + base.minutes() + "m, " + base.boundBy() + ")");
        var byScope = BY_SCOPE_DIMENSIONS[scope.narrowness()];
        return byScope < base.minutes()
            ? new DurationCaps.Result(byScope, scope.unscoped() ? "an unscoped request (every profile, every website)" : "a request narrowed only one way")
            : base;
    }

    public static boolean singleProfileNamedSites(CdpScope s) {
        return s.profiles() != null && s.profiles().size() == 1 && s.domains() != null && !s.domains().isEmpty();
    }

    @Override public int keyedMaxMinutes(CdpScope scope) { return KEYED_MAX_MINUTES[scope.narrowness()]; }

    @Override public int riskBase(CdpScope scope) { return singleProfileNamedSites(scope) ? 0 : scope.unscoped() ? 2 : 1; }

    @Override public List<ScopeLine> describe(CdpScope s) {
        var out = new ArrayList<ScopeLine>();
        var entries = profiles.entries();
        out.add(s.profiles() == null
            ? wide("profiles: ALL, " + entries.stream().filter(e -> e.contextId() != null).count() + " loaded now, and any opened later")
            : narrow("profiles: " + String.join(", ", s.profiles().stream().map(this::describeProfile).toList())));
        out.add(s.domains() == null ? wide("websites: ALL") : narrow("websites: " + String.join(", ", s.domains())));
        out.add(s.profiles() == null
            ? wide("cookies: every site's cookies, in every profile")
            : new ScopeLine("cookies: every site's cookies in " + (s.profiles().size() == 1 ? "that profile" : "those profiles")
                + " (a profile shares one cookie jar; websites limit tabs only)", s.domains() == null ? Tone.NOTE : Tone.CAUTION));
        return out;
    }

    String describeProfile(String dir) {
        return profiles.entries().stream().filter(e -> e.dir().equals(dir)).findFirst()
            .map(e -> e.name() + " (" + e.dir() + (e.contextId() == null ? ", opens on approval" : "") + ")").orElse(dir);
    }

    @Override public ScopeLine summary(CdpScope s) {
        var p = s.profiles() == null ? "all profiles" : String.join(", ", s.profiles().stream().map(profiles::nameOf).toList());
        var d = s.domains() == null ? "all websites" : String.join(", ", s.domains());
        return new ScopeLine(p + "  ·  " + d, s.unscoped() ? Tone.WIDE : Tone.NARROW);
    }

    @Override public void writeScope(ObjectNode n, CdpScope s) {
        if (s.domains() != null) s.domains().forEach(n.putArray("domains")::add);
        if (s.profiles() != null) s.profiles().forEach(n.putArray("profiles")::add);
    }

    @Override public CdpScope readScope(JsonNode n) { return new CdpScope(list(n, "domains"), list(n, "profiles"), null); }

    @Override public CdpScope stored(CdpScope s) { return new CdpScope(s.domains(), s.profiles(), null); }

    static List<String> list(JsonNode n, String field) {
        if (!n.hasNonNull(field)) return null;
        var out = new ArrayList<String>();
        n.get(field).forEach(e -> out.add(e.asText()));
        return out;
    }
}

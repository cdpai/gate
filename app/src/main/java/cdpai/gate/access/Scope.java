package cdpai.gate.access;

import java.util.List;

/// What a connection may reach beyond speaking CDP at all: `domains` are plain hosts, `profiles`
/// are profile folders. `null` in either means "not narrowed" and earns the shortest approval;
/// an empty list means "none". `requestedMinutes` is what the client asked for, shown to the human
/// and never assumed.
public record Scope(List<String> domains, List<String> profiles, Integer requestedMinutes) {

    public static final Scope UNSCOPED = new Scope(null, null, null);

    public boolean domainsScoped() { return domains != null; }

    public boolean profilesScoped() { return profiles != null; }

    public boolean unscoped() { return domains == null && profiles == null; }

    public int narrowedDimensions() { return (domainsScoped() ? 1 : 0) + (profilesScoped() ? 1 : 0); }

    /// Whether `requested` fits inside this approved scope: every asked domain matches an approved
    /// one (subdomains included), every asked profile is approved, and nothing narrowed here is
    /// asked for unnarrowed.
    public boolean covers(Scope requested) {
        var domainsOk = domains == null || requested.domains != null && requested.domains.stream()
            .allMatch(d -> domains.stream().anyMatch(a -> ScopePolicy.matchesHost(d, a)));
        var profilesOk = profiles == null || requested.profiles != null && profiles.containsAll(requested.profiles);
        return domainsOk && profilesOk;
    }

    /// What a connection gets once `covers(requested)` holds: the request wherever it narrows,
    /// this approval elsewhere.
    public Scope narrowedTo(Scope requested) {
        return new Scope(requested.domains != null ? requested.domains : domains,
            requested.profiles != null ? requested.profiles : profiles, requested.requestedMinutes);
    }
}

package cdpai.gate;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.access.Scope;
import cdpai.gate.browser.ProfileMap;

/// The `{"gate":{..}}` frames cdpgate sends a v2 client before any CDP flows. Every refusal
/// carries a hint saying what to change, and every approval of an unscoped request says how a
/// scoped one would have fared -- the nudge towards narrow requests lives in the protocol itself.
final class GateReplies {

    static final ObjectMapper M = new ObjectMapper();

    static String denied(String error, String hint) {
        return wrap(M.createObjectNode().put("ok", false).put("error", error).put("hint", hint));
    }

    static String challenge(String nonce) { return wrap(M.createObjectNode().put("challenge", nonce)); }

    static String approved(String mode, Instant expiresAt, Scope scope, ProfileMap profiles) {
        var g = M.createObjectNode().put("ok", true).put("mode", mode).put("expiresAt", expiresAt.toString());
        if (scope.domains() != null) scope.domains().forEach(g.putArray("domains")::add);
        if (scope.profiles() != null) {
            var arr = g.putArray("profiles");
            for (var dir : scope.profiles())
                arr.addObject().put("dir", dir).put("name", profiles.nameOf(dir)).put("contextId", profiles.contextOf(dir).orElse(null));
        }
        g.put("hint", hint(mode, scope));
        return wrap(g);
    }

    static String hint(String mode, Scope scope) {
        if (scope.narrowedDimensions() == 2) return "scoped to profiles and domains: the longest approvals are available";
        var missing = !scope.profilesScoped() && !scope.domainsScoped() ? "profiles and domains"
            : !scope.profilesScoped() ? "profiles" : "domains";
        return "not narrowed by " + missing + ", so this approval is short; name the " + missing
            + " you need (see `cdpg profiles`) and " + (mode.equals("keyed") ? "use a key" : "the approval can run longer")
            + " -- a keyed app scoped both ways can be approved for up to 30 days";
    }

    static String profiles(String browser, boolean running, List<ProfileMap.Entry> entries) {
        var g = M.createObjectNode().put("ok", true).put("browser", browser).put("running", running);
        var arr = g.putArray("profiles");
        for (var e : entries)
            arr.addObject().put("dir", e.dir()).put("name", e.name()).put("loaded", e.contextId() != null)
                .put("contextId", e.contextId()).put("inferred", e.inferred());
        g.put("hint", "request profiles by name or dir; a profile that is not loaded is opened when the request is approved");
        return wrap(g);
    }

    /// Who has access right now, as the access window shows it. No key material: public keys stay
    /// in cdpgate, only their fingerprints are listed.
    static String grants(GateServer server, ProfileMap profiles) {
        var g = M.createObjectNode().put("ok", true);
        var arr = g.putArray("grants");
        for (var x : server.activeGrants()) {
            var n = arr.addObject().put("mode", "attested").put("id", x.id()).put("client", x.clientImagePath())
                .put("anchor", x.anchorImagePath()).put("anchorPid", x.anchorPid()).put("minutes", x.durationMinutes())
                .put("expiresAt", x.expiresAt().toString()).put("live", server.liveConnections(x.id()));
            scopeInto(n, x.scope(), profiles);
        }
        var now = java.time.Instant.now();
        for (var k : server.keyedApps()) {
            if (k.expired(now)) continue;
            var n = arr.addObject().put("mode", "keyed").put("id", k.id()).put("app", k.app()).put("key", k.fingerprint())
                .put("lastSeen", k.lastSeenImagePath()).put("flagged", k.flagged()).put("expiresAt", k.expiresAt().toString())
                .put("live", server.liveConnections(k.id()));
            scopeInto(n, k.scope(), profiles);
        }
        return wrap(g);
    }

    static void scopeInto(ObjectNode n, Scope s, ProfileMap profiles) {
        if (s.domains() != null) s.domains().forEach(n.putArray("domains")::add);
        if (s.profiles() != null) s.profiles().forEach(d -> n.withArray("profiles").add(profiles.nameOf(d)));
    }

    static String wrap(ObjectNode body) { return M.createObjectNode().set("gate", body).toString(); }

    private GateReplies() {}
}

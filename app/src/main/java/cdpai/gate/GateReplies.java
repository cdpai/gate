package cdpai.gate;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.access.CdpScope;
import cdpai.gate.browser.ProfileMap;
import leasegate.core.LeaseEngine;
import leasegate.core.Replies;

import static leasegate.core.Replies.M;

/// The `{"gate":{..}}` frames cdpgate sends a v2 client before any CDP flows, beyond leasegate's
/// generic ones. Every approval of an unscoped request says how a scoped one would have fared --
/// the nudge towards narrow requests lives in the protocol itself.
final class GateReplies {

    static String approved(String mode, Instant expiresAt, CdpScope scope, ProfileMap profiles) {
        var g = M.createObjectNode();
        if (scope.domains() != null) scope.domains().forEach(g.putArray("domains")::add);
        if (scope.profiles() != null) {
            var arr = g.putArray("profiles");
            for (var dir : scope.profiles())
                arr.addObject().put("dir", dir).put("name", profiles.nameOf(dir)).put("contextId", profiles.contextOf(dir).orElse(null));
        }
        return Replies.approved(mode, expiresAt, g, hint(mode, scope));
    }

    static String hint(String mode, CdpScope scope) {
        if (mode.equals("trusted")) return "every program started by the trusted process passes without a window until expiresAt, within this scope";
        if (scope.narrowness() == 2) return "scoped to profiles and domains: the longest approvals are available";
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
        return Replies.wrap(g);
    }

    /// Who has access right now, as the access window shows it. No key material: public keys stay
    /// in cdpgate, only their fingerprints are listed.
    static String grants(LeaseEngine<CdpScope> engine, ProfileMap profiles) {
        var g = M.createObjectNode().put("ok", true);
        var arr = g.putArray("grants");
        for (var x : engine.activeGrants()) {
            var n = arr.addObject().put("mode", x.anyClient() ? "trusted" : "attested").put("id", x.id()).put("client", x.clientImagePath()).put("anyClient", x.anyClient())
                .put("anchor", x.anchorImagePath()).put("anchorPid", x.anchorPid()).put("minutes", x.durationMinutes())
                .put("expiresAt", x.expiresAt().toString()).put("live", engine.liveConnections(x.id()));
            scopeInto(n, x.scope(), profiles);
        }
        var now = Instant.now();
        for (var k : engine.keyedApps()) {
            if (k.expired(now)) continue;
            var n = arr.addObject().put("mode", "keyed").put("id", k.id()).put("app", k.app()).put("key", k.fingerprint())
                .put("lastSeen", k.lastSeenImagePath()).put("flagged", k.flagged()).put("expiresAt", k.expiresAt().toString())
                .put("live", engine.liveConnections(k.id()));
            scopeInto(n, k.scope(), profiles);
        }
        return Replies.wrap(g);
    }

    static void scopeInto(ObjectNode n, CdpScope s, ProfileMap profiles) {
        if (s.domains() != null) s.domains().forEach(n.putArray("domains")::add);
        if (s.profiles() != null) s.profiles().forEach(d -> n.withArray("profiles").add(profiles.nameOf(d)));
    }

    private GateReplies() {}
}

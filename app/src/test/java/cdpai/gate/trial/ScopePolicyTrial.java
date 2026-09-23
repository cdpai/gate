package cdpai.gate.trial;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cdpai.gate.access.Scope;
import cdpai.gate.access.ScopePolicy;
import cdpai.gate.access.ScopePolicy.TargetMeta;

import static cdpai.gate.trial.Checks.check;

/// Scope enforcement and scope arithmetic as pure logic, against the JSON shapes CdpHub hands it.
/// Profiles are folders; `dirOf` stands in for the live ProfileMap (ctxA is Default, ctxB is
/// Profile 2, ctxX is a context nobody has identified).
/// java -cp <classes> cdpai.gate.trial.ScopePolicyTrial
public class ScopePolicyTrial {

    static final ObjectMapper M = new ObjectMapper();
    static final Map<String, String> DIRS = Map.of("ctxA", "Default", "ctxB", "Profile 2");

    public static void main(String[] args) throws Exception {
        var domains = new Scope(List.of("youtube.com"), null, null);
        var both = new Scope(List.of("youtube.com"), List.of("Default"), null);

        check("unscoped allows any url", ScopePolicy.allowsUrl(Scope.UNSCOPED, "https://evil.example/x"));
        check("subdomain allowed", ScopePolicy.allowsUrl(domains, "https://m.youtube.com/x"));
        check("look-alike host refused (youtube.com.evil.example)", !ScopePolicy.allowsUrl(domains, "https://youtube.com.evil.example/"));
        check("extension pages are outside a domain scope", !ScopePolicy.allowsUrl(domains, "chrome-extension://abc/x.html"));
        check("an unidentified context is outside every profile scope", !ScopePolicy.allowsProfile(both, DIRS.get("ctxX")));

        check("navigate out of scope refused", deny("Page.navigate", "{\"url\":\"https://evil.example/\"}", both).length() > 0);
        check("createTarget in scope allowed", deny("Target.createTarget", "{\"url\":\"https://youtube.com/\"}", both).isEmpty());
        check("createTarget into another profile refused", deny("Target.createTarget",
            "{\"url\":\"https://youtube.com/\",\"browserContextId\":\"ctxB\"}", both).length() > 0);
        check("attach in scope allowed", deny("Target.attachToTarget", "{\"targetId\":\"t1\"}", both).isEmpty());
        check("attach to the other profile refused", deny("Target.attachToTarget", "{\"targetId\":\"t3\"}", both).length() > 0);
        check("attach to an unknown target fails closed", deny("Target.attachToTarget", "{\"targetId\":\"nope\"}", both).length() > 0);
        check("closing another profile's tab refused", deny("Target.closeTarget", "{\"targetId\":\"t3\"}", both).length() > 0);
        check("scoped consumers may not make new browser contexts", deny("Target.createBrowserContext", "{}", both).length() > 0);
        check("unrelated methods pass", deny("Runtime.evaluate", "{}", both).isEmpty());

        var infos = M.createArrayNode();
        infos.add(info("t1", "https://youtube.com/watch", "ctxA"));
        infos.add(info("t2", "https://evil.example/", "ctxA"));
        infos.add(info("t3", "https://youtube.com/watch", "ctxB"));
        var kept = ScopePolicy.filterTargetInfos(infos, both, M, DIRS::get);
        check("getTargets keeps only the target matching both", kept.size() == 1 && kept.get(0).path("targetId").asText().equals("t1"));

        // cookies: scoped by profile, never by domain; Storage calls and browser-level Network calls reach
        // the LAST-USED profile (ctxA = Default here unless a case says otherwise)
        check("Network cookie call on a tab session passes (the tab's own profile)", jar("Network.getAllCookies", "{}", true, both, "ctxB").isEmpty());
        check("Storage.getCookies while the last-used profile is approved passes, any site", jar("Storage.getCookies", "{}", false, both, "ctxA").isEmpty());
        check("Storage.getCookies while another profile is last-used is refused", jar("Storage.getCookies", "{}", false, both, "ctxB").length() > 0);
        check("... even when sent on a tab session (it still reads the last-used jar)", jar("Storage.getCookies", "{}", true, both, "ctxB").length() > 0);
        check("the refusal points to the tab-session route", jar("Storage.getCookies", "{}", false, both, "ctxB").contains("--target"));
        check("naming another profile's jar is refused", jar("Storage.getCookies", "{\"browserContextId\":\"ctxB\"}", false, both, "ctxA").length() > 0);
        check("browser-level Network.getAllCookies with another profile last-used is refused", jar("Network.getAllCookies", "{}", false, both, "ctxB").length() > 0);
        check("domain-only scope leaves the jar alone", jar("Storage.getCookies", "{}", false, domains, "ctxB").isEmpty());
        check("an unidentified last-used context fails closed", jar("Storage.getCookies", "{}", false, both, "ctxX").length() > 0);

        var approved = new Scope(List.of("youtube.com"), List.of("Default", "Profile 2"), null);
        check("covers a narrower request", approved.covers(new Scope(List.of("studio.youtube.com"), List.of("Default"), null)));
        check("does not cover an unnarrowed request", !approved.covers(new Scope(List.of("youtube.com"), null, null)));
        check("does not cover another domain", !approved.covers(new Scope(List.of("google.com"), List.of("Default"), null)));
        var eff = approved.narrowedTo(new Scope(List.of("studio.youtube.com"), List.of("Default"), 30));
        check("effective scope is the request where it narrows", eff.domains().equals(List.of("studio.youtube.com")) && eff.profiles().equals(List.of("Default")));
        check("an unscoped approval covers anything", Scope.UNSCOPED.covers(both));
        System.exit(Checks.summary());
    }

    static String deny(String method, String params, Scope scope) throws Exception {
        var known = Map.of(
            "t1", new TargetMeta("https://youtube.com/watch", "page", "ctxA"),
            "t3", new TargetMeta("https://youtube.com/watch", "page", "ctxB"));
        return ScopePolicy.deny(method, M.readTree(params), scope, known, DIRS::get).orElse("");
    }

    static String jar(String method, String params, boolean onSession, Scope scope, String lastUsed) throws Exception {
        return ScopePolicy.cookieJar(method, (com.fasterxml.jackson.databind.node.ObjectNode) M.readTree(params), onSession, scope,
            lastUsed, DIRS::get).orElse("");
    }

    static JsonNode info(String id, String url, String ctx) {
        return M.createObjectNode().put("targetId", id).put("url", url).put("type", "page").put("browserContextId", ctx);
    }
}

package cdpai.gate.trial;

import java.nio.file.Files;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cdpai.gate.browser.BrowserSupervisor;
import cdpai.gate.client.*;

import static cdpai.gate.trial.Checks.check;
import static cdpai.gate.trial.LiveGateTrial.PIPE;

/// The consumer-facing rules, each exercised through the real client library against the live
/// cdpgate that LiveGateTrial started.
final class LiveScenarios {

    static final ObjectMapper M = new ObjectMapper();

    static void attested(ScriptedApproval approval) throws Exception {
        System.out.println("== attested, unscoped");
        try (var c = GateClient.connect(PIPE, "trial", ScopeRequest.UNSCOPED, null)) {
            check("approved as attested: " + c.grant(), c.grant().path("mode").asText().equals("attested"));
            check("the reply hints at scoping", c.grant().path("hint").asText().contains("not narrowed"));
            check("Browser.getVersion answers", call(c, "Browser.getVersion", "{}").path("result").has("product"));
        }
        var before = approval.asked.size();
        try (var c = GateClient.connect(PIPE, "trial", ScopeRequest.UNSCOPED, null)) {
            check("a second connection from the same process reuses the grant", approval.asked.size() == before);
        }
    }

    static void scoped(ScriptedApproval approval, BrowserSupervisor browser) throws Exception {
        System.out.println("== scoped to one profile and one domain; the profile is not loaded yet");
        var loadedBefore = browser.profiles().contextOf("Profile 2").isPresent();
        check("Profile 2 is not loaded before the request", !loadedBefore);
        var scope = new ScopeRequest(List.of("example.com"), List.of("Profile 2"), 30);
        var before = approval.asked.size();
        try (var c = GateClient.connect(PIPE, "trial-scoped", scope, null)) {
            check("a scoped request inside the standing unscoped grant is not asked again", approval.asked.size() == before);
            check("Profile 2 was opened on approval: " + c.grant().path("profiles"), !c.grant().path("profiles").get(0).path("contextId").isNull());
            var ctx = c.grant().path("profiles").get(0).path("contextId").asText();
            var denied = call(c, "Target.createTarget", "{\"url\":\"https://example.org/\"}");
            check("createTarget outside the domains is refused: " + denied.path("error").path("message").asText(), denied.has("error"));
            var ok = call(c, "Target.createTarget", "{\"url\":\"https://example.com/\",\"browserContextId\":\"" + ctx + "\"}");
            check("createTarget into the scoped profile works " + ok, ok.path("result").has("targetId"));
            var bare = call(c, "Target.createTarget", "{\"url\":\"https://example.com/?bare\"}");
            var bareInfo = call(c, "Target.getTargetInfo", "{\"targetId\":\"" + bare.path("result").path("targetId").asText() + "\"}");
            check("a tab created with no context lands in the scoped profile " + bare + " " + bareInfo, bareInfo.path("result").path("targetInfo").path("browserContextId").asText().equals(ctx));
            call(c, "Target.closeTarget", "{\"targetId\":\"" + bare.path("result").path("targetId").asText() + "\"}");
            Thread.sleep(1500);
            var targets = call(c, "Target.getTargets", "{}").path("result").path("targetInfos");
            var allInScope = true;
            for (var t : targets) allInScope &= t.path("browserContextId").asText().equals(ctx);
            check("Target.getTargets shows only the scoped profile (" + targets.size() + " targets)", allInScope && targets.size() > 0);
            call(c, "Target.closeTarget", "{\"targetId\":\"" + ok.path("result").path("targetId").asText() + "\"}");
        }
        try { GateClient.connect(PIPE, "trial", new ScopeRequest(null, List.of("NoSuchProfile"), null), null); check("unknown profile refused", false); }
        catch (GateDeniedException e) { check("unknown profile refused, listing the known ones: " + e.hint, e.hint.contains("Default")); }
        try { GateClient.connect(PIPE, "trial", new ScopeRequest(List.of("*.google.com"), null, null), null); check("wildcard refused", false); }
        catch (GateDeniedException e) { check("a wildcard domain is refused", true); }
    }

    static void keyed(ScriptedApproval approval) throws Exception {
        System.out.println("== keyed");
        var keyFile = Files.createTempFile("gatekey", ".json");
        Files.delete(keyFile);
        var key = GateKey.loadOrCreate(keyFile);
        var scope = new ScopeRequest(List.of("example.com"), List.of("Default"), 7 * 1440);
        var before = approval.asked.size();
        try (var c = GateClient.connect(PIPE, "keyed-trial", scope, key)) {
            check("first keyed connection asked the human once", approval.asked.size() == before + 1);
            check("the requested duration reached the human", approval.asked.getLast().requested().requestedMinutes() == 7 * 1440);
            check("approved as keyed, for about a week: " + c.grant().path("expiresAt"), c.grant().path("mode").asText().equals("keyed"));
        }
        try (var c = GateClient.connect(PIPE, "keyed-trial", scope, GateKey.loadOrCreate(keyFile))) {
            check("reconnecting with the same key asks nobody", approval.asked.size() == before + 1);
        }
        var thief = GateKey.loadOrCreate(Files.createTempFile("thief", ".json").resolveSibling("thief-" + System.nanoTime() + ".json"));
        try (var c = new GateClient(PIPE)) {
            c.send("{\"gate\":{\"v\":2,\"op\":\"connect\",\"app\":\"thief\",\"domains\":[\"example.com\"],\"profiles\":[\"Default\"],\"publicKey\":\"" + key.publicKey() + "\"}}");
            var challenge = M.readTree(c.receive()).path("gate").path("challenge").asText();
            c.send("{\"gate\":{\"signature\":\"" + thief.sign(challenge) + "\"}}");
            var reply = M.readTree(c.receive()).path("gate");
            check("presenting someone else's public key without its private key is refused", !reply.path("ok").asBoolean(true));
        }
        try (var c = GateClient.connect(PIPE, "keyed-trial", new ScopeRequest(null, List.of("Default"), null), GateKey.loadOrCreate(keyFile))) {
            check("asking the same key for more than approved asks the human again", approval.asked.size() == before + 2);
        }
    }

    static void guards() throws Exception {
        System.out.println("== guards");
        try (var a = GateClient.connect(PIPE, "trial", ScopeRequest.UNSCOPED, null);
             var b = GateClient.connect(PIPE, "trial", ScopeRequest.UNSCOPED, null)) {
            var close = call(a, "Browser.close", "{}");
            check("no consumer may close the browser", close.has("error"));
            var targetId = "";
            for (var t : call(a, "Target.getTargets", "{}").path("result").path("targetInfos"))
                if (t.path("type").asText().equals("page")) targetId = t.path("targetId").asText();
            var sid = call(a, "Target.attachToTarget", "{\"targetId\":\"" + targetId + "\",\"flatten\":true}").path("result").path("sessionId").asText();
            var own = call(a, "Runtime.evaluate", "{\"expression\":\"1+1\"}", sid);
            check("the attaching connection can use its session", own.path("result").path("result").path("value").asInt() == 2);
            var stolen = call(b, "Runtime.evaluate", "{\"expression\":\"1+1\"}", sid);
            check("another connection cannot use that session", stolen.has("error"));
        }
    }

    static JsonNode call(GateClient c, String method, String params) throws Exception { return call(c, method, params, null); }

    static int nextId = 1000;

    static JsonNode call(GateClient c, String method, String params, String sessionId) throws Exception {
        var id = ++nextId;
        var o = M.createObjectNode().put("id", id).put("method", method);
        o.set("params", M.readTree(params));
        if (sessionId != null) o.put("sessionId", sessionId);
        c.send(o.toString());
        String raw;
        while ((raw = c.receive()) != null) {
            var n = M.readTree(raw);
            if (n.path("id").asInt(-1) == id) return n;
        }
        throw new IllegalStateException("closed before a reply to " + method);
    }
}

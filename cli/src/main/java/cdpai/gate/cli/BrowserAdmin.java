package cdpai.gate.cli;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.client.GateClient;

/// A cdpg connection that talks to the browser itself rather than to a page: browser-level CDP methods,
/// and the chrome.* APIs of Vivaldi's own UI page (an `app` target), which no ordinary tab can reach.
/// Only fixed calls are made here, so an approval for `cdpg admin` never needs a raw Runtime.evaluate.
final class BrowserAdmin implements AutoCloseable {

    static final ObjectMapper M = new ObjectMapper();

    final GateClient client;
    int ids;
    String uiSession;

    BrowserAdmin(GateClient client) { this.client = client; }

    JsonNode call(String method, ObjectNode params, String session) throws IOException {
        var id = ++ids;
        var req = M.createObjectNode().put("id", id).put("method", method);
        req.set("params", params);
        if (session != null) req.put("sessionId", session);
        client.send(req.toString());
        String raw;
        while ((raw = client.receive()) != null) {
            var o = M.readTree(raw);
            if (o.path("id").asInt(-1) != id) continue;
            if (o.has("error")) throw new IOException(method + ": " + o.path("error").path("message").asText());
            return o.path("result");
        }
        throw new IOException("connection closed with no reply");
    }

    JsonNode browser(String method) throws IOException { return call(method, M.createObjectNode(), null); }

    /// Runs a JavaScript expression in Vivaldi's UI page and returns what it evaluates to (awaited, as JSON).
    JsonNode ui(String expression) throws IOException {
        if (uiSession == null) uiSession = attachUi();
        var p = M.createObjectNode().put("expression", "(async()=>JSON.stringify(await (" + expression + ")))()")
            .put("awaitPromise", true).put("returnByValue", true);
        var r = call("Runtime.evaluate", p, uiSession);
        if (r.has("exceptionDetails")) throw new IOException("in the browser UI: " + r.path("exceptionDetails").path("exception").path("description").asText(r.path("exceptionDetails").path("text").asText()));
        return M.readTree(r.path("result").path("value").asText("null"));
    }

    String attachUi() throws IOException {
        var targets = browser("Target.getTargets").path("targetInfos");
        String found = null;
        for (var t : targets) {
            if (!t.path("type").asText().equals("app")) continue;
            if (found == null || t.path("url").asText().endsWith("/window.html")) found = t.path("targetId").asText();
        }
        if (found == null) throw new IOException("no Vivaldi UI target in scope (is this Vivaldi, and is the profile approved? Edge and Chrome have no such page)");
        return call("Target.attachToTarget", M.createObjectNode().put("targetId", found).put("flatten", true), null).path("sessionId").asText();
    }

    @Override public void close() { client.close(); }
}

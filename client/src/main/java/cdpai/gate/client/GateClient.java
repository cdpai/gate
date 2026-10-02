package cdpai.gate.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cdpai.gate.client.posix.SocketClient;
import cdpai.gate.client.win.PipeClient;

/// A consumer's connection to cdpgate. `connect` performs the gate handshake -- it blocks while a
/// human looks at the approval window -- and returns only once the connection is approved; after
/// that the pipe carries ordinary CDP JSON. Two ways to be identified:
///
/// - attested (`key == null`): cdpgate asks the kernel who is connecting and anchors the approval
///   in that process's ancestry. Right for anything started from a terminal or an agent session.
/// - keyed (`key != null`): the app proves possession of its own `GateKey`. Right for apps
///   started at logon, whose ancestry means nothing. Narrow scopes earn approvals of up to a month.
///
/// Scope what you need: an unscoped request is honoured, but earns the shortest approval, and an
/// unscoped request in both dimensions also makes the human confirm the passphrase.
public final class GateClient implements AutoCloseable {

    public static final String PIPE_NAME = "cdpai-gate";
    static final ObjectMapper MAPPER = new ObjectMapper();

    final FrameIo io;
    JsonNode grant;

    /// Raw connection with no handshake: treated as attested and unscoped. Kept for the trials
    /// and for plain CDP experiments; real consumers use `connect`.
    public GateClient(String pipeName) { this.io = open(pipeName); }

    public static GateClient connect(String pipeName, String app, ScopeRequest scope, GateKey key) {
        var c = new GateClient(pipeName);
        try {
            c.handshake(app, scope, key);
            return c;
        } catch (RuntimeException e) { c.close(); throw e; }
    }

    /// Ungated: the browser's profiles, by folder and name, and which are loaded right now.
    public static JsonNode profiles(String pipeName) {
        try (var c = new GateClient(pipeName)) {
            c.io.writeFrame(gateFrame(MAPPER.createObjectNode().put("v", 2).put("op", "profiles")));
            return c.readGateReply();
        }
    }

    /// Ungated: every approval in force now -- mode, who, scope, time left, live connections.
    public static JsonNode grants(String pipeName) {
        try (var c = new GateClient(pipeName)) {
            c.io.writeFrame(gateFrame(MAPPER.createObjectNode().put("v", 2).put("op", "grants")));
            return c.readGateReply();
        }
    }

    void handshake(String app, ScopeRequest scope, GateKey key) {
        var req = MAPPER.createObjectNode().put("v", 2).put("op", "connect").put("app", app);
        scope.writeInto(req);
        if (key != null) req.put("publicKey", key.publicKey());
        io.writeFrame(gateFrame(req));
        while (true) {
            var reply = readGateReply();
            if (reply.has("challenge")) {
                if (key == null) throw new GateDeniedException("cdpgate sent a challenge to an unkeyed client", null);
                io.writeFrame(gateFrame(MAPPER.createObjectNode().put("signature", key.sign(reply.get("challenge").asText()))));
                continue;
            }
            if (!reply.path("ok").asBoolean(false))
                throw new GateDeniedException(reply.path("error").asText("denied"), reply.path("hint").asText(null));
            grant = reply;
            return;
        }
    }

    JsonNode readGateReply() {
        var raw = io.readFrame();
        if (raw == null) throw new GateDeniedException("cdpgate closed the connection", null);
        try {
            var gate = MAPPER.readTree(raw).path("gate");
            if (gate.isMissingNode()) throw new GateDeniedException("unexpected frame before approval: " + raw, null);
            return gate;
        } catch (GateDeniedException e) { throw e; }
        catch (Exception e) { throw new GateDeniedException("unreadable reply from cdpgate: " + raw, null); }
    }

    static String gateFrame(JsonNode body) { return MAPPER.createObjectNode().set("gate", body).toString(); }

    /// What cdpgate approved: mode, expiresAt, domains, profiles (with their live contextIds), hint.
    public JsonNode grant() { return grant; }

    public void send(String json) { io.writeFrame(json); }

    public String receive() { return io.readFrame(); }

    /// Aborts a pending receive() from another thread -- the timeout mechanism for a CLI caller.
    public void cancelPendingIo() { io.cancelPendingIo(); }

    @Override public void close() { io.close(); }

    /// The named pipe on Windows, the Unix domain socket elsewhere -- see GatePlatform.
    static FrameIo open(String pipeName) {
        return GatePlatform.WINDOWS ? PipeClient.open(pipeName) : SocketClient.open(pipeName);
    }
}

package cdpai.gate.hub;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.client.win.PipeIo;

/// One browser link fans out to however many approved consumers are connected, routed by
/// sessionId -- the multiplexing the design doc calls for. Two problems this actually solves:
///
/// Consumers each pick their own "id" numbers independently, so ids collide once more than one
/// consumer shares the upstream link; every outgoing request gets remapped to a globally unique
/// upstream id, and the reply is unmapped back to the caller's own id on the way out.
///
/// Vivaldi's own CDP session multiplexing (the "sessionId" field) already tells us which target a
/// message belongs to -- we don't invent that. We only learn WHICH CONSUMER owns a given
/// sessionId by watching for it appearing in an attach reply, then route later session-tagged
/// events to that consumer. A message with no sessionId is browser-level and goes to everyone.
public final class CdpHub {

    record Pending(ConsumerLink consumer, long originalId) {}

    final PipeIo browser;
    final ObjectMapper mapper = new ObjectMapper();
    final ConcurrentHashMap<Long, Pending> pending = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, ConsumerLink> sessionOwners = new ConcurrentHashMap<>();
    final Set<ConsumerLink> consumers = ConcurrentHashMap.newKeySet();
    final AtomicLong nextUpstreamId = new AtomicLong();

    public CdpHub(PipeIo browser) {
        this.browser = browser;
    }

    public void addConsumer(ConsumerLink consumer) { consumers.add(consumer); }

    public void removeConsumer(ConsumerLink consumer) {
        consumers.remove(consumer);
        sessionOwners.values().removeIf(owner -> owner == consumer);
    }

    /// Called from each consumer's own reader thread. Rewrites "id" and forwards upstream; the
    /// single upstream link is written from many threads, so PipeIo.writeFrame is synchronized.
    public void onConsumerMessage(ConsumerLink from, String raw) {
        var obj = parseObject(raw);
        if (obj == null) return;
        if (obj.has("id")) {
            var upstreamId = nextUpstreamId.incrementAndGet();
            pending.put(upstreamId, new Pending(from, obj.get("id").asLong()));
            obj.put("id", upstreamId);
        }
        browser.writeFrame(obj.toString());
    }

    /// Runs on the ONE dedicated browser-reader thread for as long as the link is alive.
    public void pumpBrowserMessages() {
        String raw;
        while ((raw = browser.readFrame()) != null) handleBrowserMessage(raw);
    }

    void handleBrowserMessage(String raw) {
        var obj = parseObject(raw);
        if (obj == null) return;

        if (obj.has("id")) {
            var p = pending.remove(obj.get("id").asLong());
            if (p == null) return;   // reply for a consumer that is already gone
            obj.put("id", p.originalId());
            recordSessionOwnerIfAttach(obj, p.consumer());
            send(p.consumer(), obj.toString());
            return;
        }

        var sessionId = obj.path("sessionId");
        if (!sessionId.isMissingNode()) {
            var owner = sessionOwners.get(sessionId.asText());
            if (owner != null) send(owner, raw);
            if (isDetach(obj)) sessionOwners.remove(sessionId.asText());
            return;
        }
        for (var c : consumers) send(c, raw);   // browser-level event: everyone's business
    }

    void recordSessionOwnerIfAttach(ObjectNode reply, ConsumerLink consumer) {
        var sessionId = reply.path("result").path("sessionId");
        if (!sessionId.isMissingNode()) sessionOwners.put(sessionId.asText(), consumer);
    }

    static boolean isDetach(ObjectNode obj) {
        return "Target.detachedFromTarget".equals(obj.path("method").asText());
    }

    void send(ConsumerLink to, String raw) {
        try { to.io.writeFrame(raw); } catch (Exception e) { removeConsumer(to); }
    }

    ObjectNode parseObject(String raw) {
        try {
            var node = mapper.readTree(raw);
            return node instanceof ObjectNode o ? o : null;
        } catch (Exception e) { return null; }
    }
}

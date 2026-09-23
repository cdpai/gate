package cdpai.gate.hub;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.access.Scope;
import cdpai.gate.access.ScopePolicy;
import cdpai.gate.client.win.PipeIo;

/// One browser link fanned out to every approved consumer. Consumers pick colliding "id"s, so
/// each request is renumbered upstream and the reply mapped back. A session belongs to the
/// consumer whose attach produced it, and only that consumer may send on it or hear from it.
/// Scope is checked before anything goes upstream and applied to every listing and discovery
/// event coming back, so an out-of-scope target cannot be reached or even seen.
public final class CdpHub {

    record Pending(ConsumerLink consumer, long originalId, CompletableFuture<JsonNode> internal) {}

    /// cdpgate owns the browser's lifetime; no consumer may end or crash it.
    static final Set<String> ALWAYS_DENIED = Set.of("Browser.close", "Browser.crash", "Browser.crashGpuProcess");

    final PipeIo browser;
    final Function<String, String> dirOf;
    final TabPlacement placement;
    final TargetInventory inventory;
    final ObjectMapper mapper = new ObjectMapper();
    final ConcurrentHashMap<Long, Pending> pending = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, ConsumerLink> sessionOwners = new ConcurrentHashMap<>();
    final Set<ConsumerLink> consumers = ConcurrentHashMap.newKeySet();
    final ConcurrentHashMap<ConsumerLink, Scope> scopes = new ConcurrentHashMap<>();
    final AtomicLong nextUpstreamId = new AtomicLong();
    final ConcurrentHashMap<String, ConsumerLink> pendingAttach = new ConcurrentHashMap<>();
    volatile ConsumerLink autoAttachOwner;
    /// The context of the profile the browser was launched with: where a cookie call naming no
    /// profile lands. Distinct from the last-used default that TabPlacement follows.
    volatile Supplier<String> launchContext = () -> null;

    /// `dirOf` names the profile folder of a context; `placement` decides where new tabs go.
    public CdpHub(PipeIo browser, TargetInventory inventory, Function<String, String> dirOf, TabPlacement placement) {
        this.browser = browser;
        this.inventory = inventory;
        this.dirOf = dirOf;
        this.placement = placement;
    }

    public TargetInventory inventory() { return inventory; }

    public void launchContext(Supplier<String> s) { launchContext = s; }

    public void start() { internalCall("Target.setDiscoverTargets", mapper.createObjectNode().put("discover", true)); }

    /// cdpgate's own call to the browser, never visible to any consumer.
    public CompletableFuture<JsonNode> internalCall(String method, JsonNode params) {
        var id = nextUpstreamId.incrementAndGet();
        var f = new CompletableFuture<JsonNode>();
        pending.put(id, new Pending(null, 0, f));
        var o = mapper.createObjectNode().put("id", id).put("method", method);
        o.set("params", params);
        browser.writeFrame(o.toString());
        return f;
    }

    public void addConsumer(ConsumerLink c, Scope scope) { consumers.add(c); scopes.put(c, scope); }

    public void removeConsumer(ConsumerLink c) {
        consumers.remove(c);
        scopes.remove(c);
        sessionOwners.values().removeIf(owner -> owner == c);
        pendingAttach.values().removeIf(owner -> owner == c);
        if (autoAttachOwner == c) autoAttachOwner = null;
    }

    public void disconnectAll() { consumers.forEach(c -> c.io.cancelPendingIo()); }

    public void onConsumerMessage(ConsumerLink from, String raw) {
        var obj = parse(raw);
        if (obj == null) return;
        var denial = refuse(from, obj);
        if (denial != null) { send(from, errorReply(obj, denial)); return; }
        if (obj.path("method").asText("").equals("Target.setAutoAttach") && !obj.has("sessionId")) autoAttachOwner = from;
        var opening = placement == null ? null : placement.place(obj, scopes.getOrDefault(from, Scope.UNSCOPED));
        if (opening != null) { answerWhenOpened(from, obj, opening); return; }
        if (obj.path("method").asText("").equals("Target.attachToTarget")) pendingAttach.put(obj.path("params").path("targetId").asText(""), from);
        if (obj.has("id")) {
            var upstreamId = nextUpstreamId.incrementAndGet();
            pending.put(upstreamId, new Pending(from, obj.get("id").asLong(), null));
            obj.put("id", upstreamId);
        }
        browser.writeFrame(obj.toString());
    }

    void answerWhenOpened(ConsumerLink from, ObjectNode request, CompletableFuture<String> opening) {
        opening.whenComplete((targetId, err) -> {
            if (err != null || targetId == null) { send(from, errorReply(request, "cdpgate: the tab could not be opened in that profile")); return; }
            var reply = mapper.createObjectNode().put("id", request.path("id").asLong());
            reply.putObject("result").put("targetId", targetId);
            send(from, reply);
        });
    }

    String refuse(ConsumerLink from, ObjectNode obj) {
        var method = obj.path("method").asText("");
        if (ALWAYS_DENIED.contains(method)) return "cdpgate: " + method + " is reserved to cdpgate, which owns the browser";
        if (obj.has("sessionId") && sessionOwners.get(obj.path("sessionId").asText()) != from)
            return "cdpgate: that session does not belong to this connection";
        var scope = scopes.getOrDefault(from, Scope.UNSCOPED);
        if (!scope.unscoped() && method.equals("Target.setAutoAttach") && !obj.has("sessionId"))
            return "cdpgate: browser-wide auto-attach is not available to a scoped connection; attach to targets one at a time";
        if (!obj.has("sessionId") && ScopePolicy.isCookieCall(method)) {
            var params = obj.get("params") instanceof ObjectNode p ? p : obj.putObject("params");
            var jar = ScopePolicy.cookieJar(method, params, scope, launchContext.get(), dirOf,
                placement == null ? d -> null : placement.contextOf);
            if (jar.isPresent()) return jar.get();
        }
        return ScopePolicy.deny(method, obj.path("params"), scope, inventory.view(), dirOf).orElse(null);
    }

    ObjectNode errorReply(ObjectNode original, String message) {
        var err = mapper.createObjectNode();
        if (original.has("id")) err.put("id", original.get("id").asLong());
        if (original.has("sessionId")) err.put("sessionId", original.get("sessionId").asText());
        err.set("error", mapper.createObjectNode().put("code", -32001).put("message", message));
        return err;
    }

    /// Runs on the one browser-reader thread; returns when the browser's end of the pipe closes.
    public void pumpBrowserMessages() {
        try {
            String raw;
            while ((raw = browser.readFrame()) != null) handleBrowserMessage(raw);
        } catch (RuntimeException ignored) {
            // broken pipe: the browser has exited
        }
        pending.values().forEach(p -> { if (p.internal() != null) p.internal().cancel(true); });
    }

    void handleBrowserMessage(String raw) {
        var obj = parse(raw);
        if (obj == null) return;
        inventory.track(obj);
        if (obj.has("id")) { routeReply(obj); return; }
        var sessionId = obj.path("sessionId").asText(null);
        if (sessionId != null) { routeSessionEvent(obj, sessionId); return; }
        routeBrowserEvent(obj);
    }

    void routeReply(ObjectNode obj) {
        var p = pending.remove(obj.get("id").asLong());
        if (p == null) return;
        if (p.internal() != null) { p.internal().complete(obj); return; }
        obj.put("id", p.originalId());
        var sid = obj.path("result").path("sessionId");
        if (!sid.isMissingNode()) { sessionOwners.put(sid.asText(), p.consumer()); pendingAttach.values().remove(p.consumer()); }
        var scope = scopes.getOrDefault(p.consumer(), Scope.UNSCOPED);
        var infos = obj.path("result").path("targetInfos");
        if (!scope.unscoped() && !infos.isMissingNode())
            ((ObjectNode) obj.path("result")).set("targetInfos", ScopePolicy.filterTargetInfos(infos, scope, mapper, dirOf));
        send(p.consumer(), obj);
    }

    void routeSessionEvent(ObjectNode obj, String sessionId) {
        var owner = sessionOwners.get(sessionId);
        if (owner == null) return;
        var method = obj.path("method").asText("");
        var child = obj.path("params").path("sessionId").asText(null);
        if (method.equals("Target.attachedToTarget") && child != null) sessionOwners.put(child, owner);
        if (method.equals("Target.detachedFromTarget") && child != null) sessionOwners.remove(child);
        send(owner, obj);
    }

    void routeBrowserEvent(ObjectNode obj) {
        var method = obj.path("method").asText("");
        var info = obj.path("params").path("targetInfo");
        if (method.equals("Target.attachedToTarget")) {
            var sid = obj.path("params").path("sessionId").asText();
            var owner = sessionOwners.get(sid);
            if (owner == null) owner = pendingAttach.remove(info.path("targetId").asText(""));
            if (owner == null) owner = autoAttachOwner;
            if (owner != null) { sessionOwners.put(sid, owner); send(owner, obj); }
            return;
        }
        if (method.equals("Target.detachedFromTarget")) {
            var owner = sessionOwners.remove(obj.path("params").path("sessionId").asText(""));
            if (owner != null) send(owner, obj);
            return;
        }
        for (var c : consumers) {
            var scope = scopes.getOrDefault(c, Scope.UNSCOPED);
            if (!info.isMissingNode() && !ScopePolicy.allowsTargetInfo(info, scope, dirOf)) continue;
            send(c, obj);
        }
    }

    static boolean isTargetEvent(String method) {
        return method.equals("Target.targetCreated") || method.equals("Target.targetInfoChanged");
    }

    void send(ConsumerLink to, JsonNode msg) {
        try { to.io.writeFrame(msg.toString()); } catch (Exception e) { removeConsumer(to); }
    }

    ObjectNode parse(String raw) {
        try { return mapper.readTree(raw) instanceof ObjectNode o ? o : null; } catch (Exception e) { return null; }
    }
}

package cdpai.gate.hub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.access.ScopePolicy;
import cdpai.gate.access.ScopePolicy.TargetMeta;

/// cdpgate's own live view of every target -- url, type, browserContextId -- kept from discovery
/// events regardless of what any consumer asked to see. Scope enforcement checks against it
/// (Target.attachToTarget carries only a targetId), and profile binding watches it for contexts
/// appearing and disappearing: a profile being opened shows up as its first target in a context
/// never seen before, and a profile closing as the last target of its context going away.
public final class TargetInventory {

    final ConcurrentHashMap<String, TargetMeta> targets = new ConcurrentHashMap<>();
    volatile Consumer<String> onContextAppeared = c -> {};
    volatile Consumer<String> onContextGone = c -> {};

    public void onContextAppeared(Consumer<String> l) { onContextAppeared = l; }

    public void onContextGone(Consumer<String> l) { onContextGone = l; }

    public Map<String, TargetMeta> view() { return targets; }

    public boolean hasContext(String ctx) {
        return ctx != null && targets.values().stream().anyMatch(t -> ctx.equals(t.browserContextId()));
    }

    public java.util.Set<String> contexts() {
        var s = new java.util.HashSet<String>();
        targets.values().forEach(t -> { if (t.browserContextId() != null) s.add(t.browserContextId()); });
        return s;
    }

    void track(ObjectNode msg) {
        var method = msg.path("method").asText("");
        if (method.equals("Target.targetDestroyed") || method.equals("Target.targetCrashed")) {
            var gone = targets.remove(msg.path("params").path("targetId").asText(""));
            if (gone != null && gone.browserContextId() != null && !hasContext(gone.browserContextId()))
                onContextGone.accept(gone.browserContextId());
        } else if (CdpHub.isTargetEvent(method)) {
            put(msg.path("params").path("targetInfo"));
        }
    }

    void put(com.fasterxml.jackson.databind.JsonNode info) {
        var id = info.path("targetId").asText(null);
        if (id == null) return;
        var meta = ScopePolicy.metaOf(info);
        var ctx = meta.browserContextId();
        var fresh = ctx != null && !hasContext(ctx);
        targets.put(id, meta);
        if (fresh) onContextAppeared.accept(ctx);
    }
}

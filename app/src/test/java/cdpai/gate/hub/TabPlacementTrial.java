package cdpai.gate.hub;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.access.CdpScope;

import static cdpai.gate.trial.Checks.check;

/// PRP 30 (D1, D2, D6): a profile-scoped createTarget for a profile that has no bound context must
/// be refused with a message naming the profile, must not be sent upstream (where it lands in the
/// browser's default context and is then invisible to the caller), and must not open a window.
/// java -cp <classes> cdpai.gate.hub.TabPlacementTrial
public class TabPlacementTrial {

    static final ObjectMapper M = new ObjectMapper();
    // ctxA is the browser's default context and is bound to Default; ctxB is bound to Profile 12;
    // Profile 2 has no context at all.
    static final Map<String, String> CTX_OF = Map.of("Default", "ctxA", "Profile 12", "ctxB");
    static final Map<String, String> DIR_OF = Map.of("ctxA", "Default", "ctxB", "Profile 12");

    public static void main(String[] args) throws Exception {
        var handoffs = new AtomicInteger();
        var placement = new TabPlacement(() -> "ctxA", DIR_OF::get, CTX_OF::get, (dir, url) -> {
            handoffs.incrementAndGet();
            return CompletableFuture.completedFuture("newTab");
        });

        var unbound = new CdpScope(List.of("claude.ai"), List.of("Profile 2"), null);
        var f = placement.place(create("https://claude.ai/"), unbound);
        check("unbound profile: createTarget is refused, not passed upstream", f != null);
        check("unbound profile: the refusal is an error", f != null && f.isCompletedExceptionally());
        check("unbound profile: no window is opened", handoffs.get() == 0);
        check("unbound profile: the error names the profile", f != null && message(f).contains("Profile 2"));

        var bound = new CdpScope(List.of("claude.ai"), List.of("Default"), null);
        var msg = create("https://claude.ai/");
        check("bound default profile: passes upstream", placement.place(msg, bound) == null);
        check("bound default profile: context named in params", "ctxA".equals(msg.path("params").path("browserContextId").asText(null)));

        var other = new CdpScope(List.of("claude.ai"), List.of("Profile 12"), null);
        var g = placement.place(create("https://claude.ai/"), other);
        check("bound non-default profile: opened by hand-off once", g != null && handoffs.get() == 1 && "newTab".equals(g.getNow(null)));

        check("unscoped caller: passes upstream", placement.place(create("https://claude.ai/"), CdpScope.UNSCOPED) == null);
        System.exit(cdpai.gate.trial.Checks.summary());
    }

    static ObjectNode create(String url) throws Exception {
        return (ObjectNode) M.readTree("{\"id\":1,\"method\":\"Target.createTarget\",\"params\":{\"url\":\"" + url + "\"}}");
    }

    static String message(CompletableFuture<String> f) {
        try { f.join(); return ""; } catch (Exception e) { return String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage()); }
    }
}

package cdpai.gate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import cdpai.gate.access.*;
import cdpai.gate.browser.BrowserSupervisor;
import cdpai.gate.hub.ConsumerLink;
import cdpai.gate.win.NamedPipeServer;

/// The pipe server: accepts connections, hands each to a ConnectionHandler, and keeps the list of
/// live connections honest. Expiry closes open connections rather than merely refusing new ones,
/// so a row in the access window never reads as ended while its consumer is still working; five
/// minutes before an attested grant with a live connection runs out, the UI is told so the human
/// can extend it (within the grant's own cap) or let it lapse.
public final class GateServer {

    public record LiveLink(Approval.Kind kind, String approvalId, String label) {}

    public record Expiring(String grantId, String label, Instant expiresAt) {}

    static final long SWEEP_MS = 5_000;
    static final Duration WARN_BEFORE = Duration.ofMinutes(5);

    final NamedPipeServer pipeServer;
    final BrowserSupervisor browser;
    final Approval approval;
    final GrantStore grants = new GrantStore();
    final KeyedAppStore keyed;
    final ConcurrentHashMap<ConsumerLink, LiveLink> links = new ConcurrentHashMap<>();
    final Set<String> warned = ConcurrentHashMap.newKeySet();
    volatile Consumer<Expiring> onExpiring = e -> {};

    public GateServer(BrowserSupervisor browser, String pipeName, Approval approval, KeyedAppStore keyed) {
        this.browser = browser;
        this.pipeServer = new NamedPipeServer(pipeName);
        this.approval = approval;
        this.keyed = keyed;
    }

    public void onExpiring(Consumer<Expiring> l) { onExpiring = l; }

    public void run() {
        Thread.ofPlatform().name("cdpgate-sweep").daemon().start(this::sweep);
        while (true) {
            NamedPipeServer.Accepted accepted;
            try { accepted = pipeServer.accept(); }
            catch (Exception e) { System.err.println("accept failed: " + e.getMessage()); continue; }
            var a = accepted;
            Thread.ofPlatform().name("cdpgate-consumer-" + a.peer().pid()).start(() -> new ConnectionHandler(this, a).run());
        }
    }

    void register(ConsumerLink link, Approval.Kind kind, String approvalId, String label) { links.put(link, new LiveLink(kind, approvalId, label)); }

    void unregister(ConsumerLink link) { links.remove(link); }

    public List<Grant> activeGrants() { return grants.active(); }

    public List<KeyedApp> keyedApps() { return keyed.all(); }

    public long liveConnections(String approvalId) { return links.values().stream().filter(l -> l.approvalId().equals(approvalId)).count(); }

    public void revokeGrant(String id) { grants.revoke(id); cut(id); }

    public void revokeKeyed(String id) { keyed.revoke(id); cut(id); }

    public void clearKeyedFlag(String id) { keyed.clearFlag(id); }

    public void extendGrant(String id) { grants.extend(id); warned.remove(id); }

    public void revokeAllNow() {
        grants.clear();
        links.forEach((link, l) -> { if (l.kind() == Approval.Kind.ATTESTED) link.io.cancelPendingIo(); });
    }

    void cut(String approvalId) { links.forEach((link, l) -> { if (l.approvalId().equals(approvalId)) link.io.cancelPendingIo(); }); }

    void sweep() {
        while (true) {
            try { Thread.sleep(SWEEP_MS); } catch (InterruptedException e) { return; }
            var now = Instant.now();
            links.forEach((link, l) -> {
                if (l.kind() == Approval.Kind.KEYED) { if (!keyed.isValid(l.approvalId())) link.io.cancelPendingIo(); return; }
                var g = grants.byId(l.approvalId());
                if (g.isEmpty()) { link.io.cancelPendingIo(); return; }
                if (Duration.between(now, g.get().expiresAt()).compareTo(WARN_BEFORE) <= 0 && warned.add(g.get().id()))
                    onExpiring.accept(new Expiring(g.get().id(), l.label(), g.get().expiresAt()));
            });
        }
    }
}

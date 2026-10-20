package cdpai.gate;

import java.util.ArrayList;
import java.util.List;

import cdpai.gate.access.CdpScope;
import cdpai.gate.hub.CdpHub;
import cdpai.gate.hub.ConsumerLink;
import leasegate.core.ConsumerServer;
import leasegate.core.Outcome;
import leasegate.core.Replies;
import leasegate.core.access.Approval.Kind;
import leasegate.core.access.Handshake;
import leasegate.core.access.ProcessTree;
import leasegate.core.win.PeerIdentity;
import leasegate.transport.FrameIo;

/// One connection, from first frame to disconnect. The ancestry chain is walked before anything
/// slower happens, because a short-lived shell above the client can exit within the time a
/// full-system snapshot takes, and a dead process's parent can never be read again. Deciding is
/// leasegate's engine; joining the approved connection to the browser is the hub's.
final class ConnectionHandler {

    final GateServer server;
    final PeerIdentity peer;
    final FrameIo io;
    final List<ProcessTree.RawLink> raw;
    boolean v2;

    ConnectionHandler(GateServer server, ConsumerServer.Accepted accepted) {
        this.server = server;
        this.peer = accepted.peer();
        this.io = accepted.io();
        this.raw = ProcessTree.rawChain(peer.pid());
    }

    void run() {
        try {
            var first = io.readFrame();
            if (first == null) { io.close(); return; }
            var hs = Handshake.extract(first);
            if (hs.isEmpty()) { connect(null, CdpScope.UNSCOPED, first); return; }
            v2 = true;
            switch (hs.get().op()) {
                case "profiles" -> { io.writeFrame(GateReplies.profiles(server.browser.browserName(),
                    server.browser.hub() != null, server.browser.profiles().entries())); io.close(); }
                case "grants" -> { io.writeFrame(GateReplies.grants(server.engine, server.browser.profiles())); io.close(); }
                case "connect" -> connect(hs.get(), null, null);
                case "trust" -> trust(hs.get());
                default -> deny("unknown op " + hs.get().op(), "use op connect, trust, profiles or grants");
            }
        } catch (Exception e) { io.close(); }
    }

    void connect(Handshake hs, CdpScope legacyScope, String firstCdpFrame) {
        var hub = server.browser.hub();
        if (hub == null) { deny("the browser is not running under cdpgate (" + server.browser.state() + ")",
            "wait for it to start, or use the cdpgate tray menu: Start browser"); return; }
        var scope = legacyScope != null ? legacyScope : scopeOf(hs);
        if (scope == null) return;
        var label = hs != null && hs.app() != null ? hs.app() : imageName(peer.imagePath());
        if (hs != null && hs.keyed()) keyed(hub, hs, scope, label); else attested(hub, scope, label, firstCdpFrame);
    }

    CdpScope scopeOf(Handshake hs) {
        var domains = Handshake.strings(hs.body(), "domains");
        if (domains != null && domains.stream().anyMatch(d -> !d.matches("[A-Za-z0-9.-]+"))) {
            deny("domains must be plain hosts such as youtube.com", "no wildcards, schemes, paths or patterns"); return null;
        }
        var asked = Handshake.strings(hs.body(), "profiles");
        List<String> dirs = null;
        if (asked != null) {
            dirs = new ArrayList<>();
            for (var p : asked) {
                var dir = server.browser.profiles().resolve(p);
                if (dir.isEmpty()) { deny("unknown profile " + p, "known profiles: " + known()); return null; }
                dirs.add(dir.get());
            }
        }
        return new CdpScope(domains, dirs, hs.minutes());
    }

    void attested(CdpHub hub, CdpScope requested, String label, String firstCdpFrame) {
        switch (server.engine.attest(peer, raw, requested, label)) {
            case Outcome.Refused(var error, var hint) -> deny(error, hint);
            case Outcome.Allowed(var grant) -> {
                var effective = grant.scope().narrowedTo(requested);
                openProfiles(effective);
                var link = join(hub, Kind.ATTESTED, grant.id(), label, effective);
                if (v2) io.writeFrame(GateReplies.approved("attested", grant.expiresAt(), effective, server.browser.profiles()));
                pump(hub, link, firstCdpFrame);
            }
        }
    }

    /// `cdpg trust`: no CDP flows on this connection. It ends with a grant anchored at the chosen
    /// ancestor that covers every program under it, or with a refusal.
    void trust(Handshake hs) {
        var scope = scopeOf(hs);
        if (scope == null) return;
        var label = hs.app() != null ? hs.app() : imageName(peer.imagePath());
        switch (server.engine.trust(peer, raw, scope, label, hs.pid())) {
            case Outcome.Refused(var error, var hint) -> deny(error, hint);
            case Outcome.Allowed(var grant) -> {
                io.writeFrame(GateReplies.approved("trusted", grant.expiresAt(), grant.scope(), server.browser.profiles()));
                io.close();
            }
        }
    }

    void keyed(CdpHub hub, Handshake hs, CdpScope requested, String label) {
        switch (server.engine.keyed(peer, raw, hs.publicKey(), requested, label, io)) {
            case Outcome.Refused(var error, var hint) -> deny(error, hint);
            case Outcome.Allowed(var app) -> {
                var effective = app.scope().narrowedTo(requested);
                openProfiles(effective);
                var link = join(hub, Kind.KEYED, app.id(), label, effective);
                io.writeFrame(GateReplies.approved("keyed", app.expiresAt(), effective, server.browser.profiles()));
                pump(hub, link, null);
            }
        }
    }

    ConsumerLink join(CdpHub hub, Kind kind, String approvalId, String label, CdpScope effective) {
        var link = new ConsumerLink(peer, io);
        server.engine.register(link, kind, approvalId, label, io::cancelPendingIo);
        hub.addConsumer(link, effective);
        return link;
    }

    void openProfiles(CdpScope scope) {
        if (scope.profiles() == null) return;
        for (var dir : scope.profiles())
            if (server.browser.profiles().contextOf(dir).isEmpty()) server.browser.openProfile(dir);
    }

    void pump(CdpHub hub, ConsumerLink link, String firstCdpFrame) {
        try {
            if (firstCdpFrame != null) hub.onConsumerMessage(link, firstCdpFrame);
            String msg;
            while ((msg = io.readFrame()) != null) hub.onConsumerMessage(link, msg);
        } catch (Exception ignored) {
            // disconnected, or cut by revoke / expiry / browser exit
        } finally {
            server.engine.unregister(link);
            hub.removeConsumer(link);
            io.close();
        }
    }

    void deny(String error, String hint) {
        if (v2) try { io.writeFrame(Replies.denied(error, hint)); } catch (Exception ignored) {}
        io.close();
    }

    String known() {
        return String.join(", ", server.browser.profiles().profiles().stream().map(p -> p.name() + " (" + p.dir() + ")").toList());
    }

    static String imageName(String path) { var i = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/')); return (i < 0 ? path : path.substring(i + 1)).toLowerCase(); }
}

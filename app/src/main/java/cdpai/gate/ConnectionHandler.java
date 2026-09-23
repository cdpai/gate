package cdpai.gate;

import java.util.ArrayList;
import java.util.List;

import cdpai.gate.access.*;
import cdpai.gate.access.Approval.Kind;
import cdpai.gate.hub.CdpHub;
import cdpai.gate.hub.ConsumerLink;
import cdpai.gate.win.NamedPipeServer;
import cdpai.gate.win.PeerIdentity;

/// One connection, from first frame to disconnect. The ancestry chain is walked before anything
/// slower happens, because a short-lived shell above the client can exit within the time a
/// full-system snapshot takes, and a dead process's parent can never be read again.
final class ConnectionHandler {

    final GateServer server;
    final PeerIdentity peer;
    final cdpai.gate.client.win.PipeIo io;
    final List<ProcessTree.RawLink> raw;
    boolean v2;

    ConnectionHandler(GateServer server, NamedPipeServer.Accepted accepted) {
        this.server = server;
        this.peer = accepted.peer();
        this.io = accepted.io();
        this.raw = ProcessTree.rawChain(peer.pid());
    }

    void run() {
        try {
            var first = io.readFrame();
            if (first == null) { io.close(); return; }
            var hs = GateHandshake.extract(first);
            if (hs.isEmpty()) { connect(null, Scope.UNSCOPED, first); return; }
            v2 = true;
            switch (hs.get().op()) {
                case "profiles" -> { io.writeFrame(GateReplies.profiles(server.browser.browserName(),
                    server.browser.hub() != null, server.browser.profiles().entries())); io.close(); }
                case "grants" -> { io.writeFrame(GateReplies.grants(server, server.browser.profiles())); io.close(); }
                case "connect" -> connect(hs.get(), null, null);
                default -> deny("unknown op " + hs.get().op(), "use op connect, profiles or grants");
            }
        } catch (Exception e) { io.close(); }
    }

    void connect(GateHandshake hs, Scope legacyScope, String firstCdpFrame) {
        var hub = server.browser.hub();
        if (hub == null) { deny("the browser is not running under cdpgate (" + server.browser.state() + ")",
            "wait for it to start, or use the cdpgate tray menu: Start browser"); return; }
        var scope = legacyScope != null ? legacyScope : scopeOf(hs);
        if (scope == null) return;
        var label = hs != null && hs.app() != null ? hs.app() : imageName(peer.imagePath());
        if (hs != null && hs.keyed()) keyed(hub, hs, scope, label); else attested(hub, scope, label, firstCdpFrame);
    }

    Scope scopeOf(GateHandshake hs) {
        if (hs.domains() != null && hs.domains().stream().anyMatch(d -> !d.matches("[A-Za-z0-9.-]+"))) {
            deny("domains must be plain hosts such as youtube.com", "no wildcards, schemes, paths or patterns"); return null;
        }
        List<String> dirs = null;
        if (hs.profiles() != null) {
            dirs = new ArrayList<>();
            for (var p : hs.profiles()) {
                var dir = server.browser.profiles().resolve(p);
                if (dir.isEmpty()) { deny("unknown profile " + p, "known profiles: " + known()); return null; }
                dirs.add(dir.get());
            }
        }
        return new Scope(hs.domains(), dirs, hs.minutes());
    }

    void attested(CdpHub hub, Scope requested, String label, String firstCdpFrame) {
        var tree = ProcessTree.snapshot();
        var chain = tree.decorate(raw);
        var grant = server.grants.findValid(peer.imagePath(), chain).filter(g -> g.scope().covers(requested)).orElse(null);
        if (grant == null) {
            var d = server.approval.decide(request(Kind.ATTESTED, chain, tree, requested, label, null, null));
            if (d.isEmpty()) { deny("not approved", "the request was declined or its window closed"); return; }
            grant = server.grants.create(peer.imagePath(), d.get().anchor(), d.get().durationMinutes(), d.get().capMinutes(), d.get().scope());
        }
        var effective = grant.scope().narrowedTo(requested);
        openProfiles(effective);
        var link = new ConsumerLink(peer, io);
        server.register(link, Kind.ATTESTED, grant.id(), label);
        hub.addConsumer(link, effective);
        if (v2) io.writeFrame(GateReplies.approved("attested", grant.expiresAt(), effective, server.browser.profiles()));
        pump(hub, link, firstCdpFrame);
    }

    void keyed(CdpHub hub, GateHandshake hs, Scope requested, String label) {
        var nonce = KeyedAppStore.newChallenge();
        io.writeFrame(GateReplies.challenge(nonce));
        if (!KeyedAppStore.verify(hs.publicKey(), nonce, signatureOf(io.readFrame()))) { deny("key proof failed", "the signature did not match the public key"); return; }
        var existing = server.keyed.byKey(hs.publicKey());
        var now = java.time.Instant.now();
        KeyedApp app;
        if (existing.isPresent() && !existing.get().expired(now) && existing.get().scope().covers(requested)) {
            app = server.keyed.seen(existing.get(), peer);
        } else {
            var reason = existing.isEmpty() ? "first request from this key" : existing.get().expired(now)
                ? "the earlier approval has expired" : "asks for more than was approved before";
            var tree = ProcessTree.snapshot();
            var d = server.approval.decide(request(Kind.KEYED, tree.decorate(raw), tree, requested, label,
                KeyedAppStore.fingerprint(hs.publicKey()), reason));
            if (d.isEmpty()) { deny("not approved", "the request was declined or its window closed"); return; }
            app = server.keyed.approve(label, hs.publicKey(), d.get().scope(), d.get().durationMinutes(), peer);
        }
        var effective = app.scope().narrowedTo(requested);
        openProfiles(effective);
        var link = new ConsumerLink(peer, io);
        server.register(link, Kind.KEYED, app.id(), label);
        hub.addConsumer(link, effective);
        io.writeFrame(GateReplies.approved("keyed", app.expiresAt(), effective, server.browser.profiles()));
        pump(hub, link, null);
    }

    static String signatureOf(String frame) {
        if (frame == null) return "";
        try { return GateHandshake.MAPPER.readTree(frame).path("gate").path("signature").asText(""); }
        catch (Exception e) { return ""; }
    }

    Approval.Request request(Kind kind, List<AncestryNode> chain, ProcessTree tree, Scope requested, String label,
                             String fingerprint, String reason) {
        return new Approval.Request(kind, peer, chain, tree, requested, label, fingerprint,
            server.browser.profiles().entries(), reason);
    }

    void openProfiles(Scope scope) {
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
            server.unregister(link);
            hub.removeConsumer(link);
            io.close();
        }
    }

    void deny(String error, String hint) {
        if (v2) try { io.writeFrame(GateReplies.denied(error, hint)); } catch (Exception ignored) {}
        io.close();
    }

    String known() {
        return String.join(", ", server.browser.profiles().profiles().stream().map(p -> p.name() + " (" + p.dir() + ")").toList());
    }

    static String imageName(String path) { var i = path.lastIndexOf('\\'); return (i < 0 ? path : path.substring(i + 1)).toLowerCase(); }
}

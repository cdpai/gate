package cdpai.gate;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import cdpai.gate.access.*;
import cdpai.gate.hub.CdpHub;
import cdpai.gate.hub.ConsumerLink;
import cdpai.gate.win.NamedPipeServer;
import cdpai.gate.win.VivaldiProcess;

/// Ties the three proven pieces together: the named pipe (identity, kernel-attested), the
/// Vivaldi link (the one browser connection, multiplexed by CdpHub), and the access decision
/// (grant re-attestation, falling through to a human approval for anything not already granted).
/// This is the server; GateMain just constructs one against a real or scratch browser and calls run().
///
/// Also enforces "expiry closes open connections; it does not merely stop new ones" (design doc):
/// a background sweep periodically checks every live consumer's grant against GrantStore and
/// cancels the pending I/O of any whose grant has expired or been revoked, which wakes that
/// consumer's own reader thread to run its normal disconnect cleanup.
public final class GateServer {

    static final long SWEEP_INTERVAL_MS = 10_000;

    final NamedPipeServer pipeServer;
    final CdpHub hub;
    final GrantStore grants = new GrantStore();
    final Approval approval;
    final ConcurrentHashMap<ConsumerLink, Grant> linkGrants = new ConcurrentHashMap<>();

    public GateServer(VivaldiProcess vivaldi, String pipeName, Approval approval) {
        this.hub = new CdpHub(vivaldi.cdp);
        this.pipeServer = new NamedPipeServer(pipeName);
        this.approval = approval;
    }

    public void run() {
        Thread.ofPlatform().name("cdpgate-browser-reader").start(hub::pumpBrowserMessages);
        Thread.ofPlatform().name("cdpgate-grant-sweep").daemon().start(this::sweepInvalidGrants);
        while (true) {
            NamedPipeServer.Accepted accepted;
            try { accepted = pipeServer.accept(); }
            catch (Exception e) { System.err.println("accept failed: " + e.getMessage()); continue; }
            var finalAccepted = accepted;
            Thread.ofPlatform().name("cdpgate-consumer-" + accepted.peer().pid())
                .start(() -> handleConnection(finalAccepted));
        }
    }

    public List<Grant> activeGrants() { return grants.active(); }

    public void revoke(Grant grant) {
        grants.revoke(grant);
        linkGrants.forEach((link, g) -> { if (g.equals(grant)) link.io.cancelPendingIo(); });
    }

    public void revokeAllNow() {
        grants.clear();
        linkGrants.keySet().forEach(link -> link.io.cancelPendingIo());
    }

    void sweepInvalidGrants() {
        while (true) {
            try { Thread.sleep(SWEEP_INTERVAL_MS); } catch (InterruptedException e) { return; }
            var active = new HashSet<>(grants.active());
            linkGrants.forEach((link, grant) -> { if (!active.contains(grant)) link.io.cancelPendingIo(); });
        }
    }

    void handleConnection(NamedPipeServer.Accepted accepted) {
        var peer = accepted.peer();
        var tree = ProcessTree.snapshot();
        var chain = tree.chainFrom(peer.pid());

        var grant = grants.findValid(peer.imagePath(), chain).orElse(null);
        if (grant == null) {
            var decision = approval.decide(peer, chain, tree);
            if (decision.isEmpty()) { accepted.io().close(); return; }
            grant = grants.create(peer.imagePath(), decision.get().anchor(), decision.get().durationMinutes());
        }

        var link = new ConsumerLink(peer, accepted.io());
        linkGrants.put(link, grant);
        hub.addConsumer(link);
        try {
            String msg;
            while ((msg = link.io.readFrame()) != null) hub.onConsumerMessage(link, msg);
        } catch (Exception ignored) {
            // consumer disconnected, or was cancelled by the grant sweep -- ordinary lifecycle
        } finally {
            linkGrants.remove(link);
            hub.removeConsumer(link);
            link.io.close();
        }
    }
}

package cdpai.gate;

import cdpai.gate.access.*;
import cdpai.gate.hub.CdpHub;
import cdpai.gate.hub.ConsumerLink;
import cdpai.gate.win.NamedPipeServer;
import cdpai.gate.win.VivaldiProcess;

/// Ties the three proven pieces together: the named pipe (identity, kernel-attested), the
/// Vivaldi link (the one browser connection, multiplexed by CdpHub), and the access decision
/// (grant re-attestation, falling through to a human approval for anything not already granted).
/// This is the server; GateMain just constructs one against a real or scratch browser and calls run().
public final class GateServer {

    final NamedPipeServer pipeServer;
    final CdpHub hub;
    final GrantStore grants = new GrantStore();
    final Approval approval;

    public GateServer(VivaldiProcess vivaldi, String pipeName, Approval approval) {
        this.hub = new CdpHub(vivaldi.cdp);
        this.pipeServer = new NamedPipeServer(pipeName);
        this.approval = approval;
    }

    public void run() {
        Thread.ofPlatform().name("cdpgate-browser-reader").start(hub::pumpBrowserMessages);
        while (true) {
            NamedPipeServer.Accepted accepted;
            try { accepted = pipeServer.accept(); }
            catch (Exception e) { System.err.println("accept failed: " + e.getMessage()); continue; }
            var finalAccepted = accepted;
            Thread.ofPlatform().name("cdpgate-consumer-" + accepted.peer().pid())
                .start(() -> handleConnection(finalAccepted));
        }
    }

    void handleConnection(NamedPipeServer.Accepted accepted) {
        var peer = accepted.peer();
        var tree = ProcessTree.snapshot();
        var chain = tree.chainFrom(peer.pid());

        if (grants.findValid(peer.imagePath(), chain).isEmpty()) {
            var decision = approval.decide(peer, chain, tree);
            if (decision.isEmpty()) { accepted.io().close(); return; }
            grants.create(peer.imagePath(), decision.get().anchor(), decision.get().durationMinutes());
        }

        var link = new ConsumerLink(peer, accepted.io());
        hub.addConsumer(link);
        try {
            String msg;
            while ((msg = link.io.readFrame()) != null) hub.onConsumerMessage(link, msg);
        } catch (Exception ignored) {
            // consumer disconnected mid-read -- ordinary lifecycle, nothing to report
        } finally {
            hub.removeConsumer(link);
            link.io.close();
        }
    }
}

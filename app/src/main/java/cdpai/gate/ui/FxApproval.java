package cdpai.gate.ui;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;

import cdpai.gate.access.Approval;
import cdpai.gate.access.PassphraseGate;
import cdpai.gate.win.PeerIdentity;

/// Bridges a connection thread's Approval.decide() to the approval window on the FX thread. The
/// connection waits for as long as the human takes: it is not usable until someone has looked. If the
/// requesting process exits while its window is open -- a timed-out command, a killed shell -- the
/// window withdraws itself, so the human is never asked to approve a request nobody is waiting on.
public final class FxApproval implements Approval {

    final PassphraseGate passphraseGate;

    public FxApproval(PassphraseGate passphraseGate) { this.passphraseGate = passphraseGate; }

    @Override public Optional<Decision> decide(Request request) {
        var future = new CompletableFuture<Optional<Decision>>();
        var window = new CompletableFuture<ApprovalWindow>();
        Platform.runLater(() -> {
            var w = new ApprovalWindow(request, passphraseGate, future::complete);
            window.complete(w);
            w.show();
        });
        try {
            while (true) {
                try { return future.get(1, TimeUnit.SECONDS); }
                catch (TimeoutException stillOpen) {
                    if (!alive(request.client())) {
                        window.thenAccept(w -> Platform.runLater(w::withdraw));
                        return Optional.empty();
                    }
                }
            }
        } catch (Exception e) { return Optional.empty(); }
    }

    /// Same pid AND same start time, so a reused pid does not keep a dead request's window open.
    static boolean alive(PeerIdentity p) {
        return ProcessHandle.of(p.pid()).filter(ProcessHandle::isAlive)
            .map(h -> p.startInstant().isEmpty() || h.info().startInstant().equals(p.startInstant())).orElse(false);
    }
}

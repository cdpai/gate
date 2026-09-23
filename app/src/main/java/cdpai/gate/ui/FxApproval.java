package cdpai.gate.ui;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;

import cdpai.gate.access.Approval;
import cdpai.gate.access.PassphraseGate;

/// Bridges a connection thread's Approval.decide() to the approval window on the FX thread. The
/// connection waits for as long as the human takes: it is not usable until someone has looked.
public final class FxApproval implements Approval {

    final PassphraseGate passphraseGate;

    public FxApproval(PassphraseGate passphraseGate) { this.passphraseGate = passphraseGate; }

    @Override public Optional<Decision> decide(Request request) {
        var future = new CompletableFuture<Optional<Decision>>();
        Platform.runLater(() -> new ApprovalWindow(request, passphraseGate, future::complete).show());
        try { return future.get(); } catch (Exception e) { return Optional.empty(); }
    }
}

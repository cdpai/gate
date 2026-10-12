package cdpai.gate.ui;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;

import cdpai.gate.access.PassphraseGate;
import cdpai.gate.access.TrustApproval;

/// Bridges `cdpg trust` to its window on the FX thread, the same way FxApproval does for ordinary
/// requests: waits as long as the human takes, and withdraws the window if the asking process exits.
public final class FxTrust implements TrustApproval {

    final PassphraseGate gate;

    public FxTrust(PassphraseGate gate) { this.gate = gate; }

    @Override public Optional<Decision> decide(Request request) {
        var future = new CompletableFuture<Optional<Decision>>();
        var window = new CompletableFuture<TrustWindow>();
        Platform.runLater(() -> {
            var w = new TrustWindow(request, gate, future::complete);
            window.complete(w);
            w.show();
        });
        try {
            while (true) {
                try { return future.get(1, TimeUnit.SECONDS); }
                catch (TimeoutException stillOpen) {
                    if (!FxApproval.alive(request.client())) {
                        window.thenAccept(w -> Platform.runLater(w::withdraw));
                        return Optional.empty();
                    }
                }
            }
        } catch (Exception e) { return Optional.empty(); }
    }
}

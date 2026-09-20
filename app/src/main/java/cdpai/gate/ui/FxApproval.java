package cdpai.gate.ui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;

import cdpai.gate.access.AncestryNode;
import cdpai.gate.access.Approval;
import cdpai.gate.access.ProcessTree;
import cdpai.gate.win.PeerIdentity;

/// Bridges Approval.decide() -- called on a per-connection background thread -- to the JavaFX
/// approval window, which must be built and shown on the FX Application Thread. The calling
/// thread blocks on the future exactly the way ConsoleApproval blocked on Scanner.nextLine(),
/// which is the correct behaviour here: the consumer's connection is not usable until a human
/// has actually looked at it.
public final class FxApproval implements Approval {

    @Override public Optional<Decision> decide(PeerIdentity client, List<AncestryNode> chain, ProcessTree tree) {
        var future = new CompletableFuture<Optional<Decision>>();
        Platform.runLater(() -> new ApprovalWindow(client, chain, tree, future::complete).show());
        try { return future.get(); }
        catch (Exception e) { return Optional.empty(); }
    }
}

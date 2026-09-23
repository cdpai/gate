package cdpai.gate.trial;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cdpai.gate.access.*;
import cdpai.gate.win.PeerIdentity;

import static cdpai.gate.trial.Checks.check;

/// While blanket mode is on the approval window is never consulted; off, it is consulted as usual.
/// java -cp <classes> cdpai.gate.trial.BlanketModeApprovalTrial
public class BlanketModeApprovalTrial {
    public static void main(String[] args) throws Exception {
        var gate = new PassphraseGate(Files.createTempDirectory("blanket").resolve("passphrase.json"));
        gate.setInitialPassphrase("Testpass1");
        var calls = new int[1];
        Approval window = r -> { calls[0]++; return Optional.empty(); };
        var approval = new BlanketModeApproval(window, gate);
        var self = new AncestryNode(4242, "C:\\some\\app.exe", Optional.of(Instant.now()), 0);
        var peer = new PeerIdentity(4242, "C:\\some\\app.exe", Optional.of(Instant.now()));
        var attested = new Approval.Request(Approval.Kind.ATTESTED, peer, List.of(self), ProcessTree.snapshot(), Scope.UNSCOPED, "app", null, List.of(), null);
        var keyed = new Approval.Request(Approval.Kind.KEYED, peer, List.of(self), ProcessTree.snapshot(), Scope.UNSCOPED, "app", "fp", List.of(), "first");

        check("off: the window decides", approval.decide(attested).isEmpty() && calls[0] == 1);
        gate.activateBlanketMode();
        var d = approval.decide(attested);
        check("on: the window is not consulted", calls[0] == 1 && d.isPresent());
        check("on: attested anchors at the client itself", d.get().anchor().pid() == 4242);
        check("on: within the one-hour cap", d.get().durationMinutes() <= PassphraseGate.BLANKET_MODE_MAX_MINUTES);
        check("on: keyed requests are approved too, with no anchor", approval.decide(keyed).orElseThrow().anchor() == null);
        gate.deactivateBlanketModeNow();
        approval.decide(attested);
        check("off again: the window decides", calls[0] == 2);
        System.exit(Checks.summary());
    }
}

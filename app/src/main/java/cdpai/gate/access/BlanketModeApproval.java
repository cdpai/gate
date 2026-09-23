package cdpai.gate.access;

import java.util.Optional;

/// Tier 2 of the passphrase gating: while blanket mode is on, every request is approved with no
/// window -- any app, any profile, any site -- for whatever is left of its hard one-hour cap, and
/// attested grants are anchored at the client itself so even this mode stays per-process. For
/// testing only. With blanket mode off, the real approval window decides.
public final class BlanketModeApproval implements Approval {

    final Approval delegate;
    final PassphraseGate passphraseGate;

    public BlanketModeApproval(Approval delegate, PassphraseGate passphraseGate) {
        this.delegate = delegate;
        this.passphraseGate = passphraseGate;
    }

    @Override public Optional<Decision> decide(Request r) {
        if (passphraseGate.isBlanketModeActive() && !r.chain().isEmpty()) {
            var minutes = (int) Math.max(1, passphraseGate.blanketModeRemaining().toMinutes());
            var anchor = r.kind() == Kind.ATTESTED ? r.chain().getFirst() : null;
            return Optional.of(new Decision(anchor, minutes, minutes, r.requested()));
        }
        return delegate.decide(r);
    }
}

package cdpai.gate.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import javafx.scene.control.Alert;

import cdpai.gate.GateServer;
import cdpai.gate.access.Grant;
import cdpai.gate.access.PassphraseGate;

/// One Extend, from the access list or the expiry warning. A trust grant asks for the passphrase
/// again, as it did when it was given. An extend that changes nothing -- the grant already ended --
/// says so, rather than looking as if it worked.
final class GrantExtend {

    static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    static final DateTimeFormatter DAY_HHMM = DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault());

    static Optional<Grant> run(GateServer server, PassphraseGate gate, Grant g) {
        if (g.anyClient() && !PassphrasePrompt.confirmOnce(gate, "cdpgate: extend trust",
            "Extend the trust of " + AccessListWindow.imageName(g.anchorImagePath()) + " (" + g.anchorPid()
                + ") and every program it starts. Type the passphrase.")) return Optional.empty();
        var r = server.extendGrant(g.id());
        if (r.isEmpty()) alreadyEnded();
        return r;
    }

    static void alreadyEnded() {
        var a = new Alert(Alert.AlertType.INFORMATION, "That approval had already ended, so there was nothing to extend."
            + " The next request will ask again.");
        a.setTitle("cdpgate");
        a.setHeaderText(null);
        a.show();
    }

    static String until(Grant g) { return "now ends at " + when(g.expiresAt()); }

    /// Time of day, with the weekday once it is more than twelve hours away, so "06:10" is never ambiguous.
    static String when(java.time.Instant t) {
        return (t.isAfter(java.time.Instant.now().plusSeconds(12 * 3600)) ? DAY_HHMM : HHMM).format(t);
    }

    private GrantExtend() {}
}

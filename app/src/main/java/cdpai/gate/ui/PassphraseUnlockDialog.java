package cdpai.gate.ui;

import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import cdpai.gate.access.PassphraseGate;

/// Tier 0 of the wallet-style gating: cdpgate needs a passphrase, set on first run, entered once
/// to unlock the tray app -- nothing else starts (no Vivaldi launch, no pipe server, no tray icon)
/// until this succeeds. Stays unlocked after that for the rest of the process's life; no idle
/// auto-lock, since none was asked for. Deliberately no escape hatch on the window's close button
/// -- there is no reset once a passphrase is set (cdpgate stores only a hash), so the only ways
/// out of this screen are the right passphrase or killing the process.
public final class PassphraseUnlockDialog {

    private PassphraseUnlockDialog() {}

    /// Blocks (via showAndWait) until the gate is unlocked. Must be called on the FX Application
    /// Thread, before anything else starts.
    public static void unlockOrSetup(PassphraseGate gate) {
        while (!gate.isUnlocked()) {
            if (gate.hasPassphrase()) promptUnlock(gate); else promptSetup(gate);
        }
    }

    static void promptUnlock(PassphraseGate gate) {
        var field = new PasswordField();
        var error = errorLabel();
        var unlock = new Button("Unlock");
        unlock.setDefaultButton(true);

        var msg = new Label("cdpgate is locked. Enter the passphrase to unlock.");
        msg.setWrapText(true);
        var box = new VBox(10, msg, field, error, unlock);
        box.setPadding(new Insets(16));
        box.setPrefWidth(360);

        var stage = new Stage(StageStyle.UTILITY);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("cdpgate — locked");
        unlock.setOnAction(e -> {
            if (gate.unlock(field.getText())) stage.close();
            else { error.setText("wrong passphrase"); field.clear(); }
        });
        stage.setScene(new Scene(box));
        stage.setOnCloseRequest(Event::consume);
        stage.showAndWait();
    }

    static void promptSetup(PassphraseGate gate) {
        var field = new PasswordField();
        var confirm = new PasswordField();
        var error = errorLabel();
        var set = new Button("Set passphrase");
        set.setDefaultButton(true);

        var msg = new Label("First run: set cdpgate's passphrase (letters and digits only)."
            + " There is no reset -- cdpgate stores only a hash, so write it down somewhere safe.");
        msg.setWrapText(true);
        var box = new VBox(10, msg, field, confirm, error, set);
        box.setPadding(new Insets(16));
        box.setPrefWidth(400);

        var stage = new Stage(StageStyle.UTILITY);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("cdpgate — set up");
        set.setOnAction(e -> {
            if (!field.getText().equals(confirm.getText())) { error.setText("passphrases don't match"); return; }
            if (!PassphraseGate.isValidPassphrase(field.getText())) { error.setText("letters and digits only, not blank"); return; }
            if (!gate.setInitialPassphrase(field.getText())) { error.setText("could not set passphrase"); return; }
            stage.close();
        });
        stage.setScene(new Scene(box));
        stage.setOnCloseRequest(Event::consume);
        stage.showAndWait();
    }

    static Label errorLabel() {
        var l = new Label(" ");
        l.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 11;");
        return l;
    }
}

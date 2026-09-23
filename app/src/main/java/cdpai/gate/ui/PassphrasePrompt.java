package cdpai.gate.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import cdpai.gate.access.PassphraseGate;

/// Small modal passphrase prompts shared by Tier 1 (ApprovalWindow's per-connection re-confirm,
/// one entry) and Tier 2 (TrayShell's blanket-mode toggle, two entries) of the wallet-style
/// gating. Must be called on the FX Application Thread -- each prompt blocks via showAndWait,
/// the same nested-event-loop pattern the rest of this UI already uses for confirmation dialogs.
final class PassphrasePrompt {

    private PassphrasePrompt() {}

    /// One passphrase field, checked against the gate. Cancel or a wrong entry both return false;
    /// a wrong entry re-shows the same dialog with an error rather than closing it, so a mistyped
    /// passphrase does not need a second click to try again.
    static boolean confirmOnce(PassphraseGate gate, String title, String message) {
        var field = new PasswordField();
        var error = errorLabel();
        var confirm = new Button("Confirm");
        confirm.setDefaultButton(true);
        var cancel = new Button("Cancel");
        var result = new boolean[] { false };

        var stage = new Stage(StageStyle.UTILITY);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);
        confirm.setOnAction(e -> {
            if (gate.verify(field.getText())) { result[0] = true; stage.close(); }
            else { error.setText("wrong passphrase"); field.clear(); }
        });
        cancel.setOnAction(e -> stage.close());

        var buttons = new HBox(8, confirm, cancel);
        var msg = new Label(message);
        msg.setWrapText(true);
        var box = new VBox(10, msg, field, error, buttons);
        box.setPadding(new Insets(16));
        box.setPrefWidth(380);
        stage.setScene(new Scene(box));
        stage.setAlwaysOnTop(true);
        stage.setOnShown(e -> stage.toFront());
        stage.showAndWait();
        return result[0];
    }

    /// Two SEPARATE passphrase prompts, both must verify -- Tier 2's deliberate extra friction
    /// ("asks the passphrase TWICE") for the one mode that trusts any app, any profile, all
    /// websites. Short-circuits on the first cancel/wrong entry rather than showing the second.
    static boolean confirmTwice(PassphraseGate gate, String title, String message) {
        if (!confirmOnce(gate, title, message + "\n\n(1 of 2)")) return false;
        return confirmOnce(gate, title, message + "\n\n(2 of 2 -- confirming again, deliberately)");
    }

    static Label errorLabel() {
        var l = new Label(" ");
        l.setStyle("-fx-text-fill: #b3261e; -fx-font-size: 11;");
        return l;
    }
}

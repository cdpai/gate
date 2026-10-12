package cdpai.gate.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import cdpai.gate.access.PassphraseGate;

/// How long blanket mode should run, asked before the two passphrase prompts. The longest choice is
/// the default (Enter), Esc cancels, and the digits 1-4 pick a choice directly: nothing here is typed.
final class BlanketDurationPrompt {

    private BlanketDurationPrompt() {}

    /// The chosen minutes, or 0 on cancel.
    static int choose() {
        return choose("Blanket mode: any app, any profile, any website, with no approval window."
            + " It ends by itself after the time chosen here.");
    }

    static int choose(String intro) {
        var choices = PassphraseGate.BLANKET_MODE_CHOICES;
        var result = new int[] { 0 };
        var stage = new Stage(StageStyle.UTILITY);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("cdpgate: blanket mode");

        var row = new HBox(8);
        for (var i = 0; i < choices.length; i++) {
            var minutes = choices[i];
            var b = new Button((i + 1) + "  " + DurationChips.label(minutes));
            b.setMnemonicParsing(false);
            b.setOnAction(e -> { result[0] = minutes; stage.close(); });
            if (minutes == PassphraseGate.BLANKET_MODE_MAX_MINUTES) b.setDefaultButton(true);
            row.getChildren().add(b);
        }
        var cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        row.getChildren().add(cancel);

        var msg = new Label(intro + " Enter takes "
            + DurationChips.label(PassphraseGate.BLANKET_MODE_MAX_MINUTES) + ", 1-4 pick one, Esc cancels.");
        msg.setWrapText(true);
        var box = new VBox(12, msg, row);
        box.setPadding(new Insets(16));
        box.setPrefWidth(420);
        var scene = new Scene(box);
        scene.setOnKeyPressed(e -> {
            var idx = e.getCode().isDigitKey() && !e.getText().isEmpty() ? e.getText().charAt(0) - '1' : -1;
            if (idx >= 0 && idx < choices.length) { result[0] = choices[idx]; stage.close(); }
        });
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.setOnShown(e -> stage.toFront());
        stage.showAndWait();
        return result[0];
    }
}

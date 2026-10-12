package cdpai.gate.ui;

import java.time.Duration;
import java.time.Instant;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import cdpai.gate.GateServer;
import cdpai.gate.access.PassphraseGate;

/// Five minutes before an attested grant with a live connection ends: extend it or let it lapse.
/// Extend adds the grant's own duration (at least half an hour) on top of what is left, never leaving
/// more than its cap, and the window shows the new end for a moment before closing so it is plain
/// that it worked. Closes itself at expiry. No text input, so bare letters are the shortcuts.
final class ExpiryWarningWindow {

    static void show(GateServer server, PassphraseGate gate, GateServer.Expiring e) {
        var stage = new Stage(StageStyle.UTILITY);
        var countdown = new Label();
        countdown.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");
        var done = new boolean[] { false };
        Runnable tick = () -> {
            if (done[0]) return;
            var s = Math.max(0, Duration.between(Instant.now(), e.expiresAt()).toSeconds());
            countdown.setText(e.label() + " loses access in " + "%d:%02d".formatted(s / 60, s % 60));
            if (s == 0) stage.close();
        };
        tick.run();
        var ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), x -> tick.run()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        stage.setOnHidden(x -> ticker.stop());
        Runnable extend = () -> {
            if (done[0]) return;
            var g = server.activeGrants().stream().filter(x -> x.id().equals(e.grantId())).findFirst();
            if (g.isEmpty()) GrantExtend.alreadyEnded();
            var r = g.flatMap(x -> GrantExtend.run(server, gate, x));
            if (r.isEmpty()) { stage.close(); return; }
            done[0] = true;
            countdown.setText(e.label() + ": extended, " + GrantExtend.until(r.get()));
            var close = new PauseTransition(javafx.util.Duration.seconds(2));
            close.setOnFinished(x -> stage.close());
            close.play();
        };
        var buttons = new HBox(8, ApprovalWindow.button("E  Extend", "#2e6da8", extend),
            ApprovalWindow.button("L  Let it expire", "#777", stage::close));
        var root = new VBox(10, countdown, ApprovalParts.small("Extending keeps the same anchor and scope, and adds time up to the cap this approval first earned.", "#555"), buttons);
        root.setPadding(new Insets(14));
        root.setPrefWidth(420);
        var scene = new Scene(root);
        scene.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.E) extend.run();
            else if (k.getCode() == KeyCode.L || k.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setTitle("cdpgate: approval ending");
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.show();
    }

    private ExpiryWarningWindow() {}
}

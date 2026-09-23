package cdpai.gate.ui;

import java.util.concurrent.CompletableFuture;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import cdpai.gate.browser.BrowserSupervisor.ExistingBrowserPrompt;
import cdpai.gate.browser.ExistingBrowser;

/// Shown when the browser is already running without cdpgate. cdpgate does not close it: quitting
/// from the browser's own menu is the one path measured to save every window, and closing it from
/// outside was not reliable. The dialog waits, and closes itself the moment the browser has exited;
/// closing the windows from outside stays available as an explicit, warned choice. No text input.
public final class ExistingBrowserDialog implements ExistingBrowserPrompt {

    @Override public Choice ask(ExistingBrowser b) {
        var result = new CompletableFuture<Choice>();
        Platform.runLater(() -> build(b, result));
        try { return result.get(); } catch (Exception e) { return Choice.CANCEL; }
    }

    void build(ExistingBrowser b, CompletableFuture<Choice> result) {
        var stage = new Stage();
        var watch = new Timeline(new KeyFrame(javafx.util.Duration.millis(500), x -> {
            if (!b.alive()) { result.complete(Choice.EXITED); stage.close(); }
        }));
        watch.setCycleCount(Animation.INDEFINITE);
        watch.play();
        stage.setOnHidden(x -> { watch.stop(); result.complete(Choice.CANCEL); });
        Runnable closeAll = () -> { result.complete(Choice.CLOSE_WINDOWS); stage.close(); };
        var root = new VBox(10,
            ApprovalParts.line("Vivaldi is running without cdpgate. Please quit it: Vivaldi menu, File, Exit.", "#333"),
            ApprovalParts.small("Answer yes if Vivaldi asks to close its tabs. cdpgate is waiting, and starts Vivaldi again"
                + " under its protection the moment it has exited, with every profile and tab that was open. Sessions are backed up"
                + " before it starts again. This window closes by itself.", "#333"),
            ApprovalParts.small("Vivaldi has " + b.windowCount() + " window(s) open. Letting cdpgate close them from outside"
                + " is less safe: with several windows only the last one is restored, and Vivaldi may keep running with no window.", "#8a4b00"),
            new HBox(8, ApprovalWindow.button("F2  Close the windows for me", "#8a4b00", closeAll),
                ApprovalWindow.button("Esc  Leave Vivaldi running, don't start cdpgate's", "#777", stage::close)));
        root.setPadding(new Insets(14));
        root.setPrefWidth(540);
        var scene = new Scene(root);
        scene.setOnKeyPressed(k -> {
            if (k.getCode() == KeyCode.F2) closeAll.run();
            else if (k.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setTitle("cdpgate: waiting for Vivaldi to exit");
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.show();
    }
}

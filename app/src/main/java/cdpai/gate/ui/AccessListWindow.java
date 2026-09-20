package cdpai.gate.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import cdpai.gate.GateServer;
import cdpai.gate.access.Grant;

/// "A live list of what can currently get through... available at a glance rather than by
/// inspection" (design doc). Ticks every second so the countdown is honest -- a row disappears
/// the moment GateServer's own sweep (or a revoke here) actually closes the connection, not just
/// when the number reaches zero on screen.
public final class AccessListWindow {

    final GateServer server;
    final Stage stage = new Stage();
    final VBox rows = new VBox(4);
    Timeline ticker;

    public AccessListWindow(GateServer server) { this.server = server; }

    public void show() {
        if (stage.isShowing()) { stage.toFront(); return; }

        var revokeAll = new Hyperlink("revoke everything now");
        revokeAll.setOnAction(e -> { server.revokeAllNow(); refresh(); });
        var header = new HBox(new Label("cdpgate — active approvals"), spacer(), revokeAll);
        header.setAlignment(Pos.CENTER_LEFT);

        var scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(240);

        var root = new VBox(10, header, scroll);
        root.setPadding(new Insets(12));
        root.setPrefWidth(560);

        ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> refresh()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        stage.setOnHidden(e -> ticker.stop());

        refresh();
        stage.setTitle("cdpgate — access");
        stage.setScene(new Scene(root));
        stage.show();
    }

    void refresh() {
        var active = server.activeGrants();
        rows.getChildren().setAll(active.isEmpty() ? List.of(noneLabel()) : active.stream().map(this::rowFor).toList());
    }

    static Node noneLabel() {
        var l = new Label("no active approvals");
        l.setStyle("-fx-text-fill: #777;");
        return l;
    }

    Node rowFor(Grant g) {
        var exe = cell(new Label(imageName(g.clientImagePath())), 170);
        var anchor = cell(new Label(imageName(g.anchorImagePath()) + " (" + g.anchorPid() + ")"), 170);
        var granted = cell(new Label(AncestryTableView.label(g.durationMinutes())), 55);
        var remaining = new Label(remaining(g));
        remaining.setStyle("-fx-font-family: Consolas, monospace; -fx-font-weight: bold;");
        var revoke = new Hyperlink("revoke");
        revoke.setOnAction(e -> { server.revoke(g); refresh(); });

        var row = new HBox(10, exe, anchor, granted, cell(remaining, 70), revoke);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    static String remaining(Grant g) {
        var seconds = Math.max(0, Duration.between(Instant.now(), g.expiresAt()).toSeconds());
        return "%02d:%02d:%02d".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60);
    }

    static String imageName(String path) {
        var i = path.lastIndexOf('\\');
        return (i < 0 ? path : path.substring(i + 1)).toLowerCase();
    }

    static Node cell(Node n, double width) {
        var box = new HBox(n);
        box.setPrefWidth(width);
        return box;
    }

    static Node spacer() {
        var r = new javafx.scene.layout.Region();
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }
}

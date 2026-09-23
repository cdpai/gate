package cdpai.gate.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import cdpai.gate.GateServer;
import cdpai.gate.access.*;
import cdpai.gate.browser.ProfileMap;

/// Who can get through right now: every attested grant and every keyed approval, with what it may
/// reach, how long it has left, and how many connections are using it. Ticks every second; a row
/// goes the moment its approval ends, and revoking cuts the live connections, not just new ones.
public final class AccessListWindow {

    final GateServer server;
    final ProfileMap profiles;
    final Stage stage = new Stage();
    final VBox rows = new VBox(6);
    Timeline ticker;

    public AccessListWindow(GateServer server, ProfileMap profiles) {
        this.server = server;
        this.profiles = profiles;
    }

    public void show() {
        if (stage.isShowing()) { stage.toFront(); return; }
        var revokeAll = new Hyperlink("revoke every attested approval now");
        revokeAll.setOnAction(e -> { server.revokeAllNow(); refresh(); });
        var header = new HBox(new Label("cdpgate: who has access"), spacer(), revokeAll);
        header.setAlignment(Pos.CENTER_LEFT);
        var scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(320);
        var root = new VBox(10, header, scroll, ApprovalParts.small("Esc closes · keyed approvals survive a restart, attested ones do not", "#777"));
        root.setPadding(new Insets(12));
        root.setPrefWidth(720);
        ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> refresh()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        stage.setOnHidden(e -> ticker.stop());
        refresh();
        var scene = new Scene(root);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) stage.hide(); });
        stage.setTitle("cdpgate: access");
        stage.setScene(scene);
        stage.show();
    }

    void refresh() {
        var list = new ArrayList<Node>();
        server.activeGrants().forEach(g -> list.add(grantRow(g)));
        var now = Instant.now();
        server.keyedApps().stream().filter(a -> !a.expired(now)).forEach(a -> list.add(keyedRow(a)));
        rows.getChildren().setAll(list.isEmpty() ? List.of(ApprovalParts.small("nothing has access right now", "#777")) : list);
    }

    Node grantRow(Grant g) {
        var revoke = new Hyperlink("revoke");
        revoke.setOnAction(e -> { server.revokeGrant(g.id()); refresh(); });
        var extend = new Hyperlink("extend");
        extend.setOnAction(e -> { server.extendGrant(g.id()); refresh(); });
        return row("attested", imageName(g.clientImagePath()), "anchored at " + imageName(g.anchorImagePath()) + " (" + g.anchorPid() + ")",
            g.scope(), g.expiresAt(), server.liveConnections(g.id()), false, extend, revoke);
    }

    Node keyedRow(KeyedApp a) {
        var revoke = new Hyperlink("revoke");
        revoke.setOnAction(e -> { server.revokeKeyed(a.id()); refresh(); });
        var dismiss = new Hyperlink("dismiss flag");
        dismiss.setOnAction(e -> { server.clearKeyedFlag(a.id()); refresh(); });
        var links = a.flagged() ? new Hyperlink[]{dismiss, revoke} : new Hyperlink[]{revoke};
        return row("keyed", a.app(), "key " + a.fingerprint() + (a.lastSeenImagePath() == null ? "" : ", last from " + imageName(a.lastSeenImagePath())),
            a.scope(), a.expiresAt(), server.liveConnections(a.id()), a.flagged(), links);
    }

    Node row(String kind, String who, String how, Scope scope, Instant expires, long live, boolean flagged, Hyperlink... actions) {
        var title = new Label(who + "  ·  " + kind + (flagged ? "  ·  EXECUTABLE CHANGED" : ""));
        title.setStyle("-fx-font-weight: bold;" + (flagged ? " -fx-text-fill: #b3261e;" : ""));
        var remaining = new Label(remaining(expires));
        remaining.setStyle("-fx-font-family: Consolas, monospace; -fx-font-weight: bold;");
        var top = new HBox(10, title, spacer(), remaining, new Label(live + " live"));
        top.getChildren().addAll(actions);
        top.setAlignment(Pos.CENTER_LEFT);
        var box = new VBox(2, top, ApprovalParts.small(how, "#555"), ApprovalParts.small(describe(scope), scope.unscoped() ? "#b3261e" : "#2e7d32"));
        box.setStyle("-fx-border-color: #ddd; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 6 0;");
        return box;
    }

    String describe(Scope s) {
        var p = s.profiles() == null ? "all profiles" : String.join(", ", s.profiles().stream().map(profiles::nameOf).toList());
        var d = s.domains() == null ? "all websites" : String.join(", ", s.domains());
        return p + "  ·  " + d;
    }

    static String remaining(Instant expires) {
        var s = Math.max(0, Duration.between(Instant.now(), expires).toSeconds());
        return s >= 86400 ? "%dd %02dh".formatted(s / 86400, (s % 86400) / 3600) : "%02d:%02d:%02d".formatted(s / 3600, (s % 3600) / 60, s % 60);
    }

    static String imageName(String path) { var i = path.lastIndexOf('\\'); return (i < 0 ? path : path.substring(i + 1)).toLowerCase(); }

    static Node spacer() { var r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
}

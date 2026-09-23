package cdpai.gate.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

import com.dustinredmond.fxtrayicon.FXTrayIcon;

import cdpai.gate.GateServer;
import cdpai.gate.access.PassphraseGate;
import cdpai.gate.browser.BrowserSupervisor;

/// The tray: current state, who has access, start the browser when it is not running, the
/// one-hour blanket mode for testing, revoke everything, and quit -- which closes the browser too,
/// because the browser cannot outlive the pipe cdpgate holds.
public final class TrayShell {

    final Stage statusStage;
    final BrowserSupervisor browser;
    final GateServer server;
    final PassphraseGate passphraseGate;
    final AccessListWindow accessList;
    FXTrayIcon icon;
    MenuItem stateItem, startItem, blanketItem;
    volatile String status = "starting";

    public TrayShell(Stage statusStage, BrowserSupervisor browser, GateServer server, PassphraseGate passphraseGate) {
        this.statusStage = statusStage;
        this.browser = browser;
        this.server = server;
        this.passphraseGate = passphraseGate;
        this.accessList = new AccessListWindow(server, browser.profiles());
    }

    public void install() {
        icon = new FXTrayIcon(statusStage, GateIcon.image());
        stateItem = new MenuItem();
        stateItem.setDisable(true);
        icon.addMenuItem(stateItem);
        icon.addMenuItem(new SeparatorMenuItem());
        icon.addMenuItem(item("Who has access…", accessList::show));
        startItem = item("Start Vivaldi under cdpgate", this::startBrowser);
        icon.addMenuItem(startItem);
        icon.addMenuItem(new SeparatorMenuItem());
        blanketItem = item("", this::onBlanketModeClicked);
        icon.addMenuItem(blanketItem);
        icon.addMenuItem(item("Revoke every attested approval now", () -> {
            server.revokeAllNow();
            icon.showInfoMessage("cdpgate", "Every attested approval revoked; keyed apps are listed under Who has access.");
        }));
        icon.addMenuItem(new SeparatorMenuItem());
        icon.addMenuItem(item("Quit cdpgate (Vivaldi closes too)", this::quit));
        icon.show();
        var ticker = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> refresh()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
        refresh();
    }

    static MenuItem item(String text, Runnable action) {
        var m = new MenuItem(text);
        m.setMnemonicParsing(false);
        m.setOnAction(e -> action.run());
        return m;
    }

    public void setStatus(String s) { status = s; }

    public void notify(String message) { Platform.runLater(() -> icon.showInfoMessage("cdpgate", message)); }

    void refresh() {
        stateItem.setText(status);
        startItem.setDisable(browser.state() != BrowserSupervisor.State.STOPPED);
        icon.setTrayIconTooltip("cdpgate: " + status);
        blanketItem.setText(passphraseGate.isBlanketModeActive()
            ? "Blanket mode ON, ends in " + mmss(passphraseGate.blanketModeRemaining()) + " (click to end now)"
            : "Blanket mode for testing (" + PassphraseGate.BLANKET_MODE_MAX_MINUTES + " min max)…");
    }

    void startBrowser() {
        Thread.ofPlatform().name("cdpgate-start").start(() -> browser.start(new ExistingBrowserDialog()));
    }

    void onBlanketModeClicked() {
        if (passphraseGate.isBlanketModeActive()) { passphraseGate.deactivateBlanketModeNow(); refresh(); return; }
        var ok = PassphrasePrompt.confirmTwice(passphraseGate, "cdpgate: blanket mode",
            "Any app, any profile, any website, with no approval window, for up to " + PassphraseGate.BLANKET_MODE_MAX_MINUTES
                + " minutes. For testing only. Type the passphrase.");
        if (ok) passphraseGate.activateBlanketMode();
        refresh();
    }

    static String mmss(java.time.Duration d) { var s = Math.max(0, d.toSeconds()); return "%02d:%02d".formatted(s / 60, s % 60); }

    void quit() {
        Thread.ofPlatform().start(() -> {
            browser.quitBrowser();
            Platform.exit();
            System.exit(0);
        });
    }
}

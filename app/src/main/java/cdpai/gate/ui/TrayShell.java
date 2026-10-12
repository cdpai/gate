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
/// blanket mode for testing (up to four hours), revoke everything, and quit -- which closes the browser too,
/// because the browser cannot outlive the pipe cdpgate holds.
public final class TrayShell {

    final Stage statusStage;
    final BrowserSupervisor browser;
    final GateServer server;
    final PassphraseGate passphraseGate;
    final AccessListWindow accessList;
    final TrayStatus trayStatus;
    FXTrayIcon icon;
    MenuItem stateItem, startItem, blanketItem, blanketExtendItem;
    String tooltip = "";

    public TrayShell(Stage statusStage, BrowserSupervisor browser, GateServer server, PassphraseGate passphraseGate) {
        this.statusStage = statusStage;
        this.browser = browser;
        this.server = server;
        this.passphraseGate = passphraseGate;
        this.accessList = new AccessListWindow(server, browser.profiles(), passphraseGate);
        this.trayStatus = new TrayStatus(browser, server);
    }

    public void install() {
        icon = new FXTrayIcon(statusStage, GateIcon.image());
        icon.noDefaultAction();
        icon.setOnAction(e -> accessList.show());
        // FXTrayIcon refuses a label already in the menu, on the AWT thread where nobody sees the
        // exception, so every item needs its real text before it is added; two empty ones drop the second.
        stateItem = new MenuItem(trayStatus.line());
        stateItem.setDisable(true);
        icon.addMenuItem(stateItem);
        icon.addMenuItem(new SeparatorMenuItem());
        icon.addMenuItem(item("Who has access…", accessList::show));
        startItem = item("Start Vivaldi under cdpgate", this::startBrowser);
        icon.addMenuItem(startItem);
        icon.addMenuItem(new SeparatorMenuItem());
        blanketItem = item(blanketLabel(), this::onBlanketModeClicked);
        icon.addMenuItem(blanketItem);
        blanketExtendItem = item("Extend blanket mode…", this::onBlanketExtendClicked);
        icon.addMenuItem(blanketExtendItem);
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

    public void notify(String message) { Platform.runLater(() -> icon.showInfoMessage("cdpgate", message)); }

    /// Ticks every second but touches the menu only when something it shows has changed: every change
    /// rebuilds the native popup, and rewriting it each second is what made it flicker.
    /// The blanket countdown is shown to the minute for the same reason.
    void refresh() {
        var line = trayStatus.line();
        var blanketOn = passphraseGate.isBlanketModeActive();
        set(stateItem, line);
        if (!line.equals(tooltip)) { tooltip = line; icon.setTrayIconTooltip("cdpgate: " + line); }
        var stopped = browser.state() == BrowserSupervisor.State.STOPPED;
        if (startItem.isDisable() == stopped) startItem.setDisable(!stopped);
        set(blanketItem, blanketLabel());
        if (blanketExtendItem.isDisable() == blanketOn) blanketExtendItem.setDisable(!blanketOn);
    }

    String blanketLabel() {
        return passphraseGate.isBlanketModeActive()
            ? "Blanket mode ON, ends in " + hm(passphraseGate.blanketModeRemaining()) + " (click to end now)"
            : "Blanket mode for testing (up to " + DurationChips.label(PassphraseGate.BLANKET_MODE_MAX_MINUTES) + ")…";
    }

    static void set(MenuItem m, String text) { if (!text.equals(m.getText())) m.setText(text); }

    static String hm(java.time.Duration d) {
        var m = Math.max(0, (d.toSeconds() + 59) / 60);
        return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + " min";
    }

    void startBrowser() {
        Thread.ofPlatform().name("cdpgate-start").start(() -> browser.start(new ExistingBrowserDialog()));
    }

    void onBlanketModeClicked() {
        if (passphraseGate.isBlanketModeActive()) { passphraseGate.deactivateBlanketModeNow(); refresh(); return; }
        var minutes = BlanketDurationPrompt.choose();
        if (minutes == 0) return;
        var ok = PassphrasePrompt.confirmTwice(passphraseGate, "cdpgate: blanket mode",
            "Any app, any profile, any website, with no approval window, for " + DurationChips.label(minutes)
                + ". It then ends by itself. For testing only. Type the passphrase.");
        if (ok) passphraseGate.activateBlanketMode(minutes);
        refresh();
    }

    /// Adds time while blanket mode is still on, so nobody has to wait for it to lapse and switch it on
    /// again. One passphrase entry: the two-entry friction was paid when it was switched on.
    void onBlanketExtendClicked() {
        if (!passphraseGate.isBlanketModeActive()) return;
        var minutes = BlanketDurationPrompt.choose("Add time to blanket mode, which now ends in "
            + hm(passphraseGate.blanketModeRemaining()) + ". It never runs more than "
            + DurationChips.label(PassphraseGate.BLANKET_MODE_MAX_MINUTES) + " from now.");
        if (minutes == 0) return;
        if (!PassphrasePrompt.confirmOnce(passphraseGate, "cdpgate: extend blanket mode",
            "Add " + DurationChips.label(minutes) + " to blanket mode. Type the passphrase.")) return;
        server.extendBlanketGrants(passphraseGate.extendBlanketMode(minutes));
        refresh();
        icon.showInfoMessage("cdpgate", "Blanket mode now ends in " + hm(passphraseGate.blanketModeRemaining()));
    }


    void quit() {
        Thread.ofPlatform().start(() -> {
            browser.quitBrowser();
            Platform.exit();
            System.exit(0);
        });
    }
}

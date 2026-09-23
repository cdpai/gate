package cdpai.gate.ui;

import java.nio.file.Path;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import cdpai.gate.GateServer;
import cdpai.gate.access.*;
import cdpai.gate.browser.BrowserSupervisor;
import cdpai.gate.browser.GateSettings;
import cdpai.gate.client.GateClient;

/// cdpgate. Unlock first -- nothing starts until the passphrase is given. Then the pipe server,
/// the tray, and the browser: if the browser is already running on these profiles without
/// cdpgate it is closed (sessions backed up first) and started again under the pipe.
///
/// Options, for trials only: `--user-data-dir <dir>` runs against a scratch profile folder,
/// `--pipe <name>` uses another pipe, `--passphrase-store <file>` keeps a trial from setting the
/// real passphrase, `--keyed-store <file>` keeps trial keys out of the real list.
public final class GateApp extends Application {

    GateSettings settings;
    PassphraseGate passphraseGate;
    KeyedAppStore keyedStore;

    public static void main(String[] args) { launch(args); }

    @Override public void init() {
        Platform.setImplicitExit(false);
        var a = getParameters().getRaw();
        settings = GateSettings.load();
        passphraseGate = new PassphraseGate();
        keyedStore = new KeyedAppStore();
        for (var i = 0; i + 1 < a.size(); i += 2) {
            var v = a.get(i + 1);
            switch (a.get(i)) {
                case "--user-data-dir" -> settings = settings.withUserDataDir(v);
                case "--pipe" -> settings = settings.withPipe(v);
                case "--passphrase-store" -> passphraseGate = new PassphraseGate(Path.of(v));
                case "--keyed-store" -> keyedStore = new KeyedAppStore(Path.of(v));
                default -> throw new IllegalArgumentException("unknown option " + a.get(i));
            }
        }
    }

    @Override public void start(Stage primaryStage) {
        if (alreadyRunning(settings.pipe())) {
            System.err.println("cdpgate is already running on pipe " + settings.pipe());
            Platform.exit();
            return;
        }
        primaryStage.setTitle("cdpgate");
        PassphraseUnlockDialog.unlockOrSetup(passphraseGate);

        var browser = new BrowserSupervisor(settings);
        var server = new GateServer(browser, settings.pipe(), new BlanketModeApproval(new FxApproval(passphraseGate), passphraseGate), keyedStore);
        var tray = new TrayShell(primaryStage, browser, server, passphraseGate);
        tray.install();
        browser.onStatus(s -> { System.err.println("cdpgate: " + s); tray.setStatus(s); });
        browser.onStopped(() -> tray.notify("Vivaldi has exited. cdpgate keeps guarding; start it again from the tray."));
        server.onExpiring(e -> Platform.runLater(() -> ExpiryWarningWindow.show(server, e)));
        Thread.ofPlatform().name("cdpgate-server").start(server::run);
        tray.startBrowser();
    }

    static boolean alreadyRunning(String pipe) {
        try { GateClient.profiles(pipe); return true; } catch (Exception e) { return false; }
    }
}

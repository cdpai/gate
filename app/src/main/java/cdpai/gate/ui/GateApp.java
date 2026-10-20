package cdpai.gate.ui;

import java.nio.file.Path;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import cdpai.gate.GateServer;
import cdpai.gate.access.CdpRules;
import cdpai.gate.access.CdpScope;
import cdpai.gate.browser.BrowserSupervisor;
import cdpai.gate.browser.GateSettings;
import cdpai.gate.client.GateClient;
import leasegate.core.LeaseEngine;
import leasegate.core.access.*;
import leasegate.ui.*;

/// cdpgate. Unlock first -- nothing starts until the passphrase is given. Then the pipe server,
/// the tray, and the browser: if the browser is already running on these profiles without
/// cdpgate it is closed (sessions backed up first) and started again under the pipe.
///
/// Options, for trials only: `--user-data-dir <dir>` runs against a scratch profile folder,
/// `--pipe <name>` uses another pipe, `--passphrase-store <file>` keeps a trial from setting the
/// real passphrase, `--keyed-store <file>` keeps trial keys out of the real list.
public final class GateApp extends Application {

    static final Path HOME = Path.of(System.getProperty("user.home"), "cdpai", "gate");

    GateSettings settings;
    PassphraseGate passphraseGate;
    Path keyedStorePath = HOME.resolve("keyed-apps.json");

    public static void main(String[] args) {
        // macOS: a menu-bar app, not a Dock icon. Must be set before AWT (the tray) initialises.
        System.setProperty("apple.awt.UIElement", "true");
        launch(args);
    }

    @Override public void init() {
        Platform.setImplicitExit(false);
        var a = getParameters().getRaw();
        settings = GateSettings.load();
        passphraseGate = new PassphraseGate(HOME.resolve("passphrase.json"));
        for (var i = 0; i + 1 < a.size(); i += 2) {
            var v = a.get(i + 1);
            switch (a.get(i)) {
                case "--user-data-dir" -> settings = settings.withUserDataDir(v);
                case "--pipe" -> settings = settings.withPipe(v);
                case "--passphrase-store" -> passphraseGate = new PassphraseGate(Path.of(v));
                case "--keyed-store" -> keyedStorePath = Path.of(v);
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
        PassphraseUnlockDialog.unlockOrSetup(passphraseGate, "cdpgate");

        var browser = new BrowserSupervisor(settings);
        var rules = new CdpRules(browser.profiles());
        var engine = new LeaseEngine<CdpScope>(rules, new BlanketModeApproval<>(new FxApproval<>(passphraseGate, rules), passphraseGate),
            new KeyedAppStore<>(keyedStorePath, rules));
        engine.trustApproval(new FxTrust<>(passphraseGate, rules));
        engine.onExpiring(e -> Platform.runLater(() -> ExpiryWarningWindow.show(engine, passphraseGate, e)));
        var server = new GateServer(browser, settings.pipe(), engine);
        var status = new TrayStatus(browser, engine);
        var tray = new TrayShell<>(primaryStage, engine, passphraseGate, GateIcon.image(), status::line);
        var startItem = TrayShell.item("Start Vivaldi under cdpgate", () -> startBrowser(browser));
        tray.addItem(startItem)
            .onRefresh(() -> { var stopped = browser.state() == BrowserSupervisor.State.STOPPED; if (startItem.isDisable() == stopped) startItem.setDisable(!stopped); })
            .quit("Quit cdpgate (Vivaldi closes too)", browser::quitBrowser)
            .install();
        browser.onStatus(s -> System.err.println("cdpgate: " + s));
        browser.onStopped(tray::notify);
        Thread.ofPlatform().name("cdpgate-server").start(server::run);
        startBrowser(browser);
    }

    static void startBrowser(BrowserSupervisor browser) {
        Thread.ofPlatform().name("cdpgate-start").start(() -> browser.start(new ExistingBrowserDialog()));
    }

    static boolean alreadyRunning(String pipe) {
        try { GateClient.profiles(pipe); return true; } catch (Exception e) { return false; }
    }
}

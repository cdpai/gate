package cdpai.gate.ui;

import java.nio.file.Files;
import java.util.List;

import javafx.application.Platform;
import javafx.stage.Stage;

import cdpai.gate.access.*;
import cdpai.gate.browser.*;
import leasegate.core.LeaseEngine;
import leasegate.core.access.*;
import leasegate.frame.*;
import leasegate.ui.*;

/// A second cdpgate tray icon with nothing behind it, for trying the tray popup and the home window
/// by hand beside the real gates: no pipe, no browser, a throwaway passphrase file. Quit from its menu.
/// java --enable-native-access=ALL-UNNAMED -cp <cp> cdpai.gate.ui.TrayLiveTrial
public class TrayLiveTrial {

    public static void main(String[] a) throws Exception {
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        var me = ProcessHandle.current().pid();
        var chain = ProcessTree.snapshot().decorate(ProcessTree.rawChain(me));
        var peer = leasegate.core.win.PeerIdentity.resolve(me);
        var gate = new PassphraseGate(Files.createTempDirectory("tray").resolve("p.json"));
        var profiles = new ProfileMap();
        profiles.setProfiles(List.of(new BrowserProfile("Default", "Work")));
        var rules = new CdpRules(profiles);
        var engine = new LeaseEngine<CdpScope>(rules, r -> java.util.Optional.empty(), new KeyedAppStore<>(Files.createTempDirectory("tray").resolve("k.json"), rules));
        engine.grant(peer.imagePath(), chain.get(Math.min(3, chain.size() - 1)), 120, new CdpScope(List.of("youtube.com"), List.of("Default"), 120));
        var brand = new Brand("cdpgate", "#E5383B", new GateIcons(GateApp.class, "cdpgate"));
        Platform.runLater(() -> new TrayShell<>(new Stage(), engine, gate, brand, () -> "TRIAL · " + engine.activeGrants().size() + " approval(s)")
            .addItem(TrayShell.item("Start Vivaldi under cdpgate", () -> System.out.println("start clicked")))
            .describe("guards the browser: its DevTools link (TRIAL copy)", "https://github.com/cdpai/gate")
            .quit("Quit this trial", () -> {})
            .install());
    }
}

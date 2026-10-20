package cdpai.gate.ui;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

import cdpai.gate.access.*;
import cdpai.gate.browser.*;
import leasegate.core.LeaseEngine;
import leasegate.core.access.*;
import leasegate.core.win.PeerIdentity;
import leasegate.frame.*;
import leasegate.ui.*;

/// Renders every cdpgate window in-process with realistic data and writes each as a PNG, so the
/// layout can be looked at without driving the real app from outside. Nothing is approved and no
/// browser is involved; the requests are built from this trial's own process ancestry.
/// java --enable-native-access=ALL-UNNAMED -cp <cp> cdpai.gate.ui.UiSnapshotTrial <out-dir>
public class UiSnapshotTrial {

    public static void main(String[] a) throws Exception {
        var out = Files.createDirectories(Path.of(a[0]));
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        var me = ProcessHandle.current().pid();
        var raw = ProcessTree.rawChain(me);
        var tree = ProcessTree.snapshot();
        var chain = tree.decorate(raw);
        var peer = PeerIdentity.resolve(me);
        var gate = new PassphraseGate(Files.createTempDirectory("ui").resolve("p.json"));
        var profiles = new ProfileMap();
        profiles.setProfiles(List.of(new BrowserProfile("Default", "Work"), new BrowserProfile("Profile 3", "Personal"), new BrowserProfile("Profile 4", "Research")));
        profiles.bind("CTX1", "Default", false);
        profiles.bind("CTX2", "Profile 4", true);
        var rules = new CdpRules(profiles);
        var brand = new Brand("cdpgate", "#E5383B", new GateIcons(GateApp.class, "cdpgate"));

        var scoped = new CdpScope(List.of("youtube.com", "studio.youtube.com"), List.of("Default", "Profile 3"), 120);
        shoot(out, "approval-attested-scoped", () -> WindowPreview.approval(new Approval.Request<>(Approval.Kind.ATTESTED, peer, chain, tree,
            scoped, "cdpg", null, null), gate, rules, brand));
        shoot(out, "approval-attested-unscoped", () -> WindowPreview.approval(new Approval.Request<>(Approval.Kind.ATTESTED, peer, chain, tree,
            CdpScope.UNSCOPED, "cdpg", null, null), gate, rules, brand));
        shoot(out, "approval-keyed", () -> WindowPreview.approval(new Approval.Request<>(Approval.Kind.KEYED, peer, chain, tree,
            new CdpScope(List.of("youtube.com"), List.of("Default"), 30 * 1440), "yt_live_chats", "3f9a1c0d22b7e418",
            "first request from this key"), gate, rules, brand));

        var target = chain.get(Math.min(3, chain.size() - 1));
        shoot(out, "trust-green", () -> WindowPreview.trust(new TrustApproval.Request<>(peer, target, new CdpScope(List.of("facebook.com"), List.of("Default"), 480),
            "cdpg trust"), gate, rules, brand));
        shoot(out, "trust-amber", () -> WindowPreview.trust(new TrustApproval.Request<>(peer, target, new CdpScope(null, List.of("Default"), 1440),
            "cdpg trust"), gate, rules, brand));
        shoot(out, "trust-red", () -> WindowPreview.trust(new TrustApproval.Request<>(peer, target, new CdpScope(null, null, 2880),
            "cdpg trust"), gate, rules, brand));

        var engine = new LeaseEngine<CdpScope>(rules, r -> java.util.Optional.empty(), new KeyedAppStore<>(Files.createTempDirectory("ui").resolve("k.json"), rules));
        engine.grant(peer.imagePath(), chain.get(Math.min(3, chain.size() - 1)), 120, scoped);
        shoot(out, "access-list", () -> new AccessListWindow<>(engine, gate, brand).show());
        shoot(out, "expiry-warning", () -> ExpiryWarningWindow.show(engine, gate, brand, new LeaseEngine.Expiring("g1", "netrec", Instant.now().plusSeconds(290))));
        var tray = new TrayShell[1];
        Platform.runLater(() -> tray[0] = new TrayShell<>(new Stage(), engine, gate, brand, () -> "browser running · 3 profiles · 1 approval")
            .addItem(TrayShell.item("Start Vivaldi under cdpgate", () -> {}))
            .describe("guards the browser: its DevTools link", "https://github.com/cdpai/gate"));
        Thread.sleep(300);
        shoot(out, "home", () -> tray[0].home().show());
        shoot(out, "about", () -> AboutWindow.show(brand, "guards the browser: its DevTools link", "https://github.com/cdpai/gate"));
        shoot(out, "tray-popup", () -> new leasegate.tray.TrayMenu(brand.icon(), "cdpgate", "#E5383B", () -> "browser running · 3 profiles · 1 approval", () -> List.of(
            TrayShell.item("Open the cdpgate window", () -> {}), TrayShell.item("Who has access…", () -> {}),
            new javafx.scene.control.SeparatorMenuItem(), TrayShell.item("Start Vivaldi under cdpgate", () -> {}),
            new javafx.scene.control.SeparatorMenuItem(), TrayShell.item("Blanket mode for testing (up to 4 h)…", () -> {}),
            TrayShell.item("Revoke every attested approval now", () -> {}), new javafx.scene.control.SeparatorMenuItem(),
            TrayShell.item("Check for updates…", () -> {}), TrayShell.item("About cdpgate", () -> {}),
            new javafx.scene.control.SeparatorMenuItem(), TrayShell.item("Quit cdpgate (Vivaldi closes too)", () -> {}))).show(1500, 900));
        shoot(out, "existing-browser", () -> new ExistingBrowserDialog(brand).build(new ExistingBrowser(ProcessHandle.current().pid(), "vivaldi.exe", 1), new java.util.concurrent.CompletableFuture<>()));
        System.exit(0);
    }

    static void shoot(Path out, String name, Runnable open) throws Exception {
        var done = new CountDownLatch(1);
        Platform.runLater(open);
        Thread.sleep(2600);
        Platform.runLater(() -> {
            var stages = Window.getWindows().stream().filter(Window::isShowing).toList();
            var w = (Stage) stages.getLast();
            var img = w.getScene().snapshot(null);
            var bi = new BufferedImage((int) img.getWidth(), (int) img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var r = img.getPixelReader();
            for (var y = 0; y < bi.getHeight(); y++) for (var x = 0; x < bi.getWidth(); x++) bi.setRGB(x, y, r.getArgb(x, y));
            try { javax.imageio.ImageIO.write(bi, "png", out.resolve(name + ".png").toFile()); } catch (Exception e) { e.printStackTrace(); }
            System.out.println(name + ": " + bi.getWidth() + "x" + bi.getHeight() + " title=" + w.getTitle());
            stages.forEach(s -> ((Stage) s).close());
            done.countDown();
        });
        done.await();
    }
}

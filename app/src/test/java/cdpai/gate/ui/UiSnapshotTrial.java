package cdpai.gate.ui;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

import cdpai.gate.GateServer;
import cdpai.gate.TestGrants;
import cdpai.gate.access.*;
import cdpai.gate.browser.*;
import cdpai.gate.win.PeerIdentity;

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
        var entries = List.of(new ProfileMap.Entry("Default", "Work", "CTX1", false), new ProfileMap.Entry("Profile 3", "Personal", null, false),
            new ProfileMap.Entry("Profile 4", "Research", "CTX2", true));

        var scoped = new Scope(List.of("youtube.com", "studio.youtube.com"), List.of("Default", "Profile 3"), 120);
        shoot(out, "approval-attested-scoped", () -> new ApprovalWindow(new Approval.Request(Approval.Kind.ATTESTED, peer, chain, tree,
            scoped, "cdpg", null, entries, null), gate, d -> {}).show());
        shoot(out, "approval-attested-unscoped", () -> new ApprovalWindow(new Approval.Request(Approval.Kind.ATTESTED, peer, chain, tree,
            Scope.UNSCOPED, "cdpg", null, entries, null), gate, d -> {}).show());
        shoot(out, "approval-keyed", () -> new ApprovalWindow(new Approval.Request(Approval.Kind.KEYED, peer, chain, tree,
            new Scope(List.of("youtube.com"), List.of("Default"), 30 * 1440), "yt_live_chats", "3f9a1c0d22b7e418", entries,
            "first request from this key"), gate, d -> {}).show());

        var settings = new GateSettings("C:\\user\\Apps\\Vivaldi\\Application\\vivaldi.exe", null, "none");
        var server = new GateServer(new BrowserSupervisor(settings), "cdpai-gate-ui-trial-unused", r -> java.util.Optional.empty(),
            new KeyedAppStore(Files.createTempDirectory("ui").resolve("k.json")));
        TestGrants.add(server, peer.imagePath(), chain.get(Math.min(3, chain.size() - 1)), 120, scoped);
        shoot(out, "access-list", () -> new AccessListWindow(server, new ProfileMap()).show());
        shoot(out, "expiry-warning", () -> ExpiryWarningWindow.show(server, new GateServer.Expiring("g1", "netrec", Instant.now().plusSeconds(290))));
        shoot(out, "existing-browser", () -> new ExistingBrowserDialog().build(new ExistingBrowser(ProcessHandle.current().pid(), "vivaldi.exe", 1), new java.util.concurrent.CompletableFuture<>()));
        System.exit(0);
    }

    static void shoot(Path out, String name, Runnable open) throws Exception {
        var done = new CountDownLatch(1);
        Platform.runLater(open);
        Thread.sleep(1200);
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

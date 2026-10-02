package cdpai.gate.trial;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import cdpai.gate.client.posix.SocketClient;
import cdpai.gate.client.GatePlatform;
import cdpai.gate.posix.PosixLauncher;
import cdpai.gate.posix.UnixSocketServer;

/// The two native pieces of the macOS port, without the UI: (1) the socket server names its peer
/// by the kernel's LOCAL_PEERPID (here the peer is this same process, so the pid must be ours) and
/// frames survive the round trip; (2) the browser starts on a SCRATCH profile with its pipe on
/// fd3/fd4, answers Browser.getVersion over it, and exits on Browser.close, reaped.
///
///   java -cp cdpgate.jar:test-classes cdpai.gate.trial.MacPosixTrial [vivaldi-exe]
public final class MacPosixTrial {

    public static void main(String[] args) throws Exception {
        var exe = args.length > 0 ? args[0] : "/Applications/Vivaldi.app/Contents/MacOS/Vivaldi";

        System.out.println("== socket");
        var server = new UnixSocketServer(GatePlatform.socketPath("cdpai-gate-trial"));
        var accepted = new java.util.concurrent.CompletableFuture<cdpai.gate.ConsumerServer.Accepted>();
        Thread.ofPlatform().start(() -> accepted.complete(server.accept()));
        try (var client = SocketClient.open("cdpai-gate-trial")) {
            var a = accepted.get();
            System.out.println("peer pid " + a.peer().pid() + " (mine " + ProcessHandle.current().pid() + ") image " + a.peer().imagePath());
            check(a.peer().pid() == ProcessHandle.current().pid(), "kernel-named peer pid is this process");
            client.writeFrame("{\"hello\":1}");
            check("{\"hello\":1}".equals(a.io().readFrame()), "client -> server frame");
            a.io().writeFrame("{\"back\":2}");
            check("{\"back\":2}".equals(client.readFrame()), "server -> client frame");
            var reader = Thread.ofPlatform().start(() -> System.out.println("blocked read returned " + a.io().readFrame()));
            Thread.sleep(300);
            a.io().cancelPendingIo();
            reader.join(3000);
            check(!reader.isAlive(), "cancelPendingIo wakes a blocked read");
            a.io().close();
        }
        Files.deleteIfExists(GatePlatform.socketPath("cdpai-gate-trial"));

        System.out.println("== browser on a scratch profile");
        var udd = Files.createTempDirectory("cdpgate-trial-profile");
        var b = PosixLauncher.launch(exe, udd.toString(), List.of("about:blank"));
        System.out.println("pid " + b.pid() + " alive " + b.isAlive());
        b.cdp().writeFrame("{\"id\":1,\"method\":\"Browser.getVersion\"}");
        String reply;
        while ((reply = b.cdp().readFrame()) != null && !reply.contains("\"id\":1")) {}
        System.out.println("reply " + reply);
        check(reply != null && reply.contains("product"), "Browser.getVersion over the pipe");
        // A page must actually render: that needs the GPU and renderer helpers, which is where an
        // inherited Rosetta preference broke things while the browser process itself was fine.
        b.cdp().writeFrame("{\"id\":3,\"method\":\"Target.createTarget\",\"params\":{\"url\":\"data:text/html,<title>cdpgate-ok</title>\"}}");
        while ((reply = b.cdp().readFrame()) != null && !reply.contains("\"id\":3")) {}
        var targetId = reply.replaceAll(".*\"targetId\":\"([^\"]+)\".*", "$1");
        b.cdp().writeFrame("{\"id\":4,\"method\":\"Target.attachToTarget\",\"params\":{\"targetId\":\"" + targetId + "\",\"flatten\":true}}");
        while ((reply = b.cdp().readFrame()) != null && !reply.contains("\"id\":4")) {}
        var sessionId = reply.replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");
        var deadline = System.currentTimeMillis() + 20_000;
        String title = null;
        while (System.currentTimeMillis() < deadline && !"cdpgate-ok".equals(title)) {
            b.cdp().writeFrame("{\"id\":5,\"sessionId\":\"" + sessionId + "\",\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"document.title\"}}");
            while ((reply = b.cdp().readFrame()) != null && !reply.contains("\"id\":5")) {}
            title = reply == null ? null : reply.replaceAll(".*\"value\":\"([^\"]*)\".*", "$1");
            if (!"cdpgate-ok".equals(title)) Thread.sleep(500);
        }
        check("cdpgate-ok".equals(title), "a page rendered and answered Runtime.evaluate in its renderer");
        var helpers = new ProcessBuilder("sh", "-c", "ps -ax -o ppid=,flags= | awk '$1==" + b.pid() + " {print $2}'").start();
        var flags = new String(helpers.getInputStream().readAllBytes()).trim().split("\\s+");
        var translated = java.util.Arrays.stream(flags).filter(f -> !f.isBlank() && (Long.parseLong(f, 16) & 0x20000) != 0).count();
        System.out.println("helpers " + flags.length + ", translated " + translated);
        check(translated == 0, "no helper process runs under Rosetta");
        b.cdp().writeFrame("{\"id\":2,\"method\":\"Browser.close\"}");
        for (var i = 0; i < 60 && b.isAlive(); i++) Thread.sleep(250);
        check(!b.isAlive(), "browser exited and was reaped");
        b.close();
        System.out.println("ALL OK");
        System.exit(0);
    }

    static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) System.exit(1);
    }
}

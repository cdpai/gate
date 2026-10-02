package cdpai.gate.trial;

import java.nio.file.Files;
import java.util.List;

import cdpai.gate.posix.PosixLauncher;

/// Diagnostic: launch on a scratch profile, create a page, attach (flatten), evaluate, and print
/// EVERY frame for 20 s, so a missing reply can be told apart from one that went unrecognised.
public final class MacFrameLogTrial {

    public static void main(String[] args) throws Exception {
        var exe = args.length > 0 ? args[0] : "/Applications/Vivaldi.app/Contents/MacOS/Vivaldi";
        var udd = Files.createTempDirectory("cdpgate-trial-profile");
        var b = PosixLauncher.launch(exe, udd.toString(), List.of("about:blank"));
        var t0 = System.currentTimeMillis();
        Thread.ofPlatform().daemon().start(() -> {
            String f;
            while ((f = b.cdp().readFrame()) != null) {
                System.out.println("+" + (System.currentTimeMillis() - t0) + "ms  " + (f.length() > 260 ? f.substring(0, 260) + "..." : f));
                if (f.startsWith("{\"id\":12,"))
                    b.cdp().writeFrame("{\"id\":14,\"method\":\"Target.attachToTarget\",\"params\":{\"targetId\":\""
                        + f.replaceAll(".*\"targetId\":\"([^\"]+)\".*", "$1") + "\",\"flatten\":true}}");
                if (f.startsWith("{\"id\":14,")) {
                    var sid = f.replaceAll(".*\"sessionId\":\"([^\"]+)\".*", "$1");
                    b.cdp().writeFrame("{\"id\":15,\"sessionId\":\"" + sid + "\",\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":\"location.href+' '+document.readyState\"}}");
                    b.cdp().writeFrame("{\"id\":16,\"sessionId\":\"" + sid + "\",\"method\":\"Runtime.runIfWaitingForDebugger\"}");
                }
            }
        });
        Thread.sleep(3000);
        b.cdp().writeFrame("{\"id\":10,\"method\":\"Target.getTargets\"}");
        Thread.sleep(1000);
        b.cdp().writeFrame("{\"id\":11,\"method\":\"Target.setDiscoverTargets\",\"params\":{\"discover\":true}}");
        b.cdp().writeFrame("{\"id\":12,\"method\":\"Target.createTarget\",\"params\":{\"url\":\"https://example.com\"}}");
        Thread.sleep(4000);
        System.out.println("--- send target id to attach on stdin is not possible here; attaching to every page seen");
        b.cdp().writeFrame("{\"id\":13,\"method\":\"Target.getTargets\"}");
        Thread.sleep(16000);
        b.cdp().writeFrame("{\"id\":99,\"method\":\"Browser.close\"}");
        Thread.sleep(2000);
        System.exit(0);
    }
}

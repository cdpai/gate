package cdpai.gate.trial;

import java.nio.file.*;

import cdpai.gate.browser.*;

/// Can a running browser be quit gracefully, every window at once, by handing it a quit URL through
/// its process singleton? Two windows are opened first. SCRATCH folder only.
/// args: exe dir url
public class QuitUrlTrial {
    public static void main(String[] a) throws Exception {
        var s = new GateSettings(a[0], Path.of(a[1]).toAbsolutePath().toString(), "unused");
        var plain = new ProcessBuilder(a[0], "--user-data-dir=" + s.userDataDir(), "--no-first-run", "--no-default-browser-check").start();
        Thread.sleep(7000);
        new ProcessBuilder(a[0], "--user-data-dir=" + s.userDataDir(), "--new-window", "https://example.com/?second-window").start().waitFor();
        Thread.sleep(4000);
        var b = ExistingBrowser.find(s).getFirst();
        System.out.println("browser pid " + b.pid() + " windows " + b.windowCount());
        var t0 = System.currentTimeMillis();
        var launcher = new ProcessBuilder(a[0], "--user-data-dir=" + s.userDataDir(), a[2]).start();
        for (var i = 0; i < 40; i++) {
            System.out.println((System.currentTimeMillis() - t0) + "ms original alive=" + b.alive() + " launcher alive=" + launcher.isAlive()
                + " browsers on folder=" + ExistingBrowser.find(s).stream().map(x -> x.pid() + "(" + x.windowCount() + "w)").toList());
            if (!b.alive() && !launcher.isAlive()) break;
            Thread.sleep(500);
        }
        if (launcher.isAlive()) { System.out.println("launcher still alive: killing it"); launcher.destroyForcibly(); }
        System.out.println("alive after quit url: " + b.alive());
        if (b.alive()) { System.out.println("windows now: " + ExistingBrowser.find(s).getFirst().windowCount()); plain.destroyForcibly(); ProcessHandle.of(b.pid()).ifPresent(ProcessHandle::destroyForcibly); return; }
        Thread.sleep(1500);
        var prefs = Files.readString(s.effectiveUserDataDir().resolve("Default").resolve("Preferences"));
        var i = prefs.indexOf("\"exit_type\"");
        System.out.println("exit recorded as " + prefs.substring(i, i + 25));
        var sessions = s.effectiveUserDataDir().resolve("Default").resolve("Sessions");
        try (var f = Files.list(sessions)) { f.forEach(p -> { try { System.out.println("  " + p.getFileName() + " " + Files.size(p)); } catch (Exception e) {} }); }
    }
}

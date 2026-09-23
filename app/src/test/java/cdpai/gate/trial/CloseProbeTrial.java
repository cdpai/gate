package cdpai.gate.trial;

import java.nio.file.Path;

import cdpai.gate.browser.*;
import cdpai.gate.win.User32;

/// What happens after WM_CLOSE to a plain browser on a SCRATCH folder: windows before and after, every second.
public class CloseProbeTrial {
    public static void main(String[] a) throws Exception {
        var s = new GateSettings(a[0], Path.of(a[1]).toAbsolutePath().toString(), "unused");
        var plain = new ProcessBuilder(a[0], "--user-data-dir=" + s.userDataDir(), "--no-first-run", "--no-default-browser-check").start();
        Thread.sleep(7000);
        var b = ExistingBrowser.find(s).getFirst();
        System.out.println("browser pid " + b.pid() + " windows " + b.windowCount() + " cmd " + b.commandLine());
        b.closeWindows();
        for (var i = 0; i < 40 && b.alive(); i++) {
            Thread.sleep(1000);
            if (i % 10 == 0) System.out.println(i + "s alive, children: " + ProcessHandle.of(b.pid()).map(p -> p.descendants().count()).orElse(0L));
        }
        System.out.println("alive at end: " + b.alive() + " exit code " + (b.alive() ? "-" : ""));
        if (b.alive()) plain.destroyForcibly();
    }
}

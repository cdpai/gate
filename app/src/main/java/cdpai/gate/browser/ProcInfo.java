package cdpai.gate.browser;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import cdpai.gate.client.GatePlatform;
import cdpai.gate.win.RemoteProcessInfo;

/// Another process's full command line and working directory, for the approval window and for
/// recognising an already-running browser. Best effort: a decision aid, never the boundary.
/// Windows reads the PEB; macOS has the arguments through ProcessHandle (sysctl KERN_PROCARGS2,
/// same-user processes only, which is every process that can reach the socket) and the working
/// directory through lsof.
public final class ProcInfo {

    public static Optional<String> commandLine(long pid) {
        if (GatePlatform.WINDOWS) return RemoteProcessInfo.commandLine(pid);
        return ProcessHandle.of(pid).flatMap(p -> p.info().commandLine());
    }

    public static Optional<String> currentDirectory(long pid) {
        if (GatePlatform.WINDOWS) return RemoteProcessInfo.currentDirectory(pid);
        try {
            var p = new ProcessBuilder("lsof", "-a", "-p", Long.toString(pid), "-d", "cwd", "-Fn").redirectErrorStream(true).start();
            var out = new String(p.getInputStream().readAllBytes());
            p.waitFor(3, TimeUnit.SECONDS);
            return out.lines().filter(l -> l.startsWith("n")).map(l -> l.substring(1)).findFirst();
        } catch (Exception e) { return Optional.empty(); }
    }

    private ProcInfo() {}
}

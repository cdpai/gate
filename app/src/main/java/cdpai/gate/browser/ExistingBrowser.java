package cdpai.gate.browser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import cdpai.gate.win.RemoteProcessInfo;
import cdpai.gate.win.User32;

/// A browser already running on the same profile folder that cdpgate did not start -- typically
/// the user's normal Vivaldi, with no debugging pipe. It holds the profile's process singleton, so
/// cdpgate's own launch would only hand off to it; it has to exit first.
///
/// Closing without CDP means WM_CLOSE, i.e. what clicking X does. With one window that is an
/// ordinary quit and the whole session is saved. With several, windows close one at a time and the
/// browser keeps only the last one as the session to restore (the rest go to "recently closed"),
/// so that case is handed to the user to quit from the browser's own menu instead.
public record ExistingBrowser(long pid, String commandLine, int windowCount) {

    static final String WINDOW_CLASS = "Chrome_WidgetWin_1";

    public static List<ExistingBrowser> find(GateSettings s) {
        var exe = Path.of(s.browserExe()).toAbsolutePath().normalize();
        var udd = s.effectiveUserDataDir().toAbsolutePath().normalize().toString();
        var dflt = s.defaultUserDataDir().toAbsolutePath().normalize().toString();
        var out = new ArrayList<ExistingBrowser>();
        ProcessHandle.allProcesses()
            .filter(p -> p.info().command().map(c -> Path.of(c).toAbsolutePath().normalize().equals(exe)).orElse(false))
            .forEach(p -> {
                var cl = RemoteProcessInfo.commandLine(p.pid()).orElse("");
                if (!cl.isEmpty() && !cl.contains("--type=") && sameProfileFolder(cl, udd, dflt))
                    out.add(new ExistingBrowser(p.pid(), cl, User32.topLevelWindows(p.pid(), WINDOW_CLASS).size()));
            });
        return out;
    }

    static boolean sameProfileFolder(String commandLine, String effectiveUdd, String defaultUdd) {
        var i = commandLine.indexOf("--user-data-dir=");
        if (i < 0) return defaultUdd.equalsIgnoreCase(effectiveUdd);
        var rest = commandLine.substring(i + "--user-data-dir=".length());
        var value = rest.startsWith("\"") ? rest.substring(1, Math.max(1, rest.indexOf('"', 1))) : rest.split(" ")[0];
        return Path.of(value).toAbsolutePath().normalize().toString().equalsIgnoreCase(effectiveUdd);
    }

    public boolean alive() { return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false); }

    public boolean hasDebugPort() { return commandLine.contains("--remote-debugging-port"); }

    public void closeWindows() {
        User32.topLevelWindows(pid, WINDOW_CLASS).forEach(User32::postClose);
    }

    public boolean waitForExit(long timeoutMs) {
        var deadline = System.currentTimeMillis() + timeoutMs;
        while (alive() && System.currentTimeMillis() < deadline) sleep(250);
        return !alive();
    }

    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}

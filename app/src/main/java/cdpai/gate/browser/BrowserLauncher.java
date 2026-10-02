package cdpai.gate.browser;

import java.util.List;

import cdpai.gate.client.GatePlatform;
import cdpai.gate.posix.PosixLauncher;
import cdpai.gate.win.VivaldiLauncher;

/// Starts the browser under the pipe, on whichever platform this is.
public final class BrowserLauncher {

    public static LaunchedBrowser launch(String exe, String userDataDir, List<String> extraArgs) {
        return GatePlatform.WINDOWS ? VivaldiLauncher.launch(exe, userDataDir, extraArgs)
            : PosixLauncher.launch(exe, userDataDir, extraArgs);
    }

    /// The flags every launch carries, on every platform. Deliberately never --enable-automation;
    /// see VivaldiLauncher for why --disable-blink-features=AutomationControlled is needed anyway.
    public static List<String> pipeFlags(String userDataDir, List<String> extraArgs) {
        var parts = new java.util.ArrayList<>(List.of("--remote-debugging-pipe",
            "--disable-blink-features=AutomationControlled", "--no-first-run", "--no-default-browser-check"));
        if (userDataDir != null) parts.add("--user-data-dir=" + userDataDir);
        parts.addAll(extraArgs);
        return parts;
    }

    private BrowserLauncher() {}
}

package cdpai.gate.browser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import cdpai.gate.hub.CdpHub;
import cdpai.gate.hub.TabPlacement;
import cdpai.gate.hub.TargetInventory;
import cdpai.gate.win.VivaldiLauncher;
import cdpai.gate.win.VivaldiProcess;

/// Owns the browser's lifetime. Starting means: if the browser is already running on these
/// profiles without cdpgate, ask the user to quit it from its own menu -- the one close path measured
/// to save everything; closing it from outside proved unreliable (a window close can leave the
/// process running with no windows, and a hand-off to a busy browser gets it killed as hung) --
/// then back up its sessions; then launch it under
/// `--remote-debugging-pipe`, opening the profiles that were open at last exit one at a time so
/// each one's context is known. The browser also exits by itself when cdpgate's end of the pipe
/// closes (measured: a normal exit, session saved), so the two live and die together.
public final class BrowserSupervisor {

    public enum State { STOPPED, CLOSING_EXISTING, STARTING, RUNNING }

    public interface ExistingBrowserPrompt {
        enum Choice { EXITED, CLOSE_WINDOWS, CANCEL }
        /// Shown while the browser is still open; returns EXITED once the user has quit it.
        Choice ask(ExistingBrowser b);
    }

    final GateSettings settings;
    final ProfileMap profiles = new ProfileMap();
    volatile State state = State.STOPPED;
    volatile VivaldiProcess vivaldi;
    volatile CdpHub hub;
    volatile ProfileLoader loader;
    volatile Consumer<String> status = s -> {};
    volatile Runnable onStopped = () -> {};

    public BrowserSupervisor(GateSettings settings) { this.settings = settings; }

    public void onStatus(Consumer<String> l) { status = l; }

    public void onStopped(Runnable r) { onStopped = r; }

    public State state() { return state; }

    public CdpHub hub() { return state == State.RUNNING ? hub : null; }

    public ProfileMap profiles() { return profiles; }

    public String browserName() { return "vivaldi"; }

    public long browserPid() { var v = vivaldi; return v == null ? -1 : v.pid; }

    public boolean openProfile(String dir) { var l = loader; return l != null && state == State.RUNNING && l.open(dir); }

    /// Blocking; run it off the UI thread. Returns false if the user cancelled closing the old browser.
    public synchronized boolean start(ExistingBrowserPrompt prompt) {
        if (state != State.STOPPED) return true;
        state = State.CLOSING_EXISTING;
        if (!closeExisting(prompt)) { state = State.STOPPED; status.accept("not started: the running browser was left open"); return false; }
        state = State.STARTING;
        var ls = LocalState.read(settings.effectiveUserDataDir());
        backupSessions(ls);
        profiles.setProfiles(ls.profiles());
        var order = ls.launchOrder();
        status.accept("launching " + browserName() + " with " + profiles.nameOf(order.getFirst()));
        var extra = new ArrayList<>(List.of("--profile-directory=" + order.getFirst()));
        vivaldi = VivaldiLauncher.launch(settings.browserExe(), settings.userDataDir(), extra);
        var inventory = new TargetInventory();
        var l = new ProfileLoader(settings, profiles, inventory);
        loader = l;
        hub = new CdpHub(vivaldi.cdp, inventory, profiles::dirOf,
            new TabPlacement(this::defaultContext, profiles::dirOf, d -> profiles.contextOf(d).orElse(null), l::openTab));
        var launchDir = order.getFirst();
        hub.launchContext(() -> profiles.contextOf(launchDir).orElse(null));
        loader.holdIdentification(true);
        inventory.onContextAppeared(loader::onContextAppeared);
        inventory.onContextGone(profiles::unbind);
        var mine = vivaldi;
        Thread.ofPlatform().name("cdpgate-browser-reader").start(() -> { hub.pumpBrowserMessages(); stopped(mine); });
        hub.start();
        loader.bindFirst(order.getFirst());
        for (var dir : order.subList(1, order.size())) {
            status.accept("opening profile " + profiles.nameOf(dir));
            loader.open(dir);
        }
        loader.holdIdentification(false);
        state = State.RUNNING;
        status.accept(browserName() + " running under cdpgate, " + profiles.loadedCount() + " profile(s) open");
        return true;
    }

    boolean closeExisting(ExistingBrowserPrompt prompt) {
        var existing = ExistingBrowser.find(settings);
        if (existing.isEmpty()) return true;
        for (var b : existing) {
            status.accept("waiting for you to quit the running " + browserName() + " (pid " + b.pid() + ")");
            var asked = false;
            while (!b.waitForExit(asked ? 1500 : 0)) {
                asked = true;
                switch (prompt.ask(b)) {
                    case CANCEL -> { return false; }
                    case CLOSE_WINDOWS -> { b.closeWindows(); b.waitForExit(30_000); }
                    case EXITED -> {}
                }
            }
        }
        waitForChildren();
        return true;
    }

    /// Taken while the browser is not running, when its session files are closed and final.
    void backupSessions(LocalState ls) {
        var dirs = new ArrayList<>(ls.lastActive());
        if (!dirs.contains(ls.lastUsed())) dirs.add(ls.lastUsed());
        status.accept("sessions backed up to " + SessionBackup.backup(settings.effectiveUserDataDir(), dirs));
    }

    void waitForChildren() {
        var deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && !ExistingBrowser.find(settings).isEmpty()) ProfileLoader.sleep(250);
        ProfileLoader.sleep(1500);
    }

    void stopped(VivaldiProcess which) {
        if (which != vivaldi) return;
        state = State.STOPPED;
        profiles.clear();
        var h = hub;
        hub = null;
        loader = null;
        if (h != null) h.disconnectAll();
        which.close();
        status.accept(browserName() + " is not running");
        onStopped.run();
    }

    /// The context the browser currently treats as default -- the profile used last.
    String defaultContext() {
        var h = hub;
        if (h == null) return null;
        try {
            return h.internalCall("Target.getBrowserContexts", JsonNodeFactory.instance.objectNode()).get(3, TimeUnit.SECONDS)
                .path("result").path("defaultBrowserContextId").asText(null);
        } catch (Exception e) { return null; }
    }

    /// Asks the browser to quit normally (session saved), then waits for it.
    public void quitBrowser() {
        var h = hub;
        var v = vivaldi;
        if (h == null || v == null) return;
        try { h.internalCall("Browser.close", JsonNodeFactory.instance.objectNode()).get(5, TimeUnit.SECONDS); } catch (Exception ignored) {}
        for (var i = 0; i < 60 && v.isAlive(); i++) ProfileLoader.sleep(250);
        if (v.isAlive()) v.kill();
    }
}

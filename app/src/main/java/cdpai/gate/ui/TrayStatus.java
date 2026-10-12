package cdpai.gate.ui;

import java.util.List;

import cdpai.gate.GateServer;
import cdpai.gate.browser.BrowserSupervisor;
import cdpai.gate.browser.ExistingBrowser;

/// The tray's state line, worked out from what is true now rather than from the last message that
/// happened to be printed. It also notices a browser running WITHOUT cdpgate: the browser is plainly
/// open, but no agent can reach it, and "not running" would be wrong. That check enumerates
/// processes, so it runs on its own thread every few seconds and the line only reads its result.
final class TrayStatus {

    final BrowserSupervisor browser;
    final GateServer server;
    volatile List<ExistingBrowser> outside = List.of();

    TrayStatus(BrowserSupervisor browser, GateServer server) {
        this.browser = browser;
        this.server = server;
        Thread.ofPlatform().name("cdpgate-outside-probe").daemon().start(this::probe);
    }

    void probe() {
        while (true) {
            if (browser.state() == BrowserSupervisor.State.STOPPED) {
                try { outside = ExistingBrowser.find(browser.settings()); } catch (Exception e) { outside = List.of(); }
            } else outside = List.of();
            try { Thread.sleep(4000); } catch (InterruptedException e) { return; }
        }
    }

    String line() {
        var name = browser.browserName();
        return switch (browser.state()) {
            case RUNNING -> name + " running under cdpgate · " + browser.profiles().loadedCount() + " profile(s) open · "
                + server.activeGrants().size() + " approval(s)";
            case STARTING, CLOSING_EXISTING -> browser.lastStatus();
            case STOPPED -> outside.isEmpty() ? name + " is not running"
                : name + " is open WITHOUT cdpgate (pid " + outside.getFirst().pid() + "), so agents cannot reach it; quit it, then Start";
        };
    }
}

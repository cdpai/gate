package cdpai.gate;

import java.nio.file.Path;

import cdpai.gate.access.CdpRules;
import cdpai.gate.access.CdpScope;
import leasegate.core.access.Approval;
import leasegate.core.access.KeyedAppStore;
import cdpai.gate.browser.BrowserSupervisor;
import cdpai.gate.client.GatePlatform;
import leasegate.core.ConsumerServer;
import leasegate.core.LeaseEngine;
import leasegate.core.LeaseServer;

/// cdpgate's pipe server: accepts connections and hands each to a ConnectionHandler. Who may get
/// through, for how long, and the sweep that cuts expired links all live in leasegate's engine;
/// what is left here is the browser the approved connections are joined to.
public final class GateServer {

    final ConsumerServer pipeServer;
    final BrowserSupervisor browser;
    public final LeaseEngine<CdpScope> engine;

    public GateServer(BrowserSupervisor browser, String pipeName, LeaseEngine<CdpScope> engine) {
        this.browser = browser;
        this.pipeServer = LeaseServer.open(GatePlatform.runDir(), pipeName);
        this.engine = engine;
    }

    /// A server with its own engine, for trials: `approval` stands in for the human.
    public GateServer(BrowserSupervisor browser, String pipeName, Approval<CdpScope> approval, Path keyedStore) {
        this(browser, pipeName, new LeaseEngine<>(new CdpRules(browser.profiles()), approval,
            new KeyedAppStore<>(keyedStore, new CdpRules(browser.profiles()))));
    }

    public void run() {
        engine.startSweep();
        LeaseServer.serve(pipeServer, "cdpgate", a -> new ConnectionHandler(this, a).run());
    }
}

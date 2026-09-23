package cdpai.gate.trial;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import cdpai.gate.browser.*;

/// Every context and its targets, after start and after a hand-off of Profile 2, with what cdpgate
/// bound each to. SCRATCH only.
public class ContextsTrial {
    public static void main(String[] a) throws Exception {
        var M = new ObjectMapper();
        var s = new BrowserSupervisor(new GateSettings(a[0], Path.of(a[1]).toAbsolutePath().toString(), "unused"));
        s.start(b -> BrowserSupervisor.ExistingBrowserPrompt.Choice.CANCEL);
        dump(s, M, "after start");
        s.openProfile("Profile 2");
        Thread.sleep(3000);
        dump(s, M, "after hand-off of Profile 2");
        s.quitBrowser();
        System.exit(0);
    }

    static void dump(BrowserSupervisor s, ObjectMapper M, String label) throws Exception {
        System.out.println("== " + label + " (default ctx " + s.profiles().dirOf(s.hub().internalCall("Target.getBrowserContexts", M.createObjectNode())
            .get(5, TimeUnit.SECONDS).path("result").path("defaultBrowserContextId").asText()) + ")");
        for (var t : s.hub().internalCall("Target.getTargets", M.createObjectNode()).get(5, TimeUnit.SECONDS).path("result").path("targetInfos"))
            System.out.println("  " + t.path("browserContextId").asText().substring(0, 8) + " -> " + s.profiles().dirOf(t.path("browserContextId").asText())
                + "  " + t.path("type").asText() + "  " + t.path("url").asText());
    }
}

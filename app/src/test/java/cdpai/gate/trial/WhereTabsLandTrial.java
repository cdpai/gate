package cdpai.gate.trial;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import cdpai.gate.browser.*;

/// Where does Target.createTarget put a tab when no browserContextId is given, with the startup
/// profile and a hand-off profile both open? And does the reported default context agree? SCRATCH only.
public class WhereTabsLandTrial {
    public static void main(String[] a) throws Exception {
        var M = new ObjectMapper();
        var s = new BrowserSupervisor(new GateSettings(a[0], Path.of(a[1]).toAbsolutePath().toString(), "unused"));
        s.start(b -> BrowserSupervisor.ExistingBrowserPrompt.Choice.CLOSE_WINDOWS);
        s.openProfile("Profile 2");
        for (var i = 0; i < 2; i++) {
            var def = s.hub().internalCall("Target.getBrowserContexts", M.createObjectNode()).get(5, TimeUnit.SECONDS).path("result").path("defaultBrowserContextId").asText();
            var t = s.hub().internalCall("Target.createTarget", M.createObjectNode().put("url", "https://example.com/?where" + i)).get(5, TimeUnit.SECONDS);
            var info = s.hub().internalCall("Target.getTargetInfo", M.createObjectNode().put("targetId", t.path("result").path("targetId").asText())).get(5, TimeUnit.SECONDS);
            var ctx = info.path("result").path("targetInfo").path("browserContextId").asText();
            System.out.println("round " + i + ": reported default=" + s.profiles().dirOf(def) + ", tab landed in=" + s.profiles().dirOf(ctx)
                + ", startup profile ctx accepted by id? " + !s.hub().internalCall("Target.createTarget", M.createObjectNode().put("url", "about:blank")
                    .put("browserContextId", s.profiles().contextOf("Default").orElse(""))).get(5, TimeUnit.SECONDS).has("error"));
        }
        s.quitBrowser();
        System.exit(0);
    }
}

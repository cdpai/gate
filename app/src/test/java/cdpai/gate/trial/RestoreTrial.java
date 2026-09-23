package cdpai.gate.trial;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.browser.*;

import static cdpai.gate.trial.Checks.check;

/// Does a relaunch under cdpgate bring every profile's tabs back? Opens distinct tabs in two
/// profiles, quits the way cdpgate does (Browser.close), starts again through the supervisor --
/// first profile by --profile-directory, the second by hand-off -- and compares. SCRATCH folder only.
/// java --enable-native-access=ALL-UNNAMED -cp <cp> cdpai.gate.trial.RestoreTrial <vivaldi.exe> <scratch-dir>
public class RestoreTrial {

    static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] a) throws Exception {
        var settings = new GateSettings(a[0], Path.of(a[1]).toAbsolutePath().toString(), "unused");
        var s1 = new BrowserSupervisor(settings);
        s1.onStatus(x -> System.out.println("  [status] " + x));
        check("first start", s1.start(b -> BrowserSupervisor.ExistingBrowserPrompt.Choice.CLOSE_WINDOWS));
        s1.openProfile("Profile 2");
        var planted = Map.of("Default", "https://example.com/?restore-default", "Profile 2", "https://example.org/?restore-p2");
        var keyed = java.nio.file.Files.createTempFile("k", ".json");
        java.nio.file.Files.writeString(keyed, "[]");
        var server = new cdpai.gate.GateServer(s1, "cdpai-gate-restoretrial", new ScriptedApproval(), new cdpai.gate.access.KeyedAppStore(keyed));
        Thread.ofPlatform().daemon().start(server::run);
        Thread.sleep(500);
        for (var e : planted.entrySet()) {
            var scope = new cdpai.gate.client.ScopeRequest(null, List.of(e.getKey()), null);
            try (var c = cdpai.gate.client.GateClient.connect("cdpai-gate-restoretrial", "restore", scope, null)) {
                var made = LiveScenarios.call(c, "Target.createTarget", "{\"url\":\"" + e.getValue() + "\"}");
                var info = LiveScenarios.call(c, "Target.getTargetInfo", "{\"targetId\":\"" + made.path("result").path("targetId").asText() + "\"}");
                var landed = s1.profiles().dirOf(info.path("result").path("targetInfo").path("browserContextId").asText());
                check("a consumer scoped to " + e.getKey() + " gets its new tab there (landed in " + landed + ")", e.getKey().equals(landed));
            }
        }
        Thread.sleep(4000);
        System.out.println("  before quit: " + pages(s1));
        s1.quitBrowser();
        Thread.sleep(3000);
        var ls = LocalState.read(settings.effectiveUserDataDir());
        System.out.println("  Local State after quit: last_active=" + ls.lastActive() + " last_used=" + ls.lastUsed());
        check("both profiles recorded as open at exit", ls.lastActive().containsAll(List.of("Default", "Profile 2")));

        var s2 = new BrowserSupervisor(settings);
        s2.onStatus(x -> System.out.println("  [status] " + x));
        check("second start", s2.start(b -> BrowserSupervisor.ExistingBrowserPrompt.Choice.CLOSE_WINDOWS));
        Thread.sleep(5000);
        var after = pages(s2);
        System.out.println("  after relaunch: " + after);
        for (var e : planted.entrySet())
            check(e.getKey() + " restored its own tab", after.getOrDefault(e.getKey(), List.of()).stream().anyMatch(u -> u.startsWith(e.getValue())));
        s2.quitBrowser();
        System.exit(Checks.summary());
    }

    static Map<String, List<String>> pages(BrowserSupervisor s) throws Exception {
        var out = new TreeMap<String, List<String>>();
        var r = s.hub().internalCall("Target.getTargets", M.createObjectNode()).get(10, TimeUnit.SECONDS);
        for (var t : r.path("result").path("targetInfos")) {
            if (!t.path("type").asText().equals("page")) continue;
            var dir = s.profiles().dirOf(t.path("browserContextId").asText());
            out.computeIfAbsent(dir == null ? "?" + t.path("browserContextId").asText().substring(0, 6) : dir, k -> new ArrayList<>()).add(t.path("url").asText());
        }
        return out;
    }
}

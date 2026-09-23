package cdpai.gate.trial;

import java.nio.file.*;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cdpai.gate.GateServer;
import cdpai.gate.access.KeyedAppStore;
import cdpai.gate.browser.*;
import cdpai.gate.client.*;

import static cdpai.gate.trial.Checks.check;

/// The whole of cdpgate except the JavaFX windows, live, against a SCRATCH profile folder -- never
/// the real one. First a plain Vivaldi is started on the scratch folder, as the user's would be;
/// cdpgate must close it and relaunch it under the pipe. Then every consumer-facing rule is
/// exercised through the real client library. ScriptedApproval stands in for the human.
/// java --enable-native-access=ALL-UNNAMED -cp <cp> cdpai.gate.trial.LiveGateTrial <vivaldi.exe> <scratch-dir>
public class LiveGateTrial {

    static final ObjectMapper M = new ObjectMapper();
    static final String PIPE = "cdpai-gate-livetrial";

    public static void main(String[] a) throws Exception {
        var exe = a[0];
        var udd = Path.of(a[1]).toAbsolutePath();
        onlyDefaultActive(udd);
        var settings = new GateSettings(exe, udd.toString(), PIPE);
        var approval = new ScriptedApproval();
        var browser = new BrowserSupervisor(settings);
        browser.onStatus(s -> System.out.println("  [status] " + s));
        var keyedFile = Files.createTempFile("keyed", ".json");
        Files.writeString(keyedFile, "[]");
        var server = new GateServer(browser, PIPE, approval, new KeyedAppStore(keyedFile));
        Thread.ofPlatform().daemon().start(server::run);

        System.out.println("== an unprotected browser is already running on the profile folder");
        var plain = new ProcessBuilder(exe, "--user-data-dir=" + udd, "--no-first-run").start();
        Thread.sleep(6000);
        check("plain browser is up", !ExistingBrowser.find(settings).isEmpty());
        var started = browser.start(b -> {
            System.out.println("  [prompt] asked to quit pid " + b.pid() + "; the trial plays the user and ends the scratch browser");
            if (b.commandLine().contains(udd.toString())) ProcessHandle.of(b.pid()).ifPresent(ProcessHandle::destroy);
            return b.waitForExit(30_000) ? BrowserSupervisor.ExistingBrowserPrompt.Choice.EXITED : BrowserSupervisor.ExistingBrowserPrompt.Choice.CANCEL;
        });
        check("supervisor started", started && browser.state() == BrowserSupervisor.State.RUNNING);
        check("the plain browser was closed", !plain.isAlive());
        check("browser now runs under the pipe", browser.browserPid() > 0 && browser.browserPid() != plain.pid());

        var profiles = GateClient.profiles(PIPE);
        System.out.println("  profiles: " + profiles.path("profiles"));
        check("profiles op answers without approval", approval.asked.isEmpty() && profiles.path("ok").asBoolean());
        check("Default is loaded and bound", loaded(profiles, "Default"));

        LiveScenarios.attested(approval);
        LiveScenarios.scoped(approval, browser);
        LiveScenarios.keyed(approval);
        LiveScenarios.guards();

        System.out.println("== quit");
        browser.quitBrowser();
        Thread.sleep(1500);
        check("browser stopped", browser.state() == BrowserSupervisor.State.STOPPED);
        try { GateClient.connect(PIPE, "late", ScopeRequest.UNSCOPED, null); check("connect refused while stopped", false); }
        catch (GateDeniedException e) { check("connect refused while stopped, with a hint: " + e.getMessage(), e.hint != null); }
        System.exit(Checks.summary());
    }

    static boolean loaded(com.fasterxml.jackson.databind.JsonNode profiles, String dir) {
        for (var p : profiles.path("profiles")) if (p.path("dir").asText().equals(dir)) return p.path("loaded").asBoolean();
        return false;
    }

    /// So that the hand-off scenario has a profile to open that is not already loaded, and the
    /// plain browser opens with one tab rather than stopping on "close these tabs?".
    static void onlyDefaultActive(Path udd) throws Exception {
        var file = udd.resolve("Local State");
        var root = (ObjectNode) M.readTree(Files.readString(file));
        var p = (ObjectNode) root.path("profile");
        p.putArray("last_active_profiles").add("Default");
        p.put("last_used", "Default");
        Files.writeString(file, root.toString());
        for (var dir : List.of("Default", "Profile 2")) {
            var sessions = udd.resolve(dir).resolve("Sessions");
            if (Files.isDirectory(sessions)) try (var f = Files.list(sessions)) { for (var x : f.toList()) Files.deleteIfExists(x); }
        }
    }

    static List<String> list(String... s) { return List.of(s); }
}

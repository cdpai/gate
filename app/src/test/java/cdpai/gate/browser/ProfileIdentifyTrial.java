package cdpai.gate.browser;

import java.nio.channels.FileChannel;
import java.nio.file.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.nio.file.ExtendedOpenOption;

import cdpai.gate.hub.TargetInventory;
import static cdpai.gate.trial.Checks.check;
import static cdpai.gate.trial.Checks.summary;

/// PRP 14. A window that appears unannounced must get the label of the profile the browser actually
/// loaded, not whatever `Local State`'s `last_used` says. Reproduces the 2026-10-15 state:
/// last_used still names Home (Profile 7) while the window that appeared is Work (Default). The old
/// guess labelled it Home, so the first check FAILS on that build. Windows only (the evidence is a
/// held file); run: java -cp "target\classes;target\test-classes;<deps>" cdpai.gate.browser.ProfileIdentifyTrial
public final class ProfileIdentifyTrial {

    public static void main(String[] a) throws Exception {
        var udd = Files.createTempDirectory("cdpgate-pi");
        Files.writeString(udd.resolve("Local State"), """
            {"profile":{"info_cache":{"Default":{"name":"Work"},"Profile 7":{"name":"Home"}},
             "last_active_profiles":["Profile 7"],"last_used":"Profile 7"}}""");
        for (var d : new String[]{"Default", "Profile 7"}) {
            Files.createDirectories(udd.resolve(d).resolve("Network"));
            Files.writeString(udd.resolve(d).resolve("Network").resolve("Cookies"), "x");
        }
        var settings = new GateSettings("vivaldi.exe", udd.toString(), null);
        var profiles = new ProfileMap();
        profiles.setProfiles(LocalState.read(udd).profiles());
        var inv = new TargetInventory();
        var loader = new ProfileLoader(settings, profiles, inv);

        // Work's network service holds its cookie store; Home is not loaded.
        try (var work = FileChannel.open(udd.resolve("Default/Network/Cookies"), StandardOpenOption.READ, StandardOpenOption.WRITE,
                ExtendedOpenOption.NOSHARE_READ, ExtendedOpenOption.NOSHARE_WRITE, ExtendedOpenOption.NOSHARE_DELETE)) {
            put(inv, "T1", "CTX-A");
            loader.identify("CTX-A");
            check("stale last_used=Home: the new window is labelled Work (got " + profiles.nameOf(profiles.dirOf("CTX-A")) + ")",
                "Default".equals(profiles.dirOf("CTX-A")));

            // A label that is already wrong (an old guess) is corrected by the periodic check.
            profiles.bind("CTX-A", "Profile 7", true);
            loader.reconcile();
            check("one miss is tolerated", "Profile 7".equals(profiles.dirOf("CTX-A")));
            loader.reconcile();
            check("a wrong label is dropped and the context relabelled Work", "Default".equals(profiles.dirOf("CTX-A")));

            // A second context with no unclaimed loaded profile (an incognito window) stays unbound.
            put(inv, "T2", "CTX-B");
            loader.tryIdentify("CTX-B");
            check("no evidence: the context stays unlabelled (fails closed)", !profiles.isBound("CTX-B"));
        }
        System.exit(summary());
    }

    static void put(TargetInventory inv, String id, String ctx) throws Exception {
        JsonNode info = JsonNodeFactory.instance.objectNode().put("targetId", id).put("type", "page")
            .put("url", "https://www.youtube.com/").put("title", "t").put("browserContextId", ctx);
        var m = TargetInventory.class.getDeclaredMethod("put", JsonNode.class);
        m.setAccessible(true);
        m.invoke(inv, info);
    }
}

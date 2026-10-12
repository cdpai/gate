package cdpai.gate.trial;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import cdpai.gate.access.*;

import static cdpai.gate.trial.Checks.*;

/// Pure logic proof for PRP 12's second round: Extend adds time up to the cap (it used to reset to
/// "now + duration", so repeated clicks did nothing), blanket grants follow an extended blanket mode,
/// trust grants cover any client under their anchor, and `cdpg trust` only accepts an ancestor.
/// mvn exec:java -Dexec.mainClass=cdpai.gate.trial.ExtendAndTrustTrial -Dexec.classpathScope=test
public class ExtendAndTrustTrial {
    public static void main(String[] args) throws Exception {
        var tree = ProcessTree.snapshot();
        var chain = tree.decorate(ProcessTree.rawChain(ProcessHandle.current().pid()));
        var me = chain.getFirst();
        var scope = new Scope(List.of("facebook.com"), List.of("Default"), null);
        var store = new GrantStore();

        var g = store.create("C:\\x\\cdpg.exe", me, 60, 240, scope, false);
        check("extend adds the duration on top of what is left", near(store.extend(g.id()).orElseThrow().expiresAt(), 120));
        check("a second extend adds again", near(store.extend(g.id()).orElseThrow().expiresAt(), 180));
        check("a third stops at the cap", near(store.extend(g.id()).orElseThrow().expiresAt(), 240));

        var s = store.create("C:\\x\\cdpg.exe", me, 15, 30, scope, false);
        check("a short grant extends by at least 30 minutes, within its cap", near(store.extend(s.id()).orElseThrow().expiresAt(), 30));

        var b = store.create("C:\\x\\netrec.exe", me, 2, PassphraseGate.BLANKET_MODE_MAX_MINUTES, scope, true);
        check("a blanket grant with 2 minutes left extends by 30", near(store.extend(b.id()).orElseThrow().expiresAt(), 32));
        store.extendBlanketGrants(Instant.now().plusSeconds(200 * 60));
        check("extending blanket mode carries its grants to the new end", near(store.byId(b.id()).orElseThrow().expiresAt(), 200));
        check("...and leaves ordinary grants alone", near(store.byId(s.id()).orElseThrow().expiresAt(), 30));

        store.revoke(s.id());
        check("extending a grant that has ended returns empty", store.extend(s.id()).isEmpty());

        var t = store.create(null, chain.get(1), 480, TrustApproval.MAX_MINUTES, scope, false);
        check("a trust grant covers any executable under its anchor", store.findValid("C:\\y\\java.exe", chain, scope).map(Grant::id).orElse("").equals(t.id())
            || store.findValid("C:\\y\\java.exe", chain, scope).isPresent());
        check("...but not a wider scope", store.findValid("C:\\y\\java.exe", chain, Scope.UNSCOPED).isEmpty());

        var gate = new PassphraseGate(Files.createTempDirectory("t").resolve("p.json"));
        gate.activateBlanketMode(60);
        check("blanket extend adds to what is left", near(gate.extendBlanketMode(60), 120));
        check("blanket extend never leaves more than 4h", near(gate.extendBlanketMode(240), 240));

        check("a pid that is not an ancestor is refused", TrustTarget.pick(chain, tree, 4L) instanceof TrustTarget.Refused);
        var auto = TrustTarget.pick(chain, tree, null);
        System.out.println("  (auto pick from this process: " + auto + ")");
        check("with no pid, the nearest agent session is picked when there is one",
            auto instanceof TrustTarget.Found f ? KnownPrograms.isAgent(f.node()) : chain.stream().noneMatch(KnownPrograms::isAgent));
        check("an explicit ancestor pid is accepted", TrustTarget.pick(chain, tree, chain.get(1).pid()) instanceof TrustTarget.Found);

        check("risk: one profile + one site, 8h is green", TrustRisk.of(scope, 480) == TrustRisk.GREEN);
        check("risk: same over a day is amber", TrustRisk.of(scope, 2880) == TrustRisk.AMBER);
        check("risk: profile only is amber", TrustRisk.of(new Scope(null, List.of("Default"), null), 480) == TrustRisk.AMBER);
        check("risk: everything is red", TrustRisk.of(Scope.UNSCOPED, 240) == TrustRisk.RED);
        System.exit(summary());
    }

    static boolean near(Instant t, int minutesFromNow) {
        return Math.abs(Duration.between(Instant.now(), t).toSeconds() - minutesFromNow * 60L) < 5;
    }
}

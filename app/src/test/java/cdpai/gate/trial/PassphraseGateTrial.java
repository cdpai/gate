package cdpai.gate.trial;

import java.nio.file.Files;
import java.nio.file.Path;

import cdpai.gate.access.PassphraseGate;

/// Pure logic proof for PassphraseGate -- no JavaFX, no pipe. Covers Tier 0 (set-once, unlock,
/// wrong passphrase, no reset) and Tier 2 (blanket mode activation/deactivation/hard cap), plus
/// restart survival (a fresh instance reloading the same store file still verifies the same
/// passphrase) -- the same property RegisteredAppStoreTrial already proved for bearer tokens.
/// java -cp <classes> cdpai.gate.trial.PassphraseGateTrial
public class PassphraseGateTrial {
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("cdpgate-passphrase-trial");
        var storePath = dir.resolve("passphrase.json");

        var gate = new PassphraseGate(storePath);
        check("no passphrase set yet", !gate.hasPassphrase());
        check("not unlocked before anything is set", !gate.isUnlocked());

        check("rejects non-alphanumeric on setup", !gate.setInitialPassphrase("has a space"));
        check("rejects blank on setup", !gate.setInitialPassphrase(""));
        check("accepts a valid alphanumeric passphrase", gate.setInitialPassphrase("Sunrise42"));
        check("setInitialPassphrase unlocks in the same act", gate.isUnlocked());
        check("cannot set a second passphrase once one exists", !gate.setInitialPassphrase("Другое1"));

        var fresh = new PassphraseGate(storePath);
        check("a fresh instance is locked even though a passphrase already exists on disk", !fresh.isUnlocked());
        check("wrong passphrase does not unlock", !fresh.unlock("wrong"));
        check("still locked after a wrong attempt", !fresh.isUnlocked());
        check("right passphrase unlocks (restart survival)", fresh.unlock("Sunrise42"));
        check("now unlocked", fresh.isUnlocked());
        check("verify() does not itself unlock (Tier 1/2 use it read-only)",
            new PassphraseGate(storePath).verify("Sunrise42") && !new PassphraseGate(storePath).isUnlocked());

        // Tier 2: blanket mode.
        check("blanket mode starts inactive", !fresh.isBlanketModeActive());
        check("blanket mode remaining is zero when inactive", fresh.blanketModeRemaining().isZero());
        fresh.activateBlanketMode();
        check("blanket mode active right after activation", fresh.isBlanketModeActive());
        check("blanket mode remaining is at most the hard cap",
            fresh.blanketModeRemaining().toMinutes() <= PassphraseGate.BLANKET_MODE_MAX_MINUTES);
        check("blanket mode remaining is close to the hard cap right after activation",
            fresh.blanketModeRemaining().toMinutes() >= PassphraseGate.BLANKET_MODE_MAX_MINUTES - 1);
        fresh.deactivateBlanketModeNow();
        check("deactivateBlanketModeNow turns it off immediately", !fresh.isBlanketModeActive());

        Files.deleteIfExists(storePath);
        Files.deleteIfExists(dir);

        System.out.println("ALL CHECKS PASSED");
    }

    static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError("FAILED: " + label);
        System.out.println("ok - " + label);
    }
}

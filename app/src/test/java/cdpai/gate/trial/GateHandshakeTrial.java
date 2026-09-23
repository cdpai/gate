package cdpai.gate.trial;

import java.util.List;

import cdpai.gate.access.GateHandshake;
import cdpai.gate.access.KeyedAppStore;
import cdpai.gate.client.GateKey;

import static cdpai.gate.trial.Checks.check;

/// The first-frame parser and the key proof, as pure logic.
/// java -cp <classes> cdpai.gate.trial.GateHandshakeTrial
public class GateHandshakeTrial {
    public static void main(String[] args) throws Exception {
        check("a plain CDP frame is a legacy client", GateHandshake.extract("{\"id\":1,\"method\":\"Browser.getVersion\"}").isEmpty());
        check("garbage is a legacy client too", GateHandshake.extract("not json").isEmpty());

        var h = GateHandshake.extract("{\"gate\":{\"v\":2,\"op\":\"connect\",\"app\":\"netrec\",\"domains\":[\"youtube.com\",\" \"],"
            + "\"profiles\":[\"Work\"],\"minutes\":60}}").orElseThrow();
        check("op, app, minutes parsed", h.op().equals("connect") && h.app().equals("netrec") && h.minutes() == 60);
        check("blank entries dropped", h.domains().equals(List.of("youtube.com")) && h.profiles().equals(List.of("Work")));
        check("no publicKey means attested", !h.keyed());
        check("absent lists mean not narrowed", GateHandshake.extract("{\"gate\":{\"op\":\"connect\"}}").orElseThrow().domains() == null);
        check("profiles op parsed", GateHandshake.extract("{\"gate\":{\"v\":2,\"op\":\"profiles\"}}").orElseThrow().op().equals("profiles"));

        var dir = java.nio.file.Files.createTempDirectory("gatekey");
        var key = GateKey.loadOrCreate(dir.resolve("a.json"));
        var again = GateKey.loadOrCreate(dir.resolve("a.json"));
        check("a key reloads with the same public half", key.publicKey().equals(again.publicKey()));
        var nonce = KeyedAppStore.newChallenge();
        check("a signature verifies against its own key", KeyedAppStore.verify(key.publicKey(), nonce, again.sign(nonce)));
        var other = GateKey.loadOrCreate(dir.resolve("b.json"));
        check("another key's signature does not", !KeyedAppStore.verify(key.publicKey(), nonce, other.sign(nonce)));
        check("a signature does not verify a different challenge", !KeyedAppStore.verify(key.publicKey(), KeyedAppStore.newChallenge(), key.sign(nonce)));
        check("fingerprints are 16 hex digits", KeyedAppStore.fingerprint(key.publicKey()).matches("[0-9a-f]{16}"));
        System.exit(Checks.summary());
    }
}

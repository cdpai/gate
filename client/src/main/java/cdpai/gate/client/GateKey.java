package cdpai.gate.client;

import java.nio.file.Path;

import leasegate.transport.LeaseKey;

/// A keyed app's own identity: an Ed25519 key pair the app mints for itself on first use and
/// keeps in its own settings folder. cdpgate stores only the public half, so nothing cdpgate holds
/// can be replayed, and nothing secret ever crosses the pipe -- each connection answers a fresh
/// challenge with a signature instead.
public final class GateKey {

    final LeaseKey key;

    GateKey(LeaseKey key) { this.key = key; }

    /// `~/cdpai/gate/keys/<app>.json` -- one key per app name.
    public static Path defaultPath(String app) {
        return Path.of(System.getProperty("user.home"), "cdpai", "gate", "keys", app + ".json");
    }

    public static GateKey loadOrCreate(String app) { return loadOrCreate(defaultPath(app)); }

    public static GateKey loadOrCreate(Path file) { return new GateKey(LeaseKey.loadOrCreate(file)); }

    public String publicKey() { return key.publicKey(); }

    public String sign(String challengeB64) { return key.sign(challengeB64); }
}

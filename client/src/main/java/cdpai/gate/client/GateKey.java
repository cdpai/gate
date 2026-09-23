package cdpai.gate.client;

import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

/// A keyed app's own identity: an Ed25519 key pair the app mints for itself on first use and
/// keeps in its own settings folder. cdpgate stores only the public half, so nothing cdpgate holds
/// can be replayed, and nothing secret ever crosses the pipe -- each connection answers a fresh
/// challenge with a signature instead.
public final class GateKey {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final Base64.Encoder B64 = Base64.getEncoder();
    static final Base64.Decoder UNB64 = Base64.getDecoder();

    final PrivateKey privateKey;
    final String publicKeyB64;

    GateKey(PrivateKey privateKey, String publicKeyB64) {
        this.privateKey = privateKey;
        this.publicKeyB64 = publicKeyB64;
    }

    /// `~/cdpai/gate/keys/<app>.json` -- one key per app name.
    public static Path defaultPath(String app) {
        return Path.of(System.getProperty("user.home"), "cdpai", "gate", "keys", app + ".json");
    }

    public static GateKey loadOrCreate(String app) { return loadOrCreate(defaultPath(app)); }

    public static GateKey loadOrCreate(Path file) {
        try {
            if (Files.exists(file)) {
                var n = MAPPER.readTree(Files.readString(file));
                var priv = KeyFactory.getInstance("Ed25519")
                    .generatePrivate(new PKCS8EncodedKeySpec(UNB64.decode(n.get("privateKey").asText())));
                return new GateKey(priv, n.get("publicKey").asText());
            }
            var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var pub = B64.encodeToString(pair.getPublic().getEncoded());
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.createObjectNode()
                .put("publicKey", pub).put("privateKey", B64.encodeToString(pair.getPrivate().getEncoded()))));
            return new GateKey(pair.getPrivate(), pub);
        } catch (Exception e) { throw new IllegalStateException("cannot load or create gate key " + file, e); }
    }

    public String publicKey() { return publicKeyB64; }

    public String sign(String challengeB64) {
        try {
            var s = Signature.getInstance("Ed25519");
            s.initSign(privateKey);
            s.update(UNB64.decode(challengeB64));
            return B64.encodeToString(s.sign());
        } catch (Exception e) { throw new IllegalStateException("signing failed", e); }
    }
}

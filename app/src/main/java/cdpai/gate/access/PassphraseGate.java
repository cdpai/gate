package cdpai.gate.access;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// Wallet-style password gating on top of everything else cdpgate already enforces (kernel
/// attestation, scope enforcement, duration caps) -- modeled explicitly on a credential wallet's own
/// passphrase mechanism, per the user's own words (2026-09-21). Three tiers, each additive:
///
/// - **Tier 0 (baseline unlock)**: an alphanumeric-only passphrase, set on first run, entered
///   once to unlock the tray app -- nothing else starts until this succeeds. Stays unlocked after
///   that; no idle auto-lock, since none was asked for.
/// - **Tier 1 (per-connection re-confirm)**: a request that is fully unscoped in BOTH dimensions
///   (every domain, every profile -- "very controversial") re-asks the passphrase before Approve
///   is clickable, even though cdpgate is already unlocked. See `cdpai.gate.ui.ApprovalWindow`.
/// - **Tier 2 (blanket mode, capped)**: a separate, explicit "trust everything" toggle, confirmed
///   TWICE (typed twice, as deliberate extra friction) and hard-capped at
///   `BLANKET_MODE_MAX_MINUTES` regardless of anything else -- auto-reverts, never renewed just by
///   staying active. Explicitly for testing convenience, not normal operation. See
///   `BlanketModeApproval`.
///
/// Stores only a salted SHA-256 hash -- exactly the same shape RegisteredAppStore already uses
/// for bearer tokens -- the plain passphrase is never persisted anywhere and there is no reset.
public final class PassphraseGate {

    public static final int BLANKET_MODE_MAX_MINUTES = 60;

    final Path storePath;
    final ObjectMapper mapper = new ObjectMapper();
    String saltHex;
    String hashHex;
    volatile boolean unlocked;
    volatile Instant blanketModeExpiresAt;

    public PassphraseGate() { this(defaultPath()); }

    public PassphraseGate(Path storePath) {
        this.storePath = storePath;
        load();
    }

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), "cdpai", "gate", "passphrase.json");
    }

    public boolean hasPassphrase() { return hashHex != null; }

    public boolean isUnlocked() { return unlocked; }

    public static boolean isValidPassphrase(String candidate) {
        return candidate != null && !candidate.isEmpty() && candidate.chars().allMatch(Character::isLetterOrDigit);
    }

    /// First-run only: sets the passphrase and unlocks in the same act. Refuses if one is
    /// already set -- there is nowhere to reset it from if forgotten, by design (cdpgate stores
    /// only the hash), so changing an existing one is not something this phase supports.
    public synchronized boolean setInitialPassphrase(String passphrase) {
        if (hasPassphrase() || !isValidPassphrase(passphrase)) return false;
        var salt = newSalt();
        saltHex = HexFormat.of().formatHex(salt);
        hashHex = hash(salt, passphrase);
        save();
        unlocked = true;
        return true;
    }

    public synchronized boolean unlock(String passphrase) {
        if (!verify(passphrase)) return false;
        unlocked = true;
        return true;
    }

    public synchronized boolean verify(String passphrase) {
        if (!hasPassphrase() || passphrase == null) return false;
        return hash(HexFormat.of().parseHex(saltHex), passphrase).equals(hashHex);
    }

    // -- Tier 2: blanket mode --

    public boolean isBlanketModeActive() {
        var expires = blanketModeExpiresAt;
        return expires != null && Instant.now().isBefore(expires);
    }

    public Duration blanketModeRemaining() {
        var expires = blanketModeExpiresAt;
        if (expires == null) return Duration.ZERO;
        var remaining = Duration.between(Instant.now(), expires);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    /// Activates blanket mode for the full `BLANKET_MODE_MAX_MINUTES` -- always the hard cap,
    /// never a caller-chosen duration, so there is nothing to negotiate here. The caller (the tray
    /// menu action) is responsible for having already confirmed the passphrase TWICE before
    /// calling this -- this method itself does no verification, matching how Tier 1's own
    /// confirmation in ApprovalWindow works (verify in the UI layer, then just act).
    public void activateBlanketMode() {
        blanketModeExpiresAt = Instant.now().plusSeconds(BLANKET_MODE_MAX_MINUTES * 60L);
    }

    public void deactivateBlanketModeNow() { blanketModeExpiresAt = null; }

    // -- persistence --

    void load() {
        if (!Files.exists(storePath)) return;
        try {
            var root = mapper.readTree(Files.readString(storePath));
            saltHex = root.get("saltHex").asText();
            hashHex = root.get("hashHex").asText();
        } catch (IOException e) {
            throw new RuntimeException("cannot read " + storePath, e);
        }
    }

    void save() {
        try {
            Files.createDirectories(storePath.getParent());
            ObjectNode node = mapper.createObjectNode();
            node.put("saltHex", saltHex);
            node.put("hashHex", hashHex);
            node.put("createdAt", Instant.now().toEpochMilli());
            Files.writeString(storePath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        } catch (IOException e) {
            throw new RuntimeException("cannot write " + storePath, e);
        }
    }

    static byte[] newSalt() {
        var salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    static String hash(byte[] salt, String passphrase) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(passphrase.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

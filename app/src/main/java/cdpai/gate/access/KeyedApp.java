package cdpai.gate.access;

import java.time.Instant;

/// An approval held by an app's own key rather than by its place in the process tree -- for apps
/// started at logon, whose ancestry means nothing. cdpgate keeps only the public key; the app
/// proves possession on every connection. `scope` is what the human approved; a connection may ask
/// for less, never more. `lastSeen*` records which executable presented the key, and `flagged`
/// marks that it changed since the previous connection.
public record KeyedApp(String id, String app, String publicKey, String fingerprint, Scope scope,
                       Instant approvedAt, Instant expiresAt, String lastSeenImagePath, Long lastSeenPid, boolean flagged) {

    public boolean expired(Instant now) { return !now.isBefore(expiresAt); }
}

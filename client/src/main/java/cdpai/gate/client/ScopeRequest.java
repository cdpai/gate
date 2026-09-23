package cdpai.gate.client;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

/// What a connection asks for. `null` means "not narrowed" for that dimension, which cdpgate
/// answers with its shortest approval. `profiles` are profile names or folder names exactly as
/// `cdpg profiles` lists them (e.g. "Work" or "Profile 3"); `domains` are plain hosts, matched
/// exactly or as a subdomain, never patterns.
public record ScopeRequest(List<String> domains, List<String> profiles, Integer minutes) {

    public static final ScopeRequest UNSCOPED = new ScopeRequest(null, null, null);

    public boolean isUnscoped() { return domains == null && profiles == null; }

    void writeInto(ObjectNode gate) {
        if (domains != null) domains.forEach(gate.putArray("domains")::add);
        if (profiles != null) profiles.forEach(gate.putArray("profiles")::add);
        if (minutes != null) gate.put("minutes", minutes);
    }
}

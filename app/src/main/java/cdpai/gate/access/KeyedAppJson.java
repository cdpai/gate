package cdpai.gate.access;

import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// The on-disk shape of `keyed-apps.json`: a plain array, instants as epoch millis.
final class KeyedAppJson {

    static List<KeyedApp> load(ObjectMapper m, Path file) {
        if (!Files.exists(file)) return List.of();
        try {
            var out = new ArrayList<KeyedApp>();
            for (var n : m.readTree(Files.readString(file))) {
                out.add(new KeyedApp(n.get("id").asText(), n.get("app").asText(), n.get("publicKey").asText(),
                    n.get("fingerprint").asText(), new Scope(list(n, "domains"), list(n, "profiles"), null),
                    Instant.ofEpochMilli(n.get("approvedAt").asLong()), Instant.ofEpochMilli(n.get("expiresAt").asLong()),
                    n.hasNonNull("lastSeenImagePath") ? n.get("lastSeenImagePath").asText() : null,
                    n.hasNonNull("lastSeenPid") ? n.get("lastSeenPid").asLong() : null, n.path("flagged").asBoolean(false)));
            }
            return out;
        } catch (Exception e) { throw new IllegalStateException("cannot read " + file, e); }
    }

    static void save(ObjectMapper m, Path file, List<KeyedApp> apps) {
        try {
            Files.createDirectories(file.getParent());
            var arr = m.createArrayNode();
            for (var a : apps) arr.add(node(m, a));
            Files.writeString(file, m.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
        } catch (Exception e) { throw new IllegalStateException("cannot write " + file, e); }
    }

    static ObjectNode node(ObjectMapper m, KeyedApp a) {
        var n = m.createObjectNode().put("id", a.id()).put("app", a.app()).put("publicKey", a.publicKey())
            .put("fingerprint", a.fingerprint()).put("approvedAt", a.approvedAt().toEpochMilli())
            .put("expiresAt", a.expiresAt().toEpochMilli()).put("flagged", a.flagged());
        if (a.lastSeenImagePath() != null) n.put("lastSeenImagePath", a.lastSeenImagePath());
        if (a.lastSeenPid() != null) n.put("lastSeenPid", a.lastSeenPid());
        if (a.scope().domains() != null) a.scope().domains().forEach(n.putArray("domains")::add);
        if (a.scope().profiles() != null) a.scope().profiles().forEach(n.putArray("profiles")::add);
        return n;
    }

    static List<String> list(JsonNode n, String field) {
        if (!n.hasNonNull(field)) return null;
        var out = new ArrayList<String>();
        n.get(field).forEach(e -> out.add(e.asText()));
        return out;
    }

    private KeyedAppJson() {}
}

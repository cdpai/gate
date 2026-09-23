package cdpai.gate.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/// The first frame of a connection. A v2 client opens with
/// `{"gate":{"v":2,"op":"connect","app":..,"domains":[..],"profiles":[..],"minutes":N,"publicKey":..}}`
/// or `{"gate":{"v":2,"op":"profiles"}}`, and hears a `{"gate":{..}}` reply before any CDP flows.
/// A first frame that is plain CDP is a legacy client: attested, unscoped, no gate reply.
/// Every field but `op` is optional; an absent list means "not narrowed", an absent `minutes`
/// means "not specified" and is shown to the human as exactly that.
public record GateHandshake(String op, String app, List<String> domains, List<String> profiles,
                            Integer minutes, String publicKey) {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    public static Optional<GateHandshake> extract(String frame) {
        JsonNode g;
        try { g = MAPPER.readTree(frame).path("gate"); } catch (Exception e) { return Optional.empty(); }
        if (!g.isObject() || !g.has("op")) return Optional.empty();
        return Optional.of(new GateHandshake(g.get("op").asText(), g.path("app").asText(null),
            strings(g, "domains"), strings(g, "profiles"),
            g.hasNonNull("minutes") ? g.get("minutes").asInt() : null, g.path("publicKey").asText(null)));
    }

    public boolean keyed() { return publicKey != null && !publicKey.isBlank(); }

    static List<String> strings(JsonNode obj, String field) {
        if (!obj.hasNonNull(field) || !obj.get(field).isArray()) return null;
        var out = new ArrayList<String>();
        for (var e : obj.get(field)) if (!e.asText().isBlank()) out.add(e.asText().trim());
        return out;
    }
}

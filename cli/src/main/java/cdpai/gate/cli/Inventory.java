package cdpai.gate.cli;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/// The list of browser automations (scripts, apps, throwaway tools) people have already made, so the next session can reuse them.
/// One JSON file under the user's home; an entry is a main path, optional related paths, a description and how often it was run.
/// Being listed gives a program no trust: it is new code like any other.
final class Inventory {

    static final Path FILE = Path.of(System.getProperty("user.home"), "cdpai", "gate", "inventory.json");

    final ArrayNode entries;

    Inventory(ArrayNode entries) { this.entries = entries; }

    static Inventory load() throws IOException {
        return new Inventory(Files.exists(FILE) ? (ArrayNode) BrowserAdmin.M.readTree(FILE.toFile()) : BrowserAdmin.M.createArrayNode());
    }

    void save() throws IOException {
        Files.createDirectories(FILE.getParent());
        var tmp = FILE.resolveSibling("inventory.json.tmp");
        BrowserAdmin.M.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), entries);
        Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static String normal(String path) { return Path.of(path).toAbsolutePath().normalize().toString(); }

    ObjectNode find(String path) {
        for (var e : entries) if (e.path("path").asText().equalsIgnoreCase(path)) return (ObjectNode) e;
        return null;
    }

    ObjectNode upsert(String path, String description, java.util.List<String> also) {
        var e = find(path);
        if (e == null) {
            e = BrowserAdmin.M.createObjectNode().put("path", path).put("description", "").put("added", Instant.now().toString()).put("runs", 0).put("lastRun", "");
            e.set("also", BrowserAdmin.M.createArrayNode());
            entries.add(e);
        }
        if (description != null) e.put("description", description);
        if (also != null) { var a = BrowserAdmin.M.createArrayNode(); also.forEach(p -> a.add(normal(p))); e.set("also", a); }
        return e;
    }

    ObjectNode ran(String path) {
        var e = find(path);
        if (e == null) return null;
        e.put("runs", e.path("runs").asInt() + 1).put("lastRun", Instant.now().toString());
        return e;
    }

    static ObjectNode row(JsonNode e) {
        var also = new ArrayList<String>();
        e.path("also").forEach(p -> also.add(p.asText()));
        return BrowserAdmin.M.createObjectNode().put("runs", e.path("runs").asInt()).put("lastRun", e.path("lastRun").asText()).put("path", e.path("path").asText())
            .put("description", e.path("description").asText()).put("also", String.join(";", also));
    }

    ArrayNode search(String match, int limit) {
        var hits = new ArrayList<JsonNode>();
        for (var e : entries) if (match == null || (e.path("path").asText() + " " + e.path("description").asText() + " " + e.path("also").toString()).toLowerCase().contains(match.toLowerCase())) hits.add(e);
        hits.sort((x, y) -> x.path("runs").asInt() != y.path("runs").asInt() ? y.path("runs").asInt() - x.path("runs").asInt() : y.path("lastRun").asText().compareTo(x.path("lastRun").asText()));
        var out = BrowserAdmin.M.createArrayNode();
        hits.stream().limit(limit).forEach(out::add);
        return out;
    }
}

package cdpai.gate.cli;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import cdpai.gate.client.GateClient;
import cdpai.gate.client.GateDeniedException;

/// Every approval in force right now, as the tray's access window shows it. Needs no approval and
/// exposes nothing secret: keyed apps are listed by key fingerprint only.
@Command(name = "grants", description = "List the approvals in force now (no approval needed).")
final class GrantsCommand implements Callable<Integer> {

    @Option(names = "--pipe", defaultValue = GateClient.PIPE_NAME) String pipe;
    @Option(names = "--json", description = "Print cdpgate's reply as JSON") boolean json;

    @Override public Integer call() {
        try {
            var r = GateClient.grants(pipe);
            if (json) { System.out.println(r); return 0; }
            System.out.println("mode\twho\tanchor/key\tprofiles\tdomains\tleft\tlive");
            for (var g : r.path("grants")) {
                var left = Duration.between(Instant.now(), Instant.parse(g.path("expiresAt").asText()));
                var who = g.path("mode").asText().equals("keyed") ? g.path("app").asText() : name(g.path("client").asText());
                var how = g.path("mode").asText().equals("keyed") ? "key " + g.path("key").asText() : name(g.path("anchor").asText()) + " (" + g.path("anchorPid").asText() + ")";
                System.out.println(g.path("mode").asText() + "\t" + who + "\t" + how + "\t" + list(g.path("profiles"), "all") + "\t"
                    + list(g.path("domains"), "all") + "\t" + left.toMinutes() + "m\t" + g.path("live").asInt());
            }
            return 0;
        } catch (GateDeniedException e) {
            System.err.println("cdpg: " + e.getMessage());
            return 3;
        }
    }

    static String name(String path) { var i = path.lastIndexOf('\\'); return i < 0 ? path : path.substring(i + 1); }

    static String list(com.fasterxml.jackson.databind.JsonNode arr, String none) {
        if (arr.isMissingNode()) return none;
        var out = new java.util.ArrayList<String>();
        arr.forEach(x -> out.add(x.asText()));
        return String.join(",", out);
    }
}

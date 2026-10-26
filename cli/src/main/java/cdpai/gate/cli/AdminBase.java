package cdpai.gate.cli;

import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import cdpai.gate.client.GateDeniedException;

/// What every `cdpg admin` verb shares: approval, a watchdog so a stuck browser cannot hang the command,
/// and output as TSV (compact for an agent's context) or `--json`. A verb returns an array of flat rows.
abstract class AdminBase implements Callable<Integer> {

    @Mixin GateOptions gate;
    @Option(names = "--timeout", defaultValue = "20000", description = "ms to wait for the browser (default: ${DEFAULT-VALUE})") long timeoutMs;
    @Option(names = "--json", description = "Print the rows as a JSON array instead of TSV") boolean json;

    abstract JsonNode run(BrowserAdmin admin) throws Exception;

    @Override public Integer call() throws Exception {
        try (var client = gate.connect()) {
            System.err.println("cdpg: approved " + client.grant());
            var watchdog = Thread.ofVirtual().start(() -> {
                try { Thread.sleep(timeoutMs); } catch (InterruptedException e) { return; }
                System.err.println("cdpg: no reply within " + timeoutMs + " ms");
                System.exit(2);
            });
            try (var admin = new BrowserAdmin(client)) {
                print(run(admin));
                watchdog.interrupt();
                return 0;
            } catch (Exception e) {
                System.err.println("cdpg: " + e.getMessage());
                return 2;
            }
        } catch (GateDeniedException e) {
            System.err.println("cdpg: " + e.getMessage());
            return 3;
        }
    }

    void print(JsonNode rows) {
        if (json || !rows.isArray()) { System.out.println(rows); return; }
        if (rows.isEmpty()) { System.out.println("(none)"); return; }
        var cols = new java.util.ArrayList<String>();
        rows.get(0).fieldNames().forEachRemaining(cols::add);
        System.out.println(String.join("\t", cols));
        for (var r : rows) System.out.println(String.join("\t", cols.stream().map(c -> r.path(c).asText().replace('\t', ' ').replace('\n', ' ')).toList()));
    }
}

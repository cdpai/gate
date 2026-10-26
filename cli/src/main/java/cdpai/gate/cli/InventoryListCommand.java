package cdpai.gate.cli;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "list", description = "Listed automations, most run first. Filter with --match to keep the output small.")
final class InventoryListCommand extends AdminBase {

    @Option(names = "--match", description = "only entries whose path, related paths or description contain this text") String match;
    @Option(names = "--limit", defaultValue = "20", description = "most entries to show (default: ${DEFAULT-VALUE})") int limit;

    @Override JsonNode run(BrowserAdmin a) throws Exception {
        var rows = BrowserAdmin.M.createArrayNode();
        for (var e : Inventory.load().search(match, limit)) rows.add(Inventory.row(e));
        return rows;
    }
}

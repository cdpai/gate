package cdpai.gate.cli;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "ran", description = "Count one more run of a listed automation, so the popular ones show first.")
final class InventoryRanCommand extends AdminBase {

    @Parameters(index = "0", description = "main path as listed") String path;

    @Override JsonNode run(BrowserAdmin a) throws Exception {
        var inv = Inventory.load();
        var e = inv.ran(Inventory.normal(path));
        if (e == null) throw new IllegalArgumentException("not in the inventory: " + path + " (cdpg inventory add first)");
        inv.save();
        return BrowserAdmin.M.createArrayNode().add(Inventory.row(e));
    }
}

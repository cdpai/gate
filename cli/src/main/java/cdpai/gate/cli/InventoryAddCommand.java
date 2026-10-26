package cdpai.gate.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "add", description = "Record an automation by its main path (a file or folder), with a short description. The same path again updates it.")
final class InventoryAddCommand extends AdminBase {

    @Parameters(index = "0", description = "main path: the script or the project folder") String path;
    @Option(names = "--description", required = true, description = "what it does, one line") String description;
    @Option(names = "--also", split = ",", description = "related files or folders") List<String> also;

    @Override JsonNode run(BrowserAdmin a) throws Exception {
        if (!Files.exists(Path.of(path))) throw new IllegalArgumentException("no such path: " + path);
        var inv = Inventory.load();
        var e = inv.upsert(Inventory.normal(path), description, also);
        inv.save();
        return BrowserAdmin.M.createArrayNode().add(Inventory.row(e));
    }
}

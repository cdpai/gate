package cdpai.gate.cli;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "extension", description = "Switch one extension on or off (`cdpg admin extensions` lists ids). Changes the browser.")
final class ExtensionCommand extends AdminBase {

    @Parameters(index = "0", description = "extension id, 32 letters a-p") String id;
    @Parameters(index = "1", description = "on or off") String state;

    @Override JsonNode run(BrowserAdmin a) throws Exception {
        if (!id.matches("[a-p]{32}")) throw new IllegalArgumentException("not an extension id: " + id);
        if (!state.equals("on") && !state.equals("off")) throw new IllegalArgumentException("say on or off");
        return a.ui("chrome.management.setEnabled('" + id + "'," + state.equals("on") + ").then(()=>chrome.management.get('" + id + "')).then(e=>[{id:e.id,enabled:e.enabled,name:e.name}])");
    }
}

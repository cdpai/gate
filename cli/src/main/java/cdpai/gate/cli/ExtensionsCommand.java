package cdpai.gate.cli;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;

@Command(name = "extensions", description = "Installed extensions, enabled ones first.")
final class ExtensionsCommand extends AdminBase {

    @Override JsonNode run(BrowserAdmin a) throws Exception {
        return a.ui("chrome.management.getAll().then(l=>l.filter(e=>e.type==='extension').sort((x,y)=>y.enabled-x.enabled).map(e=>({id:e.id,enabled:e.enabled,name:e.name,version:e.version})))");
    }
}

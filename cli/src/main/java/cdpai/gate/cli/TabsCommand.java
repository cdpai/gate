package cdpai.gate.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "tabs", description = "Every tab with how long since it was last used and whether it is awake (not hibernated), most idle first.")
final class TabsCommand extends AdminBase {

    static final String QUERY = "chrome.tabs.query({}).then(t=>t.map(x=>({id:x.id,window:x.windowId,idleMin:Math.round((Date.now()-x.lastAccessed)/60000),awake:!x.discarded,active:x.active,audible:!!x.audible,pinned:x.pinned,title:(x.title||'').slice(0,50),url:(x.url||'').slice(0,100)})))";

    @Option(names = "--idle-minutes", description = "only tabs idle at least this long") Integer idle;
    @Option(names = "--awake", description = "only tabs that are not hibernated") boolean awakeOnly;

    @Override JsonNode run(BrowserAdmin a) throws Exception { return select(a.ui(QUERY), idle, awakeOnly); }

    static ArrayNode select(JsonNode tabs, Integer idle, boolean awakeOnly) {
        var rows = new java.util.ArrayList<JsonNode>();
        for (var t : tabs) if ((idle == null || t.path("idleMin").asInt() >= idle) && (!awakeOnly || t.path("awake").asBoolean())) rows.add(t);
        rows.sort((x, y) -> y.path("idleMin").asInt() - x.path("idleMin").asInt());
        return BrowserAdmin.M.createArrayNode().addAll(rows);
    }
}

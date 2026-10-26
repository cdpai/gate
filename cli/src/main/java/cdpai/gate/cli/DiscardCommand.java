package cdpai.gate.cli;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "discard", description = "Hibernate tabs to free their RAM (they reload when clicked). Plan only unless --apply. Never touches active, pinned or audible tabs.")
final class DiscardCommand extends AdminBase {

    @Parameters(arity = "0..*", description = "tab ids (from `cdpg admin tabs`)") List<Long> tabIds;
    @Option(names = "--idle-minutes", description = "instead of ids: every awake tab idle at least this long") Integer idle;
    @Option(names = "--apply", description = "do it; without this only the plan is printed") boolean apply;

    @Override JsonNode run(BrowserAdmin a) throws Exception {
        if (tabIds == null && idle == null) throw new IllegalArgumentException("give tab ids or --idle-minutes");
        var rows = BrowserAdmin.M.createArrayNode();
        for (var t : TabsCommand.select(a.ui(TabsCommand.QUERY), idle, true)) {
            var wanted = tabIds == null || tabIds.contains(t.path("id").asLong());
            if (!wanted || t.path("active").asBoolean() || t.path("pinned").asBoolean() || t.path("audible").asBoolean()) continue;
            var row = BrowserAdmin.M.createObjectNode().put("id", t.path("id").asLong()).put("idleMin", t.path("idleMin").asInt()).put("title", t.path("title").asText());
            row.put("result", !apply ? "would discard" : a.ui("chrome.tabs.discard(" + t.path("id").asLong() + ").then(x=>x&&x.discarded?'discarded':'not discarded')").asText());
            rows.add(row);
        }
        return rows;
    }
}

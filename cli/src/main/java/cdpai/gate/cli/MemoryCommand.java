package cdpai.gate.cli;

import java.util.ArrayList;

import com.fasterxml.jackson.databind.JsonNode;

import cdpai.gate.client.OsMemory;

import picocli.CommandLine.Command;

@Command(name = "memory", description = "Every browser process (renderers, GPU, network and so on) with its RAM from the OS, biggest first, and a total. Working set is RAM now; private is committed.")
final class MemoryCommand extends AdminBase {

    @Override JsonNode run(BrowserAdmin a) throws Exception {
        var procs = a.browser("SystemInfo.getProcessInfo").path("processInfo");
        var pids = new ArrayList<Long>();
        procs.forEach(p -> pids.add(p.path("id").asLong()));
        var mem = OsMemory.of(pids);
        var rows = new ArrayList<JsonNode>();
        var ws = 0L;
        var priv = 0L;
        for (var p : procs) {
            var m = mem.getOrDefault(p.path("id").asLong(), new long[] {0, 0});
            ws += m[0];
            priv += Math.max(m[1], 0);
            rows.add(BrowserAdmin.M.createObjectNode().put("pid", p.path("id").asLong()).put("type", p.path("type").asText()).put("workingSetMB", m[0] >> 20)
                .put("privateMB", m[1] < 0 ? -1 : m[1] >> 20).put("cpuSec", Math.round(p.path("cpuTime").asDouble())));
        }
        rows.sort((x, y) -> Long.compare(y.path("workingSetMB").asLong(), x.path("workingSetMB").asLong()));
        rows.add(BrowserAdmin.M.createObjectNode().put("pid", 0).put("type", "TOTAL").put("workingSetMB", ws >> 20).put("privateMB", priv >> 20).put("cpuSec", 0));
        return BrowserAdmin.M.createArrayNode().addAll(rows);
    }
}

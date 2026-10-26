package cdpai.gate.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/// Memory of processes by pid, which CDP does not report: {working set, private bytes} in bytes, -1 where the OS gives no figure.
public final class OsMemory {

    public static Map<Long, long[]> of(Collection<Long> pids) throws Exception {
        var list = pids.stream().map(String::valueOf).collect(Collectors.joining(","));
        var win = System.getProperty("os.name").toLowerCase().contains("win");
        var cmd = win ? new String[] {"powershell", "-NoProfile", "-Command", "Get-Process -Id " + list + " -ErrorAction SilentlyContinue | % { [string]$_.Id + ' ' + $_.WorkingSet64 + ' ' + $_.PrivateMemorySize64 }"}
            : new String[] {"ps", "-o", "pid=,rss=", "-p", list};
        var p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        var out = new HashMap<Long, long[]>();
        for (var line : new String(p.getInputStream().readAllBytes()).split("\\R")) {
            var f = line.trim().split("\\s+");
            if (f.length < 2 || !f[0].matches("\\d+")) continue;
            out.put(Long.parseLong(f[0]), win ? new long[] {Long.parseLong(f[1]), Long.parseLong(f[2])} : new long[] {Long.parseLong(f[1]) * 1024, -1});
        }
        return out;
    }
}

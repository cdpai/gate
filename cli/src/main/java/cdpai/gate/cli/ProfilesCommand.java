package cdpai.gate.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import cdpai.gate.client.GateClient;
import cdpai.gate.client.GateDeniedException;

/// The browser's profiles: folder, name, and whether each is loaded now. Needs no approval -- this
/// is what a consumer reads to decide what to ask for.
@Command(name = "profiles", description = "List the browser's profiles (no approval needed).")
final class ProfilesCommand implements Callable<Integer> {

    @Option(names = "--pipe", defaultValue = GateClient.PIPE_NAME) String pipe;
    @Option(names = "--json", description = "Print cdpgate's reply as JSON") boolean json;

    @Override public Integer call() {
        try {
            var r = GateClient.profiles(pipe);
            if (json) { System.out.println(r); return 0; }
            System.out.println(r.path("browser").asText() + (r.path("running").asBoolean() ? " running" : " NOT running under cdpgate"));
            System.out.println("dir\tname\tloaded");
            for (var p : r.path("profiles"))
                System.out.println(p.path("dir").asText() + "\t" + p.path("name").asText() + "\t"
                    + (p.path("loaded").asBoolean() ? (p.path("inferred").asBoolean() ? "yes (inferred)" : "yes") : "no"));
            return 0;
        } catch (GateDeniedException e) {
            System.err.println("cdpg: " + e.getMessage());
            return 3;
        }
    }
}

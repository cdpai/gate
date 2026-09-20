package cdpai.gate.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import cdpai.gate.client.GateClient;

/// Streams every frame the gate routes to this connection -- browser-level events only, since
/// this connection never attaches to a target -- one JSON object per line, until disconnected or
/// killed. For watching what the browser is doing without asking it anything.
@Command(name = "listen", description = "Stream browser-level CDP events to stdout until disconnected.")
final class ListenCommand implements Callable<Integer> {

    @Option(names = "--pipe", defaultValue = GateClient.PIPE_NAME) String pipe;

    @Override public Integer call() throws Exception {
        try (var client = new GateClient(pipe)) {
            String raw;
            while ((raw = client.receive()) != null) System.out.println(raw);
        }
        return 0;
    }
}

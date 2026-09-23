package cdpai.gate.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import cdpai.gate.client.GateDeniedException;

/// Streams the browser-level events this connection's scope lets it see -- targets appearing,
/// changing, going -- one JSON object per line, until disconnected or killed.
@Command(name = "listen", description = "Stream the target events within your scope to stdout.")
final class ListenCommand implements Callable<Integer> {

    @Mixin GateOptions gate;

    @Override public Integer call() {
        try (var client = gate.connect()) {
            System.err.println("cdpg: approved " + client.grant());
            client.send("{\"id\":1,\"method\":\"Target.setDiscoverTargets\",\"params\":{\"discover\":true}}");
            String raw;
            while ((raw = client.receive()) != null) System.out.println(raw);
            return 0;
        } catch (GateDeniedException e) {
            System.err.println("cdpg: " + e.getMessage());
            return 3;
        } catch (RuntimeException e) {
            System.err.println("cdpg: disconnected: " + e.getMessage());
            return 2;
        }
    }
}

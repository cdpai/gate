package cdpai.gate.cli;

import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import cdpai.gate.client.GateDeniedException;

/// The start-of-session ritual: ask for approval once, touch nothing, print what was granted.
/// Run it at the top of an agent session with the scope the session will need; the human anchors
/// it at the agent, and every later cdpg call from that session passes without a window.
@Command(name = "approve", description = {"Ask for approval now, without touching the browser, and print the grant.",
    "Run once at the start of an agent session: cdpg approve --profiles Work --domains youtube.com --minutes 120"})
final class ApproveCommand implements Callable<Integer> {

    @Mixin GateOptions gate;

    @Override public Integer call() {
        try (var client = gate.connect()) {
            System.out.println(client.grant());
            return 0;
        } catch (GateDeniedException e) {
            System.err.println("cdpg: " + e.getMessage());
            return 3;
        }
    }
}

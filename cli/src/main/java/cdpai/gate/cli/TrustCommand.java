package cdpai.gate.cli;

import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import cdpai.gate.client.GateClient;
import cdpai.gate.client.GateDeniedException;
import cdpai.gate.client.ScopeRequest;

/// Long trust for an agent session: the human types the passphrase once and the session -- and every
/// program it starts, cdpg, netrec, a java script -- passes for hours. CdpScope is optional; the window's
/// colour (green, amber, red) shows the human how much a wide request hands over.
@Command(name = "trust", description = {"Ask for a long approval (4 hours to 2 days) for this agent session and everything it starts.",
    "The human approves with the passphrase. With no --pid, the nearest agent process above this one (such as claude.exe) is trusted.",
    "  cdpg trust --hours 24 --profiles Work --domains facebook.com     narrow: shown green",
    "  cdpg trust --hours 48                                           every profile, every website: shown red"})
final class TrustCommand implements Callable<Integer> {

    @Option(names = "--pipe", defaultValue = GateClient.PIPE_NAME) String pipe;
    @Option(names = "--app", defaultValue = "cdpg trust", description = "Name shown to the human (default: ${DEFAULT-VALUE})") String app;
    @Option(names = "--pid", description = "An ancestor of this process to trust; default: the nearest agent session") Long pid;
    @Option(names = "--hours", description = "How long you ask for, 4 to 48; the human picks the final length") Integer hours;
    @Option(names = "--profiles", split = ",", description = "Profiles by name or folder; omit for every profile") List<String> profiles;
    @Option(names = "--domains", split = ",", description = "Plain hosts, subdomains included; omit for every website") List<String> domains;

    @Override public Integer call() {
        try {
            System.out.println(GateClient.trust(pipe, app, pid, new ScopeRequest(domains, profiles, hours == null ? null : hours * 60)));
            return 0;
        } catch (GateDeniedException e) {
            System.err.println("cdpg: " + e.getMessage());
            return 3;
        }
    }
}

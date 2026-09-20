package cdpai.gate.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/// cdpg -- gated CDP access from the command line: no curl, no port. Connects to cdpgate's named
/// pipe via cdpai-gate-client and speaks CDP JSON over it. Built for claude-code and other agents
/// that used to reach CDP over a bare loopback port; that port no longer exists, this is what
/// replaces it (per the design doc: "a dedicated CLI, cdpg.exe, gives claude-code gated CDP
/// access without curl and without a port").
@Command(name = "cdpg", mixinStandardHelpOptions = true, version = "cdpg 0.1",
    description = "Gated CDP access via cdpgate's named pipe.",
    subcommands = {SendCommand.class, ListenCommand.class})
public final class Main implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override public void run() {
        new CommandLine(this).usage(System.out);
    }
}

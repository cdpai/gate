package cdpai.gate.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/// cdpg: gated CDP from the command line, for agents and people -- no curl, no port. The browser's
/// CDP is reachable only through cdpgate's pipe, and every connection is approved by a human.
@Command(name = "cdpg", mixinStandardHelpOptions = true, version = "cdpg 0.2",
    description = {"Gated CDP access through cdpgate's pipe.",
        "Start with `cdpg profiles`, then ask for only what you need: cdpg send Target.getTargets --profiles Work --domains youtube.com"},
    subcommands = {ProfilesCommand.class, SendCommand.class, ListenCommand.class})
public final class Main implements Runnable {

    public static void main(String[] args) { System.exit(new CommandLine(new Main()).execute(args)); }

    @Override public void run() { new CommandLine(this).usage(System.out); }
}

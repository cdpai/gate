package cdpai.gate.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/// cdpg: gated CDP from the command line, for agents and people -- no curl, no port. The browser's
/// CDP is reachable only through cdpgate's pipe, and every connection is approved by a human.
@Command(name = "cdpg", mixinStandardHelpOptions = true, version = "cdpg 0.2",
    description = {"Gated CDP access through cdpgate's pipe.",
        "Start with `cdpg profiles`, then ask for only what you need: cdpg send Target.getTargets --profiles Work --domains youtube.com"},
    subcommands = {ProfilesCommand.class, GrantsCommand.class, ApproveCommand.class, SendCommand.class, ListenCommand.class},
    footerHeading = "%nFor an agent session:%n",
    footer = {
        "  cdpgate owns the browser over a pipe; there is no debug port, so curl to localhost:<port> will not work.",
        "  1. cdpg profiles                                        names of profiles (no approval)",
        "  2. cdpg approve --profiles Work --domains youtube.com --minutes 120",
        "                                                          once, at the start: a human approves in a window,",
        "                                                          anchored at the agent; later calls pass silently",
        "  3. cdpg send Target.getTargets --profiles Work --domains youtube.com     the tabs you may use",
        "  4. cdpg send Runtime.evaluate '{\"expression\":\"document.title\"}' --target <tabId> --profiles ... --domains ...",
        "  Cookies: a profile has ONE jar for all sites, so an approval reaches every cookie in its profile(s);",
        "  --domains limits which tabs you see and drive. Read cookies through a tab of the profile:",
        "    cdpg send Network.getAllCookies --target <tabId> --profiles Work --domains youtube.com",
        "  (Storage.getCookies reads whichever profile was used last, and is refused when that is not yours.)",
        "  Background tabs Vivaldi has not loaded yet (it restores them lazily) or has frozen attach but never answer;",
        "  use a visible tab, or send Target.activateTarget first (it brings the tab forward). --timeout ends a stuck call.",
        "  cdpg grants lists what is approved now (no approval). Exit 3 = refused or denied; the message says why."})
public final class Main implements Runnable {

    public static void main(String[] args) { System.exit(new CommandLine(new Main()).execute(args)); }

    @Override public void run() { new CommandLine(this).usage(System.out); }
}

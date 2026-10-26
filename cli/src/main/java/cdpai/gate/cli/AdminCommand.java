package cdpai.gate.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/// Browser housekeeping that CDP alone does not answer: which tabs are idle, what memory each browser process holds,
/// hibernating tabs and switching extensions off, to relieve RAM. Vivaldi only (it needs Vivaldi's UI page).
@Command(name = "admin", mixinStandardHelpOptions = true, description = {"Browser housekeeping for RAM: tabs by idle time, memory by process, hibernate tabs, extensions on/off (Vivaldi).",
        "Always pass --profiles (cdpg profiles). Reads change nothing; discard and extension change the browser, and discard shows a plan until --apply."},
    subcommands = {TabsCommand.class, MemoryCommand.class, DiscardCommand.class, ExtensionsCommand.class, ExtensionCommand.class},
    footerHeading = "%nTypical use:%n",
    footer = {
        "  cdpg admin memory --profiles Work                    every browser process with RAM, biggest first",
        "  cdpg admin tabs --idle-minutes 120 --profiles Work   tabs untouched for 2 hours",
        "  cdpg admin discard --idle-minutes 120 --profiles Work           plan only; add --apply to hibernate them",
        "  cdpg admin extensions --profiles Work                installed extensions and whether each is enabled",
        "  cdpg admin extension <id> off --profiles Work        switch one off (on to switch back)",
        "  Active, pinned and audible tabs are never discarded. Output is TSV; --json for JSON.",
        "  Tab ids here are the browser's (chrome.tabs), not CDP targetIds; match on url or title."})
final class AdminCommand implements Runnable {

    @Override public void run() { new CommandLine(this).usage(System.out); }
}

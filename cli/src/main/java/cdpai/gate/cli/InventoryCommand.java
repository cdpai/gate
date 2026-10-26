package cdpai.gate.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/// What browser automations already exist, so a session can reuse them instead of writing another.
@Command(name = "inventory", mixinStandardHelpOptions = true, description = {"The list of browser automations (scripts, apps) already made, with how often each was run.",
        "Look here before writing a new one; record yours when you finish. Being listed is no approval: the code is treated as new every time.",
        "Needs the same cdpgate approval as other commands (pass --profiles); the list itself is a file, ~/cdpai/gate/inventory.json."},
    subcommands = {InventoryListCommand.class, InventoryAddCommand.class, InventoryRanCommand.class},
    footerHeading = "%nTypical use:%n",
    footer = {
        "  cdpg inventory list --match youtube --profiles Work       most used first; --limit N (default 20); --json",
        "  cdpg inventory add <main path> --description \"what it does\" [--also <path>...] --profiles Work     add, or update the same path",
        "  cdpg inventory ran <main path> --profiles Work            count one more run (call it when you run a listed script)"})
final class InventoryCommand implements Runnable {

    @Override public void run() { new CommandLine(this).usage(System.out); }
}

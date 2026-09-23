package cdpai.gate.access;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Distance from the requester, counted in meaningful layers rather than raw processes. A run of
/// shells and shell helpers in a row -- bash wrapping bash wrapping timeout, as an agent's command
/// runner produces under Git Bash -- is one layer of indirection, not five, so it counts once.
/// Otherwise an agent session sits six hops up only because of how its commands are run, and the
/// distance cap would bind its approval to half an hour for no real breadth.
public final class Hops {

    static final Set<String> SHELLISH = Set.of("bash.exe", "sh.exe", "zsh.exe", "dash.exe", "timeout.exe", "env.exe",
        "nice.exe", "nohup.exe", "cmd.exe", "powershell.exe", "pwsh.exe", "conhost.exe");

    /// Layers between chain[0] and chain[hop], a run of shells counting as one.
    public static int effective(List<AncestryNode> chain, int hop) {
        var layers = 0;
        for (var i = 1; i <= hop && i < chain.size(); i++)
            if (!(shellish(chain.get(i)) && shellish(chain.get(i - 1)))) layers++;
        return layers;
    }

    static boolean shellish(AncestryNode n) { return SHELLISH.contains(n.imageName().toLowerCase(Locale.ROOT)); }

    private Hops() {}
}

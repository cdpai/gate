package cdpai.gate.access;

import java.util.List;

/// Which ancestor `cdpg trust` asks for. Only an ancestor of the requester can be named: a process
/// can ask to trust what started it, never an unrelated one. The desktop shell and a terminal host
/// are refused, because trusting them trusts every program on the desktop or in every tab. An agent
/// session is accepted even when it looks like a terminal host because several of its commands are
/// running as shells at that moment.
public final class TrustTarget {

    public sealed interface Pick permits Found, Refused {}
    public record Found(AncestryNode node) implements Pick {}
    public record Refused(String error, String hint) implements Pick {}

    public static Pick pick(List<AncestryNode> chain, ProcessTree tree, Long pid) {
        for (var hop = 1; hop < chain.size(); hop++) {
            var n = chain.get(hop);
            var agent = KnownPrograms.isAgent(n);
            if (pid == null ? !agent : n.pid() != pid) continue;
            var category = CategoryClassifier.classify(n, hop, chain.size(), tree);
            if (!agent && (category == AnchorCategory.SESSION_ROOT || category == AnchorCategory.TERMINAL_HOST))
                return new Refused(n.imageName() + " (" + n.pid() + ") is the desktop or a terminal host",
                    "trusting it would trust every program it hosts; name the agent or app process instead");
            if (n.startInstant().isEmpty()) return new Refused("cannot read the start time of " + n.pid(), "try again");
            return new Found(n);
        }
        var above = String.join(" < ", chain.stream().map(n -> n.imageName() + ":" + n.pid()).toList());
        return pid == null
            ? new Refused("no agent session (such as claude.exe) above this process", "pass --pid with one of: " + above)
            : new Refused("pid " + pid + " is not an ancestor of this process", "a process can only ask to trust what started it: " + above);
    }

    private TrustTarget() {}
}

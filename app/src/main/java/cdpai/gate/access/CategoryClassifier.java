package cdpai.gate.access;

import static cdpai.gate.access.KnownPrograms.*;

/// Classifies one ancestry level into an AnchorCategory. Structural signals decide first --
/// whether this is the root, whether its own children are themselves shells -- with the known-
/// program list as a refinement on top, exactly as the design doc specifies. Getting this wrong
/// for an unfamiliar program costs a grant that is shorter or longer than ideal, not a broken
/// model, so this stays a small heuristic rather than a large one.
public final class CategoryClassifier {

    /// `hop`/`chainSize` locate this node within its own ancestry chain -- needed to tell apart
    /// two things `!hasParent` alone cannot distinguish: genuinely reaching the top of a resolvable
    /// chain (several hops climbed, then the trail runs out -- almost always the real desktop
    /// shell) versus a chain of exactly one node, where even the CLIENT's own immediate parent
    /// could not be resolved. The second case is a real, observed race (a launcher that exits
    /// right after spawning its child -- e.g. a detached `Start-Process`, common from automation --
    /// beats cdpgate to the punch even though `ProcessTree.rawChain` is already called as early as
    /// possible), not evidence the client IS the session root. Classifying it as SESSION_ROOT
    /// there made the connection permanently unapprovable (0-minute cap, no other row exists to
    /// pick instead) -- found live, 2026-09-22, testing a one-shot `cdpg send` launched via a
    /// detached PowerShell `Start-Process`. Treating an isolated, unnamed node as an ordinary
    /// process instead degrades to "approvable but may re-prompt on the next invocation" rather
    /// than "cannot be approved at all" -- a real regression is a dead end, not a warning.
    public static AnchorCategory classify(AncestryNode node, int hop, int chainSize, ProcessTree tree) {
        var name = node.imageName();
        var hasParent = hop + 1 < chainSize;
        var isolatedNode = hop == 0 && chainSize == 1;

        if (SESSION_ROOTS.contains(name)) return AnchorCategory.SESSION_ROOT;
        if (!hasParent && !isolatedNode) return AnchorCategory.SESSION_ROOT;

        if (TERMINAL_HOSTS.contains(name) || childrenAreMostlyShells(node.pid(), tree))
            return AnchorCategory.TERMINAL_HOST;

        if (SHELLS.contains(name)) return AnchorCategory.SHELL_OR_TAB;

        if (AGENTS.contains(name)) return AnchorCategory.AGENT_SESSION;

        return AnchorCategory.ORDINARY_PROCESS;
    }

    /// Requires at least three shell children, not just a majority of two -- a shell wrapping a
    /// single nested interactive shell (a common, harmless pattern, e.g. a login shell spawning
    /// one interactive one) must not read as a multiplexer. A real terminal host hosting several
    /// independent tabs looks structurally different: several sibling shells, not one in a chain.
    static boolean childrenAreMostlyShells(long pid, ProcessTree tree) {
        var children = tree.childImageNames(pid);
        if (children.size() < 3) return false;
        var shellChildren = children.stream().filter(SHELLS::contains).count();
        return shellChildren * 2 >= children.size();
    }

    private CategoryClassifier() {}
}

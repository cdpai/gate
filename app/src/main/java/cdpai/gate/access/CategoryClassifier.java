package cdpai.gate.access;

import static cdpai.gate.access.KnownPrograms.*;

/// Classifies one ancestry level into an AnchorCategory. Structural signals decide first --
/// whether this is the root, whether its own children are themselves shells -- with the known-
/// program list as a refinement on top, exactly as the design doc specifies. Getting this wrong
/// for an unfamiliar program costs a grant that is shorter or longer than ideal, not a broken
/// model, so this stays a small heuristic rather than a large one.
public final class CategoryClassifier {

    public static AnchorCategory classify(AncestryNode node, boolean hasParent, ProcessTree tree) {
        var name = node.imageName();

        if (!hasParent || SESSION_ROOTS.contains(name)) return AnchorCategory.SESSION_ROOT;

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

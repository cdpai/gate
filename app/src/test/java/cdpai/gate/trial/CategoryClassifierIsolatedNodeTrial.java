package cdpai.gate.trial;

import java.time.Instant;
import java.util.Optional;

import cdpai.gate.access.AnchorCategory;
import cdpai.gate.access.AncestryNode;
import cdpai.gate.access.CategoryClassifier;
import cdpai.gate.access.ProcessTree;

/// Pure logic proof for the isolated-node fix (2026-09-22): a chain of exactly one node (the
/// client's own immediate parent could not be resolved at all -- a real race with a launcher that
/// exits right after spawning its child, found live testing a `cdpg send` launched via a detached
/// PowerShell Start-Process) must NOT be classified as SESSION_ROOT, which made the connection
/// permanently unapprovable (0-minute cap, no other row to pick instead). A real chain that
/// genuinely climbs several hops before running out of resolvable parents still must.
/// java -cp <classes> cdpai.gate.trial.CategoryClassifierIsolatedNodeTrial
public class CategoryClassifierIsolatedNodeTrial {
    public static void main(String[] args) {
        var tree = ProcessTree.snapshot(); // real, read-only, harmless -- only used for the
                                            // (always-false-here) childrenAreMostlyShells check
        var fakePid = 999_999_999L; // does not exist -- no real children, so structural checks are inert

        var isolatedUnknown = new AncestryNode(fakePid, "C:\\some\\cdpg.exe", Optional.of(Instant.now()), 0);
        check("an isolated (chain-of-one) node with an unrecognized name is NOT session-root",
            CategoryClassifier.classify(isolatedUnknown, 0, 1, tree) != AnchorCategory.SESSION_ROOT);
        check("...it classifies as an ordinary process instead (approvable, not a dead end)",
            CategoryClassifier.classify(isolatedUnknown, 0, 1, tree) == AnchorCategory.ORDINARY_PROCESS);

        var isolatedShell = new AncestryNode(fakePid, "C:\\Windows\\System32\\cmd.exe", Optional.of(Instant.now()), 0);
        check("an isolated node whose NAME matches a known shell still classifies structurally (SHELL_OR_TAB)",
            CategoryClassifier.classify(isolatedShell, 0, 1, tree) == AnchorCategory.SHELL_OR_TAB);

        var namedRoot = new AncestryNode(fakePid, "C:\\Windows\\explorer.exe", Optional.of(Instant.now()), 0);
        check("a node NAMED explorer.exe is still session-root regardless of chain shape",
            CategoryClassifier.classify(namedRoot, 0, 1, tree) == AnchorCategory.SESSION_ROOT);
        check("...even mid-chain, with a resolvable parent above it (name wins over hasParent)",
            CategoryClassifier.classify(namedRoot, 2, 5, tree) == AnchorCategory.SESSION_ROOT);

        var toppedOutMidChain = new AncestryNode(fakePid, "C:\\some\\unknown-top.exe", Optional.of(Instant.now()), 0);
        check("a node that is NOT the sole node, but the trail genuinely ran out above it, is STILL session-root"
            + " (the original defensive behaviour, unchanged for a real multi-hop chain)",
            CategoryClassifier.classify(toppedOutMidChain, 3, 4, tree) == AnchorCategory.SESSION_ROOT);

        System.out.println("ALL CHECKS PASSED");
    }

    static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError("FAILED: " + label);
        System.out.println("ok - " + label);
    }
}

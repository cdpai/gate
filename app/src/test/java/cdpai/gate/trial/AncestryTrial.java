package cdpai.gate.trial;

import cdpai.gate.access.*;

/// Manual proof that the ancestry/category/duration-cap logic produces sane numbers against the
/// REAL live process tree of whatever shell/agent/terminal this is run from -- read-only, touches
/// nothing. Run it from different depths (a bare terminal tab vs. inside claude-code vs. inside
/// claude-code driving another claude-code) to see the classification and caps actually shift.
/// java -cp <classes> cdpai.gate.trial.AncestryTrial
public class AncestryTrial {
    public static void main(String[] args) {
        var tree = ProcessTree.snapshot();
        var chain = tree.chainFrom(ProcessHandle.current().pid());

        System.out.printf("%-6s %-28s %-9s %-16s %-8s %s%n",
            "HOP", "IMAGE", "DESCEND", "CATEGORY", "CAP(min)", "BOUND BY");
        System.out.println("-".repeat(100));

        for (var hop = 0; hop < chain.size(); hop++) {
            var node = chain.get(hop);
            var category = CategoryClassifier.classify(node, hop, chain.size(), tree);
            var cap = DurationCaps.compute(category, hop, node.descendantCount());
            System.out.printf("%-6d %-28s %-9d %-16s %-8d %s%n",
                hop, node.imageName(), node.descendantCount(), category, cap.minutes(), cap.boundBy());
        }
    }
}

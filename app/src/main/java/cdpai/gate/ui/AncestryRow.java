package cdpai.gate.ui;

import java.util.ArrayList;
import java.util.List;

import cdpai.gate.access.*;

/// One row of the ancestry table the approval window shows: the hop's classification and cap,
/// computed once so the UI and the eventual grant agree on exactly the same numbers.
record AncestryRow(int hop, AncestryNode node, AnchorCategory category, DurationCaps.Result cap) {

    boolean selectable() {
        return category != AnchorCategory.SESSION_ROOT && category != AnchorCategory.TERMINAL_HOST
            && cap.minutes() > 0;
    }

    static List<AncestryRow> build(List<AncestryNode> chain, ProcessTree tree, Scope requestedScope) {
        var rows = new ArrayList<AncestryRow>();
        for (var hop = 0; hop < chain.size(); hop++) {
            var node = chain.get(hop);
            var category = CategoryClassifier.classify(node, hop, chain.size(), tree);
            var cap = DurationCaps.compute(category, hop, node.descendantCount(), requestedScope);
            rows.add(new AncestryRow(hop, node, category, cap));
        }
        return rows;
    }
}

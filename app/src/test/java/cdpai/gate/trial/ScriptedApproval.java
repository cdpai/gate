package cdpai.gate.trial;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import cdpai.gate.access.*;
import cdpai.gate.ui.DurationChips;

/// Stands in for the human in trials: approves the way the window would by default -- the
/// suggested anchor, else the broadest selectable one; the preselected duration -- and records
/// every request so a trial can assert how many times a human would have been asked. `deny`
/// makes it refuse everything.
public final class ScriptedApproval implements Approval {

    public final List<Request> asked = new CopyOnWriteArrayList<>();
    public volatile boolean deny;

    @Override public Optional<Decision> decide(Request r) {
        asked.add(r);
        if (deny) return Optional.empty();
        if (r.kind() == Kind.KEYED) {
            var cap = KeyedAppStore.maxMinutes(r.requested());
            return Optional.of(new Decision(null, DurationChips.preselect(DurationChips.KEYED, cap, r.requested().requestedMinutes()), cap, r.requested()));
        }
        var hop = AnchorSuggestion.suggest(r.chain(), r.tree());
        if (hop < 0) hop = broadestSelectable(r);
        if (hop < 0) return Optional.empty();
        var node = r.chain().get(hop);
        var cap = DurationCaps.compute(CategoryClassifier.classify(node, hop, r.chain().size(), r.tree()), hop, node.descendantCount(), r.requested());
        return Optional.of(new Decision(node, DurationChips.preselect(DurationChips.ATTESTED, cap.minutes(), r.requested().requestedMinutes()),
            cap.minutes(), r.requested()));
    }

    static int broadestSelectable(Request r) {
        var best = -1;
        for (var hop = 0; hop < r.chain().size(); hop++) {
            var n = r.chain().get(hop);
            var cat = CategoryClassifier.classify(n, hop, r.chain().size(), r.tree());
            if (cat == AnchorCategory.SESSION_ROOT || cat == AnchorCategory.TERMINAL_HOST) continue;
            if (DurationCaps.compute(cat, hop, n.descendantCount()).minutes() > 0) best = hop;
        }
        return best;
    }
}

package cdpai.gate.access;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import cdpai.gate.win.PeerIdentity;

/// Console stand-in for the approval window: prints the same ancestry/category/cap table
/// AncestryTrial proved correct, suggests an anchor, and makes a human confirm on stdin before
/// anything is granted. Never auto-approves. Refuses the hard-excluded categories exactly like
/// the real UI will, so the behaviour this gets tested against today is the real behaviour.
public final class ConsoleApproval implements Approval {

    final Scanner in = new Scanner(System.in);

    @Override public Optional<Decision> decide(PeerIdentity client, List<AncestryNode> chain, ProcessTree tree) {
        System.out.println();
        System.out.println("*** cdpgate: new connection wants CDP access ***");
        System.out.printf("client : pid %d, %s%n", client.pid(), client.imagePath());
        System.out.printf("%-6s %-28s %-9s %-16s %-8s %s%n",
            "HOP", "IMAGE", "DESCEND", "CATEGORY", "CAP(min)", "BOUND BY");

        for (var hop = 0; hop < chain.size(); hop++) {
            var node = chain.get(hop);
            var hasParent = hop + 1 < chain.size();
            var category = CategoryClassifier.classify(node, hasParent, tree);
            var cap = DurationCaps.compute(category, hop, node.descendantCount());
            System.out.printf("%-6d %-28s %-9d %-16s %-8d %s%n",
                hop, node.imageName(), node.descendantCount(), category, cap.minutes(), cap.boundBy());
        }
        var suggested = AnchorSuggestion.suggest(chain, tree);

        if (suggested < 0) {
            System.out.println("no anchor above the client looks materially longer-lived -- type a hop number by hand, or 'n' to deny");
        } else {
            System.out.println("suggested anchor: hop " + suggested + " (" + chain.get(suggested).imageName() + ")");
        }
        System.out.print("approve which hop? [" + (suggested < 0 ? "" : suggested) + " / n]: ");

        var line = in.nextLine().trim();
        if (line.equalsIgnoreCase("n")) return Optional.empty();
        var hop = line.isEmpty() ? suggested : parseHopOrDeny(line);
        if (hop < 0 || hop >= chain.size()) return Optional.empty();

        var node = chain.get(hop);
        var hasParent = hop + 1 < chain.size();
        var category = CategoryClassifier.classify(node, hasParent, tree);
        if (category == AnchorCategory.SESSION_ROOT || category == AnchorCategory.TERMINAL_HOST) {
            System.out.println("refused: " + category + " is hard-excluded, never selectable");
            return Optional.empty();
        }
        var cap = DurationCaps.compute(category, hop, node.descendantCount());
        if (cap.minutes() <= 0) {
            System.out.println("refused: computed cap is zero (" + cap.boundBy() + ")");
            return Optional.empty();
        }
        System.out.println("approved: anchor hop " + hop + ", " + cap.minutes() + " minutes (" + cap.boundBy() + ")");
        return Optional.of(new Decision(node, cap.minutes()));
    }

    static int parseHopOrDeny(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }
}

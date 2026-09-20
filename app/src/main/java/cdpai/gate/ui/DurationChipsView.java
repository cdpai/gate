package cdpai.gate.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/// The duration chip row: "choosing a broader anchor visibly removes duration options" (design
/// doc). Chips above the current cap are shown, not hidden, but struck through and unreachable by
/// keyboard -- an honest list of what exists, matching the mockup rather than pretending the
/// longer durations don't exist at all.
final class DurationChipsView {

    final HBox row = new HBox(6);
    final Label capLine = new Label();
    final Map<Integer, Label> chipNodes = new LinkedHashMap<>();
    int cap, selected;

    DurationChipsView() {
        for (var chip : DurationChips.ALL) {
            var l = new Label(chip.label());
            chipNodes.put(chip.minutes(), l);
            row.getChildren().add(l);
        }
        capLine.setStyle("-fx-font-size: 11;");
        render();
    }

    Node node() { return new VBox(4, capLine, row); }

    void setCap(int capMinutes) {
        this.cap = capMinutes;
        this.selected = DurationChips.largestFitting(capMinutes);
        render();
    }

    void move(boolean right) {
        var enabled = enabledMinutesAscending();
        if (enabled.isEmpty()) return;
        var idx = Math.max(0, enabled.indexOf(selected));
        selected = enabled.get(right ? Math.min(idx + 1, enabled.size() - 1) : Math.max(idx - 1, 0));
        render();
    }

    int selectedMinutes() { return selected; }

    List<Integer> enabledMinutesAscending() {
        var list = new ArrayList<Integer>();
        for (var chip : DurationChips.ALL) if (chip.minutes() <= cap) list.add(chip.minutes());
        return list;
    }

    void render() {
        capLine.setText(cap <= 0 ? "duration — no duration available at this anchor"
            : "duration — capped at " + AncestryTableView.label(cap) + " by the selected anchor");
        for (var chip : DurationChips.ALL) {
            var l = chipNodes.get(chip.minutes());
            var enabled = chip.minutes() <= cap;
            l.setStyle(chip.minutes() == selected ? selectedStyle() : enabled ? baseStyle() : disabledStyle());
        }
    }

    static String baseStyle() {
        return "-fx-border-color: #999; -fx-padding: 3 9; -fx-font-size: 11; -fx-background-color: white;";
    }

    static String selectedStyle() {
        return baseStyle() + " -fx-background-color: #2e6da8; -fx-text-fill: white; -fx-border-color: #1c4d80;";
    }

    static String disabledStyle() {
        return "-fx-border-color: #ddd; -fx-padding: 3 9; -fx-font-size: 11;"
            + " -fx-background-color: #e4e4e4; -fx-text-fill: #aaa; -fx-strikethrough: true;";
    }
}

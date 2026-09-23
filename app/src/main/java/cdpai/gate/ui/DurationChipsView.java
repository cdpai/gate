package cdpai.gate.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/// The chip row. Chips above the current cap are shown struck through and cannot be reached, so
/// choosing a broader anchor or a wider scope visibly removes the longer durations.
final class DurationChipsView {

    final DurationChips.Chip[] chips;
    final HBox row = new HBox(6);
    final Label capLine = new Label();
    final Map<Integer, Label> chipNodes = new LinkedHashMap<>();
    int cap, selected;
    String capReason = "";

    DurationChipsView(DurationChips.Chip[] chips) {
        this.chips = chips;
        for (var chip : chips) {
            var l = new Label(chip.label());
            chipNodes.put(chip.minutes(), l);
            row.getChildren().add(l);
        }
        capLine.setStyle("-fx-font-size: 11;");
        render();
    }

    Node node() { return new VBox(4, capLine, row); }

    void setCap(int capMinutes, Integer requested, String reason) {
        cap = capMinutes;
        capReason = reason;
        selected = DurationChips.preselect(chips, capMinutes, requested);
        render();
    }

    void move(boolean right) {
        var enabled = enabledAscending();
        if (enabled.isEmpty()) return;
        var idx = Math.max(0, enabled.indexOf(selected));
        selected = enabled.get(right ? Math.min(idx + 1, enabled.size() - 1) : Math.max(idx - 1, 0));
        render();
    }

    int selectedMinutes() { return selected; }

    List<Integer> enabledAscending() {
        var list = new ArrayList<Integer>();
        for (var chip : chips) if (chip.minutes() <= cap) list.add(chip.minutes());
        return list;
    }

    void render() {
        capLine.setText(cap <= 0 ? "duration: none available here"
            : "duration: at most " + DurationChips.label(cap) + (capReason.isEmpty() ? "" : ", set by " + capReason));
        for (var chip : chips) {
            var enabled = chip.minutes() <= cap;
            chipNodes.get(chip.minutes()).setStyle(chip.minutes() == selected ? SELECTED : enabled ? BASE : DISABLED);
        }
    }

    static final String
        BASE = "-fx-border-color: #999; -fx-padding: 3 9; -fx-font-size: 11; -fx-background-color: white;",
        SELECTED = BASE + " -fx-background-color: #2e6da8; -fx-text-fill: white; -fx-border-color: #1c4d80;",
        DISABLED = "-fx-border-color: #ddd; -fx-padding: 3 9; -fx-font-size: 11;"
            + " -fx-background-color: #e4e4e4; -fx-text-fill: #aaa; -fx-strikethrough: true;";
}

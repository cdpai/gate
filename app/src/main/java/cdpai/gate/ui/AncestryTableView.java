package cdpai.gate.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import cdpai.gate.access.AnchorCategory;

/// The ancestry table itself: one row per hop, a RadioButton for each selectable row (session-root
/// and terminal-host rows get a plain "-" instead, per the design doc's hard exclusion), and the
/// live "covers N procs" / colour-graded risk / cap columns that make breadth legible at a glance.
final class AncestryTableView {

    final VBox root = new VBox(2);
    final ToggleGroup group = new ToggleGroup();
    final Map<Integer, RadioButton> selectable = new LinkedHashMap<>();
    final List<AncestryRow> rows;

    AncestryTableView(List<AncestryRow> rows) {
        this.rows = rows;
        root.getChildren().add(header());
        for (var row : rows) root.getChildren().add(rowView(row));
    }

    Node node() { return root; }

    List<Integer> selectableHopsAscending() { return List.copyOf(selectable.keySet()); }

    void selectHop(int hop) {
        var rb = selectable.get(hop);
        if (rb != null) rb.setSelected(true);
    }

    Optional<Integer> selectedHop() {
        var t = group.getSelectedToggle();
        return t == null ? Optional.empty() : Optional.of((Integer) t.getUserData());
    }

    void onSelectionChange(Runnable r) {
        group.selectedToggleProperty().addListener((obs, old, now) -> { if (now != null) r.run(); });
    }

    static Node header() {
        var h = new HBox(8, cell("", 18), cell("process", 170), cell("pid", 55),
            cell("alive", 45), cell("covers", 65), cell("risk", 65), cell("max", 45));
        h.setStyle("-fx-font-weight: bold; -fx-font-size: 10; -fx-text-fill: #666;");
        return h;
    }

    Node rowView(AncestryRow row) {
        var indent = "  ".repeat(row.hop()) + (row.hop() > 0 ? "└ " : "");
        var nameLabel = new Label(indent + row.node().imageName());
        nameLabel.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 11;");

        Node selector;
        if (row.selectable()) {
            var rb = new RadioButton();
            rb.setToggleGroup(group);
            rb.setUserData(row.hop());
            selectable.put(row.hop(), rb);
            selector = rb;
        } else {
            selector = new Label("—");
        }

        var line = new HBox(8, cell(selector, 18), cell(nameLabel, 170),
            cell(String.valueOf(row.node().pid()), 55), cell(age(row.node()), 45),
            cell(row.node().descendantCount() + " procs", 65), riskBadge(row), capLabel(row));
        line.setAlignment(Pos.CENTER_LEFT);
        if (!row.selectable()) line.setOpacity(0.55);
        return line;
    }

    static Node riskBadge(AncestryRow row) {
        var text = row.category() == AnchorCategory.SESSION_ROOT ? "SHELL"
            : row.cap().minutes() == 0 ? "HIGH"
            : row.cap().minutes() < 120 ? "MEDIUM" : "LOW";
        var color = row.category() == AnchorCategory.SESSION_ROOT ? "#777"
            : row.cap().minutes() == 0 ? "#d2691e"
            : row.cap().minutes() < 120 ? "#b8860b" : "#2e7d32";
        var badge = new Label(text);
        badge.setTextFill(Color.WHITE);
        badge.setStyle("-fx-background-color: " + color + "; -fx-font-size: 9; -fx-font-weight: bold;"
            + " -fx-padding: 1 6; -fx-background-radius: 3;");
        return cell(badge, 65);
    }

    static Node capLabel(AncestryRow row) {
        var text = row.cap().minutes() <= 0 ? "0" : DurationChips.largestFitting(row.cap().minutes()) == 0
            ? row.cap().minutes() + "m" : label(row.cap().minutes());
        var l = new Label(text);
        l.setStyle("-fx-font-family: Consolas, monospace; -fx-font-weight: bold;"
            + (row.cap().minutes() <= 0 ? " -fx-text-fill: #b3261e;" : ""));
        return cell(l, 45);
    }

    static String label(int minutes) {
        for (var chip : DurationChips.ALL) if (chip.minutes() == minutes) return chip.label();
        return minutes + "m";
    }

    static String age(cdpai.gate.access.AncestryNode node) {
        var seconds = node.startInstant().map(s -> Duration.between(s, Instant.now()).toSeconds()).orElse(0L);
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        return (seconds / 86400) + "d";
    }

    static Node cell(String text, double width) { return cell(new Label(text), width); }

    static Node cell(Node n, double width) {
        var box = new HBox(n);
        box.setPrefWidth(width);
        box.setMinWidth(width);
        return box;
    }
}

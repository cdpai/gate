package cdpai.gate.ui;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import cdpai.gate.access.*;
import cdpai.gate.win.PeerIdentity;
import cdpai.gate.win.RemoteProcessInfo;

/// "The anchor is a deliberate choice made by a human, per approval, from a displayed tree. It is
/// never assumed and never auto-applied." (design doc) This window is that choice: the ancestry
/// table with a suggested-but-not-forced anchor, duration chips capped by whatever is selected,
/// and nothing granted until Approve is pressed. No text input anywhere in this window, so bare
/// letter keys (A/D) are fine per the keyboard-shortcuts guideline's own stated exception.
final class ApprovalWindow {

    final PeerIdentity client;
    final List<AncestryNode> chain;
    final ProcessTree tree;
    final Consumer<Optional<Approval.Decision>> onDecision;
    final AncestryTableView table;
    final DurationChipsView chips = new DurationChipsView();
    final Stage stage = new Stage(StageStyle.UTILITY);
    boolean decided;

    ApprovalWindow(PeerIdentity client, List<AncestryNode> chain, ProcessTree tree,
                   Consumer<Optional<Approval.Decision>> onDecision) {
        this.client = client;
        this.chain = chain;
        this.tree = tree;
        this.onDecision = onDecision;
        this.table = new AncestryTableView(AncestryRow.build(chain, tree));
    }

    void show() {
        var approve = new Button("(A)pprove");
        var deny = new Button("(D)eny");
        approve.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        deny.setStyle("-fx-background-color: #b3261e; -fx-text-fill: white;");
        approve.setOnAction(e -> approve());
        deny.setOnAction(e -> decide(Optional.empty()));

        var buttons = new HBox(8, approve, deny);
        var status = new Label("↑/↓ anchor · ←/→ duration · A approve · D/Esc deny"
            + " · grant dies when the anchor exits, whatever the clock says");
        status.setStyle("-fx-font-size: 10; -fx-text-fill: #777;");

        var root = new VBox(10, header(), instructionLine(), table.node(), chips.node(), buttons, status);
        root.setPadding(new Insets(14));
        root.setPrefWidth(620);

        table.onSelectionChange(this::onAnchorChanged);
        var suggested = AnchorSuggestion.suggest(chain, tree);
        var firstSelectable = table.selectableHopsAscending().stream().findFirst().orElse(-1);
        table.selectHop(suggested >= 0 ? suggested : firstSelectable);
        onAnchorChanged();

        var scene = new Scene(root);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        stage.setTitle("cdpgate — connection request");
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.setOnCloseRequest(e -> decide(Optional.empty()));
        stage.show();
        stage.toFront();
    }

    Node header() {
        var name = new Label(chain.get(0).imageName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        var badge = new Label("NOT APPROVED");
        badge.setStyle("-fx-background-color: #b3261e; -fx-text-fill: white; -fx-font-size: 10;"
            + " -fx-padding: 1 6; -fx-background-radius: 3;");
        var title = new HBox(8, name, badge);

        var meta = new Label("PID " + client.pid() + " · kernel-attested");
        meta.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 11; -fx-text-fill: #444;");

        return new VBox(3, title, meta, box("full command", RemoteProcessInfo.commandLine(client.pid())),
            box("working directory", RemoteProcessInfo.currentDirectory(client.pid())));
    }

    static Node box(String labelText, Optional<String> content) {
        var lbl = new Label(labelText.toUpperCase());
        lbl.setStyle("-fx-font-size: 9; -fx-text-fill: #777;");
        var text = new TextArea(content.orElse("(unavailable)"));
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefRowCount(2);
        text.setMaxHeight(46);
        text.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 10.5;");
        return new VBox(2, lbl, text);
    }

    static Node instructionLine() {
        var l = new Label("Choose how far up the process tree the approval reaches."
            + " Broader anchors get a shorter maximum duration.");
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 11;");
        return l;
    }

    void onAnchorChanged() {
        var hop = table.selectedHop();
        var cap = hop.map(h -> table.rows.get(h).cap().minutes()).orElse(0);
        chips.setCap(cap);
    }

    void onKey(KeyEvent e) {
        var code = e.getCode();
        if (code == KeyCode.UP || code == KeyCode.DOWN) { moveAnchor(code == KeyCode.DOWN); e.consume(); }
        else if (code == KeyCode.LEFT || code == KeyCode.RIGHT) { chips.move(code == KeyCode.RIGHT); e.consume(); }
        else if (code == KeyCode.A) { approve(); e.consume(); }
        else if (code == KeyCode.D || code == KeyCode.ESCAPE) { decide(Optional.empty()); e.consume(); }
    }

    void moveAnchor(boolean down) {
        var hops = table.selectableHopsAscending();
        if (hops.isEmpty()) return;
        var current = table.selectedHop().orElse(hops.get(0));
        var idx = hops.indexOf(current);
        var next = down ? Math.min(idx + 1, hops.size() - 1) : Math.max(idx - 1, 0);
        table.selectHop(hops.get(next));
    }

    void approve() {
        var hop = table.selectedHop();
        if (hop.isEmpty()) return;
        var row = table.rows.get(hop.get());
        if (!row.selectable()) return;
        var minutes = chips.selectedMinutes();
        if (minutes <= 0 || minutes > row.cap().minutes()) return;
        decide(Optional.of(new Approval.Decision(row.node(), minutes)));
    }

    void decide(Optional<Approval.Decision> decision) {
        if (decided) return;
        decided = true;
        onDecision.accept(decision);
        stage.close();
    }
}

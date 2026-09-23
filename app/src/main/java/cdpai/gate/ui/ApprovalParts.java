package cdpai.gate.ui;

import java.util.List;
import java.util.Optional;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import cdpai.gate.access.Approval;
import cdpai.gate.access.Scope;
import cdpai.gate.browser.ProfileMap;
import cdpai.gate.win.RemoteProcessInfo;

/// The read-only parts of the approval window: who is asking, what they are about to run, and
/// exactly what they ask for. Everything is shown in full, in scrollable boxes where it is long.
final class ApprovalParts {

    static final String RED = "#b3261e", GREEN = "#2e7d32", GREY = "#666";

    static Node header(Approval.Request r) {
        var name = new Label(r.chain().isEmpty() ? r.app() : r.chain().getFirst().imageName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");
        var badge = new Label(r.kind() == Approval.Kind.KEYED ? "KEYED APP" : "NOT APPROVED");
        badge.setStyle("-fx-background-color: " + (r.kind() == Approval.Kind.KEYED ? "#5b3fa0" : RED)
            + "; -fx-text-fill: white; -fx-font-size: 10; -fx-padding: 1 6; -fx-background-radius: 3;");
        var who = r.kind() == Approval.Kind.KEYED
            ? "app \"" + r.app() + "\" · key " + r.keyFingerprint() + " · " + (r.reason() == null ? "" : r.reason())
            : "app \"" + r.app() + "\" · PID " + r.client().pid() + " · identified by the kernel";
        var meta = small(who, GREY);
        return new VBox(3, new HBox(8, name, badge), meta,
            box("full command", RemoteProcessInfo.commandLine(r.client().pid())),
            box("working directory", RemoteProcessInfo.currentDirectory(r.client().pid())));
    }

    static Node box(String caption, Optional<String> content) {
        var text = new TextArea(content.orElse("(unavailable)"));
        text.setEditable(false);
        text.setWrapText(true);
        text.setPrefRowCount(3);
        text.setMaxHeight(64);
        text.setFocusTraversable(false);
        text.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 10.5;");
        return new VBox(2, small(caption.toUpperCase(), "#777"), text);
    }

    static Node scope(Approval.Request r) {
        var s = r.requested();
        var profiles = s.profiles() == null
            ? line("profiles: ALL, " + loaded(r.profiles()) + " loaded now, and any opened later", RED)
            : line("profiles: " + String.join(", ", s.profiles().stream().map(d -> describe(d, r.profiles())).toList()), GREEN);
        var domains = s.domains() == null ? line("websites: ALL", RED) : line("websites: " + String.join(", ", s.domains()), GREEN);
        var minutes = line("asked for: " + (s.requestedMinutes() == null ? "not specified" : DurationChips.label(s.requestedMinutes())), GREY);
        var box = new VBox(2, small("REQUESTED", "#777"), profiles, domains, minutes);
        if (s.unscoped()) box.getChildren().add(line("Every profile and every website. Approving needs the passphrase.", RED));
        return box;
    }

    static Node keyedNote(Approval.Request r) {
        var l = new Label("This approval is held by the app's own key, not by a process. Whatever presents this key gets it,"
            + " until it expires or you revoke it. Presented this time by " + r.client().imagePath() + ".");
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 11;");
        return l;
    }

    static String describe(String dir, List<ProfileMap.Entry> entries) {
        return entries.stream().filter(e -> e.dir().equals(dir)).findFirst()
            .map(e -> e.name() + " (" + e.dir() + (e.contextId() == null ? ", opens on approval" : "") + ")").orElse(dir);
    }

    static long loaded(List<ProfileMap.Entry> entries) { return entries.stream().filter(e -> e.contextId() != null).count(); }

    static Label line(String text, String color) {
        var l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 11; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        return l;
    }

    static Label small(String text, String color) {
        var l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-font-size: 10; -fx-text-fill: " + color + ";");
        return l;
    }

    static boolean needsPassphrase(Scope s) { return s.unscoped(); }

    private ApprovalParts() {}
}

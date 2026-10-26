package cdpai.gate.ui;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import cdpai.gate.client.OsMemory;
import cdpai.gate.hub.CdpHub;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import leasegate.frame.*;

/// The browser's processes with the RAM each holds, like a task manager: opened from the tray, read when
/// it opens and again on F5. It never polls, so a closed window costs nothing. Working set is RAM in use now;
/// private is what the process has committed. CDP gives the processes, the OS gives the memory.
final class MemoryWindow {

    static final String[] COLUMNS = {"pid", "type", "RAM now MB", "committed MB", "CPU s"};

    final Brand brand;
    final Supplier<CdpHub> hub;
    Stage stage;
    TableView<String[]> table;
    Label total;

    MemoryWindow(Brand brand, Supplier<CdpHub> hub) {
        this.brand = brand;
        this.hub = hub;
    }

    void show() {
        if (stage != null && stage.isShowing()) { stage.toFront(); refresh(); return; }
        var f = GateFrame.resizable(brand, "browser memory").notOnTop();
        stage = f.stage;
        table = new TableView<>();
        for (var i = 0; i < COLUMNS.length; i++) {
            var at = i;
            var c = new TableColumn<String[], String>(COLUMNS[i]);
            c.setCellValueFactory(r -> new SimpleStringProperty(r.getValue()[at]));
            c.setSortable(false);
            c.setPrefWidth(i == 1 ? 260 : 110);
            table.getColumns().add(c);
        }
        VBox.setVgrow(table, Priority.ALWAYS);
        total = new Label("reading...");
        total.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #222;");
        var hint = new Label("F5 reads again  ·  Esc closes");
        hint.setStyle("-fx-font-size: 11; -fx-text-fill: #777;");
        var body = new VBox(10, total, table, hint);
        body.setPadding(new Insets(16));
        body.setPrefSize(760, 420);
        var scene = f.scene(body);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
            else if (e.getCode() == KeyCode.F5) { e.consume(); refresh(); }
        });
        stage.setTitle("BROWSER MEMORY");
        stage.show();
        refresh();
    }

    void refresh() {
        Thread.ofPlatform().name("cdpgate-memory").daemon().start(() -> {
            try {
                var h = hub.get();
                if (h == null) { Platform.runLater(() -> total.setText("the browser is not running under cdpgate")); return; }
                var procs = h.internalCall("SystemInfo.getProcessInfo", JsonNodeFactory.instance.objectNode()).get(5, TimeUnit.SECONDS).path("result").path("processInfo");
                var pids = new ArrayList<Long>();
                procs.forEach(p -> pids.add(p.path("id").asLong()));
                var mem = OsMemory.of(pids);
                var rows = new ArrayList<String[]>();
                var ram = 0L;
                var committed = 0L;
                for (var p : procs) {
                    var m = mem.getOrDefault(p.path("id").asLong(), new long[] {0, 0});
                    ram += m[0];
                    committed += Math.max(m[1], 0);
                    rows.add(new String[] {p.path("id").asText(), p.path("type").asText(), String.valueOf(m[0] >> 20), m[1] < 0 ? "-" : String.valueOf(m[1] >> 20), String.valueOf(Math.round(p.path("cpuTime").asDouble()))});
                }
                rows.sort((x, y) -> Long.compare(Long.parseLong(y[2]), Long.parseLong(x[2])));
                var line = procs.size() + " processes · " + (ram >> 20) + " MB in RAM now · " + (committed >> 20) + " MB committed";
                Platform.runLater(() -> { table.setItems(FXCollections.observableArrayList(rows)); total.setText(line); });
            } catch (Exception e) {
                Platform.runLater(() -> total.setText("could not read: " + e.getMessage()));
            }
        });
    }
}

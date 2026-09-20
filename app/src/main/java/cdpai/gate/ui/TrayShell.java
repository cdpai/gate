package cdpai.gate.ui;

import javafx.application.Platform;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

import com.dustinredmond.fxtrayicon.FXTrayIcon;

import cdpai.gate.GateServer;
import cdpai.gate.win.VivaldiProcess;

/// The tray menu: "active approvals" (the live list), "revoke everything now", and quit -- the
/// subset of the mockup's tray that this cycle actually builds. Registered/bearer applications and
/// a one-click Vivaldi+gate restart are deliberately not here yet: registration has no code behind
/// it at all yet (its own PRP, per the design doc), and a live restart that swaps the browser link
/// out from under a running CdpHub needs its own careful design rather than a rushed half version.
public final class TrayShell {

    final Stage statusStage;
    final VivaldiProcess vivaldi;
    final GateServer server;
    final AccessListWindow accessList;

    public TrayShell(Stage statusStage, VivaldiProcess vivaldi, GateServer server) {
        this.statusStage = statusStage;
        this.vivaldi = vivaldi;
        this.server = server;
        this.accessList = new AccessListWindow(server);
    }

    public void install() {
        var icon = new FXTrayIcon(statusStage, GateIcon.image());
        icon.setTrayIconTooltip("cdpgate — gating · pipe → Vivaldi");

        var active = new MenuItem("Active approvals…");
        active.setOnAction(e -> accessList.show());
        icon.addMenuItem(active);

        var revokeAll = new MenuItem("Revoke everything now");
        revokeAll.setOnAction(e -> {
            server.revokeAllNow();
            icon.showInfoMessage("cdpgate", "All approvals revoked.");
        });
        icon.addMenuItem(revokeAll);

        icon.addMenuItem(new SeparatorMenuItem());
        var quit = new MenuItem("Quit");
        quit.setOnAction(e -> quit());
        icon.addMenuItem(quit);

        icon.show();
    }

    void quit() {
        vivaldi.shutdownGracefully(3000);
        Platform.exit();
        System.exit(0);
    }
}

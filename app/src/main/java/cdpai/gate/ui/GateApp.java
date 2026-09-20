package cdpai.gate.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import cdpai.gate.GateServer;
import cdpai.gate.client.GateClient;
import cdpai.gate.win.VivaldiLauncher;
import cdpai.gate.win.VivaldiProcess;

/// The real, running cdpgate: launches Vivaldi, starts GateServer against it with the JavaFX
/// approval window, and installs the tray. Args: [vivaldiExe] [userDataDir] [pipeName], all
/// optional -- omit userDataDir to run against the real default profile, which is the actual
/// production case; every trial in this PRP up to now has deliberately used a scratch profile
/// instead, and that stays true for anything run from a test/trial class, not from here.
public final class GateApp extends Application {

    static final String DEFAULT_VIVALDI_EXE = "C:\\user\\Apps\\Vivaldi\\Application\\vivaldi.exe";

    VivaldiProcess vivaldi;
    GateServer server;

    public static void main(String[] args) { launch(args); }

    @Override public void init() {
        Platform.setImplicitExit(false);
        var params = getParameters().getRaw();
        var exePath = params.size() > 0 ? params.get(0) : DEFAULT_VIVALDI_EXE;
        var userDataDir = params.size() > 1 ? params.get(1) : null;
        var pipeName = params.size() > 2 ? params.get(2) : GateClient.PIPE_NAME;

        vivaldi = VivaldiLauncher.launch(exePath, userDataDir);
        server = new GateServer(vivaldi, pipeName, new FxApproval());
        Thread.ofPlatform().name("cdpgate-server").start(server::run);
    }

    @Override public void start(Stage primaryStage) {
        primaryStage.setTitle("cdpgate");
        primaryStage.setScene(new Scene(new VBox(8,
            new Label("cdpgate is running."),
            new Label("Vivaldi pid: " + vivaldi.pid))));
        new TrayShell(primaryStage, vivaldi, server).install();
    }
}

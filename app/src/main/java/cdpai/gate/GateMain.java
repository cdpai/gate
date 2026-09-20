package cdpai.gate;

import cdpai.gate.ui.GateApp;

/// Plain launcher, deliberately NOT the Application subclass: JavaFX refuses to launch from a
/// shaded jar when main() lives directly on the class that extends Application (see the java
/// guidelines: a second, plain Main is needed for reflection to see the JavaFX modules properly).
public final class GateMain {
    public static void main(String[] args) {
        GateApp.main(args);
    }
}

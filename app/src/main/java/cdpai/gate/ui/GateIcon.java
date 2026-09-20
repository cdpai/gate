package cdpai.gate.ui;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

/// A small drawn padlock, not a stock teacup icon (per the javafx guidelines: distinguish
/// projects rather than leave every tray app looking the same). Drawn with Canvas rather than an
/// SVG/PNG asset, so there is nothing to ship.
final class GateIcon {

    static Image image() {
        var size = 32;
        var canvas = new Canvas(size, size);
        new Scene(new Group(canvas));   // forces layout/CSS so snapshot() works off-screen

        var g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, size, size);
        g.setStroke(Color.web("#2e6da8"));
        g.setLineWidth(3.5);
        g.strokeArc(8, 4, 16, 16, 0, 180, ArcType.OPEN);
        g.setFill(Color.web("#2e6da8"));
        g.fillRoundRect(6, 14, 20, 14, 5, 5);
        g.setFill(Color.WHITE);
        g.fillOval(14, 19, 4, 4);

        var params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }

    private GateIcon() {}
}

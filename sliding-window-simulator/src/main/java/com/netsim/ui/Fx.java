package com.netsim.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import java.util.Locale;
import java.util.function.Function;

/** Small UI helpers shared by all views. */
final class Fx {
    private Fx() { }

    static <T> TableColumn<T, String> col(String title, double width, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    static void error(String title, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(title); a.setContentText(message);
        a.getDialogPane().setMinWidth(480);
        a.showAndWait();
    }

    static Label heading(String text) { Label l = new Label(text); l.getStyleClass().add("section-title"); return l; }

    static Label hint(String text) { Label l = new Label(text); l.setWrapText(true); l.getStyleClass().add("hint"); return l; }

    static Region spacer() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }

    static Insets pad(double v) { return new Insets(v); }

    /** Formats NaN as "n/a" so undefined metrics are never shown as zero. */
    static String num(double v, int decimals) {
        return Double.isNaN(v) ? "n/a" : String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}

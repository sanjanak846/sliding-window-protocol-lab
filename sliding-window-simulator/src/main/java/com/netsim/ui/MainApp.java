package com.netsim.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Application entry: header, shared configuration panel, and the four modes as tabs. */
public final class MainApp extends Application {

    @Override public void start(Stage stage) {
        ConfigPanel config = new ConfigPanel();

        Label title = new Label("Sliding Window Protocol Lab");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Interactive analysis and requirement-based selection of Go-Back-N and Selective Repeat");
        subtitle.getStyleClass().add("app-subtitle");
        VBox header = new VBox(2, title, subtitle);
        header.getStyleClass().add("header");
        header.setPadding(new Insets(12, 16, 12, 16));

        TabPane tabs = new TabPane(
                tab("Simulator", new SimulatorTab(config::read)),
                tab("Compare", new ComparisonTab(config::read)),
                tab("Recommend", new RecommendationTab(config::read)),
                tab("Experiments", new ExperimentTab(config::read)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        ScrollPane left = new ScrollPane(config);
        left.setFitToWidth(true);
        left.setPrefWidth(320);
        left.setMinWidth(280);

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setLeft(left);
        root.setCenter(tabs);

        Scene scene = new Scene(root, 1360, 860);
        scene.getStylesheets().add(getClass().getResource("/com/netsim/ui/style.css").toExternalForm());
        stage.setTitle("Sliding Window Protocol Lab");
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
    }

    private static Tab tab(String name, javafx.scene.Node content) { return new Tab(name, content); }

    public static void main(String[] args) { launch(args); }
}

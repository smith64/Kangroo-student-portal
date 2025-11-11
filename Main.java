package com.kangaroo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Kangaroo Student Portal");

        BorderPane root = new BorderPane();
        WebView webView = new WebView();
        WebEngine webEngine = webView.getEngine();

        // expose bridge object to JS
        Bridge bridge = new Bridge();
        webEngine.getLoadWorker().stateProperty().addListener((obs, old, nw) -> {
            if (nw == javafx.concurrent.Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("javaBridge", bridge);
            }
        });

        // Load the HTML page as a resource so relative links (style.js, app.js) work
        String url = getClass().getResource("/web/index.html").toExternalForm();
        webEngine.load(url);

        root.setCenter(webView);
        Scene scene = new Scene(root, 1000, 700);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        DB.init();
        // start embedded API server for REST-based auth
        ApiServer.start(4567);
        // ensure we stop the server on JVM exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ApiServer.stopServer();
        }));
        launch(args);
    }
}

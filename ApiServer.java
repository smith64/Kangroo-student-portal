package com.kangaroo;

import java.io.File;
import java.util.Map;

import com.google.gson.Gson;

import static spark.Spark.*;

public class ApiServer {
    private static final Gson gson = new Gson();

    public static void start(int port) {
        port(port);

        // Serve static files from the project working directory so your existing login.html and index.html are served.
        // If you run the jar from the project root this will work; otherwise adjust the path below.
        String projectDir = System.getProperty("user.dir");
        // Optionally override via environment variable
        String staticLocation = System.getenv("STATIC_FILES_DIR");
        if (staticLocation == null || staticLocation.isEmpty()) {
            staticLocation = projectDir;
        }
        // Spark requires a directory path without trailing slash for externalLocation
        staticFiles.externalLocation(new File(staticLocation).getAbsolutePath());

        // Redirect root to login page so users must pass through login.html
        get("/", (req, res) -> {
            res.redirect("/login.html");
            return null;
        });

        // Example API route placeholder
        get("/api/ping", (req, res) -> "pong");

        // Basic CORS handling for local development (WebView or browser)
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }
            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }
            return "OK";
        });

        before((request, response) -> {
            response.header("Content-Type", "application/json");
            response.header("Access-Control-Allow-Origin", "*");
        });

        post("/api/login", (req, res) -> {
            try {
                Map body = gson.fromJson(req.body(), Map.class);
                String email = body.get("email") == null ? null : body.get("email").toString();
                String password = body.get("password") == null ? null : body.get("password").toString();
                if (email == null || password == null) {
                    res.status(400);
                    return gson.toJson(Map.of("success", false, "message", "Missing email or password"));
                }
                String user = DB.authenticateUser(email, password);
                if (user != null) {
                    return gson.toJson(Map.of("success", true, "email", user));
                } else {
                    res.status(401);
                    return gson.toJson(Map.of("success", false, "message", "Invalid credentials"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return gson.toJson(Map.of("success", false, "message", "Internal error"));
            }
        });

        post("/api/logout", (req, res) -> {
            return gson.toJson(Map.of("success", true));
        });

        get("/api/health", (req, res) -> gson.toJson(Map.of("ok", true)));
    }

    public static void stopServer() {
        stop();
    }
}

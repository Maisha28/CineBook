import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Filter;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * CineBook Bridge Server
 * ----------------------
 * Thin HTTP server that:
 *  1. Serves the React frontend (Frontend/dist) at http://localhost:8080/
 *  2. Exposes a JSON REST API consumed by the React UI
 *
 * All RMI calls go through the ExperimentManager handlers so that the
 * frontend never talks to RMI registries directly.
 */
public class BridgeServer {

    static final int PORT = 8080;
    static String WEB_ROOT = findWebRoot();

    private static String findWebRoot() {
        String[] candidates = {
            "../../Frontend/dist",
            "../Frontend/dist",
            "Frontend/dist",
            "./dist"
        };
        for (String c : candidates) {
            if (Files.isDirectory(Paths.get(c))) return c;
        }
        return "../../Frontend/dist";
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) WEB_ROOT = args[0];

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // ── Static file handler ──────────────────────────────────────────────
        route(server, "/", BridgeServer::handleStatic);

        // ── Catalog & Ticketing API ─────────────────────────────────────────
        route(server, "/api/movies",       BridgeServer::handleMovieRoute);
        route(server, "/api/cinemas",      CatalogHandler::getCinemas);
        route(server, "/api/shows",        CatalogHandler::getShows);
        route(server, "/api/system/status",CatalogHandler::getSystemStatus);

        // ── Experiment 1 & 2: RMI booking API ───────────────────────────────
        route(server, "/api/exp1/seats",   ex -> Exp12Handler.seats(ex, 1));
        route(server, "/api/exp1/book",    ex -> Exp12Handler.book(ex, 1));
        route(server, "/api/exp2/seats",   ex -> Exp12Handler.seats(ex, 2));
        route(server, "/api/exp2/book",    ex -> Exp12Handler.book(ex, 2));
        route(server, "/api/exp2/concurrent", Exp12Handler::concurrentDemo);

        // ── Experiment 3: Lamport clocks + Berkeley sync ─────────────────────
        route(server, "/api/exp3/seats",   ex -> Exp3Handler.seats(ex));
        route(server, "/api/exp3/book",    ex -> Exp3Handler.book(ex));
        route(server, "/api/exp3/lamport", ex -> Exp3Handler.lamportDemo(ex));
        route(server, "/api/exp3/berkeley",ex -> Exp3Handler.berkeleySync(ex));

        // ── Experiment 4: Bully election ─────────────────────────────────────
        route(server, "/api/exp4/status",  ex -> Exp4Handler.status(ex));
        route(server, "/api/exp4/start",   ex -> Exp4Handler.startCluster(ex));
        route(server, "/api/exp4/crash",   ex -> Exp4Handler.crash(ex));
        route(server, "/api/exp4/elect",   ex -> Exp4Handler.elect(ex));
        route(server, "/api/exp4/stop",    ex -> Exp4Handler.stopCluster(ex));
        route(server, "/api/exp4/logs",    ex -> Exp4Handler.logs(ex));

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[BridgeServer] Listening on http://localhost:" + PORT);
        System.out.println("[BridgeServer] Serving static files from: " +
                Paths.get(WEB_ROOT).toAbsolutePath());
    }

    private static void route(HttpServer server, String path, HttpHandler handler) {
        HttpContext ctx = server.createContext(path, handler);
        ctx.getFilters().add(new CorsFilter());
    }

    static class CorsFilter extends Filter {
        @Override
        public void doFilter(HttpExchange ex, Chain chain) throws IOException {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            chain.doFilter(ex);
        }
        @Override
        public String description() { return "CORS & Options Filter"; }
    }

    private static void handleMovieRoute(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.startsWith("/api/movies/") && path.length() > "/api/movies/".length()) {
            String id = path.substring("/api/movies/".length());
            CatalogHandler.getMovieById(ex, id);
        } else {
            CatalogHandler.getMovies(ex);
        }
    }

    // ── Static file serving ──────────────────────────────────────────────────
    static void handleStatic(HttpExchange ex) throws IOException {
        String uriPath = ex.getRequestURI().getPath();
        if (uriPath.equals("/")) uriPath = "/index.html";

        Path filePath = Paths.get(WEB_ROOT, uriPath).normalize();
        // Security: ensure the resolved path stays inside WEB_ROOT
        Path webRoot = Paths.get(WEB_ROOT).toAbsolutePath().normalize();
        if (!filePath.toAbsolutePath().normalize().startsWith(webRoot)) {
            send(ex, 403, "text/plain", "Forbidden");
            return;
        }

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            // Try index.html for SPA-style deep links
            Path index = filePath.resolve("index.html");
            if (Files.exists(index)) { filePath = index; }
            else {
                send(ex, 404, "text/plain", "Not found: " + uriPath);
                return;
            }
        }

        String mime = mime(filePath.toString());
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        byte[] body = Files.readAllBytes(filePath);
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    static String mime(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }

    // ── Shared helpers ───────────────────────────────────────────────────────
    static String bodyString(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new LinkedHashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try { map.put(URLDecoder.decode(kv[0], "UTF-8"),
                              URLDecoder.decode(kv[1], "UTF-8")); }
                catch (Exception ignored) {}
            }
        }
        return map;
    }

    static void json(HttpExchange ex, int code, String json) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        send(ex, code, "application/json; charset=utf-8", json);
    }

    static void send(HttpExchange ex, int code, String mime, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    /** Simple JSON string escaping */
    static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\","\\\\").replace("\"","\\\"")
                       .replace("\n","\\n").replace("\r","\\r") + "\"";
    }
}

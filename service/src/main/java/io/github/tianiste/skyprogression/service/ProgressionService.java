package io.github.tianiste.skyprogression.service;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import io.github.tianiste.skyprogression.core.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/** Local application backend. The Hypixel application key never goes into the mod or a response. */
public final class ProgressionService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String apiKey = System.getenv("HYPIXEL_API_KEY");
    private final Map<String, Cached> reports = new HashMap<>();
    private Map<String, Price> quotes = Map.of();
    private Instant quotedAt = Instant.EPOCH;
    private record Cached(Instant time, Report report) {}
    public static void main(String[] args) throws IOException {
        new ProgressionService().start();
    }
    private void start() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 18475), 16);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("SkyProgression service listening on http://localhost:18475 (local access only)");
        if (apiKey == null || apiKey.isBlank()) System.out.println("HYPIXEL_API_KEY is not set. Profile lookup is unavailable; price lookup still works.");
    }
    private void handle(HttpExchange x) throws IOException {
        try {
            if (!x.getRequestMethod().equals("GET")) { send(x, 405, Map.of("error", "GET required")); return; }
            String origin = x.getRequestHeaders().getFirst("Origin");
            String host = x.getRequestHeaders().getFirst("Host");
            if (origin != null || host == null || !(host.equals("localhost:18475") || host.equals("127.0.0.1:18475") || host.equals("[::1]:18475"))) {
                send(x, 403, Map.of("error", "Local mod access only")); return;
            }
            switch (x.getRequestURI().getPath()) {
                case "/health" -> send(x, 200, Map.of("ready", apiKey != null && !apiKey.isBlank()));
                case "/report" -> {
                    String query = Objects.toString(x.getRequestURI().getRawQuery(), "");
                    if (!query.matches("ign=[A-Za-z0-9_]{1,16}")) { send(x, 400, Map.of("error", "Enter a valid Minecraft name")); return; }
                    send(x, 200, report(query.substring(4)));
                }
                case "/prices" -> send(x, 200, prices());
                default -> send(x, 404, Map.of("error", "Unknown endpoint"));
            }
        } catch (LookupException e) { send(x, e.status, Map.of("error", e.getMessage())); }
        catch (Exception e) { send(x, 502, Map.of("error", "Data service unavailable. Please try again later.")); }
        finally { x.close(); }
    }
    private void send(HttpExchange x, int status, Object value) throws IOException {
        byte[] body = Catalog.GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        x.sendResponseHeaders(status, body.length);
        x.getResponseBody().write(body);
    }
    private synchronized Report report(String ign) throws Exception {
        if (apiKey == null || apiKey.isBlank()) throw new LookupException(503, "Set HYPIXEL_API_KEY for the companion service, then restart it. Never enter an API key into Minecraft chat.");
        String name = ign.toLowerCase(Locale.ROOT);
        Cached old = reports.get(name);
        if (old != null && old.time.plusSeconds(300).isAfter(Instant.now())) return old.report;
        JsonObject identity = get("https://api.mojang.com/users/profiles/minecraft/" + ign, false);
        String uuid = string(identity.get("id"), "");
        if (!uuid.matches("[a-fA-F0-9]{32}")) throw new LookupException(404, "Minecraft player not found");
        JsonObject data = get("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + uuid, true);
        if (!"true".equals(string(data.get("success"), "false"))) throw new LookupException(502, "Hypixel could not return this profile");
        Report result = new Report(); result.profiles = ProfileParser.parse(data, uuid, ign);
        if (result.profiles.isEmpty()) throw new LookupException(404, "No SkyBlock profiles found for " + ign);
        result.fetchedAt = Instant.now().toString();
        if (reports.size() >= 100) reports.clear();
        reports.put(name, new Cached(Instant.now(), result));
        return result;
    }
    private synchronized Map<String, Price> prices() throws Exception {
        if (quotedAt.plusSeconds(900).isAfter(Instant.now())) return quotes;
        Map<String, Price> fresh = new ConcurrentHashMap<>();
        // Bounded parallelism avoids issuing dozens of requests at once.
        try (var pool = Executors.newFixedThreadPool(3)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (Item item : Catalog.load()) jobs.add(pool.submit(() -> {
                try {
                    JsonObject response = get("https://sky.coflnet.com/api/item/price/" + item.id + "/current?minAgeSeconds=120", false);
                    Price p = new Price(); p.buy = number(response.get("buy"), 0);
                    p.available = p.buy > 0 && number(response.get("available"), 0) > 0;
                    p.source = "true".equals(string(response.get("isAh"), "false")) ? "Auction House (Coflnet)" : "Bazaar (Coflnet)";
                    p.updatedAt = string(response.get("updatedAt"), "Unknown quote time");
                    fresh.put(item.id, p);
                } catch (Exception ignored) { /* Missing prices stay unknown, never zero-cost. */ }
            }));
            for (Future<?> job : jobs) job.get();
        }
        quotes = Map.copyOf(fresh); quotedAt = Instant.now();
        return quotes;
    }
    private JsonObject get(String url, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "SkyProgression/0.1.0 (github.com/tianiste/SkyProgression)")
                .header("Accept", "application/json");
        if (authenticated) request.header("API-Key", apiKey);
        var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            int status = response.statusCode();
            if (status == 403 && authenticated) throw new LookupException(503, "Hypixel application key is invalid or expired. Update the service configuration.");
            if (status == 429) throw new LookupException(429, "Rate limited. Wait a few minutes before refreshing.");
            if (status == 404 || status == 204) throw new LookupException(404, "Player or item not found");
            if (status != 200) throw new IOException("Remote service returned " + status);
            byte[] bytes = body.readNBytes(16 * 1024 * 1024 + 1);
            if (bytes.length > 16 * 1024 * 1024) throw new IOException("Response too large");
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static final class LookupException extends Exception {
        final int status;
        LookupException(int status, String message) { super(message); this.status = status; }
    }
}

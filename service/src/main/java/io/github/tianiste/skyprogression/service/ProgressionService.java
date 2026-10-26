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
    private final String apiKey = readApiKey();
    private final Map<String, Cached> reports = new HashMap<>();
    private final Object profileLock = new Object();
    private Map<String, Price> quotes = Map.of();
    private Instant quotedAt = Instant.EPOCH;
    /** Parsed report plus the raw payload, which the stat ledger reads; owned items are decoded once per profile. */
    private record Cached(Instant time, Report report, JsonObject raw, String uuid, Map<String, Loadout.Owned> owned) {}
    private static String readApiKey() {
        String configured = System.getenv("HYPIXEL_API_KEY");
        if (configured != null && !configured.isBlank()) return configured;
        Console console = System.console();
        if (console == null) return null;
        console.printf("SkyProgression companion service%nKeep this window open while using live profile lookup.%n");
        console.printf("Enter your application key (hidden input), or press Enter to skip.%nThe key is not saved to disk.%n");
        char[] entered = console.readPassword("Hypixel application key: ");
        if (entered == null) return null;
        try { return new String(entered).strip(); }
        finally { Arrays.fill(entered, '\0'); }
    }
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
                case "/stats" -> send(x, 200, stats(query(x)));
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
    private static Map<String, String> query(HttpExchange x) throws LookupException {
        Map<String, String> values = new HashMap<>();
        String raw = Objects.toString(x.getRequestURI().getRawQuery(), "");
        if (raw.length() > 512) throw new LookupException(400, "Query too long");
        for (String part : raw.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            if (eq <= 0) throw new LookupException(400, "Malformed query");
            values.put(part.substring(0, eq), URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return values;
    }
    /** Mining Fortune / Speed ledger for one profile: best owned gear and tool by default, or what is worn. */
    private StatsReport stats(Map<String, String> q) throws Exception {
        String ign = q.getOrDefault("ign", ""), profileId = q.getOrDefault("profile", ""), tool = q.getOrDefault("tool", "");
        String zone = q.getOrDefault("zone", ""), target = q.getOrDefault("target", "");
        if (!ign.matches("[A-Za-z0-9_]{1,16}")) throw new LookupException(400, "Enter a valid Minecraft name");
        if (!profileId.matches("[a-fA-F0-9-]{0,40}") || !tool.matches("[A-Za-z0-9_:#. -]{0,80}")) throw new LookupException(400, "Invalid profile or tool");
        StatContext context;
        MiningMethod method;
        try {
            method = MiningMethod.valueOf(q.getOrDefault("method", "MF_GEMS"));
            context = new StatContext(zone.isEmpty() ? null : StatContext.Zone.valueOf(zone), target.isEmpty() ? null : StatContext.Target.valueOf(target),
                    !"false".equals(q.get("god")));
        } catch (IllegalArgumentException e) { throw new LookupException(400, "Unknown method, zone or block target"); }
        Cached cached = fetch(ign);
        JsonObject entry = null;
        for (JsonElement p : cached.raw.getAsJsonArray("profiles")) {
            boolean match = profileId.isEmpty() ? "true".equals(string(at(p, "selected"), "false")) : profileId.equals(string(at(p, "profile_id"), ""));
            if (match && at(p, "members", cached.uuid).isJsonObject()) { entry = p.getAsJsonObject(); break; }
        }
        if (entry == null) throw new LookupException(404, "Profile not found for " + ign);
        JsonObject member = at(entry, "members", cached.uuid).getAsJsonObject();
        String id = string(at(entry, "profile_id"), "");
        Loadout.Owned owned = cached.owned.computeIfAbsent(id, ignored -> Loadout.read(member));
        StatsReport result = StatsReport.of(member, owned, method, tool, !"equipped".equals(q.get("gear")), context);
        result.player = ign; result.profileId = id; result.profile = string(at(entry, "cute_name"), "Profile");
        result.fetchedAt = cached.report.fetchedAt;
        return result;
    }
    private Report report(String ign) throws Exception { return fetch(ign).report; }
    private Cached fetch(String ign) throws Exception {
        synchronized (profileLock) { return fetchLocked(ign); }
    }
    private Cached fetchLocked(String ign) throws Exception {
        if (apiKey == null || apiKey.isBlank()) throw new LookupException(503, "Set HYPIXEL_API_KEY for the companion service, then restart it. Never enter an API key into Minecraft chat.");
        String name = ign.toLowerCase(Locale.ROOT);
        Cached old = reports.get(name);
        if (old != null && old.time.plusSeconds(300).isAfter(Instant.now())) return old;
        JsonObject identity = get("https://api.mojang.com/users/profiles/minecraft/" + ign, false);
        String uuid = string(identity.get("id"), "");
        if (!uuid.matches("[a-fA-F0-9]{32}")) throw new LookupException(404, "Minecraft player not found");
        JsonObject data = get("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + uuid, true);
        if (!"true".equals(string(data.get("success"), "false"))) throw new LookupException(502, "Hypixel could not return this profile");
        Report result = new Report(); result.profiles = ProfileParser.parse(data, uuid, ign);
        if (result.profiles.isEmpty()) throw new LookupException(404, "No SkyBlock profiles found for " + ign);
        result.fetchedAt = Instant.now().toString();
        // Raw payloads are several megabytes each, so only a few players are kept.
        if (reports.size() >= 8) reports.clear();
        Cached fresh = new Cached(Instant.now(), result, data, uuid, new ConcurrentHashMap<>());
        reports.put(name, fresh);
        return fresh;
    }
    private synchronized Map<String, Price> prices() throws Exception {
        if (quotedAt.plusSeconds(900).isAfter(Instant.now())) return quotes;
        Map<String, Price> fresh = new ConcurrentHashMap<>();
        // Bounded parallelism avoids issuing dozens of requests at once.
        try (var pool = Executors.newFixedThreadPool(3)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (String itemId : GearUpgrades.priceIds(Catalog.load())) jobs.add(pool.submit(() -> {
                try {
                    JsonObject response = get("https://sky.coflnet.com/api/item/price/" + itemId + "/current?minAgeSeconds=120", false);
                    Price p = new Price(); p.buy = number(response.get("buy"), 0);
                    p.available = p.buy > 0 && number(response.get("available"), 0) > 0;
                    p.source = "true".equals(string(response.get("isAh"), "false")) ? "Auction House (Coflnet)" : "Bazaar (Coflnet)";
                    p.updatedAt = string(response.get("updatedAt"), "Unknown quote time");
                    fresh.put(itemId, p);
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

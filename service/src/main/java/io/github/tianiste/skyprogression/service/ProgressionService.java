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

/**
 * Local data backend for the mod, with the same endpoints as the hosted SkyProgression API: raw profiles, a price
 * bundle and resale quotes. All calculations run in the mod. The Hypixel key never goes into the mod or a response.
 * With SKYPROGRESSION_RELAY_SECRET set it runs as the hosted API's profile relay on a fixed-IP server (behind an HTTPS
 * reverse proxy): only /health and /profile, and /profile only for requests carrying that secret.
 */
public final class ProgressionService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String apiKey = readApiKey();
    private final Map<String, Cached> profiles = new ConcurrentHashMap<>();
    private final Map<String, Timed<String>> names = new ConcurrentHashMap<>();
    private final Object profileLock = new Object();
    private volatile Map<String, Price> quotes = Map.of();
    private volatile Instant quotedAt = Instant.EPOCH;
    /** Background refreshes and quote lookups, so a request never waits for data it can answer from cache. */
    private final ExecutorService background = Executors.newFixedThreadPool(3, Thread.ofPlatform().daemon().name("SkyProgression refresh").factory());
    private final Set<String> refreshing = ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicBoolean pricesRefreshing = new java.util.concurrent.atomic.AtomicBoolean();
    /** The mod connects to 18475; SKYPROGRESSION_PORT is only for running a second copy during development. */
    private static final int PORT = Integer.parseInt(Objects.requireNonNullElse(System.getenv("SKYPROGRESSION_PORT"), "18475"));
    /** Raw Hypixel profiles response of one player, passed through to the mod unparsed. */
    private record Cached(Instant time, byte[] body) {}
    /** Shared secret of relay mode (the Worker sends it as X-Relay-Secret); null for the local companion. */
    private static final byte[] RELAY_SECRET = relaySecret();
    private static byte[] relaySecret() {
        String value = System.getenv("SKYPROGRESSION_RELAY_SECRET");
        if (value == null || value.isBlank()) return null;
        if (value.strip().length() < 32) throw new IllegalStateException("SKYPROGRESSION_RELAY_SECRET must be at least 32 characters");
        return value.strip().getBytes(StandardCharsets.UTF_8);
    }
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
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 16);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("SkyProgression service listening on http://localhost:" + PORT + (RELAY_SECRET != null ? " (profile relay mode)" : " (local access only)"));
        if (apiKey == null || apiKey.isBlank()) System.out.println("HYPIXEL_API_KEY is not set. Profile lookup is unavailable; price lookup still works.");
        if (RELAY_SECRET != null) return; // The relay serves profiles only; the hosted API builds prices itself.
        // Warm the shared caches so the first screen does not wait for ~300 price quotes.
        background.submit(() -> { try { prices(); } catch (Exception ignored) { /* Retried on the first /prices request. */ } });
        background.submit(this::mayorPerks);
    }
    private void handle(HttpExchange x) throws IOException {
        try {
            if (!x.getRequestMethod().equals("GET")) { send(x, 405, Map.of("error", "GET required")); return; }
            String origin = x.getRequestHeaders().getFirst("Origin");
            String host = x.getRequestHeaders().getFirst("Host");
            if (origin != null || host == null || !(host.equals("localhost:" + PORT) || host.equals("127.0.0.1:" + PORT) || host.equals("[::1]:" + PORT))) {
                send(x, 403, Map.of("error", "Local mod access only")); return;
            }
            String path = x.getRequestURI().getPath();
            if (RELAY_SECRET != null && !path.equals("/health")) {
                String given = Objects.toString(x.getRequestHeaders().getFirst("X-Relay-Secret"), "");
                if (!path.equals("/profile") || !java.security.MessageDigest.isEqual(RELAY_SECRET, given.getBytes(StandardCharsets.UTF_8))) {
                    send(x, 404, Map.of("error", "Unknown endpoint")); return;
                }
            }
            switch (path) {
                case "/health" -> send(x, 200, Map.of("ok", true, "ready", apiKey != null && !apiKey.isBlank()));
                case "/uuid" -> send(x, 200, uuid(query(x).getOrDefault("name", "")));
                case "/profile" -> profile(x, query(x));
                case "/prices" -> send(x, 200, bundle());
                case "/resale" -> send(x, 200, Collections.singletonMap("price", resale(query(x))));
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
    private record Timed<T>(Instant time, T value) {}
    private final Map<String, Timed<Price>> copyQuotes = new ConcurrentHashMap<>();
    private final Map<String, Timed<Map<String, List<String>>>> filterOptions = new ConcurrentHashMap<>();
    private Price copyQuote(Resale.Query q) throws Exception {
        Map<String, List<String>> options = filterOptions(q.tag());
        StringJoiner query = new StringJoiner("&"), used = new StringJoiner(", "), dropped = new StringJoiner(", ");
        for (var f : q.filters().entrySet()) {
            List<String> allowed = options.get(f.getKey());
            String value = f.getValue();
            if (allowed == null) { dropped.add(f.getKey()); continue; }
            if (!f.getKey().equals("PetLevel")) {
                value = allowed.stream().filter(v -> v.equalsIgnoreCase(f.getValue())).findFirst().orElse(null);
                if (value == null) { dropped.add(f.getKey() + " " + f.getValue()); continue; }
            }
            query.add(f.getKey() + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
            used.add(f.getKey() + " " + value);
        }
        String path = q.tag() + "/bin?" + query;
        Timed<Price> hit = copyQuotes.get(path);
        if (hit != null && hit.time().plusSeconds(900).isAfter(Instant.now())) return hit.value();
        JsonObject response = get("https://sky.coflnet.com/api/item/price/" + path, false);
        double lowest = number(response.get("lowest"), 0);
        Price p = null;
        if (lowest > 0) {
            p = new Price(lowest, "lowest BIN with " + (used.length() == 0 ? "no filters" : used) + (dropped.length() == 0 ? "" : "; not filterable: " + dropped)
                    + " (Coflnet, before Auction House fees)");
            p.sell = lowest; p.updatedAt = "Current lowest BIN";
        }
        copyQuotes.put(path, new Timed<>(Instant.now(), p));
        return p;
    }
    private Map<String, List<String>> filterOptions(String tag) throws Exception {
        Timed<Map<String, List<String>>> hit = filterOptions.get(tag);
        if (hit != null && hit.time().plusSeconds(3600).isAfter(Instant.now())) return hit.value();
        var request = HttpRequest.newBuilder(URI.create("https://sky.coflnet.com/api/filter/options?itemTag=" + tag)).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "SkyProgression/0.1.0 (github.com/tianiste/SkyProgression)").header("Accept", "application/json").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        Map<String, List<String>> out = new HashMap<>();
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Remote service returned " + response.statusCode());
            byte[] bytes = body.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new IOException("Response too large");
            for (JsonElement f : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray()) {
                List<String> values = new ArrayList<>();
                JsonElement opts = at(f, "options");
                if (opts.isJsonArray()) opts.getAsJsonArray().forEach(v -> values.add(string(v, "")));
                out.put(string(at(f, "name"), ""), values);
            }
        }
        filterOptions.put(tag, new Timed<>(Instant.now(), out));
        return out;
    }
    /** Minecraft name to UUID (Mojang), cached for a day. */
    private Map<String, String> uuid(String name) throws Exception {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new LookupException(400, "Enter a valid Minecraft name");
        String key = name.toLowerCase(Locale.ROOT);
        Timed<String> hit = names.get(key);
        if (hit == null || !hit.time().plusSeconds(86400).isAfter(Instant.now())) {
            JsonObject identity = get("https://api.mojang.com/users/profiles/minecraft/" + name, false);
            String id = string(identity.get("id"), "").toLowerCase(Locale.ROOT);
            if (!id.matches("[a-f0-9]{32}")) throw new LookupException(404, "Minecraft player not found");
            if (names.size() > 500) names.clear();
            hit = new Timed<>(Instant.now(), id + ":" + string(identity.get("name"), name));
            names.put(key, hit);
        }
        int colon = hit.value().indexOf(':');
        return Map.of("id", hit.value().substring(0, colon), "name", hit.value().substring(colon + 1));
    }
    /**
     * Raw Hypixel profiles response, passed through unparsed with X-Fetched-At. A copy younger than five minutes is reused;
     * an older one is returned at once while a background refresh runs, unless fresh=1 (the mod's explicit Load).
     */
    private void profile(HttpExchange x, Map<String, String> q) throws Exception {
        String uuid = q.getOrDefault("uuid", "").replace("-", "").toLowerCase(Locale.ROOT);
        if (!uuid.matches("[a-f0-9]{32}")) throw new LookupException(400, "uuid must be 32 hex characters");
        Cached c = profiles.get(uuid);
        if (c == null || "1".equals(q.get("fresh")) && !fresh(c)) c = fetchProfile(uuid);
        else if (!fresh(c) && refreshing.add(uuid)) background.submit(() -> {
            try { fetchProfile(uuid); } catch (Exception ignored) { /* Keep the cached copy; the next request retries. */ }
            finally { refreshing.remove(uuid); }
        });
        x.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        x.getResponseHeaders().set("X-Fetched-At", c.time.toString());
        x.sendResponseHeaders(200, c.body.length);
        x.getResponseBody().write(c.body);
    }
    private static boolean fresh(Cached c) { return c.time.plusSeconds(300).isAfter(Instant.now()); }
    private Cached fetchProfile(String uuid) throws Exception {
        synchronized (profileLock) {
            if (apiKey == null || apiKey.isBlank()) throw new LookupException(503, "Set HYPIXEL_API_KEY for the companion service, then restart it. Never enter an API key into Minecraft chat.");
            Cached old = profiles.get(uuid);
            if (old != null && fresh(old)) return old;
            byte[] body = getBytes("https://api.hypixel.net/v2/skyblock/profiles?uuid=" + uuid, true);
            // Raw payloads can be several megabytes each, so only a few players are kept.
            if (profiles.size() >= 8) profiles.clear();
            Cached fresh = new Cached(Instant.now(), body);
            profiles.put(uuid, fresh);
            return fresh;
        }
    }
    /** Modifier-aware sale quote for one copy (filters as /resale query parameters); null when there is no listing. */
    private Price resale(Map<String, String> q) throws Exception {
        String tag = q.getOrDefault("tag", "");
        if (!tag.matches("[A-Z0-9_]{1,64}")) throw new LookupException(400, "Invalid item tag");
        Map<String, String> filters = new LinkedHashMap<>();
        for (var e : q.entrySet()) {
            if (e.getKey().equals("tag")) continue;
            if (!RESALE_FILTERS.contains(e.getKey()) || !e.getValue().matches("[A-Za-z0-9_ ]{0,40}")) throw new LookupException(400, "Invalid filter");
            filters.put(e.getKey(), e.getValue());
        }
        try { return copyQuote(new Resale.Query(tag, filters)); }
        catch (LookupException e) { if (e.status == 404) return null; throw e; }
    }
    private static final Set<String> RESALE_FILTERS = Set.of("Rarity", "Recombobulated", "Reforge", "PowderCoating", "DrillPartEngine", "DrillPartFuelTank",
            "DrillPartUpgradeModule", "PetLevel", "PetItem");
    /** Same shape as the hosted API's /prices: every quote plus the current mayor perks (null while unknown). */
    private Map<String, Object> bundle() throws Exception {
        Map<String, Price> current = prices();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("updatedAt", quotedAt.toString());
        out.put("prices", current);
        out.put("mayor", mayorPerks());
        out.put("complete", true);
        return out;
    }
    /** Price quotes: after the first load, expired quotes are served while a background refresh replaces them. */
    private Map<String, Price> prices() throws Exception {
        if (quotes.isEmpty()) return refreshPrices();
        if (!quotedAt.plusSeconds(900).isAfter(Instant.now()) && pricesRefreshing.compareAndSet(false, true)) background.submit(() -> {
            try { refreshPrices(); } catch (Exception ignored) { /* Keep the old quotes. */ }
            finally { pricesRefreshing.set(false); }
        });
        return quotes;
    }
    /** Own lock: a price refresh (~300 quotes) must not hold up profile lookups or the mayor perks. */
    private final Object priceLock = new Object();
    private Map<String, Price> refreshPrices() throws Exception {
        synchronized (priceLock) { return refreshPricesLocked(); }
    }
    private Map<String, Price> refreshPricesLocked() throws Exception {
        if (!quotes.isEmpty() && quotedAt.plusSeconds(900).isAfter(Instant.now())) return quotes;
        Map<String, Price> fresh = new ConcurrentHashMap<>();
        // Bounded parallelism avoids issuing dozens of requests at once.
        try (var pool = Executors.newFixedThreadPool(3)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (String itemId : GearUpgrades.priceIds(Catalog.load())) jobs.add(pool.submit(() -> {
                try {
                    if (itemId.contains(";")) { fresh.put(itemId, petPrice(itemId)); return; }
                    JsonObject response = get("https://sky.coflnet.com/api/item/price/" + itemId + "/current?minAgeSeconds=120", false);
                    Price p = new Price(); p.buy = number(response.get("buy"), 0); p.sell = Math.max(0, number(response.get("sell"), 0));
                    p.available = p.buy > 0 && number(response.get("available"), 0) > 0;
                    p.source = "true".equals(string(response.get("isAh"), "false")) ? "Auction House (Coflnet)" : "Bazaar (Coflnet)";
                    p.updatedAt = string(response.get("updatedAt"), "Unknown quote time");
                    fresh.put(itemId, p);
                } catch (Exception ignored) { /* Missing prices stay unknown, never zero-cost. */ }
            }));
            for (Future<?> job : jobs) job.get();
        }
        try {
            Map<String, Double> npc = npcPrices();
            String at = npcAt.toString();
            for (String id : MethodComparison.npcIds()) {
                Double v = npc.get(id);
                if (v == null || !(v > 0)) continue;
                Price p = fresh.computeIfAbsent(id, ignored -> { Price none = new Price(); none.source = "No Coflnet quote"; none.updatedAt = "unknown"; return none; });
                p.npc = v; p.npcUpdatedAt = at;
            }
        } catch (Exception ignored) { /* NPC prices stay unknown. */ }
        quotes = Map.copyOf(fresh); quotedAt = Instant.now();
        return quotes;
    }
    private Map<String, Double> npcPrices = Map.of();
    private Instant npcAt = Instant.EPOCH;
    /** npc_sell_price per item ID from the official items resource (no key needed), refreshed every six hours. */
    private Map<String, Double> npcPrices() throws Exception {
        if (npcAt.plusSeconds(6 * 3600).isAfter(Instant.now())) return npcPrices;
        Map<String, Double> out = new HashMap<>();
        for (JsonElement item : get("https://api.hypixel.net/v2/resources/skyblock/items", false).getAsJsonArray("items")) {
            JsonElement price = at(item, "npc_sell_price");
            if (price.isJsonPrimitive()) out.put(string(at(item, "id"), ""), number(price, 0));
        }
        npcPrices = Map.copyOf(out); npcAt = Instant.now();
        return npcPrices;
    }
    private List<StatBreakdown.MayorPerk> mayorPerks = List.of();
    private Instant mayorAt = Instant.EPOCH;
    /**
     * Current mayor and minister perks from the official election resource (no key needed), refreshed every 20 minutes.
     * Null when the resource cannot be read, so the ledger keeps its untrackable lines.
     */
    private synchronized List<StatBreakdown.MayorPerk> mayorPerks() {
        if (mayorAt.plusSeconds(20 * 60).isAfter(Instant.now())) return mayorPerks;
        try {
            JsonObject election = get("https://api.hypixel.net/v2/resources/skyblock/election", false);
            List<StatBreakdown.MayorPerk> out = new ArrayList<>();
            JsonElement mayor = at(election, "mayor");
            String name = string(at(mayor, "name"), "");
            if (at(mayor, "perks").isJsonArray()) for (JsonElement p : at(mayor, "perks").getAsJsonArray())
                out.add(new StatBreakdown.MayorPerk(name, string(at(p, "name"), ""), string(at(p, "description"), ""), false));
            JsonElement minister = at(mayor, "minister");
            if (minister.isJsonObject()) out.add(new StatBreakdown.MayorPerk(string(at(minister, "name"), ""), string(at(minister, "perk", "name"), ""),
                    string(at(minister, "perk", "description"), ""), true));
            mayorPerks = List.copyOf(out); mayorAt = Instant.now();
            return mayorPerks;
        } catch (Exception e) {
            // Retry after five minutes instead of slowing every /stats request.
            mayorPerks = null; mayorAt = Instant.now().minusSeconds(15 * 60);
            return null;
        }
    }
    /** "PET_TYPE;RARITY;LEVEL": lowest Auction House BIN with that rarity and level (Coflnet filters). */
    private Price petPrice(String key) throws Exception {
        String[] part = key.split(";");
        JsonObject response = get("https://sky.coflnet.com/api/item/price/" + part[0] + "/bin?Rarity=" + part[1] + "&PetLevel=" + part[2], false);
        Price p = new Price(); p.buy = number(response.get("lowest"), 0);
        p.available = p.buy > 0;
        p.source = "Auction House lowest BIN, level " + part[2] + " (Coflnet)";
        p.updatedAt = "Current lowest BIN";
        return p;
    }
    private JsonObject get(String url, boolean authenticated) throws Exception {
        return JsonParser.parseString(new String(getBytes(url, authenticated), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private byte[] getBytes(String url, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
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
            return bytes;
        }
    }
    private static final class LookupException extends Exception {
        final int status;
        LookupException(int status, String message) { super(message); this.status = status; }
    }
}

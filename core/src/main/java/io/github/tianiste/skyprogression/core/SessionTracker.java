package io.github.tianiste.skyprogression.core;

import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Measured mining session for one method: counts real gains (inventory pickups, sack summaries) and Pristine procs, keeps
 * elapsed and active time with auto-start and idle pause, and turns the counts into the method's units per hour and coins
 * per hour. Rules and sources: docs/CALCULATIONS.md "Session tracker". Not thread-safe; the client calls it on the game thread.
 */
public final class SessionTracker {
    /** Pause after this long without a gain of the method's product (sack summaries arrive at most every 30 s). */
    public static final long DEFAULT_IDLE_MS = 60_000;
    /** Longest window a sack summary covers ("(Last 30s.)"). */
    public static final long SACK_WINDOW_MS = 30_000;

    /**
     * An item the method counts: base is the lowest form of its chain and baseCount how many base items one of it is (NEU
     * recipes: enchanted forms 160, blocks and cubes 160 enchanted, gem tiers 80, Enchanted Hard Stone 576, Concentrated Stone
     * 576 Enchanted); priced is the item the rate and coins are expressed in, worth basePerPriced base items. main marks the
     * method's product (it starts and resumes the session); the others are secondary drops.
     */
    public record Tracked(String id, String name, String base, long baseCount, String priced, long basePerPriced, boolean main) {}
    public enum Source { INVENTORY, SACK }
    public enum State { READY, RUNNING, PAUSED, STOPPED }

    private static final String[] GEM_TIERS = {"ROUGH", "FLAWED", "FINE", "FLAWLESS"};
    private static final List<String> BP7 = List.of("Amber", "Sapphire", "Jade", "Amethyst");

    /** Every item the method counts, main product first. */
    public static List<Tracked> items(MiningMethod m) {
        List<Tracked> out = new ArrayList<>();
        switch (m) {
            case MF_GEMS -> BP7.forEach(g -> gem(out, g, true));
            case MF_RUBY -> gem(out, "Ruby", true);
            case MF_TOPAZ -> gem(out, "Topaz", true);
            case GROTTO -> { gem(out, "Jasper", true); BP7.forEach(g -> gem(out, g, false)); }
            case COAL -> chain(out, true, "COAL", "Coal", "ENCHANTED_COAL", "Enchanted Coal", "ENCHANTED_COAL_BLOCK", "Enchanted Coal Block");
            case GOLD -> chain(out, true, "GOLD_INGOT", "Gold Ingot", "ENCHANTED_GOLD", "Enchanted Gold Ingot", "ENCHANTED_GOLD_BLOCK", "Enchanted Gold Block");
            case OBSIDIAN -> chain(out, true, "OBSIDIAN", "Obsidian", "ENCHANTED_OBSIDIAN", "Enchanted Obsidian");
            case UMBER -> chain(out, true, "UMBER", "Umber", "ENCHANTED_UMBER", "Enchanted Umber");
            case TUNGSTEN -> chain(out, true, "TUNGSTEN", "Tungsten", "ENCHANTED_TUNGSTEN", "Enchanted Tungsten");
            case SHAFTING -> chain(out, true, "GLACITE", "Glacite", "ENCHANTED_GLACITE", "Enchanted Glacite");
            case END_STONE -> chain(out, true, "ENDER_STONE", "End Stone", "ENCHANTED_ENDSTONE", "Enchanted End Stone");
            case COBBLESTONE -> chain(out, true, "COBBLESTONE", "Cobblestone", "ENCHANTED_COBBLESTONE", "Enchanted Cobblestone");
            case MYCELIUM -> chain(out, true, "MYCEL", "Mycelium", "ENCHANTED_MYCELIUM", "Enchanted Mycelium", "ENCHANTED_MYCELIUM_CUBE", "Enchanted Mycelium Cube");
            case RED_SAND -> chain(out, true, "SAND:1", "Red Sand", "ENCHANTED_RED_SAND", "Enchanted Red Sand", "ENCHANTED_RED_SAND_CUBE", "Enchanted Red Sand Cube");
        }
        // Hard Stone is mined alongside every Crystal Hollows, Dwarven Mines and Glacite route.
        if (m.defaultZone() != StatContext.Zone.OTHER)
            chain(out, false, "HARD_STONE", "Hard Stone", "ENCHANTED_HARD_STONE", "Enchanted Hard Stone", "CONCENTRATED_STONE", "Concentrated Stone");
        return out;
    }
    private static void gem(List<Tracked> out, String gem, boolean main) {
        String up = gem.toUpperCase(Locale.ROOT), base = "ROUGH_" + up + "_GEM", flawless = "FLAWLESS_" + up + "_GEM";
        long n = 1;
        for (String tier : GEM_TIERS) {
            out.add(new Tracked(tier + "_" + up + "_GEM", tier.charAt(0) + tier.substring(1).toLowerCase(Locale.ROOT) + " " + gem + " Gemstone", base, n, flawless,
                    (long)MethodRates.ROUGH_PER_FLAWLESS, main));
            n *= 80;
        }
    }
    /** id/name pairs from the base item up; each step is 160 of the previous (576 for Hard Stone). */
    private static void chain(List<Tracked> out, boolean main, String... idNames) {
        String base = idNames[0];
        long step = base.equals("HARD_STONE") ? 576 : 160, n = 1;
        for (int i = 0; i < idNames.length; i += 2, n *= step) out.add(new Tracked(idNames[i], idNames[i + 1], base, n, base, 1, main));
    }

    private MiningMethod method;
    private Map<String, Tracked> byId = Map.of(), byName = Map.of();
    private State state = State.READY;
    public boolean autoStart = true;
    public long idleMs = DEFAULT_IDLE_MS;
    private long startedAt, segmentStart, lastGain, stoppedAt, activeMs;
    /** Base items gained per base id; raw item counts per id and source. */
    private final Map<String, Long> baseUnits = new LinkedHashMap<>();
    private final Map<String, Long> raw = new LinkedHashMap<>();
    private final Map<Source, Long> bySource = new EnumMap<>(Source.class);
    private long pristineProcs, pristineFlawed;
    /** Bumped on every change, so a view can rebuild only when something happened. */
    private int version;

    public SessionTracker(MiningMethod method) { use(method); }
    public MiningMethod method() { return method; }
    public State state() { return state; }
    public int version() { return version; }
    public boolean empty() { return state == State.READY; }
    /** Points an empty tracker at another method; a session in progress keeps its method until reset. */
    public void use(MiningMethod m) {
        if (m == method || state != State.READY) return;
        method = m;
        Map<String, Tracked> ids = new HashMap<>(), names = new HashMap<>();
        for (Tracked t : items(m)) { ids.put(t.id(), t); names.put(t.name().toLowerCase(Locale.ROOT), t); }
        byId = ids; byName = names; version++;
    }
    /** The tracked item with this SkyBlock id, or null. */
    public Tracked byId(String id) { return id == null ? null : byId.get(id); }
    /** The tracked item with this display name (sack summaries list names), or null. */
    public Tracked byName(String name) { return name == null ? null : byName.get(name.strip().toLowerCase(Locale.ROOT)); }

    public void start(long now) {
        if (state == State.RUNNING) return;
        if (state == State.READY) startedAt = now;
        state = State.RUNNING; segmentStart = lastGain = now; version++;
    }
    public void stop(long now) {
        if (state == State.RUNNING) activeMs += Math.max(0, now - segmentStart);
        if (state == State.RUNNING || state == State.PAUSED) { state = State.STOPPED; stoppedAt = now; version++; }
    }
    public void reset() {
        state = State.READY; activeMs = 0; baseUnits.clear(); raw.clear(); bySource.clear(); pristineProcs = pristineFlawed = 0; version++;
    }
    /** Pauses after idleMs without a main-product gain; the active time ends at the last gain. */
    public void tick(long now) {
        if (state == State.RUNNING && now - lastGain > idleMs) {
            activeMs += Math.max(0, lastGain - segmentStart); state = State.PAUSED; version++;
        }
    }

    /**
     * Counts amount of a tracked item (inventory amounts may be net base items of a chain, passed as its base id). windowMs is
     * how far back the gain reaches (a sack summary's "Last Ns"), used to back-date an auto-start or resume. A main-product gain
     * starts a ready session (when autoStart) or resumes a paused one; secondary gains count only while running, and a sack
     * summary arriving within its window after Stop still counts (it covers mining before the stop). Returns true when counted.
     */
    public boolean gain(String id, long amount, Source source, long now, long windowMs) {
        Tracked t = byId(id);
        if (t == null || amount <= 0) return false;
        switch (state) {
            case READY -> {
                if (!t.main() || !autoStart) return false;
                startedAt = segmentStart = Math.max(0, now - windowMs); state = State.RUNNING;
            }
            case PAUSED -> {
                if (!t.main()) return false;
                segmentStart = Math.max(lastGain, now - windowMs); state = State.RUNNING;
            }
            case STOPPED -> {
                if (source != Source.SACK || now - stoppedAt > windowMs) return false;
                record(t, amount, source); return true;
            }
            case RUNNING -> {}
        }
        if (t.main()) lastGain = now;
        record(t, amount, source);
        return true;
    }
    private void record(Tracked t, long amount, Source source) {
        baseUnits.merge(t.base(), amount * t.baseCount(), Long::sum);
        raw.merge(t.id(), amount, Long::sum);
        bySource.merge(source, amount * t.baseCount(), Long::sum);
        version++;
    }
    /** A Pristine proc of the gem the method mines; its Flawed gems arrive through the inventory or sacks and are counted there. */
    public void pristine(String gem, long flawed) {
        if (state != State.RUNNING || byId("FLAWED_" + gem.toUpperCase(Locale.ROOT) + "_GEM") == null) return;
        pristineProcs++; pristineFlawed += Math.max(0, flawed); version++;
    }

    public long elapsedMs(long now) { return state == State.READY ? 0 : Math.max(0, (state == State.STOPPED ? stoppedAt : now) - startedAt); }
    public long activeMs(long now) { return activeMs + (state == State.RUNNING ? Math.max(0, now - segmentStart) : 0); }
    public long startedAt() { return startedAt; }
    public long pristineProcs() { return pristineProcs; }
    public long pristineFlawed() { return pristineFlawed; }
    public Map<String, Long> raw() { return Collections.unmodifiableMap(raw); }
    public Map<String, Long> baseUnits() { return Collections.unmodifiableMap(baseUnits); }
    public Map<Source, Long> bySource() { return Collections.unmodifiableMap(bySource); }

    /**
     * One family of the result: amount in its priced item (for example Flawless Amber or Coal), coins per priced item in the
     * price mode (null = no price) and whether it is the method's product.
     */
    public record Family(String priced, String name, double amount, Double coinsEach, boolean main) {}
    /**
     * Measured totals: product amount in the method's rate unit (Flawless for gems, else the block product), coins of every
     * counted family (null when the product has no price; atLeast when a secondary family has none and is left out), and the
     * same per active hour (NaN before any active time).
     */
    public record Measured(double product, Double coins, boolean atLeast, List<String> unpriced, List<Family> families, double activeHours,
                           double productPerHour, Double coinsPerHour) {}
    public Measured measure(long now, Map<String, Price> prices, MethodComparison.PriceMode mode) {
        Map<String, Tracked> families = new LinkedHashMap<>();
        for (Tracked t : items(method)) families.putIfAbsent(t.base(), t);
        List<Family> out = new ArrayList<>();
        double product = 0, coins = 0; boolean mainUnknown = false; List<String> unpriced = new ArrayList<>();
        for (Tracked t : families.values()) {
            long units = baseUnits.getOrDefault(t.base(), 0L);
            if (units <= 0) continue;
            double amount = (double)units / t.basePerPriced();
            Double each = MethodComparison.coinsFor(prices, t.priced(), mode);
            String name = t.priced().startsWith("FLAWLESS_") ? "Flawless " + t.name().split(" ")[1] : t.name();
            out.add(new Family(t.priced(), name, amount, each, t.main()));
            if (t.main()) product += amount;
            if (each == null) { unpriced.add(name); if (t.main()) mainUnknown = true; }
            else coins += amount * each;
        }
        double hours = activeMs(now) / 3_600_000.0;
        Double total = mainUnknown ? null : coins;
        boolean atLeast = total != null && !unpriced.isEmpty();
        return new Measured(product, total, atLeast, unpriced, out, hours, hours > 0 ? product / hours : Double.NaN,
                total == null || !(hours > 0) ? null : total / hours);
    }
}

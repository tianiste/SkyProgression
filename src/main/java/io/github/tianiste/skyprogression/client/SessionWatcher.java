package io.github.tianiste.skyprogression.client;

import io.github.tianiste.skyprogression.core.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.regex.*;

/**
 * Feeds the session tracker from what the client already receives: "[Sacks] +N items" chat summaries (items listed in their
 * hover text), "PRISTINE! You found ..." messages and the player's own inventory. Reads only; never sends anything. The
 * inventory is compared twice a second, the idle check and HUD text once a second; nothing runs per frame except drawing
 * the cached HUD line.
 */
final class SessionWatcher {
    private SessionWatcher() {}
    private static final ClientData DATA = ClientData.INSTANCE;
    private static final Pattern SACKS = Pattern.compile("^\\[Sacks] [+-][\\d,]+ items?");
    private static final Pattern SACK_WINDOW = Pattern.compile("\\(Last (\\d+)s\\.\\)");
    /** Hover line of a sack summary: "  +20 Enchanted Gold Ingot (Enchanted Mining Sack)"; removed items start with "-" and are skipped. */
    private static final Pattern SACK_LINE = Pattern.compile("^\\s*\\+([\\d,]+) (.+?) \\(([^()]+)\\)\\s*$");
    /** "PRISTINE! You found ❈ Flawed Amber Gemstone x28!" (a gem symbol precedes the name). */
    private static final Pattern PRISTINE = Pattern.compile("PRISTINE! You found .*?Flawed (\\w+) Gemstone x([\\d,]+)!");
    private static final Pattern FORMAT = Pattern.compile("§.");
    /** Commands that move items from sacks or storage into the inventory: the next inventory increase is not a gain. */
    private static final Set<String> MOVE_COMMANDS = Set.of("gfs", "getfromsacks", "sacks", "pickupstash", "ps", "pv", "storage", "ec", "enderchest", "bp", "backpack", "trade", "wardrobe");

    private static Map<String, Long> baseline;
    private static MiningMethod baselineMethod;
    private static Player baselinePlayer;
    private static long ignoreInventoryUntil;
    private static int ticks;
    /** Cached HUD line, rebuilt once a second; null hides it. */
    private static volatile String hud;

    static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> { if (!overlay) message(message); });
        ClientSendMessageEvents.COMMAND.register(command -> {
            String name = command.strip().split(" ", 2)[0].toLowerCase(Locale.ROOT);
            if (MOVE_COMMANDS.contains(name)) ignoreInventoryUntil = System.currentTimeMillis() + 5_000;
        });
        ClientTickEvents.END_CLIENT_TICK.register(SessionWatcher::tick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skyprogression", "session"), (g, delta) -> {
            String line = hud;
            var client = Minecraft.getInstance();
            if (line == null || Compat.screen(client) != null) return;
            int w = client.font.width(line);
            g.fill(2, 2, 8 + w, 15, 0xA00D0D13);
            g.text(client.font, line, 5, 5, Theme.CYAN, true);
        });
    }

    private static void tick(Minecraft client) {
        SessionTracker t = DATA.tracker;
        t.use(DATA.method);
        long now = System.currentTimeMillis();
        if (++ticks % 10 == 0) inventory(client, t, now);
        if (ticks % 20 == 0) { t.tick(now); hud = DATA.trackerHud && !t.empty() ? hudLine(t, now) : null; }
    }

    /** Net base items of each tracked chain in the main inventory; increases are gains, decreases (selling, storing) are ignored. */
    private static void inventory(Minecraft client, SessionTracker t, long now) {
        Player player = client.player;
        var screen = Compat.screen(client);
        boolean container = screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen);
        if (player == null || container) { baseline = null; return; }
        Map<String, Long> units = new HashMap<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) continue;
            SessionTracker.Tracked item = tracked(stack, t);
            if (item != null) units.merge(item.base(), item.baseCount() * stack.getCount(), Long::sum);
        }
        // A new world, method or a just-closed container: start comparing from here without counting.
        boolean fresh = baseline == null || baselinePlayer != player || baselineMethod != t.method() || now < ignoreInventoryUntil;
        if (!fresh) for (var e : units.entrySet()) {
            long gained = e.getValue() - baseline.getOrDefault(e.getKey(), 0L);
            if (gained > 0) t.gain(e.getKey(), gained, SessionTracker.Source.INVENTORY, now, 0);
        }
        baseline = units; baselinePlayer = player; baselineMethod = t.method();
    }
    /** The tracked item of a stack: its SkyBlock id (custom data "id"), else its display name. */
    private static SessionTracker.Tracked tracked(ItemStack stack, SessionTracker t) {
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        String id = custom == null ? "" : custom.copyTag().getStringOr("id", "");
        SessionTracker.Tracked item = id.isEmpty() || id.equals("SAND") ? null : t.byId(id);
        return item != null || !id.isEmpty() && !id.equals("SAND") ? item : t.byName(strip(stack.getHoverName().getString()));
    }

    private static void message(Component message) {
        SessionTracker t = DATA.tracker;
        String text = strip(message.getString());
        long now = System.currentTimeMillis();
        Matcher pristine = PRISTINE.matcher(text);
        if (pristine.find()) { t.pristine(pristine.group(1), number(pristine.group(2))); return; }
        if (!SACKS.matcher(text).find()) return;
        Matcher window = SACK_WINDOW.matcher(text);
        long windowMs = window.find() ? Math.min(SessionTracker.SACK_WINDOW_MS, Long.parseLong(window.group(1)) * 1000) : SessionTracker.SACK_WINDOW_MS;
        // The item lists are the hover texts of the "+N items" (and "-N items") parts.
        Set<Component> hovers = Collections.newSetFromMap(new IdentityHashMap<>());
        message.visit((Style style, String part) -> {
            if (style.getHoverEvent() instanceof HoverEvent.ShowText show) hovers.add(show.value());
            return Optional.empty();
        }, Style.EMPTY);
        for (Component hover : hovers) for (String line : strip(hover.getString()).split("\n")) {
            Matcher m = SACK_LINE.matcher(line);
            if (!m.matches()) continue;
            SessionTracker.Tracked item = t.byName(m.group(2));
            if (item != null) t.gain(item.id(), number(m.group(1)), SessionTracker.Source.SACK, now, windowMs);
        }
    }

    private static String hudLine(SessionTracker t, long now) {
        var m = t.measure(now, DATA.prices, DATA.priceMode);
        var p = DATA.predicted(t.method());
        String unit = MethodComparison.itemName(t.method());
        String rate = Double.isFinite(m.productPerHour()) ? ProgressionScreen.compact(m.productPerHour()) : "-";
        String model = p != null && p.perHour() != null ? " (model " + ProgressionScreen.compact(p.perHour()) + ")" : "";
        String state = switch (t.state()) { case PAUSED -> " | idle"; case STOPPED -> " | stopped"; default -> ""; };
        return t.method().label + ": " + rate + " " + unit + "/h" + model + " | " + clock(t.activeMs(now)) + state;
    }
    static String clock(long ms) {
        long s = ms / 1000, h = s / 3600;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, s / 60 % 60, s % 60) : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
    private static String strip(String s) { return FORMAT.matcher(s).replaceAll(""); }
    private static long number(String s) { try { return Long.parseLong(s.replace(",", "")); } catch (NumberFormatException e) { return 0; } }
}

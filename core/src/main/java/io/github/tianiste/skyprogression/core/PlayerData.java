package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static io.github.tianiste.skyprogression.core.Model.*;
import static io.github.tianiste.skyprogression.core.ProfileParser.*;

/**
 * One player's raw Hypixel profiles response, decoded once: the parsed profile list for the screens and, per
 * profile, the owned items the stat ledger reads (decoded on first use). The /stats, /compare and /start reports
 * are built from it on the mod's worker threads; nothing here touches the network.
 */
public final class PlayerData {
    public final String player, uuid, fetchedAt;
    public final long fetchedAtMillis;
    public final Report report;
    private final JsonObject raw;
    private final Map<String, Loadout.Owned> owned = new ConcurrentHashMap<>();

    /** body: the raw v2/skyblock/profiles JSON; fetchedAt: when the backend fetched it (ISO-8601). */
    public PlayerData(String player, String uuid, String body, String fetchedAt) {
        this(player, uuid, object(JsonParser.parseString(body)), fetchedAt);
    }
    /** root: the already parsed v2/skyblock/profiles response (the relay receives it inside a larger request). */
    public PlayerData(String player, String uuid, JsonObject root, String fetchedAt) {
        this.player = player; this.uuid = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        raw = root;
        if (!"true".equals(string(raw.get("success"), "false"))) throw new IllegalArgumentException("Hypixel could not return this profile");
        this.fetchedAt = fetchedAt;
        long at;
        try { at = java.time.Instant.parse(fetchedAt).toEpochMilli(); } catch (Exception e) { at = System.currentTimeMillis(); }
        fetchedAtMillis = at;
        report = new Report();
        report.profiles = ProfileParser.parse(raw, this.uuid, player);
        report.fetchedAt = fetchedAt;
    }

    private static JsonObject object(JsonElement root) {
        if (!root.isJsonObject()) throw new IllegalArgumentException("Profile response is not an object");
        return root.getAsJsonObject();
    }

    public record Member(JsonObject member, Loadout.Owned owned, String id, String name) {}

    /** The player's member data in one profile (empty id = the selected profile). */
    public Member member(String profileId) {
        for (JsonElement p : raw.getAsJsonArray("profiles")) {
            boolean match = profileId.isEmpty() ? "true".equals(string(at(p, "selected"), "false")) : profileId.equals(string(at(p, "profile_id"), ""));
            if (!match || !at(p, "members", uuid).isJsonObject()) continue;
            JsonObject member = at(p, "members", uuid).getAsJsonObject();
            String id = string(at(p, "profile_id"), "");
            return new Member(member, owned.computeIfAbsent(id, ignored -> Loadout.read(member)), id, string(at(p, "cute_name"), "Profile"));
        }
        throw new NoSuchElementException("Profile not found for " + player);
    }

    /** The member with live gear merged over the API's ownership (live null = API only). */
    public Member member(String profileId, LiveGear live) {
        Member m = member(profileId);
        return live == null ? m : new Member(m.member, live.apply(m.owned), m.id, m.name);
    }

    /** Stat ledger for one method (the companion's former /stats); resale quotes are added by the caller. */
    public StatsReport stats(String profileId, MiningMethod method, String tool, boolean bestGear, StatContext context, List<StatBreakdown.MayorPerk> perks) {
        return stats(profileId, method, tool, bestGear, context, perks, null);
    }
    public StatsReport stats(String profileId, MiningMethod method, String tool, boolean bestGear, StatContext context, List<StatBreakdown.MayorPerk> perks, LiveGear live) {
        Member m = member(profileId, live);
        StatsReport r = StatsReport.of(m.member, m.owned, method, tool, bestGear, context, perks);
        r.player = player; r.profileId = m.id; r.profile = m.name; r.fetchedAt = fetchedAt;
        return r;
    }

    public StartWith.Report start(String profileId, MiningMethod method, String tool, StatContext context) {
        return start(profileId, method, tool, context, null);
    }
    public StartWith.Report start(String profileId, MiningMethod method, String tool, StatContext context, LiveGear live) {
        Member m = member(profileId, live);
        StartWith.Report r = StartWith.of(m.member, m.owned, method, tool, context);
        r.player = player; r.profileId = m.id; r.profile = m.name; r.fetchedAt = fetchedAt;
        return r;
    }

    public MethodComparison.Report compare(String profileId, boolean godPotion) {
        return compare(profileId, godPotion, null);
    }
    public MethodComparison.Report compare(String profileId, boolean godPotion, LiveGear live) {
        Member m = member(profileId, live);
        MethodComparison.Report r = MethodComparison.of(m.member, m.owned, godPotion);
        r.player = player; r.profileId = m.id; r.profile = m.name; r.fetchedAt = fetchedAt;
        return r;
    }
}

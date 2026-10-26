package io.github.tianiste.skyprogression.core;

import com.google.gson.JsonObject;
import java.util.*;
import static io.github.tianiste.skyprogression.core.Model.*;

/**
 * Wire format of the companion's {@code /stats} endpoint: the equipped-loadout ledger for every
 * supported stat in one context, plus the tools the player can choose as the held item.
 */
public final class StatsReport {
    public String player = "", profile = "", profileId = "", fetchedAt = "";
    /** Selected tool key (instance UUID or item ID); empty when not chosen. */
    public String tool = "", zone = "", target = "";
    public boolean godPotion = true;
    public List<Tool> tools = new ArrayList<>();
    public List<Ledger> stats = new ArrayList<>();
    public List<String> notes = new ArrayList<>();

    public record Tool(String key, String name, String location) {}
    public static final class Ledger {
        public Stat stat;
        public double flat, multiplier = 1, total;
        /** Equipped total is a lower bound while sources that count have unknown values. */
        public boolean complete;
        /** Hypothetical best owned copy per slot for this stat; null when not computed. */
        public Double bestOwned;
        public List<Breakdown.Contribution> contributions = new ArrayList<>();
        public List<String> notes = new ArrayList<>();
    }

    public Ledger ledger(Stat stat) { return stats.stream().filter(l -> l.stat == stat).findFirst().orElse(null); }

    public static StatsReport of(JsonObject member, Loadout.Owned owned, String tool, StatContext context) {
        StatsReport r = new StatsReport();
        for (Item item : owned.tools()) r.tools.add(new Tool(item.instance.isBlank() ? item.id : item.instance, item.name, item.location));
        r.tools.sort(Comparator.comparing(Tool::name).thenComparing(Tool::location));
        Loadout equipped = Loadout.equipped(owned, tool == null || tool.isBlank() ? null : tool);
        r.tool = equipped.tool == null ? "" : tool;
        r.zone = context.zone() == null ? "" : context.zone().name();
        r.target = context.target() == null ? "" : context.target().name();
        r.godPotion = context.godPotion();
        for (Stat stat : StatBreakdown.SUPPORTED) {
            Breakdown b = StatBreakdown.compute(member, equipped, context, stat);
            Ledger l = new Ledger();
            l.stat = stat; l.flat = b.flat(); l.multiplier = b.multiplier(); l.total = b.total(); l.complete = b.complete();
            l.contributions.addAll(b.contributions); l.notes.addAll(new LinkedHashSet<>(b.notes));
            l.bestOwned = StatBreakdown.compute(member, Loadout.bestOwned(owned, stat), context, stat).total();
            r.stats.add(l);
        }
        return r;
    }
}

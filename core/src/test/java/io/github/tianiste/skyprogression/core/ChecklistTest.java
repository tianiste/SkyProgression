package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static io.github.tianiste.skyprogression.core.ProfileFixtureTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Attribute requirements of the guide format against shard counts. */
class ChecklistTest {
    private static Checklist.Result attribute(String attribute, JsonObject member) {
        JsonObject requirement = new JsonObject();
        requirement.addProperty("kind", "attribute");
        requirement.addProperty("attribute", attribute);
        JsonArray requirements = new JsonArray();
        requirements.add(requirement);
        return Checklist.evaluate(requirements, member).results().getFirst();
    }
    private static JsonObject shards(String attribute, int shards) {
        return JsonParser.parseString("{\"attributes\":{\"stacks\":{\"" + attribute + "\":" + shards + "}}}").getAsJsonObject();
    }

    @Test void maxedForagingAttributeIsOwned() {
        var result = attribute("helix_sharpening", shards("helix_sharpening", 48));
        assertEquals(Checklist.State.OWNED, result.state);
        assertEquals(10, result.have);
        assertEquals(10, result.need);
        assertEquals("levels", result.unit);
    }
    @Test void foragingAttributeBelowMaxIsPartial() {
        var result = attribute("helix_sharpening", shards("helix_sharpening", 47));
        assertEquals(Checklist.State.PARTIAL, result.state);
        assertEquals(9, result.have);
    }
    @Test void foragingRarityDecidesTheShardsForMax() {
        assertEquals(Checklist.State.OWNED, attribute("fig_sharpening", shards("fig_sharpening", 96)).state);
        assertEquals(Checklist.State.PARTIAL, attribute("fig_sharpening", shards("fig_sharpening", 95)).state);
        assertEquals(Checklist.State.OWNED, attribute("strong_legs", shards("strong_legs", 64)).state);
        assertEquals(Checklist.State.PARTIAL, attribute("strong_legs", shards("strong_legs", 63)).state);
    }
    @Test void fixtureForagingAttributes() throws IOException {
        var member = member(fixture());
        assertEquals(Checklist.State.OWNED, attribute("forest_serendipity", member).state);
        var legs = attribute("strong_legs", member);
        assertEquals(Checklist.State.PARTIAL, legs.state);
        assertEquals(5, legs.have);
    }
}

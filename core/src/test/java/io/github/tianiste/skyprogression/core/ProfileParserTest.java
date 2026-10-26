package io.github.tianiste.skyprogression.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ProfileParserTest {
    private String inventory() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try(var out=new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);out.writeUTF("");
            out.writeByte(9);out.writeUTF("i");out.writeByte(10);out.writeInt(1);
            out.writeByte(1);out.writeUTF("Count");out.writeByte(1);
            out.writeByte(10);out.writeUTF("tag");
            out.writeByte(10);out.writeUTF("ExtraAttributes");
            out.writeByte(8);out.writeUTF("id");out.writeUTF("MINERAL_TALISMAN");
            out.writeByte(0);out.writeByte(0);out.writeByte(0);out.writeByte(0);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    @Test void readsHypixelInventoryAndSelectsMember() throws Exception {
        JsonObject root=JsonParser.parseString("""
          {"profiles":[{"profile_id":"p","cute_name":"Lime","selected":true,
            "members":{"abc":{"inventory":{"inv_contents":{}},"mining_core":{"experience":197000}}}}]}
          """).getAsJsonObject();
        var member=root.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("members").getAsJsonObject("abc");
        member.getAsJsonObject("inventory").getAsJsonObject("inv_contents").addProperty("data",inventory());
        var p=ProfileParser.parse(root,"abc","Example").getFirst();
        assertTrue(p.selected);assertTrue(p.inventoryEnabled);assertEquals(6,p.hotm);assertEquals(1,p.owned.get("MINERAL_TALISMAN"));
    }
    @Test void missingInventoryDoesNotInventOwnership() {
        var root=JsonParser.parseString("{\"profiles\":[{\"members\":{\"abc\":{}}}]}").getAsJsonObject();
        var p=ProfileParser.parse(root,"abc","Example").getFirst();
        assertFalse(p.inventoryEnabled);assertTrue(p.owned.isEmpty());assertTrue(p.notes.getFirst().contains("API"));
    }
    @Test void invalidEncodingIsRejected() {
        assertThrows(IOException.class,()->new InventoryReader().read("not base64"));
    }
}
